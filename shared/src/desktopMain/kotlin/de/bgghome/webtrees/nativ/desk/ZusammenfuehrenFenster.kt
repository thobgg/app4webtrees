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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import de.bgghome.webtrees.nativ.Sprache
import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.MergeEntry
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.TreeExport
import de.bgghome.webtrees.nativ.data.DUBLETTEN_REGELN
import de.bgghome.webtrees.nativ.data.Dublette
import de.bgghome.webtrees.nativ.data.dubletten
import de.bgghome.webtrees.nativ.pruefung.PruefOptionen
import de.bgghome.webtrees.nativ.pruefung.PruefTexte
import de.bgghome.webtrees.nativ.pruefung.Regelkatalog
import de.bgghome.webtrees.nativ.pruefung.pruefen
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.UiState
import de.bgghome.webtrees.nativ.ui.WtAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/*
 * Personen zusammenfuehren (Person > Personen zusammenfuehren ..., ab API-Stufe 29, nur Verwalter): Reiter "Moegliche
 * Doppelte" mit den Paaren aus den Pruefregeln 219 (gleicher Name, gleiches Ereignisdatum) und 228 (aehnlich), dazu
 * "Paar hinzufuegen" fuer alles, was die Regeln nicht finden; Reiter "Protokoll" mit allen Zusammenfuehrungen des
 * Baums und Rueckgaengig. Das eigentliche Zusammenfuehren laeuft im ZusammenfuehrenDialog.
 */
@Composable
fun ZusammenfuehrenFenster(state: UiState, viewModel: AppViewModel, start: String?, openSheet: (String) -> Unit, onClose: () -> Unit) {
    val tree = state.tree?.name ?: return
    var neu by remember { mutableStateOf(0) }
    var reiter by remember { mutableStateOf(0) }
    var paar by remember { mutableStateOf<Dublette?>(null) }
    var hinzufuegen by remember(start) { mutableStateOf(!start.isNullOrEmpty()) }
    var meldung by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val farben = MaterialTheme.colorScheme

    val baum by produceState<Result<TreeExport>?>(null, tree, neu) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching { BaumSpeicher.holen(viewModel.client, tree, Int.MAX_VALUE / 64) ?: throw IllegalStateException(Texte.t(Res.string.desk_list_needs_export)) }
        }
    }
    val paare by produceState<List<Dublette>?>(null, baum) {
        val b = baum?.getOrNull() ?: run { value = null; return@produceState }
        value = withContext(Dispatchers.Default) {
            val aus = Regelkatalog.alle.map { it.id }.filter { it !in DUBLETTEN_REGELN }.toSet()
            dubletten(pruefen(b, PruefOptionen(aus = aus, texte = PruefTexte.ausRessourcen(Sprache.aktiv))))
        }
    }
    val protokoll by produceState<Result<List<MergeEntry>>?>(null, tree, neu) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { viewModel.client.merges(tree).merges } }
    }

    androidx.compose.ui.window.Window(
        onCloseRequest = onClose, title = stringResource(Res.string.desk_merge_window) + " – " + state.tree?.title.orEmpty(),
        state = arbeitsfenster("fenster_zusammenfuehren", 980, 680),
        onPreviewKeyEvent = { e ->
            when {
                e.type == KeyEventType.KeyDown && e.key == Key.Escape -> { onClose(); true }
                e.type == KeyEventType.KeyDown && e.key == Key.F1 -> { Hilfe.oeffnen("person"); true }
                else -> false
            }
        },
    ) {
        Surface(Modifier.fillMaxSize(), color = farben.surface) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TabRow(selectedTabIndex = reiter) {
                    Tab(selected = reiter == 0, onClick = { reiter = 0 }, text = { Text(stringResource(Res.string.desk_merge_pairs) + (paare?.let { " (${it.size})" } ?: "")) })
                    Tab(selected = reiter == 1, onClick = { reiter = 1 }, text = { Text(stringResource(Res.string.desk_merge_log) + (protokoll?.getOrNull()?.let { " (${it.size})" } ?: "")) })
                }
                meldung?.let { Text(it, color = farben.primary, style = MaterialTheme.typography.bodyMedium) }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (reiter == 0) Paare(baum, paare, openSheet, onPaar = { paar = it })
                    else Protokoll(protokoll, onRueckgaengig = { e ->
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { runCatching { viewModel.client.mergeUndo(tree, e.id, false) } }
                            meldung = r.fold(
                                onSuccess = { u -> if (u.ok) Texte.t(Res.string.desk_merge_undo_done, e.removedName) else Texte.t(Res.string.desk_merge_undo_blocked, u.changed.joinToString { it.name.ifBlank { it.xref } }) },
                                onFailure = { meldung(it) },
                            )
                            if (r.getOrNull()?.ok == true) { neu++; viewModel.refresh() }
                        }
                    })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Knopf(stringResource(Res.string.desk_merge_add), baum?.getOrNull() != null) { hinzufuegen = true }
                    Knopf(stringResource(Res.string.desk_check_rerun), true) { neu++ }
                    Spacer(Modifier.weight(1f))
                    Knopf(stringResource(Res.string.action_close), true, onClose)
                }
            }
        }
        paar?.let { p ->
            ZusammenfuehrenDialog(p, viewModel, tree, onClose = { geaendert -> paar = null; if (geaendert) { neu++; viewModel.refresh(); reiter = 0 } })
        }
        if (hinzufuegen) PaarWaehlen(
            erste = start?.takeIf { it.isNotEmpty() }?.let { x -> baum?.getOrNull()?.person(x) ?: Person(x, x) },
            suche = { q -> runCatching { viewModel.client.individuals(tree, q, 1).data }.getOrNull().orEmpty() },
            onDismiss = { hinzufuegen = false },
            onWahl = { a, b -> hinzufuegen = false; paar = Dublette(a.xref, b.xref, "", "${a.name} ← ${b.name}") },
        )
    }
}

@Composable
private fun Paare(baum: Result<TreeExport>?, paare: List<Dublette>?, openSheet: (String) -> Unit, onPaar: (Dublette) -> Unit) {
    val farben = MaterialTheme.colorScheme
    val b = baum?.getOrNull()
    when {
        baum == null || (b != null && paare == null) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        b == null -> Text(baum.exceptionOrNull()?.message ?: "?", color = farben.error)
        else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.desk_merge_hint), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
            if (paare.isNullOrEmpty()) Text(stringResource(Res.string.desk_merge_none), Modifier.padding(vertical = 20.dp), color = farben.onSurfaceVariant)
            else Box(Modifier.fillMaxWidth().weight(1f).border(1.dp, farben.outlineVariant)) {
                LazyColumn {
                    items(paare, key = { it.schluessel }) { p ->
                        PaarZeile(p, b, openSheet, onPaar)
                        HorizontalDivider(color = farben.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaarZeile(p: Dublette, b: TreeExport, openSheet: (String) -> Unit, onPaar: (Dublette) -> Unit) {
    val farben = MaterialTheme.colorScheme
    var richtung by remember(p.schluessel) { mutableStateOf(p) }
    val a = b.person(richtung.bleibt); val z = b.person(richtung.geht)
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(p.regel, Modifier.width(40.dp), style = MaterialTheme.typography.labelMedium, color = farben.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            PersonText(a, richtung.bleibt, true, openSheet)
            PersonText(z, richtung.geht, false, openSheet)
            Text(p.text, style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Tipp(stringResource(Res.string.desk_merge_swap)) { TextButton(onClick = { richtung = richtung.umgedreht() }) { Text("⇅") } }
        OutlinedButton(onClick = { onPaar(richtung) }, shape = MaterialTheme.shapes.small) { Text(stringResource(Res.string.desk_merge_do)) }
    }
}

@Composable
private fun PersonText(p: Person?, xref: String, bleibt: Boolean, openSheet: (String) -> Unit) {
    val farben = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (bleibt) stringResource(Res.string.desk_merge_keeps) else stringResource(Res.string.desk_merge_goes), Modifier.width(110.dp),
            style = MaterialTheme.typography.labelSmall, color = farben.onSurfaceVariant)
        Text((p?.name ?: xref) + "  (" + xref + ")" + (p?.lifespan?.takeIf { it.isNotBlank() }?.let { "  $it" } ?: ""),
            Modifier.clickable { openSheet(xref) }, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bleibt) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Protokoll(protokoll: Result<List<MergeEntry>>?, onRueckgaengig: (MergeEntry) -> Unit) {
    val farben = MaterialTheme.colorScheme
    val liste = protokoll?.getOrNull()
    when {
        protokoll == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        liste == null -> Text(protokoll.exceptionOrNull()?.message ?: "?", color = farben.error)
        liste.isEmpty() -> Text(stringResource(Res.string.desk_merge_log_none), Modifier.padding(vertical = 20.dp), color = farben.onSurfaceVariant)
        else -> Box(Modifier.fillMaxSize().border(1.dp, farben.outlineVariant)) {
            LazyColumn {
                items(liste, key = { it.id }) { e ->
                    Row(Modifier.fillMaxWidth().background(if (e.undone != null) farben.surfaceVariant.copy(alpha = 0.5f) else farben.surface).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(Res.string.desk_merge_log_entry, "${e.removedName} (${e.removed})", "${e.name} (${e.xref})"),
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(isoZeit(e.time), e.user.takeIf { it.isNotBlank() }, Texte.t(Res.string.desk_merge_log_records, e.records),
                                e.undone?.let { stringResource(Res.string.desk_merge_undone) + " " + isoZeit(it) }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { onRueckgaengig(e) }, enabled = e.undone == null, shape = MaterialTheme.shapes.small) { Text(stringResource(Res.string.desk_merge_undo)) }
                    }
                    HorizontalDivider(color = farben.outlineVariant)
                }
            }
        }
    }
}

/** "2026-10-05T14:49:45+02:00" -> "05.10.2026 14:49" in der Schreibweise der Oberflaeche. */
internal fun isoZeit(iso: String): String = runCatching {
    java.time.OffsetDateTime.parse(iso).toLocalDateTime()
        .format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT).withLocale(java.util.Locale.getDefault()))
}.getOrDefault(iso)

/** Zwei Personen frei waehlen - fuer Doppelte, die die Regeln nicht finden (gleichnamige Kinder ohne Daten). */
@Composable
private fun PaarWaehlen(erste: Person?, suche: suspend (String) -> List<Person>, onDismiss: () -> Unit, onWahl: (Person, Person) -> Unit) {
    var a by remember { mutableStateOf(erste) }
    var b by remember { mutableStateOf<Person?>(null) }
    val farben = MaterialTheme.colorScheme
    WtAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.desk_merge_add)) },
        text = {
            Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.desk_merge_first), style = MaterialTheme.typography.labelLarge)
                PersonSuche(a, { a = it }, suche)
                Text(stringResource(Res.string.desk_merge_second), style = MaterialTheme.typography.labelLarge)
                PersonSuche(b, { b = it }) { q -> suche(q).filter { it.xref != a?.xref } }
                Text(stringResource(Res.string.desk_merge_add_hint), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = a != null && b != null && a?.xref != b?.xref, onClick = { onWahl(a!!, b!!) }) { Text(stringResource(Res.string.desk_merge_do)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
