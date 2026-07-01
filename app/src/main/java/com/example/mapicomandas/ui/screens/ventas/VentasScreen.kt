package com.example.mapicomandas.ui.screens.ventas

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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mapicomandas.data.model.VentaDia
import com.example.mapicomandas.ui.components.DialogoSupervisor
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VentasScreen(
    onVolver: () -> Unit,
    onIrHome: () -> Unit = {},
    viewModel: VentasViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(uiState.error) {
        uiState.error?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show(); viewModel.limpiarMensajes() }
    }
    LaunchedEffect(uiState.exito) {
        uiState.exito?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show(); viewModel.limpiarMensajes() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ventas del día", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.Default.ArrowBack, "Volver") } },
                actions = {
                    IconButton(onClick = onIrHome) { Icon(Icons.Default.Home, "Inicio") }
                    IconButton(onClick = { viewModel.cargar() }) { Icon(Icons.Default.Refresh, "Refrescar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF455A64),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (uiState.cargando) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.ventas.isEmpty()) {
                item { Text("No hay ventas hoy.", color = Color.Gray, modifier = Modifier.padding(16.dp)) }
            }
            items(uiState.ventas, key = { it.idVenta }) { venta ->
                TarjetaVenta(
                    venta = venta,
                    facturacionActiva = uiState.facturacionActiva,
                    onReimprimir = { viewModel.reimprimir(venta) },
                    onCancelar = { viewModel.pedirCancelar(venta) },
                    onFacturar = { viewModel.pedirFacturar(venta) }
                )
            }
        }
    }

    uiState.ventaParaCancelar?.let { venta ->
        DialogoSupervisor(
            titulo = "Cancelar venta ${venta.folio}",
            mensaje = "Ingresa credenciales de supervisor para cancelar esta venta ($${money(venta.total)}).",
            onConfirmar = { u, p -> viewModel.confirmarCancelar(venta, u, p) },
            onCancelar = { viewModel.cerrarCancelar() }
        )
    }

    uiState.ventaParaFacturar?.let { venta ->
        DialogoFacturar(
            venta = venta,
            facturando = uiState.facturando,
            onFacturar = { datos -> viewModel.facturar(venta, datos) },
            onDismiss = { viewModel.cerrarFacturar() }
        )
    }
}

@Composable
fun TarjetaVenta(
    venta: VentaDia,
    facturacionActiva: Boolean,
    onReimprimir: () -> Unit,
    onCancelar: () -> Unit,
    onFacturar: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "T-${venta.folio}",
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (venta.cancelada) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (venta.cancelada) Color.Gray else Color.Unspecified
                )
                Text(venta.hora + (if (venta.cancelada) " · CANCELADA" else ""), fontSize = 12.sp,
                    color = if (venta.cancelada) Color.Red else Color.Gray)
            }
            Text("$${money(venta.total)}", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.padding(end = 8.dp))
            IconButton(onClick = onReimprimir) { Icon(Icons.Default.Print, "Reimprimir", tint = Color(0xFF2196F3)) }
            if (facturacionActiva && !venta.cancelada) {
                IconButton(onClick = onFacturar) { Icon(Icons.Default.ReceiptLong, "Facturar", tint = Color(0xFF6A1B9A)) }
            }
            if (!venta.cancelada) {
                IconButton(onClick = onCancelar) { Icon(Icons.Default.Cancel, "Cancelar", tint = Color.Red) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoFacturar(
    venta: VentaDia,
    facturando: Boolean,
    onFacturar: (com.example.mapicomandas.data.facturacion.DatosFactura) -> Unit,
    onDismiss: () -> Unit
) {
    var rfc by remember { mutableStateOf("") }
    var razon by remember { mutableStateOf("") }
    var cp by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var uso by remember { mutableStateOf("G03") }
    var regimen by remember { mutableStateOf("616") }
    var expUso by remember { mutableStateOf(false) }
    var expReg by remember { mutableStateOf(false) }
    val cat = com.example.mapicomandas.data.facturacion.CatalogosCfdi

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Facturar T-${venta.folio}") },
        text = {
            androidx.compose.foundation.layout.Column(
                Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                OutlinedTextField(rfc, { rfc = it.uppercase() }, label = { Text("RFC receptor") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(razon, { razon = it }, label = { Text("Razón social") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(cp, { cp = it.filter { c -> c.isDigit() } }, label = { Text("C.P.") },
                        singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(email, { email = it }, label = { Text("Correo") },
                        singleLine = true, modifier = Modifier.weight(2f))
                }
                Spacer(Modifier.height(8.dp))
                // Uso CFDI
                ExposedDropdownMenuBox(expanded = expUso, onExpandedChange = { expUso = it }) {
                    OutlinedTextField(
                        value = cat.usosCfdi.firstOrNull { it.first == uso }?.let { "${it.first} · ${it.second}" } ?: uso,
                        onValueChange = {}, readOnly = true, label = { Text("Uso CFDI") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expUso) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expUso, onDismissRequest = { expUso = false }) {
                        cat.usosCfdi.forEach { (c, d) ->
                            DropdownMenuItem(text = { Text("$c · $d") }, onClick = { uso = c; expUso = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Régimen fiscal
                ExposedDropdownMenuBox(expanded = expReg, onExpandedChange = { expReg = it }) {
                    OutlinedTextField(
                        value = cat.regimenes.firstOrNull { it.first == regimen }?.let { "${it.first} · ${it.second}" } ?: regimen,
                        onValueChange = {}, readOnly = true, label = { Text("Régimen fiscal") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expReg) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expReg, onDismissRequest = { expReg = false }) {
                        cat.regimenes.forEach { (c, d) ->
                            DropdownMenuItem(text = { Text("$c · $d") }, onClick = { regimen = c; expReg = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !facturando && rfc.length in 12..13 && razon.isNotBlank(),
                onClick = {
                    onFacturar(
                        com.example.mapicomandas.data.facturacion.DatosFactura(
                            rfc = rfc.trim(), razonSocial = razon.trim(), usoCfdi = uso,
                            regimenFiscal = regimen, codigoPostal = cp.trim(), email = email.trim()
                        )
                    )
                }
            ) { Text(if (facturando) "Timbrando…" else "Facturar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun money(v: Double) = String.format(Locale.US, "%,.2f", v)
