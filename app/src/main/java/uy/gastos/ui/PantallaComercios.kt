@file:OptIn(ExperimentalMaterial3Api::class)

package uy.gastos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
    var busqueda by remember { mutableStateOf("") }
    var editando by remember { mutableStateOf<Comercio?>(null) }
    val visibles = comercios.filter {
        busqueda.isBlank() || it.nombreOriginal.contains(busqueda, true) ||
            (it.nombrePropio ?: "").contains(busqueda, true)
    }
    val sinAlias = comercios.count { it.nombrePropio.isNullOrBlank() }
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Comercios") }, scrollBehavior = scroll) },
    ) { relleno ->
        LazyColumn(Modifier.padding(relleno).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        busqueda, { busqueda = it }, Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar por nombre o alias") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = if (busqueda.isNotEmpty()) {
                            { IconButton(onClick = { busqueda = "" }) { Icon(Icons.Filled.Clear, "Borrar búsqueda") } }
                        } else null,
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                    if (sinAlias > 0) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Text(
                                "$sinAlias ${if (sinAlias == 1) "comercio no tiene" else "comercios no tienen"} alias. " +
                                    "Tocá uno para ponerle un nombre propio y elegir a qué cuenta se carga siempre.",
                                Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
            if (comercios.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Storefront, null, tint = MaterialTheme.colorScheme.outline)
                    Text("Los comercios aparecen acá a medida que llegan los correos.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(visibles, key = { it.id }) { c ->
                val destino = c.cuentaId?.let { id -> cuentas.firstOrNull { it.id == id }?.nombre } ?: "Personal"
                val tieneAlias = !c.nombrePropio.isNullOrBlank()
                ListItem(
                    modifier = Modifier.clickable { editando = c },
                    leadingContent = { Avatar(c.nombreMostrado, colorCuenta(c.cuentaId, ids)) },
                    headlineContent = { Text(c.nombreMostrado, fontWeight = FontWeight.Medium) },
                    overlineContent = if (tieneAlias) { { Text(c.nombreOriginal) } } else null,
                    supportingContent = {
                        Text("$destino · ${c.cantidad} ${if (c.cantidad == 1) "compra" else "compras"}" +
                            if (!tieneAlias) " · sin alias" else "")
                    },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            if (c.totalUyu > 0) Text(dinero(c.totalUyu, "UYU"), fontWeight = FontWeight.SemiBold)
                            if (c.totalUsd > 0) Text(dinero(c.totalUsd, "USD"), fontWeight = FontWeight.SemiBold)
                        }
                    },
                )
            }
        }
    }

    editando?.let { c -> HojaComercio(c, cuentas, db) { editando = null } }
}

@Composable
private fun HojaComercio(c: Comercio, cuentas: List<Cuenta>, db: Db, cerrar: () -> Unit) {
    val ids = cuentas.map { it.id }
    var alias by remember { mutableStateOf(c.nombrePropio ?: "") }
    var cuenta by remember { mutableStateOf(c.cuentaId) }

    ModalBottomSheet(onDismissRequest = cerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Editar comercio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Nombre en el banco (no cambia)", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(c.nombreOriginal, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                }
            }
            OutlinedTextField(
                alias, { alias = it }, Modifier.fillMaxWidth(),
                label = { Text("Alias") },
                placeholder = { Text(c.nombreOriginal.lowercase().replaceFirstChar { it.uppercase() }) },
                supportingText = { Text("Se muestra en las listas. El nombre del banco se conserva.") },
                singleLine = true,
                trailingIcon = if (alias.isNotEmpty()) {
                    { IconButton(onClick = { alias = "" }) { Icon(Icons.Filled.Clear, "Quitar alias") } }
                } else null,
            )
            TituloSeccion("Cargar siempre a")
            OpcionRadio("Personal", cuenta == null, colorCuenta(null, ids)) { cuenta = null }
            cuentas.forEach { cc -> OpcionRadio(cc.nombre, cuenta == cc.id, colorCuenta(cc.id, ids)) { cuenta = cc.id } }
            if (cuentas.isEmpty()) {
                Text("Creá una cuenta compartida en la pestaña Cuentas para poder asignarla.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Se aplica a todas las compras de este comercio, salvo las que cambiaste una por una.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = { db.guardarComercio(c.id, alias, cuenta); cerrar() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            ) { Text("Guardar") }
        }
    }
}
