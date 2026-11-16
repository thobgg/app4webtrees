package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.api.DateJson
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.FactRequest
import de.bgghome.webtrees.nativ.api.FamilyJson
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.PlaceJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Oberster Grundsatz des Formulars: nur Geaendertes geht an den Server, nichts wird still verworfen. */
class EinfachFormularTest {
    private val geburt = FactJson("f1", "BIRT", "Geburt", date = DateJson("14. März 1985", 1985, 2446139, "14 MAR 1985"), place = PlaceJson("Hannover"))
    private val beruf = FactJson("f2", "OCCU", "Beruf", value = "Bauingenieur")
    private val beruf2 = FactJson("f3", "OCCU", "Beruf", value = "Dozent")
    private val name = FactJson("f0", "NAME", "Name", value = "Jonas /Falkenrath/ jun.")
    private val heirat = FactJson("m1", "MARR", "Heirat", date = DateJson("18. Juni 2015", 2015, 0, "18 JUN 2015"))
    private fun detail() = IndividualDetail(
        Person("I1", "Jonas Falkenrath"), facts = listOf(name, geburt, beruf, beruf2),
        spouseFamilies = listOf(FamilyJson("F1", spouse = Person("I2", "Mira Sandvoss"), facts = listOf(heirat))),
    )
    private fun formular() = EinfachFormular("I1").also { it.abgleichen(detail()) }
    private fun EinfachFormular.feld(key: String) = felder.first { it.key == key }

    @Test
    fun unberuehrtSendetNichts() {
        val f = formular()
        assertFalse(f.geaendert)
        assertTrue(f.felder.all { it.anfrage() == null })
        assertEquals(1, f.feld("OCCU").weitere)
    }

    @Test
    fun nurDasGeaenderteTeil() {
        val f = formular()
        f.feld("BIRT").ort = "Celle"
        assertEquals(FactRequest(factId = "f1", date = null, place = "Celle"), f.feld("BIRT").anfrage())
        f.feld("BIRT").datum = "15.4.1929"
        assertEquals(FactRequest(factId = "f1", date = "15 APR 1929", place = "Celle"), f.feld("BIRT").anfrage())
    }

    @Test
    fun nameBehaeltZusatz() {
        val f = formular()
        f.feld("NAME").vornamen = "Jonas Heinrich"
        assertEquals(FactRequest(factId = "f0", value = "Jonas Heinrich /Falkenrath/ jun."), f.feld("NAME").anfrage())
    }

    @Test
    fun neuesEreignisUndHeiratAnDerFamilie() {
        val f = formular()
        f.feld("DEAT").datum = "um 2080"
        assertEquals(FactRequest(tag = "DEAT", date = "ABT 2080"), f.feld("DEAT").anfrage())
        assertNull(f.feld("BURI").anfrage())
        val marr = f.feld("MARR:F1")
        assertEquals("F1", marr.record)
        marr.ort = "Hannover"
        assertEquals(FactRequest(factId = "m1", date = null, place = "Hannover"), marr.anfrage())
    }

    @Test
    fun ungueltigesDatumBlockiertBisBestaetigt() {
        val f = formular()
        f.feld("DEAT").datum = "Frühjahr 1850"
        assertFalse(f.speicherbar) // fordert zur Korrektur auf
        f.feld("DEAT").datumAlsText = true
        assertTrue(f.speicherbar) // nur auf Wunsch als Datumstext
        assertEquals(FactRequest(tag = "DEAT", date = "(Frühjahr 1850)"), f.feld("DEAT").anfrage())
        f.feld("DEAT").datum = "12.3.1850" // Korrektur hebt die Ausnahme auf
        f.feld("DEAT").datumAlsText = false
        assertTrue(f.speicherbar)
    }

    @Test
    fun unbekanntesDatumWirdDatumstext() {
        assertEquals("(Frühjahr 1850)", datumGedcom("Frühjahr 1850"))
        assertEquals("BET 1850 AND 1860", datumGedcom("zwischen 1850 und 1860"))
        assertEquals("", datumGedcom("  "))
    }

    @Test
    fun vorbelegungVerliertNichts() {
        // Anzeige, die genau zurueckgerechnet wird, bleibt; sonst steht das GEDCOM-Datum im Feld
        assertEquals("um 1850", datumVorbelegen(DateJson(text = "um 1850", gedcom = "ABT 1850")))
        assertEquals("geschätzt 1918", datumVorbelegen(DateJson(text = "geschätzt 1918", gedcom = "EST 1918")))
        assertEquals("von 1915 bis 1918", datumVorbelegen(DateJson(text = "von 1915 bis 1918", gedcom = "FROM 1915 TO 1918")))
        assertEquals("INT 1865 (Ostern)", datumVorbelegen(DateJson(text = "interpretiert 1865 (Ostern)", gedcom = "INT 1865 (Ostern)")))
        assertEquals("@#DJULIAN@ 1 JAN 1690", datumVorbelegen(DateJson(text = "1. Januar 1690", gedcom = "@#DJULIAN@ 1 JAN 1690")))
        assertEquals("12. März 1890", datumVorbelegen(DateJson(text = "12. März 1890")))            // Server ohne gedcom
        assertEquals("", datumVorbelegen(null))
    }

    @Test
    fun eingabenUeberlebenNeuladen() {
        val f = formular()
        f.feld("OCCU").wert = "Architekt"
        f.feld("BIRT").gespeichert()
        f.abgleichen(detail()) // Server liefert den alten Stand erneut (z. B. nach Teilfehler)
        assertEquals("Architekt", f.feld("OCCU").wert)
        assertTrue(f.geaendert)
        f.feld("OCCU").gespeichert()
        assertFalse(f.geaendert)
    }
}
