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
    deleteBusy: Boolean,
    deleteStatus: String,
    deleteCompleteCount: Int,
    onRequestPermission: () -> Unit,
    onOpenMtpSession: () -> Unit,
    onDownloadActivity: (FitFileInfo) -> Unit,
    onDownloadActivityAndNewer: (FitFileInfo) -> Unit,
    onDeleteActivityAndOlder: (FitFileInfo) -> Unit,
    onDismissDownloadComplete: () -> Unit,
    onDismissDeleteComplete: () -> Unit,
    onOpenDownloadFolder: () -> Unit
) {
    var selectedFile by remember {
        mutableStateOf<FitFileInfo?>(null)
    }

    var deleteBoundaryFile by remember {
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
                    text = "Rev 0.0.22",
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

                if (deleteStatus.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = deleteStatus,
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
                        enabled =
                            !downloadBusy &&
                            !deleteBusy &&
                            !mtpBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Download this activity and newer")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            selectedFile = null
                            deleteBoundaryFile = file
                        },
                        enabled =
                            !deleteBusy &&
                            !downloadBusy &&
                            !mtpBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete this activity and older")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedFile = null
                        onDownloadActivity(file)
                    },
                    enabled =
                        !downloadBusy &&
                        !deleteBusy &&
                        !mtpBusy
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

    deleteBoundaryFile?.let { boundaryFile ->
        val filesToDelete =
            fitFiles.filter { file ->
                file.name <= boundaryFile.name
            }

        val remainingCount =
            fitFiles.size - filesToDelete.size

        val bytesToFree =
            filesToDelete.sumOf { file ->
                file.sizeBytes
            }

        AlertDialog(
            onDismissRequest = {
                deleteBoundaryFile = null
            },
            title = {
                Text("Delete Activities")
            },
            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    Row {
                        Text(
                            text = "Boundary:",
                            modifier = Modifier.width(110.dp)
                        )
                        Text(
                            formatFitTimestamp(
                                boundaryFile.name
                            )
                        )
                    }

                    Text(
                        "${filesToDelete.size} activities " +
                                "will be deleted"
                    )

                    Text(
                        "$remainingCount activities will remain"
                    )

                    Text(
                        "Approximately " +
                                formatFileSize(bytesToFree) +
                                " will be freed"
                    )

                    Text("This cannot be undone")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteBoundaryFile = null
                        onDeleteActivityAndOlder(
                            boundaryFile
                        )
                    },
                    enabled =
                        !deleteBusy &&
                        !downloadBusy &&
                        !mtpBusy
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deleteBoundaryFile = null
                    }
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

    if (deleteCompleteCount > 0) {
        AlertDialog(
            onDismissRequest =
                onDismissDeleteComplete,
            title = {
                Text("Delete Complete")
            },
            text = {
                Text(
                    "$deleteCompleteCount activities deleted"
                )
            },
            confirmButton = {
                TextButton(
                    onClick =
                        onDismissDeleteComplete
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


