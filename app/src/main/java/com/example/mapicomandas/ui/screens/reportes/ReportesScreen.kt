package com.example.mapicomandas.ui.screens.reportes

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
import com.example.mapicomandas.data.model.ReporteFila
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportesScreen(
    onVolver: () -> Unit,
    onIrHome: () -> Unit = {},
    viewModel: ReportesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(uiState.error) {
        uiState.error?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show(); viewModel.limpiarError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reportes del día", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.Default.ArrowBack, "Volver") } },
                actions = {
                    IconButton(onClick = onIrHome) { Icon(Icons.Default.Home, "Inicio") }
                    IconButton(onClick = { viewModel.cargar(uiState.fecha) }) { Icon(Icons.Default.Refresh, "Refrescar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF00695C),
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
        val r = uiState.reportes
        if (r == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Sin datos") }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Resumen del día
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF00695C))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Resumen · ${r.resumen.fecha}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("$${money(r.resumen.totalVentas)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                        Text("${r.resumen.numTickets} tickets · promedio $${money(r.resumen.ticketPromedio)}",
                            color = Color.White.copy(0.85f), fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            ChipMonto("Efectivo", r.resumen.totalEfectivo)
                            ChipMonto("Tarjeta", r.resumen.totalTarjeta)
                            ChipMonto("Otros", r.resumen.totalOtros)
                        }
                        if (r.resumen.totalDescuentos > 0)
                            Text("Descuentos: $${money(r.resumen.totalDescuentos)}", color = Color(0xFFFFCDD2), fontSize = 12.sp)
                    }
                }
            }

            seccion("Por forma de pago", r.porFormaPago, "%.0f", this)
            seccion("Por mesero", r.porMesero, "%.0f", this)
            seccion("Por categoría", r.porCategoria, "%.2f", this)
            seccion("Productos más vendidos", r.productosTop, "%.2f", this)
        }
    }
}

private fun seccion(
    titulo: String,
    filas: List<ReporteFila>,
    fmtCant: String,
    scope: androidx.compose.foundation.lazy.LazyListScope
) {
    scope.item {
        Text(titulo, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
    }
    if (filas.isEmpty()) {
        scope.item { Text("(sin datos)", color = Color.Gray, fontSize = 13.sp) }
    } else {
        scope.items(filas) { f ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(f.etiqueta, modifier = Modifier.weight(1f), fontSize = 14.sp)
                Text(String.format(Locale.US, "$fmtCant", f.cantidad), fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(end = 12.dp))
                Text("$${money(f.importe)}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Divider()
        }
    }
}

@Composable
private fun ChipMonto(label: String, monto: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(0.8f), fontSize = 11.sp)
        Text("$${money(monto)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private fun money(v: Double) = String.format(Locale.US, "%,.2f", v)
