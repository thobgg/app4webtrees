package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.ui.meldung

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.lokal.LokalBetrieb
import de.bgghome.webtrees.nativ.lokal.LokalerBaum
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.UiState
import de.bgghome.webtrees.nativ.ui.WtAlertDialog
import de.bgghome.webtrees.nativ.ui.chooseTree
import de.bgghome.webtrees.nativ.ui.reloadTrees
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/**
 * Datei › Stammbäume auf diesem PC: alle Baeume des lokalen webtrees mit Personenzahl - oeffnen, umbenennen, loeschen,
 * einen weiteren anlegen oder eine GEDCOM-Datei uebernehmen. Ohne Umweg ueber die webtrees-Verwaltung im Browser.
 */
@Composable
fun LokaleBaeumeDialog(state: UiState, viewModel: AppViewModel, onClose: () -> Unit) {
    var baeume by remember { mutableStateOf<List<LokalerBaum>?>(null) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var loeschen by remember { mutableStateOf<LokalerBaum?>(null) }
    var stand by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(stand) {
        withContext(Dispatchers.IO) { runCatching { LokalBetrieb.baeume() } }
            .onSuccess { baeume = it; fehler = null }.onFailure { fehler = meldung(it) }
    }
    fun ausfuehren(aktion: () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { runCatching(aktion) }
                .onFailure { fehler = meldung(it) }
            stand++
            viewModel.reloadTrees()
        }
    }

    WtAlertDialog(
        onDismissRequest = onClose,
        breite = 640.dp,
        title = { Text(stringResource(Res.string.lokal_trees_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val liste = baeume
                if (liste == null) CircularProgressIndicator(Modifier.padding(8.dp))
                else liste.forEach { b ->
                    BaumZeile(
                        b, offen = b.name == state.tree?.name, letzter = liste.size == 1,
                        onOeffnen = {
                            state.info?.trees?.firstOrNull { it.name == b.name }?.let(viewModel::chooseTree)
                            onClose()
                        },
                        onUmbenennen = { titel -> ausfuehren { LokalBetrieb.umbenennen(b.name, titel) } },
                        onLoeschen = { loeschen = b },
                    )
                }
                fehler?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(Res.string.lokal_trees_new), style = MaterialTheme.typography.titleSmall)
                LokalAnlegenFeld(viewModel, neu = true, onFertig = onClose)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(Res.string.action_close)) } },
    )

    loeschen?.let { b ->
        WtAlertDialog(
            onDismissRequest = { loeschen = null },
            title = { Text(stringResource(Res.string.lokal_trees_delete)) },
            text = { Text(stringResource(Res.string.lokal_trees_delete_confirm, b.titel, b.personen)) },
            confirmButton = {
                TextButton(onClick = {
                    loeschen = null
                    ausfuehren {
                        val gemerkt = LokalBetrieb.loeschen(b.name)
                        if (viewModel.settings.tree == b.name) viewModel.settings.tree = gemerkt
                    }
                }) { Text(stringResource(Res.string.lokal_trees_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { loeschen = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

@Composable
private fun BaumZeile(
    b: LokalerBaum, offen: Boolean, letzter: Boolean,
    onOeffnen: () -> Unit, onUmbenennen: (String) -> Unit, onLoeschen: () -> Unit,
) {
    var titel by remember(b.name, b.titel) { mutableStateOf(b.titel) }
    val geaendert = titel.isNotBlank() && titel.trim() != b.titel
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = titel, onValueChange = { titel = it }, singleLine = true,
            label = { Text(stringResource(Res.string.lokal_title_label)) },
            supportingText = {
                Text(
                    stringResource(Res.string.tree_people_count, b.personen) +
                        (if (offen) " · " + stringResource(Res.string.lokal_trees_current) else ""),
                    fontWeight = if (offen) FontWeight.SemiBold else FontWeight.Normal,
                )
            },
            trailingIcon = if (geaendert) ({
                IconButton(onClick = { onUmbenennen(titel.trim()) }) { Icon(Icons.Default.Check, stringResource(Res.string.lokal_trees_rename)) }
            }) else null,
            modifier = Modifier.weight(1f).onPreviewKeyEvent { e ->
                if (e.key == Key.Enter && e.type == KeyEventType.KeyDown && geaendert) { onUmbenennen(titel.trim()); true } else false
            },
        )
        TextButton(onClick = onOeffnen, enabled = !offen) { Text(stringResource(Res.string.lokal_trees_open)) }
        IconButton(onClick = onLoeschen, enabled = !letzter) {
            Icon(Icons.Default.Delete, stringResource(Res.string.lokal_trees_delete))
        }
    }
}
