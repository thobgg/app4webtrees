package de.bgghome.webtrees.nativ.desk

import org.apache.pdfbox.pdmodel.PDDocument
import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.DateJson
import de.bgghome.webtrees.nativ.data.notizenOhnePaten
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.res.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.Request
import java.awt.Color
import java.awt.image.BufferedImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO

/*
 * Buecher (26.09.2026): ein neutrales Dokument (Titelblatt, Ueberschriften, Absaetze mit Hervorhebungen und Verweisen,
 * Verzeichnisse), aus dem PDF, DOCX, HTML, TeX und Text entstehen (BuchPdf.kt, BuchFormate.kt).
 * Die Eintraege folgen dem Stil der Ortsfamilienbuecher: * Geburt, ~ Taufe, † Tod, ▭ Begraebnis, ∞ Heirat, Quellen in
 * Klammern; die Verzeichnisse verweisen auf Eintragsnummern (Kekule-Nummern), nicht auf Seiten.
 */

enum class Stil { Normal, Fett, Kursiv }

/** Ein Stueck Text; [ziel]: Anker eines anderen Eintrags (wird Verweis bzw. Link). */
data class Lauf(val text: String, val stil: Stil = Stil.Normal, val ziel: String? = null)

sealed interface Block

data class Titelblatt(val titel: String, val untertitel: String, val zeile: String, val bild: BufferedImage?) : Block

/** Ueberschrift, erscheint im Inhaltsverzeichnis. */
data class Ueberschrift(val text: String, val id: String, val neueSeite: Boolean = true) : Block

/**
 * Absatz. [marke]: Nummer am linken Rand (haengend); [anker]: Ziel fuer Verweise; [bild]: Portraet rechts neben dem
 * Text; [farbe]: Farbbalken links (Grosseltern-Linie); [einzug]: Stufen nach rechts.
 */
data class Absatz(
    val laeufe: List<Lauf>, val marke: String? = null, val anker: String? = null, val bild: BufferedImage? = null,
    val farbe: Color? = null, val einzug: Int = 0, val abstandVor: Boolean = false,
) : Block

data object Inhaltsverzeichnis : Block

/** Verzeichnis: Gruppen (Buchstabe, Ort) mit Zeilen Text -> Eintragsnummern. */
data class Verzeichnis(
    val titel: String, val id: String, val gruppen: List<Pair<String, List<Pair<String, List<Long>>>>>, val spalten: Int,
    /** Angezeigte Nummer je Eintragsschluessel (Nachfahrenbuch: "1.2.3"); null = die Zahl selbst, Folgen als "3–5". */
    val etiketten: Map<Long, String>? = null,
) : Block {
    fun nummern(n: List<Long>): String = etiketten?.let { e -> n.distinct().joinToString(", ") { e[it] ?: "$it" } } ?: nummernText(n)
    fun etikett(n: Long): String = etiketten?.get(n) ?: "$n"
}

/** [ausklapp]: Tafel als Ausklappseite am Ende des PDF (E5), erst beim Setzen erzeugt. */
class Buch(val titel: String, val kopfzeile: String, val bloecke: List<Block>, val fuss: String, val ausklapp: (() -> PDDocument)? = null)

data class BuchOptionen(
    val generationen: Int = 7,
    val bilder: Boolean = true,
    val farbkodierung: Boolean = true,
    val notizen: Boolean = true,
    val quellen: Boolean = true,
    val orteKuerzen: Boolean = true,
    /** Doppelte Vorfahren (Ahnenschwund) ganz darstellen statt nur zu verweisen. */
    val doppelteZeigen: Boolean = false,
    val titel: String = "",
    val vorwort: String = "",
    val namen: Boolean = true,
    val orte: Boolean = true,
    val berufe: Boolean = true,
    val quellenVerzeichnis: Boolean = true,
    /** Vorfahren- und Nachfahrenbuch: die Tafel dazu als Ausklappseite (A3 quer) am Ende, nur im PDF. */
    val tafel: Boolean = false,
    /** Nachfahrenbuch: Nummerierung, Ehepartner mit Kurzdaten, nur Kinder der Soehne weiterverfolgen. */
    val nummerierung: Nummerierung = Nummerierung.Saragossa,
    val partner: Boolean = true,
    val namenstraeger: Boolean = false,
    /** Familienbuch: chronologisch statt alphabetisch; Ort = Ortsfamilienbuch (leer = alle Familien). */
    val familienChronologisch: Boolean = false,
    val ortFilter: String = "",
    /** Familienbuch: Haeuserteil aus den Ortsdatensaetzen (Hoefe/Haeuser mit Art unter dem Ort), ab API-Stufe 27. */
    val haeuser: Boolean = true,
    /** Haeuserteil: nur Gebaeude nach Ortsart (Haus, Hof ...) - hoehere Ebenen wie Stadtteile gliedern nur. */
    val haeuserNurTyp: Boolean = true,
    /** Haeuserteil: Orte ohne Art mit aufnehmen (sonst im Anhang "Weitere Orte"). */
    val haeuserOhneTyp: Boolean = true,
)

// ── Formate der Angaben ──

private val MONATE = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

private val MONATSNAMEN = listOf(
    listOf("januar", "january", "jan", "janvier", "januari", "enero", "ene"),
    listOf("februar", "february", "feb", "février", "fevrier", "fév", "februari", "febrero"),
    listOf("märz", "march", "mar", "mars", "maart", "mrt", "marzo"),
    listOf("april", "apr", "avril", "avr", "abril", "abr"),
    listOf("mai", "may", "mei", "mayo"),
    listOf("juni", "june", "jun", "juin", "junio"),
    listOf("juli", "july", "jul", "juillet", "juil", "julio"),
    listOf("august", "aug", "août", "aout", "augustus", "agosto", "ago"),
    listOf("september", "sep", "sept", "septembre", "septiembre"),
    listOf("oktober", "october", "okt", "oct", "octobre", "octubre"),
    listOf("november", "nov", "novembre", "noviembre"),
    listOf("dezember", "december", "dez", "dec", "décembre", "decembre", "déc", "diciembre", "dic"),
)

/** Angezeigtes Datum ("12. Mai 1983", "May 12, 1983") in 12.05.1983 umsetzen, wenn es eindeutig ist. */
private fun ausText(t: String): String {
    val w = t.lowercase().replace(",", " ").replace(".", " ").split(Regex("\\s+")).filter(String::isNotBlank)
    val monat = w.indexOfFirst { x -> MONATSNAMEN.any { x in it } }.takeIf { it >= 0 } ?: return t
    val m = MONATSNAMEN.indexOfFirst { w[monat] in it } + 1
    val zahlen = w.filterIndexed { i, x -> i != monat && x.all(Char::isDigit) }
    val jahr = zahlen.firstOrNull { it.length == 4 } ?: return t
    val tag = zahlen.firstOrNull { it.length <= 2 }
    return if (w.size == (if (tag != null) 3 else 2)) (if (tag != null) "%02d.%02d.%s".format(tag.toInt(), m, jahr) else "%02d.%s".format(m, jahr)) else t
}

/** Datum wie in Ortsfamilienbuechern: 02.01.1812, 01.1812, um 1850; ohne GEDCOM-Form aus dem angezeigten Text. */
fun buchDatum(d: DateJson?): String {
    if (d == null) return ""
    val g = d.gedcom.trim().uppercase()
    if (g.isEmpty()) return ausText(d.text)
    val zusatz = mapOf("ABT" to "um", "EST" to "um", "CAL" to "um", "BEF" to "vor", "AFT" to "nach")
    val teile = g.split(Regex("\\s+"))
    val vor = zusatz[teile.first()]
    val rest = if (vor != null) teile.drop(1) else teile
    val kern = when {
        rest.size == 3 && rest[1] in MONATE -> "%02d.%02d.%s".format(rest[0].toIntOrNull() ?: return d.text, MONATE.indexOf(rest[1]) + 1, rest[2])
        rest.size == 2 && rest[0] in MONATE -> "%02d.%s".format(MONATE.indexOf(rest[0]) + 1, rest[1])
        rest.size == 1 && rest[0].all(Char::isDigit) -> rest[0]
        else -> return d.text
    }
    return if (vor != null) "$vor $kern" else kern
}

/** Kurznamen der Orte aus der Ortsverwaltung (voller Name in Kleinbuchstaben -> Kurzname), vor dem Buchsatz geladen. */
internal object OrtsKurznamen {
    @Volatile var je: Map<String, String> = emptyMap()

    suspend fun laden(client: de.bgghome.webtrees.nativ.api.WtClient, tree: String) {
        je = runCatching { client.placeList(tree).places.mapNotNull { p -> p.shortName?.let { p.name.lowercase() to it } }.toMap() }.getOrDefault(emptyMap())
    }
}

/** Ort fuers Buch; gekuerzt: der Kurzname aus der Ortsverwaltung, sonst der erste Teil ("Celle, Niedersachsen" -> "Celle"). */
internal fun ortText(name: String?, kurz: Boolean): String =
    name?.let { if (kurz) OrtsKurznamen.je[it.trim().lowercase()] ?: de.bgghome.webtrees.nativ.data.Ortsnamen.blatt(it) else it }.orEmpty()

private val EREIGNIS_ZEICHEN = mapOf("BIRT" to "*", "CHR" to "~", "BAPM" to "~", "DEAT" to "†", "BURI" to "▭", "CREM" to "▭")

/** Grosseltern-Linien wie in den Tafeln (Farbkodierung): Vater des Vaters blau, dessen Frau gruen, dann rot, gelb. */
private val LINIEN_FARBE = listOf(Color(0x5B, 0x8F, 0xD0), Color(0x62, 0xB0, 0x5A), Color(0xD8, 0x6A, 0x5E), Color(0xE0, 0xB8, 0x3A))

private fun linienFarbe(n: Long): Color? = reihe(n).let { g -> if (g < 2) null else LINIEN_FARBE[((n shr (g - 2)) - 4).toInt().coerceIn(0, 3)] }

private fun generationTitel(g: Int): String = Texte.t(Res.string.desk_book_generation, g + 1, when (g) {
    0 -> Texte.t(Res.string.desk_gen_root); 1 -> Texte.t(Res.string.desk_gen_parents); 2 -> Texte.t(Res.string.desk_gen_grandparents)
    3 -> Texte.t(Res.string.desk_gen_great); 4 -> Texte.t(Res.string.desk_gen_greatgreat); else -> Texte.t(Res.string.desk_book_ancestors)
})

/** Nachname, Vorname - wie in Registern. */
internal fun registerName(p: Person) = listOf(p.surname, p.given).filter(String::isNotBlank).joinToString(", ").ifBlank { p.name.ifBlank { "?" } }

// ── Gemeinsame Bausteine ──

/** Sammelt die Verzeichnisse: Name, Ort (mit Nachnamen), Beruf, Quelle -> Eintragsschluessel. */
internal class BuchRegister {
    val namen = sortedMapOf<String, MutableSet<Long>>(String.CASE_INSENSITIVE_ORDER)
    val orte = sortedMapOf<String, java.util.SortedMap<String, MutableSet<Long>>>(String.CASE_INSENSITIVE_ORDER)
    val berufe = sortedMapOf<String, MutableSet<Long>>(String.CASE_INSENSITIVE_ORDER)
    val quellen = sortedMapOf<String, MutableSet<Long>>(String.CASE_INSENSITIVE_ORDER)
    fun name(p: Person, n: Long) { namen.getOrPut(p.surname.ifBlank { "?" }) { sortedSetOf() } += n }

    fun bloecke(o: BuchOptionen, etiketten: Map<Long, String>? = null): List<Block> = buildList {
        fun nachBuchstabe(m: Map<String, Set<Long>>) = m.entries.groupBy { it.key.first().uppercaseChar().toString() }
            .map { (b, e) -> b to e.map { it.key to it.value.sorted() } }
        if (o.namen && namen.isNotEmpty()) add(Verzeichnis(Texte.t(Res.string.desk_book_index_names), "reg-namen", nachBuchstabe(namen), 2, etiketten))
        if (o.orte && orte.isNotEmpty()) add(Verzeichnis(Texte.t(Res.string.desk_book_index_places), "reg-orte",
            orte.map { (ort, n) -> ort to n.map { (name, s) -> name to s.sorted() } }, 1, etiketten))
        if (o.berufe && berufe.isNotEmpty()) add(Verzeichnis(Texte.t(Res.string.desk_book_index_occupations), "reg-berufe", nachBuchstabe(berufe), 2, etiketten))
        if (o.quellen && o.quellenVerzeichnis && quellen.isNotEmpty()) add(Verzeichnis(Texte.t(Res.string.desk_book_index_sources), "reg-quellen",
            listOf("" to quellen.map { it.key to it.value.sorted() }), 1, etiketten))
    }
}

/** Der Text zu einer Person: Name fett, Konfession, Beruf, Ereignisse mit Paten und Quellen; dazu Fakten fuer die Notizen. */
internal class PersonText(val laeufe: MutableList<Lauf>, val fakten: List<FactJson>, val ereignisse: List<FactJson>)

internal fun personText(p: Person, det: IndividualDetail, o: BuchOptionen, n: Long, reg: BuchRegister): PersonText {
    val laeufe = mutableListOf(Lauf(registerName(p), Stil.Fett))
    reg.name(p, n)
    val fakten = det.facts.filter { it.known }
    fakten.firstOrNull { it.tag == "RELI" }?.value?.takeIf(String::isNotBlank)?.let { laeufe += Lauf(", $it") }
    fakten.filter { it.tag == "OCCU" && it.value.isNotBlank() }.forEach { f ->
        laeufe += Lauf(", ${f.value}")
        // Im Verzeichnis einzelne Berufe: "Maurer (1882), später Polizeidiener (1914)" -> Maurer, Polizeidiener
        de.bgghome.webtrees.nativ.data.berufe(f.value).ifEmpty { listOf(f.value) }.forEach { reg.berufe.getOrPut(it) { sortedSetOf() } += n }
    }
    fun quelle(f: FactJson): String = if (!o.quellen || f.sources.isEmpty()) "" else
        " (${Texte.t(Res.string.desk_book_source)}: ${f.sources.joinToString("; ") { it.mitSeite() }})".also { f.sources.forEach { s -> reg.quellen.getOrPut(s.title) { sortedSetOf() } += n } }
    fun ereignis(f: FactJson): String {
        val ort = ortText(f.place?.name, o.orteKuerzen)
        if (ort.isNotBlank()) reg.orte.getOrPut(ort) { sortedMapOf(String.CASE_INSENSITIVE_ORDER) }.getOrPut(p.surname.ifBlank { "?" }) { sortedSetOf() } += n
        val zeichen = EREIGNIS_ZEICHEN[f.tag] ?: "${f.label}:"
        val wert = if (EREIGNIS_ZEICHEN.containsKey(f.tag)) "" else f.value.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
        // Paten ab API-Stufe 19 aus den verlinkten und freien Eintraegen, sonst aus der Notiz "Paten: ..."
        val paten = if (f.tag in setOf("CHR", "BAPM")) (patenZeilenText(f).takeIf { it.isNotEmpty() }?.joinToString("; ") ?: f.notes.firstOrNull { it.startsWith("Paten") })?.let { " ($it)" }.orEmpty() else ""
        return listOf("$zeichen$wert", buchDatum(f.date), ort).filter(String::isNotBlank).joinToString(" ") + paten + quelle(f)
    }
    // Lebensdaten in fester Reihenfolge, andere Ereignisse (Wohnort, Auswanderung ...) dazwischen
    val reihenfolge = listOf("BIRT", "CHR", "BAPM")
    val ende = listOf("DEAT", "BURI", "CREM")
    val ohne = setOf("NAME", "SEX", "RELI", "OCCU", "NOTE", "FAMS", "FAMC", "OBJE", "SOUR", "CHAN", "_UID", "RIN", "REFN", "ASSO", "_ASSO")
    val mitte = fakten.filter { it.tag !in reihenfolge && it.tag !in ende && it.tag !in ohne && (it.date != null || it.place != null || it.value.isNotBlank()) }
    val ereignisse = fakten.filter { it.tag in reihenfolge }.sortedBy { reihenfolge.indexOf(it.tag) } + mitte +
        fakten.filter { it.tag in ende }.sortedBy { ende.indexOf(it.tag) }
    ereignisse.filter { it.tag in reihenfolge }.forEach { f -> laeufe += Lauf(", " + ereignis(f)) }
    // Wohnort und Besitz am selben Ort zusammenziehen: "Wohnort und Besitz: von 1814 bis 1850 Klosterstrasse 5" statt
    // je Ereignis eine Angabe; Quellen und Verzeichnisse wie bei den einzelnen Ereignissen
    wohnortBesitz(mitte).forEach { gruppe ->
        if (gruppe.size == 1) { laeufe += Lauf(", " + ereignis(gruppe[0])); return@forEach }
        val f = gruppe[0]
        val ort = ortText(f.place?.name, o.orteKuerzen)
        if (ort.isNotBlank()) reg.orte.getOrPut(ort) { sortedMapOf(String.CASE_INSENSITIVE_ORDER) }.getOrPut(p.surname.ifBlank { "?" }) { sortedSetOf() } += n
        val tags = gruppe.map { it.tag }.toSet()
        val label = when {
            "RESI" in tags && "PROP" in tags -> Texte.t(Res.string.desk_book_resi_prop)
            else -> f.label
        }
        val werte = gruppe.mapNotNull { it.value.takeIf(String::isNotBlank) }.distinct().joinToString("; ")
        laeufe += Lauf(", " + listOf("$label:", zeitraumText(gruppe), ort).filter(String::isNotBlank).joinToString(" ") +
            (if (werte.isNotBlank()) " – $werte" else "") + gruppe.joinToString("") { quelle(it) })
    }
    ereignisse.filter { it.tag in ende }.forEach { f -> laeufe += Lauf(", " + ereignis(f)) }
    return PersonText(laeufe, fakten, ereignisse)
}

/**
 * Wohnort (RESI) und Besitz (PROP) am selben Ort zu einer Gruppe; alle anderen Ereignisse einzeln, Reihenfolge nach dem
 * ersten Auftreten. So wird aus zehn Eintraegen fuer dasselbe Haus eine Zeile mit Zeitraum.
 */
internal fun wohnortBesitz(fakten: List<FactJson>): List<List<FactJson>> {
    val gruppen = mutableListOf<MutableList<FactJson>>()
    val jeOrt = HashMap<String, MutableList<FactJson>>()
    fakten.forEach { f ->
        val ort = f.place?.name?.trim()?.lowercase()
        if ((f.tag == "RESI" || f.tag == "PROP") && !ort.isNullOrEmpty()) {
            jeOrt[ort]?.let { it += f } ?: mutableListOf(f).also { jeOrt[ort] = it; gruppen += it }
        } else gruppen += mutableListOf(f)
    }
    return gruppen
}

/** "von 1814 bis 1850" aus den Daten einer Gruppe: gleiche Angabe einmal, sonst fruehestes bis spaetestes Jahr. */
internal fun zeitraumText(gruppe: List<FactJson>): String {
    val daten = gruppe.mapNotNull { it.date }
    if (daten.isEmpty()) return ""
    val texte = daten.map { buchDatum(it) }.filter(String::isNotBlank).distinct()
    if (texte.size <= 1) return texte.firstOrNull().orEmpty()
    val jahre = daten.flatMap { d -> Regex("\\d{4}").findAll(d.gedcom.ifBlank { d.text }).map { it.value.toInt() }.toList() }
    return if (jahre.isEmpty()) texte.joinToString(", ") else "${Texte.t(Res.string.desk_book_from)} ${jahre.min()} ${Texte.t(Res.string.desk_book_to)} ${jahre.max()}"
}

/** Notizen: Leerzeile = neuer Absatz, einfacher Zeilenumbruch = Leerzeichen (api4webtrees ab 1.9.1 liefert beide). */
internal fun notizBloecke(t: PersonText, o: BuchOptionen): List<Block> = if (!o.notizen) emptyList() else buildList {
    fun notiz(text: String) = text.split(Regex("\\n\\s*\\n")).map { it.trim().replace(Regex("\\s*\\n\\s*"), " ") }.filter(String::isNotBlank)
        .forEach { add(Absatz(listOf(Lauf(it, Stil.Kursiv)), einzug = 1)) }
    t.fakten.filter { it.tag == "NOTE" && it.value.isNotBlank() }.forEach { notiz(it.value) }
    t.ereignisse.flatMap { f -> f.notizenOhnePaten().filter { !it.startsWith("Paten") } }.forEach(::notiz)
}

/** Kurzdaten fuer Partner und Kinder: "* 1839 Celle, † 1912 Celle". */
internal fun kurzdaten(p: Person, o: BuchOptionen): String = listOfNotNull(
    p.birth?.let { e -> listOf(buchDatum(e.date), ortText(e.place?.name, o.orteKuerzen)).filter(String::isNotBlank).joinToString(" ").takeIf(String::isNotBlank)?.let { "* $it" } },
    p.death?.let { e -> listOf(buchDatum(e.date), ortText(e.place?.name, o.orteKuerzen)).filter(String::isNotBlank).joinToString(" ").takeIf(String::isNotBlank)?.let { "† $it" } },
).joinToString(", ")

internal fun geschlechtZeichen(p: Person) = when (p.sex) { "M" -> "♂"; "F" -> "♀"; else -> "" }

// ── Vorfahrenbuch ──

class BuchDaten(val ahnen: Map<Long, AhnenEintrag>, val details: Map<Long, IndividualDetail>, val bilder: Map<String, BufferedImage>)

suspend fun vorfahrenbuchLaden(client: WtClient, tree: String, xref: String, generationen: Int, bilder: Boolean, fortschritt: (String) -> Unit = {}, abbruch: () -> Boolean = { false }): BuchDaten = coroutineScope {
    // Ganzer Baum aus Zwischenspeicher oder Export (ab Stufe 17), wenn sich das lohnt - sonst Person fuer Person
    val baum = BaumSpeicher.holen(client, tree, (1 shl generationen.coerceAtMost(20)) - 1) { g, t -> fortschritt(Texte.t(Res.string.desk_book_progress_tree, g, t)) }
    val ahnen = if (baum != null) ahnenAusBaum(baum, xref, generationen) else ahnenLaden(client, tree, xref, generationen)
    // Jede Person einmal abrufen (Ahnenschwund: dieselbe Person unter mehreren Nummern)
    val xrefs = ahnen.values.map { it.person.xref }.filter(String::isNotEmpty).distinct()
    val details = if (baum != null) xrefs.mapNotNull { x -> baum.detail(x)?.let { x to it } }.toMap() else xrefs.chunked(8).flatMap { gruppe ->
        gruppe.map { x -> async { x to runCatching { client.individual(tree, x) }.getOrNull() } }.awaitAll()
    }.mapNotNull { (x, d) -> d?.let { x to it } }.toMap()
    fortschritt(Texte.t(Res.string.desk_book_progress_persons, details.size))
    // Ein Portraet je Person, ueber den Bildspeicher (Medienordner auf dem PC, Zwischenspeicher, Server)
    val fotos = if (!bilder) emptyMap() else Bilder.alle(client, client.baseUrl, tree,
        ahnen.values.mapNotNull { a -> a.person.thumb?.let { Bilder.Quelle(it, details[a.person.xref]?.media?.firstOrNull { m -> m.isImage }?.path) } }, fortschritt, abbruch)
    BuchDaten(ahnen, ahnen.entries.mapNotNull { (n, a) -> details[a.person.xref]?.let { n to it } }.toMap(), fotos)
}

/** Das Vorfahrenbuch als Dokument. [baum]: Titel des Stammbaums, [app]: wtTux/wtWin. */
fun vorfahrenbuch(d: BuchDaten, o: BuchOptionen, baum: String, app: String): Buch {
    val proband = d.ahnen[1L]?.person ?: return Buch("", "", emptyList(), "")
    val nummern = d.ahnen.keys.filter { reihe(it) < o.generationen }.sorted()
    val jahre = nummern.mapNotNull { d.ahnen.getValue(it).person.birth?.date?.year?.takeIf { y -> y > 0 } }
    val titel = o.titel.ifBlank { Texte.t(Res.string.desk_book_title_ancestors, proband.name) }
    val erste = HashMap<String, Long>()
    nummern.forEach { n -> d.ahnen.getValue(n).person.xref.takeIf(String::isNotEmpty)?.let { erste.putIfAbsent(it, n) } }
    val reg = BuchRegister()

    fun eintrag(n: Long): List<Block> {
        val a = d.ahnen.getValue(n)
        val p = a.person
        val anker = "n$n"
        val schon = erste[p.xref]?.takeIf { it != n }
        if (schon != null && !o.doppelteZeigen) {
            return listOf(Absatz(listOf(Lauf(registerName(p), Stil.Fett), Lauf(" – " + Texte.t(Res.string.desk_book_see) + " "), Lauf("$schon", ziel = "n$schon")),
                marke = "$n", anker = anker, farbe = if (o.farbkodierung) linienFarbe(n) else null, abstandVor = true))
        }
        val det = d.details[n]
        if (p.isPrivate || det == null) {
            reg.name(p, n)
            val laeufe = mutableListOf(Lauf(registerName(p), Stil.Fett))
            if (p.isPrivate) laeufe += Lauf(", " + Texte.t(Res.string.person_private))
            return listOf(Absatz(laeufe, marke = "$n", anker = anker, abstandVor = true))
        }
        val text = personText(p, det, o, n, reg)
        val laeufe = text.laeufe
        // Eltern im Buch
        val eltern = listOf(2 * n, 2 * n + 1).filter { it in d.ahnen && reihe(it) < o.generationen }
        val bloecke = mutableListOf<Block>()
        val bild = if (o.bilder) p.thumb?.let { d.bilder[it] } else null
        bloecke += Absatz(laeufe, marke = "$n", anker = anker, bild = bild, farbe = if (o.farbkodierung) linienFarbe(n) else null, abstandVor = true)
        val zusatz = mutableListOf<Lauf>()
        if (eltern.isNotEmpty()) {
            zusatz += Lauf(Texte.t(Res.string.desk_book_parents) + " ")
            eltern.forEachIndexed { i, e -> if (i > 0) zusatz += Lauf(", "); zusatz += Lauf("$e", ziel = "n$e") }
        }
        // Ehe mit dem anderen Elternteil des Kindes (beim Vater; bei der Mutter nur der Verweis)
        if (n > 1) {
            val partnerNr = n xor 1L
            val partner = d.ahnen[partnerNr]?.person
            val fam = det.spouseFamilies.firstOrNull { it.spouse?.xref == partner?.xref && partner != null }
            if (partner != null) {
                if (zusatz.isNotEmpty()) zusatz += Lauf("; ")
                zusatz += Lauf("∞ " + listOf(buchDatum(fam?.marriage?.date), ortText(fam?.marriage?.place?.name, o.orteKuerzen)).filter(String::isNotBlank).joinToString(" ").let { if (it.isNotBlank()) "$it " else "" } +
                    Texte.t(Res.string.desk_book_with) + " ")
                zusatz += Lauf("$partnerNr", ziel = "n$partnerNr")
            }
            // Kinder beim Vater (gerade Nummer), das Kind der Linie mit Verweis
            if (n % 2 == 0L && fam != null && fam.children.isNotEmpty()) {
                val linie = d.ahnen[n / 2]?.person?.xref
                zusatz += Lauf("; " + Texte.t(Res.string.desk_book_children) + " ")
                fam.children.forEachIndexed { i, c ->
                    if (i > 0) zusatz += Lauf(", ")
                    val zeichen = geschlechtZeichen(c)
                    zusatz += Lauf("${c.given.ifBlank { c.name }} $zeichen" + (c.birth?.date?.year?.takeIf { it > 0 }?.let { " ($it)" } ?: ""))
                    if (c.xref == linie) { zusatz += Lauf(" → "); zusatz += Lauf("${n / 2}", ziel = "n${n / 2}") }
                }
            }
        }
        if (zusatz.isNotEmpty()) bloecke += Absatz(zusatz, einzug = 1)
        bloecke += notizBloecke(text, o)
        return bloecke
    }

    val bloecke = mutableListOf<Block>()
    val datum = LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
    bloecke += Titelblatt(titel, if (jahre.isNotEmpty()) "${jahre.min()} – ${jahre.max()}" else "", Texte.t(Res.string.desk_book_date, datum),
        if (o.bilder) proband.thumb?.let { d.bilder[it] } else null)
    bloecke += Inhaltsverzeichnis
    if (o.vorwort.isNotBlank()) {
        bloecke += Ueberschrift(Texte.t(Res.string.desk_book_preface), "vorwort")
        o.vorwort.split(Regex("\\n\\s*\\n")).forEach { bloecke += Absatz(listOf(Lauf(it.trim().replace('\n', ' ')))) }
    }
    nummern.groupBy(::reihe).forEach { (g, liste) ->
        // Proband, Eltern und Grosseltern teilen sich eine Seite; ab den Urgrosseltern beginnt jede Generation neu
        bloecke += Ueberschrift(generationTitel(g), "g$g", neueSeite = g == 0 || liste.size > 4)
        liste.forEach { bloecke += eintrag(it) }
    }
    bloecke += reg.bloecke(o)
    // Ausklappseite: die Ahnentafel ueber hoechstens sechs Generationen, mit Kekule-Nummern und Ahnenschwund
    val tafel: (() -> PDDocument)? = if (!o.tafel) null else { {
        val g = minOf(o.generationen, 6)
        tafelPdf(TafelInhalt(vorfahren = ahnenBaum(d.ahnen, g, true)), TafelOptionen(generationen = g, titel = titel, bilder = o.bilder, legende = true),
            { p -> p.thumb?.let { d.bilder[it] } }, Texte.t(Res.string.person_private), fusszeile(app, baum)).first
    } }
    return Buch(titel, titel, bloecke, fusszeile(app, baum), tafel)
}

/** Nummern zusammenfassen: 3, 4, 5, 9 -> "3–5, 9". */
fun nummernText(n: List<Long>): String {
    val s = n.distinct().sorted()
    val teile = mutableListOf<String>()
    var i = 0
    while (i < s.size) {
        var j = i
        while (j + 1 < s.size && s[j + 1] == s[j] + 1) j++
        teile += if (j - i >= 2) "${s[i]}–${s[j]}" else (i..j).joinToString(", ") { "${s[it]}" }
        i = j + 1
    }
    return teile.joinToString(", ")
}
