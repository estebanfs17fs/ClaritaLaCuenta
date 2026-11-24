package uy.gastos.ui

import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset

/*
 * Movimiento de la app, según Material 3: duraciones de 100, 150, 200 y 300 ms,
 * curva "emphasized decelerate" al entrar y "emphasized accelerate" al salir.
 * Solo se animan posición y opacidad.
 */
object Ritmo {
    const val CORTO = 100
    const val MEDIO = 200
    const val LARGO = 300
    val Entrada = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Salida = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

/**
 * true si en el celular están desactivadas o reducidas las animaciones
 * ("Quitar animaciones" en Accesibilidad, o escala de animación en 0).
 * En ese caso Compose ya hace las animaciones instantáneas; además usamos solo fundidos.
 */
@Composable
fun movimientoReducido(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

private fun <T> entrada(duracion: Int = Ritmo.LARGO, demora: Int = 0): FiniteAnimationSpec<T> =
    tween(duracion, demora, Ritmo.Entrada)

private fun <T> salida(duracion: Int = Ritmo.CORTO): FiniteAnimationSpec<T> =
    tween(duracion, 0, Ritmo.Salida)

/** Solo fundido: para cuando el movimiento está reducido. */
fun fundido(): ContentTransform =
    fadeIn(tween(Ritmo.CORTO)) togetherWith fadeOut(tween(Ritmo.CORTO))

/**
 * Eje compartido horizontal (Material "shared axis X"): la pantalla nueva entra desde el lado
 * hacia donde se navegó, con un desplazamiento corto y un fundido.
 * [sentido] 1 = hacia adelante (derecha), -1 = hacia atrás.
 */
fun ejeHorizontal(sentido: Int, reducido: Boolean): ContentTransform {
    if (reducido) return fundido()
    val distancia = { ancho: Int -> (ancho / 10) * sentido }
    return (fadeIn(entrada(Ritmo.MEDIO, Ritmo.CORTO / 2)) +
        slideInHorizontally(entrada<IntOffset>()) { distancia(it) }) togetherWith
        (fadeOut(salida(Ritmo.CORTO)) + slideOutHorizontally(salida<IntOffset>(Ritmo.MEDIO)) { -distancia(it) })
}

/** Eje vertical para cifras que cambian (montos al cambiar de período). */
fun ejeVertical(sentido: Int, reducido: Boolean): ContentTransform {
    if (reducido || sentido == 0) return fundido()
    return (fadeIn(entrada(Ritmo.MEDIO)) + slideInVertically(entrada<IntOffset>()) { (it / 2) * sentido }) togetherWith
        (fadeOut(salida(Ritmo.CORTO)) + slideOutVertically(salida<IntOffset>(Ritmo.MEDIO)) { -(it / 2) * sentido })
}

/**
 * Valor que arranca en 0 la primera vez que se muestra y después sigue a [objetivo] con una
 * transición: las barras crecen al entrar a la pantalla y se ajustan al cambiar de período.
 */
@Composable
fun crecerDesdeCero(objetivo: Float): Float {
    val valor = remember { Animatable(0f) }
    LaunchedEffect(objetivo) { valor.animateTo(objetivo, entrada(Ritmo.LARGO)) }
    return valor.value
}
