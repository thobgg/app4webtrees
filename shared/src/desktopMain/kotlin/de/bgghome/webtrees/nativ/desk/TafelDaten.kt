package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.DescendantNode
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.res.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.apache.pdfbox.pdmodel.PDDocument
import java.awt.image.BufferedImage

/*
 * Daten der Tafeln (26.09.2026): laden, was eine Tafelart braucht, und daraus den Baum der Tafel bauen.
 * api4webtrees liefert Vorfahren bis 7 Generationen am Stueck; tiefer geht es, indem die Tafel von der obersten
 * Reihe aus nachlaedt (hasParents sagt, bei wem sich das lohnt).
 */

/** Ein Vorfahr mit Kekule-Nummer (Long: Linien reichen bis 30 Generationen). */
class AhnenEintrag(val person: Person, val hatEltern: Boolean)

/** Generation einer Kekule-Nummer: 1 = 0, 2..3 = 1, 4..7 = 2 ... */
fun reihe(n: Long): Int = 63 - java.lang.Long.numberOfLeadingZeros(n)

/**
 * Vorfahren bis [generationen] tief. Wer in der obersten gelieferten Reihe steht und Eltern hat, bekommt eine
 * eigene Anfrage - aber nur, wenn [weiter] fuer seine Nummer zustimmt (bei Linien nur die Linie selbst).
 */
suspend fun ahnenLaden(client: WtClient, tree: String, xref: String, generationen: Int, weiter: (Long) -> Boolean = { true }): Map<Long, AhnenEintrag> {
    val alle = HashMap<Long, AhnenEintrag>()
    suspend fun holen(wurzel: Long, x: String, gen0: Int): List<Long> {
        val tiefe = minOf(7, generationen - gen0)
        if (tiefe < 1) return emptyList()
        val r = client.pedigree(tree, x, tiefe)
        val g = r.generations.coerceAtLeast(1)
        val neu = ArrayList<Long>()
        r.ancestors.forEach { a ->
            val k = a.n.toLong(); val gk = reihe(k)
            val n = (wurzel shl gk) + (k - (1L shl gk))
            if (n !in alle) alle[n] = AhnenEintrag(a.person, a.hasParents)
            // Rand dieser Antwort: dort geht es mit einer neuen Anfrage weiter
            if (gk == g - 1 && a.hasParents && gen0 + gk + 1 < generationen && gk > 0) neu += n
        }
        return neu
    }
    var rand = holen(1L, xref, 0)
    while (rand.isNotEmpty()) {
        val naechste = rand.filter(weiter)
        rand = coroutineScope {
            naechste.chunked(8).flatMap { gruppe ->
                gruppe.map { n -> async { holen(n, alle.getValue(n).person.xref, reihe(n)) } }.awaitAll().flatten()
            }
        }
    }
    return alle
}

/**
 * Nachfahren bis [generationen] tief. api4webtrees liefert ab 1.8.0 bis 10 Generationen am Stueck, aeltere nur 4 -
 * dann holt die Tafel die fehlenden Ebenen je Blatt nach (die Antwort nennt die gelieferte Tiefe).
 */
suspend fun nachfahrenLaden(client: WtClient, tree: String, xref: String, generationen: Int): DescendantNode {
    val r = client.descendants(tree, xref, generationen)
    if (r.generations >= generationen || r.generations < 1) return r.tree
    suspend fun erweitern(k: DescendantNode, tiefe: Int): DescendantNode =
        if (tiefe == r.generations) {
            if (k.person.isPrivate || k.person.xref.isEmpty()) k else nachfahrenLaden(client, tree, k.person.xref, generationen - tiefe + 1)
        } else k.copy(families = k.families.map { f -> f.copy(children = f.children.map { erweitern(it, tiefe + 1) }) })
    return erweitern(r.tree, 1)
}

/**
 * Aus dem Nachkommenbaum der API die Personen der Tafel, hoechstens [generationen] Ebenen. [namenstraeger]: die
 * Kinder von Toechtern erscheinen nicht (die Ausgangsperson zaehlt immer). [partner]: Ehepartner in den Kasten.
 */
fun tafelBaum(k: DescendantNode, generationen: Int, namenstraeger: Boolean = false, partner: Boolean = false, tiefe: Int = 1): TafelPerson {
    val weiter = tiefe < generationen && !(namenstraeger && tiefe > 1 && k.person.sex == "F")
    return TafelPerson(
        k.person,
        if (!weiter) emptyList() else k.families.flatMap { it.children }.map { tafelBaum(it, generationen, namenstraeger, partner, tiefe + 1) },
        partner = if (partner) k.families.mapNotNull { it.spouse } else emptyList(),
    )
}

/**
 * Ahnentafel aus den Kekule-Nummern: Vater 2n, Mutter 2n+1, hoechstens [generationen] Reihen, ab [start] (fuer die
 * Seiten der seitenweisen Tafel). [verweise]: wer schon unter einer kleineren Nummer steht, bekommt "= n" statt
 * seiner Vorfahren (Ahnenschwund). [hinweise]: Text ueber Personen der obersten Reihe.
 */
fun ahnenBaum(
    ahnen: Map<Long, AhnenEintrag>, generationen: Int, verweise: Boolean = false, start: Long = 1L, hinweise: Map<Long, String> = emptyMap(),
): TafelPerson? {
    val erste = if (verweise) ahnen.entries.filter { it.value.person.xref.isNotEmpty() }.groupBy { it.value.person.xref }.mapValues { e -> e.value.minOf { it.key } } else emptyMap()
    val obersteReihe = reihe(start) + generationen - 1
    fun knoten(n: Long): TafelPerson? = ahnen[n]?.let { a ->
        val v = erste[a.person.xref]?.takeIf { it != n }
        TafelPerson(a.person, if (v != null || reihe(n) >= obersteReihe) emptyList() else listOfNotNull(knoten(2 * n), knoten(2 * n + 1)), n, verweis = v, hinweis = hinweise[n])
    }
    return knoten(start)
}

/** Nummern einer Linie von der Ausgangsperson (1) bis zu ihrem Ende. */
private fun linie(art: TafelArt, ahnen: Map<Long, AhnenEintrag>): List<Long> = when (art) {
    TafelArt.Stammlinie -> generateSequence(1L) { it * 2 }.takeWhile { it in ahnen }.toList()
    TafelArt.Mutterstamm -> generateSequence(1L) { it * 2 + 1 }.takeWhile { it in ahnen }.toList()
    else -> {
        // Aeltester Vorfahr: frueheste bekannte Geburt; ohne Geburtsdaten der entfernteste
        val ziel = ahnen.entries.filter { (it.value.person.birth?.date?.jd ?: 0) > 0 }.minByOrNull { it.value.person.birth!!.date!!.jd }?.key
            ?: ahnen.keys.maxByOrNull(::reihe) ?: 1L
        generateSequence(ziel) { if (it > 1) it / 2 else null }.toList().reversed()
    }
}

/** Linie als Tafel: jede Person der Linie mit ihren Eltern (oder nur dem naechsten der Linie). */
fun linienBaum(art: TafelArt, ahnen: Map<Long, AhnenEintrag>, generationen: Int, beideEltern: Boolean): TafelPerson? {
    val pfad = linie(art, ahnen).take(generationen)
    if (pfad.isEmpty()) return null
    fun knoten(i: Int): TafelPerson {
        val n = pfad[i]
        val kinder = if (i + 1 >= pfad.size) emptyList() else {
            val naechster = pfad[i + 1]
            listOf(2 * n, 2 * n + 1).mapNotNull { m ->
                when {
                    m == naechster -> knoten(i + 1)
                    beideEltern -> ahnen[m]?.let { TafelPerson(it.person, emptyList(), m) }
                    else -> null
                }
            }
        }
        return TafelPerson(ahnen.getValue(n).person, kinder, n, aufLinie = true)
    }
    return knoten(0)
}

/** Geladene Daten einer Tafel: Vorfahren nach Kekule-Nummer und/oder der Nachkommenbaum. */
class TafelDaten(val ahnen: Map<Long, AhnenEintrag>, val nachfahren: DescendantNode?)

/** Groesste Tiefe je Tafelart (Vorfahren; bei der Stammtafel die Nachfahren). */
fun maxGen(art: TafelArt) = when (art) {
    TafelArt.Stamm -> 10
    TafelArt.Ahnen -> 10
    TafelArt.Faecher, TafelArt.Kreis -> 8
    TafelArt.Sanduhr -> 7
    TafelArt.AhnenSeiten, TafelArt.Aeltester -> 13
    TafelArt.Stammlinie, TafelArt.Mutterstamm -> 30
}

suspend fun tafelDatenLaden(client: WtClient, tree: String, xref: String, art: TafelArt, generationen: Int): TafelDaten = when (art) {
    TafelArt.Stamm -> TafelDaten(emptyMap(), nachfahrenLaden(client, tree, xref, maxGen(art)))
    TafelArt.Sanduhr -> TafelDaten(ahnenLaden(client, tree, xref, generationen), nachfahrenLaden(client, tree, xref, 10))
    TafelArt.Stammlinie -> TafelDaten(ahnenLaden(client, tree, xref, generationen) { n -> n and (n - 1) == 0L }, null)
    TafelArt.Mutterstamm -> TafelDaten(ahnenLaden(client, tree, xref, generationen) { n -> (n + 1) and n == 0L }, null)
    TafelArt.Ahnen, TafelArt.AhnenSeiten, TafelArt.Aeltester, TafelArt.Faecher, TafelArt.Kreis -> TafelDaten(ahnenLaden(client, tree, xref, generationen), null)
}

/** Der Inhalt einer Tafel aus den geladenen Daten und den Einstellungen. */
fun tafelInhalt(art: TafelArt, d: TafelDaten, o: TafelOptionen): TafelInhalt? = when (art) {
    TafelArt.Stamm -> d.nachfahren?.let { TafelInhalt(nachfahren = tafelBaum(it, o.generationen, o.namenstraeger, o.partner)) }
    // Senkrecht steht der Proband klassisch unten, waagerecht links; [ausgangOben] kehrt das um ("oben" bzw. "rechts")
    TafelArt.Ahnen -> ahnenBaum(d.ahnen, o.generationen, o.nummern)?.let { if (o.ausgangOben != o.waagerecht) TafelInhalt(nachfahren = it) else TafelInhalt(vorfahren = it) }
    TafelArt.Sanduhr -> d.nachfahren?.let { n ->
        TafelInhalt(vorfahren = ahnenBaum(d.ahnen, o.generationen, o.nummern), nachfahren = tafelBaum(n, o.nachfahren + 1, o.namenstraeger, o.partner))
    }
    TafelArt.Stammlinie, TafelArt.Mutterstamm, TafelArt.Aeltester ->
        linienBaum(art, d.ahnen, o.generationen, o.partner)?.let { TafelInhalt(vorfahren = it, linie = true) }
    TafelArt.AhnenSeiten -> ahnenBaum(d.ahnen, o.generationen, o.nummern)?.let { if (o.waagerecht) TafelInhalt(nachfahren = it) else TafelInhalt(vorfahren = it) }
    TafelArt.Faecher, TafelArt.Kreis -> ahnenBaum(d.ahnen, o.generationen, o.nummern)?.let { TafelInhalt(vorfahren = it) }
}

/**
 * Seitenweise Ahnentafel: je Seite vier Generationen (waagerecht fuenf, Proband links); die Personen der letzten
 * Reihe verweisen auf ihre Seite.
 */
fun ahnenSeitenPdf(
    d: TafelDaten, o: TafelOptionen, bilder: (Person) -> BufferedImage?, privat: String, fuss: String,
): Pair<PDDocument, TafelInfo>? {
    if (1L !in d.ahnen) return null
    val proSeite = if (o.waagerecht) 5 else 4
    // Seitenwurzeln: Nummer 1, dann alle, die in der obersten Reihe einer Seite stehen und selbst Eltern haben
    val wurzeln = ArrayList<Long>()
    val verweise = if (o.nummern) ahnenBaum(d.ahnen, o.generationen, true)?.let { b -> generateSequence(listOf(b)) { ebene -> ebene.flatMap { it.kinder }.takeIf { it.isNotEmpty() } }.flatten().mapNotNull { k -> k.verweis?.let { k.nummer } }.toSet() } ?: emptySet() else emptySet()
    var ebene = listOf(1L)
    while (ebene.isNotEmpty()) {
        wurzeln += ebene
        ebene = ebene.flatMap { w ->
            val oben = reihe(w) + proSeite - 1
            if (oben >= o.generationen - 1) emptyList()
            else d.ahnen.keys.filter { n -> reihe(n) == oben && n !in verweise && generateSequence(n) { if (it > w) it / 2 else null }.last() == w && (2 * n in d.ahnen || 2 * n + 1 in d.ahnen) }.sorted()
        }
    }
    val seiteVon = wurzeln.withIndex().associate { (i, n) -> n to i + 1 }
    val ziel = PDDocument()
    val personen = HashSet<String>()
    wurzeln.forEachIndexed { i, w ->
        val tiefe = minOf(proSeite, o.generationen - reihe(w))
        val hinweise = seiteVon.filterKeys { it != w && reihe(it) == reihe(w) + proSeite - 1 }.mapValues { Texte.t(Res.string.desk_chart_page_ref, it.value) }
        val baum = ahnenBaum(d.ahnen, tiefe, o.nummern, w, hinweise) ?: return@forEachIndexed
        generateSequence(listOf(baum)) { e -> e.flatMap { it.kinder }.takeIf { it.isNotEmpty() } }.flatten().forEach { personen += it.person.xref }
        val titel = if (w == 1L) o.titel else {
            val zurueck = generateSequence(w) { if (it > 1) it / 2 else null }.drop(1).first { it in seiteVon }
            Texte.t(Res.string.desk_chart_page_title, d.ahnen.getValue(w).person.name, w, seiteVon.getValue(zurueck))
        }
        val inhalt = if (o.waagerecht) TafelInhalt(nachfahren = baum) else TafelInhalt(vorfahren = baum)
        val (poster, _) = tafelPdf(inhalt, o.copy(titel = titel), bilder, privat, "$fuss · ${i + 1}/${wurzeln.size}")
        aufEinBlatt(poster, ziel, querErzwingen = true)
    }
    return ziel to TafelInfo(personen.size, 30, 21, ziel.numberOfPages)
}

/** Titelvorgabe je Tafelart. */
fun tafelTitel(art: TafelArt, name: String): String = Texte.t(
    when (art) {
        TafelArt.Stamm -> Res.string.desk_chart_title_default
        TafelArt.Ahnen, TafelArt.AhnenSeiten -> Res.string.desk_chart_title_ancestors
        TafelArt.Sanduhr -> Res.string.desk_chart_title_hourglass
        TafelArt.Stammlinie -> Res.string.desk_chart_title_paternal
        TafelArt.Mutterstamm -> Res.string.desk_chart_title_maternal
        TafelArt.Aeltester -> Res.string.desk_chart_title_oldest
        TafelArt.Faecher -> Res.string.desk_chart_title_fan
        TafelArt.Kreis -> Res.string.desk_chart_title_circle
    }, name,
)

/** Die fertige Tafel als PDF: ein Blatt, bei der seitenweisen Ahnentafel A4-Seiten. */
fun tafelErzeugen(
    art: TafelArt, d: TafelDaten, o: TafelOptionen, bilder: (Person) -> BufferedImage?, privat: String, fuss: String,
): Pair<PDDocument, TafelInfo>? =
    if (art == TafelArt.AhnenSeiten) ahnenSeitenPdf(d, o, bilder, privat, fuss)
    else if (art == TafelArt.Faecher || art == TafelArt.Kreis) ahnenBaum(d.ahnen, o.generationen, o.nummern)?.let { faecherPdf(it, o.generationen, art == TafelArt.Kreis, o, bilder, privat, fuss) }
    else tafelInhalt(art, d, o)?.let { tafelPdf(it, o, bilder, privat, fuss) }
