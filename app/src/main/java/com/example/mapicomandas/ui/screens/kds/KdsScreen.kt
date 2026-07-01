package com.example.mapicomandas.ui.screens.kds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.TextUnit
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mapicomandas.data.model.GrupoKds
import com.example.mapicomandas.data.model.PlatilloKds
import com.example.mapicomandas.data.model.StatusLinea

/** Paleta de colores del KDS según el tema (oscuro/claro). */
data class KdsPalette(
    val fondo: Color,
    val tarjeta: Color,
    val texto: Color,
    val textoSecundario: Color,
    val divisor: Color
) {
    companion object {
        val Oscuro = KdsPalette(
            fondo = Color(0xFF0D0D0D),
            tarjeta = Color(0xFF1E1E1E),
            texto = Color.White,
            textoSecundario = Color(0xFFBDBDBD),
            divisor = Color(0xFF333333)
        )
        val Claro = KdsPalette(
            fondo = Color(0xFFECEFF1),
            tarjeta = Color(0xFFFFFFFF),
            texto = Color(0xFF212121),
            textoSecundario = Color(0xFF616161),
            divisor = Color(0xFFCFD8DC)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KdsScreen(
    onVolver: () -> Unit,
    onIrHome: () -> Unit = {},
    viewModel: KdsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val palette = if (uiState.oscuro) KdsPalette.Oscuro else KdsPalette.Claro
    val fs = uiState.fontScale

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monitor de Cocina — KDS", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onIrHome) {
                        Icon(Icons.Default.Home, "Inicio")
                    }
                    // Ajuste de fuente
                    IconButton(onClick = { viewModel.reducirFuente() }) {
                        Icon(Icons.Default.Remove, "Reducir fuente")
                    }
                    Text("${(fs * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
                    IconButton(onClick = { viewModel.aumentarFuente() }) {
                        Icon(Icons.Default.Add, "Aumentar fuente")
                    }
                    // Tema claro/oscuro
                    IconButton(onClick = { viewModel.alternarTema() }) {
                        Icon(
                            if (uiState.oscuro) Icons.Default.LightMode else Icons.Default.DarkMode,
                            "Cambiar tema"
                        )
                    }
                    // Selector de punto de impresión
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = uiState.puntoSeleccionado == null,
                                onClick = { viewModel.seleccionarPunto(null) },
                                label = { Text("Todos") }
                            )
                        }
                        items(uiState.puntos) { punto ->
                            FilterChip(
                                selected = uiState.puntoSeleccionado == punto.idPuntoImpresion,
                                onClick = { viewModel.seleccionarPunto(punto.idPuntoImpresion) },
                                label = { Text(punto.nombre) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A237E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (uiState.grupos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(palette.fondo),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Kitchen, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    Text("Sin platillos en cocina", color = Color.Gray, fontSize = 18.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(palette.fondo)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.grupos, key = { it.idComanda }) { grupo ->
                    TarjetaKds(
                        grupo = grupo,
                        palette = palette,
                        fs = fs,
                        onMarcarListo = { idDetalle -> viewModel.marcarListo(idDetalle) },
                        onMarcarEntregado = { idDetalle -> viewModel.marcarEntregado(idDetalle) }
                    )
                }
            }
        }
    }
}

/** Multiplica un tamaño base por la escala de fuente del KDS. */
private fun TextUnit.scale(fs: Float): TextUnit = (this.value * fs).sp

@Composable
fun TarjetaKds(
    grupo: GrupoKds,
    palette: KdsPalette,
    fs: Float,
    onMarcarListo: (Int) -> Unit,
    onMarcarEntregado: (Int) -> Unit
) {
    val minutos = grupo.maxMinutosTranscurridos
    val colorEncabezado = when {
        minutos >= 20 -> Color(0xFFB71C1C)
        minutos >= 10 -> Color(0xFFE65100)
        else -> Color(0xFF1B5E20)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = palette.tarjeta),
        border = CardDefaults.outlinedCardBorder().copy(
            width = 2.dp,
        )
    ) {
        Column {
            // Encabezado de la tarjeta
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorEncabezado)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = grupo.mesa,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp.scale(fs)
                    )
                    Text(
                        text = "Folio: ${grupo.folio}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp.scale(fs)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${minutos}m",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp.scale(fs)
                    )
                    if (minutos >= 20) {
                        Text(
                            text = "¡DEMORA!",
                            color = Color(0xFFFF6B6B),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp.scale(fs)
                        )
                    }
                }
            }

            // Platillos
            Column(modifier = Modifier.padding(8.dp)) {
                grupo.platillos.forEach { platillo ->
                    FilaPlatilloKds(
                        platillo = platillo,
                        palette = palette,
                        fs = fs,
                        onMarcarListo = onMarcarListo,
                        onMarcarEntregado = onMarcarEntregado
                    )
                }
            }
        }
    }
}

@Composable
fun FilaPlatilloKds(
    platillo: PlatilloKds,
    palette: KdsPalette,
    fs: Float,
    onMarcarListo: (Int) -> Unit,
    onMarcarEntregado: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cantidad
        Text(
            text = "${platillo.cantidad.toInt()}×",
            color = palette.texto,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp.scale(fs),
            modifier = Modifier.width((40 * fs).dp)
        )
        // Nombre y notas
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = platillo.articulo,
                color = palette.texto,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp.scale(fs)
            )
            if (platillo.kitRef.isNotBlank()) {
                Text(
                    text = "(Kit: ${platillo.kitRef})",
                    color = palette.textoSecundario,
                    fontSize = 12.sp.scale(fs)
                )
            }
            if (platillo.notas.isNotBlank()) {
                Text(
                    text = "→ ${platillo.notas}",
                    color = Color(0xFFFFAA00),
                    fontSize = 12.sp.scale(fs)
                )
            }
        }
        // Botones de acción
        when (platillo.status) {
            StatusLinea.EN_COCINA -> {
                Button(
                    onClick = { onMarcarListo(platillo.idDetalleComanda) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Listo", fontSize = 12.sp.scale(fs))
                }
            }
            StatusLinea.LISTO -> {
                Button(
                    onClick = { onMarcarEntregado(platillo.idDetalleComanda) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Entregar", fontSize = 12.sp.scale(fs))
                }
            }
            StatusLinea.ENTREGADO -> {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
            }
        }
    }
    Divider(color = palette.divisor, thickness = 0.5.dp)
}
