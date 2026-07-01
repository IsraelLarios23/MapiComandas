package com.example.mapicomandas.ui.screens.caja

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CajaScreen(
    onVolver: () -> Unit,
    onIrHome: () -> Unit = {},
    viewModel: CajaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Caja — Caja ${viewModel.session.idCaja}", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.Default.ArrowBack, "Volver") }
                },
                actions = {
                    IconButton(onClick = onIrHome) { Icon(Icons.Default.Home, "Inicio") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF37474F),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (uiState.cargando) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Estado de la caja
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (viewModel.session.cajaHabilitada)
                            Color(0xFF1B5E20) else Color(0xFFB71C1C)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                if (viewModel.session.cajaHabilitada) "Caja Abierta" else "Caja Cerrada",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                "Tienda ${viewModel.session.idTienda} / Caja ${viewModel.session.idCaja}",
                                color = Color.White.copy(0.8f),
                                fontSize = 12.sp
                            )
                        }
                        if (!viewModel.session.cajaHabilitada) {
                            Button(
                                onClick = { viewModel.habilitarCaja() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                            ) {
                                Icon(Icons.Default.LockOpen, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Habilitar")
                            }
                        }
                    }
                }
            }

            // Resumen
            uiState.resumen?.let { r ->
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Resumen del Turno", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            FilaResumen("Ventas", r.totalVentas, Color(0xFF4CAF50))
                            FilaResumen("Efectivo", r.totalEfectivo)
                            FilaResumen("Otros medios", r.totalOtros)
                            FilaResumen("Ingresos", r.totalIngresos, Color(0xFF2196F3))
                            FilaResumen("Retiros", r.totalRetiros, Color(0xFFF44336))
                            Divider(modifier = Modifier.padding(vertical = 6.dp))
                            FilaResumen("SALDO FINAL", r.saldoFinal, Color.Black, FontWeight.Bold, 16)
                            Text(
                                "${r.numTransacciones} transacciones",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // Acciones
            item {
                Text("Acciones", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BotonCaja(
                        texto = "Ingreso",
                        icono = Icons.Default.AddCircle,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.abrirMovimiento("I") }
                    )
                    BotonCaja(
                        texto = "Retiro",
                        icono = Icons.Default.RemoveCircle,
                        color = Color(0xFFF44336),
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.abrirMovimiento("R") }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BotonCaja(
                        texto = "Corte X",
                        icono = Icons.Default.Print,
                        color = Color(0xFF2196F3),
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.realizarCorteX() }
                    )
                    BotonCaja(
                        texto = "Corte Z",
                        icono = Icons.Default.Close,
                        color = Color(0xFF607D8B),
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.setMostrarCorteZ(true) }
                    )
                }
            }
        }
    }

    // Diálogo movimiento de caja
    if (uiState.mostrarMovimiento) {
        DialogoMovimientoCaja(
            tipoInicial = uiState.tipoMovimientoInicial,
            onConfirmar = { tipo, concepto, importe ->
                viewModel.registrarMovimiento(tipo, concepto, importe)
            },
            onDismiss = { viewModel.setMostrarMovimiento(false) }
        )
    }

    // Confirmación corte Z
    if (uiState.mostrarCorteZ) {
        AlertDialog(
            onDismissRequest = { viewModel.setMostrarCorteZ(false) },
            title = { Text("Corte Z") },
            text = { Text("¿Realizar corte Z? Esta acción cierra el turno de caja y no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.realizarCorteZ() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                ) { Text("Realizar Corte Z") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setMostrarCorteZ(false) }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun FilaResumen(
    label: String, valor: Double,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight = FontWeight.Normal,
    fontSize: Int = 14
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = fontSize.sp, fontWeight = fontWeight, color = color)
        Text("$${String.format("%.2f", valor)}", fontSize = fontSize.sp, fontWeight = fontWeight, color = color)
    }
}

@Composable
fun BotonCaja(
    texto: String,
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Icon(icono, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(texto)
    }
}

@Composable
fun DialogoMovimientoCaja(
    tipoInicial: String = "I",
    onConfirmar: (String, String, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var tipo by remember { mutableStateOf(tipoInicial) }
    var concepto by remember { mutableStateOf("") }
    var importe by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Movimiento de Caja") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tipo == "I", onClick = { tipo = "I" }, label = { Text("Ingreso") })
                    FilterChip(selected = tipo == "R", onClick = { tipo = "R" }, label = { Text("Retiro") })
                }
                OutlinedTextField(
                    value = concepto,
                    onValueChange = { concepto = it },
                    label = { Text("Concepto") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = importe,
                    onValueChange = { importe = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Importe") },
                    modifier = Modifier.fillMaxWidth(),
                    prefix = { Text("$") }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirmar(tipo, concepto, importe.toDoubleOrNull() ?: 0.0)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
