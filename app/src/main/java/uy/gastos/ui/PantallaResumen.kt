@file:OptIn(ExperimentalMaterial3Api::class)

package uy.gastos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import uy.gastos.data.Cambios
import uy.gastos.data.Db
import uy.gastos.data.Movimiento

private data class Porcion(val cuentaId: Long?, val nombre: String, val monto: Long, val color: Color)
private data class Barra(val rango: Rango, val etiqueta: String, val porciones: List<Porcion>) {
    val total get() = porciones.sumOf { it.monto }
}

@Composable
fun PantallaResumen(modifier: Modifier, periodo: Periodo, ancla: LocalDate, alCambiar: (Periodo, LocalDate) -> Unit) {
    val ctx = LocalContext.current
    val version by Cambios.version.collectAsState()
    val db = remember { Db.get(ctx) }
    val actual = rango(periodo, ancla)
    val inicioEvolucion = rango(periodo, mover(periodo, actual.inicio, -5)).inicio

    val cuentas = remember(version) { db.cuentas() }
    val movs = remember(version, periodo, ancla) {
        db.movimientos(inicioEvolucion.toString(), actual.hastaIso)
    }
    val ids = cuentas.map { it.id }
    val nombres = cuentas.associate { it.id to it.nombre }

    val monedas = movs.filter { it.tipo == "egreso" }.map { it.moneda }.distinct().sorted().reversed() // UYU primero
    var monedaElegida by rememberSaveable { mutableStateOf("UYU") }
    val moneda = if (monedaElegida in monedas || monedas.isEmpty()) monedaElegida else monedas.first()

    fun porciones(lista: List<Movimiento>): List<Porcion> = lista
        .filter { it.tipo == "egreso" && it.moneda == moneda }
        .groupBy { it.cuentaEfectiva }
        .map { (cuenta, ms) ->
            Porcion(cuenta, cuenta?.let { nombres[it] } ?: "Personal", ms.sumOf { it.montoCentavos }, colorCuenta(cuenta, ids))
        }
        .sortedWith(compareBy<Porcion> { it.cuentaId != null }.thenByDescending { it.monto })

    val delPeriodo = movs.filter { it.fecha >= actual.desdeIso }
    val porcionesActuales = porciones(delPeriodo)
    val total = porcionesActuales.sumOf { it.monto }
    val ingresos = delPeriodo.filter { it.tipo == "ingreso" && it.moneda == moneda }.sumOf { it.montoCentavos }

    val barras = (5 downTo 0).map { atras ->
        val r = rango(periodo, mover(periodo, actual.inicio, -atras.toLong()))
        Barra(r, etiquetaCorta(periodo, r), porciones(movs.filter { it.fecha >= r.desdeIso && it.fecha < r.hastaIso }))
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Resumen", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        SelectorPeriodo(periodo, ancla, alCambiar)
        if (monedas.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                monedas.forEach { m ->
                    FilterChip(selected = m == moneda, onClick = { monedaElegida = m },
                        label = { Text(if (m == "USD") "Dólares" else "Pesos") })
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Gastos por cuenta", style = MaterialTheme.typography.titleMedium)
                if (total == 0L) {
                    Text("No hay gastos en este período.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Dona(porcionesActuales, Modifier.size(180.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dinero(total, moneda), style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                    porcionesActuales.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(p.color))
                            Text(p.nombre, Modifier.weight(1f).padding(start = 8.dp))
                            Text("${Math.round(p.monto * 100.0 / total)}%",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dinero(p.monto, moneda), Modifier.padding(start = 12.dp), fontWeight = FontWeight.Medium)
                        }
                    }
                }
                if (ingresos > 0) {
                    Text("Ingresos del período: ${dinero(ingresos, moneda)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Evolución", style = MaterialTheme.typography.titleMedium)
                Text("Tocá una barra para ver ese período.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Barras(barras, actual, moneda) { r -> alCambiar(periodo, r.inicio) }
                // Leyenda con todas las cuentas que aparecen en el gráfico
                val enGrafico = barras.flatMap { it.porciones }.distinctBy { it.cuentaId }
                    .sortedWith(compareBy<Porcion> { it.cuentaId != null }.thenBy { it.nombre })
                enGrafico.chunked(3).forEach { fila ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        fila.forEach { p ->
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(p.color))
                                Text(p.nombre, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1)
                            }
                        }
                        repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Dona(porciones: List<Porcion>, modifier: Modifier) {
    val total = porciones.sumOf { it.monto }.toFloat()
    val fondo = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        val grosor = size.minDimension * 0.16f
        val lado = size.minDimension - grosor
        val origen = Offset((size.width - lado) / 2, (size.height - lado) / 2)
        drawArc(fondo, 0f, 360f, false, origen, Size(lado, lado), style = Stroke(grosor))
        var angulo = -90f
        val separacion = if (porciones.size > 1) 1.5f else 0f
        porciones.forEach { p ->
            val barrido = 360f * p.monto / total
            drawArc(p.color, angulo + separacion / 2, (barrido - separacion).coerceAtLeast(0.5f), false,
                origen, Size(lado, lado), style = Stroke(grosor))
            angulo += barrido
        }
    }
}

@Composable
private fun Barras(barras: List<Barra>, actual: Rango, moneda: String, alTocar: (Rango) -> Unit) {
    val maximo = barras.maxOf { it.total }.coerceAtLeast(1)
    val borde = MaterialTheme.colorScheme.primary
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            barras.forEach { b ->
                Text(if (b.total > 0) dineroCorto(b.total, moneda) else "", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
        Row(
            Modifier.fillMaxWidth().height(160.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            barras.forEach { b ->
                val seleccionada = b.rango == actual
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { alTocar(b.rango) },
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    val fraccion = b.total.toFloat() / maximo
                    if (fraccion > 0f) {
                        var m = Modifier.fillMaxWidth().fillMaxHeight(fraccion.coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        if (seleccionada) m = m.border(2.dp, borde, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        Column(m) {
                            // De arriba hacia abajo: cuentas compartidas y abajo lo personal.
                            b.porciones.sortedWith(compareBy<Porcion> { it.cuentaId == null }).forEach { p ->
                                Box(Modifier.fillMaxWidth().weight(p.monto.toFloat())
                                    .background(if (seleccionada) p.color else p.color.copy(alpha = 0.55f)))
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            barras.forEach { b ->
                Text(b.etiqueta, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center, maxLines = 1,
                    fontWeight = if (b.rango == actual) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
