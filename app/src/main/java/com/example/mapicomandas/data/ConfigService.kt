package com.example.mapicomandas.data

import com.example.mapicomandas.SessionManager
import com.example.mapicomandas.data.repository.RestauranteRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lee la tabla dbo.ConfiguracionSistema (Clave/Valor) de MapiPOS y cachea los valores.
 * Interpreta los booleanos igual que MapiPOS: TRUE (case-insensitive) o "1".
 */
@Singleton
class ConfigService @Inject constructor(
    private val repo: RestauranteRepository,
    private val session: SessionManager
) {
    @Volatile
    private var mapa: Map<String, String>? = null

    suspend fun cargar() {
        mapa = runCatching {
            repo.obtenerConfiguracion(session.idTienda, session.idCaja)
                .associate { it.clave.trim().uppercase() to (it.valor.trim()) }
        }.getOrNull()
    }

    private suspend fun asegurar(): Map<String, String> {
        if (mapa == null) cargar()
        return mapa ?: emptyMap()
    }

    fun refrescar() { mapa = null }

    suspend fun bool(clave: String, default: Boolean = false): Boolean {
        val v = asegurar()[clave.uppercase()] ?: return default
        return v.equals("TRUE", ignoreCase = true) || v == "1"
    }

    suspend fun texto(clave: String, default: String = ""): String =
        asegurar()[clave.uppercase()] ?: default

    suspend fun numero(clave: String, default: Double = 0.0): Double =
        asegurar()[clave.uppercase()]?.toDoubleOrNull() ?: default
}
