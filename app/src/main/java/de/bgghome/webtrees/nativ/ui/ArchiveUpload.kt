package de.bgghome.webtrees.nativ.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import de.bgghome.webtrees.nativ.R
import de.bgghome.webtrees.nativ.api.ArchiveOverview
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
        text = { Text(stringResource(R.string.archive_capture)) },
        modifier = modifier,
    )

    if (chooseSource) {
        ChoiceDialog(
            title = stringResource(R.string.archive_capture),
            options = listOf("take" to stringResource(R.string.action_take_photo), "pick" to stringResource(R.string.action_pick_photo)),
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var persons by remember { mutableStateOf(listOf<String>()) }
    var keywords by remember { mutableStateOf("") }
    var collection by remember { mutableStateOf(defaultCollection) }

    val dateOk = ISO_DATE.matches(date.trim()) || date.isBlank()
    val rootLabel = stringResource(R.string.archive_root_folder)
    val noneLabel = stringResource(R.string.option_none)
    val thematic = archive.sammlungen.filter { it.art == "thematisch" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.archive_upload_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AsyncImage(
                    model = uri, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(8.dp)),
                )
                Choice(
                    label = R.string.field_folder,
                    options = listOf("" to rootLabel) + archive.ordnerListe.map { it to it },
                    selected = folder, onSelect = { folder = it },
                )
                Field(description, { description = it }, R.string.field_description, minLines = 2)
                OutlinedTextField(
                    value = date, onValueChange = { date = it }, label = { Text(stringResource(R.string.field_date)) },
                    supportingText = { Text(stringResource(R.string.hint_iso_date)) }, isError = !dateOk,
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                PersonsField(persons, { persons = it }, suggestPersons)
                Field(keywords, { keywords = it }, R.string.field_keywords)
                if (thematic.isNotEmpty()) {
                    Choice(
                        label = R.string.field_collection,
                        options = listOf("" to noneLabel) + thematic.map { it.slug to it.name },
                        selected = collection, onSelect = { collection = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = dateOk,
                onClick = {
                    onSave(
                        ArchiveUploadRequest(
                            ordner = folder, beschreibung = description.trim(), datum = date.trim(), personen = persons,
                            keywords = keywords.split(',').map { it.trim() }.filter { it.isNotEmpty() }, sammlung = collection,
                        )
                    )
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Auswahl aus einer festen Liste als Aufklappfeld. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choice(label: Int, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
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
                value = input, onValueChange = { input = it }, label = { Text(stringResource(R.string.field_persons)) },
                supportingText = { Text(stringResource(R.string.hint_persons)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
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
            AssistChip(onClick = { add(input) }, label = { Text(stringResource(R.string.action_add)) })
        }
    }
}
