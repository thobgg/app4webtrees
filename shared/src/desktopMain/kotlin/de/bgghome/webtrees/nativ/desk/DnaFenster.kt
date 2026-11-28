package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.DnaAnsicht
import de.bgghome.webtrees.nativ.ui.UiState
import de.bgghome.webtrees.nativ.ui.setRoot
import org.jetbrains.compose.resources.stringResource

/*
 * DNA-Vererbungslinien (Erstellen > DNA-Vererbungslinien, nur mit dem Modul dna4webtrees auf dem Server): die Linien der Person, die beim Oeffnen
 * Proband war. Ein Klick auf eine Person der Liste macht sie im Hauptfenster zum Probanden; die Liste bleibt stehen,
 * bis man unten ausdruecklich von der neuen Person ausgeht - sonst waere sie nach jedem Klick weg. Wer bearbeiten darf,
 * traegt hier auch einen DNA-Test oder -Treffer fuer diese Person ein (DnaEintragen.kt).
 */
@Composable
fun DnaFenster(state: UiState, viewModel: AppViewModel, onClose: () -> Unit) {
    val tree = state.tree?.name ?: return
    val proband = state.root ?: return
    var start by remember { mutableStateOf(proband) }
    // Eintragen: nur wer im Baum bearbeiten darf; das Modul prueft das Recht an der Person noch einmal
    var formular by remember { mutableStateOf<DnaFormular?>(null) }
    var stand by remember { mutableStateOf(0) }
    fun name(x: String) = state.detail?.takeIf { it.person.xref == x }?.person?.name ?: state.people.firstOrNull { it.xref == x }?.name ?: x
    val farben = MaterialTheme.colorScheme
    androidx.compose.ui.window.Window(
        onCloseRequest = onClose, title = stringResource(Res.string.dna_title) + " – " + state.tree?.title.orEmpty(),
        state = arbeitsfenster("fenster_dna", 640, 780),
        onPreviewKeyEvent = { e ->
            when {
                e.type == KeyEventType.KeyDown && e.key == Key.Escape -> { onClose(); true }
                e.type == KeyEventType.KeyDown && e.key == Key.F1 -> { Hilfe.oeffnen("listen"); true }
                else -> false
            }
        },
    ) {
        formular?.let { f -> DnaEintragenDialog(f, viewModel.client, tree, start, name(start), onClose = { gespeichert -> formular = null; if (gespeichert) stand++ }) }
        Surface(Modifier.fillMaxSize(), color = farben.surface) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth().border(1.dp, farben.outlineVariant)) {
                    DnaAnsicht(viewModel.client, tree, start, onPerson = { viewModel.setRoot(it) }, mitBaum = true, stand = stand)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (proband != start) Knopf(stringResource(Res.string.dna_from_selected, name(proband)), true) { start = proband }
                    if (state.tree?.canEdit == true) {
                        Knopf(stringResource(Res.string.dna_add_test), true) { formular = DnaFormular.Test }
                        Knopf(stringResource(Res.string.dna_add_match), true) { formular = DnaFormular.Treffer }
                    }
                    Spacer(Modifier.weight(1f))
                    Knopf(stringResource(Res.string.action_close), true, onClose)
                }
            }
        }
    }
}
