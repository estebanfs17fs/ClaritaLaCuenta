@file:OptIn(ExperimentalMaterial3Api::class)

package uy.gastos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import uy.gastos.data.Cambios
import uy.gastos.data.Cuenta
import uy.gastos.data.Db
import uy.gastos.data.Movimiento

private val VERDE = Color(0xFF2F9E44)

@Composable
fun PantallaMovimientos(modifier: Modifier, periodo: Periodo, ancla: LocalDate, alCambiar: (Periodo, LocalDate) -> Unit) {
    val ctx = LocalContext.current
    val version by Cambios.version.collectAsState()
    val db = remember { Db.get(ctx) }
    val r = rango(periodo, ancla)
    val movs = remember(version, periodo, ancla) { db.movimientos(r.desdeIso, r.hastaIso) }
    val cuentas = remember(version) { db.cuentas() }
    val ids = cuentas.map { it.id }
    var editando by remember { mutableStateOf<Movimiento?>(null) }
    var agregando by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text("Movimientos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    SelectorPeriodo(periodo, ancla, alCambiar)
                }
            }
            if (movs.isEmpty()) item {
                Text("No hay movimientos en este período.", Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(movs, key = { it.id }) { m ->
                val destino = m.cuentaEfectiva?.let { id -> cuentas.firstOrNull { it.id == id }?.nombre } ?: "Personal"
                Row(
                    Modifier.fillMaxWidth().clickable { editando = m }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colorCuenta(m.cuentaEfectiva, ids)))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(m.nombreMostrado, fontWeight = FontWeight.Medium, maxLines = 1)
                        val detalle = listOfNotNull(fechaLegible(m.fecha), m.tarjeta?.let { "•• $it" }, destino)
                        Text(detalle.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Text(
                        (if (m.tipo == "ingreso") "+" else "") + dinero(m.montoCentavos, m.moneda),
                        fontWeight = FontWeight.Medium,
                        color = if (m.tipo == "ingreso") VERDE else MaterialTheme.colorScheme.onSurface,
                    )
                }
                HorizontalDivider(Modifier.padding(start = 38.dp))
            }
            item { Box(Modifier.padding(48.dp)) }
        }
        FloatingActionButton(
            onClick = { agregando = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Agregar movimiento") }
    }

    editando?.let { m -> DialogoAsignar(m, cuentas, db) { editando = null } }
    if (agregando) DialogoNuevo(cuentas, db) { agregando = false }
}

@Composable
private fun DialogoAsignar(m: Movimiento, cuentas: List<Cuenta>, db: Db, cerrar: () -> Unit) {
    val nombreComercio = m.cuentaComercio?.let { id -> cuentas.firstOrNull { it.id == id }?.nombre } ?: "Personal"
    // null = según comercio, 0 = personal, id = cuenta
    var destino by remember {
        mutableStateOf(when (m.asignacion) { "comercio" -> null; "personal" -> 0L; else -> m.cuentaId })
    }
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text(m.nombreMostrado) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${dinero(m.montoCentavos, m.moneda)} · ${fechaLegible(m.fecha)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (m.descripcion.isNotBlank() && m.descripcion != m.nombreMostrado) {
                    Text(m.descripcion, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Se carga a", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall)
                if (m.comercioId != null) {
                    OpcionRadio("Según el comercio ($nombreComercio)", destino == null) { destino = null }
                }
                OpcionRadio("Personal", destino == 0L) { destino = 0L }
                cuentas.forEach { c -> OpcionRadio(c.nombre, destino == c.id) { destino = c.id } }
                if (m.origen == "manual") {
                    TextButton(onClick = { db.borrarMovimiento(m.id); cerrar() }) {
                        Text("Borrar movimiento", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { db.asignarMovimiento(m.id, destino); cerrar() }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogoNuevo(cuentas: List<Cuenta>, db: Db, cerrar: () -> Unit) {
    var tipo by remember { mutableStateOf("ingreso") }
    var fecha by remember { mutableStateOf(LocalDate.now().toString()) }
    var monto by remember { mutableStateOf("") }
    var moneda by remember { mutableStateOf("UYU") }
    var nombre by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var destino by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text("Nuevo movimiento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(tipo == "ingreso", { tipo = "ingreso" }, { Text("Ingreso") })
                    FilterChip(tipo == "egreso", { tipo = "egreso" }, { Text("Egreso") })
                }
                OutlinedTextField(monto, { monto = it }, label = { Text("Monto") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(moneda == "UYU", { moneda = "UYU" }, { Text("Pesos") })
                    FilterChip(moneda == "USD", { moneda = "USD" }, { Text("Dólares") })
                }
                OutlinedTextField(fecha, { fecha = it }, label = { Text("Fecha (aaaa-mm-dd)") }, singleLine = true)
                OutlinedTextField(nombre, { nombre = it }, label = { Text("De quién o dónde (opcional)") },
                    singleLine = true)
                OutlinedTextField(descripcion, { descripcion = it }, label = { Text("Descripción") }, singleLine = true)
                Text("Se carga a", style = MaterialTheme.typography.titleSmall)
                OpcionRadio("Personal", destino == null) { destino = null }
                cuentas.forEach { c -> OpcionRadio(c.nombre, destino == c.id) { destino = c.id } }
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val centavos = centavosDesdeTexto(monto)
                val dia = runCatching { LocalDate.parse(fecha.trim()) }.getOrNull()
                if (centavos == null || centavos <= 0 || dia == null) {
                    error = "Revisá el monto y la fecha."
                } else {
                    val hora = if (dia == LocalDate.now()) LocalTime.now() else LocalTime.NOON
                    val iso = LocalDateTime.of(dia, hora).toString().take(16)
                    db.agregarManual(iso, tipo, nombre, descripcion, centavos, moneda, destino)
                    cerrar()
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
    )
}
