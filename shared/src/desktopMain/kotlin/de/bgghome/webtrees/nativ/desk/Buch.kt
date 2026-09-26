package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.DateJson
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
data class Verzeichnis(val titel: String, val id: String, val gruppen: List<Pair<String, List<Pair<String, List<Long>>>>>, val spalten: Int) : Block

class Buch(val titel: String, val kopfzeile: String, val bloecke: List<Block>, val fuss: String)

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
)

// ── Formate der Angaben ──

private val MONATE = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

private val MONATSNAMEN = listOf(
    listOf("januar", "january", "jan"), listOf("februar", "february", "feb"), listOf("märz", "march", "mar"), listOf("april", "apr"),
    listOf("mai", "may"), listOf("juni", "june", "jun"), listOf("juli", "july", "jul"), listOf("august", "aug"),
    listOf("september", "sep"), listOf("oktober", "october", "okt", "oct"), listOf("november", "nov"), listOf("dezember", "december", "dez", "dec"),
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

private fun ortText(name: String?, kurz: Boolean): String = name?.let { if (kurz) it.substringBefore(',').trim() else it }.orEmpty()

private val EREIGNIS_ZEICHEN = mapOf("BIRT" to "*", "CHR" to "~", "BAPM" to "~", "DEAT" to "†", "BURI" to "▭", "CREM" to "▭")

/** Grosseltern-Linien wie in den Tafeln (Farbkodierung): Vater des Vaters blau, dessen Frau gruen, dann rot, gelb. */
private val LINIEN_FARBE = listOf(Color(0x5B, 0x8F, 0xD0), Color(0x62, 0xB0, 0x5A), Color(0xD8, 0x6A, 0x5E), Color(0xE0, 0xB8, 0x3A))

private fun linienFarbe(n: Long): Color? = reihe(n).let { g -> if (g < 2) null else LINIEN_FARBE[((n shr (g - 2)) - 4).toInt().coerceIn(0, 3)] }

private fun generationTitel(g: Int): String = Texte.t(Res.string.desk_book_generation, g + 1, when (g) {
    0 -> Texte.t(Res.string.desk_gen_root); 1 -> Texte.t(Res.string.desk_gen_parents); 2 -> Texte.t(Res.string.desk_gen_grandparents)
    3 -> Texte.t(Res.string.desk_gen_great); 4 -> Texte.t(Res.string.desk_gen_greatgreat); else -> Texte.t(Res.string.desk_book_ancestors)
})

/** Nachname, Vorname - wie in Registern. */
private fun registerName(p: Person) = listOf(p.surname, p.given).filter(String::isNotBlank).joinToString(", ").ifBlank { p.name.ifBlank { "?" } }

// ── Vorfahrenbuch ──

class BuchDaten(val ahnen: Map<Long, AhnenEintrag>, val details: Map<Long, IndividualDetail>, val bilder: Map<String, BufferedImage>)

suspend fun vorfahrenbuchLaden(client: WtClient, tree: String, xref: String, generationen: Int, bilder: Boolean): BuchDaten = coroutineScope {
    // Ganzer Baum aus Zwischenspeicher oder Export (ab Stufe 17), wenn sich das lohnt - sonst Person fuer Person
    val baum = BaumSpeicher.holen(client, tree, (1 shl generationen.coerceAtMost(20)) - 1)
    val ahnen = if (baum != null) ahnenAusBaum(baum, xref, generationen) else ahnenLaden(client, tree, xref, generationen)
    // Jede Person einmal abrufen (Ahnenschwund: dieselbe Person unter mehreren Nummern)
    val xrefs = ahnen.values.map { it.person.xref }.filter(String::isNotEmpty).distinct()
    val details = if (baum != null) xrefs.mapNotNull { x -> baum.detail(x)?.let { x to it } }.toMap() else xrefs.chunked(8).flatMap { gruppe ->
        gruppe.map { x -> async { x to runCatching { client.individual(tree, x) }.getOrNull() } }.awaitAll()
    }.mapNotNull { (x, d) -> d?.let { x to it } }.toMap()
    val fotos = if (!bilder) emptyMap() else ahnen.values.mapNotNull { it.person.thumb }.distinct().chunked(8).flatMap { gruppe ->
        gruppe.map { url ->
            async {
                url to runCatching {
                    client.http.newCall(Request.Builder().url(url).build()).execute().use { r -> if (r.isSuccessful) r.body?.byteStream()?.use { ImageIO.read(it) } else null }
                }.getOrNull()?.let { b -> BufferedImage(b.width, b.height, BufferedImage.TYPE_INT_RGB).also { it.createGraphics().apply { color = Color.WHITE; fillRect(0, 0, b.width, b.height); drawImage(b, 0, 0, null); dispose() } } }
            }
        }.awaitAll()
    }.mapNotNull { (u, b) -> b?.let { u to it } }.toMap()
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
    // Register sammeln: Name -> Nummern, Ort -> (Name -> Nummern), Beruf -> Nummern, Quelle -> Nummern
    val regNamen = sortedMapOf<String, MutableSet<Long>>(String.CASE_INSENSITIVE_ORDER)
    val regOrte = sortedMapOf<String, java.util.SortedMap<String, MutableSet<Long>>>(String.CASE_INSENSITIVE_ORDER)
    val regBerufe = sortedMapOf<String, MutableSet<Long>>(String.CASE_INSENSITIVE_ORDER)
    val regQuellen = sortedMapOf<String, MutableSet<Long>>(String.CASE_INSENSITIVE_ORDER)

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
        val laeufe = mutableListOf(Lauf(registerName(p), Stil.Fett))
        regNamen.getOrPut(p.surname.ifBlank { "?" }) { sortedSetOf() } += n
        if (p.isPrivate || det == null) {
            if (p.isPrivate) laeufe += Lauf(", " + Texte.t(Res.string.person_private))
            return listOf(Absatz(laeufe, marke = "$n", anker = anker, abstandVor = true))
        }
        val fakten = det.facts.filter { it.known }
        fakten.firstOrNull { it.tag == "RELI" }?.value?.takeIf(String::isNotBlank)?.let { laeufe += Lauf(", $it") }
        fakten.filter { it.tag == "OCCU" && it.value.isNotBlank() }.forEach { f ->
            laeufe += Lauf(", ${f.value}")
            regBerufe.getOrPut(f.value) { sortedSetOf() } += n
        }
        fun quelle(f: FactJson): String = if (!o.quellen || f.sources.isEmpty()) "" else
            " (${Texte.t(Res.string.desk_book_source)}: ${f.sources.joinToString("; ") { it.title }})".also { f.sources.forEach { s -> regQuellen.getOrPut(s.title) { sortedSetOf() } += n } }
        fun ereignis(f: FactJson): String {
            val ort = ortText(f.place?.name, o.orteKuerzen)
            if (ort.isNotBlank()) regOrte.getOrPut(ort) { sortedMapOf(String.CASE_INSENSITIVE_ORDER) }.getOrPut(p.surname.ifBlank { "?" }) { sortedSetOf() } += n
            val zeichen = EREIGNIS_ZEICHEN[f.tag] ?: "${f.label}:"
            val wert = if (EREIGNIS_ZEICHEN.containsKey(f.tag)) "" else f.value.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
            val paten = if (f.tag in setOf("CHR", "BAPM")) f.notes.firstOrNull { it.startsWith("Paten") }?.let { " ($it)" }.orEmpty() else ""
            return listOf("$zeichen$wert", buchDatum(f.date), ort).filter(String::isNotBlank).joinToString(" ") + paten + quelle(f)
        }
        // Lebensdaten in fester Reihenfolge, andere Ereignisse (Wohnort, Auswanderung ...) dazwischen
        val reihenfolge = listOf("BIRT", "CHR", "BAPM")
        val ende = listOf("DEAT", "BURI", "CREM")
        val ohne = setOf("NAME", "SEX", "RELI", "OCCU", "NOTE", "FAMS", "FAMC", "OBJE", "SOUR", "CHAN", "_UID", "RIN", "REFN", "ASSO", "_ASSO")
        val ereignisse = fakten.filter { it.tag in reihenfolge }.sortedBy { reihenfolge.indexOf(it.tag) } +
            fakten.filter { it.tag !in reihenfolge && it.tag !in ende && it.tag !in ohne && (it.date != null || it.place != null || it.value.isNotBlank()) } +
            fakten.filter { it.tag in ende }.sortedBy { ende.indexOf(it.tag) }
        ereignisse.forEach { f -> laeufe += Lauf(", " + ereignis(f)) }
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
                    val zeichen = when (c.sex) { "M" -> "♂"; "F" -> "♀"; else -> "" }
                    zusatz += Lauf("${c.given.ifBlank { c.name }} $zeichen" + (c.birth?.date?.year?.takeIf { it > 0 }?.let { " ($it)" } ?: ""))
                    if (c.xref == linie) { zusatz += Lauf(" → "); zusatz += Lauf("${n / 2}", ziel = "n${n / 2}") }
                }
            }
        }
        if (zusatz.isNotEmpty()) bloecke += Absatz(zusatz, einzug = 1)
        if (o.notizen) {
            // Notizen: Leerzeile = neuer Absatz, einfacher Zeilenumbruch = Leerzeichen (api4webtrees ab 1.9.1 liefert beide)
            fun notiz(t: String) = t.split(Regex("\\n\\s*\\n")).map { it.trim().replace(Regex("\\s*\\n\\s*"), " ") }.filter(String::isNotBlank)
                .forEach { bloecke += Absatz(listOf(Lauf(it, Stil.Kursiv)), einzug = 1) }
            fakten.filter { it.tag == "NOTE" && it.value.isNotBlank() }.forEach { notiz(it.value) }
            ereignisse.flatMap { f -> f.notes.filter { !it.startsWith("Paten") } }.forEach(::notiz)
        }
        return bloecke
    }

    val bloecke = mutableListOf<Block>()
    val datum = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
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
    fun gruppiertNachBuchstabe(m: Map<String, Set<Long>>) = m.entries.groupBy { it.key.first().uppercaseChar().toString() }
        .map { (b, e) -> b to e.map { it.key to it.value.sorted() } }
    if (o.namen && regNamen.isNotEmpty()) bloecke += Verzeichnis(Texte.t(Res.string.desk_book_index_names), "reg-namen", gruppiertNachBuchstabe(regNamen), 2)
    if (o.orte && regOrte.isNotEmpty()) bloecke += Verzeichnis(Texte.t(Res.string.desk_book_index_places), "reg-orte",
        regOrte.map { (ort, namen) -> ort to namen.map { (n, s) -> n to s.sorted() } }, 1)
    if (o.berufe && regBerufe.isNotEmpty()) bloecke += Verzeichnis(Texte.t(Res.string.desk_book_index_occupations), "reg-berufe", gruppiertNachBuchstabe(regBerufe), 2)
    if (o.quellen && o.quellenVerzeichnis && regQuellen.isNotEmpty()) bloecke += Verzeichnis(Texte.t(Res.string.desk_book_index_sources), "reg-quellen",
        listOf("" to regQuellen.map { it.key to it.value.sorted() }), 1)
    return Buch(titel, titel, bloecke, fusszeile(app, baum))
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
