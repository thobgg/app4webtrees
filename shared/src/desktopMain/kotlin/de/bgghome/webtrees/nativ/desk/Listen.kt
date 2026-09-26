package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.DescendantNode
import de.bgghome.webtrees.nativ.api.EventJson
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.LocalAppName
import de.bgghome.webtrees.nativ.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.apache.pdfbox.rendering.PDFRenderer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * Listen (Berichte), 26.09.2026: Fenster "Liste erstellen" wie "Tafel erstellen" - links die Listenarten, in der
 * Mitte die Einstellungen, rechts die Vorschau als die fertigen PDF-Seiten.
 * Vorfahren: Ahnenliste, Spitzenahnen, Stammlinie, Mutterstamm, Ahnenwertung. Nachfahren: Stammliste (Nummerierung
 * nach Saragossa, d'Aboville, Henry oder fortlaufend), Nachfahren je Generation. Dazu Ereignisliste und Personenblatt.
 */

enum class ListenArt { Ahnen, Spitzenahnen, Stammlinie, Mutterstamm, Ahnenwertung, Stamm, Nachfahrenzahl, Ereignisse, Namen, Orte, Familien, Fakten, Taufpaten, Personenblatt }

enum class Nummerierung { Saragossa, Aboville, Henry, Fortlaufend }

data class ListenOptionen(
    val generationen: Int = 6,
    val orte: Boolean = true,
    val volleDaten: Boolean = true,
    /** Stammliste: Ehepartner mit Heirat. */
    val partner: Boolean = true,
    /** Stammliste: die Kinder von Toechtern nicht weiterverfolgen. */
    val namenstraeger: Boolean = false,
    val nummerierung: Nummerierung = Nummerierung.Saragossa,
    /** Listen ueber den ganzen Baum: Ereignisarten (GEDCOM-Tags), Jahrestagskalender, Ortsfilter, Fakt, Sortierung. */
    val ereignisse: Set<String> = setOf("BIRT", "MARR", "DEAT"),
    val kalender: Boolean = false,
    val ortFilter: String = "",
    val fakt: String = "OCCU",
    val chronologisch: Boolean = false,
)

private val vorfahrenArten = setOf(ListenArt.Ahnen, ListenArt.Spitzenahnen, ListenArt.Stammlinie, ListenArt.Mutterstamm, ListenArt.Ahnenwertung)
private val nachfahrenArten = setOf(ListenArt.Stamm, ListenArt.Nachfahrenzahl)

fun listenMaxGen(art: ListenArt) = when (art) {
    ListenArt.Stammlinie, ListenArt.Mutterstamm -> 30
    ListenArt.Stamm, ListenArt.Nachfahrenzahl -> 10
    else -> 12
}

// ── Bausteine ──

private fun ereignisText(e: EventJson?, o: ListenOptionen): String = e?.let {
    val d = it.date?.let { d -> if (o.volleDaten) d.text.ifBlank { d.year.takeIf { y -> y > 0 }?.toString().orEmpty() } else d.year.takeIf { y -> y > 0 }?.toString().orEmpty() }.orEmpty()
    val ort = if (o.orte) it.place?.name.orEmpty() else ""
    listOf(d, ort).filter(String::isNotBlank).joinToString(", ")
}.orEmpty()

private fun lebensdaten(p: Person, einzug: Int, o: ListenOptionen): List<Zeile> = buildList {
    ereignisText(p.birth, o).takeIf(String::isNotBlank)?.let { add(Zeile("* $it", einzug)) }
    ereignisText(p.death, o).takeIf(String::isNotBlank)?.let { add(Zeile("† $it", einzug)) }
}

private fun name(p: Person) = p.name.ifBlank { "?" }

private fun generationName(g: Int): String = when (g) {
    0 -> Texte.t(Res.string.desk_gen_root)
    1 -> Texte.t(Res.string.desk_gen_parents)
    2 -> Texte.t(Res.string.desk_gen_grandparents)
    3 -> Texte.t(Res.string.desk_gen_great)
    4 -> Texte.t(Res.string.desk_gen_greatgreat)
    else -> Texte.t(Res.string.desk_generation, g + 1)
}

/** Grosseltern-Linie einer Kekule-Nummer ab der 2. Generation (0 = Vater des Vaters ... 3 = Mutter der Mutter). */
private fun grosselternLinie(n: Long): Int? = reihe(n).let { g -> if (g < 2) null else ((n shr (g - 2)) - 4).toInt() }

// ── Vorfahren ──

private fun ahnenliste(ahnen: Map<Long, AhnenEintrag>, o: ListenOptionen): List<Zeile> = buildList {
    val proband = ahnen[1L]?.person ?: return@buildList
    add(Zeile(Texte.t(Res.string.desk_title_ancestors, proband.name), gross = true))
    val erste = HashMap<String, Long>()
    ahnen.keys.sorted().groupBy(::reihe).forEach { (g, nummern) ->
        if (g >= o.generationen) return@forEach
        add(Zeile(""))
        add(Zeile(Texte.t(Res.string.desk_list_gen_head, generationName(g), nummern.size, 1L shl g), fett = true))
        nummern.forEach { n ->
            val p = ahnen.getValue(n).person
            val schon = erste[p.xref]?.takeIf { p.xref.isNotEmpty() }
            if (schon != null) {
                add(Zeile("$n   ${name(p)} – " + Texte.t(Res.string.desk_list_see, schon), 1))
            } else {
                if (p.xref.isNotEmpty()) erste[p.xref] = n
                add(Zeile("$n   ${name(p)}", 1, fett = true))
                addAll(lebensdaten(p, 2, o))
            }
        }
    }
}

private fun spitzenahnen(ahnen: Map<Long, AhnenEintrag>, o: ListenOptionen): List<Zeile> = buildList {
    val proband = ahnen[1L]?.person ?: return@buildList
    add(Zeile(Texte.t(Res.string.desk_title_apical, proband.name), gross = true))
    // Spitzenahn: in der Datenbank ohne Eltern (nicht nur jenseits der gewaehlten Tiefe); jede Person nur einmal
    val gesehen = HashSet<String>()
    val spitzen = ahnen.entries.filter { (n, a) -> n > 1 && !a.hatEltern && 2 * n !in ahnen && 2 * n + 1 !in ahnen }
        .sortedBy { it.key }.filter { gesehen.add(it.value.person.xref.ifEmpty { it.key.toString() }) }
    add(Zeile(Texte.t(Res.string.desk_list_apical_count, spitzen.size)))
    val linien = listOf(Res.string.desk_line_pp, Res.string.desk_line_pm, Res.string.desk_line_mp, Res.string.desk_line_mm)
    spitzen.groupBy { grosselternLinie(it.key) }.toSortedMap(compareBy { it ?: -1 }).forEach { (linie, eintraege) ->
        add(Zeile(""))
        add(Zeile(if (linie == null) generationName(1) else Texte.t(linien[linie]), fett = true))
        eintraege.forEach { (n, a) ->
            add(Zeile("$n   ${name(a.person)}  (${Texte.t(Res.string.desk_generation, reihe(n) + 1)})", 1, fett = true))
            addAll(lebensdaten(a.person, 2, o))
        }
    }
}

private fun linienliste(art: ListenArt, ahnen: Map<Long, AhnenEintrag>, o: ListenOptionen): List<Zeile> = buildList {
    val proband = ahnen[1L]?.person ?: return@buildList
    add(Zeile(Texte.t(if (art == ListenArt.Stammlinie) Res.string.desk_chart_title_paternal else Res.string.desk_chart_title_maternal, proband.name), gross = true))
    val pfad = linie(if (art == ListenArt.Stammlinie) TafelArt.Stammlinie else TafelArt.Mutterstamm, ahnen).take(o.generationen)
    pfad.forEachIndexed { i, n ->
        val p = ahnen.getValue(n).person
        add(Zeile(""))
        add(Zeile("${generationName(i)}  ·  Nr. $n", fett = true))
        add(Zeile(name(p), 1, fett = true))
        addAll(lebensdaten(p, 2, o))
        // Ehepartner: der andere Elternteil der vorigen Generation
        if (i > 0) ahnen[n xor 1L]?.person?.let { add(Zeile("⚭ ${name(it)}" + ereignisText(it.birth, o.copy(orte = false)).let { d -> if (d.isNotBlank()) " (* $d)" else "" }, 2)) }
    }
}

private fun ahnenwertung(ahnen: Map<Long, AhnenEintrag>, o: ListenOptionen): List<Zeile> = buildList {
    val proband = ahnen[1L]?.person ?: return@buildList
    add(Zeile(Texte.t(Res.string.desk_title_completeness, proband.name), gross = true))
    add(Zeile(""))
    val anteile = listOf(0.12f, 0.28f, 0.14f, 0.14f, 0.14f, 0.18f)
    add(Zeile("", spalten = listOf(Texte.t(Res.string.desk_col_gen), Texte.t(Res.string.desk_col_name), Texte.t(Res.string.desk_col_possible),
        Texte.t(Res.string.desk_col_known), Texte.t(Res.string.desk_col_share), Texte.t(Res.string.desk_col_double)), anteile = anteile, fett = true))
    var moeglich = 0L; var bekannt = 0; val alle = HashSet<String>()
    for (g in 0 until o.generationen) {
        val nummern = ahnen.keys.filter { reihe(it) == g }
        val xrefs = nummern.map { ahnen.getValue(it).person.xref }
        val doppelt = xrefs.count { it.isNotEmpty() && it in alle } + (xrefs.size - xrefs.toSet().size)
        alle += xrefs
        val m = 1L shl g
        moeglich += m; bekannt += nummern.size
        add(Zeile("", spalten = listOf("${g + 1}", generationName(g), "$m", "${nummern.size}", "%.1f %%".format(nummern.size * 100.0 / m), if (doppelt > 0) "$doppelt" else ""), anteile = anteile))
    }
    add(Zeile(""))
    add(Zeile("", spalten = listOf("", Texte.t(Res.string.desk_col_total), "$moeglich", "$bekannt", "%.1f %%".format(bekannt * 100.0 / moeglich), ""), anteile = anteile, fett = true))
    add(Zeile(""))
    add(Zeile(Texte.t(Res.string.desk_list_distinct, alle.count { it.isNotEmpty() })))
}

// ── Nachfahren ──

private fun henry(i: Int) = if (i < 10) "$i" else ('A' + (i - 10)).toString()

private fun stammliste(wurzel: DescendantNode, o: ListenOptionen): List<Zeile> = buildList {
    add(Zeile(Texte.t(Res.string.desk_title_descendants, wurzel.person.name), gross = true))
    add(Zeile(""))
    var laufend = 0
    fun knoten(k: DescendantNode, nr: String, tiefe: Int, vonNr: String?) {
        val nummer = when (o.nummerierung) {
            Nummerierung.Fortlaufend -> "${++laufend}"
            Nummerierung.Aboville -> "${'A' + tiefe}$nr"
            else -> nr
        }
        val herkunft = if (o.nummerierung == Nummerierung.Fortlaufend && vonNr != null) "  (${Texte.t(Res.string.desk_list_child_of, vonNr)})" else ""
        add(Zeile("$nummer   ${name(k.person)}$herkunft", tiefe, fett = true))
        addAll(lebensdaten(k.person, tiefe + 1, o))
        val weiter = tiefe + 1 < o.generationen && !(o.namenstraeger && tiefe > 0 && k.person.sex == "F")
        var kind = 0
        k.families.forEach { fam ->
            if (o.partner) fam.spouse?.let { sp ->
                val heirat = ereignisText(fam.marriage, o)
                add(Zeile("⚭ ${name(sp)}" + (if (sp.lifespan.isNotBlank()) " (${sp.lifespan})" else "") + (if (heirat.isNotBlank()) " – $heirat" else ""), tiefe + 1))
            }
            if (weiter) fam.children.forEach { c ->
                kind++
                val kindNr = when (o.nummerierung) { Nummerierung.Henry -> "$nr${henry(kind)}"; else -> "$nr.$kind" }
                knoten(c, kindNr, tiefe + 1, nummer)
            }
        }
    }
    knoten(wurzel, "1", 0, null)
}

private fun nachfahrenzahl(wurzel: DescendantNode, o: ListenOptionen): List<Zeile> = buildList {
    add(Zeile(Texte.t(Res.string.desk_title_desc_summary, wurzel.person.name), gross = true))
    add(Zeile(""))
    val anteile = listOf(0.14f, 0.2f, 0.16f, 0.16f, 0.16f, 0.18f)
    add(Zeile("", spalten = listOf(Texte.t(Res.string.desk_col_gen), Texte.t(Res.string.desk_col_persons), Texte.t(Res.string.desk_col_men),
        Texte.t(Res.string.desk_col_women), Texte.t(Res.string.desk_col_living), Texte.t(Res.string.desk_col_name_bearers)), anteile = anteile, fett = true))
    val nachname = wurzel.person.surname
    var ebene = listOf(wurzel)
    var summe = 0
    for (g in 0 until o.generationen) {
        if (ebene.isEmpty()) break
        val ps = ebene.map { it.person }
        summe += ps.size
        add(Zeile("", spalten = listOf("${g + 1}", "${ps.size}", "${ps.count { it.sex == "M" }}", "${ps.count { it.sex == "F" }}",
            "${ps.count { !it.isDead && it.death == null }}", "${ps.count { nachname.isNotBlank() && it.surname == nachname }}"), anteile = anteile))
        ebene = ebene.flatMap { k -> k.families.flatMap { it.children } }
    }
    add(Zeile(""))
    add(Zeile("", spalten = listOf(Texte.t(Res.string.desk_col_total), "$summe", "", "", "", ""), anteile = anteile, fett = true))
}

/** Ereignisliste: Geburten und Todesfaelle aller sichtbaren Personen, zeitlich geordnet. */
private suspend fun ereignisliste(client: WtClient, tree: String, titel: String): List<Zeile> {
    val alle = mutableListOf<Person>()
    var page: Int? = 1
    while (page != null && alle.size < 20000) {
        val r = client.individuals(tree, "", page)
        alle += r.data; page = r.nextPage
    }
    val geburt = Texte.t(Res.string.desk_birth); val tod = Texte.t(Res.string.desk_death)
    data class E(val jd: Int, val jahr: Int, val text: String)
    val ereignisse = alle.filter { !it.isPrivate }.flatMap { p ->
        listOfNotNull(
            p.birth?.date?.takeIf { it.jd > 0 }?.let { d -> E(d.jd, d.year, "${d.text}   $geburt   ${p.name}" + (p.birth.place?.name?.takeIf(String::isNotBlank)?.let { ", $it" } ?: "")) },
            p.death?.date?.takeIf { it.jd > 0 }?.let { d -> E(d.jd, d.year, "${d.text}   $tod   ${p.name}" + (p.death.place?.name?.takeIf(String::isNotBlank)?.let { ", $it" } ?: "")) },
        )
    }.sortedBy { it.jd }
    return buildList {
        add(Zeile(Texte.t(Res.string.desk_title_events, titel), gross = true))
        ereignisse.groupBy { it.jahr / 100 }.forEach { (jh, liste) ->
            add(Zeile("")); add(Zeile("${jh * 100}–${jh * 100 + 99}", fett = true))
            liste.forEach { add(Zeile(it.text, 1)) }
        }
    }
}

/** Die Zeilen einer Liste; laedt, was die Art braucht. */
suspend fun listenZeilen(art: ListenArt, client: WtClient, tree: String, treeTitle: String, root: String, o: ListenOptionen): List<Zeile> = when (art) {
    ListenArt.Ahnen -> ahnenliste(ahnenLaden(client, tree, root, o.generationen), o)
    ListenArt.Spitzenahnen -> spitzenahnen(ahnenLaden(client, tree, root, o.generationen), o)
    ListenArt.Ahnenwertung -> ahnenwertung(ahnenLaden(client, tree, root, o.generationen), o)
    ListenArt.Stammlinie -> linienliste(art, ahnenLaden(client, tree, root, o.generationen) { n -> n and (n - 1) == 0L }, o)
    ListenArt.Mutterstamm -> linienliste(art, ahnenLaden(client, tree, root, o.generationen) { n -> (n + 1) and n == 0L }, o)
    ListenArt.Stamm -> stammliste(nachfahrenLaden(client, tree, root, o.generationen), o)
    ListenArt.Nachfahrenzahl -> nachfahrenzahl(nachfahrenLaden(client, tree, root, o.generationen), o)
    // Mit Export (Stufe 17) alle Ereignisse und Filter, sonst wie bisher Geburten und Todesfaelle aus der Personenliste
    ListenArt.Ereignisse -> BaumSpeicher.holen(client, tree, Int.MAX_VALUE / 64)?.let { ereignislisteBaum(it, treeTitle, o) } ?: ereignisliste(client, tree, treeTitle)
    ListenArt.Namen -> namensliste(ganzerBaum(client, tree), treeTitle)
    ListenArt.Orte -> ortsliste(ganzerBaum(client, tree), treeTitle, o)
    ListenArt.Familien -> familienliste(ganzerBaum(client, tree), treeTitle, o)
    ListenArt.Fakten -> faktenliste(ganzerBaum(client, tree), treeTitle, o)
    ListenArt.Taufpaten -> taufpaten(ganzerBaum(client, tree), treeTitle, o)
    ListenArt.Personenblatt -> personenblattZeilen(client.individual(tree, root))
}

// ── Fenster ──

private val listenTexte: Map<ListenArt, Pair<StringResource, StringResource>> = mapOf(
    ListenArt.Ahnen to (Res.string.desk_list_ancestors to Res.string.desk_list_ancestors_hint),
    ListenArt.Spitzenahnen to (Res.string.desk_list_apical to Res.string.desk_list_apical_hint),
    ListenArt.Stammlinie to (Res.string.desk_chart_paternal to Res.string.desk_list_paternal_hint),
    ListenArt.Mutterstamm to (Res.string.desk_chart_maternal to Res.string.desk_list_maternal_hint),
    ListenArt.Ahnenwertung to (Res.string.desk_list_completeness to Res.string.desk_list_completeness_hint),
    ListenArt.Stamm to (Res.string.desk_list_descendants to Res.string.desk_list_descendants_hint),
    ListenArt.Nachfahrenzahl to (Res.string.desk_list_desc_summary to Res.string.desk_list_desc_summary_hint),
    ListenArt.Ereignisse to (Res.string.desk_list_events to Res.string.desk_list_events_hint),
    ListenArt.Namen to (Res.string.desk_list_names to Res.string.desk_list_names_hint),
    ListenArt.Orte to (Res.string.desk_list_places to Res.string.desk_list_places_hint),
    ListenArt.Familien to (Res.string.desk_list_families to Res.string.desk_list_families_hint),
    ListenArt.Fakten to (Res.string.desk_list_facts to Res.string.desk_list_facts_hint),
    ListenArt.Taufpaten to (Res.string.desk_list_godparents to Res.string.desk_list_godparents_hint),
    ListenArt.Personenblatt to (Res.string.desk_list_sheet to Res.string.desk_list_sheet_hint),
)

private val nummerierungNamen = mapOf(
    Nummerierung.Saragossa to "1.2.3", Nummerierung.Aboville to "d'Aboville (C1.2.3)", Nummerierung.Henry to "Henry (123)",
)

/** Die Einstellungen bleiben je Listenart erhalten. */
private object ListenWahl {
    private val prefs get() = DeskLayout.prefs
    private fun k(art: ListenArt, n: String) = "liste_${art.name.lowercase()}_$n"
    fun laden(art: ListenArt) = ListenOptionen(
        generationen = (prefs.getString(k(art, "gen"), null)?.toIntOrNull() ?: when (art) { ListenArt.Stamm, ListenArt.Nachfahrenzahl -> 5; ListenArt.Stammlinie, ListenArt.Mutterstamm -> 20; else -> 6 })
            .coerceIn(2, listenMaxGen(art)),
        orte = prefs.getBoolean(k(art, "orte"), true), volleDaten = prefs.getBoolean(k(art, "voll"), true),
        partner = prefs.getBoolean(k(art, "partner"), true), namenstraeger = prefs.getBoolean(k(art, "namen"), false),
        nummerierung = Nummerierung.entries.firstOrNull { it.name == prefs.getString(k(art, "nr"), null) } ?: Nummerierung.Saragossa,
        ereignisse = prefs.getString(k(art, "ev"), null)?.split(',')?.filter(String::isNotBlank)?.toSet() ?: setOf("BIRT", "MARR", "DEAT"),
        kalender = prefs.getBoolean(k(art, "kal"), false), ortFilter = prefs.getString(k(art, "ort"), null).orEmpty(),
        fakt = prefs.getString(k(art, "fakt"), null) ?: "OCCU", chronologisch = prefs.getBoolean(k(art, "chrono"), false),
    )
    fun sichern(art: ListenArt, o: ListenOptionen) {
        prefs.putString(k(art, "gen"), o.generationen.toString()); prefs.putBoolean(k(art, "orte"), o.orte); prefs.putBoolean(k(art, "voll"), o.volleDaten)
        prefs.putBoolean(k(art, "partner"), o.partner); prefs.putBoolean(k(art, "namen"), o.namenstraeger); prefs.putString(k(art, "nr"), o.nummerierung.name)
        prefs.putString(k(art, "ev"), o.ereignisse.joinToString(",")); prefs.putBoolean(k(art, "kal"), o.kalender); prefs.putString(k(art, "ort"), o.ortFilter)
        prefs.putString(k(art, "fakt"), o.fakt); prefs.putBoolean(k(art, "chrono"), o.chronologisch)
    }
}

/** Fenster "Liste erstellen": Listenart, Einstellungen, Vorschau der fertigen Seiten; Drucken und PDF. */
@Composable
fun ListenFenster(start: ListenArt, state: UiState, viewModel: AppViewModel, onClose: () -> Unit) {
    val appName = LocalAppName.current
    val baum = state.tree?.title.orEmpty()
    var art by remember { mutableStateOf(start) }
    var o by remember(art) { mutableStateOf(ListenWahl.laden(art)) }
    LaunchedEffect(art, o) { ListenWahl.sichern(art, o) }
    val zeilen by produceState<Result<List<Zeile>>?>(null, art, o, state.root, state.tree?.name) {
        value = null
        delay(150)
        val tree = state.tree; val root = state.root
        value = if (tree == null || root == null) null else withContext(Dispatchers.IO) {
            runCatching { listenZeilen(art, viewModel.client, tree.name, tree.title, root, o) }
        }
    }
    val titel = zeilen?.getOrNull()?.firstOrNull()?.text ?: stringResource(listenTexte.getValue(art).first)
    // Vorschau: die ersten Seiten des fertigen PDFs als Bilder
    var breitePx by remember { mutableStateOf(800) }
    val seiten by produceState<Pair<List<ImageBitmap>, Int>?>(null, zeilen, breitePx) {
        val z = zeilen?.getOrNull() ?: run { value = null; return@produceState }
        value = withContext(Dispatchers.Default) {
            val bytes = java.io.ByteArrayOutputStream().also { out -> listenPdf(z, appName, baum).use { it.save(out) } }.toByteArray()
            org.apache.pdfbox.Loader.loadPDF(bytes).use { d ->
                val r = PDFRenderer(d)
                val skala = breitePx / d.getPage(0).mediaBox.width
                (0 until minOf(d.numberOfPages, 30)).map { r.renderImage(it, skala).toComposeImageBitmap() } to d.numberOfPages
            }
        }
    }

    DialogWindow(
        onCloseRequest = onClose, title = stringResource(Res.string.desk_list_window),
        state = rememberDialogState(width = 1180.dp, height = 860.dp),
        onPreviewKeyEvent = { e -> if (e.key == Key.Escape) { onClose(); true } else false },
    ) {
        DeskTheme {
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Column(Modifier.width(210.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp)) {
                    listOf(
                        Res.string.desk_chart_group_ancestors to listOf(ListenArt.Ahnen, ListenArt.Spitzenahnen, ListenArt.Stammlinie, ListenArt.Mutterstamm, ListenArt.Ahnenwertung),
                        Res.string.desk_chart_group_descendants to listOf(ListenArt.Stamm, ListenArt.Nachfahrenzahl),
                        Res.string.desk_list_group_tree to listOf(ListenArt.Ereignisse, ListenArt.Namen, ListenArt.Orte, ListenArt.Familien, ListenArt.Fakten, ListenArt.Taufpaten, ListenArt.Personenblatt),
                    ).forEachIndexed { i, (gruppe, arten) ->
                        if (i > 0) Spacer(Modifier.height(8.dp))
                        ArtGruppe(stringResource(gruppe))
                        arten.forEach { a -> ArtEintrag(stringResource(listenTexte.getValue(a).first), art == a) { art = a } }
                    }
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(listenTexte.getValue(art).first), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(listenTexte.getValue(art).second), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (art != ListenArt.Ereignisse) {
                        val wer = state.detail?.takeIf { it.person.xref == state.root }?.person?.name ?: state.people.firstOrNull { it.xref == state.root }?.name.orEmpty()
                        Einstellung(stringResource(Res.string.desk_chart_person)) { Text(wer, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
                    }
                    if (art in vorfahrenArten || art in nachfahrenArten) Einstellung(stringResource(Res.string.desk_chart_generations)) {
                        Auswahl(o.generationen.toString(), (2..listenMaxGen(art)).map { it.toString() }) { o = o.copy(generationen = it.toInt()) }
                    }
                    if (art == ListenArt.Stamm) {
                        Einstellung(stringResource(Res.string.desk_list_numbering)) {
                            val namen = Nummerierung.entries.associateWith { nummerierungNamen[it] ?: stringResource(Res.string.desk_list_numbering_serial) }
                            Auswahl(namen.getValue(o.nummerierung), namen.values.toList()) { w -> o = o.copy(nummerierung = namen.entries.first { it.value == w }.key) }
                        }
                        Haken(stringResource(Res.string.desk_chart_spouses), o.partner) { o = o.copy(partner = it) }
                        Haken(stringResource(Res.string.desk_chart_name_bearers), o.namenstraeger) { o = o.copy(namenstraeger = it) }
                    }
                    if (art == ListenArt.Ereignisse) {
                        listOf("BIRT" to Res.string.desk_ev_birth, "CHR" to Res.string.desk_ev_baptism, "MARR" to Res.string.desk_ev_marriage,
                            "DEAT" to Res.string.desk_ev_death, "BURI" to Res.string.desk_ev_burial).forEach { (tag, name) ->
                            Haken(stringResource(name), tag in o.ereignisse) { an -> o = o.copy(ereignisse = if (an) o.ereignisse + tag else o.ereignisse - tag) }
                        }
                        Haken(stringResource(Res.string.desk_list_calendar), o.kalender) { o = o.copy(kalender = it) }
                    }
                    if (art == ListenArt.Familien) Haken(stringResource(Res.string.desk_book_sort_chrono), o.chronologisch) { o = o.copy(chronologisch = it) }
                    if (art == ListenArt.Fakten) Einstellung(stringResource(Res.string.desk_list_fact)) {
                        val werte = listOf("OCCU" to stringResource(Res.string.desk_list_fact_occupation), "RELI" to stringResource(Res.string.desk_list_fact_religion))
                        Auswahl(werte.first { it.first == o.fakt }.second, werte.map { it.second }) { w -> o = o.copy(fakt = werte.first { it.second == w }.first) }
                    }
                    if (art in setOf(ListenArt.Ereignisse, ListenArt.Orte, ListenArt.Familien, ListenArt.Taufpaten)) {
                        androidx.compose.material3.OutlinedTextField(o.ortFilter, { o = o.copy(ortFilter = it) }, label = { Text(stringResource(Res.string.desk_list_place_filter)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    if (art !in setOf(ListenArt.Ahnenwertung, ListenArt.Nachfahrenzahl, ListenArt.Ereignisse, ListenArt.Personenblatt, ListenArt.Namen, ListenArt.Orte,
                            ListenArt.Familien, ListenArt.Fakten, ListenArt.Taufpaten)) {
                        Haken(stringResource(Res.string.desk_chart_places), o.orte) { o = o.copy(orte = it) }
                        Haken(stringResource(Res.string.desk_chart_full_dates), o.volleDaten) { o = o.copy(volleDaten = it) }
                    }
                    seiten?.second?.let { Text(stringResource(Res.string.desk_list_pages, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val z = zeilen?.getOrNull()
                    Knopf(stringResource(Res.string.desk_print), !z.isNullOrEmpty()) { z?.let { drucken(listenPdf(it, appName, baum), titel) } }
                    Knopf(stringResource(Res.string.desk_save_pdf), !z.isNullOrEmpty()) { z?.let { alsPdf(listenPdf(it, appName, baum), titel) } }
                    Spacer(Modifier.height(4.dp))
                    Knopf(stringResource(Res.string.action_close), true, onClose)
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().background(androidx.compose.ui.graphics.Color(0xFF8C8F8E)), contentAlignment = Alignment.TopCenter) {
                    val px = with(LocalDensity.current) { (maxWidth - 48.dp).roundToPx() }
                    LaunchedEffect(px) { breitePx = px.coerceAtLeast(300) }
                    val fehler = zeilen?.exceptionOrNull()
                    val s = seiten
                    when {
                        fehler != null -> Text(fehler.message ?: "?", Modifier.padding(24.dp), color = androidx.compose.ui.graphics.Color.White)
                        s == null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
                            Text(stringResource(Res.string.desk_chart_loading_any), Modifier.padding(top = 8.dp), color = androidx.compose.ui.graphics.Color.White)
                        }
                        else -> Box(Modifier.fillMaxSize()) {
                            val scroll = rememberScrollState()
                            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                s.first.forEach { Image(it, contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth()) }
                                if (s.second > s.first.size) Text(stringResource(Res.string.desk_list_more_pages, s.second - s.first.size), color = androidx.compose.ui.graphics.Color.White)
                            }
                            SenkrechteLeiste(scroll)
                        }
                    }
                }
            }
        }
    }
}
