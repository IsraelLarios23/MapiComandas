package com.example.mapicomandas.ui.screens.vincular

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapicomandas.SessionManager
import com.example.mapicomandas.data.api.IdentidadService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VinculacionUiState(
    val apiUrl: String = "",
    val codigo: String = "",
    val cargando: Boolean = false,
    val error: String? = null,
    val vinculado: Boolean = false,
    val negocio: String = ""
)

@HiltViewModel
class VinculacionViewModel @Inject constructor(
    private val identidad: IdentidadService,
    private val session: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VinculacionUiState(apiUrl = session.apiBaseUrl, negocio = session.negocio)
    )
    val uiState: StateFlow<VinculacionUiState> = _uiState

    fun setApiUrl(v: String) { _uiState.value = _uiState.value.copy(apiUrl = v) }
    fun setCodigo(v: String) { _uiState.value = _uiState.value.copy(codigo = v.uppercase().take(8)) }

    fun vincular() {
        val s = _uiState.value
        if (s.codigo.length != 8) {
            _uiState.value = s.copy(error = "El código es de 8 caracteres")
            return
        }
        _uiState.value = s.copy(cargando = true, error = null)
        viewModelScope.launch {
            // Guarda la URL de la API antes de vincular (el cliente HTTP la usa).
            session.guardarApiBaseUrl(s.apiUrl.trim())
            try {
                identidad.vincular(s.codigo)
                _uiState.value = _uiState.value.copy(cargando = false, vinculado = true, negocio = session.negocio)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(cargando = false, error = e.message ?: "No se pudo vincular")
            }
        }
    }

    fun limpiarError() { _uiState.value = _uiState.value.copy(error = null) }
}
