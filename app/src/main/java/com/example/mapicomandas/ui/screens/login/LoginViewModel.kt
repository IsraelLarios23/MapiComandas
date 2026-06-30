package com.example.mapicomandas.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapicomandas.SessionManager
import com.example.mapicomandas.data.ConfigService
import com.example.mapicomandas.data.repository.RestauranteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val usuario: String = "",
    val password: String = "",
    val cargando: Boolean = false,
    val error: String? = null,
    val autenticado: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repo: RestauranteRepository,
    val session: SessionManager,
    private val configService: ConfigService
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    fun setUsuario(v: String) { _uiState.value = _uiState.value.copy(usuario = v) }
    fun setPassword(v: String) { _uiState.value = _uiState.value.copy(password = v) }

    fun ingresar() {
        val s = _uiState.value
        if (s.usuario.isBlank()) {
            _uiState.value = s.copy(error = "Ingresa tu usuario")
            return
        }
        _uiState.value = s.copy(cargando = true, error = null)
        viewModelScope.launch {
            try {
                val usuario = repo.login(s.usuario.trim(), s.password)
                if (usuario != null) {
                    session.iniciarSesion(usuario.idUsuario, usuario.nombre)
                    configService.cargar()   // cachea ConfiguracionSistema (REST_*)
                    _uiState.value = _uiState.value.copy(cargando = false, autenticado = true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        cargando = false,
                        error = "Usuario o contraseña incorrectos"
                    )
                }
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(cargando = false, error = e.message)
            }
        }
    }

    fun limpiarError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
