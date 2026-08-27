package com.example.mapicomandas.ui.screens.turno

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
import com.example.mapicomandas.data.model.Mesero

private fun dinero(v: Double) = "$" + String.format(java.util.Locale.US, "%,.2f", v)
private fun fechaCorta(iso: String?) = iso?.take(16)?.replace('T', ' ') ?: "—"

/** Turno del restaurante: cierre, corte por mesero, propinas y meseros. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnoScreen(
    onVolver: () -> Unit,
    onIrHome: () -> Unit = {},
    viewModel: TurnoViewModel = hiltViewModel()
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
    LaunchedEffect(tab) { if (tab == 2) viewModel.cargarReglas() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Turno", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.Default.ArrowBack, "Volver") } },
                actions = {
                    IconButton(onClick = { viewModel.cargarPreview() }) { Icon(Icons.Default.Refresh, "Actualizar") }
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
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Cierre") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Corte mesero") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Propinas") })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Meseros") })
            }
            when (tab) {
                0 -> TabCierre(uiState, viewModel)
                1 -> TabCorteMesero(uiState, viewModel)
                2 -> TabPropinas(uiState, viewModel)
                3 -> TabMeseros(uiState, viewModel)
            }
        }
    }

    if (uiState.mostrarConfirmarCierre) {
        DialogoCerrarTurno(
            onConfirmar = { efectivo, obs -> viewModel.cerrarTurno(efectivo, obs) },
            onDismiss = { viewModel.setMostrarConfirmarCierre(false) }
        )
    }
    if (uiState.mostrarNuevoMesero) {
        DialogoMesero(
            mesero = uiState.meseroEnEdicion,
            onGuardar = { id, nombre, pin, activo -> viewModel.guardarMesero(id, nombre, pin, activo) },
            onDismiss = { viewModel.setMostrarNuevoMesero(false) }
        )
    }
}

// ── Pestaña 1: preview + cierre ─────────────────────────────────────────────
@Composable
private fun TabCierre(uiState: TurnoUiState, viewModel: TurnoViewModel) {
    val p = uiState.preview
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (uiState.cargando && p == null) {
            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }
        if (p == null) { Text("Sin datos del turno.", color = Color.Gray); return@Column }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Periodo: ${fechaCorta(p.desde)}  →  ${fechaCorta(p.hasta)}", fontSize = 12.sp, color = Color.Gray)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ventas (${p.numCuentas} cuentas)", fontWeight = FontWeight.Bold)
                    Text(dinero(p.totalVentas), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF2E7D32))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Propinas")
                    Text(dinero(p.propinas), color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (p.porFormaPago.isNotEmpty()) {
            Text("Por forma de pago", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            p.porFormaPago.forEach { f ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(f.forma, fontSize = 13.sp)
                    Text(dinero(f.importe), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        if (p.porMesero.isNotEmpty()) {
            Divider()
            Text("Por mesero", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            p.porMesero.forEach { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${m.nombre} (${m.cuentas})", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(dinero(m.total), fontSize = 13.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("prop. ${dinero(m.propinas)}", fontSize = 12.sp, color = Color(0xFF1565C0))
                }
            }
        }

        uiState.cierre?.let { c ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                Column(Modifier.padding(12.dp)) {
                    Text("✔ Turno cerrado — ${c.folio}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    Text("Ventas ${dinero(c.totalVentas)} · Propinas ${dinero(c.propinas)}", fontSize = 13.sp)
                }
            }
        }

        Button(
            onClick = { viewModel.setMostrarConfirmarCierre(true) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
        ) {
            Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp))
            Text("CERRAR TURNO", fontWeight = FontWeight.Bold)
        }
        Text("El cierre lleva candado anti doble cierre entre cajas y genera folio Z.",
            fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
private fun DialogoCerrarTurno(
    onConfirmar: (efectivoReal: Double?, observaciones: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var efectivo by remember { mutableStateOf("") }
    var obs by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cerrar turno") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = efectivo, onValueChange = { efectivo = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Efectivo contado (opcional)") }, prefix = { Text("$") }, singleLine = true
                )
                OutlinedTextField(
                    value = obs, onValueChange = { obs = it },
                    label = { Text("Observaciones") }, singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirmar(efectivo.toDoubleOrNull(), obs) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))) { Text("Cerrar turno") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ── Pestaña 2: corte por mesero ─────────────────────────────────────────────
@Composable
private fun TabCorteMesero(uiState: TurnoUiState, viewModel: TurnoViewModel) {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(uiState.meseros.filter { it.activo }) { m ->
                FilterChip(
                    selected = uiState.meseroSel?.idMesero == m.idMesero,
                    onClick = { viewModel.seleccionarMesero(m) },
                    label = { Text(m.nombre) }
                )
            }
        }
        val c = uiState.corte
        if (uiState.meseroSel == null) {
            Text("Elige un mesero para ver su hoja del turno.", color = Color.Gray, fontSize = 13.sp)
        } else if (c == null) {
            CircularProgressIndicator(Modifier.size(24.dp))
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(c.mesero, fontWeight = FontWeight.Bold)
                    Text("Desde ${fechaCorta(c.desde)}", fontSize = 12.sp, color = Color.Gray)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Ventas ${dinero(c.totalVentas)}", fontWeight = FontWeight.Bold)
                        Text("Propinas ${dinero(c.propinas)}", color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                    }
                    if (c.cortesias > 0 || c.descuentos > 0)
                        Text("Cortesías ${dinero(c.cortesias)} · Descuentos ${dinero(c.descuentos)}",
                            fontSize = 12.sp, color = Color(0xFFB71C1C))
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(c.cuentas) { cta ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("${cta.folio} · Mesa ${cta.mesa ?: "—"} · ${cta.personas} pers.",
                                fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("${fechaCorta(cta.apertura)} → ${fechaCorta(cta.cierre)}",
                                fontSize = 11.sp, color = Color.Gray)
                        }
                        Text(dinero(cta.total), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Divider()
                }
            }
        }
    }
}

// ── Pestaña 3: propinas (reglas + reparto) ──────────────────────────────────
@Composable
private fun TabPropinas(uiState: TurnoUiState, viewModel: TurnoViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Reglas por puesto (% de la bolsa)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        if (uiState.reglas.isEmpty())
            Text("Sin reglas — el reparto igualitario sigue disponible.", fontSize = 12.sp, color = Color.Gray)
        uiState.reglas.forEach { r ->
            var pct by remember(r.idPuesto, r.porcentaje) { mutableStateOf(r.porcentaje.toString()) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(r.puesto, modifier = Modifier.weight(1f), fontSize = 13.sp)
                OutlinedTextField(
                    value = pct, onValueChange = { pct = it.filter { c -> c.isDigit() || c == '.' } },
                    modifier = Modifier.width(90.dp), singleLine = true, suffix = { Text("%") }
                )
                IconButton(onClick = { pct.toDoubleOrNull()?.let { viewModel.guardarRegla(r.idPuesto, it) } }) {
                    Icon(Icons.Default.Save, "Guardar", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Divider(Modifier.padding(vertical = 6.dp))
        Text("Reparto del cierre", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        val cierre = uiState.cierre
        if (cierre == null && uiState.reparto == null) {
            Text("Cierra el turno para repartir sus propinas.", fontSize = 12.sp, color = Color.Gray)
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = uiState.repartoModo == "puestos",
                onClick = { viewModel.setRepartoModo("puestos") }, label = { Text("Por puestos") })
            FilterChip(selected = uiState.repartoModo == "igualitario",
                onClick = { viewModel.setRepartoModo("igualitario") }, label = { Text("Igualitario") })
        }
        uiState.reparto?.let { r ->
            Text("Bolsa: ${dinero(r.totalPropina)}" + if (r.yaRepartido) "  ·  YA REPARTIDO" else "",
                fontWeight = FontWeight.Bold,
                color = if (r.yaRepartido) Color(0xFF2E7D32) else Color.Unspecified)
            r.lineas.forEach { l ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${l.nombre} (${l.puesto})", fontSize = 13.sp)
                    Text(dinero(l.monto), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            if (r.sinRepartir > 0)
                Text("Sin repartir: ${dinero(r.sinRepartir)}", fontSize = 12.sp, color = Color(0xFFB71C1C))
            if (!r.yaRepartido && r.lineas.isNotEmpty())
                Button(onClick = { viewModel.aplicarReparto() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Payments, null); Spacer(Modifier.width(6.dp)); Text("Aplicar reparto")
                }
        }
    }
}

// ── Pestaña 4: meseros ──────────────────────────────────────────────────────
@Composable
private fun TabMeseros(uiState: TurnoUiState, viewModel: TurnoViewModel) {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { viewModel.setMostrarNuevoMesero(true) }) {
            Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text("Nuevo mesero")
        }
        LazyColumn(Modifier.weight(1f)) {
            items(uiState.meseros, key = { it.idMesero }) { m ->
                Row(
                    Modifier.fillMaxWidth().clickable { viewModel.editarMesero(m) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Person, null,
                        tint = if (m.activo) Color(0xFF2E7D32) else Color.Gray)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.nombre, fontWeight = FontWeight.Medium,
                            color = if (m.activo) Color.Unspecified else Color.Gray)
                        if (m.codigo.isNotBlank())
                            Text("PIN: ${"•".repeat(m.codigo.length)}", fontSize = 11.sp, color = Color.Gray)
                    }
                    if (!m.activo) Text("Inactivo", fontSize = 11.sp, color = Color.Gray)
                    Icon(Icons.Default.Edit, "Editar", modifier = Modifier.size(18.dp), tint = Color.Gray)
                }
                Divider()
            }
        }
    }
}

@Composable
private fun DialogoMesero(
    mesero: Mesero?,
    onGuardar: (idMesero: Int?, nombre: String, pin: String?, activo: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var nombre by remember { mutableStateOf(mesero?.nombre ?: "") }
    var pin by remember { mutableStateOf(mesero?.codigo ?: "") }
    var activo by remember { mutableStateOf(mesero?.activo ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mesero == null) "Nuevo mesero" else "Editar mesero") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = nombre, onValueChange = { nombre = it },
                    label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() } },
                    label = { Text("PIN (opcional)") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = activo, onCheckedChange = { activo = it })
                    Spacer(Modifier.width(8.dp)); Text("Activo")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onGuardar(mesero?.idMesero, nombre.trim(), pin.ifBlank { null }, activo) },
                enabled = nombre.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
