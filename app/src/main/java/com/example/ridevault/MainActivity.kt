package com.example.ridevault

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ridevault.ui.theme.RideVaultTheme
import android.mtp.MtpDevice
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll


class MainActivity : ComponentActivity() {

    private val garminVendorId = 0x091e
    private val usbPermissionAction = "com.example.ridevault.USB_PERMISSION"

    private lateinit var usbManager: UsbManager

    private var connectedDevice by mutableStateOf<UsbDevice?>(null)
    private var hasUsbPermission by mutableStateOf(false)
    private var usbInspectionReport by mutableStateOf("")
    private var usbConnectionStatus by mutableStateOf("USB connection not opened")
    private var mtpStatus by mutableStateOf("MTP not opened")
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

                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                    if (device != null && granted) {
                        connectedDevice = device
                        hasUsbPermission = true
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
                    usbInspectionReport = usbInspectionReport,
                    onRequestPermission = { requestUsbPermission() },
                    onOpenUsbConnection = { openUsbConnection() },
                    onOpenMtpSession = { openMtpSession() }

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
    private fun openUsbConnection() {

        val device = connectedDevice ?: return

        if (!usbManager.hasPermission(device)) {

            usbConnectionStatus = "USB permission required"

            return

        }

        val connection = usbManager.openDevice(device)

        if (connection == null) {

            usbConnectionStatus = "Could not open USB connection"

        } else {

            usbConnectionStatus = "USB connection opened"

            connection.close()

        }

    }
    private fun openMtpSession() {
        val device = connectedDevice ?: return

        if (!usbManager.hasPermission(device)) {
            mtpStatus = "USB permission required"
            return
        }

        val connection = usbManager.openDevice(device)

        if (connection == null) {
            mtpStatus = "Could not open USB connection"
            return
        }

        val mtpDevice = MtpDevice(device)
        val opened = mtpDevice.open(connection)

        if (opened) {

            try {

                val info = mtpDevice.deviceInfo

                if (info == null) {

                    mtpStatus = "MTP opened, but no DeviceInfo"

                } else {

                    val storageIds = mtpDevice.storageIds ?: intArrayOf()

                    if (storageIds.isEmpty()) {

                        mtpStatus =
                            "MTP: ${info.manufacturer} ${info.model}; storages=0"

                    } else {

                        val storageInfo = mtpDevice.getStorageInfo(storageIds[0])

                        mtpStatus =
                            "MTP: ${info.manufacturer} ${info.model}; " +
                                    "storages=${storageIds.size}; " +
                                    "storage=${storageInfo?.description ?: "unknown"}"

                    }

                }

            } catch (e: Exception) {

                mtpStatus = "DeviceInfo exception: ${e.javaClass.simpleName}"

            }

        } else {

            mtpStatus = "Could not open MTP session"

        }

        mtpDevice.close()
        connection.close()
    }
}

@Composable
fun RideVaultHome(
    device: UsbDevice?,
    hasPermission: Boolean,
    usbConnectionStatus: String,
    mtpStatus: String,
    usbInspectionReport: String,
    onRequestPermission: () -> Unit,
    onOpenUsbConnection: () -> Unit,
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

                Spacer(modifier = Modifier.height(16.dp))

                DeviceInfoTable(device)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = usbInspectionReport,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (hasPermission) {
                    Text(
                        text = "USB permission granted",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = onOpenUsbConnection) {
                        Text("Open USB Connection")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = usbConnectionStatus,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = onOpenMtpSession) {
                        Text("Open MTP Session")
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