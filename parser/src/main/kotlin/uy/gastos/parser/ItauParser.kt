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
        "consumo de su tarjeta\\s+(?<marca>[\\p{L}\\d ]+?)\\s+terminada en\\s*:?\\s*(?<tarjeta>\\d{4})" +
            "\\s*\\.?\\s*Realizado en\\s*:?\\s*(?<comercio>.+?)\\s*Monto\\s*:?\\s*(?<monto>[\\d.,]+)\\s*" +
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
        t = t.replace(Regex("&#[xX]([0-9a-fA-F]+);")) { m -> m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: " " }
        return t.replace(Regex("[ \\t\\r\\u000C\\u00A0]+"), " ")
    }

    private fun esHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    /** Decodifica quoted-printable que quedó sin decodificar (pasa con algunos reenvíos). */
    fun decodificarQuotedPrintable(t: String): String {
        if (!Regex("=\\r?\\n|=3D|=20|=C3=").containsMatchIn(t)) return t
        val sinCortes = t.replace(Regex("=\\r?\\n"), "")
        val bytes = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < sinCortes.length) {
            val c = sinCortes[i]
            if (c == '=' && i + 2 < sinCortes.length && esHex(sinCortes[i + 1]) && esHex(sinCortes[i + 2])) {
                bytes.write(sinCortes.substring(i + 1, i + 3).toInt(16)); i += 3
            } else {
                bytes.write(c.toString().toByteArray(Charsets.UTF_8)); i++
            }
        }
        return bytes.toString("UTF-8")
    }

    /**
     * Deja el texto listo para buscar el aviso, sea el correo original o un reenvío:
     * quita HTML, marcas de cita ("> "), negritas de texto plano (*), caracteres invisibles y saltos de línea.
     */
    fun normalizar(contenido: String): String {
        var t = htmlATexto(decodificarQuotedPrintable(contenido))
        t = t.replace(Regex("(?m)^[ \\t]*(>[ \\t]?)+"), "")
        t = t.replace(Regex("[\\u200B-\\u200D\\uFEFF\\u00AD]"), "")
        t = t.replace(Regex("[*_]+"), " ")
        return t.replace(Regex("\\s+"), " ")
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
        val m = PATRON.find(normalizar(contenido)) ?: return null
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
