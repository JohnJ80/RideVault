package com.example.ridevault

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ridevault.rwgps.RwgpsApi
import com.example.ridevault.rwgps.RwgpsLoginSection

@Composable
fun RideVaultHome(
    device: UsbDevice?,
    hasPermission: Boolean,
    usbConnectionStatus: String,
    mtpStatus: String,
    mtpBusy: Boolean,
    fitFiles: List<FitFileInfo>,
    courseFiles: List<CourseFileInfo>,
    activityScanCurrent: Int,
    activityScanTotal: Int,
    downloadBusy: Boolean,
    downloadStatus: String,
    downloadCompleteSummary: DownloadSummary?,
    deleteBusy: Boolean,
    deleteStatus: String,
    deleteCompleteCount: Int,
    courseBackupBusy: Boolean,
    courseBackupStatus: String,
    courseBackupCompleteSummary: CourseBackupSummary?,
    courseDeleteBusy: Boolean,
    courseDeleteStatus: String,
    courseDeleteCompleteCount: Int,
    onRequestPermission: () -> Unit,
    onOpenMtpSession: () -> Unit,
    onDownloadActivity: (FitFileInfo) -> Unit,
    onDownloadActivityAndNewer: (FitFileInfo) -> Unit,
    onDeleteActivityAndOlder: (FitFileInfo) -> Unit,
    onDismissDownloadComplete: () -> Unit,
    onDismissDeleteComplete: () -> Unit,
    onOpenDownloadFolder: () -> Unit,
    onBackupCourse: (CourseFileInfo) -> Unit,
    onBackupAllCourses: () -> Unit,
    onDismissCourseBackupComplete: () -> Unit,
    onOpenCoursesBackupFolder: () -> Unit,
    onDeleteCourse: (CourseFileInfo) -> Unit,
    onDeleteAllCourses: () -> Unit,
    onDismissCourseDeleteComplete: () -> Unit,
    rwgpsLoggedIn: Boolean = false,
    rwgpsStatus: String = "",
    onRwgpsLogout: () -> Unit = {},
    rwgpsCollections: List<RwgpsApi.CollectionSummary> = emptyList(),
    rwgpsRoutes: List<RwgpsApi.RouteSummary> = emptyList(),
    rwgpsSelectedCollection: RwgpsApi.CollectionSummary? = null,
    rwgpsBusy: Boolean = false,
    rwgpsBrowseStatus: String = "",
    rwgpsSendStatus: String = "",
    rwgpsSendBusy: Boolean = false,
    rwgpsSendCompleteCount: Int = 0,
    onBrowseRwgpsCollections: () -> Unit = {},
    onSelectRwgpsCollection: (RwgpsApi.CollectionSummary) -> Unit = {},
    onBackFromRwgpsRoutes: () -> Unit = {},
    onSendRouteToGarmin: (RwgpsApi.RouteSummary) -> Unit = {},
    onSendAllRoutesToGarmin: () -> Unit = {},
    onDismissRwgpsSendComplete: () -> Unit = {}
) {
    var selectedFile by remember {
        mutableStateOf<FitFileInfo?>(null)
    }

    var selectedCourse by remember {
        mutableStateOf<CourseFileInfo?>(null)
    }

    var courseToDelete by remember {
        mutableStateOf<CourseFileInfo?>(null)
    }

    var confirmDeleteAllCourses by remember {
        mutableStateOf(false)
    }

    var deleteBoundaryFile by remember {
        mutableStateOf<FitFileInfo?>(null)
    }

    var selectedDomain by remember {
        mutableStateOf("home")
    }

    if (device == null || !hasPermission) {
        selectedDomain = "home"
    }

    if (
        device != null &&
        hasPermission &&
        selectedDomain == "home"
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween,
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Text(
                        text = "RideVault",
                        style =
                            MaterialTheme.typography.headlineMedium
                    )

                    Text(
                        text = "Rev 0.0.38",
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                RwgpsLoginSection(
                    loggedIn = rwgpsLoggedIn,
                    status = rwgpsStatus,
                    onLogout = onRwgpsLogout
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Garmin Edge 1050",
                    style =
                        MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "Connected",
                    style =
                        MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onOpenMtpSession,
                    enabled = !mtpBusy
                ) {
                    Text(
                        if (mtpBusy) {
                            "Reading Garmin..."
                        } else {
                            "Load Garmin Data"
                        }
                    )
                }

                if (mtpStatus.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = mtpStatus,
                        style =
                            MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = !mtpBusy
                        ) {
                            selectedDomain = "activities"
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "Activities",
                            style =
                                MaterialTheme.typography.titleLarge
                        )

                        Spacer(
                            modifier = Modifier.height(6.dp)
                        )

                        Text(
                            text = fitFiles.size.toString(),
                            style =
                                MaterialTheme.typography.headlineMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = !mtpBusy
                        ) {
                            selectedDomain = "courses"
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "Courses",
                            style =
                                MaterialTheme.typography.titleLarge
                        )

                        Spacer(
                            modifier = Modifier.height(6.dp)
                        )

                        Text(
                            text = courseFiles.size.toString(),
                            style =
                                MaterialTheme.typography.headlineMedium
                        )
                    }
                }

                if (rwgpsLoggedIn) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !rwgpsBusy) {
                                selectedDomain = "rwgps_collections"
                                onBrowseRwgpsCollections()
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Text(
                                text = "RWGPS Routes",
                                style =
                                    MaterialTheme.typography.titleLarge
                            )

                            Spacer(
                                modifier = Modifier.height(6.dp)
                            )

                            Text(
                                text = "Browse & send to Garmin",
                                style =
                                    MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        return
    }

    if (
        device != null &&
        hasPermission &&
        selectedDomain == "courses"
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween,
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            selectedDomain = "home"
                        }
                    ) {
                        Text("Back")
                    }

                    Text(
                        text = "Rev 0.0.38",
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    text = "Courses",
                    style =
                        MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${courseFiles.size} Courses",
                    style =
                        MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (courseBackupStatus.isNotBlank()) {
                    Text(
                        text = courseBackupStatus,
                        style =
                            MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (courseDeleteStatus.isNotBlank()) {
                    Text(
                        text = courseDeleteStatus,
                        style =
                            MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (courseFiles.isNotEmpty()) {
                    Button(
                        onClick = onBackupAllCourses,
                        enabled =
                            !courseBackupBusy &&
                            !courseDeleteBusy &&
                            !mtpBusy &&
                            !downloadBusy &&
                            !deleteBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (courseBackupBusy) {
                                "Backing up courses..."
                            } else {
                                "Backup All Courses"
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (courseFiles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            confirmDeleteAllCourses = true
                        },
                        enabled =
                            !courseDeleteBusy &&
                            !courseBackupBusy &&
                            !mtpBusy &&
                            !downloadBusy &&
                            !deleteBusy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                                MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            if (courseDeleteBusy) {
                                "Deleting courses..."
                            } else {
                                "Delete All Courses"
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (courseFiles.isEmpty()) {
                    Text(
                        text = if (mtpBusy) {
                            mtpStatus
                        } else {
                            "No course files found"
                        },
                        style =
                            MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement =
                            Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = courseFiles,
                            key = { it.handle }
                        ) { course ->
                            Card(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            enabled =
                                                !courseBackupBusy &&
                                                !courseDeleteBusy &&
                                                !mtpBusy &&
                                                !downloadBusy &&
                                                !deleteBusy
                                        ) {
                                            selectedCourse = course
                                        }
                            ) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                    horizontalArrangement =
                                        Arrangement.SpaceBetween,
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = course.name,
                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodyMedium
                                    )

                                    Text(
                                        text =
                                            formatFileSize(
                                                course.sizeBytes
                                            ),
                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else if (
        device != null &&
        hasPermission &&
        selectedDomain == "rwgps_collections"
    ) {
        // ── RWGPS Collections screen ─────────────────────────
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween,
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            selectedDomain = "home"
                        }
                    ) {
                        Text("Back")
                    }

                    Text(
                        text = "Rev 0.0.38",
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                }

                if (rwgpsSelectedCollection != null) {
                    // ── Routes in a collection ───────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onBackFromRwgpsRoutes
                        ) {
                            Text("← Collections")
                        }
                    }

                    Text(
                        text = rwgpsSelectedCollection!!.name,
                        style =
                            MaterialTheme.typography.headlineMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (rwgpsBrowseStatus.isNotBlank()) {
                        Text(
                            text = rwgpsBrowseStatus,
                            style =
                                MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (rwgpsSendStatus.isNotBlank()) {
                        Text(
                            text = rwgpsSendStatus,
                            style =
                                MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (rwgpsRoutes.isNotEmpty() && !rwgpsBusy) {
                        Button(
                            onClick = onSendAllRoutesToGarmin,
                            enabled = !rwgpsSendBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Send All ${rwgpsRoutes.size} Routes to Garmin")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (rwgpsRoutes.isEmpty() && !rwgpsBusy) {
                        Text(
                            text = "No routes in this collection",
                            style =
                                MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement =
                                Arrangement.spacedBy(6.dp)
                        ) {
                            items(
                                items = rwgpsRoutes,
                                key = { it.id }
                            ) { route ->
                                Card(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                enabled =
                                                    !rwgpsSendBusy
                                            ) {
                                                onSendRouteToGarmin(route)
                                            }
                                ) {
                                    Column(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp)
                                    ) {
                                        Text(
                                            text = route.name,
                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .bodyMedium
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement =
                                                Arrangement.SpaceBetween
                                        ) {
                                            val distKm = route.distanceMeters / 1000.0
                                            val distMi = distKm * 0.621371
                                            Text(
                                                text = String.format("%.1f mi", distMi),
                                                style =
                                                    MaterialTheme
                                                        .typography
                                                        .bodySmall
                                            )

                                            val gainFt = route.elevationGainMeters * 3.28084
                                            Text(
                                                text = String.format("%.0f ft gain", gainFt),
                                                style =
                                                    MaterialTheme
                                                        .typography
                                                        .bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ── Collections list ─────────────────────
                    Text(
                        text = "RWGPS Collections",
                        style =
                            MaterialTheme.typography.headlineMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (rwgpsBrowseStatus.isNotBlank()) {
                        Text(
                            text = rwgpsBrowseStatus,
                            style =
                                MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (rwgpsCollections.isEmpty() && !rwgpsBusy) {
                        Text(
                            text = "No collections found",
                            style =
                                MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement =
                                Arrangement.spacedBy(6.dp)
                        ) {
                            items(
                                items = rwgpsCollections,
                                key = { it.id }
                            ) { collection ->
                                Card(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                enabled = !rwgpsBusy
                                            ) {
                                                onSelectRwgpsCollection(collection)
                                            }
                                ) {
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                        horizontalArrangement =
                                            Arrangement.SpaceBetween,
                                        verticalAlignment =
                                            Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = collection.name,
                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Text(
                                            text = if (collection.routeCount > 0) {
                                                "${collection.routeCount} routes"
                                            } else {
                                                "Tap to view →"
                                            },
                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
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
                        text = "Rev 0.0.38",
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                selectedDomain = "home"
                            }
                        ) {
                            Text("Back")
                        }

                        Spacer(
                            modifier = Modifier.width(8.dp)
                        )

                        Text(
                            text = "Activities",
                            style =
                                MaterialTheme.typography.titleLarge
                        )
                    }

                    if (downloadStatus.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = downloadStatus,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (deleteStatus.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = deleteStatus,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    if (mtpBusy && activityScanTotal > 0) {
                        val scanProgress =
                            activityScanCurrent.toFloat() /
                                    activityScanTotal.toFloat()

                        Text(
                            text = "Scanning $activityScanCurrent of $activityScanTotal",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        LinearProgressIndicator(
                            progress = { scanProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (fitFiles.isEmpty()) {
                        Text(
                            text = if (mtpBusy) {
                                mtpStatus
                            } else {
                                "No activity files found"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
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
                                            enabled =
                                                !downloadBusy &&
                                                !deleteBusy &&
                                                !mtpBusy
                                        ) {
                                            selectedFile = file
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodyMedium
                                        )

                                        Text(
                                            text = formatFileSize(file.sizeBytes),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedFile != null) {
        AlertDialog(
            onDismissRequest = { selectedFile = null },
            title = { Text(selectedFile!!.name) },
            text = {
                Column {
                    Text("Size: ${formatFileSize(selectedFile!!.sizeBytes)}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("What would you like to do?")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDownloadActivity(selectedFile!!)
                        selectedFile = null
                    }
                ) {
                    Text("Download")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            onDownloadActivityAndNewer(selectedFile!!)
                            selectedFile = null
                        }
                    ) {
                        Text("This + Newer")
                    }
                    TextButton(
                        onClick = {
                            deleteBoundaryFile = selectedFile
                            selectedFile = null
                        }
                    ) {
                        Text("This + Older")
                    }
                    TextButton(onClick = { selectedFile = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (deleteBoundaryFile != null) {
        AlertDialog(
            onDismissRequest = { deleteBoundaryFile = null },
            title = { Text("Delete this and older?") },
            text = {
                Text(
                    "Delete ${deleteBoundaryFile!!.name} and every activity older than it? " +
                            "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteActivityAndOlder(deleteBoundaryFile!!)
                        deleteBoundaryFile = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteBoundaryFile = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (selectedCourse != null) {
        AlertDialog(
            onDismissRequest = { selectedCourse = null },
            title = { Text(selectedCourse!!.name) },
            text = {
                Column {
                    Text("Size: ${formatFileSize(selectedCourse!!.sizeBytes)}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("What would you like to do?")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onBackupCourse(selectedCourse!!)
                        selectedCourse = null
                    }
                ) {
                    Text("Backup")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            courseToDelete = selectedCourse
                            selectedCourse = null
                        }
                    ) {
                        Text("Delete")
                    }
                    TextButton(onClick = { selectedCourse = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (courseToDelete != null) {
        AlertDialog(
            onDismissRequest = { courseToDelete = null },
            title = { Text("Delete course?") },
            text = { Text("Delete ${courseToDelete!!.name}? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCourse(courseToDelete!!)
                        courseToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { courseToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (confirmDeleteAllCourses) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAllCourses = false },
            title = { Text("Delete all courses?") },
            text = {
                Text(
                    "Delete all ${courseFiles.size} courses from the Edge? " +
                            "A backup will be kept on the phone until the next delete."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAllCourses()
                        confirmDeleteAllCourses = false
                    }
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAllCourses = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    downloadCompleteSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = onDismissDownloadComplete,
            title = { Text("Download complete") },
            text = {
                Column {
                    Text("Downloaded ${summary.verifiedCount} activities.")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Total size: ${formatFileSize(summary.files.sumOf { it.sizeBytes })}")
                }
            },
            confirmButton = {
                TextButton(onClick = onOpenDownloadFolder) {
                    Text("Open Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDownloadComplete) {
                    Text("OK")
                }
            }
        )
    }

    if (deleteCompleteCount > 0) {
        AlertDialog(
            onDismissRequest = onDismissDeleteComplete,
            title = { Text("Delete complete") },
            text = { Text("Deleted $deleteCompleteCount activities.") },
            confirmButton = {
                TextButton(onClick = onDismissDeleteComplete) {
                    Text("OK")
                }
            }
        )
    }

    courseBackupCompleteSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = onDismissCourseBackupComplete,
            title = { Text("Backup complete") },
            text = {
                Column {
                    Text("Backed up ${summary.verifiedCount} courses.")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Total size: ${formatFileSize(summary.totalSizeBytes)}")
                }
            },
            confirmButton = {
                TextButton(onClick = onOpenCoursesBackupFolder) {
                    Text("Open Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissCourseBackupComplete) {
                    Text("OK")
                }
            }
        )
    }

    if (courseDeleteCompleteCount > 0) {
        AlertDialog(
            onDismissRequest = onDismissCourseDeleteComplete,
            title = { Text("Delete complete") },
            text = { Text("Deleted $courseDeleteCompleteCount courses.") },
            confirmButton = {
                TextButton(onClick = onDismissCourseDeleteComplete) {
                    Text("OK")
                }
            }
        )
    }

    if (rwgpsSendCompleteCount > 0) {
        AlertDialog(
            onDismissRequest = onDismissRwgpsSendComplete,
            title = { Text("Routes saved") },
            text = {
                Text(
                    "Saved $rwgpsSendCompleteCount route(s) as GPX to Downloads/RideVault/RWGPS-Routes/. " +
                        "Use a file manager to copy them to the Garmin."
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissRwgpsSendComplete) {
                    Text("OK")
                }
            }
        )
    }
}
