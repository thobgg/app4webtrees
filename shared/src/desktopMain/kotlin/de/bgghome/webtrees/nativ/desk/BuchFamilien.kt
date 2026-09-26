package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.ExportFamily
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.TreeExport
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
 * Familienbuch bzw. Ortsfamilienbuch (26.09.2026): ein Eintrag je Familie, wie in gedruckten Ortsfamilienbuechern -
 * Mann mit <Nr. seiner Elternfamilie>, Heirat, Frau, Kinder mit [Nr. ihrer eigenen Familie]; Kinder ohne eigene Familie
 * stehen hier mit allen Angaben. Mit Ortsfilter nur die Familien, die mit diesem Ort verbunden sind. Braucht den
 * ganzen Baum (api4webtrees ab Stufe 17, Route Export).
 */

class FamilienDaten(val baum: TreeExport, val bilder: Map<String, BufferedImage>)

suspend fun familienbuchLaden(client: WtClient, tree: String, bilder: Boolean, fortschritt: (String) -> Unit = {}): FamilienDaten = coroutineScope {
    // Das ganze Buch braucht den ganzen Baum: "personen" so gross, dass der Export sich immer lohnt
    val baum = BaumSpeicher.holen(client, tree, Int.MAX_VALUE / 64) { g, t -> fortschritt(Texte.t(Res.string.desk_book_progress_tree, g, t)) }
        ?: throw IllegalStateException(Texte.t(Res.string.desk_book_needs_export))
    fortschritt(Texte.t(Res.string.desk_book_progress_persons, baum.individuals.size))
    // Bilder nur fuer die Eheleute (Kinder mit eigener Familie erscheinen dort)
    val fotos = if (!bilder) emptyMap() else {
        fortschritt(Texte.t(Res.string.desk_book_progress_pictures))
        baum.families.values.flatMap { listOfNotNull(it.husband, it.wife) }.distinct().mapNotNull { baum.person(it)?.thumb }.distinct().chunked(8).flatMap { gruppe ->
            gruppe.map { url ->
                async {
                    url to runCatching {
                        client.http.newCall(Request.Builder().url(url).build()).execute().use { r -> if (r.isSuccessful) r.body?.byteStream()?.use { ImageIO.read(it) } else null }
                    }.getOrNull()?.let { b -> BufferedImage(b.width, b.height, BufferedImage.TYPE_INT_RGB).also { it.createGraphics().apply { color = Color.WHITE; fillRect(0, 0, b.width, b.height); drawImage(b, 0, 0, null); dispose() } } }
                }
            }.awaitAll()
        }.mapNotNull { (u, b) -> b?.let { u to it } }.toMap()
    }
    FamilienDaten(baum, fotos)
}

/** Alle Orte, mit denen eine Familie verbunden ist: Ereignisse der Eheleute, der Familie und der Kinder. */
private fun familienOrte(b: TreeExport, f: ExportFamily): Set<String> {
    val orte = HashSet<String>()
    fun fakten(l: List<FactJson>) = l.forEach { it.place?.name?.let { n -> orte += n.lowercase() } }
    fakten(f.facts)
    (listOfNotNull(f.husband, f.wife) + f.children).forEach { x -> b.individuals[x]?.let { fakten(it.facts) } }
    return orte
}

private fun jd(p: Person?) = p?.birth?.date?.jd?.takeIf { it > 0 }

/** Das Familienbuch als Dokument. [ortFilter]: leer = alle Familien, sonst Ortsfamilienbuch (Wortanfang, erster Ortsteil). */
fun familienbuch(d: FamilienDaten, o: BuchOptionen, baumTitel: String, app: String): Buch {
    val b = d.baum
    val filter = o.ortFilter.trim().lowercase()
    val familien = b.families.values.filter { f -> !f.isPrivate && (f.husband != null || f.wife != null) }
        .filter { f -> filter.isEmpty() || familienOrte(b, f).any { ort -> ort.split(',').first().trim().startsWith(filter) } }
    fun schluesselName(f: ExportFamily): String {
        val p = b.person(f.husband) ?: b.person(f.wife)
        return listOf(p?.surname.orEmpty(), p?.given.orEmpty()).joinToString(" ").lowercase()
    }
    fun zeit(f: ExportFamily): Int = f.marriage?.date?.jd?.takeIf { it > 0 } ?: f.children.mapNotNull { jd(b.person(it)) }.minOrNull()
        ?: jd(b.person(f.husband))?.plus(9000) ?: Int.MAX_VALUE
    val sortiert = if (o.familienChronologisch) familien.sortedWith(compareBy(::zeit, ::schluesselName))
        else familien.sortedWith(compareBy(::schluesselName, ::zeit))
    val nummer = sortiert.withIndex().associate { (i, f) -> f.xref to (i + 1).toLong() }
    val reg = BuchRegister()

    fun elternNr(x: String?): Long? = x?.let { b.individuals[it]?.famc?.firstNotNullOfOrNull { f -> nummer[f] } }
    fun eigene(x: String): List<Long> = b.individuals[x]?.fams.orEmpty().mapNotNull { nummer[it] }

    /** Person mit Verweis <n> auf die Elternfamilie direkt nach dem Namen. */
    fun ehepartner(x: String?, n: Long, erste: Boolean, marke: String?, farbe: Color?): List<Block> {
        val p = b.person(x)
        if (p == null || x == null) return listOf(Absatz(listOf(Lauf("?", Stil.Fett)), marke = marke, anker = if (erste) "n$n" else null, einzug = if (erste) 0 else 1, abstandVor = erste))
        val det = b.detail(x) ?: return emptyList()
        if (p.isPrivate) {
            reg.name(p, n)
            return listOf(Absatz(listOf(Lauf(registerName(p), Stil.Fett), Lauf(", " + Texte.t(Res.string.person_private))), marke = marke, anker = if (erste) "n$n" else null, einzug = if (erste) 0 else 1, abstandVor = erste))
        }
        val text = personText(p, det, o, n, reg)
        elternNr(x)?.let { en -> text.laeufe.add(1, Lauf(" <")); text.laeufe.add(2, Lauf("$en", ziel = "n$en")); text.laeufe.add(3, Lauf(">")) }
        return listOf(Absatz(text.laeufe, marke = marke, anker = if (erste) "n$n" else null, bild = if (o.bilder) p.thumb?.let { d.bilder[it] } else null,
            farbe = farbe, einzug = if (erste) 0 else 1, abstandVor = erste)) + notizBloecke(text, o)
    }

    fun eintrag(f: ExportFamily): List<Block> {
        val n = nummer.getValue(f.xref)
        val bloecke = mutableListOf<Block>()
        val erster = f.husband ?: f.wife
        val zweiter = if (f.husband != null) f.wife else null
        bloecke += ehepartner(erster, n, true, "$n", null)
        // Heirat und Scheidung der Familie
        val ehe = f.facts.filter { it.tag in setOf("MARR", "MARB", "ENGA", "DIV") }.ifEmpty { f.marriage?.let { m -> listOf(FactJson(id = "", tag = "MARR", date = m.date, place = m.place)) }.orEmpty() }
        val eheText = ehe.joinToString(", ") { e ->
            val ort = ortText(e.place?.name, o.orteKuerzen)
            val zeichen = when (e.tag) { "DIV" -> "⚮"; "ENGA" -> "⚬"; else -> "∞" }
            val quelle = if (o.quellen && e.sources.isNotEmpty()) " (${Texte.t(Res.string.desk_book_source)}: ${e.sources.joinToString("; ") { it.title }})" else ""
            listOf(zeichen, buchDatum(e.date), ort).filter(String::isNotBlank).joinToString(" ") + quelle
        }
        // Weder Heirat noch zweiter Ehepartner bekannt (z. B. unbekannte Mutter): keine leere Zeile mit "∞" und "?"
        if (eheText.isNotBlank() || zweiter != null) {
            bloecke += Absatz(listOf(Lauf(eheText.ifBlank { "∞" })), einzug = 1)
            bloecke += ehepartner(zweiter, n, false, null, null)
        }
        f.facts.filter { it.tag == "NOTE" && it.value.isNotBlank() }.forEach { bloecke += Absatz(listOf(Lauf(it.value.replace('\n', ' '), Stil.Kursiv)), einzug = 1) }
        // Kinder: mit eigener Familie kurz und verwiesen, sonst mit allen Angaben
        f.children.mapNotNull { x -> b.person(x)?.let { x to it } }.forEachIndexed { k, (x, c) ->
            val familienDesKindes = eigene(x)
            val laeufe = mutableListOf<Lauf>()
            laeufe += Lauf("${k + 1}. ", Stil.Fett)
            if (c.isPrivate) { laeufe += Lauf(Texte.t(Res.string.person_private)); bloecke += Absatz(laeufe, einzug = 2); return@forEachIndexed }
            if (familienDesKindes.isNotEmpty()) {
                reg.name(c, n)
                laeufe += Lauf("${c.given.ifBlank { c.name }} ${geschlechtZeichen(c)}", Stil.Fett)
                kurzdaten(c, o).takeIf(String::isNotBlank)?.let { laeufe += Lauf(", $it") }
                laeufe += Lauf(" [")
                familienDesKindes.forEachIndexed { j, fn -> if (j > 0) laeufe += Lauf(", "); laeufe += Lauf("$fn", ziel = "n$fn") }
                laeufe += Lauf("]")
                bloecke += Absatz(laeufe, einzug = 2)
            } else {
                val det = b.detail(x)
                if (det == null) { laeufe += Lauf(c.given.ifBlank { c.name }, Stil.Fett); bloecke += Absatz(laeufe, einzug = 2); return@forEachIndexed }
                val text = personText(c, det, o, n, reg)
                // Im Kind-Eintrag nur der Vorname (der Nachname ist der der Familie) und das Zeichen fuer das Geschlecht
                text.laeufe[0] = Lauf("${c.given.ifBlank { c.name }} ${geschlechtZeichen(c)}", Stil.Fett)
                bloecke += Absatz(laeufe + text.laeufe, einzug = 2)
                notizBloecke(text, o).forEach { nb -> bloecke += if (nb is Absatz) nb.copy(einzug = 2) else nb }
            }
        }
        return bloecke
    }

    val ort = o.ortFilter.trim()
    val titel = o.titel.ifBlank { if (ort.isEmpty()) Texte.t(Res.string.desk_book_title_families, baumTitel) else Texte.t(Res.string.desk_book_title_ofb, ort.replaceFirstChar { it.uppercase() }) }
    val jahre = sortiert.flatMap { f -> (listOfNotNull(f.husband, f.wife) + f.children).mapNotNull { b.person(it)?.birth?.date?.year?.takeIf { y -> y > 0 } } }
    val bloecke = mutableListOf<Block>()
    bloecke += Titelblatt(titel, if (jahre.isNotEmpty()) "${jahre.min()} – ${jahre.max()}" else "",
        Texte.t(Res.string.desk_book_date, LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))), null)
    bloecke += Inhaltsverzeichnis
    if (o.vorwort.isNotBlank()) {
        bloecke += Ueberschrift(Texte.t(Res.string.desk_book_preface), "vorwort")
        o.vorwort.split(Regex("\\n\\s*\\n")).forEach { bloecke += Absatz(listOf(Lauf(it.trim().replace('\n', ' ')))) }
    }
    bloecke += Ueberschrift(Texte.t(Res.string.desk_book_families), "familien")
    // Zwischenueberschriften: Anfangsbuchstabe bzw. Jahrhundert
    var gruppe = ""
    sortiert.forEach { f ->
        val g = if (o.familienChronologisch) zeit(f).takeIf { it != Int.MAX_VALUE }?.let { jdJahr(it) / 100 * 100 }?.let { "$it–${it + 99}" } ?: "?"
            else schluesselName(f).firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        if (g != gruppe) { gruppe = g; bloecke += Ueberschrift(g, "grp-${g.replace(Regex("[^A-Za-z0-9]"), "_")}", neueSeite = false) }
        bloecke += eintrag(f)
    }
    bloecke += reg.bloecke(o)
    return Buch(titel, titel, bloecke, fusszeile(app, baumTitel))
}

/** Jahr aus einem julianischen Tag (fuer die Jahrhundert-Ueberschriften). */
private fun jdJahr(jd: Int): Int = LocalDate.of(2000, 1, 1).with(java.time.temporal.JulianFields.JULIAN_DAY, jd.toLong()).year
