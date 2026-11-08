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
 * api4webtrees liefert Vorfahren bis 12 Generationen am Stueck (vor 1.9.0: 7); tiefer geht es, indem die Tafel von
 * der obersten Reihe aus nachlaedt (hasParents sagt, bei wem sich das lohnt).
 */

/** Ein Vorfahr mit Kekule-Nummer (Long: Linien reichen bis 30 Generationen). */
class AhnenEintrag(val person: Person, val hatEltern: Boolean, val geschwister: List<Person>? = null)

/** Generation einer Kekule-Nummer: 1 = 0, 2..3 = 1, 4..7 = 2 ... */
fun reihe(n: Long): Int = 63 - java.lang.Long.numberOfLeadingZeros(n)

/**
 * Vorfahren bis [generationen] tief. Wer in der obersten gelieferten Reihe steht und Eltern hat, bekommt eine
 * eigene Anfrage - aber nur, wenn [weiter] fuer seine Nummer zustimmt (bei Linien nur die Linie selbst).
 * [geschwister]: die Geschwister gleich mitliefern lassen (ab Stufe 15; aeltere Module lassen sie weg, dann null).
 */
suspend fun ahnenLaden(client: WtClient, tree: String, xref: String, generationen: Int, geschwister: Boolean = false, weiter: (Long) -> Boolean = { true }): Map<Long, AhnenEintrag> {
    val alle = HashMap<Long, AhnenEintrag>()
    suspend fun holen(wurzel: Long, x: String, gen0: Int): List<Long> {
        val tiefe = minOf(12, generationen - gen0)
        if (tiefe < 1) return emptyList()
        val r = client.pedigree(tree, x, tiefe, geschwister)
        val g = r.generations.coerceAtLeast(1)
        val neu = ArrayList<Long>()
        r.ancestors.forEach { a ->
            val k = a.n.toLong(); val gk = reihe(k)
            val n = (wurzel shl gk) + (k - (1L shl gk))
            if (n !in alle) alle[n] = AhnenEintrag(a.person, a.hasParents, a.siblings)
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
fun tafelBaum(k: DescendantNode, generationen: Int, namenstraeger: Boolean = false, partner: Boolean = false, tiefe: Int = 1, mehr: Boolean = false, kasten: Boolean = false): TafelPerson {
    val weiter = tiefe < generationen && !(namenstraeger && tiefe > 1 && k.person.sex == "F")
    val kinder = k.families.flatMap { it.children }
    // Partnerkasten nur mit ein oder zwei Ehen, in denen der Partner bekannt ist; sonst bleibt es bei der Zeile im Kasten
    val alsKasten = kasten && k.families.size in 1..2 && k.families.all { it.spouse != null }
    val kinderJeEhe = k.families.map { f -> if (!weiter) emptyList() else f.children.map { tafelBaum(it, generationen, namenstraeger, partner, tiefe + 1, mehr, kasten) } }
    return TafelPerson(
        k.person,
        kinderJeEhe.flatten(),
        partner = if (partner && !alsKasten) k.families.mapNotNull { it.spouse } else emptyList(),
        // B6: wo die Tafel endet, obwohl es Kinder gibt
        hinweis = if (mehr && !weiter) mehrKinder(kinder.size) else null,
        verheiratet = k.families.any { it.spouse != null },
        ehen = if (!alsKasten) emptyList() else k.families.mapIndexed { i, f -> TafelEhe(TafelPerson(f.spouse!!, emptyList()), kinderJeEhe[i]) },
    )
}

/** "+3 Kinder" (B6); null ohne Kinder. */
internal fun mehrKinder(n: Int): String? = when (n) { 0 -> null; 1 -> Texte.t(Res.string.desk_chart_more_child); else -> Texte.t(Res.string.desk_chart_more_children, n) }

/**
 * Nachfahren der Grosseltern aus der Ahnentafel (1-7) und den Nachkommenbaeumen beider Seiten. [generationen] zaehlt
 * die Reihen ab den Grosseltern. Die gemeinsame Familie der Eltern steht unter dem Paar, nicht unter dem Vater;
 * Kinder eines Elternteils aus anderen Ehen bleiben in dessen Baum.
 */
fun cousinBaum(d: TafelDaten, o: TafelOptionen): CousinTafel? {
    val vx = d.ahnen[2]?.person?.xref; val mx = d.ahnen[3]?.person?.xref
    val paar = vx != null && mx != null
    var vater: TafelPerson? = null; var mutter: TafelPerson? = null
    var kinder: List<TafelPerson> = emptyList()
    fun gemeinsam(f: de.bgghome.webtrees.nativ.api.DescendantFamily, x: String) = paar && f.spouse?.xref == if (x == vx) mx else vx
    // Ein Knoten wie in tafelBaum; beim Elternteil faellt die gemeinsame Familie weg (sie steht unter dem Paar),
    // Kekule-Nummern fuer Grosseltern, Eltern und Proband
    fun baum(k: DescendantNode, tiefe: Int): TafelPerson {
        val x = k.person.xref
        val elternteil = x.isNotEmpty() && (x == vx || x == mx)
        val nummer = d.ahnen.entries.firstOrNull { it.key < 8 && it.value.person.xref == x && x.isNotEmpty() }?.key
        if (elternteil && paar && kinder.isEmpty()) kinder = k.families.filter { gemeinsam(it, x) }.flatMap { it.children }.map { baum(it, tiefe + 1) }
        val familien = if (elternteil) k.families.filterNot { gemeinsam(it, x) } else k.families
        return TafelPerson(
            k.person,
            if (tiefe >= o.generationen) emptyList() else familien.flatMap { it.children }.map { baum(it, tiefe + 1) },
            nummer = nummer, partner = if (o.partner) k.families.mapNotNull { it.spouse } else emptyList(),
            hinweis = if (o.mehrHinweise && tiefe >= o.generationen) mehrKinder(familien.sumOf { it.children.size }) else null,
        ).also { if (x == vx && x.isNotEmpty()) vater = it; if (x == mx && x.isNotEmpty()) mutter = it }
    }
    // Die Wurzel einer Seite: Grossvater, sonst Grossmutter (Reihe 1), sonst der Elternteil selbst (Reihe 2)
    fun seite(wurzel: DescendantNode?, elternteil: String?, zuletzt: Boolean): TafelPerson? {
        wurzel ?: return null
        val t = baum(wurzel, if (wurzel.person.xref == elternteil) 2 else 1)
        if (t.person.xref == elternteil) return t
        // Den Elternteil nach innen: links als letztes, rechts als erstes Kind
        val (innen, rest) = t.kinder.partition { it.person.xref == elternteil }
        return TafelPerson(t.person, if (zuletzt) rest + innen else innen + rest, t.nummer, t.partner)
    }
    val links = seite(d.nachfahren, vx, true)
    val rechts = seite(d.mutterseite, mx, false)
    if (links == null && rechts == null) return null
    // Nur ein Elternteil auf der Tafel: dessen Kinder bleiben in seinem Baum
    if (vater == null || mutter == null) return CousinTafel(links, rechts, null, null, emptyList())
    return CousinTafel(links, rechts, vater, mutter, kinder)
}

/**
 * Ahnentafel aus den Kekule-Nummern: Vater 2n, Mutter 2n+1, hoechstens [generationen] Reihen, ab [start] (fuer die
 * Seiten der seitenweisen Tafel). [verweise]: wer schon unter einer kleineren Nummer steht, bekommt "= n" statt
 * seiner Vorfahren (Ahnenschwund). [hinweise]: Text ueber Personen der obersten Reihe.
 */
fun ahnenBaum(
    ahnen: Map<Long, AhnenEintrag>, generationen: Int, verweise: Boolean = false, start: Long = 1L, hinweise: Map<Long, String> = emptyMap(),
    geschwister: Map<Long, List<Person>> = emptyMap(), mehr: Boolean = false,
): TafelPerson? {
    val erste = if (verweise) ahnen.entries.filter { it.value.person.xref.isNotEmpty() }.groupBy { it.value.person.xref }.mapValues { e -> e.value.minOf { it.key } } else emptyMap()
    val obersteReihe = reihe(start) + generationen - 1
    fun knoten(n: Long): TafelPerson? = ahnen[n]?.let { a ->
        val v = erste[a.person.xref]?.takeIf { it != n }
        val eltern = if (v != null || reihe(n) >= obersteReihe) emptyList() else listOfNotNull(knoten(2 * n), knoten(2 * n + 1))
        // B6: in der obersten Reihe ein Hinweis, wenn der Baum noch Eltern kennt
        val weitere = if (mehr && v == null && reihe(n) >= obersteReihe && a.hatEltern) Texte.t(Res.string.desk_chart_more_parents) else null
        TafelPerson(a.person, eltern, n, verweis = v, hinweis = hinweise[n] ?: weitere,
            geschwister = if (eltern.isEmpty()) emptyList() else geschwister[n].orEmpty(), geschwisterLinks = n > 1 && n % 2 == 0L)
    }
    return knoten(start)
}

/** Nummern einer Linie von der Ausgangsperson (1) bis zu ihrem Ende. */
internal fun linie(art: TafelArt, ahnen: Map<Long, AhnenEintrag>): List<Long> = when (art) {
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
class TafelDaten(
    val ahnen: Map<Long, AhnenEintrag>, val nachfahren: DescendantNode?, val geschwister: Map<Long, List<Person>> = emptyMap(),
    /** Nachfahren der Grosseltern: die Seite der Mutter ([nachfahren] ist die des Vaters). */
    val mutterseite: DescendantNode? = null,
    /** Paar: die Vorfahren des Partners aus der Familie Nr. [paarFamilie] der Ausgangsperson. */
    val partnerAhnen: Map<Long, AhnenEintrag> = emptyMap(), val paarFamilie: Int = 0,
    /** Verwandtschaftstafel: die Stammpaare mit ihren Nachfahren, von links nach rechts. */
    val stammpaare: List<Stammpaar> = emptyList(),
    /** Verwandtschaftswege zweier Personen, der naechste zuerst (leer: kein gemeinsamer Vorfahr gefunden). */
    val wege: List<Weg> = emptyList(),
) {
    val weg: Weg? get() = wege.firstOrNull()
    /** Paar: die Ehepartner der Ausgangsperson zur Auswahl, in Familienfolge. */
    val partnerNamen: List<String> get() = nachfahren?.families.orEmpty().map { it.spouse?.name?.ifBlank { null } ?: "?" }
}

/**
 * Geschwister der Vorfahren (volle Geschwister aus der ersten Elternfamilie, nach Geburt) - hoechstens bis zur
 * 6. Generation. [nurProband]: nur Nr. 1. Ab Stufe 15 kamen sie mit der Ahnentafel; aeltere Module: je Person ein Abruf.
 */
suspend fun geschwisterLaden(client: WtClient, tree: String, ahnen: Map<Long, AhnenEintrag>, generationen: Int, nurProband: Boolean): Map<Long, List<Person>> =
    coroutineScope {
        ahnen.filter { (n, a) -> (if (nurProband) n == 1L else reihe(n) < minOf(generationen - 1, 6)) && a.hatEltern && !a.person.isPrivate }
            .entries.chunked(8).flatMap { gruppe ->
                gruppe.map { (n, a) ->
                    async {
                        val liste = a.geschwister ?: runCatching { client.individual(tree, a.person.xref) }.getOrNull()
                            ?.parentFamilies?.firstOrNull()?.children.orEmpty().filter { it.xref != a.person.xref }
                        n to liste.sortedBy { it.birth?.date?.jd?.takeIf { j -> j > 0 } ?: Int.MAX_VALUE }
                    }
                }.awaitAll()
            }.filter { it.second.isNotEmpty() }.toMap()
    }

/**
 * Sanduhr eines Paares: die Ausgangsperson und ihr Partner aus der gewaehlten Familie, der Mann links. Kennt der Baum
 * den Partner nicht (oder zeigt ihn nicht), steht er nur als Kasten ohne Vorfahren da; ohne Familie keine Tafel.
 */
fun paarBaum(d: TafelDaten, o: TafelOptionen): PaarTafel? {
    val familie = d.nachfahren?.families?.getOrNull(d.paarFamilie) ?: return null
    val selbst = ahnenBaum(d.ahnen, o.generationen, o.nummern, mehr = o.mehrHinweise) ?: return null
    val partner = ahnenBaum(d.partnerAhnen, o.generationen, o.nummern, mehr = o.mehrHinweise) ?: TafelPerson(familie.spouse ?: Person(xref = ""), emptyList(), 1L)
    val kinder = familie.children.map { tafelBaum(it, o.nachfahren, partner = o.partner, mehr = o.mehrHinweise) }
    return if (selbst.person.sex == "F") PaarTafel(partner, selbst, kinder) else PaarTafel(selbst, partner, kinder)
}

/** Kleinste Tiefe: bei den Nachfahren der Grosseltern mindestens bis zum Probanden. */
fun minGen(art: TafelArt) = when (art) { TafelArt.Cousins -> 3; TafelArt.Verwandt -> 1; else -> 2 }

/** Groesste Tiefe je Tafelart (Vorfahren; bei der Stammtafel die Nachfahren). */
fun maxGen(art: TafelArt) = when (art) {
    TafelArt.Stamm, TafelArt.StammSeiten -> 10
    TafelArt.Cousins -> 7
    TafelArt.Ahnen -> 10
    TafelArt.Faecher, TafelArt.Kreis, TafelArt.Zeitleiste -> 8
    TafelArt.Sanduhr, TafelArt.Paar -> 7
    TafelArt.Verwandt -> 6
    TafelArt.Weg -> 15
    TafelArt.AhnenSeiten, TafelArt.Aeltester -> 13
    TafelArt.Stammlinie, TafelArt.Mutterstamm -> 30
}

suspend fun tafelDatenLaden(client: WtClient, tree: String, xref: String, art: TafelArt, generationen: Int, geschwister: Int = 0, paarFamilie: Int = 0, nachfahren: Int = 0, zweiter: String? = null): TafelDaten = when (art) {
    TafelArt.Weg -> TafelDaten(emptyMap(), null, wege = zweiter?.takeIf { it.isNotEmpty() }?.let { wegLaden(client, tree, xref, it, generationen) }.orEmpty())
    TafelArt.Verwandt -> verwandtLaden(client, tree, xref, generationen, nachfahren)
    // Stammtafel: mit Geschwistern des Probanden (volle Geschwister der ersten Elternfamilie, nach Geburt), Nr. 1
    TafelArt.Stamm -> TafelDaten(emptyMap(), nachfahrenLaden(client, tree, xref, maxGen(art)), if (geschwister > 0) coroutineScope {
        val eltern = runCatching { client.individual(tree, xref) }.getOrNull()?.parentFamilies?.firstOrNull()?.children.orEmpty().filter { it.xref != xref }
        mapOf(1L to eltern.sortedBy { it.birth?.date?.jd?.takeIf { j -> j > 0 } ?: Int.MAX_VALUE }).filterValues { it.isNotEmpty() }
    } else emptyMap())
    TafelArt.StammSeiten -> TafelDaten(emptyMap(), nachfahrenLaden(client, tree, xref, maxGen(art)))
    // Beide Seiten ab dem Grossvater (sonst der Grossmutter, sonst dem Elternteil) in voller Tiefe, gleichzeitig
    TafelArt.Cousins -> ahnenLaden(client, tree, xref, 3).let { a ->
        fun wurzel(vararg n: Long) = n.toList().firstNotNullOfOrNull { a[it]?.person?.takeIf { p -> p.xref.isNotEmpty() && !p.isPrivate } }
        coroutineScope {
            val v = async { wurzel(4, 5, 2)?.let { nachfahrenLaden(client, tree, it.xref, maxGen(art)) } }
            val m = async { wurzel(6, 7, 3)?.let { nachfahrenLaden(client, tree, it.xref, maxGen(art)) } }
            TafelDaten(a, v.await(), mutterseite = m.await())
        }
    }
    TafelArt.Sanduhr -> TafelDaten(ahnenLaden(client, tree, xref, generationen), nachfahrenLaden(client, tree, xref, 10))
    // Erst die Familien (fuer den Partner), dann die Vorfahren beider gleichzeitig
    TafelArt.Paar -> nachfahrenLaden(client, tree, xref, 10).let { n ->
        val nr = paarFamilie.coerceIn(0, maxOf(0, n.families.size - 1))
        val partner = n.families.getOrNull(nr)?.spouse?.takeIf { it.xref.isNotEmpty() && !it.isPrivate }
        coroutineScope {
            val eigene = async { ahnenLaden(client, tree, xref, generationen) }
            val andere = async { partner?.let { ahnenLaden(client, tree, it.xref, generationen) } ?: emptyMap() }
            TafelDaten(eigene.await(), n, partnerAhnen = andere.await(), paarFamilie = nr)
        }
    }
    TafelArt.Stammlinie -> TafelDaten(ahnenLaden(client, tree, xref, generationen) { n -> n and (n - 1) == 0L }, null)
    TafelArt.Mutterstamm -> TafelDaten(ahnenLaden(client, tree, xref, generationen) { n -> (n + 1) and n == 0L }, null)
    TafelArt.Ahnen -> ahnenLaden(client, tree, xref, generationen, geschwister = geschwister > 0).let { a ->
        TafelDaten(a, null, if (geschwister > 0) geschwisterLaden(client, tree, a, generationen, geschwister == 1) else emptyMap())
    }
    TafelArt.AhnenSeiten, TafelArt.Aeltester, TafelArt.Faecher, TafelArt.Kreis -> TafelDaten(ahnenLaden(client, tree, xref, generationen), null)
    // Zeitleiste: Vor- und Nachfahren gleichzeitig, umgeschaltet wird ohne neues Laden
    TafelArt.Zeitleiste -> coroutineScope {
        val a = async { ahnenLaden(client, tree, xref, generationen) }
        val n = async { runCatching { nachfahrenLaden(client, tree, xref, generationen) }.getOrNull() }
        TafelDaten(a.await(), n.await())
    }
}

/** Der Inhalt einer Tafel aus den geladenen Daten und den Einstellungen. */
fun tafelInhalt(art: TafelArt, d: TafelDaten, o: TafelOptionen): TafelInhalt? = when (art) {
    TafelArt.Stamm -> d.nachfahren?.let { n ->
        val baum = tafelBaum(n, o.generationen, o.namenstraeger, o.partner, mehr = o.mehrHinweise, kasten = o.partner && o.partnerKasten)
        val mitGeschwistern = d.geschwister[1L]?.takeIf { o.geschwister > 0 }?.let { baum.mitGeschwister(it) } ?: baum
        TafelInhalt(nachfahren = gefiltert(mitGeschwistern, o, true))
    }
    TafelArt.StammSeiten -> d.nachfahren?.let { TafelInhalt(nachfahren = gefiltert(tafelBaum(it, o.generationen, o.namenstraeger, o.partner, mehr = o.mehrHinweise), o, true)) }
    TafelArt.Cousins -> cousinBaum(d, o)?.let { TafelInhalt(cousins = it) }
    TafelArt.Paar -> paarBaum(d, o)?.let { p -> TafelInhalt(paar = PaarTafel(gefiltert(p.mann, o, false), gefiltert(p.frau, o, false), p.kinder.filter { it.person.xref !in o.ausgeblendet }.map { gefiltert(it, o, true) })) }
    TafelArt.Verwandt -> verwandtWald(d, o).filter { it.person.xref !in o.ausgeblendet }.map { gefiltert(it, o, true) }.takeIf { it.isNotEmpty() }?.let { TafelInhalt(wald = it) }
    // Senkrecht steht der Proband klassisch unten, waagerecht links; [ausgangOben] kehrt das um ("oben" bzw. "rechts")
    TafelArt.Ahnen -> ahnenBaum(d.ahnen, o.generationen, o.nummern, geschwister = if (o.geschwister > 0) d.geschwister else emptyMap(), mehr = o.mehrHinweise)?.let { gefiltert(it, o, false) }?.let { if (o.ausgangOben != o.waagerecht) TafelInhalt(nachfahren = it) else TafelInhalt(vorfahren = it) }
    TafelArt.Sanduhr -> d.nachfahren?.let { n ->
        TafelInhalt(vorfahren = ahnenBaum(d.ahnen, o.generationen, o.nummern, mehr = o.mehrHinweise)?.let { gefiltert(it, o, false) },
            nachfahren = gefiltert(tafelBaum(n, o.nachfahren + 1, o.namenstraeger, o.partner, mehr = o.mehrHinweise), o, true))
    }
    TafelArt.Stammlinie, TafelArt.Mutterstamm, TafelArt.Aeltester ->
        linienBaum(art, d.ahnen, o.generationen, o.partner)?.let { TafelInhalt(vorfahren = it, linie = true) }
    TafelArt.AhnenSeiten -> ahnenBaum(d.ahnen, o.generationen, o.nummern)?.let { if (o.waagerecht) TafelInhalt(nachfahren = it) else TafelInhalt(vorfahren = it) }
    TafelArt.Faecher, TafelArt.Kreis -> ahnenBaum(d.ahnen, o.generationen, o.nummern)?.let { TafelInhalt(vorfahren = it) }
    TafelArt.Zeitleiste -> null
    // Ein Weg als Baum, mehrere nebeneinander
    TafelArt.Weg -> when (d.wege.size) { 0 -> null; 1 -> TafelInhalt(nachfahren = wegBaum(d.wege[0])); else -> TafelInhalt(wald = d.wege.map(::wegBaum), ohneDoppelte = true) }
}

/**
 * Seitenweise Ahnentafel: je Seite vier Generationen (waagerecht fuenf, Proband links); die Personen der letzten
 * Reihe verweisen auf ihre Seite.
 */
fun ahnenSeitenPdf(
    d: TafelDaten, o: TafelOptionen, bilder: (Person) -> BufferedImage?, privat: String, fuss: String,
    details: Map<String, de.bgghome.webtrees.nativ.api.IndividualDetail>? = null,
): Pair<PDDocument, TafelInfo>? {
    if (1L !in d.ahnen) return null
    val orte = ArrayList<Pair<Int, List<KartenOrt>>>()
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
        val (poster, pInfo) = tafelPdf(inhalt, o.copy(titel = titel, verzeichnis = false), bilder, privat, "$fuss · ${i + 1}/${wurzeln.size}")
        val nr = ziel.numberOfPages
        orte += nr to aufEinBlatt(poster, ziel, querErzwingen = true, karten = pInfo.karten)
    }
    details?.let { karteikartenFuerSeiten(ziel, orte, it, bilder, fuss) }
    return ziel to TafelInfo(personen.size, 30, 21, ziel.numberOfPages)
}

/** Titelvorgabe je Tafelart. */
fun tafelTitel(art: TafelArt, name: String, partner: String = ""): String = if (art == TafelArt.Paar && partner.isNotBlank())
    Texte.t(Res.string.desk_chart_title_couple, name, partner) else Texte.t(
    when (art) {
        TafelArt.Stamm, TafelArt.StammSeiten -> Res.string.desk_chart_title_default
        TafelArt.Cousins -> Res.string.desk_chart_title_cousins
        TafelArt.Verwandt -> Res.string.desk_chart_title_relatives
        TafelArt.Ahnen, TafelArt.AhnenSeiten -> Res.string.desk_chart_title_ancestors
        TafelArt.Sanduhr, TafelArt.Paar -> Res.string.desk_chart_title_hourglass
        TafelArt.Stammlinie -> Res.string.desk_chart_title_paternal
        TafelArt.Mutterstamm -> Res.string.desk_chart_title_maternal
        TafelArt.Aeltester -> Res.string.desk_chart_title_oldest
        TafelArt.Faecher -> Res.string.desk_chart_title_fan
        TafelArt.Zeitleiste -> Res.string.desk_chart_title_timeline
        TafelArt.Weg -> Res.string.desk_chart_title_way
        TafelArt.Kreis -> Res.string.desk_chart_title_circle
    }, name,
)

/** Die fertige Tafel als PDF: ein Blatt, bei der seitenweisen Ahnentafel A4-Seiten. */
fun tafelErzeugen(
    art: TafelArt, d: TafelDaten, o: TafelOptionen, bilder: (Person) -> BufferedImage?, privat: String, fuss: String,
    details: Map<String, de.bgghome.webtrees.nativ.api.IndividualDetail>? = null,
): Pair<PDDocument, TafelInfo>? =
    if (art == TafelArt.AhnenSeiten) ahnenSeitenPdf(d, o, bilder, privat, fuss, details)
    else if (art == TafelArt.Zeitleiste) {
        if (o.zeitNachfahren) d.nachfahren?.let { n ->
            val zweige = n.families.flatMap { it.children }.mapIndexed { i, c -> i % KASTEN_FARBEN.size to Texte.t(Res.string.desk_legend_branch, c.person.name) }.distinctBy { it.first }
            zeitleistePdf(zeitNachfahren(n, o.generationen), o, fuss, zweige)
        } else zeitleistePdf(zeitVorfahren(d.ahnen, o.generationen), o, fuss,
            (4L..7L).mapNotNull { n -> d.ahnen[n]?.let { (n - 4).toInt() to Texte.t(Res.string.desk_legend_line, it.person.name) } })
    }
    else if (art == TafelArt.StammSeiten) stammSeitenPdf(d, o, bilder, privat, fuss, details)
    else if (art == TafelArt.Faecher || art == TafelArt.Kreis) ahnenBaum(d.ahnen, o.generationen, o.nummern)?.let { faecherPdf(it, o.generationen, art == TafelArt.Kreis, o, bilder, privat, fuss, details) }
    else tafelInhalt(art, d, o)?.let { tafelPdf(it, o, bilder, privat, fuss, details) }
