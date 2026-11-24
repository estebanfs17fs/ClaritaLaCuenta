package uy.gastos.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * Paleta "Ciruela y menta" de Material 3: violeta ciruela como color principal y verde menta
 * como acento (terciario). Generada con material-color-utilities (semilla #6A3D78, acento #3FA38A),
 * así los pares de texto y fondo cumplen los contrastes de Material.
 */
private val Claro = lightColorScheme(
    primary = Color(0xFF804792),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFAD7FF),
    onPrimaryContainer = Color(0xFF662F79),
    secondary = Color(0xFF6A596C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF2DCF3),
    onSecondaryContainer = Color(0xFF514254),
    tertiary = Color(0xFF006B57),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF93F5D8),
    onTertiaryContainer = Color(0xFF005141),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFFF7FB),
    onBackground = Color(0xFF1E1A1E),
    surface = Color(0xFFFFF7FB),
    onSurface = Color(0xFF1E1A1E),
    surfaceVariant = Color(0xFFECDEEA),
    onSurfaceVariant = Color(0xFF4D444D),
    outline = Color(0xFF7E747E),
    outlineVariant = Color(0xFFD0C3CE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF1F7),
    surfaceContainer = Color(0xFFF5EBF1),
    surfaceContainerHigh = Color(0xFFEFE6EB),
    surfaceContainerHighest = Color(0xFFE9E0E6),
    inverseSurface = Color(0xFF342F33),
    inverseOnSurface = Color(0xFFF7EEF4),
    inversePrimary = Color(0xFFEFB0FF),
    surfaceDim = Color(0xFFE0D8DD),
    surfaceBright = Color(0xFFFFF7FB),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFFEFB0FF),
    onPrimary = Color(0xFF4D1660),
    primaryContainer = Color(0xFF662F79),
    onPrimaryContainer = Color(0xFFFAD7FF),
    secondary = Color(0xFFD5C0D7),
    onSecondary = Color(0xFF3A2B3D),
    secondaryContainer = Color(0xFF514254),
    onSecondaryContainer = Color(0xFFF2DCF3),
    tertiary = Color(0xFF77D8BD),
    onTertiary = Color(0xFF00382C),
    tertiaryContainer = Color(0xFF005141),
    onTertiaryContainer = Color(0xFF93F5D8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF161216),
    onBackground = Color(0xFFE9E0E6),
    surface = Color(0xFF161216),
    onSurface = Color(0xFFE9E0E6),
    surfaceVariant = Color(0xFF4D444D),
    onSurfaceVariant = Color(0xFFD0C3CE),
    outline = Color(0xFF998D98),
    outlineVariant = Color(0xFF4D444D),
    surfaceContainerLowest = Color(0xFF110D11),
    surfaceContainerLow = Color(0xFF1E1A1E),
    surfaceContainer = Color(0xFF221E22),
    surfaceContainerHigh = Color(0xFF2D292D),
    surfaceContainerHighest = Color(0xFF383338),
    inverseSurface = Color(0xFFE9E0E6),
    inverseOnSurface = Color(0xFF342F33),
    inversePrimary = Color(0xFF804792),
    surfaceDim = Color(0xFF161216),
    surfaceBright = Color(0xFF3D383C),
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Colores de las cuentas, armonizados con el primario, con variante para modo oscuro. */
object Paleta {
    @Volatile var oscura = false

    private val personalClaro = Color(0xFF756677)
    private val personalOscuro = Color(0xFFCEBBCE)
    private val claros = listOf(
        Color(0xFF9C4A48), Color(0xFF28679D), Color(0xFF007156), Color(0xFF8C5700),
        Color(0xFF904C7B), Color(0xFF6E6400), Color(0xFF625AA1), Color(0xFF7F5192),
    )
    private val oscuros = listOf(
        Color(0xFFFFABA6), Color(0xFF99C5F6), Color(0xFF84D0B3), Color(0xFFF2B56C),
        Color(0xFFF2ACD8), Color(0xFFCEC36D), Color(0xFFC1BAFA), Color(0xFFDEB1ED),
    )

    val personal get() = if (oscura) personalOscuro else personalClaro
    fun cuenta(indice: Int): Color = (if (oscura) oscuros else claros)[indice % claros.size]
}

@Composable
fun Tema(contenido: @Composable () -> Unit) {
    val oscuro = isSystemInDarkTheme()
    Paleta.oscura = oscuro
    MaterialTheme(
        colorScheme = if (oscuro) Oscuro else Claro, typography = Tipografia, shapes = Formas, content = contenido,
    )
}

/** Verde para ingresos, legible en claro y oscuro. */
@Composable
fun colorIngreso(): Color = if (isSystemInDarkTheme()) Color(0xFF89D6B9) else Color(0xFF007157)

/** Fondo y texto de la tarjeta principal del resumen. */
@Composable
fun colorHeroe(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary

@Composable
fun colorSobreHeroe(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
