package com.example.mapicomandas.ui.screens.kds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapicomandas.data.model.GrupoKds
import com.example.mapicomandas.data.model.PlatilloKds
import com.example.mapicomandas.data.model.PuntoImpresion
import com.example.mapicomandas.data.repository.RestauranteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KdsUiState(
    val grupos: List<GrupoKds> = emptyList(),
    val puntos: List<PuntoImpresion> = emptyList(),
    val puntoSeleccionado: Int? = null,
    val cargando: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class KdsViewModel @Inject constructor(
    private val repo: RestauranteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(KdsUiState())
    val uiState: StateFlow<KdsUiState> = _uiState

    private var pollingJob: Job? = null

    init {
        cargarPuntos()
        iniciarPolling()
    }

    private fun cargarPuntos() {
        viewModelScope.launch {
            try {
                val puntos = repo.obtenerPuntosImpresion()
                _uiState.value = _uiState.value.copy(puntos = puntos)
            } catch (_: Exception) {}
        }
    }

    private fun iniciarPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                cargarPlatillos()
                delay(5_000)
            }
        }
    }

    private fun cargarPlatillos() {
        viewModelScope.launch {
            try {
                val platillos = repo.obtenerPlatillosCocina(_uiState.value.puntoSeleccionado)
                val grupos = agruparPorComanda(platillos)
                _uiState.value = _uiState.value.copy(grupos = grupos, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    private fun agruparPorComanda(platillos: List<PlatilloKds>): List<GrupoKds> =
        platillos.groupBy { it.idComanda }.map { (idComanda, items) ->
            GrupoKds(
                idComanda = idComanda,
                folio = items.first().folio,
                mesa = items.first().mesa,
                platillos = items,
                maxMinutosTranscurridos = items.mapNotNull { it.minutosTranscurridos }.maxOrNull() ?: 0
            )
        }.sortedBy { grupo ->
            grupo.platillos.mapNotNull { it.minutosTranscurridos }.maxOrNull() ?: 0
        }

    fun seleccionarPunto(idPunto: Int?) {
        _uiState.value = _uiState.value.copy(puntoSeleccionado = idPunto)
        cargarPlatillos()
    }

    fun marcarListo(idDetalle: Int) {
        viewModelScope.launch {
            try {
                repo.marcarListo(idDetalle)
                cargarPlatillos()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun marcarEntregado(idDetalle: Int) {
        viewModelScope.launch {
            try {
                repo.marcarEntregado(idDetalle)
                cargarPlatillos()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
