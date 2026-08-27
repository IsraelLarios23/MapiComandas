package com.example.mapicomandas.ui.screens.monederos

import androidx.compose.foundation.layout.*
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
import com.example.mapicomandas.data.api.ApiException
import com.example.mapicomandas.data.api.CatalogosService
import com.example.mapicomandas.data.api.dto.MonederoDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MonederosUiState(
    val codigo: String = "",
    val monedero: MonederoDto? = null,
    val noExiste: Boolean = false,      // el código consultado no está dado de alta
    val buscando: Boolean = false,
    val error: String? = null,
    val exito: String? = null
)

/** Monederos de lealtad: consulta de saldo, alta y recargas/consumos/ajustes. */
@HiltViewModel
class MonederosViewModel @Inject constructor(
    private val catalogos: CatalogosService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonederosUiState())
    val uiState: StateFlow<MonederosUiState> = _uiState

    fun setCodigo(v: String) { _uiState.value = _uiState.value.copy(codigo = v, noExiste = false) }

    fun consultar() {
        val codigo = _uiState.value.codigo.trim()
        if (codigo.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(buscando = true, monedero = null, noExiste = false)
            try {
                val m = catalogos.consultarMonedero(codigo)
                _uiState.value = _uiState.value.copy(monedero = m, buscando = false)
            } catch (e: ApiException.Negocio) {
                // 400 = ese código no existe → ofrecer alta
                _uiState.value = _uiState.value.copy(buscando = false, noExiste = true)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(buscando = false, error = e.message)
            }
        }
    }

    fun alta(saldoInicial: Double) {
        val codigo = _uiState.value.codigo.trim()
        viewModelScope.launch {
            try {
                val m = catalogos.altaMonedero(codigo, saldoInicial)
                _uiState.value = _uiState.value.copy(
                    monedero = m, noExiste = false, exito = "Monedero ${m.codigo} creado"
                )
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    /** [tipo] "Recarga" | "Consumo" | "Ajuste". */
    fun movimiento(tipo: String, monto: Double, referencia: String) {
        val codigo = _uiState.value.monedero?.codigo ?: return
        viewModelScope.launch {
            try {
                val r = catalogos.movimientoMonedero(codigo, tipo, monto, referencia)
                _uiState.value = _uiState.value.copy(
                    monedero = _uiState.value.monedero?.copy(saldo = r.saldoNuevo),
                    exito = "$tipo aplicada — saldo: $${String.format(java.util.Locale.US, "%,.2f", r.saldoNuevo)}"
                )
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun limpiarMensajes() { _uiState.value = _uiState.value.copy(error = null, exito = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonederosScreen(
    onVolver: () -> Unit,
    onIrHome: () -> Unit = {},
    viewModel: MonederosViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

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
                title = { Text("Monederos", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.Default.ArrowBack, "Volver") } },
                actions = { IconButton(onClick = onIrHome) { Icon(Icons.Default.Home, "Inicio") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF6A1B9A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = uiState.codigo, onValueChange = viewModel::setCodigo,
                    label = { Text("Código / tarjeta") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.CardMembership, null) },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { viewModel.consultar() }, enabled = !uiState.buscando) {
                    if (uiState.buscando) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White)
                    else Text("Consultar")
                }
            }

            if (uiState.noExiste) {
                var saldoIni by remember { mutableStateOf("0") }
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ese código no existe. ¿Darlo de alta?", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = saldoIni, onValueChange = { saldoIni = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("Saldo inicial") }, prefix = { Text("$") },
                                singleLine = true, modifier = Modifier.weight(1f)
                            )
                            Button(onClick = { viewModel.alta(saldoIni.toDoubleOrNull() ?: 0.0) }) {
                                Text("Crear")
                            }
                        }
                    }
                }
            }

            uiState.monedero?.let { m ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7F6))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(m.codigo, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        m.cliente?.takeIf { it.isNotBlank() }?.let {
                            Text(it, fontSize = 13.sp, color = Color.Gray)
                        }
                        Text(
                            "$${String.format(java.util.Locale.US, "%,.2f", m.saldo)}",
                            fontWeight = FontWeight.Bold, fontSize = 28.sp,
                            color = if (m.saldo >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                        if (!m.activo) Text("INACTIVO", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                    }
                }

                var monto by remember(m.idMonedero) { mutableStateOf("") }
                var referencia by remember(m.idMonedero) { mutableStateOf("") }
                OutlinedTextField(
                    value = monto, onValueChange = { monto = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Monto") }, prefix = { Text("$") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = referencia, onValueChange = { referencia = it },
                    label = { Text("Referencia (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { monto.toDoubleOrNull()?.takeIf { it > 0 }?.let { viewModel.movimiento("Recarga", it, referencia) } },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Recarga") }
                    Button(
                        onClick = { monto.toDoubleOrNull()?.takeIf { it > 0 }?.let { viewModel.movimiento("Consumo", it, referencia) } },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    ) { Icon(Icons.Default.Remove, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Consumo") }
                    OutlinedButton(
                        onClick = { monto.toDoubleOrNull()?.let { viewModel.movimiento("Ajuste", it, referencia) } },
                        modifier = Modifier.weight(1f)
                    ) { Text("Ajuste") }
                }
                Text("Recarga abona · Consumo descuenta (valida saldo) · Ajuste fija diferencia.",
                    fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}
