package de.bgghome.webtrees.nativ.ui

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
import de.bgghome.webtrees.nativ.api.DnaEintrag
import de.bgghome.webtrees.nativ.api.DnaLinienEintraege
import de.bgghome.webtrees.nativ.api.DnaPerson
import de.bgghome.webtrees.nativ.api.DnaTester
import de.bgghome.webtrees.nativ.api.DnaTesterLinie
import de.bgghome.webtrees.nativ.api.DnaTreffer
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

/** Die vier Bereiche der Ansicht. */
enum class DnaBereich { Linien, Eintraege, Treffer, Tester }

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
 */
@Composable
fun DnaAnsicht(client: WtClient, tree: String, xref: String, onPerson: (String) -> Unit, modifier: Modifier = Modifier, zuerst: DnaBereich = DnaBereich.Linien) {
    var bereich by remember { mutableStateOf(zuerst) }
    Column(modifier.fillMaxSize()) {
        // Eine Zeile zum Wischen: vier Knoepfe brechen am Handy sonst in zwei Zeilen um
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                DnaBereich.Linien to Res.string.dna_area_lines, DnaBereich.Eintraege to Res.string.dna_area_entries,
                DnaBereich.Treffer to Res.string.dna_area_matches, DnaBereich.Tester to Res.string.dna_area_testers,
            ).forEach { (b, text) ->
                FilterChip(selected = bereich == b, onClick = { bereich = b }, label = { Text(stringResource(text), maxLines = 1, softWrap = false) })
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        when (bereich) {
            DnaBereich.Linien -> DnaLinienInhalt(client, tree, xref, onPerson, Modifier.weight(1f))
            DnaBereich.Tester -> TesterBereich(client, tree, xref, onPerson, Modifier.weight(1f))
            else -> PersonBereich(client, tree, xref, bereich, onPerson, Modifier.weight(1f))
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
private fun PersonBereich(client: WtClient, tree: String, xref: String, bereich: DnaBereich, onPerson: (String) -> Unit, modifier: Modifier) {
    val ergebnis by produceState<Result<Geladen<DnaPerson>>?>(null, tree, xref) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { client.dnaPerson(tree, xref).let { Geladen(it, dnaPersonenLaden(client, tree, it.kennungen())) } } }
    }
    Bereichsliste(ergebnis, modifier) { g ->
        if (bereich == DnaBereich.Eintraege) eintraege(g, onPerson) else treffer(g, onPerson)
    }
}

private fun LazyListScope.eintraege(g: Geladen<DnaPerson>, onPerson: (String) -> Unit) {
    val d = g.daten
    if (d.entries.isEmpty()) item("keine") { Absatz(stringResource(Res.string.dna_entries_none)) }
    items(d.entries, key = { "e" + it.factId }) { EintragZeile(it) }
    listOf("y" to Res.string.dna_same_line_y, "mt" to Res.string.dna_same_line_mt).forEach { (linie, titel) ->
        val l: DnaLinienEintraege = d.lines[linie] ?: return@forEach
        if (l.entries.isEmpty() && l.contradiction == null) return@forEach
        item("t$linie") { Titel(stringResource(titel)) }
        l.contradiction?.let { w -> item("w$linie") { Warnzeile(stringResource(Res.string.dna_contradiction, w.names.joinToString(", "))) } }
        items(l.entries, key = { "l$linie" + it.xref + it.factId }) { e ->
            Column {
                PersonRow(g.person(e.xref), onClick = { onPerson(e.xref) })
                EintragZeile(e, eingerueckt = true)
            }
        }
    }
}

/** Ein Eintrag: Wert gross, darunter Art, Anbieter und Datum; ein aus SNPs berechneter Zweig und - fuer Bearbeiter - der Schutz. */
@Composable
private fun EintragZeile(e: DnaEintrag, eingerueckt: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(start = if (eingerueckt) 76.dp else 16.dp, end = 16.dp, top = if (eingerueckt) 0.dp else 8.dp, bottom = 8.dp)) {
        Text(e.value.ifEmpty { e.type }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Klein(listOf(e.type.takeIf { e.value.isNotEmpty() }, e.provider, e.date).filter { !it.isNullOrBlank() }.joinToString(" · "))
        e.calculated?.takeIf { it.clade.isNotEmpty() }?.let { Klein(stringResource(Res.string.dna_calculated, it.clade, it.tree)) }
        schutzText(e.restriction)?.let { Klein(it) }
    }
}

@Composable
private fun schutzText(schutz: String?): String? = when (schutz) {
    "confidential" -> stringResource(Res.string.dna_restriction_confidential)
    "privacy" -> stringResource(Res.string.dna_restriction_privacy)
    "none" -> stringResource(Res.string.dna_restriction_none)
    else -> null
}

private fun LazyListScope.treffer(g: Geladen<DnaPerson>, onPerson: (String) -> Unit) {
    val d = g.daten
    if (d.matches.isEmpty()) item("keine") { Absatz(stringResource(Res.string.dna_matches_none)) }
    items(d.matches, key = { "m" + it.factId }) { m ->
        TrefferZeile(m, g, onPerson)
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
    // Die ueblichen Werte stehen unter CC BY - die Quelle gehoert dazu, sobald einer gezeigt wird
    if (d.matches.any { it.usual != null || it.candidates.isNotEmpty() }) item("quelle") { Klein(stringResource(Res.string.dna_usual_source), Modifier.padding(16.dp)) }
}

@Composable
private fun TrefferZeile(m: DnaTreffer, g: Geladen<DnaPerson>, onPerson: (String) -> Unit) {
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
                m.provider.ifBlank { null }, m.date.ifBlank { null },
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
    else -> null
}

@Composable
private fun TesterBereich(client: WtClient, tree: String, xref: String, onPerson: (String) -> Unit, modifier: Modifier) {
    val ergebnis by produceState<Result<Geladen<DnaTester>>?>(null, tree, xref) {
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
