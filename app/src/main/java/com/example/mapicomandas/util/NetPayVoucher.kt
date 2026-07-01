package com.example.mapicomandas.util

import com.example.mapicomandas.data.netpay.NetPayResultado
import java.util.Locale

/**
 * Construye el comprobante (voucher) de una transacción con terminal NetPay,
 * mismo estilo que el voucher que imprime MapiPOS. Ancho 40 columnas.
 */
object NetPayVoucher {

    private const val ANCHO = 40

    private fun centrar(t: String): String {
        if (t.length >= ANCHO) return t.take(ANCHO)
        val libre = ANCHO - t.length
        val izq = libre / 2
        return " ".repeat(izq) + t
    }

    private fun kv(k: String, v: String): String {
        val valor = v.take(ANCHO - k.length - 1)
        val libre = ANCHO - k.length - valor.length
        return k + " ".repeat(libre.coerceAtLeast(1)) + valor
    }

    private fun linea() = "-".repeat(ANCHO)

    /**
     * @param copia texto de la copia ("COMERCIO" / "CLIENTE") mostrado al pie.
     */
    fun construir(
        res: NetPayResultado,
        storeId: String,
        serial: String,
        fechaHora: String,
        copia: String = "COMERCIO"
    ): List<String> {
        val l = mutableListOf<String>()
        l += centrar("COMPROBANTE DE PAGO")
        l += centrar("NetPay")
        l += linea()
        if (storeId.isNotBlank()) l += kv("Comercio:", storeId)
        if (serial.isNotBlank()) l += kv("Terminal:", serial)
        l += kv("Fecha:", fechaHora)
        l += linea()
        val marcaTarjeta = listOfNotNull(
            res.marca?.takeIf { it.isNotBlank() },
            res.ultimos4?.takeIf { it.isNotBlank() }?.let { "****$it" }
        ).joinToString(" ")
        if (marcaTarjeta.isNotBlank()) l += kv("Tarjeta:", marcaTarjeta)
        res.tipoTarjeta?.takeIf { it.isNotBlank() }?.let {
            l += kv("Tipo:", if (it.equals("CR", true)) "CREDITO" else if (it.equals("DB", true)) "DEBITO" else it)
        }
        val monto = res.montoCobrado?.toDoubleOrNull()
        if (monto != null) l += kv("Importe:", "$" + String.format(Locale.US, "%,.2f", monto))
        res.authCode?.takeIf { it.isNotBlank() }?.let { l += kv("Autorizacion:", it) }
        res.orderId?.takeIf { it.isNotBlank() }?.let { l += kv("Referencia:", it) }
        res.responseCode?.takeIf { it.isNotBlank() }?.let { l += kv("Cod. Resp.:", it) }
        l += linea()
        l += centrar(if (res.aprobada) "** APROBADA **" else "** ${res.estatus} **")
        l += ""
        l += centrar("Pago con tarjeta")
        l += centrar("No requiere firma")
        l += ""
        l += centrar("COPIA $copia")
        l += ""
        l += ""
        return l
    }
}
