package com.example.mapicomandas.ui.screens.turno

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapicomandas.data.api.TurnoService
import com.example.mapicomandas.data.api.dto.*
import com.example.mapicomandas.data.model.Mesero
import com.example.mapicomandas.data.repository.RestauranteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TurnoUiState(
    // Turno
    val preview: TurnoPreviewDto? = null,
    val cierre: CierreTurnoDto? = null,
    val mostrarConfirmarCierre: Boolean = false,
    // Corte por mesero
    val meseros: List<Mesero> = emptyList(),
    val meseroSel: Mesero? = null,
    val corte: CorteMeseroDto? = null,
    // Propinas
    val reglas: List<ReglaRepartoDto> = emptyList(),
    val reparto: RepartoPreviewDto? = null,
    val repartoModo: String = "puestos",     // puestos | igualitario
    // Meseros (alta/edición)
    val meseroEnEdicion: Mesero? = null,
    val mostrarNuevoMesero: Boolean = false,
    val cargando: Boolean = false,
    val error: String? = null,
    val exito: String? = null
)

/** Turno del restaurante: preview/cierre, corte por mesero, reparto de propinas y meseros. */
@HiltViewModel
class TurnoViewModel @Inject constructor(
    private val turno: TurnoService,
    private val repo: RestauranteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TurnoUiState())
    val uiState: StateFlow<TurnoUiState> = _uiState

    init {
        cargarPreview()
        cargarMeseros()
    }

    // ── Turno ───────────────────────────────────────────────────────────────
    fun cargarPreview() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true)
            try {
                val p = turno.turnoPreview()
                _uiState.value = _uiState.value.copy(preview = p, cargando = false, error = null)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(cargando = false, error = e.message)
            }
        }
    }

    fun setMostrarConfirmarCierre(v: Boolean) {
        _uiState.value = _uiState.value.copy(mostrarConfirmarCierre = v)
    }

    fun cerrarTurno(efectivoReal: Double?, observaciones: String?) {
        viewModelScope.launch {
            try {
                // [desde] del preview = guard anti doble cierre entre cajas
                val c = turno.cerrarTurno(_uiState.value.preview?.desde, efectivoReal, observaciones)
                _uiState.value = _uiState.value.copy(
                    cierre = c, mostrarConfirmarCierre = false,
                    exito = "Turno cerrado — folio ${c.folio}"
                )
                cargarPreview()
                cargarReparto(c.idCierre)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message, mostrarConfirmarCierre = false)
            }
        }
    }

    // ── Corte por mesero ────────────────────────────────────────────────────
    private fun cargarMeseros() {
        viewModelScope.launch {
            val lista = runCatching { repo.obtenerMeseros(soloActivos = false) }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(meseros = lista)
        }
    }

    fun seleccionarMesero(m: Mesero) {
        _uiState.value = _uiState.value.copy(meseroSel = m, corte = null)
        viewModelScope.launch {
            try {
                val c = turno.corteMesero(m.idMesero)
                _uiState.value = _uiState.value.copy(corte = c)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    // ── Propinas ────────────────────────────────────────────────────────────
    fun cargarReglas() {
        viewModelScope.launch {
            val r = runCatching { turno.reglasReparto() }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(reglas = r)
        }
    }

    fun guardarRegla(idPuesto: Int, porcentaje: Double) {
        viewModelScope.launch {
            try {
                turno.guardarRegla(idPuesto, porcentaje)
                cargarReglas()
                _uiState.value = _uiState.value.copy(exito = "Regla guardada")
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun setRepartoModo(modo: String) {
        _uiState.value = _uiState.value.copy(repartoModo = modo)
        _uiState.value.cierre?.let { cargarReparto(it.idCierre) }
    }

    fun cargarReparto(idCierre: Int) {
        viewModelScope.launch {
            try {
                val r = turno.repartoPreview(idCierre, _uiState.value.repartoModo)
                _uiState.value = _uiState.value.copy(reparto = r)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun aplicarReparto() {
        val idCierre = _uiState.value.cierre?.idCierre ?: _uiState.value.reparto?.idCierre ?: return
        viewModelScope.launch {
            try {
                val r = turno.aplicarReparto(idCierre, _uiState.value.repartoModo)
                _uiState.value = _uiState.value.copy(
                    exito = "Propinas repartidas ($${String.format(java.util.Locale.US, "%,.2f", r.totalPropina)})"
                )
                cargarReparto(idCierre)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    // ── Meseros (alta / edición / PIN) ──────────────────────────────────────
    fun setMostrarNuevoMesero(v: Boolean) {
        _uiState.value = _uiState.value.copy(mostrarNuevoMesero = v, meseroEnEdicion = null)
    }

    fun editarMesero(m: Mesero?) {
        _uiState.value = _uiState.value.copy(meseroEnEdicion = m, mostrarNuevoMesero = m != null)
    }

    fun guardarMesero(idMesero: Int?, nombre: String, codigo: String?, activo: Boolean) {
        viewModelScope.launch {
            try {
                turno.guardarMesero(idMesero, nombre, codigo, activo)
                _uiState.value = _uiState.value.copy(
                    mostrarNuevoMesero = false, meseroEnEdicion = null, exito = "Mesero guardado"
                )
                cargarMeseros()
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun limpiarMensajes() { _uiState.value = _uiState.value.copy(error = null, exito = null) }
}
