package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.api.DnaAbdeckung
import de.bgghome.webtrees.nativ.api.DnaEintrag
import de.bgghome.webtrees.nativ.api.DnaGestuetzt
import de.bgghome.webtrees.nativ.api.dnaDatum
import de.bgghome.webtrees.nativ.api.selbstGetestet
import de.bgghome.webtrees.nativ.api.DnaLinienEintraege
import de.bgghome.webtrees.nativ.api.DnaPerson
import de.bgghome.webtrees.nativ.api.DnaTester
import de.bgghome.webtrees.nativ.api.DnaTesterLinie
import de.bgghome.webtrees.nativ.api.DnaTreffer
import de.bgghome.webtrees.nativ.api.DnaUebersicht
import de.bgghome.webtrees.nativ.api.DnaWarnung
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.api.kennungen
import de.bgghome.webtrees.nativ.res.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

/*
 * Die DNA-Ansicht einer Person, fuer Handy und PC: Vererbungslinien (DnaLinien.kt), eingetragene Tests und Haplogruppen,
 * Treffer mit dem, was der Baum dazu sagt, und wer fuer eine Linie testen koennte. Alles kommt aus dem webtrees-Modul
 * dna4webtrees; hier wird nur gezeigt. Eintragen geht am PC (eigene Formulare) und im Browser.
 */

/** Die Bereiche der Ansicht; [Abdeckung], [Gestuetzt] und [Baum] nur am PC. */
enum class DnaBereich { Linien, Eintraege, Treffer, Tester, Abdeckung, Gestuetzt, Baum }

/** Centimorgan zur Anzeige: ganze Werte ohne Komma, sonst eine Stelle in der Schreibweise der Sprache ("849,9"). */
fun cmText(wert: Double, sprache: Locale = Locale.getDefault()): String =
    if (wert == Math.floor(wert)) wert.toLong().toString() else String.format(sprache, "%.1f", wert)

/** Die Personen zu den Kennungen einer Modulantwort; wer nicht zu laden ist, fehlt. */
suspend fun dnaPersonenLaden(client: WtClient, tree: String, kennungen: Set<String>): Map<String, Person> = coroutineScope {
    val aus = HashMap<String, Person>()
    kennungen.chunked(8).forEach { gruppe ->
        gruppe.map { x -> async { runCatching { client.individual(tree, x).person }.getOrNull() } }.awaitAll().filterNotNull().forEach { aus[it.xref] = it }
    }
    aus
}

/** Antwort des Moduls samt den Personen dazu. */
private class Geladen<T>(val daten: T, val personen: Map<String, Person>) {
    fun person(xref: String): Person = personen[xref] ?: Person(xref, name = xref)
    fun name(xref: String): String = personen[xref]?.takeIf { !it.isPrivate }?.name?.ifEmpty { null } ?: xref
}

/**
 * Bereichswahl und der gewaehlte Bereich. [onPerson]: Tipp bzw. Klick auf eine Person. [zuerst]: womit die Ansicht aufgeht.
 * [mitBaum]: auch die Bereiche des PCs anbieten (Abdeckung, gestuetzte Vorfahren, ganzer Stammbaum). [stand]: hochzaehlen,
 * wenn etwas eingetragen wurde - dann laden Tests, Treffer und Uebersicht neu. [onLink]: Seite im Browser oeffnen.
 */
@Composable
fun DnaAnsicht(
    client: WtClient, tree: String, xref: String, onPerson: (String) -> Unit, modifier: Modifier = Modifier,
    zuerst: DnaBereich = DnaBereich.Linien, mitBaum: Boolean = false, stand: Int = 0, onLink: (String) -> Unit = {},
) {
    var bereich by remember { mutableStateOf(zuerst) }
    Column(modifier.fillMaxSize()) {
        // Eine Zeile zum Wischen: vier Knoepfe brechen am Handy sonst in zwei Zeilen um
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                DnaBereich.Linien to Res.string.dna_area_lines, DnaBereich.Eintraege to Res.string.dna_area_entries,
                DnaBereich.Treffer to Res.string.dna_area_matches, DnaBereich.Tester to Res.string.dna_area_testers,
            ).plus(if (mitBaum) listOf(DnaBereich.Abdeckung to Res.string.dna_area_coverage, DnaBereich.Gestuetzt to Res.string.dna_area_evidence,
                DnaBereich.Baum to Res.string.dna_area_tree) else emptyList()).forEach { (b, text) ->
                FilterChip(selected = bereich == b, onClick = { bereich = b }, label = { Text(stringResource(text), maxLines = 1, softWrap = false) })
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        when (bereich) {
            DnaBereich.Linien -> DnaLinienInhalt(client, tree, xref, onPerson, Modifier.weight(1f))
            DnaBereich.Tester -> TesterBereich(client, tree, xref, stand, onPerson, Modifier.weight(1f))
            DnaBereich.Baum -> BaumBereich(client, tree, stand, onPerson, onLink, Modifier.weight(1f))
            DnaBereich.Abdeckung -> AbdeckungBereich(client, tree, xref, stand, onPerson, Modifier.weight(1f))
            DnaBereich.Gestuetzt -> GestuetztBereich(client, tree, xref, stand, onPerson, Modifier.weight(1f))
            else -> PersonBereich(client, tree, xref, bereich, stand, onPerson, onLink, Modifier.weight(1f))
        }
    }
}

/** Liste mit Ladezeichen und Fehlertext davor - fuer alle Bereiche gleich. */
@Composable
private fun <T> Bereichsliste(ergebnis: Result<Geladen<T>>?, modifier: Modifier, inhalt: LazyListScope.(Geladen<T>) -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        when {
            ergebnis == null -> item("laden") { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            ergebnis.isFailure -> item("fehler") { Text(meldung(ergebnis.exceptionOrNull()), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
            else -> inhalt(ergebnis.getOrThrow())
        }
    }
}

@Composable
private fun PersonBereich(client: WtClient, tree: String, xref: String, bereich: DnaBereich, stand: Int, onPerson: (String) -> Unit, onLink: (String) -> Unit, modifier: Modifier) {
    val ergebnis by produceState<Result<Geladen<DnaPerson>>?>(null, tree, xref, stand) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { client.dnaPerson(tree, xref).let { Geladen(it, dnaPersonenLaden(client, tree, it.kennungen())) } } }
    }
    Bereichsliste(ergebnis, modifier) { g ->
        if (bereich == DnaBereich.Eintraege) eintraege(g, onPerson, onLink) else treffer(g.daten.matches, g, onPerson, onLink, mitErfasser = false)
    }
}

private fun LazyListScope.eintraege(g: Geladen<DnaPerson>, onPerson: (String) -> Unit, onLink: (String) -> Unit) {
    val d = g.daten
    if (d.entries.isEmpty()) item("keine") { Absatz(stringResource(Res.string.dna_entries_none)) }
    items(d.entries, key = { "e" + it.factId }) { EintragZeile(it, onLink = onLink) }
    listOf("y" to Res.string.dna_same_line_y, "mt" to Res.string.dna_same_line_mt).forEach { (linie, titel) ->
        val l: DnaLinienEintraege = d.lines[linie] ?: return@forEach
        if (l.entries.isEmpty() && l.contradiction == null) return@forEach
        item("t$linie") { Titel(stringResource(titel)) }
        l.contradiction?.let { w -> item("w$linie") { Warnzeile(stringResource(Res.string.dna_contradiction, w.names.joinToString(", "))) } }
        items(l.entries, key = { "l$linie" + it.xref + it.factId }) { e ->
            Column {
                PersonRow(g.person(e.xref), onClick = { onPerson(e.xref) })
                EintragZeile(e, eingerueckt = true, onLink = onLink)
            }
        }
    }
}

/** Die Art eines Eintrags in der Sprache der App; was das Modul nicht einordnet, bleibt so, wie es im Stammbaum steht. */
@Composable
private fun artText(e: DnaEintrag): String = when (e.kind) {
    "test" -> stringResource(Res.string.dna_kind_test)
    "y-haplogroup" -> stringResource(Res.string.dna_kind_y_haplogroup)
    "mt-haplogroup" -> stringResource(Res.string.dna_kind_mt_haplogroup)
    "y-other" -> stringResource(Res.string.dna_kind_y)
    "mt-other" -> stringResource(Res.string.dna_kind_mt)
    "autosomal" -> stringResource(Res.string.dna_kind_autosomal)
    else -> e.type
}

/** Ein Eintrag: Wert gross, darunter Art, Anbieter und Datum; ein aus SNPs berechneter Zweig, Verweise und - fuer Bearbeiter - der Schutz. */
@Composable
private fun EintragZeile(e: DnaEintrag, eingerueckt: Boolean = false, onLink: (String) -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(start = if (eingerueckt) 76.dp else 16.dp, end = 16.dp, top = if (eingerueckt) 0.dp else 8.dp, bottom = 8.dp)) {
        Text(e.value.ifEmpty { artText(e) }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Klein(listOf(artText(e).takeIf { e.value.isNotEmpty() }, e.provider, dnaDatum(e.date)).filter { !it.isNullOrBlank() }.joinToString(" · "))
        e.calculated?.takeIf { it.clade.isNotEmpty() }?.let { Klein(stringResource(Res.string.dna_calculated, it.clade, it.tree)) }
        schutzText(e.restriction)?.let { Klein(it) }
        Verweise(e.links.map { it.site to it.url }, onLink)
    }
}

/** Seiten im Netz als kleine Knoepfe; geoeffnet wird im Browser. */
@Composable
private fun Verweise(liste: List<Pair<String, String>>, onLink: (String) -> Unit) {
    if (liste.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        liste.forEach { (name, url) ->
            androidx.compose.material3.TextButton(onClick = { onLink(url) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                Text("$name ↗", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun schutzText(schutz: String?): String? = when (schutz) {
    "confidential" -> stringResource(Res.string.dna_restriction_confidential)
    "privacy" -> stringResource(Res.string.dna_restriction_privacy)
    "none" -> stringResource(Res.string.dna_restriction_none)
    else -> null
}

/** [mitErfasser]: in der Uebersicht des Baums steht ueber jedem Treffer, bei wem er eingetragen ist. */
private fun LazyListScope.treffer(liste: List<DnaTreffer>, g: Geladen<*>, onPerson: (String) -> Unit, onLink: (String) -> Unit, mitErfasser: Boolean) {
    if (liste.isEmpty() && !mitErfasser) item("keine") { Absatz(stringResource(Res.string.dna_matches_none)) }
    items(liste, key = { "m" + it.recordedAt + it.factId }) { m ->
        if (mitErfasser) Klein(g.name(m.recordedAt) + "  ↔", Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
        TrefferZeile(m, g, onPerson, onLink)
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
    // Die ueblichen Werte stehen unter CC BY - die Quelle gehoert dazu, sobald einer gezeigt wird
    if (liste.any { it.usual != null || it.candidates.isNotEmpty() }) item("quelle") { Klein(stringResource(Res.string.dna_usual_source), Modifier.padding(16.dp)) }
}

@Composable
private fun TrefferZeile(m: DnaTreffer, g: Geladen<*>, onPerson: (String) -> Unit, onLink: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        val andere = m.other
        when {
            andere != null -> PersonRow(g.person(andere), onClick = { onPerson(andere) })
            m.private -> PersonRow(Person("", isPrivate = true))
            else -> Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
                Text(m.name.orEmpty().ifEmpty { "…" }, style = MaterialTheme.typography.bodyLarge)
                Klein(stringResource(Res.string.dna_match_not_in_tree))
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val mengen = listOfNotNull(
                stringResource(Res.string.dna_match_cm, cmText(m.cm)),
                m.segments?.let { stringResource(Res.string.dna_match_segments, it) },
                m.longest?.let { stringResource(Res.string.dna_match_longest, cmText(it)) },
                m.provider.ifBlank { null }, m.date.ifBlank { null }?.let { dnaDatum(it) },
            )
            Text(mengen.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            val v = m.relationship
            if (v != null) {
                val vorfahren = m.commonAncestors.joinToString(", ") { g.name(it) }
                Text(v.text + if (vorfahren.isNotEmpty()) " – " + stringResource(Res.string.dna_match_common, vorfahren) else "", style = MaterialTheme.typography.bodyMedium)
            } else if (andere != null) Klein(stringResource(Res.string.dna_match_no_relationship))
            m.usual?.let { Klein(stringResource(Res.string.dna_match_usual, cmText(it.average), cmText(it.minimum), cmText(it.maximum))) }
            if (m.candidates.isNotEmpty()) Klein(stringResource(Res.string.dna_match_candidates, m.candidates.take(8).joinToString("; ") { it.text }))
            m.x?.takeIf { it.possible }?.let { x ->
                Klein(x.nearestCommonAncestor?.let { stringResource(Res.string.dna_match_x_via, g.name(it)) } ?: stringResource(Res.string.dna_match_x_possible))
            }
            m.warnings.forEach { w -> warnText(w)?.let { Warnzeile(it, Modifier) } }
            Verweise(listOfNotNull(m.sharedCmUrl?.let { stringResource(Res.string.dna_link_shared_cm) to it }), onLink)
        }
    }
}

@Composable
private fun warnText(code: String): String? = when (code) {
    DnaWarnung.OHNE_SCHUTZ -> stringResource(Res.string.dna_warn_unrestricted)
    DnaWarnung.AUSSERHALB -> stringResource(Res.string.dna_warn_outside)
    DnaWarnung.MEHRFACH -> stringResource(Res.string.dna_warn_multiple)
    DnaWarnung.ZWEITE -> stringResource(Res.string.dna_warn_distant)
    DnaWarnung.X_OHNE_VORFAHR -> stringResource(Res.string.dna_warn_x)
    DnaWarnung.UNWAHRSCHEINLICH -> stringResource(Res.string.dna_warn_unlikely)
    // Codes neuerer Modulfassungen, die die App noch nicht kennt, erscheinen wenigstens als Code statt gar nicht
    else -> code
}

@Composable
private fun TesterBereich(client: WtClient, tree: String, xref: String, stand: Int, onPerson: (String) -> Unit, modifier: Modifier) {
    val ergebnis by produceState<Result<Geladen<DnaTester>>?>(null, tree, xref, stand) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { client.dnaTester(tree, xref).let { Geladen(it, dnaPersonenLaden(client, tree, it.kennungen())) } } }
    }
    Bereichsliste(ergebnis, modifier) { g ->
        item("hinweis") { Absatz(stringResource(Res.string.dna_testers_hint)) }
        listOf("y" to Res.string.dna_line_y, "mt" to Res.string.dna_line_mt).forEach { (linie, titel) ->
            val l: DnaTesterLinie = g.daten.lines[linie] ?: return@forEach
            item("t$linie") {
                Titel(stringResource(titel))
                l.earliestAncestor?.let { Klein(stringResource(Res.string.dna_testers_earliest, g.name(it), l.onLine), Modifier.padding(horizontal = 16.dp)) }
            }
            if (l.living.isEmpty()) item("k$linie") { Absatz(stringResource(Res.string.dna_testers_none)) }
            items(l.living, key = { "p$linie" + it.xref }) { k ->
                PersonRow(g.person(k.xref), onClick = { onPerson(k.xref) },
                    trailing = if (k.hasEntry) ({ Text(stringResource(Res.string.dna_has_entry), style = MaterialTheme.typography.labelMedium) }) else null)
            }
        }
    }
}

/** Alles zu DNA im Stammbaum: erst die Treffer mit ihrer Einordnung, dann Tests und Haplogruppen je Person. */
@Composable
private fun BaumBereich(client: WtClient, tree: String, stand: Int, onPerson: (String) -> Unit, onLink: (String) -> Unit, modifier: Modifier) {
    val ergebnis by produceState<Result<Geladen<DnaUebersicht>>?>(null, tree, stand) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { client.dnaUebersicht(tree).let { Geladen(it, dnaPersonenLaden(client, tree, it.kennungen())) } } }
    }
    Bereichsliste(ergebnis, modifier) { g ->
        val d = g.daten
        if (d.matches.isEmpty() && d.entries.isEmpty()) item("leer") { Absatz(stringResource(Res.string.dna_overview_none)) }
        if (d.matches.isNotEmpty()) {
            item("tm") { Titel(stringResource(Res.string.dna_overview_matches)) }
            treffer(d.matches, g, onPerson, onLink, mitErfasser = true)
        }
        if (d.entries.isNotEmpty()) {
            item("te") { Titel(stringResource(Res.string.dna_overview_entries)) }
            // je Person einmal der Name, darunter ihre Eintraege
            d.entries.groupBy { it.xref }.forEach { (x, liste) ->
                item("p$x") { PersonRow(g.person(x), onClick = { onPerson(x) }) }
                items(liste, key = { "o$x" + it.factId }) { EintragZeile(it, eingerueckt = true, onLink = onLink) }
            }
        }
    }
}

/** Abdeckung: ein Prozentwert, wer schon getestet ist und wessen Test am meisten braechte. */
@Composable
private fun AbdeckungBereich(client: WtClient, tree: String, xref: String, stand: Int, onPerson: (String) -> Unit, modifier: Modifier) {
    val ergebnis by produceState<Result<Geladen<DnaAbdeckung>>?>(null, tree, xref, stand) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { client.dnaAbdeckung(tree, xref).let { Geladen(it, dnaPersonenLaden(client, tree, it.kennungen())) } } }
    }
    // Selbst getestet: die Abdeckung ist dann vollstaendig, unabhaengig von den Nachkommen. Das Modul sagt es ab
    // JSON-Stufe 8 (selfTested); bei aelteren Fassungen leitet die App es aus den Eintraegen der Person ab.
    val abgeleitet by produceState<Boolean?>(null, tree, xref, stand, ergebnis) {
        val a = ergebnis?.getOrNull()?.daten ?: return@produceState
        value = a.selfTested ?: withContext(Dispatchers.IO) { runCatching { client.dnaPerson(tree, xref).selbstGetestet() }.getOrDefault(false) }
    }
    val selbst = abgeleitet == true
    Bereichsliste(ergebnis, modifier) { g ->
        val a = g.daten
        item("wert") {
            Column(Modifier.padding(16.dp)) {
                if (selbst && a.coverage >= 1.0) {
                    Text(stringResource(Res.string.dna_coverage_self), style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(stringResource(Res.string.dna_coverage_value, prozent(a.coverage)), style = MaterialTheme.typography.titleMedium)
                    Klein(stringResource(Res.string.dna_coverage_hint, a.descendants, a.generations))
                }
            }
        }
        if (a.tested.isNotEmpty()) {
            item("tt") { Titel(stringResource(Res.string.dna_coverage_tested)) }
            items(a.tested, key = { "t$it" }) { x -> PersonRow(g.person(x), onClick = { onPerson(x) }) }
        }
        // Selbst getestet: kein Test eines Nachkommen bringt noch etwas - der Abschnitt entfaellt
        if (selbst && a.coverage >= 1.0) return@Bereichsliste
        item("tk") { Titel(stringResource(Res.string.dna_coverage_candidates)) }
        if (a.candidates.isEmpty()) item("kk") { Absatz(stringResource(Res.string.dna_coverage_no_candidates)) }
        items(a.candidates, key = { "k" + it.xref }) { k ->
            PersonRow(g.person(k.xref), onClick = { onPerson(k.xref) },
                trailing = { Text("+" + prozent(k.gain), style = MaterialTheme.typography.labelMedium) })
        }
    }
}

/** "43,5 %" in der Schreibweise der Sprache. */
private fun prozent(anteil: Double): String = java.text.NumberFormat.getPercentInstance().apply { maximumFractionDigits = 1 }.format(anteil)

/**
 * Gestuetzte Vorfahren: je Generation die Verbindungen Kind - Elternteil, gestuetzte mit den Treffern, die fuer sie sprechen.
 * Das Wort ist bewusst "gestuetzt": die DNA passt zum Baum, sie beweist ihn nicht.
 */
@Composable
private fun GestuetztBereich(client: WtClient, tree: String, xref: String, stand: Int, onPerson: (String) -> Unit, modifier: Modifier) {
    val ergebnis by produceState<Result<Geladen<DnaGestuetzt>>?>(null, tree, xref, stand) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { client.dnaGestuetzt(tree, xref).let { Geladen(it, dnaPersonenLaden(client, tree, it.kennungen())) } } }
    }
    Bereichsliste(ergebnis, modifier) { g ->
        val d = g.daten
        item("hinweis") {
            Absatz(stringResource(Res.string.dna_evidence_summary, d.links.count { it.supported }, d.links.size, d.generations))
            Klein(stringResource(Res.string.dna_evidence_hint), Modifier.padding(horizontal = 16.dp))
        }
        d.links.groupBy { it.generation }.toSortedMap().forEach { (gen, liste) ->
            item("g$gen") { Titel(stringResource(Res.string.dna_generation, gen)) }
            items(liste, key = { "v" + it.child + it.parent }) { v ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        (if (v.supported) "✓ " else "· ") + g.name(v.child) + "  →  " + g.name(v.parent),
                        Modifier.clickable { onPerson(v.parent) },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (v.supported) FontWeight.Medium else FontWeight.Normal,
                        color = if (v.supported) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (v.supported) Klein(stringResource(Res.string.dna_evidence_by, v.matches.joinToString("; ") { s ->
                        g.name(s.recordedAt) + " – " + (s.other?.let { g.name(it) } ?: "…") + " " + cmText(s.cm) + " cM"
                    }), Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun Titel(text: String) {
    Text(text, Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Absatz(text: String) {
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Klein(text: String, modifier: Modifier = Modifier) {
    if (text.isNotEmpty()) Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Warnzeile(text: String, modifier: Modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
    Text("⚠ $text", modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
}
