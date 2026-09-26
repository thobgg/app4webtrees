package de.bgghome.webtrees.nativ

import de.bgghome.webtrees.nativ.api.ExportCache
import de.bgghome.webtrees.nativ.api.ExportFamily
import de.bgghome.webtrees.nativ.api.ExportIndividual
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.TreeExport
import de.bgghome.webtrees.nativ.api.halfSiblings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

/** Der ganze Baum aus der Route Export (Stufe 17): Einzelansicht zusammensetzen und zwischenspeichern. */
class ExportTest {
    private fun ind(x: String, famc: List<String> = emptyList(), fams: List<String> = emptyList(), private: Boolean = false) =
        x to ExportIndividual(Person(x, isPrivate = private), famc, fams)

    // Kind K aus F1 (V + M); V hat mit X eine zweite Familie F2 mit dem Halbbruder H; M ist ein Platzhalter.
    private val baum = TreeExport(
        42,
        mapOf(ind("K", famc = listOf("F1")), ind("V", fams = listOf("F1", "F2")), ind("M", fams = listOf("F1"), private = true),
            ind("X", fams = listOf("F2")), ind("H", famc = listOf("F2"))),
        mapOf("F1" to ExportFamily("F1", husband = "V", wife = "M", children = listOf("K")),
            "F2" to ExportFamily("F2", husband = "V", wife = "X", children = listOf("H"))),
    )

    @Test
    fun detailLikeIndividualRoute() {
        val k = baum.detail("K")!!
        assertEquals(listOf("V" to "M"), k.parentFamilies.map { it.husband?.xref to it.wife?.xref })
        assertEquals(true, k.parentFamilies.single().wife?.isPrivate)
        assertEquals(listOf("F2" to "X"), k.stepFamilies.map { it.xref to it.spouse?.xref })
        assertEquals(listOf("H" to true), k.halfSiblings().map { it.person.xref to it.paternal })
        assertEquals(listOf("X", "M").toSet(), baum.detail("V")!!.spouseFamilies.map { it.spouse?.xref }.toSet())
        assertNull(baum.detail("gibtsnicht"))
    }

    @Test
    fun cacheOnlyForSameLastChange() {
        val ordner = Files.createTempDirectory("export").toFile()
        val key = ExportCache(ordner).schluessel("https://example.org", "baum", "anna", "editor")
        ExportCache(ordner).sichern(key, baum)
        // neue Instanz: liest die Datei, nicht den Speicher
        val neu = ExportCache(ordner)
        assertNull(neu.laden(key, 43))
        assertEquals(baum.individuals, neu.laden(key, 42)!!.individuals)
        assertNull(ExportCache(ordner).laden(ExportCache(ordner).schluessel("https://example.org", "baum", "anna", "visitor"), 42))
        ordner.deleteRecursively()
    }
}
