package com.example.mapicomandas.ui.screens.pendientes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapicomandas.data.api.AjustesService
import com.example.mapicomandas.data.api.dto.AccionComandaDto
import com.example.mapicomandas.data.api.dto.PendientePagoDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PendientesUiState(
    val pendientes: List<PendientePagoDto> = emptyList(),
    val bitacora: List<AccionComandaDto> = emptyList(),
    val cargando: Boolean = false,
    val error: String? = null
)

/** "Pagos en caja" del desktop: cuentas por cobrar + bitácora de acciones del día. */
@HiltViewModel
class PendientesViewModel @Inject constructor(
    private val ajustes: AjustesService
) : ViewModel() {

    private val _uiState = MutableStateFlow(PendientesUiState())
    val uiState: StateFlow<PendientesUiState> = _uiState

    init { cargar() }

    fun cargar() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true)
            try {
                val pendientes = ajustes.pendientesPago()
                val bitacora = runCatching { ajustes.bitacora() }.getOrDefault(emptyList())
                _uiState.value = _uiState.value.copy(
                    pendientes = pendientes, bitacora = bitacora, cargando = false, error = null
                )
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(cargando = false, error = e.message)
            }
        }
    }

    fun limpiarError() { _uiState.value = _uiState.value.copy(error = null) }
}
