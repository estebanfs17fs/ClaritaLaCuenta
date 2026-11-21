package uy.gastos.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Esquema Material 3 generado a partir del naranja de marca (#EC7000).
private val Claro = lightColorScheme(
    primary = Color(0xFF9C4400), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC9), onPrimaryContainer = Color(0xFF331200),
    secondary = Color(0xFF765749), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCC), onSecondaryContainer = Color(0xFF2C160B),
    tertiary = Color(0xFF006A65), onTertiary = Color.White,
    tertiaryContainer = Color(0xFF9CF2EA), onTertiaryContainer = Color(0xFF00201E),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F5), onBackground = Color(0xFF221A15),
    surface = Color(0xFFFFF8F5), onSurface = Color(0xFF221A15),
    surfaceVariant = Color(0xFFF4DED4), onSurfaceVariant = Color(0xFF52443C),
    outline = Color(0xFF85746B), outlineVariant = Color(0xFFD7C2B8),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFF1EB),
    surfaceContainer = Color(0xFFFCEBE3), surfaceContainerHigh = Color(0xFFF6E5DD),
    surfaceContainerHighest = Color(0xFFF0DFD8),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFFFFB68D), onPrimary = Color(0xFF542100),
    primaryContainer = Color(0xFF773300), onPrimaryContainer = Color(0xFFFFDBC9),
    secondary = Color(0xFFE6BEAC), onSecondary = Color(0xFF442A1E),
    secondaryContainer = Color(0xFF5C4033), onSecondaryContainer = Color(0xFFFFDBCC),
    tertiary = Color(0xFF80D5CE), onTertiary = Color(0xFF003734),
    tertiaryContainer = Color(0xFF00504C), onTertiaryContainer = Color(0xFF9CF2EA),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A120D), onBackground = Color(0xFFF0DFD8),
    surface = Color(0xFF1A120D), onSurface = Color(0xFFF0DFD8),
    surfaceVariant = Color(0xFF52443C), onSurfaceVariant = Color(0xFFD7C2B8),
    outline = Color(0xFF9F8D84), outlineVariant = Color(0xFF52443C),
    surfaceContainerLowest = Color(0xFF140D09), surfaceContainerLow = Color(0xFF221A15),
    surfaceContainer = Color(0xFF271E19), surfaceContainerHigh = Color(0xFF322823),
    surfaceContainerHighest = Color(0xFF3D332D),
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun Tema(contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro,
        shapes = Formas,
        content = contenido,
    )
}

/** Verde para ingresos, legible en claro y oscuro. */
@Composable
fun colorIngreso(): Color = if (isSystemInDarkTheme()) Color(0xFF7DDC8E) else Color(0xFF1E7A35)
