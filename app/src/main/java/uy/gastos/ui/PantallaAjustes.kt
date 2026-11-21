@file:OptIn(ExperimentalMaterial3Api::class)

package uy.gastos.ui

import android.content.Intent
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
                Text("Última revisión: ${estado.ultimaRevision.ifBlank { "todavía no" }}")
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
        OutlinedTextField(desde, { desde = it }, label = { Text("Leer desde (aaaa-mm-dd, vacío = 30 días)") },
            singleLine = true, modifier = Modifier.fillMaxWidth())

        TituloSeccion("Frecuencia")
        OutlinedTextField(intervalo, { intervalo = it }, label = { Text("Con la app abierta, revisar cada (segundos)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Text("Con la app cerrada, Android la deja revisar cada 15 minutos.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Revisar automáticamente", Modifier.weight(1f))
            Switch(checked = activado, onCheckedChange = { activado = it })
        }

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
                revisarAhora()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Guardar y revisar") }

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

/** Muestra la parte del correo alrededor de "consumo", que es donde debería estar el aviso. */
private fun fragmentoRelevante(crudo: String): String {
    val t = ItauParser.normalizar(crudo).trim()
    val i = t.indexOf("consumo", ignoreCase = true)
    return if (i < 0) t.take(500) else t.substring((i - 100).coerceAtLeast(0), (i + 400).coerceAtMost(t.length))
}
