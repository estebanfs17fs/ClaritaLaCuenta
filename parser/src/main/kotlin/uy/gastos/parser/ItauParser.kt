package uy.gastos.parser

/** Consumo extraído de un aviso de Itaú. */
data class Consumo(
    val marca: String,
    val tarjeta: String,
    val comercioOriginal: String,
    val comercio: String,
    val montoCentavos: Long,
    val moneda: String, // "UYU" o "USD"
)

object ItauParser {
    private val PATRON = Regex(
        "consumo de su tarjeta\\s+(?<marca>[\\p{L}\\d ]+?)\\s+terminada en\\s+(?<tarjeta>\\d{4})" +
            "\\s*\\.?\\s*Realizado en\\s+(?<comercio>.+?)\\s+Monto:\\s*(?<monto>[\\d.,]+)\\s*" +
            "(?<moneda>Pesos|D[oó]lares|U\\\$S|USD|UYU|\\$)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val SUFIJOS = listOf(
        Regex("\\s+VISA\\s+VIS\\w*\\s*$", RegexOption.IGNORE_CASE),
        Regex("\\s+VISA\\s*$", RegexOption.IGNORE_CASE),
        Regex("\\s+(MASTERCARD|MASTER)\\s*\\w*\\s*$", RegexOption.IGNORE_CASE),
    )

    private val ENTIDADES = mapOf(
        "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'",
        "&aacute;" to "á", "&eacute;" to "é", "&iacute;" to "í", "&oacute;" to "ó", "&uacute;" to "ú",
        "&Aacute;" to "Á", "&Eacute;" to "É", "&Iacute;" to "Í", "&Oacute;" to "Ó", "&Uacute;" to "Ú",
        "&ntilde;" to "ñ", "&Ntilde;" to "Ñ",
    )

    fun htmlATexto(contenido: String): String {
        var t = contenido.replace(Regex("(?is)<(script|style).*?</\\1>"), " ")
        t = t.replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</td>|</tr>"), "\n")
        t = t.replace(Regex("<[^>]+>"), " ")
        for ((k, v) in ENTIDADES) t = t.replace(k, v)
        t = t.replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: " " }
        return t.replace(Regex("[ \\t\\r\\u000C\\u00A0]+"), " ")
    }

    fun limpiarComercio(nombre: String): String {
        var n = nombre.replace(Regex("\\s+"), " ").trim(' ', '.')
        for (s in SUFIJOS) {
            val nuevo = s.replace(n, "")
            if (nuevo.isNotEmpty()) n = nuevo
        }
        return n.trim().uppercase()
    }

    fun montoACentavos(texto: String): Long {
        val t = texto.trim().trimEnd('.', ',')
        val decimal: Char? = when {
            ',' in t && '.' in t -> if (t.lastIndexOf(',') > t.lastIndexOf('.')) ',' else '.'
            ',' in t -> if (t.substringAfterLast(',').length == 2) ',' else null
            '.' in t -> if (t.substringAfterLast('.').length == 2) '.' else null
            else -> null
        }
        val (entero, dec) = if (decimal != null) {
            t.substringBeforeLast(decimal) to t.substringAfterLast(decimal)
        } else t to "00"
        val e = entero.filter { it.isDigit() }.ifEmpty { "0" }.toLong()
        val d = (dec + "00").take(2).toLong()
        return e * 100 + d
    }

    fun normalizarMoneda(texto: String): String {
        val t = texto.lowercase()
        return if (t.startsWith("d") || "u\$s" in t || t == "usd") "USD" else "UYU"
    }

    /** Devuelve el consumo encontrado en el texto o HTML del correo, o null. */
    fun parsear(contenido: String): Consumo? {
        val m = PATRON.find(htmlATexto(contenido)) ?: return null
        val g = m.groups
        val original = g["comercio"]!!.value.replace(Regex("\\s+"), " ").trim()
        return Consumo(
            marca = g["marca"]!!.value.trim().lowercase().replaceFirstChar { it.uppercase() },
            tarjeta = g["tarjeta"]!!.value,
            comercioOriginal = original,
            comercio = limpiarComercio(original),
            montoCentavos = montoACentavos(g["monto"]!!.value),
            moneda = normalizarMoneda(g["moneda"]!!.value),
        )
    }
}
