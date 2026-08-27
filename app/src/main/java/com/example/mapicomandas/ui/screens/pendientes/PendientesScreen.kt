package com.example.mapicomandas.ui.screens.pendientes

import androidx.compose.foundation.clickable
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

/** Pagos en caja: cuentas pendientes de cobrar (toca una para ir al cobro) + bitácora. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendientesScreen(
    onCobrar: (Int) -> Unit,
    onVolver: () -> Unit,
    onIrHome: () -> Unit = {},
    viewModel: PendientesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.limpiarError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pagos en caja", fontWeight = FontWeight.Bold) },
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    text = { Text("Por cobrar (${uiState.pendientes.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text("Bitácora del día") })
            }
            if (uiState.cargando) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            if (tab == 0) {
                if (uiState.pendientes.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay cuentas pendientes de pago 🎉", color = Color.Gray)
                    }
                } else LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
                    items(uiState.pendientes, key = { it.idComanda }) { p ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clickable { onCobrar(p.idComanda) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (p.status == 4) Color(0xFFFFF3E0)
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ReceiptLong, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${p.folio}  ·  Mesa ${p.mesa ?: "—"}", fontWeight = FontWeight.Bold)
                                    Text(
                                        listOfNotNull(
                                            p.mesero?.takeIf { it.isNotBlank() },
                                            p.fechaApertura?.take(16)?.replace('T', ' '),
                                            if (p.status == 4) "CUENTA PEDIDA" else null
                                        ).joinToString("  ·  "),
                                        fontSize = 12.sp, color = Color.Gray
                                    )
                                }
                                Text("$${String.format(java.util.Locale.US, "%,.2f", p.total)}",
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2E7D32))
                                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                            }
                        }
                    }
                }
            } else {
                if (uiState.bitacora.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Sin acciones registradas hoy", color = Color.Gray)
                    }
                } else LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
                    items(uiState.bitacora) { a ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("${a.accion}  —  ${a.folio}", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                Text(
                                    listOfNotNull(
                                        a.articulo?.takeIf { it.isNotBlank() }?.let { "${a.cantidad.toInt()} × $it" },
                                        a.motivo?.takeIf { it.isNotBlank() },
                                        a.usuario?.takeIf { it.isNotBlank() },
                                        a.fecha?.take(16)?.replace('T', ' ')
                                    ).joinToString("  ·  "),
                                    fontSize = 11.sp, color = Color.Gray
                                )
                            }
                            if (a.importe != 0.0)
                                Text("$${String.format(java.util.Locale.US, "%,.2f", a.importe)}",
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Divider()
                    }
                }
            }
        }
    }
}
