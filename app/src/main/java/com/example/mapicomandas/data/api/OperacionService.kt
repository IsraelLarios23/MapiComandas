package com.example.mapicomandas.data.api

import com.example.mapicomandas.data.api.dto.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Operación del restaurante contra la API central: tiempo de mesa,
 * plataformas delivery, menú por caja y disponibilidad de porciones.
 */
@Singleton
class OperacionService @Inject constructor(private val api: ApiClient) {

    private inline fun <reified T> parse(el: JsonElement): T = api.json.decodeFromJsonElement<T>(el)

    // ── Tiempo de mesa ──────────────────────────────────────────────────────
    suspend fun tiposMesa(): List<TipoMesaDto> = parse(api.get("/v1/tipos-mesa"))

    suspend fun iniciarTiempo(idComanda: Int): PeriodoTiempoDto =
        parse(api.post("/v1/comandas/$idComanda/tiempo/iniciar"))

    /** [motivoSinCobro] presente = cerrar el reloj SIN asentar renglón. */
    suspend fun detenerTiempo(idComanda: Int, motivoSinCobro: String? = null): CobroTiempoDto =
        parse(api.post("/v1/comandas/$idComanda/tiempo/detener",
            motivoSinCobro?.takeIf { it.isNotBlank() }?.let {
                buildJsonObject { put("motivoSinCobro", it) }
            }))

    suspend fun tiemposActivos(): List<RelojActivoDto> = parse(api.get("/v1/tiempos-activos"))

    // ── Plataformas delivery ────────────────────────────────────────────────
    suspend fun plataformas(): List<PlataformaDto> = parse(api.get("/v1/plataformas"))

    suspend fun guardarPlataforma(
        nombre: String, comisionPct: Double, idPlataforma: Int? = null, activo: Boolean? = null
    ): PlataformaDto =
        parse(api.post("/v1/plataformas", buildJsonObject {
            put("nombre", nombre); put("comisionPct", comisionPct)
            idPlataforma?.let { put("idPlataforma", it) }
            activo?.let { put("activo", it) }
        }))

    /** Abre comanda de plataforma (TipoServicio 4, folio K). */
    suspend fun abrirPorPlataforma(
        idPlataforma: Int, referencia: String?, cliente: String?, observaciones: String?
    ): ComandaPlataformaDto =
        parse(api.post("/v1/domicilio/plataforma", buildJsonObject {
            put("idPlataforma", idPlataforma)
            referencia?.takeIf { it.isNotBlank() }?.let { put("referencia", it) }
            cliente?.takeIf { it.isNotBlank() }?.let { put("cliente", it) }
            observaciones?.takeIf { it.isNotBlank() }?.let { put("observaciones", it) }
        }))

    /** Edición parcial del domicilio: solo lo NO nulo se toca. */
    suspend fun actualizarDomicilio(
        idComanda: Int, nombreCliente: String? = null, telefonoCliente: String? = null,
        direccionEntrega: String? = null, idRepartidor: Int? = null,
        idZonaReparto: Int? = null, cargoEntrega: Double? = null
    ): DomicilioActualizadoDto =
        parse(api.put("/v1/domicilio/comandas/$idComanda", buildJsonObject {
            nombreCliente?.let { put("nombreCliente", it) }
            telefonoCliente?.let { put("telefonoCliente", it) }
            direccionEntrega?.let { put("direccionEntrega", it) }
            idRepartidor?.let { put("idRepartidor", it) }
            idZonaReparto?.let { put("idZonaReparto", it) }
            cargoEntrega?.let { put("cargoEntrega", it) }
        }))

    // ── Menú por caja / disponibilidad ──────────────────────────────────────
    /** Listas vacías = sin restricción (pintar catálogo completo). */
    suspend fun menuCaja(): MenuCajaDto = parse(api.get("/v1/menu-caja"))

    suspend fun disponibilidad(): List<DisponibilidadDto> = parse(api.get("/v1/disponibilidad"))
}
