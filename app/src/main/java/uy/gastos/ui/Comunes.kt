@file:OptIn(ExperimentalMaterial3Api::class)

package uy.gastos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** Selector de tipo de período y flechas para moverse en el tiempo. */
@Composable
fun SelectorPeriodo(periodo: Periodo, ancla: LocalDate, alCambiar: (Periodo, LocalDate) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Periodo.entries.forEach { p ->
                FilterChip(selected = p == periodo, onClick = { alCambiar(p, ancla) }, label = { Text(p.nombre) })
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { alCambiar(periodo, mover(periodo, ancla, -1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Anterior")
            }
            Text(
                etiqueta(periodo, rango(periodo, ancla)),
                Modifier.weight(1f).clickable { alCambiar(periodo, LocalDate.now()) },
                textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { alCambiar(periodo, mover(periodo, ancla, 1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Siguiente")
            }
        }
    }
}

/** Fila de opción con radio, para listas de elección en diálogos. */
@Composable
fun OpcionRadio(texto: String, seleccionada: Boolean, alElegir: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = alElegir).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = seleccionada, onClick = alElegir)
        Text(texto, style = MaterialTheme.typography.bodyLarge)
    }
}
