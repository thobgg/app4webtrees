package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.ExportFamily
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.LocationEvent
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.TreeExport
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.data.Haustypen
import de.bgghome.webtrees.nativ.data.OrtsKlasse
import de.bgghome.webtrees.nativ.data.Ortsnamen
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

/**
 * Ein Haus oder Hof fuer den Haeuserteil des Ortsfamilienbuchs (05.10.2026): ein Ortsdatensatz _LOC mit Art (TYPE) unter
 * einem anderen Ort, so wie GEDCOM-L Gebaeude abbildet. [name] ist der volle Ortsname wie an den Ereignissen.
 */
class Haus(val name: String, val typ: String, val ereignisse: List<LocationEvent> = emptyList(), val notiz: String? = null,
           val klasse: OrtsKlasse = OrtsKlasse.HAUS) {
    val blatt get() = Ortsnamen.blatt(name)
    /** Der Ort, zu dem das Haus gehoert ("Bienenbuettel, Uelzen ..."). */
    val ort get() = Ortsnamen.oberort(name)
    /** Die Ebene direkt darueber - der Stadtteil oder das Dorf, nach dem der Haeuserteil gliedert. */
    val eltern get() = Ortsnamen.teile(name).getOrNull(1).orEmpty()
}

class FamilienDaten(val baum: TreeExport, val bilder: Map<String, BufferedImage>, val haeuser: List<Haus> = emptyList())

/**
 * Haeuser und Hoefe aus der Ortsverwaltung (API-Stufe 27): Orte mit Art im Ortsdatensatz, die unter einem anderen Ort
 * stehen und selbst keine Orte darunter haben. Die Ereignisse am Gebaeude (Brand, Umbau ...) kommen aus dem einzelnen Ort.
 */
suspend fun haeuserLaden(client: WtClient, tree: String): List<Haus> = coroutineScope {
    val orte = client.placeList(tree).places
    // Wer Orte unter sich hat, ist eine Ebene, kein Haus - erkannt am Namen, mit Komma oder Semikolon gegliedert
    val oberorte = orte.map { Ortsnamen.schluessel(Ortsnamen.oberort(it.name)) }.filter(String::isNotEmpty).toSet()
    val kandidaten = orte.mapNotNull { p ->
        if (p.location == null || Ortsnamen.teile(p.name).size < 2) return@mapNotNull null
        val klasse = Haustypen.klasse(p.type, p.govType, Ortsnamen.blatt(p.name), Ortsnamen.schluessel(p.name) in oberorte)
        // Ohne Art und ohne Hausnummer waere jeder Ort ein Kandidat - dann nur, wenn er eine Art hat
        if (klasse == OrtsKlasse.UNBEKANNT && p.type.isNullOrBlank() && p.govType.isNullOrBlank()) null else p to klasse
    }
    kandidaten.chunked(8).flatMap { gruppe ->
        gruppe.map { (p, klasse) ->
            async {
                val loc = runCatching { client.place(tree, p.name).location }.getOrNull()
                Haus(p.name, p.type.orEmpty(), loc?.events.orEmpty(), loc?.notes?.firstOrNull(), klasse)
            }
        }.awaitAll()
    }
}

suspend fun familienbuchLaden(client: WtClient, tree: String, bilder: Boolean, haeuser: Boolean = false, fortschritt: (String) -> Unit = {}, abbruch: () -> Boolean = { false }): FamilienDaten = coroutineScope {
    // Das ganze Buch braucht den ganzen Baum: "personen" so gross, dass der Export sich immer lohnt
    val baum = BaumSpeicher.holen(client, tree, Int.MAX_VALUE / 64) { g, t -> fortschritt(Texte.t(Res.string.desk_book_progress_tree, g, t)) }
        ?: throw IllegalStateException(Texte.t(Res.string.desk_book_needs_export))
    fortschritt(Texte.t(Res.string.desk_book_progress_persons, baum.individuals.size))
    // Bilder nur fuer die Eheleute (Kinder mit eigener Familie erscheinen dort)
    val fotos = if (!bilder) emptyMap() else Bilder.alle(client, client.baseUrl, tree,
        baum.families.values.flatMap { listOfNotNull(it.husband, it.wife) }.distinct().mapNotNull { x ->
            baum.person(x)?.thumb?.let { Bilder.Quelle(it, baum.individuals[x]?.media?.firstOrNull { m -> m.isImage }?.path) } }, fortschritt, abbruch)
    FamilienDaten(baum, fotos, if (haeuser) runCatching { haeuserLaden(client, tree) }.getOrDefault(emptyList()) else emptyList())
}

/** Rolle einer Person am Haus aus ihren Ereignissen dort: Besitz (PROP) vor Wohnen (RESI), sonst die Ereignisse. */
private fun rolleAmHaus(fakten: List<FactJson>): String {
    val tags = fakten.map { it.tag }.toSet()
    return listOfNotNull(
        Texte.t(Res.string.desk_book_owner).takeIf { "PROP" in tags },
        Texte.t(Res.string.desk_book_resident).takeIf { "RESI" in tags },
    ).ifEmpty { fakten.map { it.label.ifBlank { it.tag } }.distinct() }.joinToString(", ")
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
    // Haeuser des Orts (bzw. alle ohne Ortsfilter); Ereignisse an einem Haus zaehlen fuer den Ort, zu dem es gehoert.
    // Nach Ortsart: Gebaeude in den Haeuserteil, hoehere Ebenen (Stadtteil, Gemeinde) nur als Gliederung oder - mit
    // eigenen Bewohnern - in den Anhang "Weitere Orte"; Orte ohne Art nach Wahl.
    val imOrt = if (!o.haeuser) emptyList() else d.haeuser
        .filter { h -> filter.isEmpty() || Ortsnamen.teile(h.ort).any { it.lowercase().startsWith(filter) } }
    val haeuser = imOrt.filter { h -> !o.haeuserNurTyp || h.klasse == OrtsKlasse.HAUS || (h.klasse == OrtsKlasse.UNBEKANNT && o.haeuserOhneTyp) }
        .sortedWith(compareBy<Haus, String>(Ortsnamen.NATUERLICH) { it.eltern }.thenBy(Ortsnamen.NATUERLICH) { it.blatt })
    val weitere = (imOrt - haeuser.toSet()).sortedWith(compareBy(Ortsnamen.NATUERLICH) { it.blatt })
    val hausNr = haeuser.withIndex().associate { (i, h) -> h.name.lowercase() to i + 1 }
    val alleHaeuser = d.haeuser.map { it.name.lowercase() }.toSet()
    fun ortPasst(ort: String): Boolean {
        val teile = Ortsnamen.teile(ort).map { it.lowercase() }
        return teile.firstOrNull()?.startsWith(filter) == true || (ort in alleHaeuser && teile.drop(1).any { it.startsWith(filter) })
    }
    val familien = b.families.values.filter { f -> !f.isPrivate && (f.husband != null || f.wife != null) }
        .filter { f -> filter.isEmpty() || familienOrte(b, f).any(::ortPasst) }
    fun schluesselName(f: ExportFamily): String {
        val p = b.person(f.husband) ?: b.person(f.wife)
        return listOf(p?.surname.orEmpty(), p?.given.orEmpty()).joinToString(" ").lowercase()
    }
    // Einordnung in der Zeit: Heirat, sonst erstes Kind, sonst das frueheste datierte Ereignis der Eheleute (Wohnort,
    // Besitz, Tod ...), zuletzt die Geburt des Mannes plus etwa 25 Jahre
    fun zeit(f: ExportFamily): Int = f.marriage?.date?.jd?.takeIf { it > 0 } ?: f.children.mapNotNull { jd(b.person(it)) }.minOrNull()
        ?: listOfNotNull(f.husband, f.wife).flatMap { b.individuals[it]?.facts.orEmpty() }.filter { it.tag !in setOf("BIRT", "CHR", "BAPM") }
            .mapNotNull { it.date?.jd?.takeIf { j -> j > 0 } }.minOrNull()
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
            val quelle = if (o.quellen && e.sources.isNotEmpty()) " (${Texte.t(Res.string.desk_book_source)}: ${e.sources.joinToString("; ") { it.mitSeite() }})" else ""
            // Heiratsart bei mehreren Heiraten ("∞ 1924 Celle (standesamtlich)"), Trauzeugen ab API-Stufe 19
            val art = if (ehe.count { it.tag == "MARR" } > 1) heiratsartKlammer(e) else ""
            val zeugen = patenZeilenText(e).takeIf { it.isNotEmpty() }?.let { " (" + it.joinToString("; ") + ")" }.orEmpty()
            listOf(zeichen, buchDatum(e.date), ort).filter(String::isNotBlank).joinToString(" ") + art + zeugen + quelle
        }
        // Weder Heirat noch zweiter Ehepartner bekannt (z. B. unbekannte Mutter): keine leere Zeile mit "∞" und "?"
        if (eheText.isNotBlank() || zweiter != null) {
            bloecke += Absatz(listOf(Lauf(eheText.ifBlank { "∞" })), einzug = 1)
            bloecke += ehepartner(zweiter, n, false, null, null)
        }
        f.facts.filter { it.tag == "NOTE" && it.value.isNotBlank() }.forEach { bloecke += Absatz(listOf(Lauf(it.value.replace('\n', ' '), Stil.Kursiv)), einzug = 1) }
        // Rueckverweis auf den Haeuserteil: Haeuser, in denen die Eheleute wohnten oder die ihnen gehoerten
        val ihreHaeuser = listOfNotNull(f.husband, f.wife).flatMap { x -> b.individuals[x]?.facts.orEmpty() }
            .filter { it.tag == "RESI" || it.tag == "PROP" }.mapNotNull { it.place?.name?.lowercase() }.filter { it in hausNr }.distinct()
            .sortedBy { hausNr.getValue(it) }
        if (ihreHaeuser.isNotEmpty()) {
            val laeufe = mutableListOf(Lauf(Texte.t(Res.string.desk_book_house_ref) + ": "))
            ihreHaeuser.forEachIndexed { j, h ->
                if (j > 0) laeufe += Lauf(", ")
                laeufe += Lauf(haeuser[hausNr.getValue(h) - 1].blatt + " (H${hausNr.getValue(h)})", ziel = "h${hausNr.getValue(h)}")
            }
            bloecke += Absatz(laeufe, einzug = 1)
        }
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
        Texte.t(Res.string.desk_book_date, LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))), null)
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
    val familieVon: (String) -> Long? = { x -> b.individuals[x]?.fams.orEmpty().firstNotNullOfOrNull { nummer[it] } ?: b.individuals[x]?.famc?.firstNotNullOfOrNull { nummer[it] } }
    if (haeuser.isNotEmpty()) bloecke += haeuserTeil(b, haeuser, o, familieVon)
    // Anhang: Orte, die keine Gebaeude sind, aber eigene Bewohner oder Ereignisse haben (ein Stadtteil mit Personen daran)
    val weitereMitInhalt = weitere.filter { w -> w.ereignisse.isNotEmpty() || b.individuals.values.any { i -> !i.person.isPrivate && i.facts.any { it.place?.name?.lowercase() == w.name.lowercase() } } }
    if (weitereMitInhalt.isNotEmpty()) bloecke += haeuserTeil(b, weitereMitInhalt, o, familieVon, Texte.t(Res.string.desk_book_other_places), "weitere", "W")
    bloecke += reg.bloecke(o)
    return Buch(titel, titel, bloecke, fusszeile(app, baumTitel))
}

/**
 * Der Haeuserteil: je Haus Name und Art, Notiz, die Ereignisse am Gebaeude und alle Personen mit Ereignissen dort - der
 * Zeit nach, mit Rolle (Besitzer, Bewohner) und Verweis auf ihre Familie im Familienteil. [familieVon]: Nummer der
 * eigenen Familie, sonst der Elternfamilie.
 */
internal fun haeuserTeil(b: TreeExport, haeuser: List<Haus>, o: BuchOptionen, familieVon: (String) -> Long?,
                         titel: String = Texte.t(Res.string.desk_book_houses), id: String = "haeuser", marke: String = "H"): List<Block> = buildList {
    add(Ueberschrift(titel, id))
    // Kapitel je Ort darueber (Stadtteil, Dorf), wenn die Haeuser aus mehreren stammen
    val kapitel = haeuser.map { it.eltern }.distinct()
    var aktuell: String? = null
    haeuser.forEachIndexed { i, h ->
        val n = i + 1
        if (kapitel.size > 1 && h.eltern != aktuell) {
            aktuell = h.eltern
            add(Ueberschrift(h.eltern.ifBlank { "?" }, "$id-${h.eltern.lowercase().replace(Regex("[^a-z0-9]"), "_")}", neueSeite = false))
        }
        add(Absatz(listOf(Lauf(h.blatt, Stil.Fett), Lauf(if (h.typ.isNotBlank()) " – ${h.typ}" else "")), marke = "$marke$n", anker = "${marke.lowercase()}$n", abstandVor = true))
        h.notiz?.takeIf(String::isNotBlank)?.let { add(Absatz(listOf(Lauf(it.replace('\n', ' '), Stil.Kursiv)), einzug = 1)) }
        h.ereignisse.sortedBy { it.date?.jd?.takeIf { j -> j > 0 } ?: Int.MAX_VALUE }.forEach { e ->
            val was = listOfNotNull(e.type ?: e.label.takeIf(String::isNotBlank), e.value).joinToString(": ")
            val notiz = if (o.notizen && e.notes.isNotEmpty()) " – " + e.notes.joinToString(" ") { it.replace('\n', ' ') } else ""
            val quelle = if (o.quellen && e.sources.isNotEmpty()) " (${Texte.t(Res.string.desk_book_source)}: ${e.sources.joinToString("; ") { listOfNotNull(it.title, it.page).joinToString(", ") }})" else ""
            add(Absatz(listOf(Lauf(listOf(buchDatum(e.date), was).filter(String::isNotBlank).joinToString(" "), Stil.Kursiv), Lauf(notiz + quelle)), einzug = 1))
        }
        // Personen mit Ereignissen an diesem Haus, nach dem fruehesten Datum
        val schluessel = h.name.lowercase()
        b.individuals.mapNotNull { (x, ind) ->
            if (ind.person.isPrivate) return@mapNotNull null
            val hier = ind.facts.filter { it.place?.name?.lowercase() == schluessel }
            if (hier.isEmpty()) null else Triple(x, ind, hier)
        }.sortedBy { (_, _, hier) -> hier.mapNotNull { it.date?.jd?.takeIf { j -> j > 0 } }.minOrNull() ?: Int.MAX_VALUE }
            .forEach { (x, ind, hier) ->
                val zeit = hier.firstOrNull { it.tag == "PROP" || it.tag == "RESI" }?.date ?: hier.firstNotNullOfOrNull { it.date }
                val laeufe = mutableListOf<Lauf>()
                buchDatum(zeit).takeIf(String::isNotBlank)?.let { laeufe += Lauf("$it  ") }
                laeufe += Lauf(registerName(ind.person), Stil.Fett)
                laeufe += Lauf(", " + rolleAmHaus(hier))
                familieVon(x)?.let { fn -> laeufe += Lauf(" <"); laeufe += Lauf("$fn", ziel = "n$fn"); laeufe += Lauf(">") }
                add(Absatz(laeufe, einzug = 1))
            }
    }
}

/** Jahr aus einem julianischen Tag (fuer die Jahrhundert-Ueberschriften). */
private fun jdJahr(jd: Int): Int = LocalDate.of(2000, 1, 1).with(java.time.temporal.JulianFields.JULIAN_DAY, jd.toLong()).year
