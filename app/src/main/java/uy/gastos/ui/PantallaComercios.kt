@file:OptIn(ExperimentalMaterial3Api::class)

package uy.gastos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import uy.gastos.data.Comercio
import uy.gastos.data.Cuenta
import uy.gastos.data.Db

@Composable
fun PantallaComercios(modifier: Modifier) {
    val ctx = LocalContext.current
    val version by Cambios.version.collectAsState()
    val db = remember { Db.get(ctx) }
    val comercios = remember(version) { db.comercios() }
    val cuentas = remember(version) { db.cuentas() }
    val ids = cuentas.map { it.id }
    var editando by remember { mutableStateOf<Comercio?>(null) }

    LazyColumn(modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text("Comercios", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Tocá un comercio para ponerle un nombre propio y elegir a qué cuenta se cargan siempre sus " +
                    "gastos. Los que todavía no tienen nombre aparecen primero.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (comercios.isEmpty()) item {
            Text("Los comercios aparecen acá a medida que llegan los correos.", Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(comercios, key = { it.id }) { c ->
            val destino = c.cuentaId?.let { id -> cuentas.firstOrNull { it.id == id }?.nombre } ?: "Personal"
            Row(
                Modifier.fillMaxWidth().clickable { editando = c }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colorCuenta(c.cuentaId, ids)))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(c.nombreMostrado, fontWeight = FontWeight.Medium)
                    val sub = buildList {
                        if (!c.nombrePropio.isNullOrBlank()) add(c.nombreOriginal)
                        add(destino)
                        add("${c.cantidad} ${if (c.cantidad == 1) "compra" else "compras"}")
                    }
                    Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (c.totalUyu > 0) Text(dinero(c.totalUyu, "UYU"))
                    if (c.totalUsd > 0) Text(dinero(c.totalUsd, "USD"))
                }
            }
            HorizontalDivider(Modifier.padding(start = 38.dp))
        }
    }

    editando?.let { c -> DialogoComercio(c, cuentas, db) { editando = null } }
}

@Composable
private fun DialogoComercio(c: Comercio, cuentas: List<Cuenta>, db: Db, cerrar: () -> Unit) {
    var nombre by remember { mutableStateOf(c.nombrePropio ?: "") }
    var cuenta by remember { mutableStateOf(c.cuentaId) }
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text(c.nombreOriginal) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre propio") }, singleLine = true,
                    placeholder = { Text(c.nombreOriginal.lowercase().replaceFirstChar { it.uppercase() }) },
                    modifier = Modifier.fillMaxWidth())
                Text("Cargar siempre a", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall)
                OpcionRadio("Personal", cuenta == null) { cuenta = null }
                cuentas.forEach { cc -> OpcionRadio(cc.nombre, cuenta == cc.id) { cuenta = cc.id } }
                if (cuentas.isEmpty()) {
                    Text("Creá una cuenta compartida en la pestaña Cuentas para poder asignarla.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Se aplica a todas las compras de este comercio, salvo las que cambiaste a mano.",
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { db.guardarComercio(c.id, nombre, cuenta); cerrar() }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
    )
}
