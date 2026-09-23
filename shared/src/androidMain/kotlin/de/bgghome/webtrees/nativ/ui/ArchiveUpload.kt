package de.bgghome.webtrees.nativ.ui

import org.jetbrains.compose.resources.StringResource
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.api.ArchiveEntry
import de.bgghome.webtrees.nativ.api.ArchiveOverview
import de.bgghome.webtrees.nativ.api.ExifRequest
import de.bgghome.webtrees.nativ.api.ArchiveUploadRequest
import kotlinx.coroutines.delay

// "Festhalten": unterwegs ein Foto abfotografieren und mit Ordner, Beschreibung, Datum und Personen ins Archiv legen.
// Es entsteht eine Datei im Archiv, kein Medienobjekt - das Feine bleibt Schreibtischarbeit im Modul.

/** Das Datum, wie das Modul es annimmt: Jahr, Jahr-Monat oder Jahr-Monat-Tag. */
private val ISO_DATE = Regex("""^\d{4}(-(0[1-9]|1[0-2])(-(0[1-9]|[12]\d|3[01]))?)?$""")

/** Der Knopf unten rechts im Archiv. Erst die Quelle (Kamera oder Galerie), dann das Formular zum Bild. */
@Composable
fun ArchiveCaptureButton(state: UiState, viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val archive = state.archive ?: return
    var chooseSource by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<Uri?>(null) }
    val sources = rememberPhotoSources { photo = it }

    ExtendedFloatingActionButton(
        onClick = { chooseSource = true },
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text(stringResource(Res.string.archive_capture)) },
        modifier = modifier,
    )

    if (chooseSource) {
        ChoiceDialog(
            title = stringResource(Res.string.archive_capture),
            options = listOf("take" to stringResource(Res.string.action_take_photo), "pick" to stringResource(Res.string.action_pick_photo)),
            onDismiss = { chooseSource = false },
            onChoose = { chooseSource = false; if (it == "take") sources.take() else sources.pick() },
        )
    }

    photo?.let { uri ->
        val collection = state.collection
        ArchiveUploadDialog(
            uri = uri, archive = archive,
            defaultFolder = collection?.takeIf { it.art == "ordner" }?.ordner.orEmpty(),
            defaultCollection = collection?.takeIf { it.art == "thematisch" }?.slug.orEmpty(),
            suggestPersons = viewModel.personSuggestions(),
            onDismiss = { photo = null },
            onSave = { photo = null; viewModel.uploadArchivePhoto(uri, it) },
        )
    }
}

/** Die Beschriftung im Formular - beim Festhalten und beim Bearbeiten dieselben vier Felder. */
private class ExifForm(beschreibung: String = "", datum: String = "", personen: List<String> = emptyList(), keywords: List<String> = emptyList()) {
    var description by mutableStateOf(beschreibung)
    var date by mutableStateOf(datum)
    var persons by mutableStateOf(personen)
    var keywords by mutableStateOf(keywords.joinToString(", "))

    val dateOk: Boolean get() = date.isBlank() || ISO_DATE.matches(date.trim())

    fun request() = ExifRequest(
        beschreibung = description.trim(), datum = date.trim(), personen = persons,
        keywords = keywords.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    )
}

@Composable
private fun ExifFields(form: ExifForm, suggestPersons: PlaceSuggest?) {
    Field(form.description, { form.description = it }, Res.string.field_description, minLines = 2)
    OutlinedTextField(
        value = form.date, onValueChange = { form.date = it }, label = { Text(stringResource(Res.string.field_date)) },
        supportingText = { Text(stringResource(Res.string.hint_iso_date)) }, isError = !form.dateOk,
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    PersonsField(form.persons, { form.persons = it }, suggestPersons)
    Field(form.keywords, { form.keywords = it }, Res.string.field_keywords)
}

@Composable
private fun ArchiveUploadDialog(
    uri: Uri,
    archive: ArchiveOverview,
    defaultFolder: String,
    defaultCollection: String,
    suggestPersons: PlaceSuggest?,
    onDismiss: () -> Unit,
    onSave: (ArchiveUploadRequest) -> Unit,
) {
    var folder by remember { mutableStateOf(defaultFolder) }
    var collection by remember { mutableStateOf(defaultCollection) }
    val form = remember { ExifForm() }

    val rootLabel = stringResource(Res.string.archive_root_folder)
    val noneLabel = stringResource(Res.string.option_none)
    val thematic = archive.sammlungen.filter { it.art == "thematisch" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.archive_upload_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AsyncImage(
                    model = uri, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(8.dp)),
                )
                Choice(
                    label = Res.string.field_folder,
                    options = listOf("" to rootLabel) + archive.ordnerListe.map { it to it },
                    selected = folder, onSelect = { folder = it },
                )
                ExifFields(form, suggestPersons)
                if (thematic.isNotEmpty()) {
                    Choice(
                        label = Res.string.field_collection,
                        options = listOf("" to noneLabel) + thematic.map { it.slug to it.name },
                        selected = collection, onSelect = { collection = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = form.dateOk,
                onClick = {
                    val exif = form.request()
                    onSave(
                        ArchiveUploadRequest(
                            ordner = folder, beschreibung = exif.beschreibung, datum = exif.datum, personen = exif.personen,
                            keywords = exif.keywords, sammlung = collection,
                        )
                    )
                },
            ) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/**
 * Beschriftung eines Archivbildes aendern - im Betrachter, nativ. Dieselben vier Felder wie die Seitenleiste der
 * Lightbox im Modul; geschrieben wird ueber dessen Route, mit dessen Regel (nur Verwalter).
 */
@Composable
fun ExifEditDialog(entry: ArchiveEntry, suggestPersons: PlaceSuggest?, onDismiss: () -> Unit, onSave: (ExifRequest) -> Unit) {
    val form = remember(entry.pfad) { ExifForm(entry.beschreibung, entry.datumIso.ifEmpty { entry.datum }, entry.exifPersonen, entry.keywords) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.viewer_edit_exif)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.datei, style = MaterialTheme.typography.labelMedium)
                ExifFields(form, suggestPersons)
            }
        },
        confirmButton = { TextButton(enabled = form.dateOk, onClick = { onSave(form.request()) }) { Text(stringResource(Res.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Auswahl aus einer festen Liste als Aufklappfeld. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choice(label: StringResource, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: selected, onValueChange = {}, readOnly = true,
            label = { Text(stringResource(label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (key, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(key); open = false })
            }
        }
    }
}

/**
 * Personen als Chips. Beim Tippen schlaegt die App Namen aus dem Stammbaum vor, damit im EXIF die Schreibweise von
 * webtrees steht und der Abgleich in der Lightbox spaeter trifft. Wer nicht im Baum ist, wird einfach getippt und
 * mit "Hinzufuegen" uebernommen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonsField(persons: List<String>, onChange: (List<String>) -> Unit, suggest: PlaceSuggest?) {
    var input by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }

    fun add(name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty() && clean !in persons) onChange(persons + clean)
        input = ""
        suggestions = emptyList()
    }

    LaunchedEffect(input) {
        val query = input.trim()
        if (suggest == null || query.length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        suggestions = suggest(query).filter { it !in persons }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (persons.isNotEmpty()) {
            // Mehrzeilig waere schoener (FlowRow), ist in dieser Compose-Version noch experimentell.
            persons.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { name ->
                        InputChip(selected = false, onClick = { onChange(persons - name) }, label = { Text(name) })
                    }
                }
            }
        }
        Box {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, label = { Text(stringResource(Res.string.field_persons)) },
                supportingText = { Text(stringResource(Res.string.hint_persons)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                // Eingabetaste uebernimmt den getippten Namen - fuer Leute, die nicht im Baum sind.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add(input) }),
            )
            DropdownMenu(
                expanded = suggestions.isNotEmpty(), onDismissRequest = { suggestions = emptyList() },
                properties = PopupProperties(focusable = false),
            ) {
                suggestions.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { add(name) }) }
            }
        }
        if (input.isNotBlank()) {
            AssistChip(onClick = { add(input) }, label = { Text(stringResource(Res.string.action_add)) })
        }
    }
}
