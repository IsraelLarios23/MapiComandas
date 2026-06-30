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
import kotlinx.coroutines.withTimeout
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
    val impresoraTicket: String = "",
    val fastFood: Boolean = false,
    // NetPay (valores de prueba MapiPOS por defecto)
    val npBaseUrl: String = "https://api-154.api-netpay.com",
    val npOAuthPath: String = "/oauth-service/oauth/token",
    val npSalePath: String = "/integration-service/transactions/sale",
    val npAuthString: String = "dHJ1c3RlZC1hcHA6c2VjcmV0",
    val npUsername: String = "Nacional",
    val npPassword: String = "netpay",
    val npSerial: String = "",
    val npStoreId: String = "9194",
    val npGuardando: Boolean = false,
    val npGuardado: Boolean = false,
    val npProbando: Boolean = false,
    val npResultadoPrueba: String? = null,
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
    private val db: JdbcDataSource,
    private val repo: com.example.mapicomandas.data.repository.RestauranteRepository,
    private val configService: com.example.mapicomandas.data.ConfigService,
    private val netPayService: com.example.mapicomandas.data.netpay.NetPayService
) : ViewModel() {

    private val _uiState = MutableStateFlow(cargarEstadoInicial())
    val uiState: StateFlow<ConfigUiState> = _uiState

    init { cargarNetPay() }

    private fun cargarNetPay() {
        if (!session.estaConfigurado) return
        viewModelScope.launch {
            runCatching {
                configService.cargar()
                val s = _uiState.value
                // Solo sobrescribe con lo de BD si tiene valor; mantiene defaults de prueba
                suspend fun pick(clave: String, actual: String) =
                    configService.texto(clave).ifBlank { actual }
                // Auto-corrige rutas viejas guardadas (/gateway/ o /oauth/token)
                fun rutaOAuth(v: String) =
                    if (v.contains("/gateway/") || v == "/oauth/token" || v.isBlank())
                        "/oauth-service/oauth/token" else v
                fun rutaSale(v: String) =
                    if (v.contains("/gateway/") || v.isBlank())
                        "/integration-service/transactions/sale" else v
                _uiState.value = s.copy(
                    npBaseUrl = pick("NetPayBaseUrl", s.npBaseUrl),
                    npOAuthPath = rutaOAuth(pick("NetPayOAuthPath", s.npOAuthPath)),
                    npSalePath = rutaSale(pick("NetPaySalePath", s.npSalePath)),
                    npAuthString = pick("NetPayAuthString", s.npAuthString),
                    npUsername = pick("NetPayUsername", s.npUsername),
                    npPassword = pick("NetPayPassword", s.npPassword),
                    npSerial = pick("NetPaySerialNumber", s.npSerial),
                    npStoreId = pick("NetPayStoreId", s.npStoreId)
                )
            }
        }
    }

    fun probarNetPay() {
        val s = _uiState.value
        _uiState.value = s.copy(npProbando = true, npResultadoPrueba = null)
        viewModelScope.launch {
            val cfg = com.example.mapicomandas.data.netpay.NetPayConfig(
                baseUrl = s.npBaseUrl.trim(),
                oauthPath = s.npOAuthPath.trim(),
                salePath = s.npSalePath.trim(),
                authString = s.npAuthString.trim(),
                username = s.npUsername.trim(),
                password = s.npPassword,
                serialNumber = s.npSerial.trim(),
                storeId = s.npStoreId.trim()
            )
            val error = netPayService.probarCredenciales(cfg)
            _uiState.value = _uiState.value.copy(
                npProbando = false,
                npResultadoPrueba = error ?: "✅ Token OAuth obtenido — credenciales válidas"
            )
        }
    }

    fun limpiarResultadoNetPay() {
        _uiState.value = _uiState.value.copy(npResultadoPrueba = null)
    }

    fun setNpBaseUrl(v: String) { _uiState.value = _uiState.value.copy(npBaseUrl = v) }
    fun setNpOAuthPath(v: String) { _uiState.value = _uiState.value.copy(npOAuthPath = v) }
    fun setNpSalePath(v: String) { _uiState.value = _uiState.value.copy(npSalePath = v) }
    fun setNpAuthString(v: String) { _uiState.value = _uiState.value.copy(npAuthString = v) }
    fun setNpUsername(v: String) { _uiState.value = _uiState.value.copy(npUsername = v) }
    fun setNpPassword(v: String) { _uiState.value = _uiState.value.copy(npPassword = v) }
    fun setNpSerial(v: String) { _uiState.value = _uiState.value.copy(npSerial = v) }
    fun setNpStoreId(v: String) { _uiState.value = _uiState.value.copy(npStoreId = v) }

    fun guardarNetPay() {
        val s = _uiState.value
        _uiState.value = s.copy(npGuardando = true, npGuardado = false)
        viewModelScope.launch {
            val r = runCatching {
                repo.guardarConfig("NetPayBaseUrl", s.npBaseUrl.trim())
                repo.guardarConfig("NetPayOAuthPath", s.npOAuthPath.trim())
                repo.guardarConfig("NetPaySalePath", s.npSalePath.trim())
                repo.guardarConfig("NetPayAuthString", s.npAuthString.trim())
                repo.guardarConfig("NetPayUsername", s.npUsername.trim())
                repo.guardarConfig("NetPayPassword", s.npPassword)
                repo.guardarConfig("NetPaySerialNumber", s.npSerial.trim())
                repo.guardarConfig("NetPayStoreId", s.npStoreId.trim())
                configService.refrescar()
            }
            _uiState.value = _uiState.value.copy(
                npGuardando = false,
                npGuardado = r.isSuccess,
                error = r.exceptionOrNull()?.message
            )
        }
    }

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
            ssl = cfg.ssl,
            impresoraTicket = cfg.impresoraTicket,
            fastFood = session.fastFoodActivo
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
    fun setImpresoraTicket(v: String) { _uiState.value = _uiState.value.copy(impresoraTicket = v) }
    fun setFastFood(v: Boolean) {
        _uiState.value = _uiState.value.copy(fastFood = v)
        session.setFastFood(v)
    }

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
                ssl = s.ssl,
                impresoraTicket = s.impresoraTicket.trim()
            )
            // Guardar primero para que JdbcDataSource use la nueva config
            session.guardarDbConfig(
                config = cfg,
                idTienda = s.idTienda.toIntOrNull() ?: 1,
                idCaja = s.idCaja.toIntOrNull() ?: 1,
                idAlmacen = s.idAlmacen.toIntOrNull() ?: 1
            )
            db.invalidate()

            val resultado = runCatching {
                withTimeout(40_000) {
                    withContext(Dispatchers.IO) {
                        db.getConnection().also { it.createStatement().execute("SELECT 1") }
                    }
                }
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
