package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.api.DateJson
import de.bgghome.webtrees.nativ.api.ExportFamily
import de.bgghome.webtrees.nativ.api.ExportIndividual
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.LocationEvent
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.PlaceJson
import de.bgghome.webtrees.nativ.api.TreeExport
import de.bgghome.webtrees.nativ.data.GovTypen
import de.bgghome.webtrees.nativ.data.Haustypen
import de.bgghome.webtrees.nativ.data.OrtsKlasse
import de.bgghome.webtrees.nativ.data.Ortsnamen
import de.bgghome.webtrees.nativ.data.berufe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

/**
 * Haeuserteil des Ortsfamilienbuchs: nur Gebaeude nach Ortsart, Stadtteile als Kapitel oder im Anhang, Namen mit Komma
 * oder Semikolon, natuerliche Sortierung, Wohnort und Besitz zusammengezogen, Berufe einzeln im Verzeichnis.
 */
class HaeuserbuchTest {
    // Die erwarteten Saetze sind deutsch - unabhaengig von der Sprache des Rechners (der Windows-Rechner von GitHub ist englisch)
    private val ursprung = java.util.Locale.getDefault()
    @BeforeTest fun deutsch() = java.util.Locale.setDefault(java.util.Locale.GERMANY)
    @AfterTest fun zurueck() = java.util.Locale.setDefault(ursprung)

    private fun datum(jahr: Int, text: String = "$jahr") = DateJson(text = text, year = jahr, jd = 2300000 + jahr * 365, gedcom = text.uppercase().replace("VON ", "FROM ").replace(" BIS ", " TO "))
    private fun fakt(tag: String, label: String, ort: String?, d: DateJson? = null, wert: String = "") =
        FactJson(id = "$tag-$ort-${d?.year}", tag = tag, label = label, value = wert, date = d, place = ort?.let { PlaceJson(name = it) })
    private fun person(x: String, vor: String, nach: String, fakten: List<FactJson>, fams: List<String> = emptyList(), famc: List<String> = emptyList()) =
        ExportIndividual(Person(xref = x, name = "$vor $nach", given = vor, surname = nach, sex = "M", isDead = true), famc = famc, fams = fams, facts = fakten)

    private val stadtteil = "Ennetach; Mengen"
    private val h1 = "Klosterstraße 1 (früher Haus Nr. 77); Ennetach; Mengen"
    private val h3 = "Klosterstraße 3; Ennetach; Mengen"
    private val h12 = "Klosterstraße 12; Ennetach; Mengen"
    private val h7 = "Haus Nr. 7; Ennetach; Mengen"
    private val rulf = "Dorfstraße 2, Rulfingen, Mengen"

    private val baum = TreeExport(0, mapOf(
        "I1" to person("I1", "Wunibald", "Löw", listOf(
            fakt("OCCU", "Beruf", null, wert = "Maurer (1882), später Polizeidiener (1914)"),
            fakt("RESI", "Wohnort", h1, datum(1767)), fakt("PROP", "Besitz", h1, datum(1767), "Haus und Garten"),
            fakt("RESI", "Wohnort", h1, datum(1790)), fakt("DEAT", "Tod", stadtteil, datum(1801)),
        ), fams = listOf("F1")),
        "I2" to person("I2", "Kunigunde", "Waldraff", listOf(fakt("RESI", "Wohnort", h1, datum(1767))), fams = listOf("F1")),
        "I3" to person("I3", "Alois", "Erne", listOf(fakt("RESI", "Wohnort", h12, datum(1850)), fakt("PROP", "Besitz", h12, datum(1850))), fams = listOf("F2")),
        "I4" to person("I4", "Karl", "Sick", listOf(fakt("RESI", "Wohnort", h3, datum(1882))), fams = listOf("F3")),
        "I5" to person("I5", "Josef", "Bolter", listOf(fakt("RESI", "Wohnort", stadtteil, datum(1409)))),
        "I6" to person("I6", "Anton", "Rapp", listOf(fakt("RESI", "Wohnort", h7, datum(1833)))),
        "I7" to person("I7", "Johann", "Briemle", listOf(fakt("RESI", "Wohnort", rulf, datum(1871)))),
    ), mapOf(
        "F1" to ExportFamily("F1", husband = "I1", wife = "I2"),
        "F2" to ExportFamily("F2", husband = "I3"),
        "F3" to ExportFamily("F3", husband = "I4"),
    ))

    private val haeuser = listOf(
        Haus(stadtteil, "Stadtteil", klasse = OrtsKlasse.OBER),
        Haus(h12, "Hofstelle", klasse = OrtsKlasse.HAUS),
        Haus(h3, "farm", klasse = OrtsKlasse.HAUS),
        Haus(h1, "", listOf(LocationEvent(type = "Brand", date = datum(1583))), klasse = OrtsKlasse.UNBEKANNT),
        Haus(h7, "", klasse = Haustypen.klasse("", "Haus Nr. 7", false)),
        Haus(rulf, "Hof", klasse = OrtsKlasse.HAUS),
    )

    private fun bloecke(o: BuchOptionen) = familienbuch(FamilienDaten(baum, emptyMap(), haeuser), o, "Test", "wtTux").bloecke

    @Test
    fun nurHaeuserMitKapitelnUndAnhang() {
        val b = bloecke(BuchOptionen(ortFilter = "Mengen", bilder = false))
        val marken = b.filterIsInstance<Absatz>().mapNotNull { it.marke }.filter { it.startsWith("H") || it.startsWith("W") }
        // Haeuser nach Stadtteil (Ennetach vor Rulfingen) und natuerlich sortiert: 1, 3, 7, 12; der Stadtteil nicht als Haus
        assertEquals(listOf("H1", "H2", "H3", "H4", "H5", "W1"), marken)
        val namen = b.filterIsInstance<Absatz>().filter { it.marke?.startsWith("H") == true }.map { it.laeufe[0].text }
        assertEquals(listOf("Haus Nr. 7", "Klosterstraße 1 (früher Haus Nr. 77)", "Klosterstraße 3", "Klosterstraße 12", "Dorfstraße 2"), namen)
        // Kapitel je Stadtteil, der Stadtteil selbst mit seinem Bewohner im Anhang
        val ids = b.filterIsInstance<Ueberschrift>().map { it.id }
        assertEquals(listOf("haeuser", "haeuser-ennetach", "haeuser-rulfingen", "weitere"), ids.filter { it.startsWith("haeuser") || it == "weitere" })
        val anhang = b.filterIsInstance<Absatz>().first { it.marke == "W1" }
        assertEquals("Ennetach", anhang.laeufe[0].text)
        // Rueckverweis bei der Familie Loew auf Klosterstrasse 1 (H2), Bewohner mit Familiennummer
        val verweis = b.filterIsInstance<Absatz>().first { a -> a.laeufe.any { it.ziel == "h2" } }
        assertEquals("Klosterstraße 1 (früher Haus Nr. 77) (H2)", verweis.laeufe.first { it.ziel == "h2" }.text)
    }

    @Test
    fun ohneArtWahlweiseImAnhang() {
        val b = bloecke(BuchOptionen(ortFilter = "Ennetach", bilder = false, haeuserOhneTyp = false))
        val marken = b.filterIsInstance<Absatz>().mapNotNull { it.marke }.filter { it.startsWith("H") || it.startsWith("W") }
        // Klosterstrasse 1 (ohne Art, ohne Hausnummer am Ende) faellt in den Anhang; Haus Nr. 7 bleibt (Hausnummer).
        // Der Stadtteil Ennetach selbst ist hier der gefilterte Ort und kein "weiterer Ort" - seine Leute stehen im Familienteil.
        assertEquals(listOf("H1", "H2", "H3", "W1"), marken)
        assertEquals("Klosterstraße 1 (früher Haus Nr. 77)", b.filterIsInstance<Absatz>().first { it.marke == "W1" }.laeufe[0].text)
        val alles = bloecke(BuchOptionen(ortFilter = "Ennetach", bilder = false, haeuserNurTyp = false))
        assertEquals(4, alles.filterIsInstance<Absatz>().count { it.marke?.startsWith("H") == true }, "ohne Artfilter alle Orte unter Ennetach")
    }

    @Test
    fun wohnortUndBesitzZusammengezogen() {
        val b = bloecke(BuchOptionen(ortFilter = "Ennetach", bilder = false))
        val loew = b.filterIsInstance<Absatz>().map { a -> a.laeufe.joinToString("") { it.text } }.first { it.startsWith("Löw, Wunibald") }
        assert("Wohnort und Besitz: von 1767 bis 1790 Klosterstraße 1 (früher Haus Nr. 77) – Haus und Garten" in loew) { loew }
        assert(loew.count { it == '†' } == 1 && "Wohnort: 1790" !in loew) { loew }
        // Familie ohne Heirat steht nach dem fruehesten Ereignis der Eheleute im Jahrhundert 1700, nicht unter "?"
        val gruppen = b.filterIsInstance<Ueberschrift>().map { it.text }
        assert("?" !in gruppen) { gruppen.toString() }
    }

    @Test
    fun artenUndNamen() {
        assertEquals(OrtsKlasse.HAUS, Haustypen.klasse("Hofstelle", "Klosterstraße 1", false))
        assertEquals(OrtsKlasse.HAUS, Haustypen.klasse("Hof, Mühle", "x", false))
        assertEquals(OrtsKlasse.OBER, Haustypen.klasse("Stadtteil", "Ennetach", false))
        assertEquals(OrtsKlasse.OBER, Haustypen.klasse(null, "Ennetach", true))
        assertEquals(OrtsKlasse.HAUS, Haustypen.klasse(null, "Klosterstraße 6 (früher Haus Nr. 61)", false))
        assertEquals(OrtsKlasse.HAUS, Haustypen.klasse("", "Haus Nr. 12a", false))
        assertEquals(OrtsKlasse.UNBEKANNT, Haustypen.klasse("", "Oberdorf", false))
        // GOV-Typnummer (2 _GOVTYPE) geht vor dem Text: 24 Hof, 55 Dorf, 54 Stadtteil; 229 Haeusergruppe entscheidet nichts
        assertEquals(OrtsKlasse.HAUS, Haustypen.klasse("farm", "24", "Oberdorf", false))
        assertEquals(OrtsKlasse.OBER, Haustypen.klasse("Hof", "55", "Hof Nr. 3", false))
        assertEquals(OrtsKlasse.OBER, Haustypen.klasse(null, "54", "Klosterstraße 6", false))
        assertEquals(OrtsKlasse.HAUS, Haustypen.klasse("Häusergruppe", "229", "x", false))
        assertEquals(OrtsKlasse.UNBEKANNT, Haustypen.klasse("", "229", "Oberdorf", false))
        assertEquals(OrtsKlasse.HAUS, Haustypen.klasse("", "unsinn", "Haus Nr. 12a", false))
        assertEquals("Hof" to "Farm", GovTypen.NAMEN.getValue(24))
        assertEquals("Mühle", GovTypen.name("87", "de")); assertEquals("mill", GovTypen.name("87", "en")); assertNull(GovTypen.name("9999", "de"))
        assertTrue(GovTypen.HAUS.none { it in GovTypen.OBER }); assertEquals(276, GovTypen.NAMEN.size)
        assertEquals(listOf("Klosterstraße 6", "Ennetach", "Mengen"), Ortsnamen.teile("Klosterstraße 6; Ennetach; Mengen"))
        assertEquals("Hof Nr. 3", Ortsnamen.blatt("Hof Nr. 3, Bienenbüttel, Uelzen"))
        assertEquals("Bienenbüttel, Uelzen", Ortsnamen.oberort("Hof Nr. 3, Bienenbüttel, Uelzen"))
        assertEquals(listOf("Haus Nr. 2", "Haus Nr. 12", "Hof Nr. 3", "Hof Nr. 12"), listOf("Hof Nr. 12", "Haus Nr. 12", "Hof Nr. 3", "Haus Nr. 2").sortedWith(Ortsnamen.NATUERLICH))
        assertEquals(listOf("Maurer", "Polizeidiener"), berufe("Maurer (1882), später Polizeidiener (1914)"))
        assertEquals(listOf("Schmied"), berufe("Schmied"))
        assertEquals(listOf("Weber", "Bauer"), berufe("Weber und Bauer"))
    }
}
