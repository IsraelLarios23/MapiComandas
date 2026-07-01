package com.example.mapicomandas.ui.screens.cobro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.mapicomandas.data.model.FormaPago
import com.example.mapicomandas.data.model.PagoVenta
import com.example.mapicomandas.data.model.StatusLinea

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CobroScreen(
    onVolver: () -> Unit,
    onCobrado: (Int?) -> Unit,
    viewModel: CobroViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(uiState.finalizado) {
        if (uiState.finalizado) onCobrado(uiState.nuevaComandaFastFood)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.limpiarError()
        }
    }
    LaunchedEffect(uiState.mensajeImpresion) {
        uiState.mensajeImpresion?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.limpiarMensajeImpresion()
        }
    }
    LaunchedEffect(uiState.mensajeNetPay) {
        if (!uiState.procesandoNetPay) uiState.mensajeNetPay?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.limpiarMensajeNetPay()
        }
    }

    // Diálogo de espera de la terminal NetPay (cancelable)
    if (uiState.procesandoNetPay) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Terminal NetPay") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(uiState.mensajeNetPay ?: "Procesando…")
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.cancelarNetPay() }) { Text("Cancelar") }
            }
        )
    }

    // Panel de venta cobrada: vista previa del ticket + Finalizar
    if (uiState.cobrado) {
        TicketFinalizarDialog(
            ticketLineas = uiState.ticketLineas,
            imprimiendo = uiState.imprimiendo,
            onFinalizarImprimir = { viewModel.finalizar(imprimir = true) },
            onFinalizarSinImprimir = { viewModel.finalizar(imprimir = false) }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Cobro — ${uiState.comanda?.folio ?: ""}",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2E7D32),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
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

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Panel izquierdo: resumen de la comanda
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .padding(12.dp)
            ) {
                Text("Resumen", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(
                        uiState.lineas.filter { it.status != StatusLinea.CANCELADO }
                    ) { linea ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${linea.cantidad.toInt()} × ${linea.nombreArticulo}",
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp
                            )
                            Text(
                                "$${String.format("%.2f", linea.total)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Propina
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Propina sugerida:", fontSize = 13.sp)
                    Text(
                        "$${String.format("%.2f", uiState.propinaSugerida)}",
                        fontSize = 13.sp,
                        color = Color(0xFF4CAF50)
                    )
                }
                var propinaInput by remember {
                    mutableStateOf(String.format("%.2f", uiState.propinaIngresada))
                }
                // Sugerencias 15/18/20% sobre el subtotal (total sin propina)
                val baseProp = uiState.comanda?.total ?: 0.0
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    listOf(0.0, 0.10, 0.15, 0.18, 0.20).forEach { pct ->
                        val monto = baseProp * pct
                        val etiqueta = if (pct == 0.0) "Sin" else "${(pct * 100).toInt()}%"
                        val sel = kotlin.math.abs(uiState.propinaIngresada - monto) < 0.01
                        FilterChip(
                            selected = sel,
                            onClick = { viewModel.setPropina(monto); propinaInput = String.format("%.2f", monto) },
                            label = { Text(etiqueta, fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = propinaInput,
                    onValueChange = {
                        propinaInput = it
                        it.toDoubleOrNull()?.let { p -> viewModel.setPropina(p) }
                    },
                    label = { Text("Propina") },
                    modifier = Modifier.fillMaxWidth(),
                    prefix = { Text("$") }
                )

                Spacer(Modifier.height(8.dp))

                // Totales
                uiState.comanda?.let { c ->
                    FilaTotal("Subtotal", c.subtotal)
                    if (c.descuento > 0) FilaTotal("Descuento", -c.descuento, Color(0xFF4CAF50))
                    FilaTotal("IVA", c.iva)
                    Divider()
                    val totalConPropina = c.total + uiState.propinaIngresada
                    FilaTotal("TOTAL", totalConPropina, fontWeight = FontWeight.Bold, fontSize = 18)
                    Spacer(Modifier.height(8.dp))
                    FilaTotal("Pagado", uiState.totalPagado, color = Color(0xFF2196F3))
                    if (uiState.cambio > 0) {
                        FilaTotal("Cambio", uiState.cambio, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(12.dp))
                    Divider()
                    // ── División de cuenta ──────────────────────────────────
                    Text("Dividir cuenta", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp))
                    val dividir = uiState.partesDivision > 1
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = !dividir,
                            onClick = { viewModel.setModoDivision(com.example.mapicomandas.ui.screens.cobro.ModoDivision.NINGUNO) },
                            label = { Text("No dividir") }
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = dividir,
                            onClick = { viewModel.setModoDivision(com.example.mapicomandas.ui.screens.cobro.ModoDivision.PARTES_IGUALES) },
                            label = { Text("Partes iguales") }
                        )
                    }
                    if (dividir) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            IconButton(onClick = { viewModel.setPartesDivision(uiState.partesDivision - 1) }) {
                                Icon(Icons.Default.Remove, "Menos partes")
                            }
                            Text("${uiState.partesDivision} partes", fontWeight = FontWeight.Bold)
                            IconButton(onClick = { viewModel.setPartesDivision(uiState.partesDivision + 1) }) {
                                Icon(Icons.Default.Add, "Más partes")
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "$${String.format(java.util.Locale.US, "%,.2f", viewModel.montoPorParte())} c/u",
                                color = Color(0xFF1A237E), fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "Cobra cada parte con una forma de pago; el monto sugerido será una parte.",
                            fontSize = 11.sp, color = Color.Gray
                        )
                    }
                }
            }

            Divider(modifier = Modifier.fillMaxHeight().width(1.dp))

            // Panel derecho: formas de pago
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .padding(12.dp)
            ) {
                Text("Formas de Pago", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))

                var pagoEnEdicion by remember { mutableStateOf<PagoVenta?>(null) }
                var formaSeleccionada by remember { mutableStateOf<FormaPago?>(null) }

                val totalAPagar = (uiState.comanda?.total ?: 0.0) + uiState.propinaIngresada
                val importeRestante = maxOf(0.0, totalAPagar - uiState.totalPagado)
                val montoCompleto = importeRestante <= 0.0

                // Contenido scrolleable: pagos aplicados + botones de forma de pago
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (uiState.pagos.isNotEmpty()) {
                        uiState.pagos.forEach { pago ->
                            FilaPago(
                                pago = pago,
                                onEditar = { pagoEnEdicion = pago },
                                onQuitar = { viewModel.quitarPago(pago.idFormaPago) }
                            )
                        }
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                    if (montoCompleto) {
                        Text(
                            "Monto completo ✓",
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    uiState.formasPago.forEach { forma ->
                        Button(
                            onClick = { formaSeleccionada = forma },
                            enabled = !montoCompleto,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (forma.esEfectivo) Color(0xFF4CAF50) else Color(0xFF2196F3)
                            )
                        ) {
                            Icon(
                                if (forma.esEfectivo) Icons.Default.AttachMoney else Icons.Default.CreditCard,
                                null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(forma.nombre)
                        }
                    }
                }

                // Diálogo para editar el monto de un pago ya aplicado
                pagoEnEdicion?.let { pago ->
                    val otrosPagos = uiState.totalPagado - pago.importe
                    DialogoMontoPago(
                        forma = FormaPago(pago.idFormaPago, pago.nombreFormaPago, true, false),
                        montoSugerido = pago.importe,
                        montoMaximo = maxOf(0.0, totalAPagar - otrosPagos),
                        titulo = "Editar ${pago.nombreFormaPago}",
                        onConfirmar = { monto ->
                            viewModel.editarPago(pago.idFormaPago, monto)
                            pagoEnEdicion = null
                        },
                        onDismiss = { pagoEnEdicion = null }
                    )
                }

                // Diálogo para capturar el monto de la forma de pago seleccionada
                formaSeleccionada?.let { forma ->
                    // Con división activa, sugiere una parte (sin exceder lo que resta).
                    val sugerido = if (uiState.partesDivision > 1)
                        minOf(viewModel.montoPorParte(), importeRestante) else importeRestante
                    DialogoMontoPago(
                        forma = forma,
                        montoSugerido = sugerido,
                        montoMaximo = importeRestante,
                        onConfirmar = { monto ->
                            if (forma.usaTerminal) {
                                viewModel.cobrarConNetPay(forma, monto)   // terminal NetPay
                            } else {
                                viewModel.agregarPago(forma, monto)
                            }
                            formaSeleccionada = null
                        },
                        onDismiss = { formaSeleccionada = null }
                    )
                }

                // Botón COBRAR fijo, siempre visible al fondo
                val puedeCobrar = uiState.totalPagado >= totalAPagar
                Button(
                    onClick = { viewModel.cobrar() },
                    enabled = puedeCobrar && !uiState.cargando,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(top = 6.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    if (uiState.cargando) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.CheckCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text("COBRAR", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun FilaTotal(
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
fun FilaPago(pago: PagoVenta, onEditar: () -> Unit, onQuitar: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditar)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(pago.nombreFormaPago, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$${String.format("%.2f", pago.importe)}", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
            IconButton(onClick = onEditar, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, null, tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onQuitar, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoMontoPago(
    forma: FormaPago,
    montoSugerido: Double,
    montoMaximo: Double,
    titulo: String? = null,
    onConfirmar: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var monto by remember { mutableStateOf(String.format("%.2f", montoSugerido)) }
    val montoValido = monto.toDoubleOrNull()?.let { it > 0.0 && it <= montoMaximo + 0.001 } ?: false

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo ?: forma.nombre) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Pendiente: $${String.format("%.2f", montoMaximo)}",
                    color = Color.Gray, fontSize = 13.sp
                )
                OutlinedTextField(
                    value = monto,
                    onValueChange = { v -> monto = v.filter { it.isDigit() || it == '.' } },
                    label = { Text("Monto a pagar") },
                    prefix = { Text("$") },
                    singleLine = true,
                    isError = monto.isNotBlank() && !montoValido,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                // Atajos rápidos
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { monto = String.format("%.2f", montoMaximo) },
                        label = { Text("Total pendiente") }
                    )
                    AssistChip(
                        onClick = { monto = String.format("%.2f", montoMaximo / 2) },
                        label = { Text("Mitad") }
                    )
                }
                if (monto.isNotBlank() && !montoValido) {
                    Text(
                        "El monto debe ser mayor a 0 y no exceder lo pendiente.",
                        color = MaterialTheme.colorScheme.error, fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { monto.toDoubleOrNull()?.let { onConfirmar(it) } },
                enabled = montoValido
            ) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun TicketFinalizarDialog(
    ticketLineas: List<String>,
    imprimiendo: Boolean,
    onFinalizarImprimir: () -> Unit,
    onFinalizarSinImprimir: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* obligar a finalizar */ },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                Spacer(Modifier.width(8.dp))
                Text("Venta cobrada")
            }
        },
        text = {
            Column {
                Text("Vista previa del ticket:", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = Color(0xFFF5F5F5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        items(ticketLineas) { linea ->
                            Text(
                                text = linea,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color.Black,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onFinalizarImprimir,
                enabled = !imprimiendo,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                if (imprimiendo) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Default.Print, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Finalizar e imprimir")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onFinalizarSinImprimir, enabled = !imprimiendo) {
                Text("Finalizar sin imprimir")
            }
        }
    )
}
