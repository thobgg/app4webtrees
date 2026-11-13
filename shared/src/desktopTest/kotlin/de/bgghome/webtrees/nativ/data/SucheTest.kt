package de.bgghome.webtrees.nativ.data

import de.bgghome.webtrees.nativ.gedcom.GedcomLeser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SucheTest {
    private val baum by lazy {
        val f = File("../testdaten/falkenrath-1.3.ged").takeIf { it.isFile } ?: File("testdaten/falkenrath-1.3.ged")
        GedcomLeser.alsExport(GedcomLeser.lesen(f), jetzt = 2026)
    }

    @Test fun koelnerPhonetik() {
        assertEquals("67", Suche.koelner("Meyer")); assertEquals("67", Suche.koelner("Maier")); assertEquals("67", Suche.koelner("Mayer"))
        assertEquals("657", Suche.koelner("Müller")); assertEquals("657", Suche.koelner("Mueller"))
        assertEquals("8", Suche.koelner("Zeh")); assertEquals("", Suche.koelner("123"))
        assertEquals(Suche.koelner("Mohwinkel"), Suche.koelner("Mohwinckel"))
    }

    @Test fun ortUndBeruf() {
        val orte = Suche.suchen(baum, SuchAnfrage("Bienenbüttel", felder = setOf(SuchFeld.Orte)))
        assertTrue(orte.size > 10, "Bienenbüttel: ${orte.size}")
        assertTrue(orte.all { t -> t.fundstellen.all { it.feld == SuchFeld.Orte && "Bienenbüttel" in it.text } })
        val landwirte = Suche.suchen(baum, SuchAnfrage("landwirt", felder = setOf(SuchFeld.Beruf)))
        assertTrue(landwirte.any { it.person.xref == "I130" }, landwirte.map { it.person.xref }.toString())
        // Gross/Klein: "landwirt" klein passt dann nicht mehr auf "Landwirt"
        assertTrue(Suche.suchen(baum, SuchAnfrage("landwirt", felder = setOf(SuchFeld.Beruf), grossKlein = true)).none { it.person.xref == "I130" })
    }

    @Test fun woerterUndPhonetik() {
        val und = Suche.suchen(baum, SuchAnfrage("Mohwinkel Bienenbüttel", SuchArt.AlleWoerter))
        assertTrue(und.any { it.person.xref == "I130" })
        val oder = Suche.suchen(baum, SuchAnfrage("Mohwinkel Hinrichs", SuchArt.EinWort, felder = setOf(SuchFeld.Namen)))
        assertTrue(oder.size > und.size)
        val phon = Suche.suchen(baum, SuchAnfrage("Mohwinckel", felder = setOf(SuchFeld.Namen), phonetisch = true))
        assertTrue(phon.any { it.person.xref == "I130" }, "phonetisch: ${phon.size}")
        assertTrue(Suche.suchen(baum, SuchAnfrage("Mohwinckel", felder = setOf(SuchFeld.Namen))).isEmpty())
        // Geschlossene Gruppe: nur in den vorigen Treffern
        val eng = Suche.suchen(baum, SuchAnfrage("Georg", felder = setOf(SuchFeld.Namen)), nurIn = phon.map { it.person.xref }.toSet())
        assertTrue(eng.isNotEmpty() && eng.all { p -> phon.any { it.person.xref == p.person.xref } })
    }

    @Test fun verwandteUndGanzesFeld() {
        val kinder = Suche.suchen(baum, SuchAnfrage("Richard Mohwinkel", felder = setOf(SuchFeld.Kinder)))
        assertTrue(kinder.any { it.person.xref == "I130" }, kinder.map { it.person.name }.toString())
        val ganz = Suche.suchen(baum, SuchAnfrage("Landwirt", felder = setOf(SuchFeld.Beruf), ganzesFeld = true))
        assertTrue(ganz.isNotEmpty() && ganz.all { t -> t.fundstellen.all { it.text == "Landwirt" } })
    }

    @Test fun kennzeichen() {
        val k = Suche.kennzeichen(baum, "130", KennArt.Kennung, KennLage.Komplett)
        assertEquals(listOf("I130"), k.map { it.person.xref })
        assertTrue(Suche.kennzeichen(baum, "I13", KennArt.Kennung, KennLage.Anfang).size > 1)
        assertTrue(Suche.kennzeichen(baum, "0", KennArt.Kennung, KennLage.Ende).all { it.person.xref.endsWith("0") })
    }

    @Test fun ersetzenVorschau() {
        val v = Suche.ersetzenVorschau(baum, "Bienenbüttel", "Bienenbuettel", setOf(ErsetzFeld.Orte), ganzesFeld = false, grossKlein = false)
        assertTrue(v.size > 10, "Vorschau: ${v.size}")
        assertTrue(v.all { it.feld == ErsetzFeld.Orte && "Bienenbuettel" in it.neu && it.anfrage().place == it.neu && it.anfrage().factId == it.fact.id })
        // Familienereignisse haengen an der Familie, nicht an der Person
        assertTrue(v.any { it.record.startsWith("F") }, "keine Heirat dabei")
        assertTrue(Suche.ersetzenVorschau(baum, "Mohwinkel", "X", ErsetzFeld.entries.toSet(), false, false).none { it.fact.tag == "NAME" })
        val liste = Suche.listeLesen(Suche.listeText(v.map { SuchTreffer(it.person, emptyList()) }.distinctBy { it.person.xref }), baum)
        assertEquals(v.map { it.person.xref }.distinct().toSet(), liste.map { it.person.xref }.toSet())
    }
}
