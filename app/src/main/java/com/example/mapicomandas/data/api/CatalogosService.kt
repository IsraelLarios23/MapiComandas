package com.example.mapicomandas.data.api

import com.example.mapicomandas.data.api.dto.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catálogos operativos del restaurante contra la API central:
 * mermas, monederos, habitaciones/estancias y comisiones.
 */
@Singleton
class CatalogosService @Inject constructor(private val api: ApiClient) {

    private inline fun <reified T> parse(el: JsonElement): T = api.json.decodeFromJsonElement<T>(el)

    // ── Mermas ──────────────────────────────────────────────────────────────
    suspend fun causasMerma(): List<CausaMermaDto> = parse(api.get("/v1/mermas/causas"))

    suspend fun registrarMerma(
        idArticulo: Int, cantidad: Double, idCausa: Int?, observaciones: String?
    ): MermaRegistradaDto =
        parse(api.post("/v1/mermas", buildJsonObject {
            put("idArticulo", idArticulo); put("cantidad", cantidad)
            idCausa?.let { put("idCausa", it) }
            observaciones?.takeIf { it.isNotBlank() }?.let { put("observaciones", it) }
        }))

    /** Fechas "yyyy-MM-dd"; nulas = hoy. */
    suspend fun reporteMermas(desde: String? = null, hasta: String? = null): List<MermaReporteDto> {
        val qs = listOfNotNull(desde?.let { "desde=$it" }, hasta?.let { "hasta=$it" }).joinToString("&")
        return parse(api.get("/v1/mermas" + (if (qs.isBlank()) "" else "?$qs")))
    }

    // ── Monederos ───────────────────────────────────────────────────────────
    suspend fun consultarMonedero(codigo: String): MonederoDto =
        parse(api.get("/v1/monederos/" + java.net.URLEncoder.encode(codigo, "UTF-8")))

    suspend fun altaMonedero(codigo: String, saldoInicial: Double, idCliente: Int? = null): MonederoDto =
        parse(api.post("/v1/monederos", buildJsonObject {
            put("codigo", codigo); put("saldoInicial", saldoInicial)
            idCliente?.let { put("idCliente", it) }
        }))

    /** [tipo] "Recarga" | "Ajuste" | "Consumo". */
    suspend fun movimientoMonedero(
        codigo: String, tipo: String, monto: Double, referencia: String? = null
    ): MonederoMovidoDto =
        parse(api.post("/v1/monederos/movimiento", buildJsonObject {
            put("codigo", codigo); put("tipo", tipo); put("monto", monto)
            referencia?.takeIf { it.isNotBlank() }?.let { put("referencia", it) }
        }))

    // ── Habitaciones / estancias ────────────────────────────────────────────
    suspend fun habitaciones(): List<HabitacionDto> = parse(api.get("/v1/habitaciones"))

    suspend fun guardarHabitacion(
        numero: String, descripcion: String?, idHabitacion: Int? = null, activo: Boolean = true
    ): HabitacionDto =
        parse(api.post("/v1/habitaciones", buildJsonObject {
            put("numero", numero)
            descripcion?.takeIf { it.isNotBlank() }?.let { put("descripcion", it) }
            idHabitacion?.let { put("idHabitacion", it) }
            put("activo", activo)
        }))

    suspend fun estanciasAbiertas(): List<EstanciaAbiertaDto> = parse(api.get("/v1/estancias"))

    suspend fun checkIn(idHabitacion: Int, idCliente: Int, nombreHuesped: String?): EstanciaAbiertaDto =
        parse(api.post("/v1/estancias/checkin", buildJsonObject {
            put("idHabitacion", idHabitacion); put("idCliente", idCliente)
            nombreHuesped?.takeIf { it.isNotBlank() }?.let { put("nombreHuesped", it) }
        }))

    suspend fun checkOut(idEstancia: Int): CheckOutDto =
        parse(api.post("/v1/estancias/$idEstancia/checkout"))

    // ── Comisiones ──────────────────────────────────────────────────────────
    suspend fun reglasComision(): List<ReglaComisionDto> = parse(api.get("/v1/comisiones/reglas"))

    suspend fun guardarReglaComision(
        nombre: String, ambito: Int, baseCalculo: Int, valor: Double,
        idReferencia: Int = 0, idReglaComision: Int? = null, idMesero: Int? = null, activo: Boolean = true
    ): ReglaComisionDto =
        parse(api.post("/v1/comisiones/reglas", buildJsonObject {
            put("nombre", nombre); put("ambito", ambito); put("baseCalculo", baseCalculo)
            put("valor", valor); put("idReferencia", idReferencia)
            idReglaComision?.let { put("idReglaComision", it) }
            idMesero?.let { put("idMesero", it) }
            put("activo", activo)
        }))

    /** Fechas "yyyy-MM-dd"; nulas = hoy. */
    suspend fun reporteComisiones(
        desde: String? = null, hasta: String? = null, idMesero: Int? = null
    ): List<ComisionMeseroDto> {
        val qs = listOfNotNull(
            desde?.let { "desde=$it" }, hasta?.let { "hasta=$it" }, idMesero?.let { "idMesero=$it" }
        ).joinToString("&")
        return parse(api.get("/v1/comisiones/reporte" + (if (qs.isBlank()) "" else "?$qs")))
    }
}
