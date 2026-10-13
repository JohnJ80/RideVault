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
    onDismissCourseDeleteComplete: () -> Unit
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
                        text = "Rev 0.0.26",
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                RwgpsLoginSection()

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
            }
        }

        return
    }

    // rest of file unchanged — the tool will replace the whole file, so I need the full content
