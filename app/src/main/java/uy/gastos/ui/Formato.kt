package uy.gastos.ui

import androidx.compose.ui.graphics.Color
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

val ES: Locale = Locale.forLanguageTag("es-UY")

fun dinero(centavos: Long, moneda: String): String {
    val simbolo = if (moneda == "USD") "U\$S" else "$"
    val entero = abs(centavos) / 100
    val dec = abs(centavos) % 100
    val miles = entero.toString().reversed().chunked(3).joinToString(".").reversed()
    return "${if (centavos < 0) "-" else ""}$simbolo $miles,${dec.toString().padStart(2, '0')}"
}

/** Monto corto para ejes: "$ 12,5 mil". */
fun dineroCorto(centavos: Long, moneda: String): String {
    val simbolo = if (moneda == "USD") "U\$S" else "$"
    val v = centavos / 100.0
    return when {
        v >= 1_000_000 -> "$simbolo ${"%.1f".format(ES, v / 1_000_000)} M"
        v >= 1_000 -> "$simbolo ${"%.1f".format(ES, v / 1_000)} mil"
        else -> "$simbolo ${"%.0f".format(ES, v)}"
    }
}

/** Convierte "1.234,56" o "1234.56" a centavos. */
fun centavosDesdeTexto(texto: String): Long? {
    var t = texto.trim().replace(" ", "")
    if (t.isEmpty()) return null
    if (',' in t) t = t.replace(".", "").replace(',', '.')
    val v = t.toDoubleOrNull() ?: return null
    return Math.round(v * 100)
}

enum class Periodo(val nombre: String) { SEMANA("Semana"), MES("Mes"), TRIMESTRE("Trimestre"), ANIO("Año") }

data class Rango(val inicio: LocalDate, val fin: LocalDate) {
    val desdeIso get() = inicio.toString()
    val hastaIso get() = fin.toString()
}

fun rango(p: Periodo, ancla: LocalDate): Rango {
    val inicio = when (p) {
        Periodo.SEMANA -> ancla.with(DayOfWeek.MONDAY)
        Periodo.MES -> ancla.withDayOfMonth(1)
        Periodo.TRIMESTRE -> ancla.withMonth(((ancla.monthValue - 1) / 3) * 3 + 1).withDayOfMonth(1)
        Periodo.ANIO -> ancla.withDayOfYear(1)
    }
    return Rango(inicio, mover(p, inicio, 1))
}

fun mover(p: Periodo, fecha: LocalDate, n: Long): LocalDate = when (p) {
    Periodo.SEMANA -> fecha.plusWeeks(n)
    Periodo.MES -> fecha.plusMonths(n)
    Periodo.TRIMESTRE -> fecha.plusMonths(3 * n)
    Periodo.ANIO -> fecha.plusYears(n)
}

fun etiqueta(p: Periodo, r: Rango): String = when (p) {
    Periodo.SEMANA -> "Semana del ${r.inicio.format(DateTimeFormatter.ofPattern("d/M"))} al " +
        r.fin.minusDays(1).format(DateTimeFormatter.ofPattern("d/M/yyyy"))
    Periodo.MES -> r.inicio.month.getDisplayName(TextStyle.FULL, ES).replaceFirstChar { it.uppercase() } +
        " ${r.inicio.year}"
    Periodo.TRIMESTRE -> "Trimestre ${(r.inicio.monthValue - 1) / 3 + 1} de ${r.inicio.year}"
    Periodo.ANIO -> r.inicio.year.toString()
}

fun etiquetaCorta(p: Periodo, r: Rango): String = when (p) {
    Periodo.SEMANA -> r.inicio.format(DateTimeFormatter.ofPattern("d/M"))
    Periodo.MES -> r.inicio.month.getDisplayName(TextStyle.SHORT, ES).trimEnd('.')
    Periodo.TRIMESTRE -> "T${(r.inicio.monthValue - 1) / 3 + 1}"
    Periodo.ANIO -> r.inicio.year.toString()
}

fun fechaLegible(iso: String): String = iso.replace('T', ' ').let {
    // "2026-11-19 21:46" -> "19/11 21:46"
    if (it.length >= 16) "${it.substring(8, 10)}/${it.substring(5, 7)} ${it.substring(11, 16)}" else it
}

/** Color estable por cuenta: null = personal. */
fun colorCuenta(cuentaId: Long?, idsOrdenados: List<Long>): Color =
    if (cuentaId == null) Paleta.personal
    else Paleta.cuenta(idsOrdenados.indexOf(cuentaId).coerceAtLeast(0))

/** Encabezado de día para las listas: "Hoy", "Ayer" o "Jueves 19 de noviembre". */
fun encabezadoDia(iso: String): String {
    val dia = runCatching { LocalDate.parse(iso.take(10)) }.getOrNull() ?: return iso
    val hoy = LocalDate.now()
    return when (dia) {
        hoy -> "Hoy"
        hoy.minusDays(1) -> "Ayer"
        else -> {
            val formato = if (dia.year == hoy.year) "EEEE d 'de' MMMM" else "EEEE d 'de' MMMM 'de' yyyy"
            dia.format(DateTimeFormatter.ofPattern(formato, ES)).replaceFirstChar { it.uppercase() }
        }
    }
}

fun hora(iso: String): String = if (iso.length >= 16) iso.substring(11, 16) else ""

fun inicial(nombre: String): String = nombre.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"

/** Identificador de destino para filtros: 0 = Personal, otro = id de cuenta. */
fun claveDestino(cuentaId: Long?): Long = cuentaId ?: 0L
