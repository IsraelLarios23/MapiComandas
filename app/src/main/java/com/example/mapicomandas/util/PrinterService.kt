package com.example.mapicomandas.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Impresión ESC/POS por Red, Bluetooth o USB.
 *
 * El destino se especifica con prefijo:
 *   - "net:IP:puerto"  o  "IP:puerto"   → impresora de red (TCP, def 9100)
 *   - "bt:NOMBRE"  o  "bt:AA:BB:CC:..." → impresora Bluetooth emparejada (SPP)
 *   - "usb"  o  "usb:VID:PID"           → impresora USB (clase impresora o VID:PID)
 */
@Singleton
class PrinterService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val accionPermisoUsb = "com.example.mapicomandas.USB_PERMISSION"

    /** Imprime las líneas. Devuelve null si OK, o el mensaje de error. */
    suspend fun imprimir(destino: String, lineas: List<String>, abrirCajon: Boolean = true): String? {
        val payload = EscPosPayload.construir(lineas, abrirCajon)
        val t = destino.trim()
        return when {
            t.startsWith("bt:", ignoreCase = true) -> imprimirBluetooth(t.substring(3), payload)
            t.startsWith("usb", ignoreCase = true) -> imprimirUsb(t.removePrefix("usb").removePrefix(":").removePrefix("USB").trim(), payload)
            t.startsWith("net:", ignoreCase = true) -> imprimirRed(t.substring(4), payload)
            else -> imprimirRed(t, payload)
        }
    }

    // ── Red (TCP) ───────────────────────────────────────────────────────────
    private suspend fun imprimirRed(destino: String, payload: ByteArray): String? =
        withContext(Dispatchers.IO) {
            val idx = destino.lastIndexOf(':')
            val host = if (idx > 0) destino.substring(0, idx) else destino
            val port = if (idx > 0) destino.substring(idx + 1).toIntOrNull() ?: 9100 else 9100
            try {
                withTimeout(10_000) {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(host, port), 8_000)
                        s.getOutputStream().apply { write(payload); flush() }
                    }
                }
                null
            } catch (e: Exception) {
                "Error de red ($host:$port): ${e.message}"
            }
        }

    // ── Bluetooth (RFCOMM/SPP) ────────────────────────────────────────────────
    private suspend fun imprimirBluetooth(nombreOMac: String, payload: ByteArray): String? =
        withContext(Dispatchers.IO) {
            try {
                val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val adapter: BluetoothAdapter? = manager?.adapter
                    ?: BluetoothAdapter.getDefaultAdapter()
                if (adapter == null) return@withContext "Bluetooth no disponible"
                if (!adapter.isEnabled) return@withContext "Bluetooth apagado"

                val bonded = try {
                    adapter.bondedDevices
                } catch (e: SecurityException) {
                    return@withContext "Falta permiso de Bluetooth (BLUETOOTH_CONNECT)"
                }
                val clave = nombreOMac.trim()
                val device = bonded.firstOrNull {
                    it.address.equals(clave, true) || (it.name?.equals(clave, true) == true)
                } ?: bonded.firstOrNull { it.name?.contains(clave, true) == true }
                    ?: return@withContext "Impresora Bluetooth '$clave' no emparejada"

                adapter.cancelDiscovery()
                val socket = device.createRfcommSocketToServiceRecord(sppUuid)
                socket.use {
                    it.connect()
                    it.outputStream.apply { write(payload); flush() }
                }
                null
            } catch (e: SecurityException) {
                "Falta permiso de Bluetooth (BLUETOOTH_CONNECT)"
            } catch (e: Exception) {
                "Error Bluetooth: ${e.message}"
            }
        }

    // ── USB ───────────────────────────────────────────────────────────────────
    private suspend fun imprimirUsb(vidPid: String, payload: ByteArray): String? {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return "USB no disponible"

        val dispositivos = usbManager.deviceList.values
        if (dispositivos.isEmpty()) return "No hay dispositivos USB conectados"

        val device: UsbDevice = run {
            if (vidPid.contains(":")) {
                val (vid, pid) = vidPid.split(":").let {
                    (it.getOrNull(0)?.toIntOrNull(16) ?: it.getOrNull(0)?.toIntOrNull()) to
                    (it.getOrNull(1)?.toIntOrNull(16) ?: it.getOrNull(1)?.toIntOrNull())
                }
                dispositivos.firstOrNull { it.vendorId == vid && it.productId == pid }
            } else {
                // Buscar interfaz de clase "impresora" (7)
                dispositivos.firstOrNull { dev ->
                    (0 until dev.interfaceCount).any { dev.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_PRINTER }
                } ?: dispositivos.firstOrNull()
            }
        } ?: return "Impresora USB no encontrada"

        if (!usbManager.hasPermission(device)) {
            val ok = solicitarPermisoUsb(usbManager, device)
            if (!ok) return "Permiso USB denegado para la impresora"
        }

        return withContext(Dispatchers.IO) {
            enviarUsb(usbManager, device, payload)
        }
    }

    private fun enviarUsb(usbManager: UsbManager, device: UsbDevice, payload: ByteArray): String? {
        var conn: UsbDeviceConnection? = null
        return try {
            // Buscar interfaz + endpoint OUT (preferir clase impresora)
            var intf = (0 until device.interfaceCount)
                .map { device.getInterface(it) }
                .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_PRINTER }
                ?: device.getInterface(0)

            val endpoint = (0 until intf.endpointCount)
                .map { intf.getEndpoint(it) }
                .firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }
                ?: return "La impresora USB no tiene endpoint de salida"

            conn = usbManager.openDevice(device) ?: return "No se pudo abrir el dispositivo USB"
            if (!conn.claimInterface(intf, true)) return "No se pudo reclamar la interfaz USB"

            val enviados = conn.bulkTransfer(endpoint, payload, payload.size, 8_000)
            if (enviados < 0) "Fallo al enviar datos por USB" else null
        } catch (e: Exception) {
            "Error USB: ${e.message}"
        } finally {
            conn?.close()
        }
    }

    private suspend fun solicitarPermisoUsb(usbManager: UsbManager, device: UsbDevice): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == accionPermisoUsb) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    deferred.complete(granted)
                }
            }
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            android.app.PendingIntent.FLAG_MUTABLE else 0
        val pi = android.app.PendingIntent.getBroadcast(
            context, 0, Intent(accionPermisoUsb).setPackage(context.packageName), flags
        )
        val filter = IntentFilter(accionPermisoUsb)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        return try {
            usbManager.requestPermission(device, pi)
            withTimeout(30_000) { deferred.await() }
        } catch (e: Exception) {
            false
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
}
