package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.ui.meldung
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import de.bgghome.webtrees.nativ.api.ChangeEntry
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.UiState
import de.bgghome.webtrees.nativ.ui.setRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/*
 * Letzte Aenderungen (Ansicht > Letzte Aenderungen, Strg+8, ab API-Stufe 30): der Aenderungsverlauf des Baums aus
 * webtrees - wer hat wann welchen Datensatz angelegt, geaendert oder geloescht, auch noch ausstehende Aenderungen.
 * Klick macht die Person zum Probanden, Doppelklick oeffnet das Personenblatt.
 */
@Composable
fun AenderungenFenster(state: UiState, viewModel: AppViewModel, openSheet: (String) -> Unit, onClose: () -> Unit) {
    val tree = state.tree?.name ?: return
    var neu by remember { mutableIntStateOf(0) }
    val farben = MaterialTheme.colorScheme
    val liste by produceState<Result<List<ChangeEntry>>?>(null, tree, neu) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { viewModel.client.changes(tree, 200).changes } }
    }
    androidx.compose.ui.window.Window(
        onCloseRequest = onClose, title = stringResource(Res.string.desk_changes_window) + " – " + state.tree?.title.orEmpty(),
        state = arbeitsfenster("fenster_aenderungen", 820, 600),
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
                Box(Modifier.weight(1f).fillMaxWidth().border(1.dp, farben.outlineVariant)) {
                    val l = liste
                    when {
                        l == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        l.isFailure -> Text(meldung(l.exceptionOrNull()), Modifier.padding(12.dp), color = farben.error)
                        l.getOrThrow().isEmpty() -> Text(stringResource(Res.string.desk_changes_empty), Modifier.padding(16.dp), color = farben.onSurfaceVariant)
                        else -> {
                            val ls = rememberLazyListState()
                            LazyColumn(Modifier.fillMaxSize(), state = ls) {
                                itemsIndexed(l.getOrThrow()) { i, e ->
                                    val person = e.type == "INDI"
                                    Row(
                                        Modifier.fillMaxWidth().fokusRahmen()
                                            .combinedClickable(enabled = person, onClick = { viewModel.setRoot(e.xref) }, onDoubleClick = { openSheet(e.xref) })
                                            .background(if (i % 2 == 1) farben.surfaceVariant.copy(alpha = 0.35f) else farben.surface)
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Text(isoZeit(e.time), Modifier.width(130.dp), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
                                        Text(e.user, Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(stringResource(when (e.action) { "created" -> Res.string.desk_change_created; "deleted" -> Res.string.desk_change_deleted; else -> Res.string.desk_change_updated }),
                                            Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall)
                                        Text(e.name + "  (" + e.xref + ")" + if (e.pending) "  · " + stringResource(Res.string.desk_change_pending) else "",
                                            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = if (e.xref == state.root) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    HorizontalDivider(color = farben.outlineVariant)
                                }
                            }
                            ListenLeiste(ls)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Knopf(stringResource(Res.string.desk_check_rerun), true) { neu++ }
                    Spacer(Modifier.weight(1f))
                    Knopf(stringResource(Res.string.action_close), true, onClose)
                }
            }
        }
    }
}
