package com.example.mapicomandas.ui.screens.cobro

import androidx.compose.foundation.layout.*
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
    onCobrado: () -> Unit,
    viewModel: CobroViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(uiState.cobrado) {
        if (uiState.cobrado) onCobrado()
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.limpiarError()
        }
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

                // Pagos aplicados
                if (uiState.pagos.isNotEmpty()) {
                    uiState.pagos.forEach { pago ->
                        FilaPago(
                            pago = pago,
                            onQuitar = { viewModel.quitarPago(pago.idFormaPago) }
                        )
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Botones de forma de pago
                val importeRestante = maxOf(
                    0.0,
                    (uiState.comanda?.total ?: 0.0) + uiState.propinaIngresada - uiState.totalPagado
                )

                uiState.formasPago.forEach { forma ->
                    Button(
                        onClick = { viewModel.agregarPago(forma, importeRestante) },
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

                Spacer(Modifier.weight(1f))

                // Botón cobrar
                val puedeCobrar = uiState.totalPagado >= (uiState.comanda?.total ?: 0.0) + uiState.propinaIngresada
                Button(
                    onClick = { viewModel.cobrar() },
                    enabled = puedeCobrar && !uiState.cargando,
                    modifier = Modifier
                        .fillMaxWidth()
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
fun FilaPago(pago: PagoVenta, onQuitar: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(pago.nombreFormaPago, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$${String.format("%.2f", pago.importe)}", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
            IconButton(onClick = onQuitar, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(16.dp))
            }
        }
    }
}
