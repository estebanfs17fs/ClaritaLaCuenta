package uy.gastos.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ItauParserTest {
    private val ejemploHtml = """<html><body><p style="color:#ff6200"><b>Aviso de consumo aprobado con tarjeta de d&eacute;bito</b></p>
<p><b>Se aprobo un consumo de su tarjeta Visa terminada en 1757 . Realizado en BOCATTI
VISA VIS3 Monto: 820.00 Pesos. Si no fuiste t&uacute; comun&iacute;cate al 1784.</b></p></body></html>"""

    @Test fun ejemploReal() {
        val c = assertNotNull(ItauParser.parsear(ejemploHtml))
        assertEquals("1757", c.tarjeta)
        assertEquals("Visa", c.marca)
        assertEquals("BOCATTI", c.comercio)
        assertEquals("BOCATTI VISA VIS3", c.comercioOriginal)
        assertEquals(82000, c.montoCentavos)
        assertEquals("UYU", c.moneda)
    }

    @Test fun dolares() {
        val c = assertNotNull(ItauParser.parsear("Se aprobo un consumo de su tarjeta Visa terminada en 0001 . " +
            "Realizado en AMAZON MKTPLACE VISA VIS3 Monto: 1,234.50 Dólares."))
        assertEquals("AMAZON MKTPLACE", c.comercio)
        assertEquals(123450, c.montoCentavos)
        assertEquals("USD", c.moneda)
    }

    @Test fun reenviado() {
        val c = assertNotNull(ItauParser.parsear("From: Itaú <comunicaciones@itau.com.uy>\nSe aprobo un consumo de su " +
            "tarjeta Visa terminada en 1757 . Realizado en TIENDA INGLESA SUC 12 VISA VIS3 Monto: 3.540,75 Pesos."))
        assertEquals("TIENDA INGLESA SUC 12", c.comercio)
        assertEquals(354075, c.montoCentavos)
    }

    @Test fun noReconocido() = assertNull(ItauParser.parsear("Hola"))

    @Test fun montos() {
        assertEquals(82000, ItauParser.montoACentavos("820.00"))
        assertEquals(82000, ItauParser.montoACentavos("820"))
        assertEquals(123400, ItauParser.montoACentavos("1.234"))
        assertEquals(123450, ItauParser.montoACentavos("1.234,5"))
    }
}
