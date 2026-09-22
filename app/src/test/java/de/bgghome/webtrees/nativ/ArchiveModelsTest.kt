package de.bgghome.webtrees.nativ

import de.bgghome.webtrees.nativ.api.ArchiveOverview
import de.bgghome.webtrees.nativ.api.CollectionPage
import de.bgghome.webtrees.nativ.ui.viewerItem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Antworten des Sammlungen-Moduls (Stufe 1), aufgezeichnet an der lokalen Testinstallation, muessen durch die
 * Modelle laufen - mit derselben Json-Einstellung wie in WtClient. Aendert das Modul ein Feld, faellt es hier auf,
 * nicht erst am Handy.
 */
class ArchiveModelsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private fun resource(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/archive/$name")) { "fehlt: $name" }.bufferedReader().readText()

    @Test
    fun uebersichtLaesstSichLesen() {
        val overview = json.decodeFromString(ArchiveOverview.serializer(), resource("uebersicht.json"))

        assertEquals(1, overview.api)
        assertEquals("falkenrath", overview.baum)
        assertEquals(listOf("ordner", "thematisch", "medientyp", "medientyp"), overview.sammlungen.map { it.art })

        val ordner = overview.sammlungen.first()
        assertEquals("archiv-test", ordner.slug)
        assertEquals("#8a5a2b", ordner.farbe)
        assertEquals(3, ordner.anzahl)

        val thematisch = overview.sammlungen[1]
        assertEquals(2, thematisch.vorschau.size)
        assertNull(thematisch.ordner)

        assertEquals(listOf("PHOTO"), overview.unverknuepft.map { it.typ })
        assertEquals(7, overview.frei.gesamt)
        assertEquals(listOf("", "Archiv-Test"), overview.frei.jeOrdner.map { it.ordner })
    }

    @Test
    fun ordnerSammlungMitDateienOhneMedienobjekt() {
        val page = json.decodeFromString(CollectionPage.serializer(), resource("ordner.json"))

        assertEquals("ordner", page.art)
        assertEquals(1, page.seiten)
        assertEquals(3, page.eintraege.size)
        assertTrue(page.eintraege.all { it.istBild && it.xref == null && it.kachel != null && it.vollbild != null })
        assertEquals(listOf("hochzeit-1928"), page.eintraege[1].inSammlungen)

        val item = viewerItem(page.eintraege[1])
        assertEquals("hochzeit-1928.jpg", item.caption)
        assertTrue(item.image.contains("w=1600"))
        assertTrue(item.webUrl!!.contains("media-datei"))
    }

    @Test
    fun medientypSammlungAusMedienobjekten() {
        val page = json.decodeFromString(CollectionPage.serializer(), resource("medientyp.json"))

        assertEquals("medientyp", page.art)
        assertEquals(10, page.proSeite)
        assertEquals(3, page.seiten)
        assertEquals(10, page.eintraege.size)

        val first = page.eintraege.first()
        assertNotNull(first.xref)
        assertNotNull(first.seite)
        assertTrue(first.kachel!!.contains("media-thumbnail"))

        val mitPerson = page.eintraege.first { it.personen.isNotEmpty() }
        val item = viewerItem(mitPerson)
        assertEquals(mitPerson.personen.joinToString(", ") { it.name }, item.subtitle)
        assertEquals(mitPerson.seite, item.webUrl)
    }
}
