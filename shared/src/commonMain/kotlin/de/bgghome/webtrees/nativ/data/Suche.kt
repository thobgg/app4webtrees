package de.bgghome.webtrees.nativ.data

import de.bgghome.webtrees.nativ.api.ExportIndividual
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.FactRequest
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.TreeExport

/*
 * Suche ueber alle Felder des Baums, ohne Server: Grundlage ist der Baum-Export (derselbe wie fuer Personentabelle,
 * Pruefung und Buecher). Dazu die Suche nach Kennzeichen (Kennung, REFN, UID) und die Vorschau fuers Ersetzen.
 */

/** Felder, in denen gesucht wird. */
enum class SuchFeld { Namen, Beruf, Daten, Orte, Quellen, Notizen, Eltern, Partner, Geschwister, Kinder }

/** Wie der Suchtext gelesen wird: als Ganzes, alle Woerter (UND) oder eines der Woerter (ODER). */
enum class SuchArt { Genau, AlleWoerter, EinWort }

data class SuchAnfrage(
    val text: String,
    val art: SuchArt = SuchArt.Genau,
    val felder: Set<SuchFeld> = SuchFeld.entries.toSet(),
    /** Der Suchtext muss das ganze Feld sein, nicht nur ein Teil davon. */
    val ganzesFeld: Boolean = false,
    val grossKlein: Boolean = false,
    /** Koelner Phonetik: Meyer findet Maier und Mayer. */
    val phonetisch: Boolean = false,
)

/** Eine Fundstelle: das Feld und sein Text (bei Familienereignissen mit dem Partner davor). */
data class Fundstelle(val feld: SuchFeld, val text: String)

data class SuchTreffer(val person: Person, val fundstellen: List<Fundstelle>)

/** Kennzeichen-Suche: welche Kennung und wo der Text darin steht. */
enum class KennArt { Kennung, Refn, Uid }
enum class KennLage { Beliebig, Anfang, Ende, Komplett }

/** Felder, in denen ersetzt werden darf: Orte, Beruf, Notizen und die Werte sonstiger Ereignisse (nicht Namen). */
enum class ErsetzFeld { Orte, Beruf, Notizen, Werte }

/**
 * Eine Stelle, an der ersetzt wuerde: [record] ist die Kennung des Datensatzes, an dem das Ereignis haengt (Person oder
 * Familie), [person] die Person, unter der es angezeigt wird.
 */
data class Ersetzung(val record: String, val person: Person, val fact: FactJson, val feld: ErsetzFeld, val alt: String, val neu: String) {
    fun anfrage(): FactRequest = when (feld) {
        ErsetzFeld.Orte -> FactRequest(factId = fact.id, tag = fact.tag, place = neu)
        ErsetzFeld.Notizen -> FactRequest(factId = fact.id, tag = fact.tag, note = neu)
        else -> FactRequest(factId = fact.id, tag = fact.tag, value = neu)
    }
}

object Suche {

    /** Alle Texte einer Person, nach Feldern - die Grundlage fuer jede Suche. */
    fun felder(baum: TreeExport, i: ExportIndividual): List<Fundstelle> {
        val p = i.person
        val aus = mutableListOf<Fundstelle>()
        fun add(feld: SuchFeld, text: String?) { if (!text.isNullOrBlank()) aus += Fundstelle(feld, text.trim()) }
        add(SuchFeld.Namen, p.name)
        if (p.call.isNotBlank() && p.call !in p.name) add(SuchFeld.Namen, p.call)
        i.facts.filter { it.tag == "NAME" && it.value.isNotBlank() }.map { it.value.replace("/", "") }.filter { it != p.name }.forEach { add(SuchFeld.Namen, it) }
        i.facts.filter { it.tag == "NICK" }.forEach { add(SuchFeld.Namen, it.value) }
        add(SuchFeld.Beruf, p.occupation)
        i.facts.filter { it.tag == "OCCU" || it.tag == "TITL" }.map { it.value }.filter { it != p.occupation }.forEach { add(SuchFeld.Beruf, it) }
        fun ereignis(f: FactJson, vor: String) {
            f.date?.text?.let { add(SuchFeld.Daten, vor + it) }
            f.place?.name?.let { add(SuchFeld.Orte, vor + it) }
            f.sources.forEach { s -> add(SuchFeld.Quellen, vor + listOf(s.title, s.page, s.text).filter(String::isNotBlank).joinToString(", ")) }
            f.notes.forEach { add(SuchFeld.Notizen, vor + it) }
        }
        i.facts.forEach { ereignis(it, "") }
        // Familienereignisse (Heirat, Scheidung ...) mit dem Partner davor
        val fams = i.fams.mapNotNull { baum.families[it] }
        fams.forEach { f ->
            val partner = baum.person(if (f.husband == p.xref) f.wife else f.husband)
            val vor = partner?.let { "${it.name}: " } ?: ""
            f.facts.forEach { ereignis(it, vor) }
            partner?.let { add(SuchFeld.Partner, it.name) }
            f.children.mapNotNull(baum::person).forEach { add(SuchFeld.Kinder, it.name) }
        }
        val eltern = i.famc.mapNotNull { baum.families[it] }
        eltern.forEach { f ->
            listOfNotNull(baum.person(f.husband), baum.person(f.wife)).forEach { add(SuchFeld.Eltern, it.name) }
            f.children.filter { it != p.xref }.mapNotNull(baum::person).forEach { add(SuchFeld.Geschwister, it.name) }
        }
        return aus
    }

    /** Die Suche: Treffer nach Sortiername; [nurIn] beschraenkt auf vorige Treffer (geschlossene Gruppe). */
    fun suchen(baum: TreeExport, a: SuchAnfrage, nurIn: Set<String>? = null): List<SuchTreffer> {
        val worte = a.text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (worte.isEmpty()) return emptyList()
        val ganz = a.text.trim()
        fun norm(s: String) = if (a.grossKlein) s else s.lowercase()
        // Ein Suchwort passt auf einen Feldtext: als Teil, als ganzes Feld oder phonetisch Wort fuer Wort
        fun passt(wort: String, feld: String): Boolean = when {
            a.phonetisch -> { val k = koelner(wort); k.isNotEmpty() && feld.split(Regex("[\\s,;/()\\-]+")).any { koelner(it) == k } }
            a.ganzesFeld -> norm(feld) == norm(wort)
            else -> norm(feld).contains(norm(wort))
        }
        val aus = ArrayList<SuchTreffer>()
        for (i in baum.individuals.values) {
            if (i.person.isPrivate) continue
            if (nurIn != null && i.person.xref !in nurIn) continue
            val texte = felder(baum, i).filter { it.feld in a.felder }
            if (texte.isEmpty()) continue
            val fund: List<Fundstelle> = when (a.art) {
                SuchArt.Genau -> if (a.phonetisch) {
                    if (worte.all { w -> texte.any { passt(w, it.text) } }) texte.filter { f -> worte.any { passt(it, f.text) } } else emptyList()
                } else texte.filter { passt(ganz, it.text) }
                SuchArt.AlleWoerter -> if (worte.all { w -> texte.any { passt(w, it.text) } }) texte.filter { f -> worte.any { passt(it, f.text) } } else emptyList()
                SuchArt.EinWort -> texte.filter { f -> worte.any { passt(it, f.text) } }
            }
            if (fund.isNotEmpty()) aus += SuchTreffer(i.person, fund)
        }
        return aus.sortedWith(compareBy({ it.person.sortName.ifBlank { it.person.name } }, { it.person.xref }))
    }

    /** Suche nach Kennzeichen: Kennung (I123), REFN oder _UID; Ziffern allein finden auch "I123". */
    fun kennzeichen(baum: TreeExport, text: String, art: KennArt, lage: KennLage, nurIn: Set<String>? = null): List<SuchTreffer> {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return emptyList()
        fun passt(wert: String): Boolean {
            val w = wert.lowercase()
            return when (lage) {
                KennLage.Beliebig -> w.contains(t)
                KennLage.Anfang -> w.startsWith(t)
                KennLage.Ende -> w.endsWith(t)
                KennLage.Komplett -> w == t || (art == KennArt.Kennung && w.trimStart('i', 'f') == t)
            }
        }
        val aus = ArrayList<SuchTreffer>()
        for (i in baum.individuals.values) {
            if (i.person.isPrivate) continue
            if (nurIn != null && i.person.xref !in nurIn) continue
            val werte = when (art) {
                KennArt.Kennung -> listOf(i.person.xref)
                KennArt.Refn -> i.facts.filter { it.tag == "REFN" }.map { it.value }
                KennArt.Uid -> i.facts.filter { it.tag == "_UID" || it.tag == "UID" }.map { it.value }
            }
            val fund = werte.filter { it.isNotBlank() && passt(it) }.map { Fundstelle(SuchFeld.Namen, it) }
            if (fund.isNotEmpty()) aus += SuchTreffer(i.person, fund)
        }
        return aus.sortedWith(compareBy({ it.person.sortName.ifBlank { it.person.name } }, { it.person.xref }))
    }

    /** Vorschau fuers Ersetzen: jede Stelle mit altem und neuem Text; geschrieben wird erst, was der Nutzer bestaetigt. */
    fun ersetzenVorschau(baum: TreeExport, suchen: String, ersetzen: String, felder: Set<ErsetzFeld>, ganzesFeld: Boolean, grossKlein: Boolean,
        nurIn: Set<String>? = null): List<Ersetzung> {
        val s = suchen.trim()
        if (s.isEmpty() || felder.isEmpty()) return emptyList()
        fun neu(alt: String): String? = when {
            ganzesFeld -> if (alt.trim().equals(s, ignoreCase = !grossKlein)) ersetzen.trim() else null
            else -> alt.replace(s, ersetzen, ignoreCase = !grossKlein).takeIf { it != alt }
        }
        val aus = ArrayList<Ersetzung>()
        fun fakt(record: String, person: Person, f: FactJson) {
            if (ErsetzFeld.Orte in felder) f.place?.name?.let { alt -> neu(alt)?.let { aus += Ersetzung(record, person, f, ErsetzFeld.Orte, alt, it) } }
            if (f.tag == "OCCU" || f.tag == "TITL") {
                if (ErsetzFeld.Beruf in felder) neu(f.value)?.let { aus += Ersetzung(record, person, f, ErsetzFeld.Beruf, f.value, it) }
            } else if (ErsetzFeld.Werte in felder && f.tag !in OHNE_WERT && f.value.isNotBlank()) {
                neu(f.value)?.let { aus += Ersetzung(record, person, f, ErsetzFeld.Werte, f.value, it) }
            }
            // Notizen nur, wenn das Ereignis genau eine eingebettete Notiz hat - sonst waere unklar, welche geschrieben wird
            if (ErsetzFeld.Notizen in felder && f.notes.size == 1 && f.noteXref == null && f.noteKinds.none { it == "associates" }) {
                neu(f.notes[0])?.let { aus += Ersetzung(record, person, f, ErsetzFeld.Notizen, f.notes[0], it) }
            }
        }
        val familien = HashSet<String>()
        for (i in baum.individuals.values) {
            if (i.person.isPrivate) continue
            if (nurIn != null && i.person.xref !in nurIn) continue
            i.facts.forEach { fakt(i.person.xref, i.person, it) }
            i.fams.mapNotNull { baum.families[it] }.filter { familien.add(it.xref) }.forEach { f -> f.facts.forEach { fakt(f.xref, i.person, it) } }
        }
        return aus
    }

    /** Ereignisse, deren Wert nie ersetzt wird: Namen, Geschlecht, Verweise, Kennzeichen. */
    private val OHNE_WERT = setOf("NAME", "SEX", "FAMC", "FAMS", "REFN", "_UID", "UID", "RIN", "CHAN", "OBJE", "SOUR", "NOTE", "ASSO", "ALIA", "ANCI", "DESI", "SUBM")

    /**
     * Koelner Phonetik (Postel 1969): Buchstaben zu Ziffern nach Lautgruppen, Doppelte und Nullen (ausser am Anfang) fallen weg.
     * "Meyer", "Maier" und "Mayer" werden alle zu "67".
     */
    fun koelner(wort: String): String {
        val w = wort.uppercase().replace("Ä", "A").replace("Ö", "O").replace("Ü", "U").replace("ß", "S").replace("SS", "S")
            .filter { it in 'A'..'Z' }
        if (w.isEmpty()) return ""
        val roh = StringBuilder()
        for (i in w.indices) {
            val c = w[i]; val vor = w.getOrNull(i - 1); val nach = w.getOrNull(i + 1)
            val code = when (c) {
                'A', 'E', 'I', 'J', 'O', 'U', 'Y' -> '0'
                'H' -> '-'
                'B' -> '1'
                'P' -> if (nach == 'H') '3' else '1'
                'D', 'T' -> if (nach in listOf('C', 'S', 'Z')) '8' else '2'
                'F', 'V', 'W' -> '3'
                'G', 'K', 'Q' -> '4'
                'C' -> when {
                    i == 0 -> if (nach in listOf('A', 'H', 'K', 'L', 'O', 'Q', 'R', 'U', 'X')) '4' else '8'
                    vor in listOf('S', 'Z') -> '8'
                    nach in listOf('A', 'H', 'K', 'O', 'Q', 'U', 'X') -> '4'
                    else -> '8'
                }
                'X' -> if (vor in listOf('C', 'K', 'Q')) '8' else '4'
                'L' -> '5'
                'M', 'N' -> '6'
                'R' -> '7'
                'S', 'Z' -> '8'
                else -> '-'
            }
            if (code != '-') roh.append(code)
            if (c == 'X' && vor !in listOf('C', 'K', 'Q')) roh.append('8')
        }
        val aus = StringBuilder()
        for (i in roh.indices) {
            val z = roh[i]
            if (i > 0 && z == roh[i - 1]) continue
            if (z == '0' && i > 0) continue
            aus.append(z)
        }
        return aus.toString()
    }

    /** Suchliste als Text: eine Kennung je Zeile, dahinter der Name als Hinweis. */
    fun listeText(treffer: List<SuchTreffer>): String = treffer.joinToString("\n") { "${it.person.xref}\t${it.person.name}" } + "\n"

    /** Suchliste lesen: nur die Kennungen zaehlen, Personen kommen aus dem aktuellen Baum. */
    fun listeLesen(text: String, baum: TreeExport): List<SuchTreffer> =
        text.lines().map { it.substringBefore('\t').trim() }.filter(String::isNotBlank).distinct()
            .mapNotNull { x -> baum.individuals[x]?.person?.takeIf { !it.isPrivate }?.let { SuchTreffer(it, emptyList()) } }
}

