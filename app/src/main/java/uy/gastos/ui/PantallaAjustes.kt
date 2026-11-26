@file:OptIn(ExperimentalMaterial3Api::class)

package uy.gastos.ui

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberDatePickerState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uy.gastos.data.Ajustes
import uy.gastos.data.Cambios
import uy.gastos.data.Db
import uy.gastos.parser.ItauParser
import uy.gastos.sync.LectorCorreo
import uy.gastos.sync.Notificaciones
import uy.gastos.sync.ServicioCorreo
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun PantallaAjustes(modifier: Modifier) {
    val ctx = LocalContext.current
    val ajustes = remember { Ajustes(ctx) }
    val db = remember { Db.get(ctx) }
    val alcance = rememberCoroutineScope()
    val version by Cambios.version.collectAsState()
    val estado = remember(version) { ajustes.estado() }
    val leidos = remember(version) { db.cantidadCorreosLeidos() }
    val noReconocidos = remember(version) { db.noReconocidos() }

    val inicial = remember { ajustes.leer() }
    var usuario by remember { mutableStateOf(inicial.usuario) }
    var contrasena by remember { mutableStateOf("") }
    var servidor by remember { mutableStateOf(inicial.servidor) }
    var puerto by remember { mutableStateOf(inicial.puerto.toString()) }
    var carpeta by remember { mutableStateOf(inicial.carpeta) }
    var remitente by remember { mutableStateOf(inicial.filtroRemitente) }
    var asunto by remember { mutableStateOf(inicial.filtroAsunto) }
    var desde by remember { mutableStateOf(inicial.leerDesde) }
    var intervalo by remember { mutableStateOf(inicial.intervaloSegundos.toString()) }
    var activado by remember { mutableStateOf(inicial.activado) }
    var revisando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf("") }
    var informe by remember { mutableStateOf<List<String>>(emptyList()) }
    var probando by remember { mutableStateOf(false) }
    var avanzado by remember { mutableStateOf(false) }
    var eligiendoDesde by remember { mutableStateOf(false) }
    // Lo técnico queda plegado; se abre solo si hay correos que no se pudieron leer.
    var diagnostico by remember { mutableStateOf(noReconocidos.isNotEmpty()) }
    val registro = remember(version) { ajustes.registro() }

    fun revisarAhora() {
        revisando = true
        alcance.launch {
            val n = LectorCorreo.revisar(ctx)
            aviso = if (n >= 0) "Listo: $n consumos nuevos." else ""
            revisando = false
        }
    }

    Scaffold(
        modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Ajustes del correo") }) },
    ) { relleno ->
    Column(
        Modifier.padding(relleno).fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Estado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Última revisión: ${momentoLegible(estado.ultimaRevision)}")
                Text("Consumos nuevos en la última revisión: ${estado.nuevosUltimaVez}")
                Text("Correos leídos en total: $leidos")
                if (estado.ultimoError.isNotBlank()) {
                    Text("Error: ${estado.ultimoError}", color = MaterialTheme.colorScheme.error)
                }
                if (aviso.isNotBlank()) Text(aviso, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = { revisarAhora() }, enabled = !revisando) {
                        Text(if (revisando) "Revisando…" else "Revisar ahora")
                    }
                    OutlinedButton(onClick = {
                        probando = true
                        informe = listOf("Probando…")
                        alcance.launch { informe = LectorCorreo.diagnosticar(ctx); probando = false }
                    }, enabled = !probando) { Text("Probar conexión") }
                }
            }
        }

        if (informe.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Prueba de conexión", style = MaterialTheme.typography.titleMedium)
                    informe.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        TituloSeccion("Cuenta de correo")
        OutlinedTextField(usuario, { usuario = it }, label = { Text("Usuario") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(contrasena, { contrasena = it }, label = { Text("Contraseña de aplicación") },
            placeholder = { Text(if (inicial.contrasena.isNotBlank()) "Guardada. Dejá vacío para no cambiarla." else "xxxx xxxx xxxx xxxx") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { avanzado = !avanzado }) {
            Text(if (avanzado) "Ocultar opciones avanzadas" else "Opciones avanzadas: servidor y filtros")
        }
        if (avanzado) {
            OutlinedTextField(servidor, { servidor = it }, label = { Text("Servidor IMAP") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(puerto, { puerto = it }, label = { Text("Puerto") }, singleLine = true,
                    modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(carpeta, { carpeta = it }, label = { Text("Carpeta") }, singleLine = true,
                    modifier = Modifier.weight(2f))
            }

            TituloSeccion("Qué correos leer")
            OutlinedTextField(remitente, { remitente = it }, label = { Text("El remitente contiene") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(asunto, { asunto = it }, label = { Text("El asunto o el texto contiene") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            // Fecha con selector en lugar de escribirla a mano en formato aaaa-mm-dd.
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { eligiendoDesde = true }, Modifier.weight(1f)) {
                    Icon(Icons.Filled.CalendarMonth, null)
                    Text("  " + (runCatching { "Leer desde el " + encabezadoDia(LocalDate.parse(desde).toString()).lowercase() }
                        .getOrDefault("Leer los últimos 30 días")))
                }
                if (desde.isNotBlank()) {
                    IconButton(onClick = { desde = "" }) { Icon(Icons.Filled.Clear, "Volver a los últimos 30 días") }
                }
            }
        }


        TituloSeccion("Revisión automática")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Revisar todo el tiempo")
                Text("También con la app cerrada y después de reiniciar el celular.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = activado, onCheckedChange = { activado = it })
        }
        // Frecuencia con opciones fijas en minutos en lugar de un número de segundos.
        Text("Revisar cada", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val opciones = listOf(60 to "1 min", 300 to "5 min", 900 to "15 min", 1800 to "30 min")
        val segundos = intervalo.toIntOrNull() ?: 60
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            opciones.forEach { (valor, texto) ->
                FilterChip(selected = segundos == valor, onClick = { intervalo = valor.toString() },
                    label = { Text(texto) }, enabled = activado)
            }
        }
        PermisosSegundoPlano(version = version)

        Button(
            onClick = {
                ajustes.guardar(inicial.copy(
                    usuario = usuario.trim(),
                    contrasena = contrasena.replace(" ", "").ifBlank { ajustes.leer().contrasena },
                    servidor = servidor.trim(),
                    puerto = puerto.toIntOrNull() ?: 993,
                    carpeta = carpeta.trim().ifBlank { "INBOX" },
                    filtroRemitente = remitente.trim(),
                    filtroAsunto = asunto.trim(),
                    leerDesde = desde.trim(),
                    intervaloSegundos = intervalo.toIntOrNull()?.coerceAtLeast(30) ?: 60,
                    activado = activado,
                ))
                contrasena = ""
                ServicioCorreo.sincronizar(ctx)
                revisarAhora()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Guardar y revisar") }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        TextButton(onClick = { diagnostico = !diagnostico }) {
            Text(
                (if (diagnostico) "Ocultar diagnóstico" else "Ver diagnóstico") +
                    if (noReconocidos.isNotEmpty()) " (${noReconocidos.size} correos sin leer)" else ""
            )
        }
        if (diagnostico) {
        OutlinedButton(onClick = { db.olvidarNoReconocidos(); revisarAhora() }, modifier = Modifier.fillMaxWidth()) {
            Text("Volver a leer los correos descartados")
        }
        Text("Usalo después de cambiar los filtros: vuelve a revisar los correos que se habían descartado.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (registro.isNotEmpty()) {
            TituloSeccion("Registro de revisiones")
            registro.take(15).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }

        if (noReconocidos.isNotEmpty()) {
            TituloSeccion("Correos que no se pudieron leer")
            Text("Coinciden con el filtro pero no tienen el formato esperado.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            noReconocidos.forEach { c ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${fechaLegible(c.fecha)} · ${c.asunto}", fontWeight = FontWeight.Medium)
                        Text(fragmentoRelevante(c.extracto), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            val envio = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, c.asunto)
                                putExtra(Intent.EXTRA_TEXT, c.extracto)
                            }
                            ctx.startActivity(Intent.createChooser(envio, "Compartir el correo"))
                        }) { Text("Compartir texto completo") }
                    }
                }
            }
        }
        }
    }
    }

    if (eligiendoDesde) {
        val estadoFecha = rememberDatePickerState(
            initialSelectedDateMillis = runCatching {
                LocalDate.parse(desde).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull(),
        )
        DatePickerDialog(
            onDismissRequest = { eligiendoDesde = false },
            confirmButton = {
                TextButton(onClick = {
                    estadoFecha.selectedDateMillis?.let {
                        desde = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    eligiendoDesde = false
                }) { Text("Elegir") }
            },
            dismissButton = { TextButton(onClick = { eligiendoDesde = false }) { Text("Cancelar") } },
        ) { DatePicker(estadoFecha) }
    }
}

/** "26/11 16:20:05" -> "hoy a las 16:20", "ayer a las 9:05" o "24/11 a las 16:20". */
private fun momentoLegible(texto: String): String {
    if (texto.isBlank()) return "todavía no"
    val m = Regex("""(\d{2})/(\d{2}) (\d{2}):(\d{2})""").find(texto) ?: return texto
    val (d, mes, h, min) = m.destructured
    val hoy = LocalDate.now()
    val dia = runCatching { LocalDate.of(hoy.year, mes.toInt(), d.toInt()) }.getOrNull() ?: return texto
    val horaTexto = "${h.toInt()}:$min"
    return when (dia) {
        hoy -> "hoy a las $horaTexto"
        hoy.minusDays(1) -> "ayer a las $horaTexto"
        else -> "$d/$mes a las $horaTexto"
    }
}

/** Muestra la parte del correo alrededor de "consumo", que es donde debería estar el aviso. */
private fun fragmentoRelevante(crudo: String): String {
    val t = ItauParser.normalizar(crudo).trim()
    val i = t.indexOf("consumo", ignoreCase = true)
    return if (i < 0) t.take(500) else t.substring((i - 100).coerceAtLeast(0), (i + 400).coerceAtMost(t.length))
}

/** Lo que Android necesita para que la revisión siga con la app cerrada: notificaciones y sin ahorro de batería. */
@Composable
private fun PermisosSegundoPlano(version: Int) {
    val ctx = LocalContext.current
    val ciclo = LocalLifecycleOwner.current.lifecycle
    var chequeo by remember { mutableStateOf(0) }
    // Al volver de los ajustes del sistema, volver a mirar el estado.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        ciclo.repeatOnLifecycle(Lifecycle.State.RESUMED) { chequeo++ }
    }
    val notificaciones = remember(chequeo, version) { Notificaciones.puedeNotificar(ctx) }
    val sinRestriccion = remember(chequeo, version) {
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    }
    if (notificaciones && sinRestriccion) {
        Text("Notificaciones y batería listas para revisar en segundo plano.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        return
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Falta un permiso", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (!notificaciones) {
                Text("Sin notificaciones no te puedo avisar de gastos nuevos ni de correos que no pude leer.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                }) { Text("Activar notificaciones") }
            }
            if (!sinRestriccion) {
                Text("Android puede pausar la revisión para ahorrar batería. Permití que la app funcione sin restricciones.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    val pedido = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                    try { ctx.startActivity(pedido) } catch (_: Exception) {
                        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }) { Text("Quitar el ahorro de batería") }
            }
        }
    }
}
