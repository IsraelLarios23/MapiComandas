package com.example.mapicomandas.ui.screens.comanda

import androidx.compose.foundation.clickable
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
import com.example.mapicomandas.data.api.dto.DescuentoCuentaPreviewDto
import com.example.mapicomandas.data.api.dto.MotivoAjusteDto
import com.example.mapicomandas.data.model.LineaComanda

// ═══════════════════════════════════════════════════════════════════════════
//  Diálogos de ajustes de partida y de cuenta (clon del menú contextual del
//  desktop): cortesía, descuento, corregir, devolver, dividir, transferir.
// ═══════════════════════════════════════════════════════════════════════════

/** Menú contextual de la partida (⋮). */
@Composable
fun MenuPartidaDialog(
    linea: LineaComanda,
    onCortesia: () -> Unit,
    onDescuento: () -> Unit,
    onQuitarAjuste: () -> Unit,
    onCorregir: () -> Unit,
    onDevolver: () -> Unit,
    onDividir: () -> Unit,
    onTransferir: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(linea.nombreArticulo, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OpcionMenu(Icons.Default.CardGiftcard, "Cortesía (no se cobra)", onCortesia)
                OpcionMenu(Icons.Default.Percent, "Descuento a la partida", onDescuento)
                OpcionMenu(Icons.Default.Backspace, "Quitar cortesía/descuento", onQuitarAjuste)
                Divider(Modifier.padding(vertical = 4.dp))
                OpcionMenu(Icons.Default.Edit, "Corregir cantidad / precio", onCorregir)
                OpcionMenu(Icons.Default.Replay, "Devolver a cocina (merma)", onDevolver)
                Divider(Modifier.padding(vertical = 4.dp))
                OpcionMenu(Icons.Default.CallSplit, "Dividir en partes iguales", onDividir)
                OpcionMenu(Icons.Default.SwapHoriz, "Transferir a otra mesa", onTransferir)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun OpcionMenu(icono: androidx.compose.ui.graphics.vector.ImageVector, texto: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)
    ) {
        Icon(icono, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(texto, fontSize = 14.sp)
    }
}

/** Selector de motivo reutilizable (chips). */
@Composable
private fun SelectorMotivo(
    motivos: List<MotivoAjusteDto>,
    seleccionado: Int,
    onSeleccionar: (MotivoAjusteDto) -> Unit
) {
    if (motivos.isEmpty()) {
        Text("Sin motivos en el catálogo — se usará el genérico.", fontSize = 12.sp, color = Color.Gray)
        return
    }
    Text("Motivo:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
    Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
        motivos.forEach { m ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onSeleccionar(m) }.padding(vertical = 2.dp)
            ) {
                RadioButton(selected = m.idMotivo == seleccionado, onClick = { onSeleccionar(m) })
                Text(m.nombre + if (m.requiereAutorizacion) "  🔒" else "", fontSize = 13.sp)
            }
        }
    }
}

/** Cortesía (tipo 1) o descuento por partida (tipo 2, % o importe). */
@Composable
fun DialogoAjustePartida(
    tipo: Int,
    linea: LineaComanda,
    motivos: List<MotivoAjusteDto>,
    onConfirmar: (idMotivo: Int, porcentaje: Double?, importe: Double?, nota: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val lista = remember(motivos, tipo) { motivos.filter { it.activo && (it.tipo == tipo || it.tipo == 0) } }
    var idMotivo by remember { mutableStateOf(lista.firstOrNull()?.idMotivo ?: 0) }
    var pct by remember {
        mutableStateOf(lista.firstOrNull()?.porcentajeSugerido?.takeIf { it > 0 }?.toString() ?: "")
    }
    var importe by remember { mutableStateOf("") }
    var nota by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tipo == 1) "Cortesía" else "Descuento a la partida") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${linea.cantidad.toInt()} × ${linea.nombreArticulo} — $${String.format(java.util.Locale.US, "%.2f", linea.total)}",
                    fontSize = 13.sp, color = Color.Gray)
                SelectorMotivo(lista, idMotivo) { m ->
                    idMotivo = m.idMotivo
                    if (tipo == 2 && m.porcentajeSugerido > 0) pct = m.porcentajeSugerido.toString()
                }
                if (tipo == 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pct, onValueChange = { pct = it.filter { c -> c.isDigit() || c == '.' }; if (it.isNotBlank()) importe = "" },
                            label = { Text("%") }, singleLine = true, modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = importe, onValueChange = { importe = it.filter { c -> c.isDigit() || c == '.' }; if (it.isNotBlank()) pct = "" },
                            label = { Text("$ importe") }, singleLine = true, modifier = Modifier.weight(1f)
                        )
                    }
                    Text("Captura % o importe (uno de los dos).", fontSize = 11.sp, color = Color.Gray)
                } else {
                    Text("La partida se marca al 100% sin cobro.", fontSize = 12.sp, color = Color.Gray)
                }
                OutlinedTextField(
                    value = nota, onValueChange = { nota = it },
                    label = { Text("Nota (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val p = pct.toDoubleOrNull()
                val i = importe.toDoubleOrNull()
                if (tipo == 2 && p == null && i == null) return@Button
                onConfirmar(idMotivo, if (tipo == 1) null else p, if (tipo == 1) null else i, nota)
            }) { Text("Aplicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Corregir cantidad y/o precio con motivo (queda en bitácora). */
@Composable
fun DialogoCorregirPartida(
    linea: LineaComanda,
    motivos: List<MotivoAjusteDto>,
    onConfirmar: (cantidad: Double?, precio: Double?, idMotivo: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val lista = remember(motivos) { motivos.filter { it.activo } }
    var idMotivo by remember { mutableStateOf(lista.firstOrNull()?.idMotivo ?: 0) }
    var cantidad by remember { mutableStateOf(linea.cantidad.toString()) }
    var precio by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", linea.precioUnitario)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Corregir partida") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(linea.nombreArticulo, fontSize = 13.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cantidad, onValueChange = { cantidad = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Cantidad") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = precio, onValueChange = { precio = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Precio unit.") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                SelectorMotivo(lista, idMotivo) { idMotivo = it.idMotivo }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirmar(cantidad.toDoubleOrNull(), precio.toDoubleOrNull(), idMotivo)
            }) { Text("Corregir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Devolver a cocina: la partida se cancela y se registra la merma. */
@Composable
fun DialogoDevolverPartida(
    linea: LineaComanda,
    motivos: List<MotivoAjusteDto>,
    onConfirmar: (idMotivo: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val lista = remember(motivos) { motivos.filter { it.activo } }
    var idMotivo by remember { mutableStateOf(lista.firstOrNull()?.idMotivo ?: 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Devolver a cocina") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${linea.cantidad.toInt()} × ${linea.nombreArticulo}", fontSize = 13.sp)
                Text("La partida se retira de la cuenta y queda registrada como merma.",
                    fontSize = 12.sp, color = Color.Gray)
                SelectorMotivo(lista, idMotivo) { idMotivo = it.idMotivo }
            }
        },
        confirmButton = { Button(onClick = { onConfirmar(idMotivo) }) { Text("Devolver") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Dividir la partida en N renglones iguales (para cobros por separado). */
@Composable
fun DialogoDividirPartes(
    linea: LineaComanda,
    onConfirmar: (partes: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var partes by remember { mutableStateOf("2") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dividir partida") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(linea.nombreArticulo, fontSize = 13.sp, color = Color.Gray)
                OutlinedTextField(
                    value = partes, onValueChange = { partes = it.filter { c -> c.isDigit() } },
                    label = { Text("Número de partes") }, singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { partes.toIntOrNull()?.takeIf { it >= 2 }?.let(onConfirmar) }) { Text("Dividir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Transferir la partida seleccionada a otra mesa (abre comanda allá si no hay). */
@Composable
fun DialogoTransferirMesa(
    mesas: List<com.example.mapicomandas.data.model.MesaUi>,
    onConfirmar: (idMesa: Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transferir a mesa…") },
        text = {
            if (mesas.isEmpty()) Text("No hay otras mesas disponibles.")
            else Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                mesas.forEach { m ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onConfirmar(m.idMesa) }.padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.TableRestaurant, null, modifier = Modifier.size(18.dp),
                            tint = if (m.status == 1) Color(0xFF2E7D32) else Color(0xFFF9A825))
                        Spacer(Modifier.width(10.dp))
                        Text("Mesa ${m.numero} · ${m.zona}" + if (m.status != 1) "  (ocupada)" else "",
                            fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Descuento a la CUENTA completa, con vista previa e impedimentos del server. */
@Composable
fun DialogoDescuentoCuenta(
    motivos: List<MotivoAjusteDto>,
    preview: DescuentoCuentaPreviewDto?,
    onPreview: (porcentaje: Double, idMotivo: Int) -> Unit,
    onAplicar: (porcentaje: Double, idMotivo: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val lista = remember(motivos) { motivos.filter { it.activo && (it.tipo == 2 || it.tipo == 0) } }
    var idMotivo by remember { mutableStateOf(lista.firstOrNull()?.idMotivo ?: 0) }
    var pct by remember {
        mutableStateOf(lista.firstOrNull()?.porcentajeSugerido?.takeIf { it > 0 }?.toString() ?: "10")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Descuento a la cuenta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pct, onValueChange = { pct = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Porcentaje %") }, singleLine = true
                )
                SelectorMotivo(lista, idMotivo) { m ->
                    idMotivo = m.idMotivo
                    if (m.porcentajeSugerido > 0) pct = m.porcentajeSugerido.toString()
                }
                preview?.let { p ->
                    Divider()
                    if (p.impedimentos.isEmpty()) {
                        Text("Descuento: −$${String.format(java.util.Locale.US, "%,.2f", p.importeDescuento)}",
                            color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                        Text("Nuevo total: $${String.format(java.util.Locale.US, "%,.2f", p.totalNuevo)}",
                            fontWeight = FontWeight.Bold)
                    } else p.impedimentos.forEach {
                        Text("⚠ $it", color = Color(0xFFB71C1C), fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { pct.toDoubleOrNull()?.let { onPreview(it, idMotivo) } }) {
                    Text("Vista previa")
                }
                Button(
                    onClick = { pct.toDoubleOrNull()?.let { onAplicar(it, idMotivo) } },
                    enabled = preview?.impedimentos?.isEmpty() != false
                ) { Text("Aplicar") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
