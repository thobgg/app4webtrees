package de.bgghome.webtrees.nativ.pruefung

import de.bgghome.webtrees.nativ.api.DateJson
import de.bgghome.webtrees.nativ.api.ExportFamily
import de.bgghome.webtrees.nativ.api.ExportIndividual
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.PlaceJson
import de.bgghome.webtrees.nativ.api.SourceRef
import de.bgghome.webtrees.nativ.api.TreeExport
import de.bgghome.webtrees.nativ.desk.abhakSchluesselDatei
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plausibilitaetspruefung gegen den Demo-Baum Falkenrath: dieselben Treffer wie das Pruefprogramm aus db-blank
 * (`python3 plausibilitaet.py testdaten/falkenrath-1.3.ged`, Stand 27.09.2026). Der Baum wird hier aus dem GEDCOM in
 * die Form des Server-Exports gebracht (Fakten mit GEDCOM-Datum), so wie ihn wtWin von api4webtrees bekommt.
 */
class PruefungTest {

    private fun gedcomAlsExport(datei: File): TreeExport {
        val saetze = mutableListOf<Triple<String, String, MutableList<Triple<Int, String, String>>>>()
        datei.forEachLine { roh ->
            val z = roh.trimEnd('\r'); if (z.isEmpty()) return@forEachLine
            val t = z.split(' ', limit = 3)
            val lvl = t[0].toIntOrNull() ?: return@forEachLine
            if (lvl == 0) { if (t.size >= 3 && t[1].startsWith("@")) saetze += Triple(t[2].trim(), t[1].trim('@'), mutableListOf()) else saetze += Triple("", "", mutableListOf()); return@forEachLine }
            saetze.lastOrNull()?.third?.add(Triple(lvl, t.getOrElse(1) { "" }, t.getOrElse(2) { "" }))
        }
        fun fakten(zeilen: List<Triple<Int, String, String>>, tags: Set<String>): List<FactJson> {
            val out = mutableListOf<FactJson>()
            var offen: String? = null; var datum: String? = null; var ort: String? = null
            val notizen = mutableListOf<String>(); val quellen = mutableListOf<SourceRef>()
            fun schliessen() { offen?.let { out += FactJson(id = "f${out.size}", tag = it, label = it, date = datum?.let { d -> DateJson(text = d, gedcom = d) },
                place = ort?.let { o -> PlaceJson(name = o, short = o.substringBefore(',')) }, notes = notizen.toList(), sources = quellen.toList()) } }
            for ((lvl, tag, wert) in zeilen) {
                if (lvl == 1) { schliessen(); offen = tag.takeIf { it in tags }; datum = null; ort = null; notizen.clear(); quellen.clear() }
                else if (lvl == 2 && tag == "DATE" && offen != null && datum == null) datum = wert
                else if (lvl == 2 && tag == "PLAC" && offen != null) ort = wert
                else if (lvl == 2 && tag == "NOTE" && offen != null) notizen += wert
                else if (lvl == 2 && tag == "SOUR" && offen != null) quellen += SourceRef(xref = wert.trim('@'))
            }
            schliessen()
            return out
        }
        val ereignisse = setOf("NAME", "BIRT", "CHR", "BAPM", "DEAT", "BURI", "CREM", "OCCU", "RELI", "NOTE")
        val personen = LinkedHashMap<String, ExportIndividual>()
        val familien = LinkedHashMap<String, ExportFamily>()
        for ((typ, id, zeilen) in saetze) when (typ) {
            "INDI" -> {
                val name = zeilen.firstOrNull { it.first == 1 && it.second == "NAME" }?.third.orEmpty()
                val m = Regex("^(.*?)\\s*/(.*?)/\\s*(.*)$").find(name)
                val (vn, fn) = if (m != null) m.groupValues[1].trim() to m.groupValues[2].trim() else name.trim() to ""
                val sex = zeilen.firstOrNull { it.first == 1 && it.second == "SEX" }?.third?.trim() ?: "U"
                personen[id] = ExportIndividual(
                    person = Person(xref = id, name = "$vn $fn".trim(), given = vn, surname = fn, sex = sex),
                    famc = zeilen.filter { it.first == 1 && it.second == "FAMC" }.map { it.third.trim('@') },
                    fams = zeilen.filter { it.first == 1 && it.second == "FAMS" }.map { it.third.trim('@') },
                    facts = fakten(zeilen, ereignisse),
                )
            }
            "FAM" -> familien[id] = ExportFamily(
                xref = id,
                husband = zeilen.firstOrNull { it.first == 1 && it.second == "HUSB" }?.third?.trim('@'),
                wife = zeilen.firstOrNull { it.first == 1 && it.second == "WIFE" }?.third?.trim('@'),
                children = zeilen.filter { it.first == 1 && it.second == "CHIL" }.map { it.third.trim('@') },
                facts = fakten(zeilen, setOf("MARR")),
            )
        }
        return TreeExport(1, personen, familien)
    }

    private val falkenrath = File("../testdaten/falkenrath-1.3.ged")

    /** Die Regeln aus Stufe 2 (nicht im Python-Programm). */
    private val stufe2 = setOf("024", "126", "127", "228", "310", "311", "315", "330", "420", "421", "510", "511", "512", "513") +
        Regelkatalog.alle.map { it.id }.filter { it.startsWith("6") }.toSet()   // Form (gedaudit) ebenfalls nicht im Python-Programm

    @Test
    fun falkenrathWieDasPythonProgramm() {
        val e = pruefen(gedcomAlsExport(falkenrath), aus = stufe2, jetzt = 2026)
        val zahlen = e.treffer.filterValues { it.isNotEmpty() }.mapValues { it.value.size }
        assertEquals(mapOf("010" to 7, "016" to 1, "124" to 3, "223" to 2, "224" to 8), zahlen, e.treffer.filterValues { it.isNotEmpty() }.toString())
        assertEquals(listOf("I307", "I319", "I321", "I340", "I342", "I370", "I376"), e.treffer.getValue("010").map { it.person }.sortedBy { it!!.drop(1).toInt() })
        assertEquals("I329", e.treffer.getValue("016").single().person)
        assertEquals("F75", e.treffer.getValue("016").single().familie)
        assertEquals(77, Regelkatalog.alle.size)
    }

    @Test
    fun grenzwerteUndAbschalten() {
        val b = gedcomAlsExport(falkenrath)
        // Geschwisterluecke ab 10 statt 12 Jahren: mehr Treffer; Regel 224 abgeschaltet
        val e = pruefen(b, grenzwerte = mapOf("124" to 10.0), aus = stufe2 + "224", jetzt = 2026)
        assertTrue(e.treffer.getValue("124").size > 3)
        assertNull(e.treffer["224"])
    }

    @Test
    fun datumsformen() {
        val genau = assertNotNull(PruefDatum.aus("21 JUN 1844"))
        assertTrue(genau.voll); assertEquals(genau.min, genau.max)
        val jahr = assertNotNull(PruefDatum.aus("1844"))
        assertEquals(1844, jahr.jahr)
        assertEquals(365L, jahr.max - jahr.min) // 1844 ist ein Schaltjahr: 366 Tage
        assertEquals(1850, PruefDatum.aus("ABT 1850")?.jahr)
        val bet = assertNotNull(PruefDatum.aus("BET 1840 AND 1845"))
        assertNull(bet.jahr)
        assertEquals(true, vor(PruefDatum.aus("BEF 1850"), PruefDatum.aus("1850")))
        assertEquals(false, vor(PruefDatum.aus("AFT 1850"), PruefDatum.aus("1851")))
        assertEquals(true, vor(PruefDatum.aus("MAR 1844"), PruefDatum.aus("JUN 1844")))
        assertEquals(false, vor(PruefDatum.aus("MAR 1844"), PruefDatum.aus("1844")))
        assertEquals(1721, PruefDatum.aus("10 FEB 1720/21")?.jahr)
        // Umstellung 1700: auf den julianischen 18.2. folgte der gregorianische 1.3. - der 19.2. julianisch ist der 1.3.
        assertEquals(PruefDatum.aus("1 MAR 1700")!!.min, PruefDatum.aus("@#DJULIAN@ 19 FEB 1700")!!.min)
        assertNull(PruefDatum.aus("@#DFRENCH R@ 1 VEND 10"))
        assertNull(PruefDatum.aus("(unbekannt)"))
        assertTrue(PruefDatum.aus("31 APR 1850")!!.unmoeglich)
        assertTrue(PruefDatum.aus("29 FEB 1900")!!.unmoeglich)
        assertTrue(!PruefDatum.aus("29 FEB 2000")!!.unmoeglich)
    }

    @Test
    fun kreisInDerAbstammung() {
        fun p(x: String, famc: List<String>, fams: List<String>) = ExportIndividual(Person(xref = x, name = x, given = x, surname = "T", sex = "M"), famc, fams)
        // A ist Kind von B, B ist Kind von A (F1: B Vater von A, F2: A Vater von B); C haengt unbeteiligt daran
        val b = TreeExport(1,
            mapOf("A" to p("A", listOf("F1"), listOf("F2")), "B" to p("B", listOf("F2"), listOf("F1")), "C" to p("C", listOf("F2"), emptyList())),
            mapOf("F1" to ExportFamily("F1", husband = "B", children = listOf("A")), "F2" to ExportFamily("F2", husband = "A", children = listOf("B", "C"))))
        assertEquals(setOf("A", "B"), pruefen(b, jetzt = 2026).treffer.getValue("225").map { it.person }.toSet())
    }
}

class PruefungStufe2Test {
    private fun p(x: String, vn: String, fn: String, sex: String, famc: List<String> = emptyList(), fams: List<String> = emptyList(), vararg facts: FactJson, dead: Boolean = true) =
        ExportIndividual(Person(xref = x, name = "$vn $fn", given = vn, surname = fn, sex = sex, isDead = dead), famc, fams, facts.toList())
    private fun f(id: String, tag: String, datum: String? = null, ort: String? = null, lat: Double? = null, lng: Double? = null, notiz: String? = null) =
        FactJson(id = id, tag = tag, label = tag, date = datum?.let { DateJson(text = it, gedcom = it) },
            place = ort?.let { PlaceJson(name = it, short = it.substringBefore(','), lat = lat, lng = lng) }, notes = listOfNotNull(notiz))

    @Test
    fun schaetzenAusTaufe() {
        // Nur Taufe, keine Geburt: mit Schaetzen findet Regel 010 den Tod vor der Taufe, ohne nicht
        val b = TreeExport(1, mapOf("I1" to p("I1", "Anna", "Test", "F", emptyList(), emptyList(), f("c", "CHR", "5 MAY 1850"), f("d", "DEAT", "1 MAY 1850"))), emptyMap())
        val mit = pruefen(b, PruefOptionen(aus = emptySet(), schaetzen = true, jetzt = 2026)).treffer.getValue("010")
        assertEquals(1, mit.size)
        assertTrue(mit.single().text.contains("~ 5 MAY 1850"))
        assertEquals(listOf(FaktRef("I1", "d"), FaktRef("I1", "c")), mit.single().fakten)
        assertTrue(pruefen(b, PruefOptionen(aus = emptySet(), schaetzen = false, jetzt = 2026)).treffer.getValue("010").isEmpty())
    }

    @Test
    fun patenAusDerNotiz() {
        val b = TreeExport(1, mapOf(
            "I1" to p("I1", "Karl", "Kind", "M", emptyList(), emptyList(), f("c", "CHR", "10 JUN 1850", notiz = "Paten: Wilhelm Alt, Tischler, Sophie Jung, Ehefrau")),
            "I2" to p("I2", "Wilhelm", "Alt", "M", emptyList(), emptyList(), f("b", "BIRT", "1790"), f("d", "DEAT", "3 MAR 1849")),
            "I3" to p("I3", "Sophie", "Jung", "F", emptyList(), emptyList(), f("b", "BIRT", "1841")),
        ), emptyMap())
        val e = pruefen(b, PruefOptionen(aus = emptySet(), jetzt = 2026))
        assertEquals(listOf("I1"), e.treffer.getValue("024").map { it.person })   // Pate Wilhelm Alt schon tot
        assertTrue(e.treffer.getValue("024").single().text.contains("Wilhelm Alt"))
        assertEquals(1, e.treffer.getValue("126").size)                             // Sophie Jung 9 Jahre alt
    }

    @Test
    fun dubletteOrteNamenLebende() {
        val b = TreeExport(1, mapOf(
            "I1" to p("I1", "Catharina", "Müller", "F", emptyList(), emptyList(), f("b", "BIRT", "1801", "Hermannsburg, Celle, Niedersachsen", 52.83, 10.08), f("d", "DEAT", "1860")),
            "I2" to p("I2", "Katharina", "Mueller", "F", emptyList(), emptyList(), f("b", "BIRT", "1802", "Hermannsburg")),
            "I3" to p("I3", "Johann", "Maria", "F", emptyList(), emptyList(), f("b", "BIRT", "1 JAN 1830", "Celle, Niedersachsen", 52.62, 10.08),
                f("c", "CHR", "2 JAN 1830", "Hamburg, Hamburg", 53.55, 9.99), f("d", "DEAT", "1890", "Hermannsbrug, Celle, Niedersachsen")),
        ), emptyMap())
        val e = pruefen(b, PruefOptionen(aus = emptySet(), jetzt = 2026))
        assertEquals(1, e.treffer.getValue("228").size)          // Catharina Müller ≈ Katharina Mueller
        assertEquals(1, e.treffer.getValue("310").size)          // Nachname Maria
        assertEquals(1, e.treffer.getValue("330").size)          // Johann, weiblich
        assertEquals(setOf("I2"), e.treffer.getValue("127").map { it.person }.toSet())   // 1802 ohne Sterbeangabe
        assertEquals(2, e.treffer.getValue("510").size)          // Hermannsbrug ≈ Hermannsburg, Hermannsburg ohne Gliederung
        assertEquals(1, e.treffer.getValue("512").size)          // Celle und Hamburg am Folgetag, ueber 100 km
        assertEquals(setOf("I2"), e.treffer.getValue("421").map { it.person }.toSet().intersect(setOf("I2")))
    }

    @Test
    fun abhakenUndFingerabdruck() {
        fun baum(tod: String) = TreeExport(1, mapOf("I1" to p("I1", "Anna", "Test", "F", emptyList(), emptyList(), f("b", "BIRT", "5 MAY 1850"), f("d", "DEAT", tod))), emptyMap())
        val t = pruefen(baum("1 MAY 1850"), PruefOptionen(jetzt = 2026)).treffer.getValue("010").single()
        // Fingerabdruck haengt nicht an der Sprache
        assertEquals(t.fingerabdruck, pruefen(baum("1 MAY 1850"), PruefOptionen(jetzt = 2026, texte = { "en:$it" })).treffer.getValue("010").single().fingerabdruck)
        var liste = Abhakliste().umschalten("srv|t", t, "2026-09-27")
        assertTrue(t.schluessel in liste.fuer("srv|t"))
        liste = Abhakliste.ausJson(liste.alsJson())
        assertTrue(t.schluessel in liste.fuer("srv|t"))
        // Daten geaendert: anderer Fingerabdruck, der Treffer ist wieder offen
        val t2 = pruefen(baum("2 MAY 1850"), PruefOptionen(jetzt = 2026)).treffer.getValue("010").single()
        assertTrue(t2.schluessel !in liste.fuer("srv|t"))
        assertEquals(0, liste.aufraeumen("srv|t", setOf(t2.schluessel)).anzahl("srv|t"))
        // gedaudit: Haken aus der Zeit, als der Pfad der Schluessel war, ziehen um
        val umgezogen = liste.uebernehmen("srv|t", "gedaudit|webtrees|mytree")
        assertEquals(0, umgezogen.anzahl("srv|t")); assertTrue(t.schluessel in umgezogen.fuer("gedaudit|webtrees|mytree"))
        assertTrue(umgezogen.uebernehmen("srv|t", "gedaudit|webtrees|mytree") === umgezogen)
    }

    @Test
    fun abhakSchluesselEinerDateiUeberstehtKopienUndSicherungen() {
        val s = abhakSchluesselDatei(null, "WEBTREES", "MyTree.ged")
        assertEquals("gedaudit|WEBTREES|mytree", s)
        listOf("MyTree (2).ged", "MyTree (3).GED", "MyTree_2026-10-08.ged", "MyTree 2026-11-01 (2).ged", "MyTree-20261008_1530.gedcom")
            .forEach { assertEquals(s, abhakSchluesselDatei(null, "WEBTREES", it), it) }
        // Name aus dem Kopf geht vor, auch mit Ordner davor; umbenannte Datei bleibt derselbe Baum
        assertEquals(s, abhakSchluesselDatei("C:\\Export\\MyTree.ged", "WEBTREES", "Sicherung Oktober.ged"))
        // anderes Programm oder anderer Name: anderer Baum
        assertTrue(s != abhakSchluesselDatei(null, "ANDERES", "MyTree.ged"))
        assertTrue(s != abhakSchluesselDatei(null, "WEBTREES", "Kies.ged"))
        // nur Ziffern im Namen: nichts wegschneiden, was den Namen leer macht
        assertEquals("gedaudit||2026-10-08", abhakSchluesselDatei(null, null, "2026-10-08.ged"))
    }

    @Test
    fun voreinstellungenPassenZumKatalog() {
        Voreinstellung.alle.forEach { v ->
            (v.grenzwerte.keys + v.aus + v.schwere.keys).forEach { id -> assertNotNull(Regelkatalog.nachId[id], "${v.id}: $id") }
            v.grenzwerte.keys.forEach { id -> assertNotNull(Regelkatalog.nachId.getValue(id).grenzwert, "${v.id}: $id ohne Grenzwert") }
        }
    }
}
