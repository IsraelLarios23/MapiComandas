package com.example.mapicomandas.ui.screens.mermas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.mapicomandas.data.api.CatalogosService
import com.example.mapicomandas.data.api.dto.CausaMermaDto
import com.example.mapicomandas.data.api.dto.MermaReporteDto
import com.example.mapicomandas.data.model.Articulo
import com.example.mapicomandas.data.repository.RestauranteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MermasUiState(
    val causas: List<CausaMermaDto> = emptyList(),
    val busqueda: String = "",
    val resultados: List<Articulo> = emptyList(),
    val articuloSel: Articulo? = null,
    val reporte: List<MermaReporteDto> = emptyList(),
    val cargandoReporte: Boolean = false,
    val error: String? = null,
    val exito: String? = null
)

/** Registro de mermas (movimiento tipo M + upsert de existencias) y su reporte. */
@HiltViewModel
class MermasViewModel @Inject constructor(
    private val catalogos: CatalogosService,
    private val repo: RestauranteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MermasUiState())
    val uiState: StateFlow<MermasUiState> = _uiState

    init {
        viewModelScope.launch {
            val causas = runCatching { catalogos.causasMerma() }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(causas = causas)
        }
        cargarReporte()
    }

    fun buscarArticulo(q: String) {
        _uiState.value = _uiState.value.copy(busqueda = q)
        if (q.length < 2) {
            _uiState.value = _uiState.value.copy(resultados = emptyList())
            return
        }
        viewModelScope.launch {
            val arts = runCatching { repo.obtenerArticulos(nombre = q) }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(resultados = arts.take(15))
        }
    }

    fun seleccionarArticulo(a: Articulo?) {
        _uiState.value = _uiState.value.copy(articuloSel = a, resultados = emptyList(),
            busqueda = a?.nombre ?: "")
    }

    fun registrar(cantidad: Double, idCausa: Int?, observaciones: String) {
        val art = _uiState.value.articuloSel ?: run {
            _uiState.value = _uiState.value.copy(error = "Elige el artículo de la merma")
            return
        }
        viewModelScope.launch {
            try {
                val r = catalogos.registrarMerma(art.idArticulo, cantidad, idCausa, observaciones)
                _uiState.value = _uiState.value.copy(
                    exito = "Merma ${r.folio} — existencia nueva: ${r.existenciaNueva}",
                    articuloSel = null, busqueda = ""
                )
                cargarReporte()
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun cargarReporte() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargandoReporte = true)
            val lista = runCatching { catalogos.reporteMermas() }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(reporte = lista, cargandoReporte = false)
        }
    }

    fun limpiarMensajes() { _uiState.value = _uiState.value.copy(error = null, exito = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MermasScreen(
    onVolver: () -> Unit,
    onIrHome: () -> Unit = {},
    viewModel: MermasViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.limpiarMensajes()
        }
    }
    LaunchedEffect(uiState.exito) {
        uiState.exito?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.limpiarMensajes()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mermas", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.Default.ArrowBack, "Volver") } },
                actions = {
                    IconButton(onClick = { viewModel.cargarReporte() }) { Icon(Icons.Default.Refresh, "Actualizar") }
                    IconButton(onClick = onIrHome) { Icon(Icons.Default.Home, "Inicio") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFB71C1C),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Registrar") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Reporte de hoy") })
            }
            if (tab == 0) TabRegistrar(uiState, viewModel) else TabReporte(uiState)
        }
    }
}

@Composable
private fun TabRegistrar(uiState: MermasUiState, viewModel: MermasViewModel) {
    var cantidad by remember { mutableStateOf("1") }
    var idCausa by remember(uiState.causas) { mutableStateOf(uiState.causas.firstOrNull()?.idCausa) }
    var obs by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = uiState.busqueda, onValueChange = viewModel::buscarArticulo,
            label = { Text("Artículo / insumo") }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (uiState.articuloSel != null)
                    IconButton(onClick = { viewModel.seleccionarArticulo(null) }) {
                        Icon(Icons.Default.Close, "Quitar")
                    }
            },
            modifier = Modifier.fillMaxWidth()
        )
        uiState.resultados.forEach { a ->
            Text(
                a.nombre, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().clickable { viewModel.seleccionarArticulo(a) }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            )
        }
        uiState.articuloSel?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
                Text("Merma de: ${it.nombre}", modifier = Modifier.padding(10.dp),
                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        OutlinedTextField(
            value = cantidad, onValueChange = { cantidad = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Cantidad") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        if (uiState.causas.isNotEmpty()) {
            Text("Causa:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            uiState.causas.forEach { c ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { idCausa = c.idCausa }
                ) {
                    RadioButton(selected = idCausa == c.idCausa, onClick = { idCausa = c.idCausa })
                    Text(c.nombre, fontSize = 13.sp)
                }
            }
        }
        OutlinedTextField(
            value = obs, onValueChange = { obs = it },
            label = { Text("Observaciones") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { cantidad.toDoubleOrNull()?.takeIf { it > 0 }?.let { viewModel.registrar(it, idCausa, obs) } },
            enabled = uiState.articuloSel != null,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
        ) {
            Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(8.dp))
            Text("REGISTRAR MERMA", fontWeight = FontWeight.Bold)
        }
        Text("Descuenta existencias y queda en el kárdex como movimiento de merma.",
            fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
private fun TabReporte(uiState: MermasUiState) {
    if (uiState.cargandoReporte) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (uiState.reporte.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Sin mermas registradas hoy", color = Color.Gray)
        }
        return
    }
    val totalCosto = uiState.reporte.sumOf { it.costoEstimado }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            "Costo estimado del día: $${String.format(java.util.Locale.US, "%,.2f", totalCosto)}",
            fontWeight = FontWeight.Bold, color = Color(0xFFB71C1C)
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(uiState.reporte) { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("${m.cantidad} × ${m.articulo}", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text(
                            listOfNotNull(
                                m.causa.takeIf { it.isNotBlank() },
                                m.usuario.takeIf { it.isNotBlank() },
                                m.fecha.take(16).replace('T', ' ')
                            ).joinToString("  ·  "),
                            fontSize = 11.sp, color = Color.Gray
                        )
                    }
                    if (m.costoEstimado > 0)
                        Text("$${String.format(java.util.Locale.US, "%,.2f", m.costoEstimado)}",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Divider()
            }
        }
    }
}
