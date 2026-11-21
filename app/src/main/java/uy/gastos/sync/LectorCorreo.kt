package uy.gastos.sync

import android.content.Context
import android.util.Log
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Properties
import javax.mail.FetchProfile
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.MimeMessage
import javax.mail.search.ComparisonTerm
import javax.mail.search.ReceivedDateTerm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uy.gastos.data.Ajustes
import uy.gastos.data.ConfigCorreo
import uy.gastos.data.Db
import uy.gastos.data.EstadoLectura
import uy.gastos.parser.ItauParser

/** Se conecta a la casilla por IMAP y guarda los avisos de consumo nuevos. */
object LectorCorreo {
    private val mutex = Mutex()
    private val FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    private val FORMATO_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")

    private enum class Resultado { NUEVO, REPETIDO, IGNORADO, NO_RECONOCIDO }

    /** Revisa la casilla una vez. Devuelve la cantidad de consumos nuevos, o -1 si hubo un error. */
    suspend fun revisar(ctx: Context): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            val ajustes = Ajustes(ctx)
            try {
                val (nuevos, resumen) = revisarCasilla(ctx, ajustes.leer())
                ajustes.guardarEstado(EstadoLectura(ahora(), "", nuevos))
                ajustes.registrar("${ahora()} · $resumen")
                nuevos
            } catch (e: Throwable) {
                Log.w("LectorCorreo", "Error al revisar", e)
                val texto = describir(e)
                ajustes.guardarEstado(EstadoLectura(ahora(), texto, 0))
                ajustes.registrar("${ahora()} · Error: $texto")
                -1
            }
        }
    }

    /** Prueba la conexión paso a paso y devuelve un informe legible. No guarda nada. */
    suspend fun diagnosticar(ctx: Context): List<String> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val cfg = Ajustes(ctx).leer()
            val db = Db.get(ctx)
            val inf = mutableListOf<String>()
            inf += "Usuario: ${cfg.usuario.ifBlank { "(vacío)" }}"
            inf += "Contraseña: ${if (cfg.contrasena.isBlank()) "NO está guardada" else "guardada (${cfg.contrasena.length} caracteres)"}"
            inf += "Servidor: ${cfg.servidor}:${cfg.puerto}, carpeta ${cfg.carpeta}"
            inf += "Revisión automática: ${if (cfg.activado) "activada" else "DESACTIVADA"}"
            if (cfg.usuario.isBlank() || cfg.contrasena.isBlank()) {
                inf += "✗ Falta el usuario o la contraseña de aplicación."
                return@withContext inf
            }
            var store: Store? = null
            try {
                store = conectar(cfg)
                inf += "✓ Conexión e inicio de sesión correctos"
                val carpeta = store.getFolder(cfg.carpeta.ifBlank { "INBOX" })
                carpeta.open(Folder.READ_ONLY)
                inf += "✓ Carpeta abierta: ${carpeta.messageCount} correos en total"
                val desde = fechaDesde(cfg)
                val enRango = carpeta.search(ReceivedDateTerm(ComparisonTerm.GE, aDate(desde)))
                inf += "Correos desde $desde: ${enRango.size}"
                val ultimos = carpeta.messages.takeLast(5).reversed()
                if (ultimos.isNotEmpty()) inf += "Últimos ${ultimos.size} correos de la casilla:"
                for (msg in ultimos) {
                    val remitente = remitente(msg)
                    val cuerpo = texto(msg)
                    val pasaRem = pasaRemitente(cfg, remitente, cuerpo)
                    val pasaAsu = pasaAsunto(cfg, msg.subject ?: "", cuerpo)
                    val consumo = ItauParser.parsear(cuerpo)
                    val id = idMensaje(msg)
                    inf += buildString {
                        append("• ${fechaLocal(msg).replace('T', ' ')} · ${msg.subject ?: "(sin asunto)"}\n")
                        append("   De: $remitente\n")
                        append("   Remitente ${if (pasaRem) "✓" else "✗"} · Asunto ${if (pasaAsu) "✓" else "✗"} · ")
                        append(if (consumo != null) "Leído: ${consumo.comercio} ${consumo.moneda} ${consumo.montoCentavos / 100.0}" else "Formato no reconocido")
                        if (db.correoProcesado(id)) append(" · ya procesado")
                    }
                }
                carpeta.close(false)
            } catch (e: Throwable) {
                inf += "✗ ${describir(e)}"
                if ("AUTHENTICATIONFAILED" in describir(e).uppercase() || "Invalid credentials" in describir(e)) {
                    inf += "Revisá el usuario y que la contraseña sea una contraseña de aplicación de esa misma cuenta."
                }
            } finally {
                runCatching { store?.close() }
            }
            inf
        }
    }

    private fun ahora() = LocalDateTime.now().format(FORMATO_HORA)

    private fun describir(e: Throwable): String {
        val partes = mutableListOf<String>()
        var actual: Throwable? = e
        while (actual != null && partes.size < 3) {
            partes += "${actual.javaClass.simpleName}: ${actual.message ?: ""}".trim()
            actual = actual.cause?.takeIf { it !== actual }
        }
        return partes.joinToString(" ← ")
    }

    private fun conectar(cfg: ConfigCorreo): Store {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", cfg.servidor)
            put("mail.imaps.port", cfg.puerto.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.connectiontimeout", "20000")
            put("mail.imaps.timeout", "30000")
        }
        val store = Session.getInstance(props).getStore("imaps")
        store.connect(cfg.servidor, cfg.puerto, cfg.usuario.trim(), cfg.contrasena.replace(" ", ""))
        return store
    }

    private fun fechaDesde(cfg: ConfigCorreo): LocalDate =
        cfg.leerDesde.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
            ?: LocalDate.now().minusDays(30)

    private fun aDate(d: LocalDate): Date = Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant())

    private fun revisarCasilla(ctx: Context, cfg: ConfigCorreo): Pair<Int, String> {
        require(cfg.usuario.isNotBlank() && cfg.contrasena.isNotBlank()) {
            "Falta configurar el usuario o la contraseña de aplicación."
        }
        val db = Db.get(ctx)
        val store = conectar(cfg)
        try {
            val carpeta = store.getFolder(cfg.carpeta.ifBlank { "INBOX" })
            carpeta.open(Folder.READ_ONLY)
            try {
                // Se busca solo por fecha en el servidor; remitente y asunto se revisan acá,
                // así un asunto distinto o un reenvío manual no dejan afuera un aviso.
                val mensajes = carpeta.search(ReceivedDateTerm(ComparisonTerm.GE, aDate(fechaDesde(cfg))))
                carpeta.fetch(mensajes, FetchProfile().apply {
                    add(FetchProfile.Item.ENVELOPE); add(UIDFolder.FetchProfileItem.UID)
                })
                val cuenta = Resultado.entries.associateWith { 0 }.toMutableMap()
                for (msg in mensajes) {
                    val r = procesar(db, cfg, msg)
                    cuenta[r] = cuenta.getValue(r) + 1
                }
                val nuevos = cuenta.getValue(Resultado.NUEVO)
                val resumen = "${mensajes.size} correos en el período: $nuevos consumos nuevos, " +
                    "${cuenta.getValue(Resultado.REPETIDO)} ya leídos, ${cuenta.getValue(Resultado.IGNORADO)} de otros " +
                    "remitentes o asuntos, ${cuenta.getValue(Resultado.NO_RECONOCIDO)} sin formato reconocido"
                return nuevos to resumen
            } finally {
                carpeta.close(false)
            }
        } finally {
            store.close()
        }
    }

    private fun idMensaje(msg: Message): String {
        val fecha = msg.sentDate ?: msg.receivedDate ?: Date(0)
        return (msg as? MimeMessage)?.messageID?.trim()?.takeIf { it.isNotEmpty() }
            ?: "sin-id-${fecha.time}-${msg.subject}"
    }

    private fun remitente(msg: Message): String =
        msg.from?.joinToString(" ") { it.toString() } ?: ""

    private fun sinAcentos(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

    private fun pasaRemitente(cfg: ConfigCorreo, remitente: String, cuerpo: String): Boolean {
        val f = sinAcentos(cfg.filtroRemitente.trim())
        return f.isEmpty() || f in sinAcentos(remitente) || f in sinAcentos(cuerpo)
    }

    private fun pasaAsunto(cfg: ConfigCorreo, asunto: String, cuerpo: String): Boolean {
        val f = sinAcentos(cfg.filtroAsunto.trim())
        // Acepta el texto en el asunto o en el cuerpo (el título del aviso).
        return f.isEmpty() || f in sinAcentos(asunto) || f in sinAcentos(ItauParser.normalizar(cuerpo))
    }

    private fun fechaLocal(msg: Message): String {
        val fecha = msg.sentDate ?: msg.receivedDate ?: Date()
        return LocalDateTime.ofInstant(fecha.toInstant(), ZoneId.systemDefault()).format(FORMATO_FECHA)
    }

    private fun procesar(db: Db, cfg: ConfigCorreo, msg: Message): Resultado {
        val id = idMensaje(msg)
        if (db.correoProcesado(id)) return Resultado.REPETIDO
        val asunto = msg.subject ?: ""
        val fecha = fechaLocal(msg)
        val cuerpo = texto(msg)
        if (!pasaRemitente(cfg, remitente(msg), cuerpo) || !pasaAsunto(cfg, asunto, cuerpo)) {
            db.guardarIgnorado(id, fecha, asunto)
            return Resultado.IGNORADO
        }
        val consumo = ItauParser.parsear(cuerpo)
        if (consumo == null) {
            db.guardarNoReconocido(id, fecha, asunto, cuerpo)
            return Resultado.NO_RECONOCIDO
        }
        db.guardarConsumo(id, fecha, asunto, consumo)
        return Resultado.NUEVO
    }

    private fun texto(p: Part): String = try {
        when {
            p.isMimeType("text/*") -> when (val c = p.content) {
                is String -> c
                else -> p.inputStream.readBytes().toString(Charsets.UTF_8)
            }
            p.isMimeType("multipart/*") -> {
                val mp = p.content as Multipart
                (0 until mp.count).joinToString("\n") { texto(mp.getBodyPart(it)) }
            }
            p.isMimeType("message/rfc822") -> texto(p.content as Part)
            else -> ""
        }
    } catch (e: Exception) {
        Log.w("LectorCorreo", "No se pudo leer una parte del correo", e)
        ""
    }
}
