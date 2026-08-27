package com.example.mapicomandas.ui.screens.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapicomandas.SessionManager
import com.example.mapicomandas.data.ConfigService
import com.example.mapicomandas.data.api.IdentidadService
import com.example.mapicomandas.data.netpay.NetPayConfig
import com.example.mapicomandas.data.netpay.NetPayService
import com.example.mapicomandas.data.repository.RestauranteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConfigUiState(
    val apiUrl: String = "https://api.mapi.codesi.mx",
    val vinculado: Boolean = false,
    val negocio: String = "",
    val impresoraTicket: String = "",
    val fastFood: Boolean = false,
    val modoVista: String = "auto",   // auto | telefono | tableta
    val propinaGlobal: String = "",   // % sugerido (REST_PROPINA_GLOBAL, se guarda por API)
    // NetPay (config viaja por la API central; se edita aquí y se guarda con PUT /v1/config)
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
    val guardado: Boolean = false,
    val desvinculado: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val session: SessionManager,
    private val repo: RestauranteRepository,
    private val configService: ConfigService,
    private val identidad: IdentidadService,
    private val netPayService: NetPayService
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ConfigUiState(
            apiUrl = session.apiBaseUrl,
            vinculado = session.estaVinculado,
            negocio = session.negocio,
            impresoraTicket = session.impresoraTicket,
            fastFood = session.fastFoodActivo,
            modoVista = session.modoVista.value
        )
    )
    val uiState: StateFlow<ConfigUiState> = _uiState

    init {
        cargarNetPay()
        viewModelScope.launch { runCatching { netPayService.iniciarReceptor() } }
    }

    private fun cargarNetPay() {
        if (!session.estaVinculado) return
        viewModelScope.launch {
            runCatching {
                configService.cargar()
                val s = _uiState.value
                suspend fun pick(clave: String, actual: String) = configService.texto(clave).ifBlank { actual }
                _uiState.value = s.copy(
                    npBaseUrl = pick("NetPayBaseUrl", s.npBaseUrl),
                    npAuthString = pick("NetPayAuthString", s.npAuthString),
                    npUsername = pick("NetPayUsername", s.npUsername),
                    npPassword = pick("NetPayPassword", s.npPassword),
                    // Serial: local del dispositivo primero; global solo de respaldo
                    npSerial = session.netpaySerialLocal.ifBlank { pick("NetPaySerialNumber", s.npSerial) },
                    npStoreId = pick("NetPayStoreId", s.npStoreId),
                    propinaGlobal = configService.texto("REST_PROPINA_GLOBAL").ifBlank { s.propinaGlobal }
                )
            }
        }
    }

    // ── conexión / vinculación ──────────────────────────────────────────────────
    fun setApiUrl(v: String) { _uiState.value = _uiState.value.copy(apiUrl = v) }

    fun guardarGeneral() {
        val s = _uiState.value
        session.guardarApiBaseUrl(s.apiUrl.trim())
        session.guardarImpresora(s.impresoraTicket.trim())
        // Propina sugerida global (la lee /v1/comandas/{id}/propina-sugerida en el server)
        viewModelScope.launch {
            runCatching {
                if (s.propinaGlobal.isNotBlank())
                    repo.guardarConfig("REST_PROPINA_GLOBAL", s.propinaGlobal.trim())
                configService.refrescar()
            }
        }
        _uiState.value = _uiState.value.copy(guardado = true)
    }

    fun setPropinaGlobal(v: String) {
        _uiState.value = _uiState.value.copy(propinaGlobal = v.filter { it.isDigit() || it == '.' })
    }

    fun desvincular() {
        viewModelScope.launch {
            runCatching { identidad.desvincular() }
            _uiState.value = _uiState.value.copy(desvinculado = true)
        }
    }

    // ── impresora / fast food ───────────────────────────────────────────────────
    fun setImpresoraTicket(v: String) { _uiState.value = _uiState.value.copy(impresoraTicket = v) }
    fun setFastFood(v: Boolean) {
        _uiState.value = _uiState.value.copy(fastFood = v)
        session.setFastFood(v)
    }

    fun setModoVista(v: String) {
        _uiState.value = _uiState.value.copy(modoVista = v)
        session.setModoVista(v)
    }

    // ── NetPay ──────────────────────────────────────────────────────────────────
    fun setNpBaseUrl(v: String) { _uiState.value = _uiState.value.copy(npBaseUrl = v) }
    fun setNpAuthString(v: String) { _uiState.value = _uiState.value.copy(npAuthString = v) }
    fun setNpUsername(v: String) { _uiState.value = _uiState.value.copy(npUsername = v) }
    fun setNpPassword(v: String) { _uiState.value = _uiState.value.copy(npPassword = v) }
    fun setNpSerial(v: String) { _uiState.value = _uiState.value.copy(npSerial = v) }
    fun setNpStoreId(v: String) { _uiState.value = _uiState.value.copy(npStoreId = v) }

    fun probarNetPay() {
        val s = _uiState.value
        _uiState.value = s.copy(npProbando = true, npResultadoPrueba = null)
        viewModelScope.launch {
            val cfg = NetPayConfig(
                baseUrl = s.npBaseUrl.trim(), oauthPath = s.npOAuthPath.trim(), salePath = s.npSalePath.trim(),
                authString = s.npAuthString.trim(), username = s.npUsername.trim(), password = s.npPassword,
                serialNumber = s.npSerial.trim(), storeId = s.npStoreId.trim()
            )
            val error = netPayService.probarCredenciales(cfg)
            _uiState.value = _uiState.value.copy(
                npProbando = false,
                npResultadoPrueba = error ?: "✅ Token OAuth obtenido — credenciales válidas"
            )
        }
    }

    fun guardarNetPay() {
        val s = _uiState.value
        _uiState.value = s.copy(npGuardando = true, npGuardado = false)
        viewModelScope.launch {
            val r = runCatching {
                // Config compartida del negocio → API (PUT /v1/config, clave global)
                repo.guardarConfig("NetPayBaseUrl", s.npBaseUrl.trim())
                repo.guardarConfig("NetPayAuthString", s.npAuthString.trim())
                repo.guardarConfig("NetPayUsername", s.npUsername.trim())
                repo.guardarConfig("NetPayPassword", s.npPassword)
                repo.guardarConfig("NetPayStoreId", s.npStoreId.trim())
                // Serial de la terminal = POR DISPOSITIVO → local (dos tablets no se pisan)
                session.guardarNetpaySerialLocal(s.npSerial)
                configService.refrescar()
                netPayService.iniciarReceptor()
            }
            _uiState.value = _uiState.value.copy(
                npGuardando = false, npGuardado = r.isSuccess, error = r.exceptionOrNull()?.message
            )
        }
    }

    fun limpiarError() { _uiState.value = _uiState.value.copy(error = null) }
    fun limpiarResultadoNetPay() { _uiState.value = _uiState.value.copy(npResultadoPrueba = null) }
}
