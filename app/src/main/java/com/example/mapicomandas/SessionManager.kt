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
    val baseUrl: String = ""
)

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "mapi_session",
        masterKey,
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
        baseUrl = prefs.getString("baseUrl", "http://10.0.2.2:5000") ?: "http://10.0.2.2:5000"
    )

    fun guardarConfiguracion(baseUrl: String, idTienda: Int, idCaja: Int, idAlmacen: Int) {
        prefs.edit()
            .putString("baseUrl", baseUrl)
            .putInt("idTienda", idTienda)
            .putInt("idCaja", idCaja)
            .putInt("idAlmacen", idAlmacen)
            .apply()
        _sesion.value = cargarSesion()
    }

    fun setMesero(idMesero: Int, nombre: String) {
        prefs.edit()
            .putInt("idMesero", idMesero)
            .putString("nombreMesero", nombre)
            .apply()
        _sesion.value = _sesion.value.copy(idMesero = idMesero, nombreMesero = nombre)
    }

    fun setCajaHabilitada(habilitada: Boolean) {
        prefs.edit().putBoolean("cajaHabilitada", habilitada).apply()
        _sesion.value = _sesion.value.copy(cajaHabilitada = habilitada)
    }

    fun setUsuario(idUsuario: Int) {
        prefs.edit().putInt("idUsuario", idUsuario).apply()
        _sesion.value = _sesion.value.copy(idUsuario = idUsuario)
    }

    val baseUrl get() = _sesion.value.baseUrl
    val idTienda get() = _sesion.value.idTienda
    val idCaja get() = _sesion.value.idCaja
    val idAlmacen get() = _sesion.value.idAlmacen
    val idMesero get() = _sesion.value.idMesero
    val idUsuario get() = _sesion.value.idUsuario
    val cajaHabilitada get() = _sesion.value.cajaHabilitada
}
