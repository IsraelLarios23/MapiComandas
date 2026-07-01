package com.example.mapicomandas.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapicomandas.SessionManager
import com.example.mapicomandas.data.ConfigService
import com.example.mapicomandas.data.db.JdbcDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    val session: SessionManager,
    db: JdbcDataSource,
    private val config: ConfigService
) : ViewModel() {
    val conectado: StateFlow<Boolean> = db.conectado

    private val _clienteLogo = MutableStateFlow<String?>(null)
    val clienteLogo: StateFlow<String?> = _clienteLogo

    init {
        viewModelScope.launch {
            // Logo del cliente (base64) opcional desde ConfiguracionSistema
            _clienteLogo.value = runCatching {
                config.texto("LOGOCLIENTE").ifBlank { config.texto("LOGO_CLIENTE") }
            }.getOrNull()?.ifBlank { null }
        }
    }
}
