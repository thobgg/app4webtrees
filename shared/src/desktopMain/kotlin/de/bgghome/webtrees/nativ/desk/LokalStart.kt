package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import de.bgghome.webtrees.nativ.ui.LocalStartKopf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.lokal.LokalBetrieb
import de.bgghome.webtrees.nativ.lokal.LokalOrte
import de.bgghome.webtrees.nativ.lokal.LokalerZugang
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppRoot
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.LocalAppName
import de.bgghome.webtrees.nativ.ui.login
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/**
 * Erster Start am PC (Stufe 4): zwei Wege nebeneinander - links mit einem webtrees verbinden (der Bildschirm der
 * App wie bisher), rechts einen neuen Stammbaum auf diesem PC anlegen. Danach sieht man keinen Server und kein Passwort.
 */
@Composable
fun DeskStart(viewModel: AppViewModel) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Marke oben ueber beiden Wegen; links ersetzt eine Ueberschrift den Programmnamen (der steht schon in der Marke)
            Box(Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 8.dp), contentAlignment = Alignment.Center) { Markenkopf() }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                CompositionLocalProvider(LocalStartKopf provides { Text(stringResource(Res.string.desk_connect_heading), style = MaterialTheme.typography.headlineMedium) }) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { AppRoot(viewModel) }
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.weight(1f).fillMaxHeight()) { LokalAnlegen(viewModel) }
            }
        }
    }
}

@Composable
private fun LokalAnlegen(viewModel: AppViewModel) {
    val appName = LocalAppName.current
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Gibt es schon Stammbaeume auf diesem PC, oeffnet der Knopf den zuletzt benutzten - dann heisst er auch so
                // (vorher "Stammbaum anlegen", das hielt niemand fuer den Weg zurueck, Issue 31).
                val vorhanden = remember { LokalOrte.eingerichtet && LokalerZugang.laden() != null }
                Text(stringResource(if (vorhanden) Res.string.lokal_heading_existing else Res.string.lokal_heading), style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (vorhanden) stringResource(Res.string.lokal_text_existing) else stringResource(Res.string.lokal_text, appName),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LokalAnlegenFeld(viewModel, oeffnen = vorhanden)
                Text(
                    stringResource(Res.string.lokal_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Name, "Stammbaum anlegen", "Aus GEDCOM-Datei übernehmen", Fortschritt und Fehler - im Startbildschirm und im Dialog
 * "Stammbäume auf diesem PC" (dort mit [neu]: immer ein weiterer Baum, nie der vorhandene).
 */
@Composable
internal fun LokalAnlegenFeld(viewModel: AppViewModel, neu: Boolean = false, oeffnen: Boolean = false, onFertig: () -> Unit = {}) {
    val vorgabe = stringResource(Res.string.lokal_title_default)
    var titel by remember { mutableStateOf(vorgabe) }
    var schritt by remember { mutableStateOf<String?>(null) }
    var fehler by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Anlegen, leer oder aus einer GEDCOM-Datei; blockiert (Auspacken, Import), also im Hintergrund.
    fun anlegen(gedcom: java.io.File?) {
        fehler = null
        schritt = "…"
        // Beim Uebernehmen heisst der Baum wie die Datei, solange der Name nicht von Hand geaendert wurde.
        val name = titel.trim().takeUnless { it.isEmpty() || (gedcom != null && it == vorgabe) }
            ?: gedcom?.nameWithoutExtension ?: vorgabe
        scope.launch {
            runCatching {
                val (adresse, zugang) = withContext(Dispatchers.IO) { LokalBetrieb.anlegen(name, gedcom, neu) { schritt = it } }
                viewModel.client.baseUrl = adresse
                viewModel.settings.baseUrl = viewModel.client.baseUrl
                // Gleich den eben angelegten Baum oeffnen, auch wenn schon andere da sind
                viewModel.settings.tree = zugang.baum
                viewModel.login(zugang.benutzer, zugang.passwort)
            }.onSuccess { onFertig() }.onFailure { fehler = it.message ?: it.toString() }
            schritt = null
        }
    }
    val dialogTitel = stringResource(Res.string.lokal_gedcom_choose)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Mit [oeffnen] kein Namensfeld: der Knopf oeffnet den vorhandenen Baum (LokaleEinrichtung.einrichten ohne GEDCOM)
        if (!oeffnen) OutlinedTextField(
            value = titel, onValueChange = { titel = it }, singleLine = true, enabled = schritt == null,
            label = { Text(stringResource(Res.string.lokal_title_label)) }, modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { anlegen(null) },
            enabled = schritt == null && (oeffnen || titel.isNotBlank()),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (oeffnen) Res.string.lokal_trees_open else Res.string.lokal_create)) }
        OutlinedButton(
            onClick = { gedcomWaehlen(dialogTitel)?.let(::anlegen) },
            enabled = schritt == null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(Res.string.lokal_gedcom)) }
        Text(
            stringResource(Res.string.lokal_gedcom_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        schritt?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(2.dp).widthIn(max = 18.dp), strokeWidth = 2.dp)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
        fehler?.let {
            // Verstaendlich oben, der rohe Text klein darunter (fuer die Fehlermeldung an uns)
            Text(stringResource(Res.string.lokal_error, LokalBetrieb.protokoll), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Text(it.take(1500), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** GEDCOM-Datei aus dem bisherigen Programm waehlen (.ged, unter Windows auch .GED). */
private fun gedcomWaehlen(titel: String): java.io.File? {
    val d = java.awt.FileDialog(null as java.awt.Frame?, titel, java.awt.FileDialog.LOAD).apply {
        setFilenameFilter { _, name -> name.endsWith(".ged", ignoreCase = true) }
        file = "*.ged"
        isVisible = true
    }
    return d.file?.let { java.io.File(d.directory, it) }?.takeIf { it.isFile }
}
