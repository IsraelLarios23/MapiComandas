package com.example.mapicomandas.data.api

import com.example.mapicomandas.data.api.dto.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ajustes de comanda contra la API central (/v1/negocio):
 * cortesías, descuentos, correcciones, devoluciones, división y transferencia
 * de partidas, cambio de status/unión de mesas, pendientes de pago y bitácora.
 */
@Singleton
class AjustesService @Inject constructor(private val api: ApiClient) {

    private inline fun <reified T> parse(el: JsonElement): T = api.json.decodeFromJsonElement<T>(el)

    // ── Catálogo de motivos ─────────────────────────────────────────────────
    suspend fun motivos(): List<MotivoAjusteDto> = parse(api.get("/v1/motivos-ajuste"))

    suspend fun guardarMotivo(m: MotivoAjusteDto): MotivoAjusteDto =
        parse(api.post("/v1/motivos-ajuste", buildJsonObject {
            put("idMotivo", m.idMotivo); put("nombre", m.nombre); put("tipo", m.tipo)
            put("porcentajeSugerido", m.porcentajeSugerido)
            put("requiereAutorizacion", m.requiereAutorizacion)
            put("orden", m.orden); put("activo", m.activo)
        }))

    // ── Ajuste por partida (tipo 1 = cortesía, 2 = descuento) ───────────────
    suspend fun aplicarAjuste(
        idDetalle: Int, tipo: Int, idMotivo: Int,
        porcentaje: Double? = null, importe: Double? = null, nota: String? = null
    ): LineaAjustadaDto =
        parse(api.post("/v1/comandas/lineas/$idDetalle/ajuste", buildJsonObject {
            put("tipo", tipo); put("idMotivo", idMotivo)
            porcentaje?.let { put("porcentaje", it) }
            importe?.let { put("importe", it) }
            nota?.takeIf { it.isNotBlank() }?.let { put("nota", it) }
        }))

    suspend fun quitarAjuste(idDetalle: Int): LineaAjustadaDto =
        parse(api.delete("/v1/comandas/lineas/$idDetalle/ajuste"))

    // ── Descuento a la cuenta completa ──────────────────────────────────────
    suspend fun descuentoPreview(idComanda: Int, porcentaje: Double, idMotivo: Int): DescuentoCuentaPreviewDto =
        parse(api.get("/v1/comandas/$idComanda/descuento-preview?porcentaje=$porcentaje&idMotivo=$idMotivo"))

    suspend fun aplicarDescuentoCuenta(idComanda: Int, porcentaje: Double, idMotivo: Int): DescuentoCuentaPreviewDto =
        parse(api.post("/v1/comandas/$idComanda/descuento", buildJsonObject {
            put("porcentaje", porcentaje); put("idMotivo", idMotivo)
        }))

    // ── Corregir / devolver / dividir / transferir ──────────────────────────
    suspend fun corregirPartida(
        idDetalle: Int, cantidad: Double? = null, precio: Double? = null, idMotivo: Int = 0
    ): LineaAjustadaDto =
        parse(api.post("/v1/comandas/lineas/$idDetalle/corregir", buildJsonObject {
            cantidad?.let { put("cantidad", it) }
            precio?.let { put("precio", it) }
            put("idMotivo", idMotivo)
        }))

    suspend fun devolverPartida(idDetalle: Int, idMotivo: Int): PartidaDevueltaDto =
        parse(api.post("/v1/comandas/lineas/$idDetalle/devolver", buildJsonObject {
            put("idMotivo", idMotivo)
        }))

    suspend fun dividirPartida(idDetalle: Int, partes: Int): PartidaDivididaDto =
        parse(api.post("/v1/comandas/lineas/$idDetalle/dividir", buildJsonObject {
            put("partes", partes)
        }))

    suspend fun transferirPartidas(idsDetalle: List<Int>, idMesaDestino: Int): PartidasTransferidasDto =
        parse(api.post("/v1/comandas/transferir", buildJsonObject {
            put("idMesaDestino", idMesaDestino)
            putJsonArrayInts("idsDetalle", idsDetalle)
        }))

    // ── Mesas ───────────────────────────────────────────────────────────────
    suspend fun cambiarStatusMesa(idMesa: Int, status: Int): MesaStatusDto =
        parse(api.put("/v1/mesas/$idMesa/status", buildJsonObject { put("status", status) }))

    suspend fun unirMesas(idMesaOrigen: Int, idMesaDestino: Int): MesasUnidasDto =
        parse(api.post("/v1/mesas/unir", buildJsonObject {
            put("idMesaOrigen", idMesaOrigen); put("idMesaDestino", idMesaDestino)
        }))

    suspend fun separarMesa(idMesa: Int): SepararMesaDto =
        parse(api.post("/v1/mesas/$idMesa/separar"))

    // ── Pagos en caja / bitácora ────────────────────────────────────────────
    suspend fun pendientesPago(): List<PendientePagoDto> =
        parse(api.get("/v1/comandas/pendientes-pago"))

    /** [fecha] "yyyy-MM-dd"; null = hoy. */
    suspend fun bitacora(fecha: String? = null): List<AccionComandaDto> =
        parse(api.get("/v1/comandas/bitacora" + (fecha?.let { "?fecha=$it" } ?: "")))
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putJsonArrayInts(clave: String, valores: List<Int>) {
    put(clave, kotlinx.serialization.json.buildJsonArray {
        valores.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
    })
}
