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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    // null = cerrado; Cuenta con id 0 = nueva
    var editando by remember { mutableStateOf<Cuenta?>(null) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text("Cuentas compartidas", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text("Por ejemplo \"Casa\", \"Viaje\" o \"Fútbol\". Lo que no se carga a una cuenta compartida " +
                        "es personal.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (cuentas.isEmpty()) item {
                Text("Todavía no hay cuentas. Tocá + para crear la primera.", Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(cuentas, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { editando = c }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(colorCuenta(c.id, ids)))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(c.nombre, fontWeight = FontWeight.Medium)
                        if (c.descripcion.isNotBlank()) Text(c.descripcion, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
        }
        FloatingActionButton(
            onClick = { editando = Cuenta(0, "", "") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Nueva cuenta") }
    }

    editando?.let { c -> DialogoCuenta(c, db) { editando = null } }
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
