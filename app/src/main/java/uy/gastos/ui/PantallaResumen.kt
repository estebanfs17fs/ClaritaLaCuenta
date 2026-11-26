@file:OptIn(ExperimentalMaterial3Api::class)

package uy.gastos.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
fun PantallaResumen(
    modifier: Modifier, periodo: Periodo, ancla: LocalDate, alCambiar: (Periodo, LocalDate) -> Unit,
    filtro: Set<Long>, alCambiarFiltro: (Set<Long>) -> Unit,
) {
    val ctx = LocalContext.current
    val version by Cambios.version.collectAsState()
    val db = remember { Db.get(ctx) }
    val actual = rango(periodo, ancla)
    val inicioEvolucion = rango(periodo, mover(periodo, actual.inicio, -5)).inicio

    val cuentas = remember(version) { db.cuentas() }
    val todos = remember(version, periodo, ancla) { db.movimientos(inicioEvolucion.toString(), actual.hastaIso) }
    val ids = cuentas.map { it.id }
    val nombres = cuentas.associate { it.id to it.nombre }
    // Si el filtro tiene cuentas que ya no existen, se ignoran.
    val filtroValido = filtro.filter { it == 0L || it in ids }.toSet()
    val movs = if (filtroValido.isEmpty()) todos else todos.filter { claveDestino(it.cuentaEfectiva) in filtroValido }

    val monedas = movs.filter { it.tipo == "egreso" }.map { it.moneda }.distinct().sortedDescending() // UYU primero
    var monedaElegida by rememberSaveable { mutableStateOf("UYU") }
    val moneda = if (monedaElegida in monedas || monedas.isEmpty()) monedaElegida else monedas.first()

    fun porciones(lista: List<Movimiento>): List<Porcion> = lista
        .filter { it.tipo == "egreso" && it.moneda == moneda }
        .groupBy { it.cuentaEfectiva }
        .map { (cuenta, ms) ->
            Porcion(cuenta, cuenta?.let { nombres[it] } ?: "Personal", ms.sumOf { it.montoCentavos }, colorCuenta(cuenta, ids))
        }
        .sortedByDescending { it.monto }

    val delPeriodo = movs.filter { it.fecha >= actual.desdeIso }
    val porcionesActuales = porciones(delPeriodo)
    val total = porcionesActuales.sumOf { it.monto }
    val ingresos = delPeriodo.filter { it.tipo == "ingreso" && it.moneda == moneda }.sumOf { it.montoCentavos }
    val compras = delPeriodo.count { it.tipo == "egreso" && it.moneda == moneda }

    val barras = (5 downTo 0).map { atras ->
        val r = rango(periodo, mover(periodo, actual.inicio, -atras.toLong()))
        Barra(r, etiquetaCorta(periodo, r), porciones(movs.filter { it.fecha >= r.desdeIso && it.fecha < r.hastaIso }))
    }
    val anterior = barras[4].total

    val topComercios = delPeriodo.filter { it.tipo == "egreso" && it.moneda == moneda }
        .groupBy { it.nombreMostrado }
        .map { (nombre, ms) -> Triple(nombre, ms.sumOf { it.montoCentavos }, ms.first().cuentaEfectiva) }
        .sortedByDescending { it.second }.take(5)

    val reducido = movimientoReducido()
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Resumen") }, scrollBehavior = scroll,
                navigationIcon = { Box(Modifier.padding(start = 4.dp)) { LogoTicket(36.dp) } },
            )
        },
    ) { relleno ->
        Column(
            Modifier.padding(relleno).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SelectorPeriodo(periodo, ancla, alCambiar)
            FiltroCuentas(cuentas, filtroValido, alCambiarFiltro)
            if (monedas.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    monedas.forEach { m ->
                        FilterChip(selected = m == moneda, onClick = { monedaElegida = m },
                            label = { Text(if (m == "USD") "Dólares" else "Pesos") })
                    }
                }
            }

            // Tarjeta principal con el total
            Card(
                Modifier.fillMaxWidth(),
                // En modo oscuro el primario es muy claro; se usa el contenedor para no encandilar.
                colors = CardDefaults.cardColors(
                    containerColor = colorHeroe(), contentColor = colorSobreHeroe(),
                ),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (filtroValido.isEmpty()) "Gastado en total"
                        else "Gastado en " + porcionesNombres(filtroValido, nombres),
                        style = MaterialTheme.typography.labelLarge,
                        color = colorSobreHeroe().copy(alpha = 0.85f), maxLines = 1,
                    )
                    // Al cambiar de período el monto se desliza hacia arriba (adelante) o hacia abajo (atrás).
                    AnimatedContent(
                        targetState = actual.inicio to dinero(total, moneda),
                        transitionSpec = {
                            ejeVertical(targetState.first.compareTo(initialState.first).coerceIn(-1, 1), reducido)
                        },
                        label = "total",
                    ) { (_, texto) ->
                        Text(
                            texto, maxLines = 1, fontWeight = FontWeight.SemiBold,
                            style = if (texto.length > 13) MaterialTheme.typography.headlineLarge
                            else MaterialTheme.typography.displaySmall,
                        )
                    }
                    Text("$compras ${if (compras == 1) "compra" else "compras"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorSobreHeroe().copy(alpha = 0.85f))
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (anterior > 0) {
                            val cambio = ((total - anterior) * 100.0 / anterior).toInt()
                            Pastilla(
                                "${if (cambio >= 0) "+" else ""}$cambio% vs. período anterior",
                                if (cambio >= 0) Icons.AutoMirrored.Filled.TrendingUp
                                else Icons.AutoMirrored.Filled.TrendingDown,
                            )
                        }
                        if (ingresos > 0) Pastilla("Ingresos ${dineroCorto(ingresos, moneda)}", null, acento = true)
                    }
                }
            }

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Por cuenta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (total == 0L) {
                        Text("No hay gastos en este período.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        BarraSegmentada(porcionesActuales)
                        porcionesActuales.forEach { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Punto(p.color, 10.dp)
                                Text(p.nombre, Modifier.weight(1f).padding(start = 12.dp),
                                    style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                Text("${Math.round(p.monto * 100.0 / total)}%",
                                    Modifier.padding(end = 12.dp), style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(dinero(p.monto, moneda), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Evolución", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Tocá una barra para ver ese período.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Barras(barras, actual, moneda) { r -> alCambiar(periodo, r.inicio) }
                }
            }

            if (topComercios.isNotEmpty()) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Dónde más gastaste", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        topComercios.forEach { (nombre, monto, cuenta) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(nombre, colorCuenta(cuenta, ids), 36.dp)
                                Text(nombre, Modifier.weight(1f).padding(start = 12.dp), maxLines = 1)
                                Text(dinero(monto, moneda), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun porcionesNombres(filtro: Set<Long>, nombres: Map<Long, String>): String =
    filtro.map { if (it == 0L) "Personal" else nombres[it] ?: "" }.filter { it.isNotEmpty() }.joinToString(" + ")

/** Barra horizontal dividida por cuenta: muestra la proporción de un vistazo. */
@Composable
private fun BarraSegmentada(porciones: List<Porcion>) {
    Row(
        Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        porciones.forEach { p ->
            // Cada tramo crece hasta su proporción (las proporciones cambian al cambiar de período).
            val peso = crecerDesdeCero(p.monto.toFloat())
            Box(Modifier.weight(peso.coerceAtLeast(0.0001f)).fillMaxHeight().background(p.color))
        }
    }
}

/** Dato informativo dentro de la tarjeta principal (no es un botón). */
@Composable
private fun Pastilla(texto: String, icono: androidx.compose.ui.graphics.vector.ImageVector?, acento: Boolean = false) {
    // La pastilla de acento usa el verde menta (terciario) para destacar los ingresos.
    val fondo = if (acento) MaterialTheme.colorScheme.tertiaryContainer else colorSobreHeroe().copy(alpha = 0.14f)
    val tinta = if (acento) MaterialTheme.colorScheme.onTertiaryContainer else LocalContentColor.current
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(fondo)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icono != null) Icon(icono, null, Modifier.size(16.dp).padding(end = 4.dp), tint = tinta)
        Text(texto, style = MaterialTheme.typography.labelMedium, maxLines = 1, color = tinta)
    }
}

@Composable
private fun Barras(barras: List<Barra>, actual: Rango, moneda: String, alTocar: (Rango) -> Unit) {
    val maximo = barras.maxOf { it.total }.coerceAtLeast(1)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            barras.forEach { b ->
                Text(if (b.total > 0) dineroCorto(b.total, moneda).substringAfter(' ') else "", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            Modifier.fillMaxWidth().height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            barras.forEach { b ->
                val seleccionada = b.rango == actual
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                        .clickable { alTocar(b.rango) },
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    val fraccion = crecerDesdeCero(b.total.toFloat() / maximo)
                    if (fraccion > 0.001f) {
                        Column(
                            Modifier.fillMaxWidth().fillMaxHeight(fraccion.coerceAtLeast(0.03f))
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 2.dp, bottomEnd = 2.dp)),
                        ) {
                            b.porciones.sortedBy { it.cuentaId == null }.forEach { p ->
                                Box(Modifier.fillMaxWidth().weight(p.monto.toFloat())
                                    .background(if (seleccionada) p.color else p.color.copy(alpha = 0.4f)))
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            barras.forEach { b ->
                val sel = b.rango == actual
                Text(b.etiqueta, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center, maxLines = 1,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

