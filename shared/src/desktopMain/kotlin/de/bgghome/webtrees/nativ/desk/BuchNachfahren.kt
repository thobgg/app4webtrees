package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.Texte
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
 * Nachfahrenbuch (26.09.2026): Gegenstueck zum Vorfahrenbuch auf demselben Unterbau. Kapitel je Generation
 * (Stammeltern, Kinder, Enkel ...), Nummern nach Saragossa (1.2.3), d'Aboville (C1.2.3), Henry (123) oder fortlaufend;
 * je Eintrag die Lebensdaten, der Verweis auf den Elterneintrag, jede Ehe mit Partner und die Kinder mit Verweis auf
 * ihren eigenen Eintrag. Wer ueber zwei Linien abstammt, steht einmal und wird beim zweiten Mal nur verwiesen.
 */

class NachfahrenDaten(val wurzel: String, val details: Map<String, IndividualDetail>, val bilder: Map<String, BufferedImage>)

/** Zweigfarben: jedes Kind der Stammeltern bekommt eine Farbe fuer seine ganze Nachkommenschaft. */
private val ZWEIG_FARBE = listOf(
    Color(0x5B, 0x8F, 0xD0), Color(0x62, 0xB0, 0x5A), Color(0xD8, 0x6A, 0x5E), Color(0xE0, 0xB8, 0x3A),
    Color(0x8E, 0x6C, 0xC4), Color(0x3F, 0xA7, 0xA3), Color(0xC2, 0x7B, 0x3E), Color(0x9A, 0x9A, 0x9A),
)

suspend fun nachfahrenbuchLaden(
    client: WtClient, tree: String, xref: String, generationen: Int, bilder: Boolean, fortschritt: (String) -> Unit = {},
): NachfahrenDaten = coroutineScope {
    // Ganzer Baum, wenn verfuegbar (Stufe 17, Zwischenspeicher), sonst Person fuer Person
    val schaetzung = Math.pow(3.0, generationen.toDouble()).toInt().coerceAtMost(20000)
    val baum = BaumSpeicher.holen(client, tree, schaetzung) { g, t -> fortschritt(Texte.t(Res.string.desk_book_progress_tree, g, t)) }
    val details = HashMap<String, IndividualDetail>()
    var ebene = listOf(xref)
    for (g in 0 until generationen) {
        val neu = ebene.filter { it !in details }
        val geladen = if (baum != null) neu.mapNotNull { x -> baum.detail(x)?.let { x to it } } else neu.chunked(8).flatMap { gruppe ->
            gruppe.map { x -> async { x to runCatching { client.individual(tree, x) }.getOrNull() } }.awaitAll()
        }.mapNotNull { (x, d) -> d?.let { x to it } }
        details.putAll(geladen)
        fortschritt(Texte.t(Res.string.desk_book_progress_persons, details.size))
        ebene = neu.flatMap { x -> details[x]?.spouseFamilies.orEmpty().flatMap { it.children }.map { it.xref } }
            .filter { it.isNotEmpty() && it !in details }.distinct()
        if (ebene.isEmpty()) break
    }
    val fotos = if (!bilder) emptyMap() else {
        fortschritt(Texte.t(Res.string.desk_book_progress_pictures))
        details.values.mapNotNull { it.person.thumb }.distinct().chunked(8).flatMap { gruppe ->
            gruppe.map { url ->
                async {
                    url to runCatching {
                        client.http.newCall(Request.Builder().url(url).build()).execute().use { r -> if (r.isSuccessful) r.body?.byteStream()?.use { ImageIO.read(it) } else null }
                    }.getOrNull()?.let { b -> BufferedImage(b.width, b.height, BufferedImage.TYPE_INT_RGB).also { it.createGraphics().apply { color = Color.WHITE; fillRect(0, 0, b.width, b.height); drawImage(b, 0, 0, null); dispose() } } }
                }
            }.awaitAll()
        }.mapNotNull { (u, b) -> b?.let { u to it } }.toMap()
    }
    NachfahrenDaten(xref, details, fotos)
}

private fun generationNachfahren(g: Int): String = Texte.t(Res.string.desk_book_generation, g + 1, when (g) {
    0 -> Texte.t(Res.string.desk_gen_root_desc); 1 -> Texte.t(Res.string.desk_gen_children); 2 -> Texte.t(Res.string.desk_gen_grandchildren)
    3 -> Texte.t(Res.string.desk_gen_great_grandchildren); 4 -> Texte.t(Res.string.desk_gen_gg_grandchildren); else -> Texte.t(Res.string.desk_book_descendants)
})

private fun henryZiffer(i: Int) = if (i < 10) "$i" else ('A' + (i - 10)).toString()

/** Das Nachfahrenbuch als Dokument. [baum]: Titel des Stammbaums, [app]: wtTux/wtWin. */
fun nachfahrenbuch(d: NachfahrenDaten, o: BuchOptionen, baum: String, app: String): Buch {
    val wurzel = d.details[d.wurzel]?.person ?: return Buch("", "", emptyList(), "")
    // Eintraege Generation fuer Generation; pfad = Kindpositionen ab den Stammeltern
    class E(val xref: String, val gen: Int, val pfad: List<Int>, val eltern: Int?, val zweig: Int?, var verweis: Int? = null)
    val eintraege = mutableListOf<E>()
    val erstes = HashMap<String, Int>()
    val kindEintrag = HashMap<Pair<Int, String>, Int>()   // (Elterneintrag, Kind) -> Eintrag
    var ebene = listOf(E(d.wurzel, 0, emptyList(), null, null))
    while (ebene.isNotEmpty()) {
        val naechste = mutableListOf<E>()
        ebene.forEach { e ->
            val i = eintraege.size
            eintraege += e
            e.eltern?.let { kindEintrag[it to e.xref] = i }
            val schon = erstes[e.xref]
            if (schon != null) { e.verweis = schon; return@forEach }
            erstes[e.xref] = i
            val p = d.details[e.xref]?.person ?: return@forEach
            val weiter = e.gen + 1 < o.generationen && !(o.namenstraeger && e.gen > 0 && p.sex == "F")
            if (!weiter) return@forEach
            var k = 0
            d.details[e.xref]?.spouseFamilies.orEmpty().forEach { f ->
                f.children.forEach { c -> k++; naechste += E(c.xref, e.gen + 1, e.pfad + k, i, e.zweig ?: (k - 1)) }
            }
        }
        ebene = naechste
    }
    fun etikett(i: Int): String {
        val e = eintraege[i]
        return when (o.nummerierung) {
            Nummerierung.Saragossa -> (listOf(1) + e.pfad).joinToString(".")
            Nummerierung.Aboville -> "${'A' + e.gen}" + (listOf(1) + e.pfad).joinToString(".")
            Nummerierung.Henry -> "1" + e.pfad.joinToString("") { henryZiffer(it) }
            Nummerierung.Fortlaufend -> "${i + 1}"
        }
    }
    val etiketten = eintraege.indices.associate { (it + 1).toLong() to etikett(it) }
    fun schluessel(i: Int) = (i + 1).toLong()
    fun ziel(i: Int) = "n${schluessel(i)}"
    val reg = BuchRegister()

    fun eintrag(i: Int): List<Block> {
        val e = eintraege[i]
        val det = d.details[e.xref]
        val p = det?.person
        val farbe = if (o.farbkodierung) e.zweig?.let { ZWEIG_FARBE[it % ZWEIG_FARBE.size] } else null
        if (p == null) return listOf(Absatz(listOf(Lauf("?", Stil.Fett)), marke = etikett(i), anker = ziel(i), farbe = farbe, abstandVor = true))
        e.verweis?.let { v ->
            reg.name(p, schluessel(i))
            return listOf(Absatz(listOf(Lauf(registerName(p), Stil.Fett), Lauf(" – " + Texte.t(Res.string.desk_book_see) + " "), Lauf(etikett(v), ziel = ziel(v))),
                marke = etikett(i), anker = ziel(i), farbe = farbe, abstandVor = true))
        }
        if (p.isPrivate) {
            reg.name(p, schluessel(i))
            return listOf(Absatz(listOf(Lauf(registerName(p), Stil.Fett), Lauf(", " + Texte.t(Res.string.person_private))), marke = etikett(i), anker = ziel(i), farbe = farbe, abstandVor = true))
        }
        val text = personText(p, det, o, schluessel(i), reg)
        val bloecke = mutableListOf<Block>()
        bloecke += Absatz(text.laeufe, marke = etikett(i), anker = ziel(i), bild = if (o.bilder) p.thumb?.let { d.bilder[it] } else null, farbe = farbe, abstandVor = true)
        e.eltern?.let { el -> bloecke += Absatz(listOf(Lauf(Texte.t(Res.string.desk_book_child_of) + " "), Lauf(etikett(el), ziel = ziel(el))), einzug = 1) }
        // Jede Ehe: Partner mit Kurzdaten, darunter die Kinder mit Verweis auf ihren Eintrag
        det.spouseFamilies.forEach { f ->
            val zeile = mutableListOf<Lauf>()
            val heirat = listOf(buchDatum(f.marriage?.date), ortText(f.marriage?.place?.name, o.orteKuerzen)).filter(String::isNotBlank).joinToString(" ")
            val sp = f.spouse
            if (o.partner || f.children.isNotEmpty()) {
                zeile += Lauf("∞ " + (if (heirat.isNotBlank()) "$heirat " else "") + Texte.t(Res.string.desk_book_with) + " ")
                if (sp != null) {
                    zeile += Lauf(registerName(sp), Stil.Fett)
                    if (o.partner) kurzdaten(sp, o).takeIf(String::isNotBlank)?.let { zeile += Lauf(" ($it)") }
                    reg.name(sp, schluessel(i))
                } else zeile += Lauf(Texte.t(Res.string.desk_unknown_partner))
            }
            if (f.children.isNotEmpty()) {
                zeile += Lauf("; " + Texte.t(Res.string.desk_book_children) + " ")
                f.children.forEachIndexed { k, c ->
                    if (k > 0) zeile += Lauf(", ")
                    zeile += Lauf("${c.given.ifBlank { c.name }} ${geschlechtZeichen(c)}" + (c.birth?.date?.year?.takeIf { it > 0 }?.let { " ($it)" } ?: ""))
                    kindEintrag[i to c.xref]?.let { ki -> zeile += Lauf(" → "); zeile += Lauf(etikett(ki), ziel = ziel(ki)) }
                }
            }
            if (zeile.isNotEmpty()) bloecke += Absatz(zeile, einzug = 1)
        }
        bloecke += notizBloecke(text, o)
        return bloecke
    }

    val titel = o.titel.ifBlank { Texte.t(Res.string.desk_book_title_descendants, wurzel.name) }
    val jahre = eintraege.mapNotNull { d.details[it.xref]?.person?.birth?.date?.year?.takeIf { y -> y > 0 } }
    val bloecke = mutableListOf<Block>()
    bloecke += Titelblatt(titel, if (jahre.isNotEmpty()) "${jahre.min()} – ${jahre.max()}" else "",
        Texte.t(Res.string.desk_book_date, LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))),
        if (o.bilder) wurzel.thumb?.let { d.bilder[it] } else null)
    bloecke += Inhaltsverzeichnis
    if (o.vorwort.isNotBlank()) {
        bloecke += Ueberschrift(Texte.t(Res.string.desk_book_preface), "vorwort")
        o.vorwort.split(Regex("\\n\\s*\\n")).forEach { bloecke += Absatz(listOf(Lauf(it.trim().replace('\n', ' ')))) }
    }
    eintraege.indices.groupBy { eintraege[it].gen }.forEach { (g, liste) ->
        bloecke += Ueberschrift(generationNachfahren(g), "g$g", neueSeite = g == 0 || liste.size > 4)
        liste.forEach { bloecke += eintrag(it) }
    }
    bloecke += reg.bloecke(o, etiketten)
    return Buch(titel, titel, bloecke, fusszeile(app, baum))
}
