package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.api.TaskJson
import de.bgghome.webtrees.nativ.api.TaskRequest
import de.bgghome.webtrees.nativ.res.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/*
 * Forschungsaufgaben am Handy und Tablet (webtrees _TODO, ab API-Stufe 30): die Liste des Baums als Seite im Nachschlage-
 * Stapel, Anlegen aus dem Profil. Der Dialog kennt nur Aufgabe, Datum und Notiz - die GEDCOM-L-Felder (Kategorie,
 * Prioritaet, Status, Archiv) pflegt man am PC (desk/AufgabenFenster.kt).
 */

/** Fuer welche Person eine Aufgabe angelegt oder geaendert wird. */
data class AufgabeFuer(val xref: String, val name: String, val vorhanden: TaskJson? = null)

@Composable
internal fun AufgabenSeite(state: UiState, viewModel: AppViewModel, onZurueck: () -> Unit, onPerson: (String) -> Unit) {
    val tree = state.tree?.name ?: return
    var nurFaellige by remember { mutableStateOf(true) }
    var stand by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<AufgabeFuer?>(null) }
    var erledigen by remember { mutableStateOf<TaskJson?>(null) }
    val scope = rememberCoroutineScope()
    val liste by produceState<Result<List<TaskJson>>?>(null, tree, nurFaellige, stand) {
        value = withContext(Dispatchers.Default) { runCatching { viewModel.client.tasks(tree, nurFaellige).tasks } }
    }

    Column(Modifier.fillMaxSize()) {
        Kopf(stringResource(Res.string.desk_tasks_window), onZurueck)
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(nurFaellige, { nurFaellige = it })
            Text(stringResource(Res.string.desk_tasks_open_only), style = MaterialTheme.typography.bodyMedium)
            Box(Modifier.weight(1f))
            liste?.getOrNull()?.let { Text(stringResource(Res.string.desk_tasks_count, it.size), Modifier.padding(end = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        val l = liste
        when {
            l == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            l.isFailure -> Text((l.exceptionOrNull() as? Exception)?.let(::explain) ?: "?", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            l.getOrThrow().isEmpty() -> Text(stringResource(Res.string.tasks_empty_phone), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                items(l.getOrThrow(), key = { it.record + "|" + it.factId }) { t ->
                    AufgabeZeile(t, onPerson = { if (t.recordType == "INDI") onPerson(t.record) },
                        onBearbeiten = { dialog = AufgabeFuer(t.record, t.name, t) }, onErledigt = { erledigen = t })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    dialog?.let { AufgabeDialogHandy(it, viewModel, tree, onClose = { gespeichert -> dialog = null; if (gespeichert) stand++ }) }
    erledigen?.let { t ->
        ConfirmDialog(
            title = stringResource(Res.string.task_done_title), text = t.text, confirm = stringResource(Res.string.desk_task_done),
            onDismiss = { erledigen = null },
            onConfirm = {
                erledigen = null
                scope.launch {
                    // Erledigt = die _TODO-Zeile loeschen, wie in webtrees und am PC
                    withContext(Dispatchers.Default) { runCatching { viewModel.client.deleteFact(tree, t.record, t.factId) } }
                        .onSuccess { stand++ }.onFailure { e -> (e as? Exception)?.let(viewModel::fail) }
                }
            },
        )
    }
}

@Composable
private fun AufgabeZeile(t: TaskJson, onPerson: () -> Unit, onBearbeiten: () -> Unit, onErledigt: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(t.name.ifBlank { t.record }, Modifier.clickable(onClick = onPerson), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(t.text, style = MaterialTheme.typography.bodyLarge)
        val unten = listOfNotNull(t.date?.text?.takeIf(String::isNotBlank), t.user.takeIf(String::isNotBlank), t.note.takeIf(String::isNotBlank)).joinToString(" · ")
        if (unten.isNotEmpty()) Text(unten, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = onBearbeiten) { Text(stringResource(Res.string.action_edit)) }
            TextButton(onClick = onErledigt) { Text(stringResource(Res.string.desk_task_done)) }
        }
    }
}

/** Aufgabe anlegen oder aendern: Text, Datum (leer = heute, spaeter = Wiedervorlage), Notiz. Nur Geaendertes geht an den Server. */
@Composable
internal fun AufgabeDialogHandy(ziel: AufgabeFuer, viewModel: AppViewModel, tree: String, onClose: (gespeichert: Boolean) -> Unit) {
    val v = ziel.vorhanden
    var text by remember { mutableStateOf(v?.text.orEmpty()) }
    val datum = rememberDateInput(gedcom = v?.date?.gedcom.orEmpty(), displayText = v?.date?.text.orEmpty())
    var notiz by remember { mutableStateOf(v?.note.orEmpty()) }
    var laeuft by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    WtAlertDialog(
        onDismissRequest = { onClose(false) },
        title = { Text(stringResource(Res.string.desk_task_for, ziel.name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(text, { text = it }, Res.string.desk_task_text, minLines = 2)
                DateInput(datum, Res.string.field_date)
                Field(notiz, { notiz = it }, Res.string.desk_task_note, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank() && !laeuft && datum.result != null, onClick = {
                laeuft = true
                scope.launch {
                    withContext(Dispatchers.Default) {
                        runCatching { viewModel.client.saveTask(tree, ziel.xref, TaskRequest(v?.factId, text.trim(), datum.result?.ifBlank { null }, v?.user?.ifBlank { null }, notiz.trim())) }
                    }.onSuccess { onClose(true) }.onFailure { e -> laeuft = false; (e as? Exception)?.let(viewModel::fail) }
                }
            }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = { onClose(false) }) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
