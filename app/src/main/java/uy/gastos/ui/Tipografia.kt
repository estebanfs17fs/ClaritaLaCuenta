package uy.gastos.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import uy.gastos.R

/*
 * Bricolage Grotesque para títulos y montos grandes; Figtree para textos, etiquetas y botones.
 * Tres pesos en toda la app: 400, 500 y 600. Los números usan cifras de ancho fijo ("tnum")
 * para que los montos queden alineados en columna.
 */
private val Bricolage = FontFamily(
    Font(R.font.bricolage_medium, FontWeight.Medium),
    Font(R.font.bricolage_semibold, FontWeight.SemiBold),
    // Si algún texto pide más peso, se usa el semibold en lugar de un negrita sintético.
    Font(R.font.bricolage_semibold, FontWeight.Bold),
)

private val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_semibold, FontWeight.Bold),
)

private const val CIFRAS = "tnum"

private fun titulo(tamano: Int, alto: Int, espaciado: Double = 0.0) = TextStyle(
    fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, fontSize = tamano.sp, lineHeight = alto.sp,
    letterSpacing = espaciado.em, fontFeatureSettings = CIFRAS,
)

private fun texto(tamano: Int, alto: Int, peso: FontWeight = FontWeight.Normal, espaciado: Double = 0.0) = TextStyle(
    fontFamily = Figtree, fontWeight = peso, fontSize = tamano.sp, lineHeight = alto.sp,
    letterSpacing = espaciado.em, fontFeatureSettings = CIFRAS,
)

val Tipografia = Typography(
    displayLarge = titulo(57, 64, -0.025),
    displayMedium = titulo(45, 52, -0.025),
    displaySmall = titulo(40, 46, -0.025),
    headlineLarge = titulo(32, 38, -0.02),
    headlineMedium = titulo(28, 34, -0.02),
    headlineSmall = titulo(24, 30, -0.015),
    titleLarge = titulo(22, 28, -0.01),
    titleMedium = titulo(17, 24),
    titleSmall = texto(14, 20, FontWeight.SemiBold),
    bodyLarge = texto(16, 24),
    bodyMedium = texto(14, 20),
    bodySmall = texto(12, 16),
    labelLarge = texto(14, 20, FontWeight.Medium),
    labelMedium = texto(12, 16, FontWeight.Medium),
    labelSmall = texto(12, 16, FontWeight.Medium),
)
