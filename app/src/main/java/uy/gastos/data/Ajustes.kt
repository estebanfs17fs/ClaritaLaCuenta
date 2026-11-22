package uy.gastos.data

import android.content.Context
import androidx.core.content.edit

data class ConfigCorreo(
    val servidor: String = "imap.gmail.com",
    val puerto: Int = 993,
    val usuario: String = "cuentasmao689@gmail.com",
    val contrasena: String = "",
    val carpeta: String = "INBOX",
    val filtroRemitente: String = "itau.com.uy",
    val filtroAsunto: String = "consumo aprobado",
    val intervaloSegundos: Int = 60,
    val leerDesde: String = "", // yyyy-MM-dd, vacío = últimos 30 días
    val activado: Boolean = false,
)

data class EstadoLectura(val ultimaRevision: String, val ultimoError: String, val nuevosUltimaVez: Int)

class Ajustes(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    fun leer(): ConfigCorreo {
        val d = ConfigCorreo()
        return ConfigCorreo(
            servidor = prefs.getString("servidor", d.servidor)!!,
            puerto = prefs.getInt("puerto", d.puerto),
            usuario = prefs.getString("usuario", d.usuario)!!,
            contrasena = prefs.getString("contrasena", d.contrasena)!!,
            carpeta = prefs.getString("carpeta", d.carpeta)!!,
            filtroRemitente = prefs.getString("filtro_remitente", d.filtroRemitente)!!,
            filtroAsunto = prefs.getString("filtro_asunto", d.filtroAsunto)!!,
            intervaloSegundos = prefs.getInt("intervalo", d.intervaloSegundos),
            leerDesde = prefs.getString("leer_desde", d.leerDesde)!!,
            activado = prefs.getBoolean("activado", d.activado),
        )
    }

    fun guardar(c: ConfigCorreo) {
        prefs.edit {
            putString("servidor", c.servidor); putInt("puerto", c.puerto); putString("usuario", c.usuario)
            putString("contrasena", c.contrasena); putString("carpeta", c.carpeta)
            putString("filtro_remitente", c.filtroRemitente); putString("filtro_asunto", c.filtroAsunto)
            putInt("intervalo", c.intervaloSegundos.coerceAtLeast(30)); putString("leer_desde", c.leerDesde)
            putBoolean("activado", c.activado)
        }
        Cambios.avisar()
    }

    fun estado() = EstadoLectura(
        prefs.getString("ultima_revision", "")!!, prefs.getString("ultimo_error", "")!!,
        prefs.getInt("nuevos_ultima_vez", 0),
    )

    /** Registro de las últimas revisiones, la más nueva primero. */
    fun registro(): List<String> = prefs.getString("registro", "")!!.split("\n").filter { it.isNotBlank() }

    fun registrar(linea: String) {
        val nuevo = (listOf(linea.replace("\n", " ")) + registro()).take(30)
        prefs.edit { putString("registro", nuevo.joinToString("\n")) }
        Cambios.avisar()
    }

    fun fallasSeguidas(): Int = prefs.getInt("fallas_seguidas", 0)
    fun sumarFalla(): Int = (fallasSeguidas() + 1).also { n -> prefs.edit { putInt("fallas_seguidas", n) } }
    fun reiniciarFallas() { if (fallasSeguidas() != 0) prefs.edit { putInt("fallas_seguidas", 0) } }

    fun guardarEstado(e: EstadoLectura) {
        prefs.edit {
            putString("ultima_revision", e.ultimaRevision); putString("ultimo_error", e.ultimoError)
            putInt("nuevos_ultima_vez", e.nuevosUltimaVez)
        }
        Cambios.avisar()
    }
}
