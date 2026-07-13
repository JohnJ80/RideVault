package com.example.ridevault

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.DocumentsContract
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.mtp.MtpDevice
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.example.ridevault.ui.theme.RideVaultTheme
import kotlin.concurrent.thread

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
    private var fitFiles by mutableStateOf<List<FitFileInfo>>(emptyList())
    private var courseFiles by mutableStateOf<List<CourseFileInfo>>(emptyList())
    private var activityScanCurrent by mutableIntStateOf(0)
    private var activityScanTotal by mutableIntStateOf(0)
    private var downloadBusy by mutableStateOf(false)
    private var downloadStatus by mutableStateOf("")
    private var downloadCompleteSummary by mutableStateOf<DownloadSummary?>(null)
    private var deleteBusy by mutableStateOf(false)
    private var deleteStatus by mutableStateOf("")
    private var deleteCompleteCount by mutableIntStateOf(0)


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
                    fitFiles = fitFiles,
                    courseFiles = courseFiles,
                    activityScanCurrent = activityScanCurrent,
                    activityScanTotal = activityScanTotal,
                    downloadBusy = downloadBusy,
                    downloadStatus = downloadStatus,
                    downloadCompleteSummary = downloadCompleteSummary,
                    deleteBusy = deleteBusy,
                    deleteStatus = deleteStatus,
                    deleteCompleteCount = deleteCompleteCount,
                    onRequestPermission = { requestUsbPermission() },
                    onOpenMtpSession = {
                        openMtpSessionInBackground()
                    },
                    onDownloadActivity = { file ->
                        downloadActivitiesInBackground(listOf(file))
                    },
                    onDownloadActivityAndNewer = { boundaryFile ->
                        val filesToDownload =
                            fitFiles.takeWhile { file ->
                                file.name >= boundaryFile.name
                            }

                        downloadActivitiesInBackground(filesToDownload)
                    },
                    onDeleteActivityAndOlder = { boundaryFile ->
                        val filesToDelete =
                            fitFiles.filter { file ->
                                file.name <= boundaryFile.name
                            }

                        deleteActivitiesInBackground(filesToDelete)
                    },
                    onDismissDownloadComplete = {
                        downloadCompleteSummary = null
                    },
                    onDismissDeleteComplete = {
                        deleteCompleteCount = 0
                    },
                    onOpenDownloadFolder = {
                        openActivitiesDownloadFolder()
                    }
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
            fitFiles = emptyList()
            courseFiles = emptyList()
            activityScanCurrent = 0
            activityScanTotal = 0
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
        activityScanCurrent = 0
        activityScanTotal = 0

        thread(start = true) {
            val result = openMtpSessionWorker()

            runOnUiThread {
                mtpStatus = result
                mtpBusy = false
            }
        }
    }

    private fun downloadActivitiesInBackground(
        files: List<FitFileInfo>
    ) {
        if (downloadBusy || mtpBusy || files.isEmpty()) return

        downloadBusy = true
        downloadStatus = "Preparing download..."
        downloadCompleteSummary = null

        thread(start = true) {
            val result = downloadActivitiesWorker(files)

            runOnUiThread {
                downloadStatus = result.first
                downloadCompleteSummary = result.second
                downloadBusy = false
            }
        }
    }

    private fun deleteActivitiesInBackground(
        files: List<FitFileInfo>
    ) {
        if (
            deleteBusy ||
            mtpBusy ||
            downloadBusy ||
            files.isEmpty()
        ) {
            return
        }

        deleteBusy = true
        deleteStatus = "Preparing deletion..."
        deleteCompleteCount = 0

        thread(start = true) {
            val result = deleteActivitiesWorker(files)

            runOnUiThread {
                deleteStatus = result.first
                deleteCompleteCount = result.second
                deleteBusy = false

                if (result.second > 0) {
                    openMtpSessionInBackground()
                }
            }
        }
    }

    private fun deleteActivitiesWorker(
        files: List<FitFileInfo>
    ): Pair<String, Int> {
        val device = connectedDevice
            ?: return "No Garmin device connected" to 0

        if (!usbManager.hasPermission(device)) {
            return "USB permission required" to 0
        }

        val connection = usbManager.openDevice(device)
            ?: return "Could not open USB connection" to 0

        val mtpDevice = MtpDevice(device)

        try {
            if (!mtpDevice.open(connection)) {
                return "Could not open MTP session" to 0
            }

            var deletedCount = 0

            for ((index, file) in files.withIndex()) {
                runOnUiThread {
                    deleteStatus =
                        "Deleting ${index + 1} of ${files.size}: " +
                                file.name
                }

                if (!mtpDevice.deleteObject(file.handle)) {
                    return (
                        "Delete failed at ${index + 1} of " +
                                "${files.size}: ${file.name}"
                    ) to deletedCount
                }

                deletedCount++
            }

            return (
                "Deleted $deletedCount activities"
            ) to deletedCount

        } catch (e: Exception) {
            return (
                "Delete exception: ${e.javaClass.simpleName}"
            ) to 0
        } finally {
            try {
                mtpDevice.close()
            } catch (_: Exception) {
            }

            connection.close()
        }
    }

    private fun openActivitiesDownloadFolder() {
        val folderUri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Download/RideVault/Activities"
        )

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                folderUri,
                DocumentsContract.Document.MIME_TYPE_DIR
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

        try {
            startActivity(viewIntent)
        } catch (_: Exception) {
            val fallbackIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                putExtra(
                    DocumentsContract.EXTRA_INITIAL_URI,
                    folderUri
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            startActivity(fallbackIntent)
        }
    }

    private fun downloadActivitiesWorker(
        files: List<FitFileInfo>
    ): Pair<String, DownloadSummary?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return "Downloads require Android 10 or newer" to null
        }

        val device = connectedDevice
            ?: return "No Garmin device connected" to null

        if (!usbManager.hasPermission(device)) {
            return "USB permission required" to null
        }

        val currentPath =
            "${Environment.DIRECTORY_DOWNLOADS}/RideVault/Activities/"

        val previousPath =
            "${Environment.DIRECTORY_DOWNLOADS}/RideVault/Activities-Previous/"

        val collection =
            MediaStore.Downloads.EXTERNAL_CONTENT_URI

        try {
            rotateDownloadSet(
                collection = collection,
                currentPath = currentPath,
                previousPath = previousPath
            )
        } catch (e: Exception) {
            return (
                "Could not prepare download folders: " +
                        e.javaClass.simpleName
            ) to null
        }

        val connection = usbManager.openDevice(device)
            ?: return "Could not open USB connection" to null

        val mtpDevice = MtpDevice(device)

        try {
            if (!mtpDevice.open(connection)) {
                return "Could not open MTP session" to null
            }

            var verifiedCount = 0

            for ((index, file) in files.withIndex()) {
                runOnUiThread {
                    downloadStatus =
                        "Downloading ${index + 1} of ${files.size}: " +
                                file.name
                }

                val values = ContentValues().apply {
                    put(
                        MediaStore.Downloads.DISPLAY_NAME,
                        file.name
                    )

                    put(
                        MediaStore.Downloads.MIME_TYPE,
                        "application/octet-stream"
                    )

                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        currentPath
                    )

                    put(
                        MediaStore.Downloads.IS_PENDING,
                        1
                    )
                }

                val outputUri = contentResolver.insert(
                    collection,
                    values
                ) ?: return (
                    "Could not create destination file: " +
                            file.name
                ) to null

                val imported = try {
                    contentResolver
                        .openFileDescriptor(outputUri, "w")
                        ?.use { descriptor ->
                            mtpDevice.importFile(
                                file.handle,
                                descriptor
                            )
                        } ?: false
                } catch (_: Exception) {
                    false
                }

                if (!imported) {
                    contentResolver.delete(
                        outputUri,
                        null,
                        null
                    )

                    return (
                        "Download failed at ${index + 1} of " +
                                "${files.size}: ${file.name}"
                    ) to null
                }

                val completedValues = ContentValues().apply {
                    put(
                        MediaStore.Downloads.IS_PENDING,
                        0
                    )
                }

                contentResolver.update(
                    outputUri,
                    completedValues,
                    null,
                    null
                )

                val savedSize = contentResolver
                    .openFileDescriptor(outputUri, "r")
                    ?.use { descriptor ->
                        descriptor.statSize
                    }
                    ?: -1L

                if (
                    savedSize >= 0L &&
                    savedSize != file.sizeBytes
                ) {
                    return (
                        "Size mismatch for ${file.name}: expected " +
                                "${formatFileSize(file.sizeBytes)}, got " +
                                formatFileSize(savedSize)
                    ) to null
                }

                verifiedCount++
            }

            val summary = DownloadSummary(
                files = files,
                verifiedCount = verifiedCount
            )

            return (
                "Downloaded and verified $verifiedCount " +
                        "of ${files.size} activities"
            ) to summary

        } catch (e: Exception) {
            return (
                "Download exception: " +
                        e.javaClass.simpleName
            ) to null
        } finally {
            try {
                mtpDevice.close()
            } catch (_: Exception) {
            }

            connection.close()
        }
    }

    private fun rotateDownloadSet(
        collection: android.net.Uri,
        currentPath: String,
        previousPath: String
    ) {
        val projection = arrayOf(MediaStore.Downloads._ID)

        // Remove the download set from two sessions ago.
        contentResolver.query(
            collection,
            projection,
            "${MediaStore.Downloads.RELATIVE_PATH} = ?",
            arrayOf(previousPath),
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Downloads._ID
            )

            while (cursor.moveToNext()) {
                val itemUri =
                    android.content.ContentUris.withAppendedId(
                        collection,
                        cursor.getLong(idColumn)
                    )

                contentResolver.delete(itemUri, null, null)
            }
        }

        // Move the current download set into Activities-Previous.
        contentResolver.query(
            collection,
            projection,
            "${MediaStore.Downloads.RELATIVE_PATH} = ?",
            arrayOf(currentPath),
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Downloads._ID
            )

            while (cursor.moveToNext()) {
                val itemUri =
                    android.content.ContentUris.withAppendedId(
                        collection,
                        cursor.getLong(idColumn)
                    )

                val moveValues = ContentValues().apply {
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        previousPath
                    )
                }

                contentResolver.update(
                    itemUri,
                    moveValues,
                    null,
                    null
                )
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
                .firstOrNull {
                    it.second.equals(
                        "Activities",
                        ignoreCase = true
                    )
                }
                ?.first

            val coursesHandle = garminObjects
                .firstOrNull {
                    it.second.equals(
                        "Courses",
                        ignoreCase = true
                    )
                }
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

            val activityHandleSet = linkedSetOf<Int>()

            repeat(4) { attempt ->
                val handles =
                    mtpDevice.getObjectHandles(
                        storageId,
                        0,
                        activitiesHandle
                    ) ?: intArrayOf()

                activityHandleSet.addAll(handles.toList())

                runOnUiThread {
                    mtpStatus =
                        "Reading activity index... " +
                                "pass ${attempt + 1} of 4; " +
                                "${activityHandleSet.size} found"
                }

                if (attempt < 3) {
                    Thread.sleep(250)
                }
            }

            val activityHandles = activityHandleSet.toIntArray()
            val fitFiles = mutableListOf<FitFileInfo>()
            var unreadableCount = 0

            runOnUiThread {
                activityScanCurrent = 0
                activityScanTotal = activityHandles.size
            }

            for ((index, handle) in activityHandles.withIndex()) {
                var objectInfo = mtpDevice.getObjectInfo(handle)
                var objectRetryCount = 0

                while (objectInfo == null && objectRetryCount < 4) {
                    Thread.sleep(100)
                    objectRetryCount++
                    objectInfo = mtpDevice.getObjectInfo(handle)
                }

                if (objectInfo == null) {
                    unreadableCount++
                } else if (
                    objectInfo.name.endsWith(".fit", ignoreCase = true)
                ) {
                    fitFiles.add(
                        FitFileInfo(
                            handle = handle,
                            name = objectInfo.name,
                            sizeBytes =
                                objectInfo.compressedSize.toLong(),
                            modifiedEpochSeconds =
                                objectInfo.dateModified
                        )
                    )
                }

                runOnUiThread {
                    activityScanCurrent = index + 1
                    mtpStatus =
                        "Reading activity ${index + 1} " +
                                "of ${activityHandles.size}..."
                }
            }

            fitFiles.sortByDescending { it.name }

            runOnUiThread {
                this.fitFiles = fitFiles.toList()
            }

            val courseFiles =
                mutableListOf<CourseFileInfo>()

            var unreadableCourseCount = 0

            if (coursesHandle != null) {
                runOnUiThread {
                    mtpStatus = "Reading Courses folder..."
                }

                val courseHandleSet =
                    linkedSetOf<Int>()

                repeat(3) { attempt ->
                    val handles =
                        mtpDevice.getObjectHandles(
                            storageId,
                            0,
                            coursesHandle
                        ) ?: intArrayOf()

                    courseHandleSet.addAll(
                        handles.toList()
                    )

                    runOnUiThread {
                        mtpStatus =
                            "Reading course index... " +
                                    "pass ${attempt + 1} of 3; " +
                                    "${courseHandleSet.size} found"
                    }

                    if (attempt < 2) {
                        Thread.sleep(200)
                    }
                }

                for (handle in courseHandleSet) {
                    var objectInfo =
                        mtpDevice.getObjectInfo(handle)

                    var retryCount = 0

                    while (
                        objectInfo == null &&
                        retryCount < 4
                    ) {
                        Thread.sleep(100)
                        retryCount++
                        objectInfo =
                            mtpDevice.getObjectInfo(handle)
                    }

                    if (objectInfo == null) {
                        unreadableCourseCount++
                    } else if (
                        objectInfo.name.endsWith(
                            ".fit",
                            ignoreCase = true
                        )
                    ) {
                        courseFiles.add(
                            CourseFileInfo(
                                handle = handle,
                                name = objectInfo.name,
                                sizeBytes =
                                    objectInfo.compressedSize
                                        .toLong(),
                                modifiedEpochSeconds =
                                    objectInfo.dateModified
                            )
                        )
                    }
                }
            }

            courseFiles.sortBy {
                it.name.lowercase()
            }

            runOnUiThread {
                this.courseFiles =
                    courseFiles.toList()
            }

            val unreadableTotal =
                unreadableCount +
                        unreadableCourseCount

            return if (unreadableTotal == 0) {
                "Loaded ${fitFiles.size} activities " +
                        "and ${courseFiles.size} courses"
            } else {
                "Loaded ${fitFiles.size} activities " +
                        "and ${courseFiles.size} courses; " +
                        "$unreadableTotal objects could not be read"
            }

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
