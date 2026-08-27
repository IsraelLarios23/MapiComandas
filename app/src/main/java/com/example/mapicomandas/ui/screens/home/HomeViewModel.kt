package com.example.mapicomandas.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapicomandas.SessionManager
import com.example.mapicomandas.data.ConfigService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Qué módulos opcionales usa este negocio (claves REST_MOD_* en la config central). */
data class ModulosHome(
    val domicilio: Boolean = true,
    val reservaciones: Boolean = true,
    val disponibilidad: Boolean = true,
    val mermas: Boolean = true,
    val monederos: Boolean = false,
    val habitaciones: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    val session: SessionManager,
    private val config: ConfigService
) : ViewModel() {

    private val _clienteLogo = MutableStateFlow<String?>(null)
    val clienteLogo: StateFlow<String?> = _clienteLogo

    private val _modulos = MutableStateFlow(ModulosHome())
    val modulos: StateFlow<ModulosHome> = _modulos

    init {
        viewModelScope.launch {
            // Logo del cliente (base64) opcional desde la config central (GET /v1/config)
            _clienteLogo.value = runCatching {
                config.texto("LOGOCLIENTE").ifBlank { config.texto("LOGO_CLIENTE") }
            }.getOrNull()?.ifBlank { null }
        }
        recargarModulos()
    }

    /** Lee de la config del negocio qué módulos se muestran (se llama al volver al Home). */
    fun recargarModulos() {
        viewModelScope.launch {
            runCatching {
                _modulos.value = ModulosHome(
                    domicilio = config.bool("REST_MOD_DOMICILIO", true),
                    reservaciones = config.bool("REST_MOD_RESERVACIONES", true),
                    disponibilidad = config.bool("REST_MOD_DISPONIBILIDAD", true),
                    mermas = config.bool("REST_MOD_MERMAS", true),
                    monederos = config.bool("REST_MOD_MONEDEROS", false),
                    habitaciones = config.bool("REST_MOD_HABITACIONES", false)
                )
            }
        }
    }
}
