package de.bgghome.webtrees.nativ.gedcom

import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.Associate
import de.bgghome.webtrees.nativ.api.DateJson
import de.bgghome.webtrees.nativ.api.EventJson
import de.bgghome.webtrees.nativ.api.ExportFamily
import de.bgghome.webtrees.nativ.api.ExportIndividual
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.FreeAssociate
import de.bgghome.webtrees.nativ.api.GedcomZusatz
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.PlaceJson
import de.bgghome.webtrees.nativ.api.SourceRef
import de.bgghome.webtrees.nativ.api.TreeExport
import de.bgghome.webtrees.nativ.data.hauptHeirat
import de.bgghome.webtrees.nativ.pruefung.DateiBefund
import de.bgghome.webtrees.nativ.pruefung.Rueckverweis
import de.bgghome.webtrees.nativ.pruefung.TagVorkommen
import de.bgghome.webtrees.nativ.pruefung.Verweis
import de.bgghome.webtrees.nativ.res.Res
import de.bgghome.webtrees.nativ.res.desk_gl_family_status
import de.bgghome.webtrees.nativ.res.desk_gl_status_never_married
import de.bgghome.webtrees.nativ.res.desk_gl_status_not_married
import de.bgghome.webtrees.nativ.res.desk_gl_status_unknown
import de.bgghome.webtrees.nativ.res.date_text_about
import de.bgghome.webtrees.nativ.res.date_text_after
import de.bgghome.webtrees.nativ.res.date_text_before
import de.bgghome.webtrees.nativ.res.date_text_between
import de.bgghome.webtrees.nativ.res.date_text_calculated
import de.bgghome.webtrees.nativ.res.date_text_day
import de.bgghome.webtrees.nativ.res.date_text_estimated
import de.bgghome.webtrees.nativ.res.date_text_from
import de.bgghome.webtrees.nativ.res.date_text_from_to
import de.bgghome.webtrees.nativ.res.date_text_month
import de.bgghome.webtrees.nativ.res.date_text_to
import de.bgghome.webtrees.nativ.res.desk_ev_baptism
import de.bgghome.webtrees.nativ.res.desk_ev_birth
import de.bgghome.webtrees.nativ.res.desk_ev_burial
import de.bgghome.webtrees.nativ.res.desk_ev_cremation
import de.bgghome.webtrees.nativ.res.desk_ev_death
import de.bgghome.webtrees.nativ.res.desk_ev_marriage
import de.bgghome.webtrees.nativ.res.desk_ev_name
import de.bgghome.webtrees.nativ.res.desk_ev_occupation
import de.bgghome.webtrees.nativ.res.desk_ev_religion
import de.bgghome.webtrees.nativ.res.desk_ev_residence
import de.bgghome.webtrees.nativ.res.desk_ev_property
import de.bgghome.webtrees.nativ.res.desk_ev_census
import de.bgghome.webtrees.nativ.res.desk_ev_confirmation
import de.bgghome.webtrees.nativ.res.desk_ev_emigration
import de.bgghome.webtrees.nativ.res.desk_ev_immigration
import de.bgghome.webtrees.nativ.res.desk_ev_divorce
import de.bgghome.webtrees.nativ.res.desk_ev_engagement
import de.bgghome.webtrees.nativ.res.desk_ev_education
import de.bgghome.webtrees.nativ.res.desk_ev_will
import de.bgghome.webtrees.nativ.res.desk_ev_note
import java.io.File
import java.text.Normalizer
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/*
 * GEDCOM-Datei lesen (gedaudit, 13.10.2026): aus einer .ged-Datei dieselbe Form, die wtWin vom Server bekommt
 * (TreeExport), damit Pruefung, Listen und Druck nicht wissen muessen, woher der Baum kommt. Gelesen wird
 * GEDCOM 5.5/5.5.1 und 7 (Zeilenform), Zeichensatz aus Byte-Reihenfolge-Marke oder dem Kopf (UTF-8, UTF-16,
 * ANSEL, ANSI). Es werden nur die Teile uebernommen, die Pruefung und Anzeige brauchen: Personen, Familien,
 * Ereignisse mit Datum, Ort, Notizen, Quellen und Beteiligten (Paten, Zeugen). Medien bleiben aussen vor.
 */

/** Ein Knoten der Datei: Tag, Wert (CONT/CONC schon zusammengesetzt) und Unterknoten. */
class GedcomKnoten(val tag: String, var wert: String, val kinder: MutableList<GedcomKnoten> = mutableListOf()) {
    fun erstes(tag: String): GedcomKnoten? = kinder.firstOrNull { it.tag == tag }
    fun alle(tag: String): List<GedcomKnoten> = kinder.filter { it.tag == tag }
    fun wert(tag: String): String? = erstes(tag)?.wert?.takeIf { it.isNotBlank() }
}

/** Ein Datensatz der Stufe 0: Art (INDI, FAM, SOUR ...), Kennung ohne @ und die Unterknoten. */
class GedcomSatz(val art: String, val xref: String, val knoten: GedcomKnoten)

/** Was beim Lesen auffiel: Zeichensatz, uebersprungene Zeilen, fehlende Verweise. */
class GedcomBefund(val zeichensatz: String, val zeilen: Int, val uebersprungen: Int, val fehlendeVerweise: Int)

class GedcomDatei(
    val saetze: List<GedcomSatz>,
    val befund: GedcomBefund,
    /** Zeilen, die kein GEDCOM sind (Nummer, Text), hoechstens die ersten 500. */
    val uebersprungen: List<Pair<Int, String>> = emptyList(),
    /** Kennungen, die mehr als einmal vergeben sind. */
    val doppelt: Map<String, Int> = emptyMap(),
    /** Zeilen mit CONC (in GEDCOM 7 nicht mehr erlaubt; beim Lesen schon angehaengt). */
    val concZeilen: Int = 0,
) {
    val kopf: GedcomKnoten? get() = saetze.firstOrNull { it.art == "HEAD" }?.knoten
    /** "1 CHAR" im Kopf, wie geschrieben. */
    val zeichensatzAngabe: String? get() = kopf?.wert("CHAR")
    /** "2 VERS" unter "1 GEDC", wie geschrieben (5.5.1, 7.0 ...); null = keine Angabe. */
    val versionAngabe: String? get() = kopf?.erstes("GEDC")?.wert("VERS")?.trim()
    /** Aus der Angabe im Kopf: GEDCOM 7 oder (sonst) 5.5.1. */
    val istSieben: Boolean get() = versionAngabe?.startsWith("7") == true
}

object GedcomLeser {

    fun lesen(datei: File): GedcomDatei = lesen(datei.readBytes())

    fun lesen(bytes: ByteArray): GedcomDatei {
        val (text, zeichensatz) = Zeichensatz.dekodieren(bytes)
        return zerlegen(text, zeichensatz)
    }

    /** Die Zeilen zu Datensaetzen und Knoten; CONT haengt mit Zeilenumbruch, CONC ohne an den Wert davor. */
    fun zerlegen(text: String, zeichensatz: String = "UTF-8"): GedcomDatei {
        val saetze = ArrayList<GedcomSatz>()
        val stapel = ArrayList<GedcomKnoten>()   // Knoten je Stufe
        var zeilen = 0; var uebersprungen = 0; var conc = 0
        val schlecht = ArrayList<Pair<Int, String>>()
        val kennungen = HashMap<String, Int>()
        var zuletzt: GedcomKnoten? = null
        for (roh in text.lineSequence()) {
            val z = roh.trimEnd('\r').let { if (zeilen == 0) it.removePrefix("﻿") else it }.trimStart()
            if (z.isEmpty()) continue
            zeilen++
            val m = ZEILE.matchEntire(z)
            if (m == null) { uebersprungen++; if (schlecht.size < 500) schlecht += zeilen to z; continue }
            val stufe = m.groupValues[1].toInt()
            val xref = m.groupValues[2].takeIf { it.isNotEmpty() }?.trim('@')
            val tag = m.groupValues[3].uppercase()
            val wert = m.groupValues[4]
            if (tag == "CONT" || tag == "CONC") {
                val ziel = zuletzt ?: run { uebersprungen++; if (schlecht.size < 500) schlecht += zeilen to z; continue }
                ziel.wert = if (tag == "CONT") ziel.wert + "\n" + wert else ziel.wert + wert
                if (tag == "CONC") conc++
                continue
            }
            val knoten = GedcomKnoten(tag, wert)
            if (stufe == 0) {
                stapel.clear(); stapel += knoten
                // "0 @I1@ INDI" oder "0 HEAD" / "0 TRLR"
                saetze += GedcomSatz(tag, xref.orEmpty(), knoten)
                if (!xref.isNullOrEmpty()) kennungen.merge(xref, 1, Int::plus)
            } else {
                if (stufe > stapel.size) { uebersprungen++; if (schlecht.size < 500) schlecht += zeilen to z; continue }
                while (stapel.size > stufe) stapel.removeAt(stapel.lastIndex)
                stapel.last().kinder += knoten
                stapel += knoten
            }
            zuletzt = knoten
        }
        return GedcomDatei(saetze, GedcomBefund(zeichensatz, zeilen, uebersprungen, 0), schlecht, kennungen.filterValues { it > 1 }, conc)
    }

    private val ZEILE = Regex("^(\\d{1,2}) (?:(@[^@]+@) )?([A-Za-z0-9_]+)(?: (.*))?$")

    // ── In die Form des Servers ──

    /** Der Baum in der Form von api4webtrees; [jetzt] fuer "vermutlich lebend". */
    fun alsExport(d: GedcomDatei, jetzt: Int = java.time.Year.now().value, stand: Long = 0): TreeExport =
        Umsetzer(d, jetzt).export(stand)

    /**
     * Baum und Befund ueber die Form der Datei (Regeln 6xx) in einem Gang. [dateiname] fuer den Vergleich mit FILE im
     * Kopf; [pruefenAls] "5.5.1" oder "7.0" erzwingt die Version, null nimmt die Angabe im Kopf.
     */
    fun auswerten(d: GedcomDatei, jetzt: Int = java.time.Year.now().value, stand: Long = 0, dateiname: String? = null,
                  pruefenAls: String? = null): Pair<TreeExport, DateiBefund> {
        val u = Umsetzer(d, jetzt)
        val baum = u.export(stand)
        val sieben = pruefenAls?.startsWith("7") ?: d.istSieben
        return baum to DateiBefund(
            uebersprungen = d.uebersprungen,
            verweise = u.verweise,
            doppelt = d.doppelt,
            rueckverweise = u.rueckverweise(),
            kopfFehlt = d.kopf == null,
            zeichensatzAngabe = d.zeichensatzAngabe,
            zeichensatzErkannt = d.befund.zeichensatz,
            fremdeTags = u.fremdeTags(),
            version = if (sieben) "7.0" else "5.5.1",
            versionAngabe = d.versionAngabe,
            kopf = GedcomForm.kopf(d, sieben),
            dateiname = if (sieben) null else GedcomForm.dateiname(d, dateiname),
            addendum = GedcomForm.addendum(d, sieben),
            kopfDatum = GedcomForm.kopfDatum(d),
            sieben = if (sieben) GedcomForm.sieben(d) else emptyList(),
            ereignisText = GedcomForm.ereignisText(d),
        )
    }

    private class Umsetzer(val d: GedcomDatei, val jetzt: Int) {
        val quellen: Map<String, String> = d.saetze.filter { it.art == "SOUR" }.associate { s ->
            s.xref to (s.knoten.wert("TITL") ?: s.knoten.wert("ABBR") ?: s.xref)
        }
        val notizen: Map<String, String> = d.saetze.filter { it.art == "NOTE" || it.art == "SNOTE" }.associate { it.xref to it.knoten.wert }
        val indis = d.saetze.filter { it.art == "INDI" && it.xref.isNotEmpty() }
        val fams = d.saetze.filter { it.art == "FAM" && it.xref.isNotEmpty() }
        val famIds = fams.map { it.xref }.toSet()
        val indiIds = indis.map { it.xref }.toSet()
        /** Ortsdatensaetze (GEDCOM-L _LOC): Verweise darauf (3 _LOC am Ort, 1 _LOC im Ortsdatensatz) muessen ankommen. */
        val locIds = d.saetze.filter { it.art == "_LOC" && it.xref.isNotEmpty() }.map { it.xref }.toSet()
        /** Name und Geschlecht je Person, vorab fuer Beteiligte (ASSO). */
        val namen: Map<String, Pair<String, String>> = indis.associate { s ->
            val (vn, fn) = nameTeile(s.knoten.erstes("NAME")?.wert.orEmpty())
            s.xref to ("$vn $fn".trim() to (s.knoten.wert("SEX")?.take(1)?.uppercase() ?: "U"))
        }
        var fehlend = 0
        val verweise = ArrayList<Verweis>()
        /** Welcher Datensatz gerade umgesetzt wird (fuer die Verweise). */
        private var aktuell = ""

        fun export(stand: Long): TreeExport {
            val personen = LinkedHashMap<String, ExportIndividual>()
            val familien = LinkedHashMap<String, ExportFamily>()
            indis.forEach { s -> aktuell = s.xref; personen[s.xref] = person(s) }
            fams.forEach { s -> aktuell = s.xref; familien[s.xref] = familie(s) }
            // Uebergeordnete Orte im Ortsdatensatz (1 _LOC @L1@): auch sie muessen auf einen vorhandenen Ortsdatensatz zeigen
            d.saetze.filter { it.art == "_LOC" }.forEach { s -> aktuell = s.xref; s.knoten.alle("_LOC").forEach { verweis(it.wert.trim('@'), locIds, "_LOC") } }
            return TreeExport(stand, personen, familien)
        }

        fun person(s: GedcomSatz): ExportIndividual {
            val k = s.knoten
            val (vn, fn) = nameTeile(k.erstes("NAME")?.wert.orEmpty())
            val sex = k.wert("SEX")?.take(1)?.uppercase()?.takeIf { it == "M" || it == "F" } ?: "U"
            val fakten = fakten(s.xref, k, person = true)
            // Paten an der Person (1 ASSO) gehoeren zur Taufe
            val patenAnPerson = k.alle("ASSO").mapNotNull { beteiligter(it, level1 = true) }
            val mitPaten = if (patenAnPerson.isEmpty()) fakten else {
                val i = fakten.indexOfFirst { it.tag == "CHR" || it.tag == "BAPM" }
                if (i < 0) fakten else fakten.mapIndexed { j, f -> if (j == i) f.copy(associates = f.associates + patenAnPerson) else f }
            }
            fun ereignis(vararg tags: String): FactJson? = tags.firstNotNullOfOrNull { t -> mitPaten.firstOrNull { it.tag == t && (it.date != null || it.place != null) } }
                ?: tags.firstNotNullOfOrNull { t -> mitPaten.firstOrNull { it.tag == t } }
            val geb = ereignis("BIRT"); val tod = ereignis("DEAT"); val chr = ereignis("CHR", "BAPM"); val buri = ereignis("BURI", "CREM")
            val gebJahr = geb?.date?.year?.takeIf { it > 0 } ?: chr?.date?.year?.takeIf { it > 0 }
            val todJahr = tod?.date?.year?.takeIf { it > 0 } ?: buri?.date?.year?.takeIf { it > 0 }
            val hatTod = mitPaten.any { it.tag == "DEAT" || it.tag == "BURI" || it.tag == "CREM" }
            // Wie webtrees: tot bei Sterbeangabe oder wenn die Geburt zu lange her ist
            val tot = hatTod || (gebJahr != null && jetzt - gebJahr > 110)
            val lebensdaten = listOf(gebJahr?.toString().orEmpty(), todJahr?.toString().orEmpty()).let { (a, b) -> if (a.isEmpty() && b.isEmpty()) "" else "$a–$b" }
            val person = Person(
                xref = s.xref, name = "$vn $fn".trim(), sortName = listOf(fn, vn).filter { it.isNotBlank() }.joinToString(", "),
                given = vn, surname = fn, sex = sex, isDead = tot, lifespan = lebensdaten,
                birth = geb?.let { EventJson(it.date, it.place) }, death = tod?.let { EventJson(it.date, it.place) },
                chr = chr?.let { EventJson(it.date, it.place) }, buri = buri?.let { EventJson(it.date, it.place) },
                occupation = mitPaten.firstOrNull { it.tag == "OCCU" }?.value?.takeIf { it.isNotBlank() },
                call = k.erstes("NAME")?.wert("NICK") ?: k.erstes("NAME")?.wert("_RUFNAME").orEmpty(),
            )
            return ExportIndividual(
                person = person,
                famc = k.alle("FAMC").map { it.wert.trim('@') }.filter { verweis(it, famIds, "FAMC") },
                fams = k.alle("FAMS").map { it.wert.trim('@') }.filter { verweis(it, famIds, "FAMS") },
                facts = mitPaten,
            )
        }

        fun familie(s: GedcomSatz): ExportFamily {
            val k = s.knoten
            val fakten = fakten(s.xref, k, person = false)
            val heirat = fakten.hauptHeirat()
            return ExportFamily(
                xref = s.xref,
                husband = k.wert("HUSB")?.trim('@')?.takeIf { verweis(it, indiIds, "HUSB") },
                wife = k.wert("WIFE")?.trim('@')?.takeIf { verweis(it, indiIds, "WIFE") },
                children = k.alle("CHIL").map { it.wert.trim('@') }.filter { verweis(it, indiIds, "CHIL") },
                marriage = heirat?.let { EventJson(it.date, it.place) },
                facts = fakten,
            )
        }

        fun verweis(id: String, vorhanden: Set<String>, tag: String): Boolean {
            if (id.equals("VOID", ignoreCase = true)) return false   // GEDCOM 7 @VOID@: bewusst ohne Datensatz
            return (id in vorhanden).also { if (!it) { fehlend++; verweise += Verweis(aktuell, tag, id) } }
        }

        /** FAMC/FAMS an der Person gegen CHIL/HUSB/WIFE an der Familie: was nur auf einer Seite steht. */
        fun rueckverweise(): List<Rueckverweis> {
            val out = ArrayList<Rueckverweis>()
            val famVon = fams.associateBy { it.xref }
            val indiVon = indis.associateBy { it.xref }
            indis.forEach { s ->
                s.knoten.alle("FAMC").map { it.wert.trim('@') }.forEach { f -> famVon[f]?.let { fam -> if (fam.knoten.alle("CHIL").none { it.wert.trim('@') == s.xref }) out += Rueckverweis(s.xref, f, "FAMC") } }
                s.knoten.alle("FAMS").map { it.wert.trim('@') }.forEach { f -> famVon[f]?.let { fam -> if (listOfNotNull(fam.knoten.wert("HUSB"), fam.knoten.wert("WIFE")).none { it.trim('@') == s.xref }) out += Rueckverweis(s.xref, f, "FAMS") } }
            }
            fams.forEach { fam ->
                fam.knoten.alle("CHIL").map { it.wert.trim('@') }.forEach { c -> indiVon[c]?.let { i -> if (i.knoten.alle("FAMC").none { it.wert.trim('@') == fam.xref }) out += Rueckverweis(c, fam.xref, "CHIL") } }
                listOf("HUSB", "WIFE").forEach { tag -> fam.knoten.wert(tag)?.trim('@')?.let { p -> indiVon[p]?.let { i -> if (i.knoten.alle("FAMS").none { it.wert.trim('@') == fam.xref }) out += Rueckverweis(p, fam.xref, tag) } } }
            }
            return out
        }

        /** Tags ohne Unterstrich, die weder in GEDCOM 5.5.1 noch in 7 vorkommen, mit Anzahl und einem Datensatz. */
        fun fremdeTags(): Map<String, TagVorkommen> {
            val zahl = LinkedHashMap<String, Int>(); val beispiel = HashMap<String, String>()
            fun gehe(k: GedcomKnoten, satz: String) {
                k.kinder.forEach { n ->
                    if (!n.tag.startsWith("_") && n.tag !in STANDARD_TAGS) { zahl.merge(n.tag, 1, Int::plus); beispiel.putIfAbsent(n.tag, satz) }
                    gehe(n, satz)
                }
            }
            d.saetze.forEach { s -> if (s.art !in STANDARD_TAGS && !s.art.startsWith("_")) { zahl.merge(s.art, 1, Int::plus); beispiel.putIfAbsent(s.art, s.xref) }; gehe(s.knoten, s.xref) }
            return zahl.mapValues { (tag, n) -> TagVorkommen(n, beispiel[tag]?.takeIf { it.isNotEmpty() }) }
        }

        /** Die Fakten eines Datensatzes: alle Unterknoten der Stufe 1, die Ereignis, Eigenschaft oder Notiz sind. */
        fun fakten(xref: String, k: GedcomKnoten, person: Boolean): List<FactJson> {
            val out = ArrayList<FactJson>()
            k.kinder.forEach { n ->
                if (n.tag in KEIN_FAKT || (n.tag.startsWith("_") && n.tag != "_STAT")) return@forEach
                if (n.tag == "SOUR") {
                    // Quelle an der Person selbst: als Fakt mit sich selbst als Beleg, damit "ohne Quelle" nicht greift
                    val q = quelle(n) ?: return@forEach
                    out += FactJson(id = "$xref:${out.size}", tag = "SOUR", label = q.mitSeite(), value = q.title, sources = listOf(q))
                    return@forEach
                }
                val datum = n.erstes("DATE")?.wert?.trim()?.takeIf { it.isNotEmpty() }
                val ort = n.erstes("PLAC")
                val art = n.wert("TYPE").orEmpty()
                // Verweis auf den Ortsdatensatz (GEDCOM-L 3 _LOC) wie FAMC/FAMS pruefen
                ort?.alle("_LOC")?.forEach { verweis(it.wert.trim('@'), locIds, "_LOC") }
                val adresse = n.erstes("ADDR")
                out += FactJson(
                    id = "$xref:${out.size}",
                    tag = n.tag,
                    label = label(n.tag, art),
                    value = if (n.wert == "Y") "" else if (n.tag == "_STAT") familienstand(n.wert) else n.wert,
                    type = art,
                    date = datum?.let { DateJson(text = GedcomDatum.anzeige(it), year = GedcomDatum.jahr(it), gedcom = it) },
                    place = ort?.let { o ->
                        val name = o.wert.trim(); if (name.isEmpty()) null else {
                            val karte = o.erstes("MAP")
                            PlaceJson(name = name, short = name.substringBefore(',').trim(),
                                lat = karte?.wert("LATI")?.let(::koordinate), lng = karte?.wert("LONG")?.let(::koordinate))
                        }
                    },
                    notes = (n.alle("NOTE") + n.alle("SNOTE")).map { notiz(it) }.filter { it.isNotBlank() },
                    sources = n.alle("SOUR").mapNotNull { quelle(it) },
                    // GEDCOM 5.5.1 kennt ASSO, GEDCOM-L _ASSO am Ereignis; Paten und Zeugen ohne Datensatz als _GODP/_WITN
                    associates = (n.alle("ASSO") + n.alle("_ASSO")).mapNotNull { beteiligter(it, level1 = false) },
                    freeAssociates = n.alle("_GODP").map { FreeAssociate(role = "godparent", name = it.wert.trim(), text = it.wert.trim()) } +
                        n.alle("_WITN").map { FreeAssociate(role = "witness", name = it.wert.trim(), text = it.wert.trim()) } +
                        // GEDCOM 7: Beteiligte ohne Datensatz als ASSO @VOID@ mit dem Namen unter PHRASE
                        n.alle("ASSO").filter { istVoid(it.wert) }.mapNotNull { a ->
                            val name = a.wert("PHRASE")?.trim() ?: return@mapNotNull null
                            FreeAssociate(role = rolle(a.wert("ROLE") ?: a.wert("RELA").orEmpty()), name = name, text = name)
                        },
                    address = adresse?.let { a -> (listOf(a.wert) + a.alle("CONT").map { it.wert }).joinToString("\n").trim().ifBlank { a.wert("ADR1") } },
                    extras = buildList {
                        ort?.let { o -> listOf("_GOV", "_POST", "_MAIDENHEAD").forEach { t -> o.alle(t).forEach { z -> if (z.wert.isNotBlank()) add(GedcomZusatz("PLAC:$t", z.wert.trim(), z.wert("DATE"))) } } }
                        adresse?.alle("_NAME")?.forEach { z -> if (z.wert.isNotBlank()) add(GedcomZusatz("ADDR:_NAME", z.wert.trim(), null)) }
                    },
                )
            }
            return out
        }

        fun notiz(n: GedcomKnoten): String {
            val w = n.wert.trim()
            if (w.startsWith("@") && w.endsWith("@")) return notizen[w.trim('@')] ?: ""
            return n.wert
        }

        fun quelle(n: GedcomKnoten): SourceRef? {
            val w = n.wert.trim()
            val xref = if (w.startsWith("@") && w.endsWith("@")) w.trim('@') else ""
            val titel = if (xref.isNotEmpty()) (quellen[xref] ?: xref) else n.wert
            if (xref.isEmpty() && titel.isBlank()) return null
            return SourceRef(
                xref = xref, title = titel, page = n.wert("PAGE").orEmpty(),
                quality = n.wert("QUAY")?.toIntOrNull(),
                date = n.erstes("DATA")?.wert("DATE")?.let { DateJson(text = GedcomDatum.anzeige(it), year = GedcomDatum.jahr(it), gedcom = it) },
                text = n.erstes("DATA")?.erstes("TEXT")?.wert ?: n.wert("TEXT").orEmpty(),
                notes = n.alle("NOTE").map { notiz(it) }.filter { it.isNotBlank() },
            )
        }

        fun beteiligter(n: GedcomKnoten, level1: Boolean): Associate? {
            if (istVoid(n.wert)) return null   // GEDCOM 7 @VOID@: kein Datensatz, kein fehlender Verweis
            val xref = n.wert.trim().trim('@')
            val (name, sex) = namen[xref] ?: run { fehlend++; return null }
            val rela = n.wert("RELA") ?: n.wert("ROLE") ?: ""
            val rolle = rolle(rela)
            if (level1 && rolle != "godparent") return null
            return Associate(xref = xref, name = name, sex = sex, rela = rela, role = rolle, label = rela, level1 = level1,
                notes = n.alle("NOTE").map { notiz(it) }.filter { it.isNotBlank() })
        }
    }

    /** "Johann Georg /Fischer/ jun." -> Vornamen und Nachname (mit Zusatz). */
    fun nameTeile(name: String): Pair<String, String> {
        val n = name.trim()
        val i = n.indexOf('/'); val j = if (i >= 0) n.indexOf('/', i + 1) else -1
        if (i < 0) return n.replace(Regex("\\s+"), " ") to ""
        val vn = n.substring(0, i).trim()
        val fn = (if (j > i) n.substring(i + 1, j) else n.substring(i + 1)).trim()
        val rest = if (j > i) n.substring(j + 1).trim() else ""
        return (if (rest.isEmpty()) vn else "$vn $rest".trim()).replace(Regex("\\s+"), " ") to fn.replace(Regex("\\s+"), " ")
    }

    /** "N48.123" / "W8.5" (GEDCOM) oder "48.123" -> Grad. */
    fun koordinate(s: String): Double? {
        val t = s.trim().uppercase()
        if (t.isEmpty()) return null
        val vorzeichen = when (t.first()) { 'S', 'W' -> -1.0; else -> 1.0 }
        val zahl = t.trimStart('N', 'S', 'E', 'W', 'O').replace(',', '.').toDoubleOrNull() ?: return null
        return vorzeichen * zahl
    }

    fun rolle(rela: String): String {
        val r = rela.lowercase()
        return when {
            r.contains("pat") || r.contains("godp") || r.contains("sponsor") || r.contains("taufzeug") || r.contains("peter") || r.contains("meter") || r.contains("padrin") || r.contains("marrain") || r.contains("parrain") -> "godparent"
            r == "witn" || r.contains("zeug") || r.contains("witness") || r.contains("getuige") || r.contains("témoin") || r.contains("temoin") || r.contains("testigo") -> "witness"
            else -> "other"
        }
    }

    /** Die Beschriftung eines Fakts in der Sprache der Oberflaeche; unbekannte Tags bleiben, wie sie sind. */
    fun label(tag: String, art: String = ""): String {
        val res = when (tag) {
            "BIRT" -> Res.string.desk_ev_birth
            "CHR", "BAPM" -> Res.string.desk_ev_baptism
            "DEAT" -> Res.string.desk_ev_death
            "BURI" -> Res.string.desk_ev_burial
            "CREM" -> Res.string.desk_ev_cremation
            "MARR" -> Res.string.desk_ev_marriage
            "NAME" -> Res.string.desk_ev_name
            "OCCU" -> Res.string.desk_ev_occupation
            "RELI" -> Res.string.desk_ev_religion
            "RESI" -> Res.string.desk_ev_residence
            "PROP" -> Res.string.desk_ev_property
            "CENS" -> Res.string.desk_ev_census
            "CONF" -> Res.string.desk_ev_confirmation
            "EMIG" -> Res.string.desk_ev_emigration
            "IMMI" -> Res.string.desk_ev_immigration
            "DIV" -> Res.string.desk_ev_divorce
            "ENGA" -> Res.string.desk_ev_engagement
            "EDUC" -> Res.string.desk_ev_education
            "WILL" -> Res.string.desk_ev_will
            "NOTE" -> Res.string.desk_ev_note
            "_STAT" -> Res.string.desk_gl_family_status
            else -> null
        }
        if (res != null) return Texte.t(res)
        return TAG_NAMEN[tag] ?: (if (tag == "EVEN" || tag == "FACT") art.ifBlank { tag } else tag)
    }

    /** Die Tags des GEDCOM-L-Addendums zu GEDCOM 5.5.1 (Liste wie in webtrees, CustomTags/GedcomL). */
    val GEDCOML_TAGS: Set<String> = setOf("_ADDENDUM", "_SCHEMA", "_DEFN", "_UID", "_RUFNAME", "_GOV", "_GOVTYPE", "_POST", "_MAIDENHEAD", "_LOC",
        "_NAME", "_GODP", "_WITN", "_ASSO", "_PRIM", "_TODO", "_CAT", "_CDATE", "_RDATE", "_PRTY", "_STAT", "_AIDN", "_DMGD")

    /** Familienstand nach GEDCOM-L (1 _STAT) als Text; andere Werte roh. */
    fun familienstand(wert: String): String = when (wert.trim().uppercase()) {
        "NOT MARRIED" -> Texte.t(Res.string.desk_gl_status_not_married)
        "NEVER MARRIED" -> Texte.t(Res.string.desk_gl_status_never_married)
        "UNKNOWN" -> Texte.t(Res.string.desk_gl_status_unknown)
        else -> wert
    }

    /** "@VOID@": GEDCOM 7 fuer einen Verweis ohne Datensatz. */
    fun istVoid(wert: String): Boolean = wert.trim().equals("@VOID@", ignoreCase = true)

    /** Die Tags von GEDCOM 7.0, aus der Registry von FamilySearch (GEDCOM-registries, g7validation.json, Oktober 2026). */
    val STANDARD_70: Set<String> = """
        ABBR ADDR ADOP ADR1 ADR2 ADR3 AGE AGNC ALIA ANCI ANUL ASSO AUTH BAPL BAPM BARM BASM BIRT BLES BURI CALN CAST CAUS CENS
        CHAN CHIL CHR CHRA CITY CONF CONL CONT COPR CORP CREA CREM CROP CTRY DATA DATE DEAT DESI DEST DIV DIVF DSCR EDUC EMAIL
        EMIG ENDL ENGA EVEN EXID FACT FAM FAMC FAMS FAX FCOM FILE FORM GEDC GIVN GRAD HEAD HEIGHT HUSB IDNO IMMI INDI INIL LANG
        LATI LEFT LONG MAP MARB MARC MARL MARR MARS MEDI MIME NAME NATI NATU NCHI NICK NMR NO NOTE NPFX NSFX OBJE OCCU ORDN
        PAGE PEDI PHON PHRASE PLAC POST PROB PROP PUBL QUAY REFN RELI REPO RESI RESN RETI ROLE SCHMA SDATE SEX SLGC SLGS SNOTE
        SOUR SPFX SSN STAE STAT SUBM SURN TAG TEMP TEXT TIME TITL TOP TRAN TRLR TYPE UID VERS WIDTH WIFE WILL WWW
    """.trim().split(Regex("\\s+")).toSet()

    /** Tags, die es erst in GEDCOM 7 gibt (in einer 5.5.1-Datei nicht Standard). */
    private val NUR_70 = setOf("SNOTE", "EXID", "MIME", "UID", "SCHMA", "TAG", "CREA", "INIL", "NO", "TRAN", "PHRASE", "SDATE",
        "CROP", "TOP", "LEFT", "HEIGHT", "WIDTH")

    /** Die Tags von GEDCOM 5.5.1 (Datensaetze, Ereignisse, Attribute, Strukturen). */
    val STANDARD_551: Set<String> by lazy { STANDARD_TAGS - NUR_70 }

    /** Alle Tags aus GEDCOM 5.5.1 und 7.0 (Datensaetze, Ereignisse, Attribute, Strukturen). */
    val STANDARD_TAGS: Set<String> = """
        HEAD TRLR INDI FAM SOUR REPO NOTE SNOTE OBJE SUBM SUBN
        ABBR ADDR ADR1 ADR2 ADR3 ADOP AFN AGE AGNC ALIA ANCE ANCI ANUL ASSO AUTH BAPL BAPM BARM BASM BIRT BLES BLOB BURI CALN CAST CAUS
        CENS CHAN CHAR CHIL CHR CHRA CITY CONC CONF CONL CONT COPR CORP CREA CREM CTRY DATA DATE DEAT DESC DESI DEST DIV DIVF DSCR EDUC
        EMAIL EMAI EMIG ENDL ENGA EVEN EXID FACT FAMC FAMS FAX FCOM FILE FONE FORM GEDC GIVN GRAD HUSB IDNO IMMI INIL LANG LATI LEGA
        LONG MAP MARB MARC MARL MARR MARS MEDI MIME NAME NATI NATU NCHI NICK NMR NO NPFX NSFX OCCU ORDI ORDN PAGE PEDI PHON PLAC POST
        PROB PROP PUBL QUAY REFN RELA RELI RESI RESN RETI RFN RIN ROLE ROMN SCHMA SEX SLGC SLGS SPFX SSN STAE STAT SURN TAG TEMP TEXT
        TIME TITL TRAN TYPE UID VERS WIFE WILL WWW PHRASE SDATE CROP TOP LEFT HEIGHT WIDTH
    """.trim().split(Regex("\\s+")).toSet()

    /** Unterknoten der Stufe 1, die keine Fakten sind. */
    private val KEIN_FAKT = setOf("SEX", "FAMC", "FAMS", "HUSB", "WIFE", "CHIL", "CHAN", "CREA", "OBJE", "RIN", "RESN", "REFN", "UID", "SUBM", "ANCI", "DESI", "ASSO", "ALIA", "NCHI", "NMR")

    /** Englische Namen fuer gaengige Tags ohne eigene Uebersetzung (webtrees uebersetzt sie, der Leser nicht). */
    private val TAG_NAMEN = mapOf(
        "RESI" to "Residence", "CENS" to "Census", "CONF" to "Confirmation", "FCOM" to "First communion", "ORDN" to "Ordination",
        "EMIG" to "Emigration", "IMMI" to "Immigration", "NATU" to "Naturalization", "GRAD" to "Graduation", "EDUC" to "Education",
        "DIV" to "Divorce", "DIVF" to "Divorce filed", "ENGA" to "Engagement", "MARB" to "Marriage banns", "MARC" to "Marriage contract",
        "MARL" to "Marriage licence", "MARS" to "Marriage settlement", "ANUL" to "Annulment", "WILL" to "Will", "PROB" to "Probate",
        "ADOP" to "Adoption", "BLES" to "Blessing", "BARM" to "Bar mitzvah", "BASM" to "Bas mitzvah", "RETI" to "Retirement",
        "TITL" to "Title", "NATI" to "Nationality", "CAST" to "Caste", "DSCR" to "Description", "IDNO" to "Identification number",
        "SSN" to "Social security number", "PROP" to "Property", "NOTE" to "Note", "ADDR" to "Address", "PHON" to "Phone",
        "EMAIL" to "Email", "WWW" to "Website", "CHRA" to "Adult christening", "SLGC" to "Sealing child", "SLGS" to "Sealing spouse",
    )
}

/** Die GEDCOM-Datumsform fuer die Anzeige ("12. März 1890", "um 1850", "zwischen 1840 und 1845") in der Sprache der Oberflaeche. */
object GedcomDatum {
    private val MONATE = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    private val PUNKT = Regex("^(?:(\\d{1,2}) )?(?:([A-Z]{3}) )?(\\d{1,4})(?:/(\\d{1,2}))?( B\\.?C\\.?)?$")

    fun jahr(gedcom: String): Int = Regex("\\b(\\d{3,4})\\b").findAll(gedcom).lastOrNull()?.value?.toIntOrNull() ?: 0

    fun anzeige(gedcom: String, locale: Locale = Locale.getDefault()): String {
        var s = gedcom.trim().replace(Regex("\\s+"), " ")
        // Kalender-Vorsatz (@#DJULIAN@); fremde Kalender bleiben Text
        val kal = Regex("^@#D([A-Z ]+)@ ").find(s.uppercase())?.groupValues?.get(1)
        if (kal != null) {
            if (kal != "JULIAN" && kal != "GREGORIAN") return gedcom.trim()
            s = s.substring(s.indexOf("@ ") + 2)
        }
        val u = s.uppercase()
        fun p(t: String) = punkt(t.trim(), locale)
        return when {
            u.startsWith("BET ") && u.contains(" AND ") -> { val (a, b) = s.substring(4).split(Regex("(?i) AND "), limit = 2); Texte.t(Res.string.date_text_between, p(a), p(b)) }
            u.startsWith("FROM ") && u.contains(" TO ") -> { val (a, b) = s.substring(5).split(Regex("(?i) TO "), limit = 2); Texte.t(Res.string.date_text_from_to, p(a), p(b)) }
            u.startsWith("FROM ") -> Texte.t(Res.string.date_text_from, p(s.substring(5)))
            u.startsWith("TO ") -> Texte.t(Res.string.date_text_to, p(s.substring(3)))
            u.startsWith("ABT ") -> Texte.t(Res.string.date_text_about, p(s.substring(4)))
            u.startsWith("EST ") -> Texte.t(Res.string.date_text_estimated, p(s.substring(4)))
            u.startsWith("CAL ") -> Texte.t(Res.string.date_text_calculated, p(s.substring(4)))
            u.startsWith("BEF ") -> Texte.t(Res.string.date_text_before, p(s.substring(4)))
            u.startsWith("AFT ") -> Texte.t(Res.string.date_text_after, p(s.substring(4)))
            u.startsWith("INT ") -> p(s.substring(4).substringBefore('(')).ifBlank { gedcom.trim() }
            u.startsWith("(") -> gedcom.trim().trim('(', ')')
            else -> p(s)
        }
    }

    private fun punkt(t: String, locale: Locale): String {
        val m = PUNKT.matchEntire(t.uppercase()) ?: return t
        val tag = m.groupValues[1].toIntOrNull()
        val monat = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { MONATE.indexOf(it) + 1 }?.takeIf { it > 0 }
        val jahr = m.groupValues[3] + (m.groupValues[4].takeIf { it.isNotEmpty() }?.let { "/$it" } ?: "") + (if (m.groupValues[5].isNotEmpty()) " v. Chr." else "")
        if (m.groupValues[2].isNotEmpty() && monat == null) return t
        val monatName = monat?.let { Month.of(it).getDisplayName(TextStyle.FULL, locale) }
        return when {
            tag != null && monatName != null -> Texte.t(Res.string.date_text_day, tag, monatName, jahr)
            monatName != null -> Texte.t(Res.string.date_text_month, monatName, jahr)
            else -> jahr
        }
    }
}

/** Zeichensatz einer GEDCOM-Datei: Byte-Reihenfolge-Marke, sonst "1 CHAR" im Kopf, sonst UTF-8 mit Rueckfall auf ANSI. */
object Zeichensatz {
    fun dekodieren(b: ByteArray): Pair<String, String> {
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) return String(b, 3, b.size - 3, Charsets.UTF_8) to "UTF-8"
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16LE) to "UTF-16"
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16BE) to "UTF-16"
        // UTF-16 ohne Marke: jedes zweite Byte 0
        if (b.size >= 4 && b[1] == 0.toByte() && b[3] == 0.toByte() && b[0] == '0'.code.toByte()) return String(b, Charsets.UTF_16LE) to "UTF-16"
        if (b.size >= 4 && b[0] == 0.toByte() && b[2] == 0.toByte() && b[1] == '0'.code.toByte()) return String(b, Charsets.UTF_16BE) to "UTF-16"
        val kopf = String(b, 0, minOf(b.size, 4000), Charsets.ISO_8859_1)
        val angabe = Regex("(?m)^\\s*1 CHAR\\s+([A-Za-z0-9_-]+)").find(kopf)?.groupValues?.get(1)?.uppercase()
        return when (angabe) {
            "ANSEL" -> ansel(b) to "ANSEL"
            "ANSI", "CP1252", "WINDOWS-1252", "WIN1252" -> String(b, charset("windows-1252")) to "ANSI"
            "ASCII", "IBMPC", "IBM-PC", "MACINTOSH", "MAC" -> String(b, if (angabe.startsWith("MAC")) charset("x-MacRoman") else Charsets.ISO_8859_1) to angabe
            "UTF-8", "UTF8", "UNICODE" -> utf8OderAnsi(b)
            else -> utf8OderAnsi(b)
        }
    }

    /** Ohne (oder mit falscher) Angabe: UTF-8, wenn es sauber dekodiert, sonst Windows-1252. */
    private fun utf8OderAnsi(b: ByteArray): Pair<String, String> {
        val dec = Charsets.UTF_8.newDecoder()
        return runCatching { dec.decode(java.nio.ByteBuffer.wrap(b)).toString() to "UTF-8" }
            .getOrElse { String(b, charset("windows-1252")) to "ANSI" }
    }

    // ANSEL (MARC-8): Sonderzeichen 0xA1..0xCF, kombinierende Zeichen 0xE0..0xFE stehen VOR dem Grundbuchstaben
    private val SONDER = mapOf(
        0xA1 to 'Ł', 0xA2 to 'Ø', 0xA3 to 'Đ', 0xA4 to 'Þ', 0xA5 to 'Æ', 0xA6 to 'Œ', 0xA7 to 'ʹ', 0xA8 to '·', 0xA9 to '♭',
        0xAA to '®', 0xAB to '±', 0xAC to 'Ơ', 0xAD to 'Ư', 0xAE to 'ʼ', 0xB0 to 'ʻ', 0xB1 to 'ł', 0xB2 to 'ø', 0xB3 to 'đ',
        0xB4 to 'þ', 0xB5 to 'æ', 0xB6 to 'œ', 0xB7 to 'ʺ', 0xB8 to 'ı', 0xB9 to '£', 0xBA to 'ð', 0xBC to 'ơ', 0xBD to 'ư',
        0xC0 to '°', 0xC1 to 'ℓ', 0xC2 to '℗', 0xC3 to '©', 0xC4 to '♯', 0xC5 to '¿', 0xC6 to '¡', 0xC7 to 'ß', 0xC8 to '€', 0xCF to 'ß',
    )
    private val KOMBI = mapOf(
        0xE0 to '̉', 0xE1 to '̀', 0xE2 to '́', 0xE3 to '̂', 0xE4 to '̃', 0xE5 to '̄', 0xE6 to '̆',
        0xE7 to '̇', 0xE8 to '̈', 0xE9 to '̌', 0xEA to '̊', 0xEB to '︠', 0xEC to '︡', 0xED to '̕',
        0xEE to '̋', 0xEF to '̐', 0xF0 to '̧', 0xF1 to '̨', 0xF2 to '̣', 0xF3 to '̤', 0xF4 to '̥',
        0xF5 to '̳', 0xF6 to '̲', 0xF7 to '̦', 0xF8 to '̜', 0xF9 to '̮', 0xFA to '︢', 0xFB to '︣', 0xFE to '̓',
    )

    fun ansel(b: ByteArray): String {
        val sb = StringBuilder(b.size)
        val offen = StringBuilder()
        for (byte in b) {
            val c = byte.toInt() and 0xFF
            when {
                c < 0x80 -> { sb.append(c.toChar()); if (offen.isNotEmpty()) { sb.append(offen); offen.setLength(0) } }
                c in KOMBI -> offen.append(KOMBI.getValue(c))
                c in SONDER -> { sb.append(SONDER.getValue(c)); if (offen.isNotEmpty()) { sb.append(offen); offen.setLength(0) } }
                else -> sb.append('�')
            }
        }
        if (offen.isNotEmpty()) sb.append(offen)
        return Normalizer.normalize(sb, Normalizer.Form.NFC)
    }
}
