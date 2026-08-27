package com.example.mapicomandas

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class Sesion(
    val idTienda: Int = 1,
    val idCaja: Int = 1,
    val idUsuario: Int = 1,
    val idAlmacen: Int = 1,
    val idMesero: Int = 1,
    val nombreMesero: String = "",
    val cajaHabilitada: Boolean = false,
    val impresoraTicket: String = ""   // IP/IP:puerto de la impresora ESC/POS (local a la tablet)
)

/**
 * Identidad y ajustes locales. La app se configura SOLO con el código de vinculación
 * (como las otras apps): NO guarda credenciales SQL — todo el dato viaja por la API central.
 * Lo único local es la impresora ESC/POS (Bluetooth/red/USB) y el modo comida rápida.
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context, "mapi_session", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _sesion = MutableStateFlow(cargarSesion())
    val sesion: StateFlow<Sesion> = _sesion

    private fun cargarSesion() = Sesion(
        idTienda = prefs.getInt("idTienda", 1),
        idCaja = prefs.getInt("idCaja", 1),
        idUsuario = prefs.getInt("idUsuario", 1),
        idAlmacen = prefs.getInt("idAlmacen", 1),
        idMesero = prefs.getInt("idMesero", 1),
        nombreMesero = prefs.getString("nombreMesero", "") ?: "",
        cajaHabilitada = prefs.getBoolean("cajaHabilitada", false),
        impresoraTicket = prefs.getString("impresoraTicket", "") ?: ""
    )

    /** Guarda la impresora ESC/POS local. */
    fun guardarImpresora(impresora: String) {
        prefs.edit().putString("impresoraTicket", impresora).apply()
        _sesion.value = cargarSesion()
    }

    fun setMesero(idMesero: Int, nombre: String) {
        prefs.edit().putInt("idMesero", idMesero).putString("nombreMesero", nombre).apply()
        _sesion.value = _sesion.value.copy(idMesero = idMesero, nombreMesero = nombre)
    }

    fun setCajaHabilitada(habilitada: Boolean) {
        prefs.edit().putBoolean("cajaHabilitada", habilitada).apply()
        _sesion.value = _sesion.value.copy(cajaHabilitada = habilitada)
    }

    private val _nombreUsuario = MutableStateFlow(prefs.getString("nombreUsuario", "") ?: "")
    val nombreUsuario: StateFlow<String> = _nombreUsuario

    fun iniciarSesion(idUsuario: Int, nombre: String) {
        prefs.edit().putInt("idUsuario", idUsuario).putString("nombreUsuario", nombre).apply()
        _sesion.value = _sesion.value.copy(idUsuario = idUsuario)
        _nombreUsuario.value = nombre
    }

    fun cerrarSesion() {
        prefs.edit().remove("nombreUsuario").apply()
        _nombreUsuario.value = ""
    }

    val haIniciadoSesion get() = _nombreUsuario.value.isNotBlank()

    // ── Modo de vista: "auto" (por ancho de pantalla) | "telefono" | "tableta" ──
    private val _modoVista = MutableStateFlow(prefs.getString("modoVista", "auto") ?: "auto")
    val modoVista: StateFlow<String> = _modoVista
    fun setModoVista(modo: String) {
        prefs.edit().putString("modoVista", modo).apply()
        _modoVista.value = modo
    }

    // Modo Comida Rápida (REST_COMIDA_RAPIDA)
    private val _fastFood = MutableStateFlow(prefs.getBoolean("fastFood", false))
    val fastFood: StateFlow<Boolean> = _fastFood
    val fastFoodActivo get() = _fastFood.value

    fun setFastFood(activo: Boolean) {
        prefs.edit().putBoolean("fastFood", activo).apply()
        _fastFood.value = activo
    }

    // ── API central (identidad) ───────────────────────────────────────────────
    val apiBaseUrl get() = prefs.getString("apiBaseUrl", "https://api.mapi.codesi.mx") ?: "https://api.mapi.codesi.mx"
    val deviceToken get() = prefs.getString("deviceToken", "") ?: ""
    val sessionToken get() = prefs.getString("sessionToken", "") ?: ""
    val negocio get() = prefs.getString("negocio", "") ?: ""
    val estaVinculado get() = deviceToken.isNotBlank()

    fun guardarApiBaseUrl(url: String) {
        prefs.edit().putString("apiBaseUrl", url.trim().trimEnd('/')).apply()
    }

    /** Token de DISPOSITIVO tras /v1/vincular. */
    fun guardarVinculacion(token: String, negocio: String) {
        prefs.edit().putString("deviceToken", token).putString("negocio", negocio).apply()
    }

    /** Token de SESIÓN tras /v1/login. */
    fun guardarSesionApi(token: String, idUsuario: Int, nombre: String) {
        prefs.edit()
            .putString("sessionToken", token)
            .putInt("idUsuario", idUsuario)
            .putString("nombreUsuario", nombre)
            .apply()
        _sesion.value = _sesion.value.copy(idUsuario = idUsuario)
        _nombreUsuario.value = nombre
    }

    /** Cierra sesión de API (conserva la vinculación del dispositivo). */
    fun cerrarSesionApi() {
        prefs.edit().remove("sessionToken").remove("nombreUsuario").apply()
        _nombreUsuario.value = ""
    }

    /** Desvincula el dispositivo (obliga a re-emparejar). */
    fun desvincular() {
        prefs.edit().remove("deviceToken").remove("sessionToken").remove("nombreUsuario").apply()
        _nombreUsuario.value = ""
    }

    // ── Re-autenticación global (401 de la API en llamadas de datos) ───────────
    // motivo: "login" = renovar sesión · "vincular" = re-emparejar. La observa el NavGraph.
    private val _reautenticar = MutableStateFlow<String?>(null)
    val reautenticar: StateFlow<String?> = _reautenticar
    fun notificarReautenticacion(motivo: String) { _reautenticar.value = motivo }
    fun limpiarReautenticacion() { _reautenticar.value = null }

    // ── Serial de la terminal NetPay: dato POR DISPOSITIVO (no global del negocio).
    // PUT /v1/config escribe la clave GLOBAL; dos tablets se pisarían el serial entre sí,
    // así que se guarda local y la config global queda solo como fallback.
    val netpaySerialLocal get() = prefs.getString("npSerialLocal", "") ?: ""
    fun guardarNetpaySerialLocal(serial: String) {
        prefs.edit().putString("npSerialLocal", serial.trim()).apply()
    }

    // ── accesos ────────────────────────────────────────────────────────────────
    val idTienda get() = _sesion.value.idTienda
    val idCaja get() = _sesion.value.idCaja
    val idAlmacen get() = _sesion.value.idAlmacen
    val idMesero get() = _sesion.value.idMesero
    val idUsuario get() = _sesion.value.idUsuario
    val cajaHabilitada get() = _sesion.value.cajaHabilitada
    val impresoraTicket get() = _sesion.value.impresoraTicket
    val nombreUsuarioActual get() = _nombreUsuario.value
}
