package com.example.mapicomandas.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.mapicomandas.R

/**
 * Fondo con logo tenue tipo marca de agua que parpadea (intermitencia).
 * Si se proporciona el logo del cliente (base64), alterna entre el logo de
 * MapiPOS y el del cliente en cada ciclo del parpadeo — igual que MapiPOS.
 *
 * Se usa como capa de fondo: coloca el contenido encima dentro del mismo Box.
 */
@Composable
fun FondoLogoIntermitente(
    modifier: Modifier = Modifier,
    clienteLogoBase64: String? = null,
    alphaMax: Float = 0.10f
) {
    val clienteBitmap: ImageBitmap? = remember(clienteLogoBase64) {
        clienteLogoBase64?.takeIf { it.isNotBlank() }?.let { b64 ->
            runCatching {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }

    // Transición infinita: alpha 0 → alphaMax → 0 (parpadeo)
    val transition = rememberInfiniteTransition(label = "fondoLogo")
    val ciclo by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ciclo"
    )

    // ciclo va de 0..2: primera mitad (0..1) muestra un logo, segunda (1..2) el otro
    val mostrarCliente = clienteBitmap != null && ciclo >= 1f
    // alpha en forma de campana dentro de cada mitad: sube y baja
    val fase = ciclo % 1f
    val alpha = (if (fase < 0.5f) fase * 2f else (1f - fase) * 2f) * alphaMax

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (mostrarCliente && clienteBitmap != null) {
            Image(
                bitmap = clienteBitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .alpha(alpha),
                contentScale = ContentScale.Fit
            )
        } else {
            Image(
                painter = painterResource(R.drawable.logo_mapipos),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .alpha(alpha),
                contentScale = ContentScale.Fit
            )
        }
    }
}
