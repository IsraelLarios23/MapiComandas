package com.example.mapicomandas.ui.screens.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapicomandas.DbConfig
import com.example.mapicomandas.SessionManager
import com.example.mapicomandas.data.db.JdbcDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ConfigUiState(
    val host: String = "",
    val puerto: String = "1433",
    val baseDatos: String = "",
    val usuario: String = "",
    val password: String = "",
    val idTienda: String = "1",
    val idCaja: String = "1",
    val idAlmacen: String = "1",
    val ssl: String = "off",
    val probando: Boolean = false,
    val conectado: Boolean = false,
    val error: String? = null
) {
    companion object {
        val OPCIONES_SSL = listOf("off", "request", "require", "authenticate")
    }
}

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val session: SessionManager,
    private val db: JdbcDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(cargarEstadoInicial())
    val uiState: StateFlow<ConfigUiState> = _uiState

    private fun cargarEstadoInicial(): ConfigUiState {
        val cfg = session.dbConfig
        return ConfigUiState(
            host = cfg.host,
            puerto = cfg.puerto.toString(),
            baseDatos = cfg.baseDatos,
            usuario = cfg.usuario,
            password = cfg.password,
            idTienda = session.idTienda.toString(),
            idCaja = session.idCaja.toString(),
            idAlmacen = session.idAlmacen.toString(),
            ssl = cfg.ssl
        )
    }

    fun setHost(v: String) { _uiState.value = _uiState.value.copy(host = v) }
    fun setPuerto(v: String) { _uiState.value = _uiState.value.copy(puerto = v) }
    fun setBaseDatos(v: String) { _uiState.value = _uiState.value.copy(baseDatos = v) }
    fun setUsuario(v: String) { _uiState.value = _uiState.value.copy(usuario = v) }
    fun setPassword(v: String) { _uiState.value = _uiState.value.copy(password = v) }
    fun setIdTienda(v: String) { _uiState.value = _uiState.value.copy(idTienda = v) }
    fun setIdCaja(v: String) { _uiState.value = _uiState.value.copy(idCaja = v) }
    fun setIdAlmacen(v: String) { _uiState.value = _uiState.value.copy(idAlmacen = v) }
    fun setSsl(v: String) { _uiState.value = _uiState.value.copy(ssl = v) }

    fun probarYGuardar() {
        val s = _uiState.value
        if (s.host.isBlank() || s.baseDatos.isBlank() || s.usuario.isBlank()) {
            _uiState.value = s.copy(error = "Completa servidor, base de datos y usuario")
            return
        }
        _uiState.value = s.copy(probando = true, error = null)

        viewModelScope.launch {
            val cfg = DbConfig(
                host = s.host.trim(),
                puerto = s.puerto.toIntOrNull() ?: 1433,
                baseDatos = s.baseDatos.trim(),
                usuario = s.usuario.trim(),
                password = s.password,
                ssl = s.ssl
            )
            // Guardar primero para que JdbcDataSource use la nueva config
            session.guardarDbConfig(
                config = cfg,
                idTienda = s.idTienda.toIntOrNull() ?: 1,
                idCaja = s.idCaja.toIntOrNull() ?: 1,
                idAlmacen = s.idAlmacen.toIntOrNull() ?: 1
            )
            db.invalidate()

            val resultado = withContext(Dispatchers.IO) {
                runCatching { db.getConnection().also { it.createStatement().execute("SELECT 1") } }
            }

            if (resultado.isSuccess) {
                _uiState.value = _uiState.value.copy(probando = false, conectado = true)
            } else {
                val msg = resultado.exceptionOrNull()?.message ?: "Error de conexión"
                _uiState.value = _uiState.value.copy(probando = false, error = msg)
            }
        }
    }

    fun limpiarError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
