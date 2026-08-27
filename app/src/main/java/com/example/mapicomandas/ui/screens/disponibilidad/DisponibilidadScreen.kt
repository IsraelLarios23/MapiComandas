package com.example.mapicomandas.ui.screens.disponibilidad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapicomandas.data.api.OperacionService
import com.example.mapicomandas.data.api.dto.DisponibilidadDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DisponibilidadUiState(
    val renglones: List<DisponibilidadDto> = emptyList(),
    val filtro: String = "",
    val soloProblemas: Boolean = false,
    val cargando: Boolean = false,
    val error: String? = null
)

/** Tablero "¿cuántas porciones alcanzan?" por receta (insumo limitante incluido). */
@HiltViewModel
class DisponibilidadViewModel @Inject constructor(
    private val operacion: OperacionService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DisponibilidadUiState())
    val uiState: StateFlow<DisponibilidadUiState> = _uiState

    init { cargar() }

    fun cargar() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true)
            try {
                val lista = operacion.disponibilidad()
                _uiState.value = _uiState.value.copy(renglones = lista, cargando = false, error = null)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(cargando = false, error = e.message)
            }
        }
    }

    fun setFiltro(v: String) { _uiState.value = _uiState.value.copy(filtro = v) }
    fun setSoloProblemas(v: Boolean) { _uiState.value = _uiState.value.copy(soloProblemas = v) }
    fun limpiarError() { _uiState.value = _uiState.value.copy(error = null) }
}

private fun colorEstado(estado: String): Color = when (estado.lowercase()) {
    "disponible" -> Color(0xFF2E7D32)
    "por agotarse" -> Color(0xFFF9A825)
    "agotado" -> Color(0xFFC62828)
    else -> Color(0xFF9E9E9E)   // Sin receta
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisponibilidadScreen(
    onVolver: () -> Unit,
    onIrHome: () -> Unit = {},
    viewModel: DisponibilidadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.limpiarError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Disponibilidad", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.Default.ArrowBack, "Volver") } },
                actions = {
                    IconButton(onClick = { viewModel.cargar() }) { Icon(Icons.Default.Refresh, "Actualizar") }
                    IconButton(onClick = onIrHome) { Icon(Icons.Default.Home, "Inicio") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.filtro, onValueChange = viewModel::setFiltro,
                    label = { Text("Buscar platillo") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = uiState.soloProblemas,
                    onClick = { viewModel.setSoloProblemas(!uiState.soloProblemas) },
                    label = { Text("Solo agotados") }
                )
            }
            Spacer(Modifier.height(8.dp))
            if (uiState.cargando) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            val visibles = uiState.renglones.filter { r ->
                (uiState.filtro.isBlank() || r.nombre.contains(uiState.filtro, true) ||
                    r.categoria.contains(uiState.filtro, true)) &&
                (!uiState.soloProblemas || r.estado.lowercase() != "disponible")
            }
            if (visibles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Sin platillos que mostrar", color = Color.Gray)
                }
                return@Column
            }
            LazyColumn {
                items(visibles, key = { it.idArticulo }) { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(10.dp).background(colorEstado(r.estado), RoundedCornerShape(5.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.nombre, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(
                                listOfNotNull(
                                    r.categoria.takeIf { it.isNotBlank() },
                                    r.limitante.takeIf { it.isNotBlank() }?.let { "limita: $it" }
                                ).joinToString("  ·  "),
                                fontSize = 11.sp, color = Color.Gray
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                if (r.estado.lowercase() == "sin receta") "—" else "${r.porciones}",
                                fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colorEstado(r.estado)
                            )
                            Text(r.estado, fontSize = 10.sp, color = colorEstado(r.estado))
                        }
                    }
                    Divider()
                }
            }
        }
    }
}
