package com.example.mapicomandas.ui.screens.reportes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapicomandas.data.model.ReportesDia
import com.example.mapicomandas.data.repository.RestauranteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportesUiState(
    val reportes: ReportesDia? = null,
    val fecha: String? = null,      // null = hoy
    val cargando: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ReportesViewModel @Inject constructor(
    private val repo: RestauranteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportesUiState())
    val uiState: StateFlow<ReportesUiState> = _uiState

    init { cargar(null) }

    fun cargar(fecha: String?) {
        _uiState.value = _uiState.value.copy(cargando = true, fecha = fecha)
        viewModelScope.launch {
            try {
                val r = repo.obtenerReportesDia(fecha)
                _uiState.value = _uiState.value.copy(reportes = r, cargando = false, error = null)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(cargando = false, error = e.message)
            }
        }
    }

    fun limpiarError() { _uiState.value = _uiState.value.copy(error = null) }
}
