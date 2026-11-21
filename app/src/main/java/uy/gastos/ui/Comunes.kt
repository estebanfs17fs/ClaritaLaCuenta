@file:OptIn(ExperimentalMaterial3Api::class)

package uy.gastos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import uy.gastos.data.Cuenta

/** Selector de tipo de período (botones segmentados) y navegación en el tiempo. */
@Composable
fun SelectorPeriodo(periodo: Periodo, ancla: LocalDate, alCambiar: (Periodo, LocalDate) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Periodo.entries.forEachIndexed { i, p ->
                SegmentedButton(
                    selected = p == periodo,
                    onClick = { alCambiar(p, ancla) },
                    shape = SegmentedButtonDefaults.itemShape(i, Periodo.entries.size),
                    icon = {},
                ) { Text(p.nombre, maxLines = 1) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { alCambiar(periodo, mover(periodo, ancla, -1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Período anterior")
            }
            Column(Modifier.weight(1f).clickable { alCambiar(periodo, LocalDate.now()) },
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(etiqueta(periodo, rango(periodo, ancla)), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                if (rango(periodo, ancla) != rango(periodo, LocalDate.now())) {
                    Text("Tocá para volver a hoy", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            FilledTonalIconButton(onClick = { alCambiar(periodo, mover(periodo, ancla, 1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Período siguiente")
            }
        }
    }
}

/**
 * Filtro de una o varias cuentas. [seleccion] vacía = todas.
 * La clave 0 representa lo personal.
 */
@Composable
fun FiltroCuentas(cuentas: List<Cuenta>, seleccion: Set<Long>, alCambiar: (Set<Long>) -> Unit) {
    val ids = cuentas.map { it.id }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = seleccion.isEmpty(), onClick = { alCambiar(emptySet()) },
            label = { Text("Todas") },
            leadingIcon = if (seleccion.isEmpty()) {
                { Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
            } else null,
        )
        val opciones = listOf(0L to "Personal") + cuentas.map { it.id to it.nombre }
        opciones.forEach { (clave, nombre) ->
            val elegida = clave in seleccion
            FilterChip(
                selected = elegida,
                onClick = { alCambiar(if (elegida) seleccion - clave else seleccion + clave) },
                label = { Text(nombre) },
                leadingIcon = { Punto(colorCuenta(clave.takeIf { it != 0L }, ids), 10.dp) },
            )
        }
    }
}

@Composable
fun Punto(color: Color, tamano: Dp = 10.dp) {
    Box(Modifier.size(tamano).clip(CircleShape).background(color))
}

/** Círculo con la inicial del nombre, coloreado según la cuenta. */
@Composable
fun Avatar(nombre: String, color: Color, tamano: Dp = 40.dp) {
    Box(
        Modifier.size(tamano).clip(CircleShape).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(inicial(nombre), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

/** Fila de opción con radio, para listas de elección. */
@Composable
fun OpcionRadio(texto: String, seleccionada: Boolean, color: Color? = null, alElegir: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = alElegir)
            .padding(vertical = 2.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = seleccionada, onClick = alElegir)
        if (color != null) { Punto(color); Box(Modifier.size(8.dp)) }
        Text(texto, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun TituloSeccion(texto: String, modifier: Modifier = Modifier) {
    Text(texto, modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
}
