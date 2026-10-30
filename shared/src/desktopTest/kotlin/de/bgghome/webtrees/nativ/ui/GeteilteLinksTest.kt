package de.bgghome.webtrees.nativ.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GeteilteLinksTest {
    private val base = "https://stammbaum.example.org/webtrees"

    @Test
    fun schoeneAdresseImText() {
        val link = datensatzAusText("Schau mal: https://stammbaum.example.org/webtrees/tree/falkenrath/individual/I130/Georg-Mohwinkel.", base)
        assertEquals(GeteilterLink("falkenrath", "INDI", "I130", "https://stammbaum.example.org/webtrees/tree/falkenrath/individual/I130/Georg-Mohwinkel"), link)
    }

    @Test
    fun routeFormMitKodierung() {
        val url = "https://stammbaum.example.org/webtrees/index.php?route=%2Fwebtrees%2Ftree%2Ftree1%2Fsource%2FS5%2FKirchenbuch&foo=1"
        assertEquals(GeteilterLink("tree1", "SOUR", "S5", url), datensatzAusText("Quelle: $url", base))
    }

    @Test
    fun weitereArtenUndBaumMitLeerzeichen() {
        assertEquals("OBJE", datensatzAusUrl("https://x.org/tree/mein%20baum/media/M3")?.type)
        assertEquals("mein baum", datensatzAusUrl("https://x.org/tree/mein%20baum/media/M3")?.tree)
        assertEquals("_LOC", datensatzAusUrl("https://x.org/tree/t/location/L1/Celle")?.type)
        assertEquals("FAM", datensatzAusUrl("https://x.org/tree/t/family/F8")?.type)
    }

    @Test
    fun fremderServerUndKeinLink() {
        assertNull(datensatzAusText("https://anderer.example.org/tree/t/individual/I1", base))
        assertNull(datensatzAusText("nur Text ohne Adresse", base))
        assertNull(datensatzAusText("https://stammbaum.example.org/webtrees/tree/t/", base))
        assertNull(datensatzAusText("https://stammbaum.example.org/tree/t/individual/I1", ""))
    }

    @Test
    fun schemaZaehltNichtPortSchon() {
        assertEquals("I1", datensatzAusText("http://stammbaum.example.org/webtrees/tree/t/individual/I1", base)?.xref)
        assertNull(datensatzAusText("https://stammbaum.example.org:8443/webtrees/tree/t/individual/I1", base))
        assertEquals("I1", datensatzAusText("http://192.168.178.73:8095/tree/t/individual/I1", "http://192.168.178.73:8095")?.xref)
    }

    @Test
    fun ersterEigenerLinkGewinnt() {
        val text = "https://anderer.example.org/tree/t/individual/I9 und https://stammbaum.example.org/webtrees/tree/t/individual/I2"
        assertEquals("I2", datensatzAusText(text, base)?.xref)
    }
}
