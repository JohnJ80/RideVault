package com.example.ridevault

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.mtp.MtpDevice
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ridevault.ui.theme.RideVaultTheme
import kotlin.concurrent.thread

data class FitFileInfo(
    val handle: Int,
    val name: String,
    val sizeBytes: Long,
    val modifiedEpochSeconds: Long
)

class MainActivity : ComponentActivity() {

    private val garminVendorId = 0x091e
    private val usbPermissionAction = "com.example.ridevault.USB_PERMISSION"

    private lateinit var usbManager: UsbManager

    private var connectedDevice by mutableStateOf<UsbDevice?>(null)
    private var hasUsbPermission by mutableStateOf(false)
    private var usbInspectionReport by mutableStateOf("")
    private var usbConnectionStatus by mutableStateOf("USB connection not opened")
    private var mtpStatus by mutableStateOf("MTP not opened")
    private var mtpBusy by mutableStateOf(false)

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                usbPermissionAction -> {
                    val device: UsbDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }

                    val granted =
                        intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                    if (device != null && granted) {
                        connectedDevice = device
                        hasUsbPermission = true
                        usbConnectionStatus = "USB ready"
                    } else {
                        refreshUsbState()
                    }
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    refreshUsbState()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        usbManager = getSystemService(USB_SERVICE) as UsbManager

        val filter = IntentFilter().apply {
            addAction(usbPermissionAction)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(usbReceiver, filter)
        }

        refreshUsbState()

        setContent {
            RideVaultTheme {
                RideVaultHome(
                    device = connectedDevice,
                    hasPermission = hasUsbPermission,
                    usbConnectionStatus = usbConnectionStatus,
                    mtpStatus = mtpStatus,
                    mtpBusy = mtpBusy,
                    onRequestPermission = { requestUsbPermission() },
                    onOpenMtpSession = { openMtpSessionInBackground() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUsbState()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
    }

    private fun refreshUsbState() {
        val device = findGarminDevice()
        connectedDevice = device
        usbInspectionReport = device?.let { UsbInspector.inspect(it) } ?: ""
        hasUsbPermission = device?.let { usbManager.hasPermission(it) } ?: false

        if (device == null) {
            usbConnectionStatus = "USB not connected"
            mtpStatus = "MTP not opened"
            mtpBusy = false
        } else if (hasUsbPermission) {
            usbConnectionStatus = "USB ready"
        } else {
            usbConnectionStatus = "USB permission required"
        }
    }

    private fun findGarminDevice(): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull { device ->
            device.vendorId == garminVendorId
        }
    }

    private fun requestUsbPermission() {
        val device = connectedDevice ?: return

        val permissionIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(usbPermissionAction),
            PendingIntent.FLAG_IMMUTABLE
        )

        usbManager.requestPermission(device, permissionIntent)
    }


    private fun openMtpSessionInBackground() {
        if (mtpBusy) return

        mtpBusy = true
        mtpStatus = "Starting MTP..."

        thread(start = true) {
            val result = openMtpSessionWorker()

            runOnUiThread {
                mtpStatus = result
                mtpBusy = false
            }
        }
    }

    private fun openMtpSessionWorker(): String {
        val device = connectedDevice ?: return "No Garmin device connected"

        if (!usbManager.hasPermission(device)) {
            return "USB permission required"
        }

        runOnUiThread { mtpStatus = "Opening USB connection..." }

        val connection = usbManager.openDevice(device)
            ?: return "Could not open USB connection"

        val mtpDevice = MtpDevice(device)

        try {
            runOnUiThread { mtpStatus = "Opening MTP session..." }

            val opened = mtpDevice.open(connection)

            if (!opened) {
                return "Could not open MTP session"
            }

            runOnUiThread { mtpStatus = "Reading DeviceInfo..." }

            var info = mtpDevice.deviceInfo
            var retryCount = 0

            while (info == null && retryCount < 4) {
                Thread.sleep(250)
                retryCount++
                runOnUiThread {
                    mtpStatus = "Reading DeviceInfo... retry $retryCount"
                }
                info = mtpDevice.deviceInfo
            }

            if (info == null) {
                return "MTP opened, but no DeviceInfo after retries"
            }

            runOnUiThread { mtpStatus = "Reading storage IDs..." }

            var storageIds = mtpDevice.storageIds ?: intArrayOf()
            var storageRetryCount = 0

            while (storageIds.isEmpty() && storageRetryCount < 4) {
                Thread.sleep(250)
                storageRetryCount++
                runOnUiThread {
                    mtpStatus = "Reading storage IDs... retry $storageRetryCount"
                }
                storageIds = mtpDevice.storageIds ?: intArrayOf()
            }

            if (storageIds.isEmpty()) {
                return "MTP: ${info.manufacturer} ${info.model}; storages=0 after retries"
            }

            val storageId = storageIds[0]

            runOnUiThread { mtpStatus = "Reading StorageInfo..." }

            val storageInfo = mtpDevice.getStorageInfo(storageId)

            runOnUiThread {
                mtpStatus = "Reading root object handles..."
            }

            val rootHandles =
                mtpDevice.getObjectHandles(storageId, 0, -1) ?: intArrayOf()

            val rootObjects = mutableListOf<Pair<Int, String>>()

            for (handle in rootHandles) {
                val objectInfo = mtpDevice.getObjectInfo(handle)
                if (objectInfo != null) {
                    rootObjects.add(handle to objectInfo.name)
                }
            }

            val rootSummary = rootObjects
                .take(5)
                .mapIndexed { index, item ->
                    "${index + 1}: ${item.second}"
                }
                .joinToString("; ")

            val garminHandle = rootObjects
                .firstOrNull { it.second.equals("Garmin", ignoreCase = true) }
                ?.first

            if (garminHandle == null) {
                return "MTP: ${info.manufacturer} ${info.model}; " +
                        "storage=${storageInfo?.description ?: "unknown"}; " +
                        "root=${rootHandles.size}; " +
                        rootSummary + "; Garmin folder not found"
            }

            runOnUiThread {
                mtpStatus = "Reading Garmin folder..."
            }

            val garminHandles =
                mtpDevice.getObjectHandles(storageId, 0, garminHandle) ?: intArrayOf()

            val garminObjects = mutableListOf<Pair<Int, String>>()

            for (handle in garminHandles) {
                val objectInfo = mtpDevice.getObjectInfo(handle)
                if (objectInfo != null) {
                    garminObjects.add(handle to objectInfo.name)
                }
            }

            val garminSummary = garminObjects
                .take(12)
                .joinToString("; ") { it.second }

            val activitiesHandle = garminObjects
                .firstOrNull { it.second.equals("Activities", ignoreCase = true) }
                ?.first

            if (activitiesHandle == null) {
                return "MTP: ${info.manufacturer} ${info.model}; " +
                        "storage=${storageInfo?.description ?: "unknown"}; " +
                        "root=${rootHandles.size}; " +
                        rootSummary + "; " +
                        "Garmin children=${garminHandles.size}; " +
                        garminSummary + "; Activities folder not found"
            }

            runOnUiThread {
                mtpStatus = "Reading Activities folder..."
            }

            val activityHandles =
                mtpDevice.getObjectHandles(storageId, 0, activitiesHandle) ?: intArrayOf()

            val fitFiles = mutableListOf<FitFileInfo>()

            for (handle in activityHandles) {
                val objectInfo = mtpDevice.getObjectInfo(handle)

                if (objectInfo != null && objectInfo.name.endsWith(".fit", ignoreCase = true)) {
                    fitFiles.add(
                        FitFileInfo(
                            handle = handle,
                            name = objectInfo.name,
                            sizeBytes = objectInfo.compressedSize.toLong(),
                            modifiedEpochSeconds = objectInfo.dateModified
                        )
                    )
                }
            }

            fitFiles.sortByDescending { it.name }

            val newestSummary = fitFiles
                .take(5)
                .joinToString("; ") { file ->
                    "${file.name} (${formatFileSize(file.sizeBytes)})"
                }

            val oldestSummary = fitFiles
                .takeLast(5)
                .reversed()
                .joinToString("; ") { file ->
                    "${file.name} (${formatFileSize(file.sizeBytes)})"
                }

            val zeroByteCount = fitFiles.count { it.sizeBytes == 0L }
            val verySmallCount = fitFiles.count { it.sizeBytes in 1L..1023L }

            return "MTP: ${info.manufacturer} ${info.model}; " +
                    "storage=${storageInfo?.description ?: "unknown"}; " +
                    "FIT files=${fitFiles.size}; " +
                    "zero-byte=$zeroByteCount; under-1KB=$verySmallCount; " +
                    "newest: $newestSummary; " +
                    "oldest: $oldestSummary"

        } catch (e: Exception) {
            return "MTP exception: ${e.javaClass.simpleName}"
        } finally {
            try {
                mtpDevice.close()
            } catch (_: Exception) {
            }

            connection.close()
        }
    }
}

@Composable
fun RideVaultHome(
    device: UsbDevice?,
    hasPermission: Boolean,
    usbConnectionStatus: String,
    mtpStatus: String,
    mtpBusy: Boolean,
    onRequestPermission: () -> Unit,
    onOpenMtpSession: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "RideVault",
                style = MaterialTheme.typography.headlineLarge
            )

            Text(
                text = "Rev 0.0.12",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (device == null) {
                Text(
                    text = "No cycling computer connected",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Connect your Edge with USB-C.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = "Cycling computer connected",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (hasPermission) {
                    Text(
                        text = "USB permission granted",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = usbConnectionStatus,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onOpenMtpSession,
                        enabled = !mtpBusy
                    ) {
                        Text(if (mtpBusy) "MTP Working..." else "Open MTP Session")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = mtpStatus,
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    Button(onClick = onRequestPermission) {
                        Text("Grant USB Permission")
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceInfoTable(device: UsbDevice) {
    Column(horizontalAlignment = Alignment.Start) {
        InfoRow("Device name", device.deviceName)
        InfoRow("Vendor ID", "0x${device.vendorId.toString(16)}")
        InfoRow("Product ID", "0x${device.productId.toString(16)}")
        InfoRow("Class", device.deviceClass.toString())
        InfoRow("Subclass", device.deviceSubclass.toString())
        InfoRow("Protocol", device.deviceProtocol.toString())
        InfoRow("Interfaces", device.interfaceCount.toString())
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}


fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000L -> String.format("%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000L -> String.format("%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}
