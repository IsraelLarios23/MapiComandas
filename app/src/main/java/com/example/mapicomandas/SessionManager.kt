package com.example.mapicomandas

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class DbConfig(
    val host: String = "",
    val puerto: Int = 1433,
    val baseDatos: String = "",
    val usuario: String = "",
    val password: String = ""
)

data class Sesion(
    val idTienda: Int = 1,
    val idCaja: Int = 1,
    val idUsuario: Int = 1,
    val idAlmacen: Int = 1,
    val idMesero: Int = 1,
    val nombreMesero: String = "",
    val cajaHabilitada: Boolean = false,
    val dbConfig: DbConfig = DbConfig()
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
        dbConfig = DbConfig(
            host = prefs.getString("dbHost", "") ?: "",
            puerto = prefs.getInt("dbPuerto", 1433),
            baseDatos = prefs.getString("dbNombre", "") ?: "",
            usuario = prefs.getString("dbUsuario", "") ?: "",
            password = prefs.getString("dbPassword", "") ?: ""
        )
    )

    fun guardarDbConfig(config: DbConfig, idTienda: Int, idCaja: Int, idAlmacen: Int) {
        prefs.edit()
            .putString("dbHost", config.host)
            .putInt("dbPuerto", config.puerto)
            .putString("dbNombre", config.baseDatos)
            .putString("dbUsuario", config.usuario)
            .putString("dbPassword", config.password)
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

    val dbConfig get() = _sesion.value.dbConfig
    val idTienda get() = _sesion.value.idTienda
    val idCaja get() = _sesion.value.idCaja
    val idAlmacen get() = _sesion.value.idAlmacen
    val idMesero get() = _sesion.value.idMesero
    val idUsuario get() = _sesion.value.idUsuario
    val cajaHabilitada get() = _sesion.value.cajaHabilitada
    val estaConfigurado get() = _sesion.value.dbConfig.host.isNotBlank()
}
