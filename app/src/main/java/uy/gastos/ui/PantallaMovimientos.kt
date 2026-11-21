@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package uy.gastos.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.launch
import uy.gastos.data.Cambios
import uy.gastos.data.Cuenta
import uy.gastos.data.Db
import uy.gastos.data.Movimiento

@Composable
fun PantallaMovimientos(
    modifier: Modifier, periodo: Periodo, ancla: LocalDate, alCambiar: (Periodo, LocalDate) -> Unit,
    filtro: Set<Long>, alCambiarFiltro: (Set<Long>) -> Unit,
) {
    val ctx = LocalContext.current
    val version by Cambios.version.collectAsState()
    val db = remember { Db.get(ctx) }
    val r = rango(periodo, ancla)
    val cuentas = remember(version) { db.cuentas() }
    val ids = cuentas.map { it.id }
    val filtroValido = filtro.filter { it == 0L || it in ids }.toSet()
    val todos = remember(version, periodo, ancla) { db.movimientos(r.desdeIso, r.hastaIso) }
    val movs = if (filtroValido.isEmpty()) todos else todos.filter { claveDestino(it.cuentaEfectiva) in filtroValido }
    val porDia = movs.groupBy { it.fecha.take(10) }

    var detalle by remember { mutableStateOf<Movimiento?>(null) }
    var agregando by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Movimientos") }, scrollBehavior = scroll) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { agregando = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Agregar") },
            )
        },
    ) { relleno ->
        LazyColumn(Modifier.padding(relleno).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SelectorPeriodo(periodo, ancla, alCambiar)
                    FiltroCuentas(cuentas, filtroValido, alCambiarFiltro)
                    ResumenTotales(movs)
                }
            }
            if (movs.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.ReceiptLong, null, Modifier.padding(bottom = 8.dp),
                        tint = MaterialTheme.colorScheme.outline)
                    Text("No hay movimientos en este período.", textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            porDia.forEach { (dia, lista) ->
                stickyHeader(key = "dia-$dia") {
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(encabezadoDia(dia), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        val gastoDia = lista.filter { it.tipo == "egreso" && it.moneda == "UYU" }.sumOf { it.montoCentavos }
                        if (gastoDia > 0) Text(dinero(gastoDia, "UYU"), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(lista, key = { it.id }) { m -> FilaMovimiento(m, cuentas, ids) { detalle = m } }
            }
        }
    }

    detalle?.let { m ->
        HojaMovimiento(m, cuentas, db, cerrar = { detalle = null }) {
            detalle = null
            alcance.launch { snackbar.showSnackbar("Movimiento eliminado") }
        }
    }
    if (agregando) HojaNuevo(cuentas, db) { agregando = false }
}

@Composable
private fun ResumenTotales(movs: List<Movimiento>) {
    val gasto = movs.filter { it.tipo == "egreso" && it.moneda == "UYU" }.sumOf { it.montoCentavos }
    val ingreso = movs.filter { it.tipo == "ingreso" && it.moneda == "UYU" }.sumOf { it.montoCentavos }
    val gastoUsd = movs.filter { it.tipo == "egreso" && it.moneda == "USD" }.sumOf { it.montoCentavos }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Gastos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(dinero(gasto, "UYU"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (gastoUsd > 0) Text(dinero(gastoUsd, "USD"), style = MaterialTheme.typography.bodyMedium)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Ingresos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(dinero(ingreso, "UYU"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = colorIngreso())
            }
        }
    }
}

@Composable
private fun FilaMovimiento(m: Movimiento, cuentas: List<Cuenta>, ids: List<Long>, alTocar: () -> Unit) {
    val destino = m.cuentaEfectiva?.let { id -> cuentas.firstOrNull { it.id == id }?.nombre } ?: "Personal"
    val color = colorCuenta(m.cuentaEfectiva, ids)
    val tieneAlias = !m.nombrePropio.isNullOrBlank()
    ListItem(
        modifier = Modifier.clickable(onClick = alTocar),
        leadingContent = { Avatar(m.nombreMostrado, color) },
        headlineContent = { Text(m.nombreMostrado, fontWeight = FontWeight.Medium, maxLines = 1) },
        supportingContent = {
            val partes = buildList {
                if (tieneAlias) add(m.nombreOriginal ?: "")
                add(hora(m.fecha))
                m.tarjeta?.let { add("•• $it") }
            }.filter { it.isNotBlank() }
            Text(partes.joinToString(" · "), maxLines = 1)
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (m.tipo == "ingreso") "+ " else "") + dinero(m.montoCentavos, m.moneda),
                    fontWeight = FontWeight.SemiBold,
                    color = if (m.tipo == "ingreso") colorIngreso() else MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Punto(color, 8.dp)
                    Text(" $destino", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        },
    )
}

@Composable
private fun HojaMovimiento(m: Movimiento, cuentas: List<Cuenta>, db: Db, cerrar: () -> Unit, alBorrar: () -> Unit) {
    val ids = cuentas.map { it.id }
    val nombreComercio = m.cuentaComercio?.let { id -> cuentas.firstOrNull { it.id == id }?.nombre } ?: "Personal"
    // null = según comercio, 0 = personal, id = cuenta
    var destino by remember {
        mutableStateOf(when (m.asignacion) { "comercio" -> null; "personal" -> 0L; else -> m.cuentaId })
    }
    var confirmarBorrado by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = cerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(m.nombreMostrado, colorCuenta(m.cuentaEfectiva, ids), 48.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(m.nombreMostrado, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(encabezadoDia(m.fecha) + " · " + hora(m.fecha), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                (if (m.tipo == "ingreso") "+ " else "") + dinero(m.montoCentavos, m.moneda),
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = if (m.tipo == "ingreso") colorIngreso() else MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                m.tarjeta?.let { SuggestionChip(onClick = {}, label = { Text("Tarjeta •• $it") },
                    icon = { Icon(Icons.Filled.CreditCard, null) }) }
                SuggestionChip(onClick = {}, label = { Text(if (m.origen == "correo") "Desde el correo" else "Cargado a mano") })
            }
            if (!m.nombreOriginal.isNullOrBlank() || m.descripcion.isNotBlank()) {
                Text("Nombre en el banco", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(m.descripcion.ifBlank { m.nombreOriginal ?: "" }, style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            TituloSeccion("Se carga a")
            if (m.comercioId != null) {
                OpcionRadio("Según el comercio ($nombreComercio)", destino == null) { destino = null }
            }
            OpcionRadio("Personal", destino == 0L, colorCuenta(null, ids)) { destino = 0L }
            cuentas.forEach { c -> OpcionRadio(c.nombre, destino == c.id, colorCuenta(c.id, ids)) { destino = c.id } }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { confirmarBorrado = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.DeleteOutline, null)
                    Text(" Eliminar", color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = { db.asignarMovimiento(m.id, destino); cerrar() }, modifier = Modifier.weight(1f)) {
                    Text("Guardar")
                }
            }
        }
    }

    if (confirmarBorrado) {
        AlertDialog(
            onDismissRequest = { confirmarBorrado = false },
            icon = { Icon(Icons.Filled.DeleteOutline, null) },
            title = { Text("¿Eliminar este movimiento?") },
            text = {
                Text(if (m.origen == "correo") "Se borra de la app y no se vuelve a importar desde el correo."
                else "Se borra de la app.")
            },
            confirmButton = {
                TextButton(onClick = { db.borrarMovimiento(m.id); confirmarBorrado = false; alBorrar() }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarBorrado = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun HojaNuevo(cuentas: List<Cuenta>, db: Db, cerrar: () -> Unit) {
    val ids = cuentas.map { it.id }
    var tipo by remember { mutableStateOf("ingreso") }
    var fecha by remember { mutableStateOf(LocalDate.now().toString()) }
    var monto by remember { mutableStateOf("") }
    var moneda by remember { mutableStateOf("UYU") }
    var nombre by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var destino by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = cerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Nuevo movimiento", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(tipo == "ingreso", { tipo = "ingreso" }, { Text("Ingreso") })
                FilterChip(tipo == "egreso", { tipo = "egreso" }, { Text("Gasto") })
                Spacer(Modifier.weight(1f))
                FilterChip(moneda == "UYU", { moneda = "UYU" }, { Text("$") })
                FilterChip(moneda == "USD", { moneda = "USD" }, { Text("U\$S") })
            }
            OutlinedTextField(monto, { monto = it }, label = { Text("Monto") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(fecha, { fecha = it }, label = { Text("Fecha (aaaa-mm-dd)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(nombre, { nombre = it }, label = { Text("De quién o dónde (opcional)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(descripcion, { descripcion = it }, label = { Text("Descripción (opcional)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            TituloSeccion("Se carga a")
            OpcionRadio("Personal", destino == null, colorCuenta(null, ids)) { destino = null }
            cuentas.forEach { c -> OpcionRadio(c.nombre, destino == c.id, colorCuenta(c.id, ids)) { destino = c.id } }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            Button(
                onClick = {
                    val centavos = centavosDesdeTexto(monto)
                    val dia = runCatching { LocalDate.parse(fecha.trim()) }.getOrNull()
                    if (centavos == null || centavos <= 0 || dia == null) {
                        error = "Revisá el monto y la fecha."
                    } else {
                        val h = if (dia == LocalDate.now()) LocalTime.now() else LocalTime.NOON
                        db.agregarManual(LocalDateTime.of(dia, h).toString().take(16), tipo, nombre, descripcion,
                            centavos, moneda, destino)
                        cerrar()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            ) { Text("Guardar") }
            Box(Modifier.height(8.dp))
        }
    }
}
