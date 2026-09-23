package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.*
import org.jetbrains.compose.resources.stringResource
import java.time.LocalDate

/*
 * Der Eingabedialog: ein eigenes Fenster fuer eine Person - oben Bild, Name und Alter, links Reiter (Daten als
 * Tabelle, Lebenslauf, Medien, Karte), rechts die direkten Verwandten, unten Blaettern, Loeschen, Schliessen.
 * Bild-auf/Bild-ab blaettert durch die Personenliste, Esc schliesst. Die Formulare sind die der App.
 */

/** Eine Zeile der Datentabelle: das Ereignis und, bei Familienereignissen, der Datensatz der Familie. */
private data class FactRow(val fact: FactJson, val record: String?, val label: String)

@Composable
fun PersonSheet(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit, onClose: () -> Unit) {
    val detail = state.detail
    val people = state.people.filter { !it.isPrivate }
    val index = people.indexOfFirst { it.xref == state.selected }
    fun step(delta: Int) {
        if (index >= 0) people.getOrNull(index + delta)?.let { viewModel.select(it.xref) }
    }
    val title = detail?.person?.name.orEmpty() + if (index >= 0) "  (${index + 1} / ${people.size})" else ""

    DialogWindow(
        onCloseRequest = onClose,
        title = title,
        state = rememberDialogState(width = 980.dp, height = 680.dp),
        onPreviewKeyEvent = { e ->
            if (e.type != KeyEventType.KeyDown) false else when (e.key) {
                Key.Escape -> { onClose(); true }
                Key.PageUp -> { step(-1); true }
                Key.PageDown -> { step(1); true }
                else -> false
            }
        },
    ) {
        DeskTheme {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Box(Modifier.fillMaxWidth()) { if (state.loadingDetail) LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (detail == null) return@Column
                SheetHeader(detail)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    SheetTabs(state, detail, viewModel, openWeb, Modifier.weight(1f).fillMaxHeight())
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    RelativesColumn(detail, viewModel, Modifier.width(260.dp).fillMaxHeight())
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SheetFooter(state, detail, viewModel, canPrev = index > 0, canNext = index >= 0 && index < people.lastIndex, onStep = ::step, onClose = onClose)
            }
        }
    }
}

@Composable
private fun SheetHeader(detail: IndividualDetail) {
    val person = detail.person
    val birthYear = person.birth?.date?.year?.takeIf { it > 0 }
    val endYear = if (person.isDead) person.death?.date?.year?.takeIf { it > 0 } else LocalDate.now().year
    val age = if (birthYear != null && endYear != null && endYear >= birthYear) endYear - birthYear else null
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(person, 36.dp)
        Spacer(Modifier.width(10.dp))
        Text(person.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        age?.let { Text(stringResource(Res.string.desk_age, it), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SheetTabs(state: UiState, detail: IndividualDetail, viewModel: AppViewModel, openWeb: (String) -> Unit, modifier: Modifier) {
    var tab by remember { mutableStateOf(0) }
    var dialog by remember { mutableStateOf<ProfileDialog?>(null) }
    val canEdit = detail.canEdit
    val labels = listOf(Res.string.desk_tab_data, Res.string.desk_tab_life, Res.string.tab_media, Res.string.tab_map)

    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
            labels.forEachIndexed { i, l -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(l), style = MaterialTheme.typography.labelLarge) }) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> FactTable(detail, canEdit, onEdit = { r -> dialog = ProfileDialog.EditFact(r.fact, r.record) }, onDelete = { r -> dialog = ProfileDialog.DeleteFact(r.fact, r.record) }, onNew = { dialog = ProfileDialog.NewFact })
                1 -> Timeline(
                    detail, canEdit,
                    onEdit = { fact, record -> dialog = ProfileDialog.EditFact(fact, record) },
                    onDelete = { fact, record -> dialog = ProfileDialog.DeleteFact(fact, record) },
                    onPerson = viewModel::select,
                )
                2 -> MediaGrid(detail.media, onOpen = { item ->
                    when {
                        item.isImage -> viewModel.openMediaViewer(ViewerSource.Profile, detail.media, item, owner = detail.person.name)
                        item.mime == "application/pdf" -> viewModel.openPdf(item.file, item.title, item.url)
                        else -> openWeb(item.url)
                    }
                })
                else -> LifeMap(mapFacts(detail))
            }
        }
    }
    ProfileDialogs(dialog, state, detail, viewModel, onDismiss = { dialog = null }, onPickFamily = { dialog = ProfileDialog.NewFamilyFact(it) })
}

/** Die Daten als Tabelle: Ereignis, Datum, Ort oder Beschreibung. Doppelklick bearbeitet, Knoepfe darunter. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FactTable(detail: IndividualDetail, canEdit: Boolean, onEdit: (FactRow) -> Unit, onDelete: (FactRow) -> Unit, onNew: () -> Unit) {
    val withSpouse = stringResource(Res.string.fact_with_spouse, "%s", "%s")
    val rows = detail.facts.filter { it.known }.map { FactRow(it, null, it.label) } +
        detail.spouseFamilies.flatMap { family ->
            family.facts.filter { it.known }.map { f ->
                FactRow(f, family.xref, family.spouse?.name?.let { withSpouse.replaceFirst("%s", f.label).replaceFirst("%s", it) } ?: f.label)
            }
        }
    var selected by remember(detail.person.xref) { mutableStateOf(-1) }
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(colors.surfaceVariant).padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text(stringResource(Res.string.desk_col_event), Modifier.weight(0.28f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(Res.string.desk_col_date), Modifier.weight(0.22f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(Res.string.desk_col_place), Modifier.weight(0.5f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(rows) { i, row ->
                val f = row.fact
                val where = listOfNotNull(f.value.takeIf { it.isNotBlank() && f.tag != "NAME" }, f.place?.name?.takeIf { it.isNotBlank() }).joinToString(" · ")
                    .ifEmpty { if (f.tag == "NAME") f.value else "" }
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (i == selected) colors.secondaryContainer else if (i % 2 == 1) colors.surfaceContainerLow else colors.surface)
                        .combinedClickable(onClick = { selected = i }, onDoubleClick = { if (canEdit) onEdit(row) })
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text(row.label, Modifier.weight(0.28f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(f.date?.text.orEmpty(), Modifier.weight(0.22f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(where, Modifier.weight(0.5f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                HorizontalDivider(color = colors.outlineVariant)
            }
        }
        if (canEdit) {
            Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(shape = MaterialTheme.shapes.small, onClick = onNew) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(Res.string.action_add_event)) }
                OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { rows.getOrNull(selected)?.let(onEdit) }, enabled = selected >= 0) { Text(stringResource(Res.string.action_edit)) }
                OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { rows.getOrNull(selected)?.let(onDelete) }, enabled = selected >= 0 && rows.getOrNull(selected)?.fact?.tag != "NAME") { Text(stringResource(Res.string.action_delete)) }
            }
        }
    }
}

/** Rechts: Vater, Mutter, Geschwister, Partner, Kinder - jede Gruppe mit einer farbigen Kopfzeile. */
@Composable
private fun RelativesColumn(detail: IndividualDetail, viewModel: AppViewModel, modifier: Modifier) {
    val self = detail.person.xref
    val parents = detail.parentFamilies.firstOrNull()
    val siblings = detail.parentFamilies.flatMap { it.children }.filter { it.xref != self }.distinctBy { it.xref }
    val partners = detail.spouseFamilies.mapNotNull { it.spouse }
    val children = detail.spouseFamilies.flatMap { it.children }
    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        LazyColumn(Modifier.weight(1f)) {
            item { Group(stringResource(Res.string.rel_father), listOfNotNull(parents?.husband), viewModel) }
            item { Group(stringResource(Res.string.rel_mother), listOfNotNull(parents?.wife), viewModel) }
            item { Group(stringResource(Res.string.desk_siblings), siblings, viewModel) }
            item { Group(stringResource(Res.string.rel_partner), partners, viewModel) }
            item { Group(stringResource(Res.string.desk_children), children, viewModel) }
        }
        if (detail.canEdit) {
            TextButton(onClick = { viewModel.requestAddRelative(self) }, modifier = Modifier.padding(4.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(Res.string.action_add_relative))
            }
        }
    }
}

@Composable
private fun Group(title: String, people: List<Person>, viewModel: AppViewModel) {
    val colors = MaterialTheme.colorScheme
    Text(title, Modifier.fillMaxWidth().background(colors.primary).padding(horizontal = 8.dp, vertical = 3.dp), color = colors.onPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    val priv = stringResource(Res.string.person_private)
    val none = stringResource(Res.string.person_no_name)
    people.forEach { p ->
        Text(
            registerName(p, priv, none) + (if (p.lifespan.isNotBlank()) "  ${p.lifespan}" else ""),
            Modifier.fillMaxWidth().clickable(enabled = !p.isPrivate) { viewModel.select(p.xref) }.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
    if (people.isEmpty()) Text("–", Modifier.padding(horizontal = 10.dp, vertical = 3.dp), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SheetFooter(
    state: UiState, detail: IndividualDetail, viewModel: AppViewModel,
    canPrev: Boolean, canNext: Boolean, onStep: (Int) -> Unit, onClose: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (detail.canEdit) TextButton(onClick = { confirmDelete = true }) { Text(stringResource(Res.string.action_delete_person)) }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { onStep(-1) }, enabled = canPrev) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
        IconButton(onClick = { onStep(1) }, enabled = canNext) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) }
        Spacer(Modifier.weight(1f))
        OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { viewModel.setRoot(detail.person.xref); onClose() }) { Text(stringResource(Res.string.desk_as_centre)) }
        Spacer(Modifier.width(6.dp))
        OutlinedButton(shape = MaterialTheme.shapes.small, onClick = onClose) { Text(stringResource(Res.string.action_close)) }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(Res.string.delete_person_title, detail.person.name),
            text = stringResource(Res.string.delete_person_text),
            confirm = stringResource(Res.string.action_delete),
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; viewModel.deletePerson(detail.person.xref); onClose() },
        )
    }
}
