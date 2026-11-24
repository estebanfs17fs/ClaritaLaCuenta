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
 * Paleta "Abeto y arena": verde abeto profundo como color principal, arena cálida como
 * secundario y terracota como acento, sobre neutros cálidos. Las cuentas usan tonos
 * apagados de la misma familia (ver Paleta).
 */
private val Claro = lightColorScheme(
    primary = Color(0xFF1F5F55), onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE9E1), onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF6E5D4B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4E3CF), onSecondaryContainer = Color(0xFF261A0D),
    tertiary = Color(0xFF9C4A2E), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCF), onTertiaryContainer = Color(0xFF3A0B00),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF9F8F5), onBackground = Color(0xFF1B1C1A),
    surface = Color(0xFFF9F8F5), onSurface = Color(0xFF1B1C1A),
    surfaceVariant = Color(0xFFDEE4DF), onSurfaceVariant = Color(0xFF4A4F4B),
    outline = Color(0xFF7A807B), outlineVariant = Color(0xFFC9CDC8),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF4F3EF),
    surfaceContainer = Color(0xFFEFEEEA), surfaceContainerHigh = Color(0xFFE9E8E4),
    surfaceContainerHighest = Color(0xFFE3E2DE),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFF93D4C6), onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF0B4A40), onPrimaryContainer = Color(0xFFCFE9E1),
    secondary = Color(0xFFDCC4A8), onSecondary = Color(0xFF3E2D1C),
    secondaryContainer = Color(0xFF56442F), onSecondaryContainer = Color(0xFFF4E3CF),
    tertiary = Color(0xFFFFB59D), onTertiary = Color(0xFF5C1A05),
    tertiaryContainer = Color(0xFF7D3519), onTertiaryContainer = Color(0xFFFFDBCF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121413), onBackground = Color(0xFFE3E3DF),
    surface = Color(0xFF121413), onSurface = Color(0xFFE3E3DF),
    surfaceVariant = Color(0xFF3F4943), onSurfaceVariant = Color(0xFFBEC9C2),
    outline = Color(0xFF88938C), outlineVariant = Color(0xFF3F4943),
    surfaceContainerLowest = Color(0xFF0D0F0E), surfaceContainerLow = Color(0xFF1A1C1B),
    surfaceContainer = Color(0xFF1E201F), surfaceContainerHigh = Color(0xFF282B29),
    surfaceContainerHighest = Color(0xFF333634),
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Colores de las cuentas, con variante para modo oscuro (contraste ≥ 4.5:1 sobre el fondo). */
object Paleta {
    @Volatile var oscura = false

    private val personalClaro = Color(0xFF56677A)
    private val personalOscuro = Color(0xFFA9B8C8)
    private val claros = listOf(
        Color(0xFFB5532F), Color(0xFF2C6A8A), Color(0xFF4E7D5B), Color(0xFF75528A),
        Color(0xFF9A6B12), Color(0xFFA3506A), Color(0xFF6A6F2A), Color(0xFF3F5B9C),
    )
    private val oscuros = listOf(
        Color(0xFFE8957A), Color(0xFF86BEDD), Color(0xFF94C7A1), Color(0xFFC6A3DA),
        Color(0xFFE2B860), Color(0xFFE59AB2), Color(0xFFC2C77A), Color(0xFFA6B8F0),
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
fun colorIngreso(): Color = if (isSystemInDarkTheme()) Color(0xFF8BD5A6) else Color(0xFF2E7D4F)
