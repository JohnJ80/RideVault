package com.example.ridevault

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Raw USB implementation of MTP write operations.
 *
 * Android's MtpDevice.sendObjectInfo() returns null on Garmin Edge 1050,
 * so this class bypasses it entirely by constructing MTP protocol packets
 * and sending them over USB bulk endpoints.
 */
class RawMtpSession(
    private val connection: UsbDeviceConnection,
    private val bulkOut: UsbEndpoint,
    private val bulkIn: UsbEndpoint,
    private val timeout: Int = 10000
) {
    private var transactionId = 1

    companion object {
        // Container types
        private const val CONTAINER_COMMAND = 1
        private const val CONTAINER_DATA = 2
        private const val CONTAINER_RESPONSE = 3

        // Operation codes
        private const val OP_GET_DEVICE_INFO = 0x1001
        private const val OP_OPEN_SESSION = 0x1002
        private const val OP_CLOSE_SESSION = 0x1003
        private const val OP_GET_STORAGE_IDS = 0x1004
        private const val OP_GET_OBJECT_HANDLES = 0x1007
        private const val OP_GET_OBJECT_INFO = 0x1008
        private const val OP_SEND_OBJECT_INFO = 0x100C
        private const val OP_SEND_OBJECT = 0x100D

        // Response codes
        private const val RESP_OK = 0x2001
        private const val RESP_SESSION_ALREADY_OPEN = 0x201E

        /**
         * Find the MTP USB interface and endpoints, open a session.
         * Returns null with a reason string if setup fails.
         */
        fun open(
            device: UsbDevice,
            connection: UsbDeviceConnection
        ): Pair<RawMtpSession?, String> {
            // Collect interface info for diagnostics
            val ifaceInfo = StringBuilder()
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                ifaceInfo.append("iface[$i] class=${iface.interfaceClass} " +
                    "sub=${iface.interfaceSubclass} proto=${iface.interfaceProtocol} " +
                    "eps=${iface.endpointCount}; ")
            }

            // Find the MTP interface:
            // 1. class 6 = Still Image (PTP/MTP)
            // 2. class 255 subclass 1 = vendor-specific MTP
            // 3. Fallback: any interface with bulk in + bulk out endpoints
            var mtpInterface: UsbInterface? = null
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == 6 ||
                    (iface.interfaceClass == 255 && iface.interfaceSubclass == 1)
                ) {
                    mtpInterface = iface
                    break
                }
            }

            // Fallback: find any interface with both bulk in and bulk out
            if (mtpInterface == null) {
                for (i in 0 until device.interfaceCount) {
                    val iface = device.getInterface(i)
                    var hasIn = false
                    var hasOut = false
                    for (e in 0 until iface.endpointCount) {
                        val ep = iface.getEndpoint(e)
                        if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                            if (ep.direction == UsbConstants.USB_DIR_OUT) hasOut = true
                            else hasIn = true
                        }
                    }
                    if (hasIn && hasOut) {
                        mtpInterface = iface
                        break
                    }
                }
            }

            if (mtpInterface == null) {
                return null to "No MTP interface found ($ifaceInfo)"
            }

            if (!connection.claimInterface(mtpInterface, true)) {
                return null to "Could not claim MTP interface"
            }

            var bulkOut: UsbEndpoint? = null
            var bulkIn: UsbEndpoint? = null

            for (i in 0 until mtpInterface.endpointCount) {
                val ep = mtpInterface.getEndpoint(i)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_OUT) {
                        bulkOut = ep
                    } else {
                        bulkIn = ep
                    }
                }
            }

            if (bulkOut == null || bulkIn == null) {
                return null to "Could not find bulk endpoints"
            }

            // Reset USB device state before opening raw MTP
            // This clears any lingering state from Android's MtpDevice
            try {
                // SET_CONFIGURATION to re-initialize the device
                connection.controlTransfer(
                    UsbConstants.USB_DIR_OUT, // host-to-device
                    0x09,  // SET_CONFIGURATION
                    1,     // configuration value 1
                    0,
                    null, 0, 5000
                )
            } catch (_: Exception) {
            }

            // Re-claim interface after reset
            connection.claimInterface(mtpInterface, true)

            val session = RawMtpSession(connection, bulkOut, bulkIn)

            // Open MTP session
            val openResult = session.openSession()
            if (openResult != null) {
                return null to openResult
            }

            return session to "OK"
        }
    }

    // ── Low-level USB I/O ──────────────────────────────────

    /** Last bulk transfer diagnostics for error reporting */
    private var lastSendResult = 0
    private var lastSendSize = 0

    private fun sendCommand(
        opCode: Int,
        vararg params: Int
    ): Int {
        val length = 12 + params.size * 4
        val buf = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(length)
        buf.putShort(CONTAINER_COMMAND.toShort())
        buf.putShort(opCode.toShort())
        buf.putInt(transactionId)
        for (p in params) {
            buf.putInt(p)
        }
        lastSendSize = length
        lastSendResult = connection.bulkTransfer(bulkOut, buf.array(), length, timeout)
        return lastSendResult
    }

    private fun sendDataPhase(opCode: Int, data: ByteArray): Int {
        val length = 12 + data.size
        val buf = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(length)
        buf.putShort(CONTAINER_DATA.toShort())
        buf.putShort(opCode.toShort())
        buf.putInt(transactionId)
        buf.put(data)
        lastSendSize = length
        lastSendResult = connection.bulkTransfer(bulkOut, buf.array(), length, timeout)
        return lastSendResult
    }

    /** Last read bytes for diagnostics */
    private var lastReadResult = 0

    /**
     * Clear a halted endpoint via USB control transfer.
     */
    private fun clearHalt(endpoint: UsbEndpoint) {
        try {
            connection.controlTransfer(
                UsbConstants.USB_DIR_OUT or 0x02, // USB_RECIP_ENDPOINT
                0x01,  // CLEAR_FEATURE
                0x00,  // ENDPOINT_HALT
                endpoint.address,
                null, 0, timeout
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Read a response or data container.
     * Returns (containerType, responseCode, params, dataPayload).
     */
    private fun readContainer(): ContainerResult {
        // Read up to 16KB at a time
        val buf = ByteArray(16384)
        var bytesRead = connection.bulkTransfer(bulkIn, buf, buf.size, timeout)
        lastReadResult = bytesRead

        // If read failed, try clearing halt and reading again
        if (bytesRead < 12) {
            clearHalt(bulkIn)
            clearHalt(bulkOut)
            bytesRead = connection.bulkTransfer(bulkIn, buf, buf.size, timeout)
            lastReadResult = bytesRead
        }

        if (bytesRead < 12) {
            return ContainerResult(
                type = -1,
                code = 0,
                params = intArrayOf(),
                data = ByteArray(0)
            )
        }

        val bb = ByteBuffer.wrap(buf, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN)
        val containerLength = bb.getInt()
        val containerType = bb.getShort().toInt() and 0xFFFF
        val code = bb.getShort().toInt() and 0xFFFF
        val txId = bb.getInt()

        return when (containerType) {
            CONTAINER_RESPONSE -> {
                // Params are remaining 4-byte ints
                val paramCount = (bytesRead - 12) / 4
                val params = IntArray(paramCount)
                for (i in 0 until paramCount) {
                    params[i] = bb.getInt()
                }
                ContainerResult(containerType, code, params, ByteArray(0))
            }
            CONTAINER_DATA -> {
                // Data payload follows the header
                val dataLen = bytesRead - 12
                val data = ByteArray(dataLen)
                bb.get(data)

                // If container says it's longer than what we read,
                // read remaining chunks
                if (containerLength > bytesRead) {
                    val fullData = ByteArray(containerLength - 12)
                    System.arraycopy(data, 0, fullData, 0, dataLen)
                    var offset = dataLen
                    while (offset < fullData.size) {
                        val chunk = ByteArray(16384)
                        val chunkRead = connection.bulkTransfer(
                            bulkIn, chunk, chunk.size, timeout
                        )
                        if (chunkRead <= 0) break
                        val toCopy = minOf(chunkRead, fullData.size - offset)
                        System.arraycopy(chunk, 0, fullData, offset, toCopy)
                        offset += toCopy
                    }
                    ContainerResult(containerType, code, intArrayOf(), fullData)
                } else {
                    ContainerResult(containerType, code, intArrayOf(), data)
                }
            }
            else -> {
                ContainerResult(containerType, code, intArrayOf(), ByteArray(0))
            }
        }
    }

    private data class ContainerResult(
        val type: Int,
        val code: Int,
        val params: IntArray,
        val data: ByteArray
    )

    /**
     * Send a command that expects an immediate response (no data phase).
     * Returns the response code and params.
     */
    private fun executeSimple(
        opCode: Int,
        vararg params: Int
    ): ContainerResult {
        sendCommand(opCode, *params)
        val result = readContainer()
        transactionId++
        return result
    }

    /**
     * Send a command + data, then read response.
     */
    private fun executeWithData(
        opCode: Int,
        data: ByteArray,
        vararg params: Int
    ): ContainerResult {
        sendCommand(opCode, *params)
        sendDataPhase(opCode, data)
        val result = readContainer()
        transactionId++
        return result
    }

    /**
     * Send a command that returns data, then a response.
     */
    private fun executeGetData(
        opCode: Int,
        vararg params: Int
    ): Pair<ByteArray, ContainerResult> {
        sendCommand(opCode, *params)
        val dataContainer = readContainer()
        val response = readContainer()
        transactionId++
        return dataContainer.data to response
    }

    // ── MTP Operations ─────────────────────────────────────

    private fun openSession(): String? {
        // Close any stale session left by Android's MtpDevice
        try {
            sendCommand(OP_CLOSE_SESSION)
            readContainer()
            transactionId++
        } catch (_: Exception) {
        }

        // Reset transaction ID after clearing stale session
        transactionId = 1

        val result = executeSimple(OP_OPEN_SESSION, 1)
        return when (result.code) {
            RESP_OK, RESP_SESSION_ALREADY_OPEN -> null
            else -> "OpenSession failed: 0x${result.code.toString(16)}"
        }
    }

    fun closeSession() {
        try {
            executeSimple(OP_CLOSE_SESSION)
        } catch (_: Exception) {
        }
    }

    /**
     * Get supported operations from DeviceInfo.
     * Returns hex strings of supported op codes, or null on failure.
     *
     * DeviceInfo dataset layout:
     * - StandardVersion (uint16)
     * - VendorExtensionID (uint32)
     * - VendorExtensionVersion (uint16)
     * - VendorExtensionDesc (MTP string)
     * - FunctionalMode (uint16)
     * - OperationsSupported (uint16 array)
     */
    fun getSupportedOperations(): List<Int>? {
        val (data, response) = executeGetData(OP_GET_DEVICE_INFO)
        if (response.code != RESP_OK || data.size < 12) return null

        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // Skip: StandardVersion(2) + VendorExtensionID(4) + VendorExtensionVersion(2)
        bb.position(8)

        // Skip VendorExtensionDesc (MTP string)
        if (bb.remaining() < 1) return null
        val descLen = bb.get().toInt() and 0xFF
        if (descLen > 0) {
            val skipBytes = descLen * 2  // UTF-16LE chars including null
            if (bb.remaining() < skipBytes) return null
            bb.position(bb.position() + skipBytes)
        }

        // Skip FunctionalMode (uint16)
        if (bb.remaining() < 2) return null
        bb.getShort()

        // OperationsSupported: uint32 count, then uint16[] ops
        if (bb.remaining() < 4) return null
        val opCount = bb.getInt()
        val ops = mutableListOf<Int>()
        for (i in 0 until opCount) {
            if (bb.remaining() < 2) break
            ops.add(bb.getShort().toInt() and 0xFFFF)
        }
        return ops
    }

    /**
     * Get vendor extension description from DeviceInfo.
     */
    fun getVendorExtension(): String? {
        val (data, response) = executeGetData(OP_GET_DEVICE_INFO)
        if (response.code != RESP_OK || data.size < 10) return null

        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val stdVersion = bb.getShort().toInt() and 0xFFFF
        val vendorId = bb.getInt()
        val vendorVersion = bb.getShort().toInt() and 0xFFFF

        // Read vendor extension description
        if (bb.remaining() < 1) return "v$stdVersion vendor=0x${vendorId.toString(16)}"
        val descLen = bb.get().toInt() and 0xFF
        val desc = if (descLen > 0 && bb.remaining() >= descLen * 2) {
            val chars = CharArray(descLen - 1) // -1 for null terminator
            for (i in 0 until descLen - 1) {
                chars[i] = bb.getShort().toInt().toChar()
            }
            bb.getShort() // skip null terminator
            String(chars)
        } else ""

        return "MTP$stdVersion vendor=0x${vendorId.toString(16)} " +
                "vendorVer=$vendorVersion ext=\"$desc\""
    }

    /**
     * Get a summary of device capabilities for diagnostics.
     */
    fun getCapabilitySummary(): String {
        val vendorInfo = getVendorExtension() ?: ""
        val ops = getSupportedOperations()
            ?: return "Could not read DeviceInfo"

        val hasSendObjectInfo = ops.contains(OP_SEND_OBJECT_INFO)
        val hasSendObject = ops.contains(OP_SEND_OBJECT)

        val writeOps = ops.filter { it in 0x100C..0x1020 }
            .joinToString(", ") { "0x${it.toString(16)}" }

        return "$vendorInfo | SendObjInfo=$hasSendObjectInfo SendObj=$hasSendObject " +
                "writeOps=[$writeOps] totalOps=${ops.size}"
    }

    fun getStorageIds(): IntArray? {
        val (data, response) = executeGetData(OP_GET_STORAGE_IDS)
        if (response.code != RESP_OK || data.size < 4) return null

        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val count = bb.getInt()
        val ids = IntArray(count)
        for (i in 0 until count) {
            if (bb.remaining() < 4) break
            ids[i] = bb.getInt()
        }
        return ids
    }

    fun getObjectHandles(
        storageId: Int,
        format: Int = 0,
        parent: Int = -1
    ): IntArray? {
        val (data, response) = executeGetData(
            OP_GET_OBJECT_HANDLES, storageId, format, parent
        )
        if (response.code != RESP_OK || data.size < 4) return null

        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val count = bb.getInt()
        val handles = IntArray(count)
        for (i in 0 until count) {
            if (bb.remaining() < 4) break
            handles[i] = bb.getInt()
        }
        return handles
    }

    /**
     * Read just the filename from an ObjectInfo dataset.
     */
    fun getObjectName(handle: Int): String? {
        val (data, response) = executeGetData(OP_GET_OBJECT_INFO, handle)
        if (response.code != RESP_OK || data.size < 53) return null

        // ObjectInfo dataset layout:
        // Offset 0: StorageID (4)
        // Offset 4: ObjectFormat (2)
        // Offset 6: ProtectionStatus (2)
        // Offset 8: ObjectCompressedSize (4)
        // Offset 12: ThumbFormat (2)
        // Offset 14: ThumbCompressedSize (4)
        // Offset 18: ThumbPixWidth (4)
        // Offset 22: ThumbPixHeight (4)
        // Offset 26: ImagePixWidth (4)
        // Offset 30: ImagePixHeight (4)
        // Offset 34: ImageBitDepth (4)
        // Offset 38: ParentObject (4)
        // Offset 42: AssociationType (2)
        // Offset 44: AssociationDesc (4)
        // Offset 48: SequenceNumber (4)
        // Offset 52: Filename (MTP string)
        return readMtpString(data, 52)
    }

    /**
     * Send a file to the device.
     * Tries two approaches:
     * 1. Combined command+data in a single USB transfer
     * 2. Standard separate transfers with extended timeout
     *
     * @return Pair of (success, statusMessage)
     */
    fun sendFile(
        storageId: Int,
        parentHandle: Int,
        fileName: String,
        fileData: ByteArray
    ): Pair<Boolean, String> {
        val objectInfo = buildObjectInfoDataset(
            fileName = fileName,
            fileSize = fileData.size
        )

        // Approach 1: Combined command+data as single USB transfer
        // Some MTP responders parse containers from a stream and handle
        // single-transfer better than split transfers
        val combinedResult = trySendObjectInfoCombined(
            storageId, parentHandle, objectInfo
        )
        if (combinedResult != null) {
            // SendObjectInfo succeeded — now send the actual file data
            val assignedHandle = combinedResult
            sendCommand(OP_SEND_OBJECT)
            sendDataPhase(OP_SEND_OBJECT, fileData)
            val objResponse = readContainer()
            transactionId++

            if (objResponse.code != RESP_OK) {
                return false to "SendObject failed: 0x${objResponse.code.toString(16)}"
            }
            return true to "OK (handle=$assignedHandle)"
        }

        // Approach 2: Standard separate transfers with 30s timeout
        val stdResult = trySendObjectInfoStandard(
            storageId, parentHandle, objectInfo
        )
        if (stdResult != null) {
            val assignedHandle = stdResult
            sendCommand(OP_SEND_OBJECT)
            sendDataPhase(OP_SEND_OBJECT, fileData)
            val objResponse = readContainer()
            transactionId++

            if (objResponse.code != RESP_OK) {
                return false to "SendObject failed: 0x${objResponse.code.toString(16)}"
            }
            return true to "OK (handle=$assignedHandle)"
        }

        return false to "Garmin blocked write (combined=$lastCombinedDetail standard=$lastStandardDetail)"
    }

    private var lastCombinedDetail = ""
    private var lastStandardDetail = ""

    /**
     * Try SendObjectInfo with command+data concatenated into one USB transfer.
     * Returns assigned object handle on success, null on failure.
     */
    private fun trySendObjectInfoCombined(
        storageId: Int,
        parentHandle: Int,
        objectInfo: ByteArray
    ): Int? {
        // Build command container
        val cmdParams = intArrayOf(storageId, parentHandle)
        val cmdLen = 12 + cmdParams.size * 4
        val dataLen = 12 + objectInfo.size
        val totalLen = cmdLen + dataLen

        val buf = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN)

        // Command container
        buf.putInt(cmdLen)
        buf.putShort(CONTAINER_COMMAND.toShort())
        buf.putShort(OP_SEND_OBJECT_INFO.toShort())
        buf.putInt(transactionId)
        for (p in cmdParams) buf.putInt(p)

        // Data container (same transaction ID)
        buf.putInt(dataLen)
        buf.putShort(CONTAINER_DATA.toShort())
        buf.putShort(OP_SEND_OBJECT_INFO.toShort())
        buf.putInt(transactionId)
        buf.put(objectInfo)

        val sent = connection.bulkTransfer(bulkOut, buf.array(), totalLen, timeout)
        if (sent < 0) {
            lastCombinedDetail = "send=$sent"
            transactionId++
            return null
        }

        // Read response with extended timeout
        val respBuf = ByteArray(16384)
        val bytesRead = connection.bulkTransfer(bulkIn, respBuf, respBuf.size, 30000)

        if (bytesRead < 12) {
            lastCombinedDetail = "sent=$sent read=$bytesRead"
            // Clear state for next attempt
            clearHalt(bulkIn)
            clearHalt(bulkOut)
            transactionId++
            return null
        }

        val bb = ByteBuffer.wrap(respBuf, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN)
        bb.getInt() // container length
        val containerType = bb.getShort().toInt() and 0xFFFF
        val code = bb.getShort().toInt() and 0xFFFF
        bb.getInt() // transaction ID

        if (containerType != CONTAINER_RESPONSE || code != RESP_OK) {
            lastCombinedDetail = "type=$containerType code=0x${code.toString(16)}"
            transactionId++
            return null
        }

        // Extract assigned handle from response params
        val paramCount = (bytesRead - 12) / 4
        val handle = if (paramCount >= 3) {
            bb.getInt(); bb.getInt(); bb.getInt()  // StorageID, ParentHandle, ObjectHandle
        } else 0
        transactionId++
        return handle
    }

    /**
     * Try SendObjectInfo with standard separate transfers and extended 30s read timeout.
     * Returns assigned object handle on success, null on failure.
     */
    private fun trySendObjectInfoStandard(
        storageId: Int,
        parentHandle: Int,
        objectInfo: ByteArray
    ): Int? {
        val cmdResult = sendCommand(OP_SEND_OBJECT_INFO, storageId, parentHandle)
        if (cmdResult < 0) {
            lastStandardDetail = "cmd=$cmdResult"
            transactionId++
            return null
        }
        val dataResult = sendDataPhase(OP_SEND_OBJECT_INFO, objectInfo)
        if (dataResult < 0) {
            lastStandardDetail = "data=$dataResult"
            transactionId++
            return null
        }

        // Extended 30s timeout for slow responders
        val respBuf = ByteArray(16384)
        val bytesRead = connection.bulkTransfer(bulkIn, respBuf, respBuf.size, 30000)

        if (bytesRead < 12) {
            lastStandardDetail = "cmd=$cmdResult data=$dataResult read=$bytesRead"
            clearHalt(bulkIn)
            clearHalt(bulkOut)
            transactionId++
            return null
        }

        val bb = ByteBuffer.wrap(respBuf, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN)
        bb.getInt()
        val containerType = bb.getShort().toInt() and 0xFFFF
        val code = bb.getShort().toInt() and 0xFFFF
        bb.getInt()

        if (containerType != CONTAINER_RESPONSE || code != RESP_OK) {
            lastStandardDetail = "type=$containerType code=0x${code.toString(16)}"
            transactionId++
            return null
        }

        val paramCount = (bytesRead - 12) / 4
        val handle = if (paramCount >= 3) {
            bb.getInt(); bb.getInt(); bb.getInt()
        } else 0
        transactionId++
        return handle
    }

    // ── Helpers ─────────────────────────────────────────────

    private fun buildObjectInfoDataset(
        fileName: String,
        fileSize: Int
    ): ByteArray {
        val nameBytes = encodeMtpString(fileName)
        val emptyString = encodeMtpString("")

        // Fixed part: 52 bytes + Filename + DateCreated + DateModified + Keywords
        val size = 52 + nameBytes.size + emptyString.size * 3
        val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

        buf.putInt(0)                    // StorageID (0 = use command param)
        buf.putShort(0x3000.toShort())   // ObjectFormat: Undefined
        buf.putShort(0x0000.toShort())   // ProtectionStatus: No protection
        buf.putInt(fileSize)             // ObjectCompressedSize
        buf.putShort(0x0000.toShort())   // ThumbFormat
        buf.putInt(0)                    // ThumbCompressedSize
        buf.putInt(0)                    // ThumbPixWidth
        buf.putInt(0)                    // ThumbPixHeight
        buf.putInt(0)                    // ImagePixWidth
        buf.putInt(0)                    // ImagePixHeight
        buf.putInt(0)                    // ImageBitDepth
        buf.putInt(0)                    // ParentObject (0 = use command param)
        buf.putShort(0x0000.toShort())   // AssociationType
        buf.putInt(0)                    // AssociationDesc
        buf.putInt(0)                    // SequenceNumber
        buf.put(nameBytes)               // Filename
        buf.put(emptyString)             // DateCreated
        buf.put(emptyString)             // DateModified
        buf.put(emptyString)             // Keywords

        return buf.array()
    }

    /**
     * Encode a string in MTP format:
     * 1 byte: number of chars (including null terminator), or 0 for empty
     * Then UTF-16LE encoded chars + null terminator (2 bytes of 0x00)
     */
    private fun encodeMtpString(s: String): ByteArray {
        if (s.isEmpty()) {
            return byteArrayOf(0)
        }
        val chars = s.toCharArray()
        // numChars includes the null terminator
        val numChars = chars.size + 1
        val buf = ByteBuffer.allocate(1 + numChars * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
        buf.put(numChars.toByte())
        for (c in chars) {
            buf.putShort(c.code.toShort())
        }
        buf.putShort(0) // null terminator
        return buf.array()
    }

    /**
     * Read an MTP string from a byte array at the given offset.
     */
    private fun readMtpString(data: ByteArray, offset: Int): String? {
        if (offset >= data.size) return null
        val numChars = data[offset].toInt() and 0xFF
        if (numChars == 0) return ""
        val strStart = offset + 1
        // numChars includes null terminator, so actual chars = numChars - 1
        val charCount = numChars - 1
        if (strStart + charCount * 2 > data.size) return null

        val bb = ByteBuffer.wrap(data, strStart, charCount * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
        val chars = CharArray(charCount)
        for (i in 0 until charCount) {
            chars[i] = bb.getShort().toInt().toChar()
        }
        return String(chars)
    }

    /**
     * Navigate to a named subfolder within a parent.
     * Returns the handle of the subfolder, or null if not found.
     */
    fun findChildFolder(
        storageId: Int,
        parentHandle: Int,
        folderName: String
    ): Int? {
        val handles = getObjectHandles(storageId, 0, parentHandle)
            ?: return null
        for (handle in handles) {
            val name = getObjectName(handle) ?: continue
            if (name.equals(folderName, ignoreCase = true)) {
                return handle
            }
        }
        return null
    }
}
