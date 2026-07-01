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
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mapicomandas.data.model.GrupoKds
import com.example.mapicomandas.data.model.PlatilloKds
import com.example.mapicomandas.data.model.StatusLinea

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KdsScreen(
    onVolver: () -> Unit,
    onIrHome: () -> Unit = {},
    viewModel: KdsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
                    .padding(padding),
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
                    .background(Color(0xFF0D0D0D))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.grupos, key = { it.idComanda }) { grupo ->
                    TarjetaKds(
                        grupo = grupo,
                        onMarcarListo = { idDetalle -> viewModel.marcarListo(idDetalle) },
                        onMarcarEntregado = { idDetalle -> viewModel.marcarEntregado(idDetalle) }
                    )
                }
            }
        }
    }
}

@Composable
fun TarjetaKds(
    grupo: GrupoKds,
    onMarcarListo: (Int) -> Unit,
    onMarcarEntregado: (Int) -> Unit
) {
    val minutos = grupo.maxMinutosTranscurridos
    val colorBorde = when {
        minutos >= 20 -> Color(0xFFF44336)
        minutos >= 10 -> Color(0xFFFF9800)
        else -> Color(0xFF4CAF50)
    }
    val colorEncabezado = when {
        minutos >= 20 -> Color(0xFFB71C1C)
        minutos >= 10 -> Color(0xFFE65100)
        else -> Color(0xFF1B5E20)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
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
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Folio: ${grupo.folio}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${minutos}m",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    if (minutos >= 20) {
                        Text(
                            text = "¡DEMORA!",
                            color = Color(0xFFFF6B6B),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Platillos
            Column(modifier = Modifier.padding(8.dp)) {
                grupo.platillos.forEach { platillo ->
                    FilaPlatilloKds(
                        platillo = platillo,
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
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.width(40.dp)
        )
        // Nombre y notas
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = platillo.articulo,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            if (platillo.kitRef.isNotBlank()) {
                Text(
                    text = "(Kit: ${platillo.kitRef})",
                    color = Color(0xFFBDBDBD),
                    fontSize = 12.sp
                )
            }
            if (platillo.notas.isNotBlank()) {
                Text(
                    text = "→ ${platillo.notas}",
                    color = Color(0xFFFFCC02),
                    fontSize = 12.sp
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
                    Text("Listo", fontSize = 12.sp)
                }
            }
            StatusLinea.LISTO -> {
                Button(
                    onClick = { onMarcarEntregado(platillo.idDetalleComanda) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Entregar", fontSize = 12.sp)
                }
            }
            StatusLinea.ENTREGADO -> {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
            }
        }
    }
    Divider(color = Color(0xFF333333), thickness = 0.5.dp)
}
