package com.example.mapicomandas.util

import java.net.Inet4Address
import java.net.NetworkInterface

/** Utilidades de red para el receptor NetPay embebido. */
object NetworkUtils {

    /**
     * Devuelve la primera IPv4 de red local (Wi-Fi/Ethernet), no loopback, que la
     * terminal NetPay puede usar como destino del servicio de respuesta.
     * Prioriza rangos privados (192.168/10./172.16-31). Null si no hay red.
     */
    fun obtenerIpLocal(): String? {
        return runCatching {
            val candidatas = mutableListOf<String>()
            for (intf in NetworkInterface.getNetworkInterfaces()) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (addr.isLoopbackAddress || addr !is Inet4Address) continue
                    val ip = addr.hostAddress ?: continue
                    if (addr.isSiteLocalAddress) return@runCatching ip
                    candidatas.add(ip)
                }
            }
            candidatas.firstOrNull()
        }.getOrNull()
    }

    /** Path fijo que sugerimos capturar en la terminal (nuestro receptor acepta cualquier path). */
    const val NETPAY_PATH = "/netpay"

    /** URL completa a capturar en "Configurar respuesta del servicio" de la terminal. */
    fun urlReceptorNetPay(puerto: Int): String {
        val ip = obtenerIpLocal() ?: "IP-NO-DISPONIBLE"
        return "http://$ip:$puerto$NETPAY_PATH"
    }
}
