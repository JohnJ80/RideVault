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
import android.mtp.MtpObjectInfo
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.example.ridevault.ui.theme.RideVaultTheme
import com.example.ridevault.rwgps.RwgpsApi
import com.example.ridevault.rwgps.RwgpsAuth
import com.example.ridevault.rwgps.handleRwgpsRedirect
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
    private var courseBackupBusy by mutableStateOf(false)
    private var courseBackupStatus by mutableStateOf("")
    private var courseBackupCompleteSummary by mutableStateOf<CourseBackupSummary?>(null)
    private var courseDeleteBusy by mutableStateOf(false)
    private var courseDeleteStatus by mutableStateOf("")
    private var courseDeleteCompleteCount by mutableIntStateOf(0)

    private var rwgpsLoggedIn by mutableStateOf(false)
    private var rwgpsStatus by mutableStateOf("")
    private var rwgpsCollections by mutableStateOf<List<RwgpsApi.CollectionSummary>>(emptyList())
    private var rwgpsRoutes by mutableStateOf<List<RwgpsApi.RouteSummary>>(emptyList())
    private var rwgpsSelectedCollection by mutableStateOf<RwgpsApi.CollectionSummary?>(null)
    private var rwgpsBusy by mutableStateOf(false)
    private var rwgpsBrowseStatus by mutableStateOf("")
    private var rwgpsSendStatus by mutableStateOf("")
    private var rwgpsSendBusy by mutableStateOf(false)
    private var rwgpsSendCompleteCount by mutableIntStateOf(0)

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

        rwgpsLoggedIn = RwgpsAuth.isLoggedIn(this)

        handleOauthRedirect(intent)

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
                    courseBackupBusy = courseBackupBusy,
                    courseBackupStatus = courseBackupStatus,
                    courseBackupCompleteSummary = courseBackupCompleteSummary,
                    courseDeleteBusy = courseDeleteBusy,
                    courseDeleteStatus = courseDeleteStatus,
                    courseDeleteCompleteCount = courseDeleteCompleteCount,
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
                    },
                    onBackupCourse = { file ->
                        backupCoursesInBackground(listOf(file))
                    },
                    onBackupAllCourses = {
                        backupCoursesInBackground(courseFiles)
                    },
                    onDismissCourseBackupComplete = {
                        courseBackupCompleteSummary = null
                    },
                    onOpenCoursesBackupFolder = {
                        openCoursesBackupFolder()
                    },
                    onDeleteCourse = { file ->
                        deleteCoursesInBackground(listOf(file))
                    },
                    onDeleteAllCourses = {
                        deleteCoursesInBackground(courseFiles)
                    },
                    onDismissCourseDeleteComplete = {
                        courseDeleteCompleteCount = 0
                    },
                    rwgpsLoggedIn = rwgpsLoggedIn,
                    rwgpsStatus = rwgpsStatus,
                    onRwgpsLogout = {
                        RwgpsAuth.clearToken(this@MainActivity)
                        rwgpsLoggedIn = false
                        rwgpsCollections = emptyList()
                        rwgpsRoutes = emptyList()
                        rwgpsSelectedCollection = null
                    },
                    rwgpsCollections = rwgpsCollections,
                    rwgpsRoutes = rwgpsRoutes,
                    rwgpsSelectedCollection = rwgpsSelectedCollection,
                    rwgpsBusy = rwgpsBusy,
                    rwgpsBrowseStatus = rwgpsBrowseStatus,
                    rwgpsSendStatus = rwgpsSendStatus,
                    rwgpsSendBusy = rwgpsSendBusy,
                    rwgpsSendCompleteCount = rwgpsSendCompleteCount,
                    onBrowseRwgpsCollections = {
                        loadRwgpsCollections()
                    },
                    onSelectRwgpsCollection = { collection ->
                        loadRwgpsCollectionRoutes(collection)
                    },
                    onBackFromRwgpsRoutes = {
                        rwgpsSelectedCollection = null
                        rwgpsRoutes = emptyList()
                        rwgpsBrowseStatus = ""
                    },
                    onSendRouteToGarmin = { route ->
                        sendRwgpsRouteToGarmin(route)
                    },
                    onSendAllRoutesToGarmin = {
                        sendAllRwgpsRoutesToGarmin()
                    },
                    onDismissRwgpsSendComplete = {
                        rwgpsSendCompleteCount = 0
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOauthRedirect(intent)
    }

    private fun handleOauthRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "ridevault") return

        rwgpsStatus = "Exchanging authorization code..."
        lifecycleScope.launch {
            val (success, message) = handleRwgpsRedirect(this@MainActivity, intent)
            if (success) {
                rwgpsLoggedIn = true
                rwgpsStatus = ""
            } else {
                rwgpsStatus = "RWGPS login failed: $message"
            }
        }
    }

    // ── RWGPS browsing ───────────────────────────────────────

    private fun loadRwgpsCollections() {
        val token = RwgpsAuth.getAccessToken(this) ?: return
        rwgpsBusy = true
        rwgpsBrowseStatus = "Loading collections..."
        lifecycleScope.launch {
            try {
                val collections = RwgpsApi.listCollections(token)
                rwgpsCollections = collections
                rwgpsBrowseStatus = if (collections.isEmpty()) {
                    "No collections found"
                } else {
                    ""
                }
            } catch (e: Exception) {
                rwgpsBrowseStatus = "Error: ${e.message}"
            } finally {
                rwgpsBusy = false
            }
        }
    }

    private fun loadRwgpsCollectionRoutes(collection: RwgpsApi.CollectionSummary) {
        val token = RwgpsAuth.getAccessToken(this) ?: return
        rwgpsSelectedCollection = collection
        rwgpsBusy = true
        rwgpsBrowseStatus = "Loading routes..."
        lifecycleScope.launch {
            try {
                val (status, routes) = RwgpsApi.listCollectionRoutes(token, collection.id)
                rwgpsRoutes = routes
                rwgpsBrowseStatus = if (status != "OK") {
                    status
                } else if (routes.isEmpty()) {
                    "No routes in this collection"
                } else {
                    ""
                }
            } catch (e: Exception) {
                rwgpsBrowseStatus = "Error: ${e.message}"
            } finally {
                rwgpsBusy = false
            }
        }
    }

    private fun sendRwgpsRouteToGarmin(route: RwgpsApi.RouteSummary) {
        val token = RwgpsAuth.getAccessToken(this) ?: return
        val device = connectedDevice ?: run {
            rwgpsSendStatus = "No Garmin connected"
            return
        }
        if (!hasUsbPermission) {
            rwgpsSendStatus = "USB permission required"
            return
        }

        rwgpsSendBusy = true
        rwgpsSendStatus = "Downloading ${route.name}..."

        thread {
            try {
                // Step 1: Download the route as GPX from RWGPS
                var downloadResult: Pair<String, RwgpsApi.RouteDownload?> = "" to null
                val latch = java.util.concurrent.CountDownLatch(1)
                lifecycleScope.launch {
                    downloadResult = RwgpsApi.downloadRouteGpx(token, route)
                    latch.countDown()
                }
                latch.await()

                val (dlStatus, download) = downloadResult
                if (download == null) {
                    runOnUiThread {
                        rwgpsSendStatus = "Download failed: $dlStatus"
                        rwgpsSendBusy = false
                    }
                    return@thread
                }

                runOnUiThread {
                    rwgpsSendStatus = "Sending ${download.fileName} to Garmin..."
                }

                // Step 2: Open MTP and push to Courses folder
                val mtpDevice = MtpDevice(device)
                val usbDeviceConnection = usbManager.openDevice(device)
                if (usbDeviceConnection == null || !mtpDevice.open(usbDeviceConnection)) {
                    runOnUiThread {
                        rwgpsSendStatus = "Could not open MTP connection"
                        rwgpsSendBusy = false
                    }
                    return@thread
                }

                try {
                    val storageIds = mtpDevice.storageIds
                    if (storageIds == null || storageIds.isEmpty()) {
                        runOnUiThread {
                            rwgpsSendStatus = "No storage found on Garmin"
                            rwgpsSendBusy = false
                        }
                        return@thread
                    }

                    val storageId = storageIds[0]
                    val (folderName, targetHandle) = findNewFilesFolderHandle(mtpDevice, storageId)
                    if (targetHandle == null) {
                        runOnUiThread {
                            rwgpsSendStatus = folderName // error message
                            rwgpsSendBusy = false
                        }
                        return@thread
                    }

                    runOnUiThread {
                        rwgpsSendStatus = "Sending ${download.fileName} to Garmin/$folderName..."
                    }

                    // Try sending with minimal object info (no size declared)
                    val sentInfo = sendFileToMtp(
                        mtpDevice = mtpDevice,
                        storageId = storageId,
                        parentHandle = targetHandle,
                        fileName = download.fileName,
                        data = download.data
                    )
                    if (sentInfo == null) {
                        runOnUiThread {
                            rwgpsSendStatus =
                                "Failed to create ${download.fileName} " +
                                "in $folderName (handle=$targetHandle, " +
                                "${download.data.size} bytes, storage=$storageId)"
                            rwgpsSendBusy = false
                        }
                        return@thread
                    }

                    runOnUiThread {
                        rwgpsSendStatus = "Sent ${download.fileName} to Garmin/$folderName"
                        rwgpsSendCompleteCount++
                        rwgpsSendBusy = false
                    }
                } finally {
                    try { mtpDevice.close() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                runOnUiThread {
                    rwgpsSendStatus = "${e.javaClass.simpleName}: ${e.message}"
                    rwgpsSendBusy = false
                }
            }
        }
    }

    private fun sendAllRwgpsRoutesToGarmin() {
        val token = RwgpsAuth.getAccessToken(this) ?: return
        val device = connectedDevice ?: run {
            rwgpsSendStatus = "No Garmin connected"
            return
        }
        if (!hasUsbPermission) {
            rwgpsSendStatus = "USB permission required"
            return
        }
        val routes = rwgpsRoutes
        if (routes.isEmpty()) {
            rwgpsSendStatus = "No routes to send"
            return
        }

        rwgpsSendBusy = true
        rwgpsSendStatus = "Downloading ${routes.size} routes..."

        thread {
            try {
                // Step 1: Download all routes as GPX
                val downloads = mutableListOf<RwgpsApi.RouteDownload>()
                val errors = mutableListOf<String>()

                for ((index, route) in routes.withIndex()) {
                    runOnUiThread {
                        rwgpsSendStatus =
                            "Downloading ${index + 1} of ${routes.size}: ${route.name}..."
                    }

                    var downloadResult: Pair<String, RwgpsApi.RouteDownload?> = "" to null
                    val latch = java.util.concurrent.CountDownLatch(1)
                    lifecycleScope.launch {
                        downloadResult = RwgpsApi.downloadRouteGpx(token, route)
                        latch.countDown()
                    }
                    latch.await()

                    val (dlStatus, download) = downloadResult
                    if (download != null) {
                        downloads.add(download)
                    } else {
                        errors.add("${route.name}: $dlStatus")
                    }
                }

                if (downloads.isEmpty()) {
                    runOnUiThread {
                        rwgpsSendStatus = "All downloads failed"
                        rwgpsSendBusy = false
                    }
                    return@thread
                }

                // Step 2: Open MTP and push all files to Courses folder
                runOnUiThread {
                    rwgpsSendStatus = "Connecting to Garmin..."
                }

                val mtpDevice = MtpDevice(device)
                val usbDeviceConnection = usbManager.openDevice(device)
                if (usbDeviceConnection == null || !mtpDevice.open(usbDeviceConnection)) {
                    runOnUiThread {
                        rwgpsSendStatus = "Could not open MTP connection"
                        rwgpsSendBusy = false
                    }
                    return@thread
                }

                try {
                    val storageIds = mtpDevice.storageIds
                    if (storageIds == null || storageIds.isEmpty()) {
                        runOnUiThread {
                            rwgpsSendStatus = "No storage found on Garmin"
                            rwgpsSendBusy = false
                        }
                        return@thread
                    }

                    val storageId = storageIds[0]
                    val (folderName, targetHandle) = findNewFilesFolderHandle(mtpDevice, storageId)
                    if (targetHandle == null) {
                        runOnUiThread {
                            rwgpsSendStatus = folderName // error message
                            rwgpsSendBusy = false
                        }
                        return@thread
                    }

                    var sentCount = 0
                    for ((index, download) in downloads.withIndex()) {
                        runOnUiThread {
                            rwgpsSendStatus =
                                "Sending ${index + 1} of ${downloads.size} to $folderName: ${download.fileName}..."
                        }

                        val result = sendFileToMtp(
                            mtpDevice = mtpDevice,
                            storageId = storageId,
                            parentHandle = targetHandle,
                            fileName = download.fileName,
                            data = download.data
                        )
                        if (result != null) {
                            sentCount++
                        } else {
                            errors.add("${download.fileName}: failed to create on device")
                        }
                    }

                    runOnUiThread {
                        val errorSuffix = if (errors.isNotEmpty()) {
                            " (${errors.size} failed)"
                        } else ""
                        rwgpsSendStatus = "Sent $sentCount of ${routes.size} routes$errorSuffix"
                        rwgpsSendCompleteCount += sentCount
                        rwgpsSendBusy = false
                    }
                } finally {
                    try { mtpDevice.close() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                runOnUiThread {
                    rwgpsSendStatus = "${e.javaClass.simpleName}: ${e.message}"
                    rwgpsSendBusy = false
                }
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

    private fun closeMtpSession(
        mtpDevice: MtpDevice,
        mtpOpened: Boolean
    ) {
        if (!mtpOpened) {
            return
        }

        try {
            mtpDevice.close()
        } catch (_: Exception) {
        }
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
        if (downloadBusy || mtpBusy || courseDeleteBusy || files.isEmpty()) return

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

    private fun backupCoursesInBackground(
        files: List<CourseFileInfo>
    ) {
        if (
            courseBackupBusy ||
            mtpBusy ||
            downloadBusy ||
            deleteBusy ||
            courseDeleteBusy ||
            files.isEmpty()
        ) {
            return
        }

        courseBackupBusy = true
        courseBackupStatus = "Preparing backup..."
        courseBackupCompleteSummary = null

        thread(start = true) {
            val result = backupCoursesWorker(files)

            runOnUiThread {
                courseBackupStatus = result.first
                courseBackupCompleteSummary = result.second
                courseBackupBusy = false
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
            courseBackupBusy ||
            courseDeleteBusy ||
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

    private fun deleteCoursesInBackground(
        files: List<CourseFileInfo>
    ) {
        if (
            courseDeleteBusy ||
            mtpBusy ||
            downloadBusy ||
            deleteBusy ||
            courseBackupBusy ||
            files.isEmpty()
        ) {
            return
        }

        courseDeleteBusy = true
        courseDeleteStatus = "Preparing deletion..."
        courseDeleteCompleteCount = 0

        thread(start = true) {
            val result = deleteCoursesWorker(files)

            runOnUiThread {
                courseDeleteStatus = result.first
                courseDeleteCompleteCount = result.second
                courseDeleteBusy = false

                if (result.second > 0) {
                    openMtpSessionInBackground()
                }
            }
        }
    }

    private fun deleteCoursesWorker(
        files: List<CourseFileInfo>
    ): Pair<String, Int> {
        val device = connectedDevice
            ?: return "No Garmin device connected" to 0

        if (!usbManager.hasPermission(device)) {
            return "USB permission required" to 0
        }

        val connection = usbManager.openDevice(device)
            ?: return "Could not open USB connection" to 0

        val mtpDevice = MtpDevice(device)
        var mtpOpened = false

        try {
            if (!mtpDevice.open(connection)) {
                return "Could not open MTP session" to 0
            }

            mtpOpened = true

            val storageIds = mtpDevice.storageIds ?: intArrayOf()

            if (storageIds.isEmpty()) {
                return "No MTP storage found" to 0
            }

            val storageId = storageIds[0]
            val coursesHandle = findCoursesFolderHandle(
                mtpDevice = mtpDevice,
                storageId = storageId
            ) ?: return "Courses folder not found" to 0

            val freshCourseFiles = enumerateCourseFilesOnDevice(
                mtpDevice = mtpDevice,
                storageId = storageId,
                coursesHandle = coursesHandle
            )

            val requestedNames = files
                .map { it.name.lowercase() }
                .toSet()
            val filesToDelete = freshCourseFiles.filter { file ->
                file.name.lowercase() in requestedNames
            }

            if (filesToDelete.isEmpty()) {
                return "No matching courses found on device" to 0
            }

            var deletedCount = 0

            for ((index, file) in filesToDelete.withIndex()) {
                runOnUiThread {
                    courseDeleteStatus =
                        "Deleting ${index + 1} of ${filesToDelete.size}: " +
                                file.name
                }

                if (!deleteMtpObjectWithRetry(mtpDevice, file.handle)) {
                    return (
                        "Delete failed at ${index + 1} of " +
                                "${filesToDelete.size}: ${file.name}"
                    ) to deletedCount
                }

                deletedCount++
            }

            return (
                "Deleted $deletedCount courses"
            ) to deletedCount

        } catch (e: Exception) {
            return (
                "Delete exception: ${e.javaClass.simpleName}"
            ) to 0
        } finally {
            closeMtpSession(
                mtpDevice = mtpDevice,
                mtpOpened = mtpOpened
            )
        }
    }

    private fun getMtpObjectInfoWithRetry(
        mtpDevice: MtpDevice,
        handle: Int
    ): MtpObjectInfo? {
        var objectInfo = mtpDevice.getObjectInfo(handle)
        var retryCount = 0

        while (objectInfo == null && retryCount < 4) {
            Thread.sleep(100)
            retryCount++
            objectInfo = mtpDevice.getObjectInfo(handle)
        }

        return objectInfo
    }

    private fun findGarminFolderHandle(
        mtpDevice: MtpDevice,
        storageId: Int
    ): Int? {
        val rootHandles =
            mtpDevice.getObjectHandles(storageId, 0, -1)
                ?: return null

        return rootHandles
            .toList()
            .mapNotNull { handle ->
                getMtpObjectInfoWithRetry(mtpDevice, handle)?.let { info ->
                    handle to info.name
                }
            }
            .firstOrNull {
                it.second.equals("Garmin", ignoreCase = true)
            }
            ?.first
    }

    private fun findGarminChildFolderHandle(
        mtpDevice: MtpDevice,
        storageId: Int,
        garminHandle: Int,
        folderName: String
    ): Int? {
        val garminHandles =
            mtpDevice.getObjectHandles(
                storageId,
                0,
                garminHandle
            ) ?: return null

        return garminHandles
            .toList()
            .mapNotNull { handle ->
                getMtpObjectInfoWithRetry(mtpDevice, handle)?.let { info ->
                    handle to info.name
                }
            }
            .firstOrNull {
                it.second.equals(folderName, ignoreCase = true)
            }
            ?.first
    }

    /**
     * Try to create and send a file to the Garmin via MTP.
     * Attempts multiple format/size combinations since Garmin MTP
     * implementations vary in what they accept.
     * Returns the MtpObjectInfo on success, null on failure.
     */
    private fun sendFileToMtp(
        mtpDevice: MtpDevice,
        storageId: Int,
        parentHandle: Int,
        fileName: String,
        data: ByteArray
    ): MtpObjectInfo? {
        // Try different combinations — Garmin MTP can be picky
        val attempts = listOf(
            // Attempt 1: no size, FORMAT_UNDEFINED
            Triple(0L, android.mtp.MtpConstants.FORMAT_UNDEFINED, "no-size/undefined"),
            // Attempt 2: with size, FORMAT_UNDEFINED
            Triple(data.size.toLong(), android.mtp.MtpConstants.FORMAT_UNDEFINED, "sized/undefined"),
            // Attempt 3: no size, FORMAT_TEXT
            Triple(0L, android.mtp.MtpConstants.FORMAT_TEXT, "no-size/text"),
            // Attempt 4: with size, FORMAT_TEXT
            Triple(data.size.toLong(), android.mtp.MtpConstants.FORMAT_TEXT, "sized/text"),
        )

        for ((compressedSize, format, label) in attempts) {
            val builder = MtpObjectInfo.Builder()
                .setStorageId(storageId)
                .setParent(parentHandle)
                .setName(fileName)
                .setFormat(format)

            if (compressedSize > 0) {
                builder.setCompressedSize(compressedSize)
            }

            val objectInfo = builder.build()
            val sentInfo = mtpDevice.sendObjectInfo(objectInfo)

            if (sentInfo != null) {
                // sendObjectInfo succeeded — now send the data
                val tempFile = java.io.File(cacheDir, "rwgps_temp.gpx")
                try {
                    tempFile.writeBytes(data)

                    val pfd = android.os.ParcelFileDescriptor.open(
                        tempFile,
                        android.os.ParcelFileDescriptor.MODE_READ_ONLY
                    )

                    val success = pfd.use {
                        mtpDevice.sendObject(
                            sentInfo.objectHandle,
                            data.size.toLong(),
                            it
                        )
                    }

                    if (success) {
                        return sentInfo
                    }
                } finally {
                    tempFile.delete()
                }
            }

            Thread.sleep(100)
        }

        return null
    }

    private fun findCoursesFolderHandle(
        mtpDevice: MtpDevice,
        storageId: Int
    ): Int? {
        val garminHandle = findGarminFolderHandle(
            mtpDevice = mtpDevice,
            storageId = storageId
        ) ?: return null

        return findGarminChildFolderHandle(
            mtpDevice = mtpDevice,
            storageId = storageId,
            garminHandle = garminHandle,
            folderName = "Courses"
        )
    }

    /**
     * Find the NewFiles folder on the Garmin for importing GPX courses.
     * The Garmin processes files placed here on next reboot.
     * Falls back to Courses folder if NewFiles not found.
     */
    private fun findNewFilesFolderHandle(
        mtpDevice: MtpDevice,
        storageId: Int
    ): Pair<String, Int?> {
        val garminHandle = findGarminFolderHandle(
            mtpDevice = mtpDevice,
            storageId = storageId
        ) ?: return "Garmin folder not found" to null

        // Try NewFiles first (standard Garmin import folder)
        val newFilesHandle = findGarminChildFolderHandle(
            mtpDevice = mtpDevice,
            storageId = storageId,
            garminHandle = garminHandle,
            folderName = "NewFiles"
        )
        if (newFilesHandle != null) {
            return "NewFiles" to newFilesHandle
        }

        // Fall back to Courses
        val coursesHandle = findGarminChildFolderHandle(
            mtpDevice = mtpDevice,
            storageId = storageId,
            garminHandle = garminHandle,
            folderName = "Courses"
        )
        if (coursesHandle != null) {
            return "Courses" to coursesHandle
        }

        return "Neither NewFiles nor Courses folder found" to null
    }

    private fun enumerateCourseFilesOnDevice(
        mtpDevice: MtpDevice,
        storageId: Int,
        coursesHandle: Int
    ): List<CourseFileInfo> {
        val courseHandles =
            mtpDevice.getObjectHandles(
                storageId,
                0,
                coursesHandle
            ) ?: return emptyList()

        return courseHandles
            .toList()
            .mapNotNull { handle ->
                getMtpObjectInfoWithRetry(mtpDevice, handle)?.let { info ->
                    if (info.name.endsWith(".fit", ignoreCase = true) ||
                        info.name.endsWith(".gpx", ignoreCase = true)
                    ) {
                        CourseFileInfo(
                            handle = handle,
                            name = info.name,
                            sizeBytes = info.compressedSize.toLong(),
                            modifiedEpochSeconds = info.dateModified / 1000
                        )
                    } else {
                        null
                    }
                }
            }
            .sortedBy { it.name }
    }

    private fun deleteMtpObjectWithRetry(
        mtpDevice: MtpDevice,
        handle: Int
    ): Boolean {
        repeat(4) { attempt ->
            if (mtpDevice.deleteObject(handle)) {
                return true
            }

            if (attempt < 3) {
                Thread.sleep(100)
            }
        }

        return false
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
        var mtpOpened = false

        try {
            if (!mtpDevice.open(connection)) {
                return "Could not open MTP session" to 0
            }

            mtpOpened = true

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
            closeMtpSession(
                mtpDevice = mtpDevice,
                mtpOpened = mtpOpened
            )
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

    private fun openCoursesBackupFolder() {
        val folderUri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Download/RideVault/Courses"
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

    private data class TransferFile(
        val handle: Int,
        val name: String,
        val sizeBytes: Long
    )

    private data class TransferResult(
        val verifiedCount: Int
    )

    private fun downloadActivitiesWorker(
        files: List<FitFileInfo>
    ): Pair<String, DownloadSummary?> {
        val transferFiles = files.map { file ->
            TransferFile(
                handle = file.handle,
                name = file.name,
                sizeBytes = file.sizeBytes
            )
        }

        val result = copyMtpFilesToDownloads(
            files = transferFiles,
            currentPath =
                "${Environment.DIRECTORY_DOWNLOADS}/RideVault/Activities/",
            previousPath =
                "${Environment.DIRECTORY_DOWNLOADS}/RideVault/Activities-Previous/",
            preparingErrorPrefix =
                "Could not prepare download folders",
            progressVerb = "Downloading",
            createErrorPrefix =
                "Could not create destination file",
            transferFailurePrefix = "Download failed",
            exceptionPrefix = "Download exception",
            successNoun = "activities",
            onProgress = { message ->
                downloadStatus = message
            }
        )

        val transferResult = result.second
            ?: return result.first to null

        return result.first to DownloadSummary(
            files = files,
            verifiedCount = transferResult.verifiedCount
        )
    }

    private fun backupCoursesWorker(
        files: List<CourseFileInfo>
    ): Pair<String, CourseBackupSummary?> {
        val transferFiles = files.map { file ->
            TransferFile(
                handle = file.handle,
                name = normalizeCourseFitFilename(file.name),
                sizeBytes = file.sizeBytes
            )
        }

        val result = copyMtpFilesToDownloads(
            files = transferFiles,
            currentPath =
                "${Environment.DIRECTORY_DOWNLOADS}/RideVault/Courses/",
            previousPath =
                "${Environment.DIRECTORY_DOWNLOADS}/RideVault/Courses-Previous/",
            preparingErrorPrefix =
                "Could not prepare backup folders",
            progressVerb = "Backing up",
            createErrorPrefix =
                "Could not create destination file",
            transferFailurePrefix = "Backup failed",
            exceptionPrefix = "Backup exception",
            successNoun = "courses",
            onProgress = { message ->
                courseBackupStatus = message
            }
        )

        val transferResult = result.second
            ?: return result.first to null

        return result.first to CourseBackupSummary(
            files = files,
            verifiedCount = transferResult.verifiedCount
        )
    }

    private fun copyMtpFilesToDownloads(
        files: List<TransferFile>,
        currentPath: String,
        previousPath: String,
        preparingErrorPrefix: String,
        progressVerb: String,
        createErrorPrefix: String,
        transferFailurePrefix: String,
        exceptionPrefix: String,
        successNoun: String,
        onProgress: (String) -> Unit
    ): Pair<String, TransferResult?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return "Downloads require Android 10 or newer" to null
        }

        val device = connectedDevice
            ?: return "No Garmin device connected" to null

        if (!usbManager.hasPermission(device)) {
            return "USB permission required" to null
        }

        val collection =
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val normalizedCurrentPath =
            normalizeDownloadRelativePath(currentPath)
        val normalizedPreviousPath =
            normalizeDownloadRelativePath(previousPath)

        try {
            rotateDownloadSet(
                collection = collection,
                currentPath = normalizedCurrentPath,
                previousPath = normalizedPreviousPath
            )
        } catch (e: Exception) {
            return (
                "$preparingErrorPrefix: " +
                        e.javaClass.simpleName
            ) to null
        }

        val connection = usbManager.openDevice(device)
            ?: return "Could not open USB connection" to null

        val mtpDevice = MtpDevice(device)
        var mtpOpened = false

        try {
            if (!mtpDevice.open(connection)) {
                return "Could not open MTP session" to null
            }

            mtpOpened = true

            var verifiedCount = 0

            for ((index, file) in files.withIndex()) {
                runOnUiThread {
                    onProgress(
                        "$progressVerb ${index + 1} of ${files.size}: " +
                                file.name
                    )
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
                        normalizedCurrentPath
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
                    "$createErrorPrefix: ${file.name}"
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
                        "$transferFailurePrefix at ${index + 1} of " +
                                "${files.size}: ${file.name}"
                    ) to null
                }

                contentResolver.update(
                    outputUri,
                    ContentValues().apply {
                        put(
                            MediaStore.Downloads.IS_PENDING,
                            0
                        )
                    },
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

            return (
                "Downloaded and verified $verifiedCount of " +
                        "${files.size} $successNoun"
            ) to TransferResult(
                verifiedCount = verifiedCount
            )
        } catch (e: Exception) {
            return (
                "$exceptionPrefix: ${e.javaClass.simpleName}"
            ) to null
        } finally {
            closeMtpSession(
                mtpDevice = mtpDevice,
                mtpOpened = mtpOpened
            )
        }
    }

    private fun queryDownloadIdsAtRelativePath(
        collection: android.net.Uri,
        relativePath: String
    ): Set<Long> {
        val projection = arrayOf(MediaStore.Downloads._ID)
        val ids = linkedSetOf<Long>()

        for (queryPath in downloadRelativePathQueryValues(relativePath)) {
            contentResolver.query(
                collection,
                projection,
                "${MediaStore.Downloads.RELATIVE_PATH} = ?",
                arrayOf(queryPath),
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Downloads._ID
                )

                while (cursor.moveToNext()) {
                    ids.add(cursor.getLong(idColumn))
                }
            }
        }

        return ids
    }

    private fun rotateDownloadSet(
        collection: android.net.Uri,
        currentPath: String,
        previousPath: String
    ) {
        val normalizedCurrentPath =
            normalizeDownloadRelativePath(currentPath)
        val normalizedPreviousPath =
            normalizeDownloadRelativePath(previousPath)

        for (itemId in queryDownloadIdsAtRelativePath(
            collection,
            normalizedPreviousPath
        )) {
            val itemUri =
                android.content.ContentUris.withAppendedId(
                    collection,
                    itemId
                )

            contentResolver.delete(itemUri, null, null)
        }

        for (itemId in queryDownloadIdsAtRelativePath(
            collection,
            normalizedCurrentPath
        )) {
            val itemUri =
                android.content.ContentUris.withAppendedId(
                    collection,
                    itemId
                )

            val moveValues = ContentValues().apply {
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    normalizedPreviousPath
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

    private fun openMtpSessionWorker(): String {
        val device = connectedDevice ?: return "No Garmin device connected"

        if (!usbManager.hasPermission(device)) {
            return "USB permission required"
        }

        runOnUiThread { mtpStatus = "Opening USB connection..." }

        val connection = usbManager.openDevice(device)
            ?: return "Could not open USB connection"

        val mtpDevice = MtpDevice(device)
        var mtpOpened = false

        try {
            runOnUiThread { mtpStatus = "Opening MTP session..." }

            val opened = mtpDevice.open(connection)

            if (!opened) {
                return "Could not open MTP session"
            }

            mtpOpened = true

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

                    var retryCount2 = 0

                    while (
                        objectInfo == null &&
                        retryCount2 < 4
                    ) {
                        Thread.sleep(100)
                        retryCount2++
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
            closeMtpSession(
                mtpDevice = mtpDevice,
                mtpOpened = mtpOpened
            )
        }
    }
}
