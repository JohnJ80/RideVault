package com.example.ridevault

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint

object UsbInspector {

    fun inspect(device: UsbDevice): String {
        val report = StringBuilder()

        report.appendLine("USB Device")
        report.appendLine("Vendor: 0x${device.vendorId.toString(16)}")
        report.appendLine("Product: 0x${device.productId.toString(16)}")
        report.appendLine("Device class: ${device.deviceClass}")
        report.appendLine("Device subclass: ${device.deviceSubclass}")
        report.appendLine("Device protocol: ${device.deviceProtocol}")
        report.appendLine("Interfaces: ${device.interfaceCount}")
        report.appendLine()

        for (interfaceIndex in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(interfaceIndex)

            report.appendLine("Interface $interfaceIndex")
            report.appendLine("  ID: ${usbInterface.id}")
            report.appendLine("  Class: ${usbInterface.interfaceClass} (${className(usbInterface.interfaceClass)})")
            report.appendLine("  Subclass: ${usbInterface.interfaceSubclass}")
            report.appendLine("  Protocol: ${usbInterface.interfaceProtocol}")
            report.appendLine("  Endpoints: ${usbInterface.endpointCount}")

            for (endpointIndex in 0 until usbInterface.endpointCount) {
                val endpoint = usbInterface.getEndpoint(endpointIndex)

                report.appendLine("    Endpoint $endpointIndex")
                report.appendLine("      Address: ${endpoint.address}")
                report.appendLine("      Direction: ${directionName(endpoint)}")
                report.appendLine("      Type: ${typeName(endpoint.type)}")
                report.appendLine("      Max packet: ${endpoint.maxPacketSize}")
                report.appendLine("      Interval: ${endpoint.interval}")
            }

            report.appendLine()
        }

        return report.toString().trimEnd()
    }

    private fun className(usbClass: Int): String {
        return when (usbClass) {
            UsbConstants.USB_CLASS_PER_INTERFACE -> "per-interface"
            UsbConstants.USB_CLASS_AUDIO -> "audio"
            UsbConstants.USB_CLASS_COMM -> "communications"
            UsbConstants.USB_CLASS_HID -> "HID"
            UsbConstants.USB_CLASS_PHYSICA -> "physical"
            UsbConstants.USB_CLASS_STILL_IMAGE -> "still-image/PTP/MTP"
            UsbConstants.USB_CLASS_PRINTER -> "printer"
            UsbConstants.USB_CLASS_MASS_STORAGE -> "mass-storage"
            UsbConstants.USB_CLASS_HUB -> "hub"
            UsbConstants.USB_CLASS_CDC_DATA -> "CDC-data"
            UsbConstants.USB_CLASS_CSCID -> "smart-card"
            UsbConstants.USB_CLASS_CONTENT_SEC -> "content-security"
            UsbConstants.USB_CLASS_VIDEO -> "video"
            UsbConstants.USB_CLASS_WIRELESS_CONTROLLER -> "wireless-controller"
            UsbConstants.USB_CLASS_MISC -> "misc"
            UsbConstants.USB_CLASS_APP_SPEC -> "application-specific"
            UsbConstants.USB_CLASS_VENDOR_SPEC -> "vendor-specific"
            else -> "unknown"
        }
    }

    private fun directionName(endpoint: UsbEndpoint): String {
        return when (endpoint.direction) {
            UsbConstants.USB_DIR_IN -> "IN"
            UsbConstants.USB_DIR_OUT -> "OUT"
            else -> "unknown"
        }
    }

    private fun typeName(type: Int): String {
        return when (type) {
            UsbConstants.USB_ENDPOINT_XFER_CONTROL -> "control"
            UsbConstants.USB_ENDPOINT_XFER_ISOC -> "isochronous"
            UsbConstants.USB_ENDPOINT_XFER_BULK -> "bulk"
            UsbConstants.USB_ENDPOINT_XFER_INT -> "interrupt"
            else -> "unknown"
        }
    }
}