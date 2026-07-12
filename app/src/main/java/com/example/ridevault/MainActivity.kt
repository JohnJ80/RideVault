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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ridevault.ui.theme.RideVaultTheme
import kotlin.concurrent.thread
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class FitFileInfo(
    val handle: Int,
    val name: String,
    val sizeBytes: Long,
    val modifiedEpochSeconds: Long
)

data class DownloadSummary(
    val files: List<FitFileInfo>,
    val verifiedCount: Int
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
    private var fitFiles by mutableStateOf<List<FitFileInfo>>(emptyList())
    private var activityScanCurrent by mutableIntStateOf(0)
    private var activityScanTotal by mutableIntStateOf(0)
    private var downloadBusy by mutableStateOf(false)
    private var downloadStatus by mutableStateOf("")
    private var downloadCompleteSummary by mutableStateOf<DownloadSummary?>(null)

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
                    activityScanCurrent = activityScanCurrent,
                    activityScanTotal = activityScanTotal,
                    downloadBusy = downloadBusy,
                    downloadStatus = downloadStatus,
                    downloadCompleteSummary = downloadCompleteSummary,
                    onRequestPermission = { requestUsbPermission() },
                    onOpenMtpSession = { openMtpSessionInBackground() },
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
                    onDismissDownloadComplete = {
                        downloadCompleteSummary = null
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

            return if (unreadableCount == 0) {
                "Loaded ${fitFiles.size} activities"
            } else {
                "Loaded ${fitFiles.size} activities; " +
                        "$unreadableCount objects could not be read"
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

@Composable
fun RideVaultHome(
    device: UsbDevice?,
    hasPermission: Boolean,
    usbConnectionStatus: String,
    mtpStatus: String,
    mtpBusy: Boolean,
    fitFiles: List<FitFileInfo>,
    activityScanCurrent: Int,
    activityScanTotal: Int,
    downloadBusy: Boolean,
    downloadStatus: String,
    downloadCompleteSummary: DownloadSummary?,
    onRequestPermission: () -> Unit,
    onOpenMtpSession: () -> Unit,
    onDownloadActivity: (FitFileInfo) -> Unit,
    onDownloadActivityAndNewer: (FitFileInfo) -> Unit,
    onDismissDownloadComplete: () -> Unit,
    onOpenDownloadFolder: () -> Unit
) {
    var selectedFile by remember {
        mutableStateOf<FitFileInfo?>(null)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RideVault",
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = "Rev 0.0.21",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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
            } else if (!hasPermission) {
                Text(
                    text = "Cycling computer connected",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = onRequestPermission) {
                    Text("Grant USB Permission")
                }
            } else {
                Text(
                    text = "Garmin Edge 1050",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onOpenMtpSession,
                    enabled = !mtpBusy
                ) {
                    Text(
                        if (mtpBusy) "Loading..."
                        else "Refresh"
                    )
                }

                if (downloadStatus.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = downloadStatus,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (mtpBusy && activityScanTotal > 0) {
                    val scanProgress =
                        activityScanCurrent.toFloat() /
                                activityScanTotal.toFloat()

                    Text(
                        text =
                            "$activityScanCurrent of " +
                                    "$activityScanTotal activities",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { scanProgress },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = mtpStatus,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (fitFiles.isEmpty()) {
                    Text(
                        text = mtpStatus,
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    Text(
                        text = "${fitFiles.size} Activities",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = fitFiles,
                            key = { it.handle }
                        ) { file ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        enabled = !downloadBusy && !mtpBusy
                                    ) {
                                        selectedFile = file
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = 12.dp,
                                            vertical = 6.dp
                                        ),
                                    horizontalArrangement =
                                        Arrangement.SpaceBetween,
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formatFitTimestamp(file.name),
                                        style =
                                            MaterialTheme.typography.bodyLarge
                                    )

                                    Text(
                                        text =
                                            formatFileSize(file.sizeBytes),
                                        style =
                                            MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedFile?.let { file ->
        AlertDialog(
            onDismissRequest = { selectedFile = null },
            title = {
                Text("Activity")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row {
                        Text(
                            text = "Activity time:",
                            modifier = Modifier.width(110.dp)
                        )
                        Text(formatFitTimestamp(file.name))
                    }

                    Row {
                        Text(
                            text = "Filename:",
                            modifier = Modifier.width(110.dp)
                        )
                        Text(file.name)
                    }

                    Row {
                        Text(
                            text = "Size:",
                            modifier = Modifier.width(110.dp)
                        )
                        Text(formatFileSize(file.sizeBytes))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            selectedFile = null
                            onDownloadActivityAndNewer(file)
                        },
                        enabled = !downloadBusy && !mtpBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Download this activity and newer")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedFile = null
                        onDownloadActivity(file)
                    },
                    enabled = !downloadBusy && !mtpBusy
                ) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { selectedFile = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    downloadCompleteSummary?.let { summary ->
        val files = summary.files
        val newest = files.first()
        val oldest = files.last()

        AlertDialog(
            onDismissRequest = onDismissDownloadComplete,
            title = {
                Text("Download Complete")
            },
            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    if (files.size == 1) {
                        Row {
                            Text(
                                text = "Activity time:",
                                modifier = Modifier.width(110.dp)
                            )
                            Text(
                                formatFitTimestamp(newest.name)
                            )
                        }

                        Row {
                            Text(
                                text = "Filename:",
                                modifier = Modifier.width(110.dp)
                            )
                            Text(newest.name)
                        }

                        Row {
                            Text(
                                text = "Size:",
                                modifier = Modifier.width(110.dp)
                            )
                            Text(
                                formatFileSize(
                                    newest.sizeBytes
                                )
                            )
                        }
                    } else {
                        Text(
                            "${files.size} activities downloaded"
                        )

                        Row {
                            Text(
                                text = "Newest:",
                                modifier = Modifier.width(80.dp)
                            )
                            Text(
                                formatFitTimestamp(newest.name)
                            )
                        }

                        Row {
                            Text(
                                text = "Oldest:",
                                modifier = Modifier.width(80.dp)
                            )
                            Text(
                                formatFitTimestamp(oldest.name)
                            )
                        }

                        Text(
                            "${summary.verifiedCount} of " +
                                    "${files.size} files verified"
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDismissDownloadComplete()
                        onOpenDownloadFolder()
                    }
                ) {
                    Text("Open Folder")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissDownloadComplete
                ) {
                    Text("OK")
                }
            }
        )
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


fun formatFitTimestamp(filename: String): String {
    return try {
        val timestamp = filename.removeSuffix(".fit")
        val parsed = LocalDateTime.parse(
            timestamp,
            DateTimeFormatter.ofPattern(
                "yyyy-MM-dd-HH-mm-ss",
                Locale.US
            )
        )
        parsed.format(
            DateTimeFormatter.ofPattern(
                "yyyy-MM-dd HH:mm:ss",
                Locale.US
            )
        )
    } catch (_: Exception) {
        filename.removeSuffix(".fit")
    }
}
