package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.ui.meldung

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.api.MergeFact
import de.bgghome.webtrees.nativ.api.MergePreview
import de.bgghome.webtrees.nativ.api.MergeResult
import de.bgghome.webtrees.nativ.api.MergeSuggestion
import de.bgghome.webtrees.nativ.api.MergeUndoResult
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.data.Dublette
import de.bgghome.webtrees.nativ.data.vorschlag
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.WtAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/*
 * Zwei Personen zusammenfuehren (ab API-Stufe 29, nur Verwalter): beide nebeneinander mit allen Fakten, je Fakt ein
 * Haken, ob er bleibt - vorbelegt mit dem Vorschlag der API (alles der ersten, von der zweiten nur, was die erste
 * nicht wortgleich hat); Verknuepfungen bleiben immer. Darunter, was danach auf die bleibende Person zeigt, und
 * weitere Paare (Eltern, Partner, Kinder gleichen Namens), die nach dem Zusammenfuehren der Reihe nach angeboten
 * werden. Nach dem Zusammenfuehren: Ergebnis mit Rueckgaengig.
 */

/** Die Daten, die der Dialog braucht - ohne ViewModel, damit er sich auch ohne Server zeichnen laesst. */
internal class ZusammenfuehrenZugriff(
    val vorschau: suspend (String, String) -> MergePreview,
    val zusammenfuehren: suspend (String, String, List<String>, List<String>) -> MergeResult,
    val rueckgaengig: suspend (String) -> MergeUndoResult,
)

internal fun zusammenfuehrenZugriff(viewModel: AppViewModel, tree: String) = ZusammenfuehrenZugriff(
    vorschau = { a, b -> viewModel.client.mergePreview(tree, a, b) },
    zusammenfuehren = { a, b, k1, k2 -> viewModel.client.merge(tree, a, b, k1, k2) },
    rueckgaengig = { id -> viewModel.client.mergeUndo(tree, id, false) },
)

@Composable
fun ZusammenfuehrenDialog(paar: Dublette, viewModel: AppViewModel, tree: String, onClose: (geaendert: Boolean) -> Unit) =
    ZusammenfuehrenDialogInhalt(paar, zusammenfuehrenZugriff(viewModel, tree), onClose)

@Composable
internal fun ZusammenfuehrenDialogInhalt(start: Dublette, zugriff: ZusammenfuehrenZugriff, onClose: (geaendert: Boolean) -> Unit) {
    var paar by remember { mutableStateOf(start) }
    var warteschlange by remember { mutableStateOf(emptyList<Dublette>()) }
    var geaendert by remember { mutableStateOf(false) }
    var ergebnis by remember { mutableStateOf<MergeResult?>(null) }
    var rueckgaengig by remember { mutableStateOf<MergeUndoResult?>(null) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var laeuft by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val farben = MaterialTheme.colorScheme

    val vorschau by produceState<Result<MergePreview>?>(null, paar) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { zugriff.vorschau(paar.bleibt, paar.geht) } }
    }
    val v = vorschau?.getOrNull()
    var keep1 by remember(v) { mutableStateOf(v?.let { vorschlag(it.facts1) } ?: emptySet()) }
    var keep2 by remember(v) { mutableStateOf(v?.let { vorschlag(it.facts2) } ?: emptySet()) }
    var weitere by remember(v) { mutableStateOf(v?.suggestions?.map { it.schluessel }?.toSet() ?: emptySet()) }

    val e = ergebnis
    if (e != null) {
        // Fertig: Ergebnis, Rueckgaengig, naechstes Paar
        WtAlertDialog(
            onDismissRequest = { onClose(geaendert) },
            title = { Text(stringResource(Res.string.desk_merge_window)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val r = rueckgaengig
                    when {
                        r == null -> {
                            Text(stringResource(Res.string.desk_merge_done, v?.person2?.name ?: e.removed, v?.person1?.name ?: e.xref, e.records))
                            if (e.pending) Text(stringResource(Res.string.desk_merge_done_pending), color = farben.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        r.ok -> Text(stringResource(Res.string.desk_merge_undo_done, v?.person2?.name ?: e.removed))
                        else -> Text(stringResource(Res.string.desk_merge_undo_blocked, r.changed.joinToString { it.name.ifBlank { it.xref } }), color = farben.error)
                    }
                    fehler?.let { Text(it, color = farben.error) }
                    if (warteschlange.isNotEmpty() && (r == null || !r.ok)) {
                        val n = warteschlange.first()
                        Text(stringResource(Res.string.desk_merge_next_hint, n.text), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                if (warteschlange.isNotEmpty() && (rueckgaengig == null || rueckgaengig?.ok == false)) {
                    TextButton(onClick = { paar = warteschlange.first(); warteschlange = warteschlange.drop(1); ergebnis = null; rueckgaengig = null; fehler = null }) {
                        Text(stringResource(Res.string.desk_merge_next))
                    }
                }
                TextButton(onClick = { onClose(geaendert) }) { Text(stringResource(Res.string.action_close)) }
            },
            dismissButton = {
                if (rueckgaengig == null) OutlinedButton(enabled = !laeuft, shape = MaterialTheme.shapes.small, onClick = {
                    laeuft = true
                    scope.launch {
                        rueckgaengig = withContext(Dispatchers.IO) { runCatching { zugriff.rueckgaengig(e.mergeId) } }
                            .onFailure { fehler = meldung(it) }.getOrNull()
                        laeuft = false
                    }
                }) { Text(stringResource(Res.string.desk_merge_undo)) }
            },
        )
        return
    }

    WtAlertDialog(
        onDismissRequest = { onClose(geaendert) },
        title = { Text(stringResource(Res.string.desk_merge_title)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    vorschau == null -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    v == null -> Text(vorschau?.exceptionOrNull()?.message ?: "?", color = farben.error)
                    else -> {
                        ZusammenfuehrenVorschau(v, keep1, keep2, weitere,
                            onKeep1 = { id, an -> keep1 = if (an) keep1 + id else keep1 - id },
                            onKeep2 = { id, an -> keep2 = if (an) keep2 + id else keep2 - id },
                            onWeitere = { k -> weitere = if (k in weitere) weitere - k else weitere + k },
                            onTauschen = { paar = paar.umgedreht() })
                        fehler?.let { Text(it, color = farben.error) }
                    }
                }
            }
        },
        breite = 900.dp,
        confirmButton = {
            TextButton(enabled = v != null && !laeuft, onClick = {
                val p = v ?: return@TextButton
                laeuft = true; fehler = null
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { zugriff.zusammenfuehren(paar.bleibt, paar.geht, keep1.toList(), keep2.toList()) } }
                        .onSuccess { r ->
                            geaendert = true
                            warteschlange = warteschlange + p.suggestions.filter { it.schluessel in weitere }.map { Dublette(it.xref1, it.xref2, "", "${it.name1} ← ${it.name2}") }
                            ergebnis = r
                        }
                        .onFailure { fehler = meldung(it) }
                    laeuft = false
                }
            }) { Text(stringResource(Res.string.desk_merge_confirm)) }
        },
        dismissButton = { TextButton(onClick = { onClose(geaendert) }) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Der Inhalt der Vorschau - ohne Dialograhmen, damit er sich auch ohne Server zeichnen laesst. */
@Composable
internal fun ZusammenfuehrenVorschau(
    v: MergePreview, keep1: Set<String>, keep2: Set<String>, weitere: Set<String>,
    onKeep1: (String, Boolean) -> Unit, onKeep2: (String, Boolean) -> Unit, onWeitere: (String) -> Unit, onTauschen: () -> Unit,
    alleAnfangs: Boolean = false,
) {
    val farben = MaterialTheme.colorScheme
    // Kompakt (Normalfall): nur, was von der zweiten Person dazukommt; "Alle Ereignisse anzeigen" klappt die Gegenueberstellung auf
    var alle by remember { mutableStateOf(alleAnfangs) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            KopfKarte(Modifier.weight(1f), stringResource(Res.string.desk_merge_keeps), v.person1)
            Tipp(stringResource(Res.string.desk_merge_swap)) { TextButton(onClick = onTauschen) { Text("⇄") } }
            KopfKarte(Modifier.weight(1f), stringResource(Res.string.desk_merge_goes), v.person2)
        }
        if (!alle) {
            val dazu = v.facts2.filter { !it.link && !it.same }
            val gleich = v.facts2.count { !it.link && it.same }
            if (dazu.isEmpty()) Text(stringResource(Res.string.desk_merge_adds_none, v.person2.name, v.person1.name), style = MaterialTheme.typography.bodyMedium)
            else {
                Text(stringResource(Res.string.desk_merge_adds, v.person2.name), style = MaterialTheme.typography.labelLarge)
                dazu.forEach { f ->
                    val an = f.id in keep2
                    Row(Modifier.fillMaxWidth().clickable { onKeep2(f.id, !an) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = an, onCheckedChange = { onKeep2(f.id, it) })
                        Text(f.label.ifBlank { f.tag }, Modifier.width(150.dp), style = MaterialTheme.typography.labelMedium)
                        Text(f.text.ifBlank { "–" }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (gleich > 0) Text(stringResource(Res.string.desk_merge_same_count, gleich), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Spalte(Modifier.weight(1f), stringResource(Res.string.desk_merge_keeps), v.person1, v.facts1, keep1, onKeep1)
                Spalte(Modifier.weight(1f), stringResource(Res.string.desk_merge_goes), v.person2, v.facts2, keep2, onKeep2)
            }
            Text(stringResource(Res.string.desk_merge_same_hint), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
        }
        TextButton(onClick = { alle = !alle }) { Text(stringResource(if (alle) Res.string.desk_merge_show_less else Res.string.desk_merge_show_all)) }
        HorizontalDivider(color = farben.outlineVariant)
        if (v.links.isEmpty()) Text(stringResource(Res.string.desk_merge_links_none, v.person2.name), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
        else Text(stringResource(Res.string.desk_merge_links, v.person1.name, v.links.joinToString { "${it.name} (${it.xref})" }),
            style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
        if (v.suggestions.isNotEmpty()) {
            Text(stringResource(Res.string.desk_merge_suggestions), style = MaterialTheme.typography.labelLarge)
            v.suggestions.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onWeitere(s.schluessel) }) {
                    Checkbox(checked = s.schluessel in weitere, onCheckedChange = { onWeitere(s.schluessel) })
                    Text("${rolle(s.role)}: ${s.name1} (${s.xref1})  ←  ${s.name2} (${s.xref2})", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Kopf der kompakten Ansicht: wer bleibt, wer aufgeht - Name, Kennung, Lebensdaten. */
@Composable
private fun KopfKarte(modifier: Modifier, kopf: String, person: Person) {
    val farben = MaterialTheme.colorScheme
    Column(modifier.border(1.dp, farben.outlineVariant).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(kopf, style = MaterialTheme.typography.labelMedium, color = farben.onSurfaceVariant)
        Text(person.name + "  (" + person.xref + ")", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(person.lifespan.ifBlank { " " }, style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
    }
}

private val MergeSuggestion.schluessel: String get() = "$xref1|$xref2"

@Composable
private fun rolle(role: String) = stringResource(when (role) {
    "father" -> Res.string.desk_merge_role_father
    "mother" -> Res.string.desk_merge_role_mother
    "spouse" -> Res.string.desk_merge_role_spouse
    else -> Res.string.desk_merge_role_child
})

@Composable
private fun Spalte(modifier: Modifier, kopf: String, person: Person, fakten: List<MergeFact>, keep: Set<String>, onKeep: (String, Boolean) -> Unit) {
    val farben = MaterialTheme.colorScheme
    Column(modifier.border(1.dp, farben.outlineVariant).padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(kopf, style = MaterialTheme.typography.labelMedium, color = farben.onSurfaceVariant)
        Text(person.name + "  (" + person.xref + ")", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        person.lifespan.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant) }
        Spacer(Modifier.padding(2.dp))
        fakten.forEach { f ->
            val an = f.link || f.id in keep
            Row(Modifier.fillMaxWidth().background(if (f.same) farben.surfaceVariant.copy(alpha = 0.45f) else farben.surface)
                .clickable(enabled = !f.link) { onKeep(f.id, !an) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = an, enabled = !f.link, onCheckedChange = { onKeep(f.id, it) })
                Column(Modifier.weight(1f)) {
                    Text(f.label.ifBlank { f.tag }, style = MaterialTheme.typography.labelMedium)
                    Text(f.text.ifBlank { "–" }, style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                when {
                    f.link -> Text(stringResource(Res.string.desk_merge_link_always), Modifier.width(90.dp), style = MaterialTheme.typography.labelSmall, color = farben.onSurfaceVariant)
                    f.same -> Text(stringResource(Res.string.desk_merge_same), Modifier.width(90.dp), style = MaterialTheme.typography.labelSmall, color = farben.onSurfaceVariant)
                    else -> Spacer(Modifier.width(90.dp))
                }
            }
        }
    }
}
