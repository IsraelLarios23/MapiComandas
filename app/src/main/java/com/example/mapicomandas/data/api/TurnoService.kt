package com.example.mapicomandas.data.api

import com.example.mapicomandas.data.api.dto.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turno de restaurante contra la API central: preview y cierre del turno,
 * corte por mesero, reglas y reparto de propinas, y catálogo de meseros.
 */
@Singleton
class TurnoService @Inject constructor(private val api: ApiClient) {

    private inline fun <reified T> parse(el: JsonElement): T = api.json.decodeFromJsonElement<T>(el)

    // ── Turno ───────────────────────────────────────────────────────────────
    suspend fun turnoPreview(): TurnoPreviewDto = parse(api.get("/v1/turno/preview"))

    /** [desde] = el inicio que mostró el preview (guard anti doble cierre). */
    suspend fun cerrarTurno(
        desde: String? = null, efectivoReal: Double? = null, observaciones: String? = null
    ): CierreTurnoDto =
        parse(api.post("/v1/turno/cierre", buildJsonObject {
            desde?.let { put("desde", it) }
            efectivoReal?.let { put("efectivoReal", it) }
            observaciones?.takeIf { it.isNotBlank() }?.let { put("observaciones", it) }
        }))

    suspend fun corteMesero(idMesero: Int, desde: String? = null): CorteMeseroDto =
        parse(api.get("/v1/turno/mesero/$idMesero" + (desde?.let { "?desde=$it" } ?: "")))

    // ── Propinas ────────────────────────────────────────────────────────────
    suspend fun reglasReparto(): List<ReglaRepartoDto> = parse(api.get("/v1/propinas/reglas"))

    /** porcentaje 0 elimina la regla. */
    suspend fun guardarRegla(idPuesto: Int, porcentaje: Double): JsonElement =
        api.post("/v1/propinas/reglas", buildJsonObject {
            put("idPuesto", idPuesto); put("porcentaje", porcentaje)
        })

    suspend fun repartoPreview(idCierre: Int, modo: String? = null, presentes: List<Int>? = null): RepartoPreviewDto {
        val qs = buildList {
            modo?.let { add("modo=$it") }
            presentes?.takeIf { it.isNotEmpty() }?.let { add("presentes=" + it.joinToString(",")) }
        }.joinToString("&")
        return parse(api.get("/v1/turno/$idCierre/reparto-preview" + (if (qs.isBlank()) "" else "?$qs")))
    }

    suspend fun aplicarReparto(idCierre: Int, modo: String? = null, presentes: List<Int>? = null): RepartoAplicadoDto =
        parse(api.post("/v1/turno/$idCierre/reparto", buildJsonObject {
            modo?.let { put("modo", it) }
            presentes?.takeIf { it.isNotEmpty() }?.let { lista ->
                put("presentes", buildJsonArray { lista.forEach { add(JsonPrimitive(it)) } })
            }
        }))

    // ── Meseros ─────────────────────────────────────────────────────────────
    /** idMesero null = alta. codigo = PIN de acceso rápido (opcional). */
    suspend fun guardarMesero(
        idMesero: Int?, nombre: String, codigo: String?, activo: Boolean = true
    ): MeseroGuardadoDto =
        parse(api.post("/v1/meseros", buildJsonObject {
            idMesero?.let { put("idMesero", it) }
            put("nombre", nombre)
            codigo?.takeIf { it.isNotBlank() }?.let { put("codigo", it) }
            put("activo", activo)
        }))

    suspend fun validarPin(pin: String): PinValidadoDto =
        parse(api.post("/v1/meseros/validar-pin", buildJsonObject { put("pin", pin) }))
}
