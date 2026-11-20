package uy.gastos.sync

import android.content.Context
import android.util.Log
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Properties
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.internet.MimeMessage
import javax.mail.search.AndTerm
import javax.mail.search.ComparisonTerm
import javax.mail.search.ReceivedDateTerm
import javax.mail.search.SearchTerm
import javax.mail.search.SubjectTerm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uy.gastos.data.Ajustes
import uy.gastos.data.Db
import uy.gastos.data.EstadoLectura
import uy.gastos.parser.ItauParser

/** Se conecta a la casilla por IMAP y guarda los avisos de consumo nuevos. */
object LectorCorreo {
    private val mutex = Mutex()
    private val FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    /** Revisa la casilla una vez. Devuelve la cantidad de consumos nuevos, o lanza la excepción. */
    suspend fun revisar(ctx: Context): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            val ajustes = Ajustes(ctx)
            try {
                val nuevos = revisarCasilla(ctx, ajustes)
                ajustes.guardarEstado(EstadoLectura(ahora(), "", nuevos))
                nuevos
            } catch (e: Exception) {
                Log.w("LectorCorreo", "Error al revisar", e)
                ajustes.guardarEstado(EstadoLectura(ahora(), e.message ?: e.javaClass.simpleName, 0))
                -1
            }
        }
    }

    private fun ahora() = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

    private fun revisarCasilla(ctx: Context, ajustes: Ajustes): Int {
        val cfg = ajustes.leer()
        require(cfg.usuario.isNotBlank() && cfg.contrasena.isNotBlank()) {
            "Falta configurar el usuario o la contraseña de aplicación."
        }
        val db = Db.get(ctx)
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", cfg.servidor)
            put("mail.imaps.port", cfg.puerto.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.connectiontimeout", "20000")
            put("mail.imaps.timeout", "30000")
        }
        val store = Session.getInstance(props).getStore("imaps")
        store.connect(cfg.servidor, cfg.puerto, cfg.usuario, cfg.contrasena)
        try {
            val carpeta = store.getFolder(cfg.carpeta.ifBlank { "INBOX" })
            carpeta.open(Folder.READ_ONLY)
            try {
                val desde = cfg.leerDesde.takeIf { it.isNotBlank() }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: LocalDate.now().minusDays(30)
                var criterio: SearchTerm = ReceivedDateTerm(
                    ComparisonTerm.GE, Date.from(desde.atStartOfDay(ZoneId.systemDefault()).toInstant()),
                )
                if (cfg.filtroAsunto.isNotBlank()) criterio = AndTerm(criterio, SubjectTerm(cfg.filtroAsunto.trim()))
                var nuevos = 0
                for (msg in carpeta.search(criterio)) {
                    if (procesar(db, msg, cfg.filtroRemitente)) nuevos++
                }
                return nuevos
            } finally {
                carpeta.close(false)
            }
        } finally {
            store.close()
        }
    }

    /** Devuelve true si guardó un consumo nuevo. */
    private fun procesar(db: Db, msg: Message, filtroRemitente: String): Boolean {
        val asunto = msg.subject ?: ""
        val fechaMsg = msg.sentDate ?: msg.receivedDate ?: Date()
        val id = (msg as? MimeMessage)?.messageID?.trim()?.takeIf { it.isNotEmpty() }
            ?: "sin-id-${fechaMsg.time}-$asunto"
        if (db.correoProcesado(id)) return false

        val remitente = msg.from?.joinToString(" ") { it.toString() }?.lowercase() ?: ""
        val cuerpo = texto(msg)
        val filtro = filtroRemitente.trim().lowercase()
        // Acepta el remitente original o un reenvío manual que lo menciona en el cuerpo.
        if (filtro.isNotEmpty() && filtro !in remitente && filtro !in cuerpo.lowercase()) return false

        val fecha = LocalDateTime.ofInstant(fechaMsg.toInstant(), ZoneId.systemDefault()).format(FORMATO_FECHA)
        val consumo = ItauParser.parsear(cuerpo)
        if (consumo == null) {
            db.guardarNoReconocido(id, fecha, asunto, ItauParser.htmlATexto(cuerpo).trim())
            return false
        }
        db.guardarConsumo(id, fecha, asunto, consumo)
        return true
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
