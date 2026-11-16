package de.bgghome.webtrees.nativ

import de.bgghome.webtrees.nativ.api.LifespanGedcom
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.data.Lebensjahre
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Lebensjahre mit Kennzeichen fuer ungenaue Daten (Issue #44). */
class LebensjahreTest {

    private fun m(lifespan: String, birth: String?, death: String?) = Lebensjahre.markiert(lifespan, LifespanGedcom(birth, death))

    @Test
    fun kennzeichen() {
        assertEquals("1865–1918", m("1865–1918", "12 MAR 1865", "3 JUN 1918"))
        assertEquals("~1865–1918", m("1865–1918", "ABT 1865", "1918"))
        assertEquals("~1865–~1918", m("1865–1918", "EST 1865", "CAL 1918"))
        assertEquals("<1865–>1918", m("1865–1918", "BEF 1865", "AFT 1918"))
        assertEquals("1865–1915/1918", m("1865–1918", "1865", "BET 1915 AND 1918"))
        assertEquals("1865–1915/1918", m("1865–1918", "1865", "FROM 1915 TO 1918"))
        assertEquals("1865–1915/1918", m("1865–1918", "1865", "BET 1915 AND 1918 (laut Grabstein)"))
        assertEquals("~1865–1918", m("1865–1918", "@#DJULIAN@ ABT 1865", null))
        assertEquals("1750–1820", m("1750–1820", "BET 1750/51 AND 1750", "1820"))   // gleiches Jahr: unveraendert
    }

    @Test
    fun unveraendertWoNichtsZuTunIst() {
        assertEquals("1865–1918", Lebensjahre.markiert("1865–1918", null))                 // Server vor Stufe 44
        assertEquals("~1865–", m("1865–", "ABT 1865", null))                              // lebt: rechte Seite leer
        assertEquals("…–~1918", m("…–1918", null, "ABT 1918"))                             // "…" bekommt kein Zeichen
        assertEquals("1865–1918", m("1865–1918", "INT 1865 (Ostern)", "(um die Jahrhundertwende)"))
        assertEquals("1865 - 1918", m("1865 - 1918", "ABT 1865", null))                   // andere Form: nicht anfassen
    }

    @Test
    fun ausDemJsonDesServers() {
        val json = Json { ignoreUnknownKeys = true }
        val neu = json.decodeFromString(Person.serializer(),
            """{"xref":"I1","lifespan":"1865–1918","lifespanGedcom":{"birth":"1865","death":"BET 1915 AND 1918"}}""")
        assertEquals("1865–1915/1918", neu.lifespan)
        val alt = json.decodeFromString(Person.serializer(), """{"xref":"I1","lifespan":"1865–1918"}""")
        assertEquals("1865–1918", alt.lifespan)
        // Wieder geschrieben (Merkliste, Zwischenspeicher) bleibt das Feld "lifespan" der Server-Text
        assertEquals("1865–1918", json.decodeFromString(Person.serializer(), json.encodeToString(Person.serializer(), neu)).lifespanServer)
    }
}
