package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.ui.meldung

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.RepositorySummary
import de.bgghome.webtrees.nativ.api.SourceDetail
import de.bgghome.webtrees.nativ.api.SourceRequest
import de.bgghome.webtrees.nativ.api.SourceSummary
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.Field
import de.bgghome.webtrees.nativ.ui.WtAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/*
 * Quellen und Archive pflegen (Stufe 3, API-Stufe 18): Quelle anlegen und aendern (nur geaenderte Felder gehen an
 * den Server), Archiv waehlen oder neu anlegen, Scan einer Quelle hinzufuegen, Quelle aus einer Datei anlegen.
 */

private const val NEUES_ARCHIV = "\u0000neu"

/** Datei zum Oeffnen waehlen (Systemdialog); null = abgebrochen. */
fun dateiOeffnen(titel: String): File? {
    val dialog = FileDialog(null as Frame?, titel, FileDialog.LOAD).apply { isVisible = true }
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}

/** MIME-Typ nach Dateiendung fuer die Uebertragung an webtrees. */
fun mimeVon(datei: File): String = when (datei.extension.lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "gif" -> "image/gif"; "webp" -> "image/webp"
    "tif", "tiff" -> "image/tiff"; "pdf" -> "application/pdf"; "mp3" -> "audio/mpeg"; "mp4" -> "video/mp4"
    else -> "application/octet-stream"
}

/** Eine Datei als Dokument an eine Quelle haengen. Wirft bei Fehlern. */
suspend fun scanHochladen(client: WtClient, tree: String, quelle: String, datei: File) {
    val bytes = withContext(Dispatchers.IO) { datei.readBytes() }
    client.uploadMedia(tree, quelle, bytes, datei.name, mimeVon(datei), datei.nameWithoutExtension, type = "document")
}

/**
 * Quelle neu anlegen ([alt] null) oder aendern. [onSaved] bekommt die Kennung der Quelle. Beim Aendern gehen nur
 * geaenderte Felder an den Server; alles Uebrige (Medien, weitere Archive, Notiz-Datensaetze) bleibt.
 */
@Composable
fun QuelleDialog(tree: String, alt: SourceDetail?, viewModel: AppViewModel, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    var titel by remember { mutableStateOf(alt?.title.orEmpty()) }
    var autor by remember { mutableStateOf(alt?.author.orEmpty()) }
    var publikation by remember { mutableStateOf(alt?.publication.orEmpty()) }
    var kurz by remember { mutableStateOf(alt?.abbreviation.orEmpty()) }
    var text by remember { mutableStateOf(alt?.text.orEmpty()) }
    var notiz by remember { mutableStateOf(alt?.notes?.firstOrNull().orEmpty()) }
    var archiv by remember { mutableStateOf(alt?.repositories?.firstOrNull()?.xref.orEmpty()) }
    var neuesArchiv by remember { mutableStateOf("") }
    var signatur by remember { mutableStateOf(alt?.callNumber.orEmpty()) }
    var speichert by remember { mutableStateOf(false) }
    var fehler by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val archive by produceState<List<RepositorySummary>?>(null, tree) {
        value = withContext(Dispatchers.IO) { runCatching { viewModel.client.repositories(tree).repositories }.getOrDefault(emptyList()) }
    }
    val keins = stringResource(Res.string.desk_repo_none); val neu = stringResource(Res.string.desk_repo_new)
    val namen = listOf(keins) + archive.orEmpty().map { it.name.ifBlank { it.xref } } + listOf(neu)
    val gewaehltName = when (archiv) { "" -> keins; NEUES_ARCHIV -> neu; else -> archive?.firstOrNull { it.xref == archiv }?.name ?: archiv }

    fun anfrage(repoXref: String): SourceRequest {
        val altRepo = alt?.repositories?.firstOrNull()?.xref.orEmpty()
        fun f(neuW: String, altW: String?) = neuW.trim().takeIf { alt == null || it != altW.orEmpty() }?.let { if (alt == null) it.ifEmpty { null } else it }
        return SourceRequest(
            title = f(titel, alt?.title), author = f(autor, alt?.author), publication = f(publikation, alt?.publication),
            abbreviation = f(kurz, alt?.abbreviation), text = f(text, alt?.text), note = f(notiz, alt?.notes?.firstOrNull()),
            repository = repoXref.takeIf { alt == null && it.isNotEmpty() || alt != null && it != altRepo },
            callNumber = f(signatur, alt?.callNumber),
        )
    }

    WtAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (alt == null) Res.string.desk_source_new else Res.string.desk_source_edit)) },
        text = {
            // Zwei Spalten: links die Angaben zur Quelle, rechts Text und Notiz mit Platz
            Row(Modifier.width(980.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(titel, { titel = it }, Res.string.desk_source_title, hint = Res.string.desk_source_title_hint)
                Field(autor, { autor = it }, Res.string.desk_source_author)
                Field(publikation, { publikation = it }, Res.string.desk_source_publication, hint = Res.string.desk_source_publication_hint)
                Field(kurz, { kurz = it }, Res.string.desk_source_abbreviation)
                Einstellung(stringResource(Res.string.desk_source_repository)) {
                    Auswahl(gewaehltName, namen) { w ->
                        archiv = when (w) { keins -> ""; neu -> NEUES_ARCHIV; else -> archive?.firstOrNull { (it.name.ifBlank { it.xref }) == w }?.xref.orEmpty() }
                    }
                }
                if (archiv == NEUES_ARCHIV) Field(neuesArchiv, { neuesArchiv = it }, Res.string.desk_repo_name)
                if (archiv.isNotEmpty()) Field(signatur, { signatur = it }, Res.string.desk_source_call_number, hint = Res.string.desk_source_call_number_hint)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(text, { text = it }, Res.string.desk_source_text, hint = Res.string.desk_source_text_hint, minLines = 8)
                Field(notiz, { notiz = it }, Res.string.fact_note, minLines = 4)
                fehler?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            }
        },
        breite = 1040.dp,
        confirmButton = {
            TextButton(enabled = titel.isNotBlank() && !speichert && (archiv != NEUES_ARCHIV || neuesArchiv.isNotBlank()), onClick = {
                speichert = true; fehler = null
                scope.launch {
                    runCatching {
                        val repo = if (archiv == NEUES_ARCHIV) viewModel.client.saveRepository(tree, null, neuesArchiv.trim()).xref else archiv
                        viewModel.client.saveSource(tree, alt?.xref, anfrage(repo)).xref.ifEmpty { alt?.xref.orEmpty() }
                    }.onSuccess { onSaved(it); onDismiss() }
                        .onFailure { fehler = meldung(it); speichert = false }
                }
            }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !speichert) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Unbenutzte Quellen auswaehlen und loeschen; alle vorgewaehlt, jede laesst sich abhaken. */
@Composable
fun UnbenutzteDialog(tree: String, quellen: List<SourceSummary>, viewModel: AppViewModel, onDismiss: () -> Unit, onFertig: () -> Unit) {
    val unbenutzt = remember(quellen) { quellen.filter { it.uses == 0 } }
    var gewaehlt by remember { mutableStateOf(unbenutzt.map { it.xref }.toSet()) }
    var laeuft by remember { mutableStateOf(false) }
    var fehler by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    WtAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.desk_sources_unused_title)) },
        text = {
            Column(Modifier.width(520.dp).verticalScroll(rememberScrollState())) {
                if (unbenutzt.isEmpty()) Text(stringResource(Res.string.desk_sources_unused_none))
                else Text(stringResource(Res.string.desk_sources_unused_text, unbenutzt.size), Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.bodyMedium)
                unbenutzt.forEach { q ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = q.xref in gewaehlt, onCheckedChange = { gewaehlt = if (it) gewaehlt + q.xref else gewaehlt - q.xref })
                        Text(q.title.ifBlank { q.xref }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                fehler?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = gewaehlt.isNotEmpty() && !laeuft, onClick = {
                laeuft = true
                scope.launch {
                    runCatching { for (x in gewaehlt) viewModel.client.deleteRecord(tree, x) }
                        .onSuccess { onFertig(); onDismiss() }
                        .onFailure { fehler = meldung(it); laeuft = false; onFertig() }
                }
            }) { Text(stringResource(Res.string.desk_sources_unused_delete, gewaehlt.size)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !laeuft) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Titel fuer eine Quelle, die aus einer Datei entsteht: der Dateiname ohne Endung, Unterstriche als Leerzeichen. */
fun titelAusDatei(datei: File): String = datei.nameWithoutExtension.replace('_', ' ').replace('-', ' ').trim().ifEmpty { datei.name }

/** Der Name des Systemdialogs "Datei waehlen" fuer Scans. */
fun scanDialogTitel(): String = Texte.t(Res.string.desk_source_add_file)
