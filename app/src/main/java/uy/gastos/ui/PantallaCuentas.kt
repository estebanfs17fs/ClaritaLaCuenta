@file:OptIn(ExperimentalMaterial3Api::class)

package uy.gastos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import uy.gastos.data.Cambios
import uy.gastos.data.Cuenta
import uy.gastos.data.Db

@Composable
fun PantallaCuentas(modifier: Modifier) {
    val ctx = LocalContext.current
    val version by Cambios.version.collectAsState()
    val db = remember { Db.get(ctx) }
    val cuentas = remember(version) { db.cuentas() }
    val ids = cuentas.map { it.id }
    val mes = rango(Periodo.MES, LocalDate.now())
    val gastosMes = remember(version) {
        db.movimientos(mes.desdeIso, mes.hastaIso).filter { it.tipo == "egreso" && it.moneda == "UYU" }
            .groupBy { it.cuentaEfectiva }.mapValues { (_, ms) -> ms.sumOf { it.montoCentavos } }
    }
    // null = cerrado; Cuenta con id 0 = nueva
    var editando by remember { mutableStateOf<Cuenta?>(null) }

    Scaffold(
        modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Cuentas compartidas") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { editando = Cuenta(0, "", "") },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Nueva cuenta") })
        },
    ) { relleno ->
        LazyColumn(
            Modifier.padding(relleno).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Lo que no se carga a una cuenta compartida es personal. Ejemplos: \"Casa\", \"Viaje\", \"Fútbol\".",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                TarjetaCuenta("Personal", "Todo lo que no es compartido", colorCuenta(null, ids), gastosMes[null] ?: 0L) {}
            }
            if (cuentas.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Group, null, tint = MaterialTheme.colorScheme.outline)
                    Text("Todavía no hay cuentas compartidas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(cuentas, key = { it.id }) { c ->
                TarjetaCuenta(c.nombre, c.descripcion, colorCuenta(c.id, ids), gastosMes[c.id] ?: 0L) { editando = c }
            }
        }
    }

    editando?.let { c -> DialogoCuenta(c, db) { editando = null } }
}

@Composable
private fun TarjetaCuenta(nombre: String, descripcion: String, color: androidx.compose.ui.graphics.Color,
                          gastoMes: Long, alTocar: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = alTocar)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(nombre, color, 48.dp)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (descripcion.isNotBlank()) Text(descripcion, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Este mes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(dinero(gastoMes, "UYU"), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DialogoCuenta(c: Cuenta, db: Db, cerrar: () -> Unit) {
    val nueva = c.id == 0L
    var nombre by remember { mutableStateOf(c.nombre) }
    var descripcion by remember { mutableStateOf(c.descripcion) }
    var error by remember { mutableStateOf("") }
    var confirmarBorrado by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = cerrar,
        icon = { Icon(Icons.Filled.Group, null) },
        title = { Text(if (nueva) "Nueva cuenta compartida" else "Editar cuenta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(descripcion, { descripcion = it }, label = { Text("Descripción (opcional)") })
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                if (!nueva) {
                    if (confirmarBorrado) {
                        Text("Sus gastos pasan a personales o vuelven a seguir al comercio. ¿Borrar?",
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { db.borrarCuenta(c.id); cerrar() }) {
                            Text("Sí, borrar", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        TextButton(onClick = { confirmarBorrado = true }) {
                            Text("Borrar cuenta", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    nombre.isBlank() -> error = "Poné un nombre."
                    !db.guardarCuenta(if (nueva) null else c.id, nombre, descripcion) ->
                        error = "Ya existe una cuenta con ese nombre."
                    else -> cerrar()
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
    )
}
