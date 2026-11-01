package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.api.hat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import de.bgghome.webtrees.nativ.api.TaskJson
import de.bgghome.webtrees.nativ.api.TaskRequest
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.UiState
import de.bgghome.webtrees.nativ.ui.WtAlertDialog
import de.bgghome.webtrees.nativ.ui.setRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/*
 * Forschungsaufgaben (Ansicht > Aufgaben, Strg+7, ab API-Stufe 30): alle Aufgaben des Baums als Liste - Datum,
 * Person oder Familie, Text, Bearbeiter. Es sind die Forschungsaufgaben von webtrees (Feld _TODO), also dieselben,
 * die im Browser der Block "Forschungsaufgaben" zeigt. Neue Aufgabe fuer den Probanden, Bearbeiten, Erledigt (=
 * loeschen). Haken "nur faellige" laesst Wiedervorlagen mit Datum in der Zukunft weg.
 */

/** Was der Dialog braucht: Datensatz, Anzeigename, vorhandene Aufgabe (null = neu), Vorgabetext. */
class AufgabeZiel(val xref: String, val name: String, val vorhanden: TaskJson? = null, val vorgabe: String = "")

@Composable
fun AufgabenFenster(state: UiState, viewModel: AppViewModel, openSheet: (String) -> Unit, onClose: () -> Unit) {
    val tree = state.tree?.name ?: return
    var neu by remember { mutableIntStateOf(0) }
    var nurOffene by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf<AufgabeZiel?>(null) }
    var meldung by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val farben = MaterialTheme.colorScheme

    val liste by produceState<Result<List<TaskJson>>?>(null, tree, neu, nurOffene) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { viewModel.client.tasks(tree, nurOffene).tasks } }
    }

    androidx.compose.ui.window.Window(
        onCloseRequest = onClose, title = stringResource(Res.string.desk_tasks_window) + " – " + state.tree?.title.orEmpty(),
        state = arbeitsfenster("fenster_aufgaben", 900, 600),
        onPreviewKeyEvent = { e ->
            when {
                e.type == KeyEventType.KeyDown && e.key == Key.Escape -> { onClose(); true }
                e.type == KeyEventType.KeyDown && e.key == Key.F1 -> { Hilfe.oeffnen("hauptfenster"); true }
                else -> false
            }
        },
    ) {
        Surface(Modifier.fillMaxSize(), color = farben.surface) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = nurOffene, onCheckedChange = { nurOffene = it })
                    Text(stringResource(Res.string.desk_tasks_open_only), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    liste?.getOrNull()?.let { Text(stringResource(Res.string.desk_tasks_count, it.size), style = MaterialTheme.typography.labelMedium, color = farben.onSurfaceVariant) }
                }
                meldung?.let { Text(it, color = farben.error, style = MaterialTheme.typography.bodySmall) }
                Box(Modifier.weight(1f).fillMaxWidth().border(1.dp, farben.outlineVariant)) {
                    val l = liste
                    when {
                        l == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        l.isFailure -> Text(l.exceptionOrNull()?.message ?: "?", Modifier.padding(12.dp), color = farben.error)
                        l.getOrThrow().isEmpty() -> Text(stringResource(Res.string.desk_tasks_empty), Modifier.padding(16.dp), color = farben.onSurfaceVariant)
                        else -> {
                            val ls = rememberLazyListState()
                            LazyColumn(Modifier.fillMaxSize(), state = ls) {
                                items(l.getOrThrow(), key = { it.record + "|" + it.factId }) { t ->
                                    AufgabeZeile(t, state.root,
                                        onKlick = { viewModel.setRoot(t.record) }, onDoppel = { openSheet(t.record) },
                                        onBearbeiten = { dialog = AufgabeZiel(t.record, t.name, t) },
                                        onErledigt = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) { runCatching { viewModel.client.deleteFact(tree, t.record, t.factId) } }
                                                    .onSuccess { neu++ }.onFailure { meldung = it.message }
                                            }
                                        })
                                    HorizontalDivider(color = farben.outlineVariant)
                                }
                            }
                            ListenLeiste(ls)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val root = state.root
                    val name = state.detail?.person?.takeIf { it.xref == root }?.name ?: root.orEmpty()
                    Knopf(stringResource(Res.string.desk_task_new), root != null && state.tree?.canEdit == true) { dialog = AufgabeZiel(root!!, name) }
                    Knopf(stringResource(Res.string.desk_check_rerun), true) { neu++ }
                    Spacer(Modifier.weight(1f))
                    Knopf(stringResource(Res.string.action_close), true, onClose)
                }
            }
        }
        dialog?.let { z -> AufgabeDialog(z, viewModel, tree, onClose = { gespeichert -> dialog = null; if (gespeichert) { neu++; viewModel.setRoot(z.xref, remember = false) } }) }
    }
}

@Composable
private fun AufgabeZeile(t: TaskJson, root: String?, onKlick: () -> Unit, onDoppel: () -> Unit, onBearbeiten: () -> Unit, onErledigt: () -> Unit) {
    val farben = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().fokusRahmen().combinedClickable(onClick = onKlick, onDoubleClick = onDoppel)
            .background(if (t.record == root) farben.secondaryContainer.copy(alpha = 0.4f) else farben.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(t.date?.text ?: "–", Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(t.text + if (t.pending) "  (" + stringResource(Res.string.desk_change_pending) + ")" else "", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOf(t.name + " (" + t.record + ")", t.user).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (t.note.isNotBlank()) Text(t.note, style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // GEDCOM-L (ab API-Stufe 34): Beschreibung, Einordnung, Termine, Archiv und Loesung
            AufgabeGedcomL(t)
        }
        TextButton(onClick = onBearbeiten) { Text(stringResource(Res.string.desk_task_edit)) }
        TextButton(onClick = onErledigt) { Text(stringResource(Res.string.desk_task_done)) }
    }
}

/** Aufgabe anlegen oder aendern: Text, Datum (leer = heute), Bearbeiter, Notiz. */
@Composable
fun AufgabeDialog(ziel: AufgabeZiel, viewModel: AppViewModel, tree: String, onClose: (gespeichert: Boolean) -> Unit) {
    var text by remember { mutableStateOf(ziel.vorhanden?.text ?: ziel.vorgabe) }
    var datum by remember { mutableStateOf(ziel.vorhanden?.date?.gedcom?.ifBlank { null } ?: ziel.vorhanden?.date?.text.orEmpty()) }
    var bearbeiter by remember { mutableStateOf(ziel.vorhanden?.user.orEmpty()) }
    var notiz by remember { mutableStateOf(ziel.vorhanden?.note.orEmpty()) }
    // GEDCOM-L (ab API-Stufe 35): gesendet wird nur, was sich gegenueber der vorhandenen Aufgabe aendert
    val v = ziel.vorhanden
    val glFelder = viewModel.state.value.info.hat(de.bgghome.webtrees.nativ.api.API_GEDCOML_WRITE)
    var beschreibung by remember { mutableStateOf(v?.description.orEmpty()) }
    var kategorie by remember { mutableStateOf(v?.category.orEmpty()) }
    var prioritaet by remember { mutableStateOf(v?.priority.orEmpty()) }
    var art by remember { mutableStateOf(v?.taskType.orEmpty()) }
    var status by remember { mutableStateOf(v?.status.orEmpty()) }
    var erinnerung by remember { mutableStateOf(v?.reminder?.gedcom?.ifBlank { null } ?: v?.reminder?.text.orEmpty()) }
    var erledigt by remember { mutableStateOf(v?.completed?.gedcom?.ifBlank { null } ?: v?.completed?.text.orEmpty()) }
    var loesung by remember { mutableStateOf(v?.solution.orEmpty()) }
    var archiv by remember { mutableStateOf(v?.repository?.xref.orEmpty()) }
    val archive by produceState(emptyList<de.bgghome.webtrees.nativ.api.RepositorySummary>(), glFelder) {
        if (glFelder) value = withContext(Dispatchers.IO) { runCatching { viewModel.client.repositories(tree).repositories }.getOrDefault(emptyList()) }
    }
    var fehler by remember { mutableStateOf<String?>(null) }
    var laeuft by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    WtAlertDialog(
        onDismissRequest = { onClose(false) },
        title = { Text(stringResource(Res.string.desk_task_for, ziel.name)) },
        text = {
            Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.desk_task_text)) }, singleLine = false, minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(datum, { datum = it }, Modifier.weight(1f), label = { Text(stringResource(Res.string.desk_task_date)) }, singleLine = true)
                    OutlinedTextField(bearbeiter, { bearbeiter = it }, Modifier.weight(1f), label = { Text(stringResource(Res.string.desk_task_user)) }, singleLine = true)
                }
                OutlinedTextField(notiz, { notiz = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.desk_task_note)) }, minLines = 3)
                if (glFelder) {
                    OutlinedTextField(beschreibung, { beschreibung = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.desk_task_description)) }, minLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(kategorie, { kategorie = it }, Modifier.weight(1f), label = { Text(stringResource(Res.string.desk_task_category)) }, singleLine = true)
                        Wahl(stringResource(Res.string.desk_task_priority), prioritaet, listOf("") + (0..8).map(Int::toString), { it.ifBlank { "–" } }) { prioritaet = it }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val artText = listOf("0", "1", "2").associateWith { aufgabenArt(it).orEmpty() }
                        val statusText = listOf("open", "completed", "auto").associateWith { aufgabenStatus(it).orEmpty() }
                        Wahl(stringResource(Res.string.desk_task_type), art, listOf("", "0", "1", "2"), { artText[it] ?: it.ifBlank { "–" } }) { art = it }
                        Wahl(stringResource(Res.string.desk_task_status), status, listOf("", "open", "completed", "auto"), { statusText[it] ?: it.ifBlank { "–" } }) { status = it }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(erinnerung, { erinnerung = it }, Modifier.weight(1f), label = { Text(stringResource(Res.string.desk_task_reminder)) }, singleLine = true)
                        OutlinedTextField(erledigt, { erledigt = it }, Modifier.weight(1f), label = { Text(stringResource(Res.string.desk_task_completed)) }, singleLine = true)
                    }
                    OutlinedTextField(loesung, { loesung = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.desk_task_solution)) }, minLines = 2)
                    Wahl(stringResource(Res.string.desk_task_repository), archiv, listOf("") + archive.map { it.xref } + listOfNotNull(archiv.takeIf { a -> a.isNotBlank() && archive.none { it.xref == a } }),
                        { x -> if (x.isBlank()) "–" else archive.firstOrNull { it.xref == x }?.name?.ifBlank { null } ?: v?.repository?.name ?: x }) { archiv = it }
                }
                fehler?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank() && !laeuft, onClick = {
                laeuft = true; fehler = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        // Nur Geaendertes senden (null = nicht anfassen, "" = entfernen)
                        fun neu(jetzt: String, vorher: String?) = jetzt.trim().takeIf { glFelder && it != vorher.orEmpty().trim() }
                        val anfrage = TaskRequest(ziel.vorhanden?.factId, text.trim(), datum.trim().ifBlank { null }, bearbeiter.trim().ifBlank { null }, notiz.trim(),
                            description = neu(beschreibung, v?.description), category = neu(kategorie, v?.category), priority = neu(prioritaet, v?.priority),
                            taskType = neu(art, v?.taskType), status = neu(status, v?.status),
                            reminder = neu(erinnerung, v?.reminder?.gedcom?.ifBlank { null } ?: v?.reminder?.text),
                            completed = neu(erledigt, v?.completed?.gedcom?.ifBlank { null } ?: v?.completed?.text),
                            solution = neu(loesung, v?.solution), repository = neu(archiv, v?.repository?.xref))
                        runCatching { viewModel.client.saveTask(tree, ziel.xref, anfrage) }
                    }.onSuccess { onClose(true) }.onFailure { fehler = it.message }
                    laeuft = false
                }
            }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = { onClose(false) }) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Eine kleine Auswahl mit Beschriftung: Knopf mit dem aktuellen Wert, Klick klappt die Werte auf. */
@Composable
private fun Wahl(label: String, wert: String, werte: List<String>, text: @Composable (String) -> String, onWahl: (String) -> Unit) {
    var offen by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { offen = true }, shape = MaterialTheme.shapes.small) { Text(text(wert)) }
            DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
                werte.forEach { w -> DropdownMenuItem(text = { Text(text(w)) }, onClick = { onWahl(w); offen = false }) }
            }
        }
    }
}

/** Die GEDCOM-L-Felder einer Aufgabe in zwei, drei kleinen Zeilen; ohne diese Felder nichts. */
@Composable
internal fun AufgabeGedcomL(t: TaskJson) {
    val farben = MaterialTheme.colorScheme
    val klein = MaterialTheme.typography.bodySmall
    t.description?.takeIf { it.isNotBlank() && it != t.text }?.let { Text(it, style = klein, maxLines = 3, overflow = TextOverflow.Ellipsis) }
    val teile = listOfNotNull(
        t.category?.takeIf(String::isNotBlank)?.let { stringResource(Res.string.desk_task_category) + ": " + it },
        t.priority?.takeIf(String::isNotBlank)?.let { stringResource(Res.string.desk_task_priority) + ": " + it },
        aufgabenArt(t.taskType)?.let { stringResource(Res.string.desk_task_type) + ": " + it },
        aufgabenStatus(t.status)?.let { stringResource(Res.string.desk_task_status) + ": " + it },
        t.reminder?.text?.takeIf(String::isNotBlank)?.let { stringResource(Res.string.desk_task_reminder) + ": " + it },
        t.completed?.text?.takeIf(String::isNotBlank)?.let { stringResource(Res.string.desk_task_completed) + " " + it },
        t.repository?.let { r -> stringResource(Res.string.desk_task_repository) + ": " + (r.name ?: r.xref) },
    )
    if (teile.isNotEmpty()) Text(teile.joinToString(" · "), style = klein, color = farben.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    t.solution?.takeIf(String::isNotBlank)?.let { Text(stringResource(Res.string.desk_task_solution) + ": " + it, style = klein, color = farben.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }
}
