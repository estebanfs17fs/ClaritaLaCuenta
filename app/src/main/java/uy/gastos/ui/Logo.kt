package uy.gastos.ui

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uy.gastos.R

/** Mismo degradé ciruela del ícono de la app. */
private val FondoLogo = Brush.verticalGradient(listOf(Color(0xFF7A3F8D), Color(0xFF56206A)))

/**
 * Logo de la app: el ticket roto con cara. Al aparecer y al tocarlo, las dos mitades
 * se juntan, la cara queda completa un momento y vuelven a separarse.
 * Con las animaciones del sistema desactivadas queda quieto.
 */
@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun LogoTicket(tamano: Dp = 40.dp) {
    val imagen = AnimatedImageVector.animatedVectorResource(R.drawable.avd_ticket_juntar)
    val reducido = movimientoReducido()
    var junto by remember { mutableStateOf(false) }
    var toques by remember { mutableIntStateOf(0) }
    LaunchedEffect(toques) {
        if (reducido) return@LaunchedEffect
        delay(if (toques == 0) 400 else 0)
        junto = true
        delay(900)
        junto = false
    }
    // Área táctil de 48 dp (mínimo de Android) aunque el dibujo sea más chico.
    Box(
        Modifier.size(maxOf(tamano, 48.dp)).clip(CircleShape).clickable(onClickLabel = "Animar el logo") { toques++ },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = rememberAnimatedVectorPainter(imagen, atEnd = junto),
            contentDescription = "Gastos",
            modifier = Modifier.size(tamano).clip(CircleShape).background(FondoLogo),
        )
    }
}
