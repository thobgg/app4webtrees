package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.data.Ortsanzeige
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import de.bgghome.webtrees.nativ.data.artZusatz
import de.bgghome.webtrees.nativ.data.Heiratsart
import de.bgghome.webtrees.nativ.data.notizenOhnePaten
import de.bgghome.webtrees.nativ.data.ohneDoppelteAsso
import de.bgghome.webtrees.nativ.ui.PatenZeilen
import de.bgghome.webtrees.nativ.ui.faktLabelMitArt
import de.bgghome.webtrees.nativ.ui.heiratsartText
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable
import de.bgghome.webtrees.nativ.ui.saveFact
import de.bgghome.webtrees.nativ.ui.editMedia
import de.bgghome.webtrees.nativ.ui.unlinkMedia
import de.bgghome.webtrees.nativ.ui.linkMedia
import de.bgghome.webtrees.nativ.ui.uploadMediaFile
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
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
import de.bgghome.webtrees.nativ.api.halfSiblings
import de.bgghome.webtrees.nativ.api.SourceRef
import de.bgghome.webtrees.nativ.ui.saveCitations
import de.bgghome.webtrees.nativ.ui.ConfirmDialog
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
fun PersonSheet(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit, onClose: () -> Unit, onQuelle: ((String) -> Unit)? = null) {
    val detail = state.detail
    val people = state.people.filter { !it.isPrivate }
    val index = people.indexOfFirst { it.xref == state.selected }
    fun step(delta: Int) {
        if (index >= 0) people.getOrNull(index + delta)?.let { viewModel.select(it.xref) }
    }
    val gesamt = state.tree?.individuals ?: people.size
    val title = (detail?.person?.let { registerName(it, "", "") }.orEmpty()) + if (index >= 0 && state.query.isEmpty()) "  [${index + 1} von $gesamt]" else ""
    // Einfacher Eingabemodus (Formular) oder vollstaendig (Tabelle); die Wahl bleibt gespeichert.
    var einfach by remember { mutableStateOf(DeskLayout.prefs.getBoolean("blatt_einfach", false)) }
    // Schliessen mit ungespeicherten Eingaben (auch bei anderen Personen): erst nachfragen.
    var schliessenFragen by remember { mutableStateOf(false) }
    // Beim Schliessen: Gueltiges wird still gespeichert; nur ein ungueltiges Datum haelt auf
    val schliessen: () -> Unit = { if (Entwuerfe.abschliessen(viewModel)) schliessenFragen = true else onClose() }

    androidx.compose.ui.window.Window(
        onCloseRequest = schliessen,
        title = title,
        state = arbeitsfenster("fenster_personenblatt", 1100, 820),
        onPreviewKeyEvent = { e ->
            if (e.type != KeyEventType.KeyDown) false else when (e.key) {
                Key.Escape -> { schliessen(); true }
                Key.F1 -> { Hilfe.oeffnen("person"); true }
                Key.PageUp -> { step(-1); true }
                Key.PageDown -> { step(1); true }
                Key.MoveHome -> { if (e.isCtrlPressed) { step(-index); true } else false }
                Key.MoveEnd -> { if (e.isCtrlPressed) { step(people.lastIndex - index); true } else false }
                else -> false
            }
        },
        onKeyEvent = { e -> personKopierTaste(e, viewModel) },
    ) {
        DeskTheme { androidx.compose.runtime.CompositionLocalProvider(LocalQuelleOeffnen provides onQuelle, LocalOpenWeb provides openWeb) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Box(Modifier.fillMaxWidth()) { if (state.loadingDetail) LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (detail == null) return@Column
                SheetHeader(detail)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    SheetTabs(state, detail, viewModel, openWeb, Modifier.weight(1f).fillMaxHeight(), einfach, onEinfach = { einfach = it; DeskLayout.prefs.putBoolean("blatt_einfach", it) })
                    val verwandteBreite = rememberBreite("blatt_verwandte", 260f)
                    Trenner(verwandteBreite, "blatt_verwandte", 200f, 520f, rechts = true)
                    RelativesColumn(detail, viewModel, Modifier.width(verwandteBreite.value.dp).fillMaxHeight())
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SheetFooter(state, detail, viewModel, canPrev = index > 0, canNext = index >= 0 && index < people.lastIndex, onStep = ::step, onFirst = { step(-index) }, onLast = { step(people.lastIndex - index) }, onClose = schliessen)
                if (schliessenFragen) UngespeichertDialog(viewModel, onWeiter = { schliessenFragen = false; onClose() }, onAbbrechen = { schliessenFragen = false })
            }
        } }
    }
}

@Composable
private fun SheetHeader(detail: IndividualDetail) {
    val person = detail.person
    val birthYear = person.birth?.date?.year?.takeIf { it > 0 }
    val endYear = if (person.isDead) person.death?.date?.year?.takeIf { it > 0 } else LocalDate.now().year
    val age = if (birthYear != null && endYear != null && endYear >= birthYear) endYear - birthYear else null
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Portrait(person, Modifier.size(56.dp).clip(MaterialTheme.shapes.small))
        Spacer(Modifier.width(12.dp))
        Text(person.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        // Kennung des Datensatzes (Issue 13): klein, markierbar zum Kopieren; die eindeutige Kennung (GEDCOM-L _UID) als Tooltip
        Tipp(detail.uids.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.desk_gl_uid) + ": " + it.joinToString(", ") }) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(person.xref, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
        age?.let { Text(stringResource(Res.string.desk_age, it), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SheetTabs(state: UiState, detail: IndividualDetail, viewModel: AppViewModel, openWeb: (String) -> Unit, modifier: Modifier, einfach: Boolean, onEinfach: (Boolean) -> Unit) {
    val formular = Entwuerfe.fuer(detail.person.xref)
    LaunchedEffect(detail) { formular.abgleichen(detail) }
    var tab by remember { mutableStateOf(0) }
    var dialog by remember { mutableStateOf<ProfileDialog?>(null) }
    val canEdit = detail.canEdit
    val labels = listOf(
        Res.string.desk_tab_data, Res.string.desk_tab_parents, Res.string.desk_tab_partners, Res.string.desk_tab_notes,
        Res.string.desk_tab_sources, Res.string.tab_media, Res.string.desk_tab_life, Res.string.tab_map,
    )

    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        // Kompakte Reiter mit Symbol, alle sichtbar; bei zu wenig Platz waagerecht rollbar.
        val icons = listOf(Icons.AutoMirrored.Filled.List, Icons.Default.Person, Icons.Default.Favorite, Icons.Default.Create, Icons.Default.Info, PhotoIcon, Icons.Default.DateRange, Icons.Default.Place)
        val tabScroll = androidx.compose.foundation.rememberScrollState()
        Row(Modifier.fillMaxWidth().horizontalScroll(tabScroll)) {
            labels.forEachIndexed { i, l ->
                val aktiv = tab == i
                Column(
                    Modifier.clickable { tab = i }.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icons[i], contentDescription = null, Modifier.size(15.dp), tint = if (aktiv) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(l), style = MaterialTheme.typography.labelLarge, color = if (aktiv) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    }
                    Box(Modifier.padding(top = 4.dp).height(2.dp).fillMaxWidth().background(if (aktiv) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                // Der Umschalter gehoert zum Reiter Daten - nur hier wirkt er (Formular oder Tabelle)
                0 -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.desk_mode_label), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Tipp(stringResource(Res.string.tipp_mode)) {
                            Row(Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)) {
                                listOf(true to Res.string.desk_mode_simple, false to Res.string.desk_mode_full).forEach { (wert, text) ->
                                    Text(stringResource(text), Modifier.background(if (einfach == wert) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.shapes.small)
                                        .clickable { onEinfach(wert) }.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) { if (einfach) EinfachDaten(formular, canEdit, viewModel) else FactTable(detail, canEdit, viewModel, onEdit = { r -> dialog = ProfileDialog.EditFact(r.fact, r.record) }, onDelete = { r -> dialog = ProfileDialog.DeleteFact(r.fact, r.record) }, onNew = { dialog = ProfileDialog.NewFact }) }
                }
                1 -> ParentsTab(detail, viewModel)
                2 -> PartnersTab(detail, viewModel, onNewFact = { family -> dialog = ProfileDialog.NewFamilyFact(family) },
                    onEdit = { r -> dialog = ProfileDialog.EditFact(r.fact, r.record) }, onDelete = { r -> dialog = ProfileDialog.DeleteFact(r.fact, r.record) })
                3 -> NotesTab(detail, openWeb, darfSchreiben = canEdit && (state.info?.api ?: 0) >= de.bgghome.webtrees.nativ.api.API_PLACE_RENAME,
                    viewModel = viewModel, onLoeschen = { f -> dialog = ProfileDialog.DeleteFact(f, null) })
                4 -> SourcesTab(detail, openWeb)
                6 -> Timeline(
                    detail, canEdit,
                    onEdit = { fact, record -> dialog = ProfileDialog.EditFact(fact, record) },
                    onDelete = { fact, record -> dialog = ProfileDialog.DeleteFact(fact, record) },
                    onPerson = viewModel::select,
                )
                5 -> PersonMedien(detail, viewModel, state, openWeb, darfSchreiben = canEdit && (state.info?.api ?: 0) >= de.bgghome.webtrees.nativ.api.API_PLACE_RENAME)
                else -> LifeMap(mapFacts(detail))
            }
        }
    }
    ProfileDialogs(dialog, state, detail, viewModel, onDismiss = { dialog = null }, onPickFamily = { dialog = ProfileDialog.NewFamilyFact(it) })
}

/** Eine anklickbare Personenzeile im Stil der Verwandtenliste. */
@Composable
private fun PersonLine(p: Person, viewModel: AppViewModel, einzug: Int = 0) {
    val priv = stringResource(Res.string.person_private)
    val none = stringResource(Res.string.person_no_name)
    Text(
        registerName(p, priv, none) + jahre(p).let { if (it.isNotEmpty()) "   $it" else "" },
        Modifier.fillMaxWidth().fokusRahmen().clickable(enabled = !p.isPrivate) { viewModel.select(p.xref) }.padding(start = (12 + einzug * 18).dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Heading(text: String) {
    Text(text, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
}

/** Reiter Eltern/Geschwister: jede Herkunftsfamilie mit Vater, Mutter und den Geschwistern. */
@Composable
private fun ParentsTab(detail: IndividualDetail, viewModel: AppViewModel) {
    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = list) {
            detail.parentFamilies.forEach { fam ->
                item { Heading(stringResource(Res.string.rel_father)) }
                item { fam.husband?.let { PersonLine(it, viewModel) } ?: Text("–", Modifier.padding(12.dp, 4.dp)) }
                item { Heading(stringResource(Res.string.rel_mother)) }
                item { fam.wife?.let { PersonLine(it, viewModel) } ?: Text("–", Modifier.padding(12.dp, 4.dp)) }
                item { Heading(stringResource(Res.string.desk_siblings)) }
                items(fam.children) { c -> if (c.xref == detail.person.xref) Text(registerName(c, "", "") + "   " + c.lifespan, Modifier.padding(12.dp, 5.dp), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium) else PersonLine(c, viewModel) }
            }
            if (detail.parentFamilies.isEmpty()) item { Text("–", Modifier.padding(12.dp)) }
            detail.halfSiblings().groupBy { it.paternal }.toSortedMap(reverseOrder()).forEach { (paternal, group) ->
                item { Heading(stringResource(if (paternal) Res.string.half_siblings_paternal else Res.string.half_siblings_maternal)) }
                items(group) { PersonLine(it.person, viewModel) }
            }
        }
        ListenLeiste(list)
    }
}

/**
 * Reiter Partner/Kinder als Arbeitsflaeche: links die Partnerschaften, rechts die Kinder der gewaehlten, darunter ihre
 * Ereignisse (Heirat, Scheidung, Wohnort ...) zum Anlegen, Bearbeiten und Loeschen. Doppelklick auf Partner oder Kind
 * zeigt dessen Blatt.
 */
@Composable
private fun PartnersTab(
    detail: IndividualDetail, viewModel: AppViewModel,
    onNewFact: (String) -> Unit, onEdit: (FactRow) -> Unit, onDelete: (FactRow) -> Unit,
) {
    val familien = detail.spouseFamilies
    var gewaehlt by remember(detail.person.xref) { mutableStateOf(0) }
    val familie = familien.getOrNull(gewaehlt.coerceIn(0, (familien.size - 1).coerceAtLeast(0)))
    val canEdit = detail.canEdit
    val colors = MaterialTheme.colorScheme

    val partnerHoehe = rememberBreite("blatt_partner_hoehe", 200f)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(partnerHoehe.value.dp)) {
            // ── Partner ──
            Column(Modifier.weight(1f).fillMaxHeight()) {
                KopfMitPlus(stringResource(Res.string.rel_partner), if (canEdit) stringResource(Res.string.desk_partner_add) else null) {
                    Verwandtenwahl.vorwahl = "spouse"; viewModel.requestAddRelative(detail.person.xref)
                }
                val liste = rememberLazyListState()
                Box(Modifier.weight(1f)) {
                    LazyColumn(Modifier.fillMaxSize(), state = liste) {
                        itemsIndexed(familien) { i, fam ->
                            val sp = fam.spouse
                            val jahr = fam.marriage?.date?.year?.takeIf { it > 0 }?.let { "   oo $it" }.orEmpty()
                            val name = sp?.let { registerName(it, stringResource(Res.string.person_private), stringResource(Res.string.person_no_name)) + jahre(it).let { j -> if (j.isNotEmpty()) "  $j" else "" } }
                                ?: unbekannterPartner(detail.person.sex)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    AuswahlZeile(name + jahr, i == gewaehlt, kursiv = sp == null,
                                        onClick = { gewaehlt = i }, onDoppel = sp?.takeIf { !it.isPrivate }?.let { { viewModel.select(it.xref) } })
                                }
                                // Reihenfolge der Partnerschaften (ab API-Stufe 30, mit Bearbeitungsrecht)
                                if (canEdit && viewModel.tasksSupported && familien.size > 1) ReihenfolgePfeile(i, familien.size) { ziel ->
                                    val order = familien.map { it.xref }.toMutableList().also { it.add(ziel, it.removeAt(i)) }
                                    viewModel.reorder(detail.person.xref, "families", order, detail.person.xref) { gewaehlt = ziel }
                                }
                            }
                        }
                        if (familien.isEmpty()) item { Text("–", Modifier.padding(12.dp, 6.dp), color = colors.onSurfaceVariant) }
                    }
                    ListenLeiste(liste)
                }
            }
            VerticalDivider(color = colors.outlineVariant)
            // ── Kinder der gewaehlten Partnerschaft ──
            Column(Modifier.weight(1f).fillMaxHeight()) {
                KopfMitPlus(stringResource(Res.string.desk_children), if (canEdit) stringResource(Res.string.desk_child_add) else null) {
                    Verwandtenwahl.vorwahl = "child"; Verwandtenwahl.familie = familie?.xref; viewModel.requestAddRelative(detail.person.xref)
                }
                val liste = rememberLazyListState()
                Box(Modifier.weight(1f)) {
                    LazyColumn(Modifier.fillMaxSize(), state = liste) {
                        val kinder = familie?.children.orEmpty()
                        itemsIndexed(kinder) { i, k ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    AuswahlZeile(registerName(k, stringResource(Res.string.person_private), stringResource(Res.string.person_no_name)) + jahre(k).let { if (it.isNotEmpty()) "  $it" else "" },
                                        false, onClick = {}, onDoppel = if (k.isPrivate) null else { { viewModel.select(k.xref) } })
                                }
                                // Reihenfolge der Kinder an der Familie (ab API-Stufe 30, mit Bearbeitungsrecht)
                                if (canEdit && viewModel.tasksSupported && familie != null && kinder.size > 1) ReihenfolgePfeile(i, kinder.size) { ziel ->
                                    val order = kinder.map { it.xref }.toMutableList().also { it.add(ziel, it.removeAt(i)) }
                                    viewModel.reorder(familie.xref, "children", order, detail.person.xref)
                                }
                            }
                        }
                        if (kinder.isEmpty()) item { Text("–", Modifier.padding(12.dp, 6.dp), color = colors.onSurfaceVariant) }
                    }
                    ListenLeiste(liste)
                }
            }
        }
        TrennerWaagrecht(partnerHoehe, "blatt_partner_hoehe", 110f, 600f)
        // ── Ereignisse der Partnerschaft ──
        // Familienstand nach GEDCOM-L (1 _STAT) als Auswahl, ab API-Stufe 35 mit Bearbeitungsrecht; dann nicht noch einmal in der Tabelle
        val standWaehlbar = familie != null && canEdit && (viewModel.state.value.info?.api ?: 0) >= de.bgghome.webtrees.nativ.api.API_GEDCOML_WRITE
        if (familie != null && standWaehlbar) Familienstand(familie, viewModel)
        if (familie != null) {
            val rows = familie.facts.filter { it.known && !(standWaehlbar && it.tag == "_STAT") }.map { FactRow(it, familie.xref, faktLabelMitArt(it)) }
            EreignisTabelle(rows, familie.xref, canEdit, onEdit, onDelete, onNew = { onNewFact(familie.xref) }, Modifier.weight(1f).fillMaxWidth(), geburtJd(detail), detail, viewModel)
        } else {
            Text(stringResource(Res.string.desk_partner_none), Modifier.padding(12.dp), color = colors.onSurfaceVariant)
        }
    }
}

/** Familienstand einer Partnerschaft (GEDCOM-L 1 _STAT): nicht verheiratet, nie verheiratet, unbekannt oder keine Angabe. */
@Composable
private fun Familienstand(familie: de.bgghome.webtrees.nativ.api.FamilyJson, viewModel: AppViewModel) {
    val alt = familie.facts.firstOrNull { it.tag == "_STAT" }
    val roh = (alt?.gedcomValue ?: alt?.value).orEmpty().trim().uppercase()
    val texte = mapOf("" to "–", "NOT MARRIED" to stringResource(Res.string.desk_gl_status_not_married),
        "NEVER MARRIED" to stringResource(Res.string.desk_gl_status_never_married), "UNKNOWN" to stringResource(Res.string.desk_gl_status_unknown))
    var offen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.desk_gl_family_status), Modifier.width(130.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { offen = true }, shape = MaterialTheme.shapes.small) { Text(texte[roh] ?: roh) }
            DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
                texte.forEach { (wert, text) ->
                    DropdownMenuItem(text = { Text(text) }, onClick = { offen = false; if (wert != roh) viewModel.familyStatus(familie, wert) })
                }
            }
        }
    }
}

@Composable
private fun KopfMitPlus(titel: String, plus: String?, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(titel, Modifier.weight(1f).padding(vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        if (plus != null) Tipp(plus) {
            IconButton(onClick = onPlus, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, contentDescription = plus, Modifier.size(16.dp)) }
        }
    }
}

/** Zeile einer Auswahlliste: Klick waehlt, Doppelklick oeffnet (ohne [onDoppel] nicht). */
@OptIn(ExperimentalFoundationApi::class)
/** Zwei kleine Pfeile: Zeile [i] von [n] nach oben oder unten schieben; [onZiel] bekommt die neue Stelle. */
@Composable
private fun ReihenfolgePfeile(i: Int, n: Int, onZiel: (Int) -> Unit) {
    Row {
        Tipp(stringResource(Res.string.desk_reorder_up)) { TextButton(onClick = { onZiel(i - 1) }, enabled = i > 0, modifier = Modifier.width(34.dp)) { Text("▲", style = MaterialTheme.typography.labelSmall) } }
        Tipp(stringResource(Res.string.desk_reorder_down)) { TextButton(onClick = { onZiel(i + 1) }, enabled = i < n - 1, modifier = Modifier.width(34.dp)) { Text("▼", style = MaterialTheme.typography.labelSmall) } }
    }
}

@Composable
private fun AuswahlZeile(text: String, aktiv: Boolean, kursiv: Boolean = false, onClick: () -> Unit, onDoppel: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    Text(
        text,
        Modifier.fillMaxWidth().background(if (aktiv) colors.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .fokusRahmen().combinedClickable(onDoubleClick = onDoppel, onClick = onClick).padding(horizontal = 12.dp, vertical = 5.dp),
        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        fontWeight = if (aktiv) FontWeight.SemiBold else FontWeight.Normal,
        fontStyle = if (kursiv) androidx.compose.ui.text.font.FontStyle.Italic else null,
        color = if (kursiv) colors.onSurfaceVariant else colors.onSurface,
    )
}

/** Reiter Notizen: eigene Notizen und die an Ereignissen. Bearbeitet wird vorerst in webtrees. */
@Composable
private fun NotesTab(detail: IndividualDetail, openWeb: (String) -> Unit, darfSchreiben: Boolean = false, viewModel: AppViewModel? = null,
                     onLoeschen: (de.bgghome.webtrees.nativ.api.FactJson) -> Unit = {}) {
    // Allgemeine Notizen der Person (1 NOTE) lassen sich schreiben; Notizen an Ereignissen und Verweise auf
    // Notiz-Datensaetze (1 NOTE @N1@) bleiben hier zum Lesen
    val eigene = detail.facts.filter { it.tag == "NOTE" }
    val anEreignissen = notizenVon(detail).filter { it.first.isNotBlank() }
    var bearbeiten by remember { mutableStateOf<de.bgghome.webtrees.nativ.api.FactJson?>(null) }
    var neu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        val list = rememberLazyListState()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), state = list) {
                if (eigene.isEmpty() && anEreignissen.isEmpty()) item { Text(stringResource(Res.string.desk_notes_none), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(eigene, key = { it.id }) { f ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(f.value, style = MaterialTheme.typography.bodyMedium)
                        if (darfSchreiben) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (f.noteXref == null) Text(stringResource(Res.string.desk_media_edit), Modifier.clickable { bearbeiten = f }.padding(vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            else Text(stringResource(Res.string.desk_note_shared), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(Res.string.action_delete), Modifier.clickable { onLoeschen(f) }.padding(vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                items(anEreignissen) { (wo, text) ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(wo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            ListenLeiste(list)
        }
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (darfSchreiben) OutlinedButton(onClick = { neu = true }, shape = MaterialTheme.shapes.small) { Text("+ " + stringResource(Res.string.desk_note_new)) }
            TextButton(onClick = { openWeb(detail.person.url) }) { Text(stringResource(Res.string.desk_edit_in_web)) }
        }
    }
    if (viewModel != null && (neu || bearbeiten != null)) {
        val alt = bearbeiten
        NotizDialog(alt?.value.orEmpty(), onDismiss = { neu = false; bearbeiten = null }) { text ->
            neu = false; bearbeiten = null
            if (alt == null) viewModel.saveFact(de.bgghome.webtrees.nativ.api.FactRequest(tag = "NOTE", value = text))
            else viewModel.saveFact(de.bgghome.webtrees.nativ.api.FactRequest(factId = alt.id, value = text))
        }
    }
}

/** Eine Notiz schreiben oder aendern (mehrzeilig). */
@Composable
private fun NotizDialog(alt: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(alt) }
    de.bgghome.webtrees.nativ.ui.WtAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (alt.isEmpty()) Res.string.desk_note_new else Res.string.desk_note_edit)) },
        text = { androidx.compose.material3.OutlinedTextField(text, { text = it }, Modifier.width(560.dp), minLines = 8, label = { Text(stringResource(Res.string.fact_note)) }) },
        confirmButton = { TextButton(enabled = text.isNotBlank() && text.trim() != alt.trim(), onClick = { onSave(text.trim()) }) { Text(stringResource(Res.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/**
 * Fotos und Dokumente der Person: Klick oeffnet sie wie bisher; mit Bearbeitungsrecht hochladen (auch per Ziehen aus
 * dem Dateimanager), vorhandenes Medium oder eines aus dem Archiv verknuepfen, Titel und Art aendern, loesen.
 */
@Composable
private fun PersonMedien(detail: IndividualDetail, viewModel: AppViewModel, state: UiState, openWeb: (String) -> Unit, darfSchreiben: Boolean) {
    val m = detail.media
    var waehlen by remember { mutableStateOf(false) }
    var bearbeiten by remember { mutableStateOf<de.bgghome.webtrees.nativ.api.MediaJson?>(null) }
    var ziehtDarueber by remember { mutableStateOf(false) }
    fun hochladen(dateien: List<java.io.File>) = dateien.forEach { datei ->
        viewModel.uploadMediaFile(datei.readBytes(), datei.name, mimeVon(datei), titelAusDatei(datei))
    }
    val ablage = remember(detail.person.xref) {
        object : androidx.compose.ui.draganddrop.DragAndDropTarget {
            override fun onEntered(event: androidx.compose.ui.draganddrop.DragAndDropEvent) { ziehtDarueber = true }
            override fun onExited(event: androidx.compose.ui.draganddrop.DragAndDropEvent) { ziehtDarueber = false }
            override fun onEnded(event: androidx.compose.ui.draganddrop.DragAndDropEvent) { ziehtDarueber = false }
            override fun onDrop(event: androidx.compose.ui.draganddrop.DragAndDropEvent): Boolean {
                ziehtDarueber = false
                val liste = event.dragData() as? androidx.compose.ui.draganddrop.DragData.FilesList ?: return false
                val dateien = liste.readFiles().mapNotNull { runCatching { java.io.File(java.net.URI(it)) }.getOrNull() }.filter { it.isFile }
                hochladen(dateien)
                return dateien.isNotEmpty()
            }
        }
    }
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            (if (darfSchreiben) Modifier.dragAndDropTarget(shouldStartDragAndDrop = { true }, target = ablage) else Modifier)
                .fillMaxSize().verticalScroll(scroll).padding(12.dp)
                .border(if (ziehtDarueber) 2.dp else 0.dp, if (ziehtDarueber) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.shapes.small),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (m.isEmpty()) Text(stringResource(Res.string.desk_place_nothing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else MedienReihe(m, openWeb,
                onLoesen = if (darfSchreiben) ({ x -> viewModel.unlinkMedia(x.xref) }) else null,
                // Hauptbild festlegen: an die erste Stelle, ab API-Stufe 35 auch mit GEDCOM-L "_PRIM Y"
                onHauptbild = if (darfSchreiben) ({ x -> viewModel.primaryMedia(x.xref) }) else null,
                onBearbeiten = if (darfSchreiben) ({ x -> bearbeiten = x }) else null,
                onOeffnen = { item ->
                    when {
                        item.isImage -> viewModel.openMediaViewer(ViewerSource.Profile, m, item, owner = detail.person.name)
                        item.mime == "application/pdf" -> viewModel.openPdf(item.file, item.title, item.url)
                        else -> openWeb(item.url)
                    }
                })
            if (darfSchreiben) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { dateiOeffnen(scanDialogTitel())?.let { hochladen(listOf(it)) } }, shape = MaterialTheme.shapes.small) {
                        Text(stringResource(Res.string.desk_source_add_file))
                    }
                    OutlinedButton(onClick = { waehlen = true }, shape = MaterialTheme.shapes.small) { Text(stringResource(Res.string.desk_media_existing)) }
                }
                Text(stringResource(Res.string.desk_media_drop_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Gruppenbilder aus dem Archiv, auf denen die Person markiert ist (Sammlungen ab Stufe 5) - haengen nicht an ihr
            state.archive?.takeIf { it.api >= 5 && it.personenFilter }?.let { a ->
                state.tree?.name?.let { t -> AufGruppenbildern(detail.person.xref, t, viewModel, darfBereiche = a.darfBereiche) }
            }
        }
        SenkrechteLeiste(scroll)
    }
    if (waehlen) MedienWahlDialog(state.tree?.name.orEmpty(), viewModel.client, m.map { it.xref }.toSet(), onDismiss = { waehlen = false },
        archive = state.archive, rechteXref = detail.person.xref) { neu -> waehlen = false; viewModel.linkMedia(neu.xref) }
    bearbeiten?.let { medium ->
        MediumDialog(medium, onDismiss = { bearbeiten = null }) { titel, art -> bearbeiten = null; viewModel.editMedia(medium.xref, titel, art) }
    }
}

/** Reiter Quellen: jede Quelle mit den Ereignissen, die sie belegt. */
@Composable
private fun SourcesTab(detail: IndividualDetail, openWeb: (String) -> Unit) {
    val quellen = quellenVon(detail)
    Column(Modifier.fillMaxSize()) {
        val list = rememberLazyListState()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), state = list) {
                if (quellen.isEmpty()) item { Text(stringResource(Res.string.desk_sources_none), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(quellen) { (titel, wo) ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(titel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(wo.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            ListenLeiste(list)
        }
        TextButton(onClick = { openWeb(detail.person.url) }, modifier = Modifier.padding(4.dp)) { Text(stringResource(Res.string.desk_edit_in_web)) }
    }
}

/** Die Daten als Tabelle: Ereignis, Datum, Ort oder Beschreibung. Doppelklick bearbeitet, Knoepfe darunter. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FactTable(detail: IndividualDetail, canEdit: Boolean, viewModel: AppViewModel, onEdit: (FactRow) -> Unit, onDelete: (FactRow) -> Unit, onNew: () -> Unit) {
    val withSpouse = stringResource(Res.string.fact_with_spouse, "%s", "%s")
    // 1 ASSO an der Person steht ab API-Stufe 19 schon bei der Taufe - den eigenen Fakt dann nicht doppelt zeigen.
    val rows = detail.facts.filter { it.known }.ohneDoppelteAsso().map { FactRow(it, null, faktLabelMitArt(it)) } +
        detail.spouseFamilies.flatMap { family ->
            family.facts.filter { it.known }.map { f ->
                val label = faktLabelMitArt(f)
                FactRow(f, family.xref, family.spouse?.name?.let { withSpouse.replaceFirst("%s", label).replaceFirst("%s", it) } ?: label)
            }
        }
    Column(Modifier.fillMaxSize()) {
        EreignisTabelle(rows, detail.person.xref, canEdit, onEdit, onDelete, onNew, Modifier.weight(1f).fillMaxWidth(), geburtJd(detail), detail, viewModel)
        // Wo die Person selbst Pate oder Trauzeuge ist (ab API-Stufe 19; bei aelteren Modulen fehlt der Abschnitt)
        Patenschaften(detail, viewModel::select)
    }
}

/** Julianischer Tag der Geburt (sonst der Taufe) - fuer die Spalte Alter; 0 = unbekannt. */
private fun geburtJd(detail: IndividualDetail): Int =
    (detail.person.birth?.date?.jd?.takeIf { it > 0 } ?: detail.person.chr?.date?.jd?.takeIf { it > 0 }) ?: 0

/** Alter in vollen Jahren am Tag des Ereignisses; null ohne beide Daten, bei Geburt/Taufe selbst oder vor der Geburt. */
internal fun alterBeiEreignis(geburtJd: Int, fact: FactJson): Int? {
    val jd = fact.date?.jd ?: 0
    if (geburtJd <= 0 || jd <= 0 || fact.tag in setOf("BIRT", "CHR", "BAPM", "NAME", "SEX")) return null
    return ((jd - geburtJd) / 365.2425).toInt().takeIf { it >= 0 }
}

/** Wonach die Ereignistabelle sortiert ist; null = Reihenfolge des Servers. */
private enum class EreignisSortierung { Art, Datum, Ort, Alter }

/** Ereignisse als Tabelle mit Neu/Bearbeiten/Loeschen; [schluessel] setzt die Auswahl zurueck (andere Person, andere Familie). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EreignisTabelle(
    alleZeilen: List<FactRow>, schluessel: String, canEdit: Boolean,
    onEdit: (FactRow) -> Unit, onDelete: (FactRow) -> Unit, onNew: () -> Unit, modifier: Modifier, geburtJd: Int = 0,
    detail: IndividualDetail? = null, viewModel: AppViewModel? = null,
) {
    var selected by remember(schluessel) { mutableStateOf(-1) }
    var sortierung by remember { mutableStateOf<EreignisSortierung?>(null) }
    var absteigend by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val rows = remember(alleZeilen, sortierung, absteigend) {
        val s = sortierung ?: return@remember alleZeilen
        // Ohne Wert (kein Datum, kein Ort) immer ans Ende, in beiden Richtungen
        fun schluesselVon(r: FactRow): Comparable<*>? = when (s) {
            EreignisSortierung.Art -> r.label.lowercase()
            EreignisSortierung.Datum -> r.fact.date?.jd?.takeIf { it > 0 }
            EreignisSortierung.Ort -> r.fact.place?.name?.takeIf { it.isNotBlank() }?.lowercase()
            EreignisSortierung.Alter -> alterBeiEreignis(geburtJd, r.fact)
        }
        val (mit, ohne) = alleZeilen.partition { schluesselVon(it) != null }
        @Suppress("UNCHECKED_CAST")
        val sortiert = mit.sortedWith(compareBy { schluesselVon(it) as Comparable<Any> })
        (if (absteigend) sortiert.reversed() else sortiert) + ohne
    }
    fun kopf(s: EreignisSortierung) { if (sortierung == s) absteigend = !absteigend else { sortierung = s; absteigend = false }; selected = -1 }

    Column(modifier) {
        Row(Modifier.fillMaxWidth().background(colors.surfaceVariant).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            @Composable
            fun Kopf(text: String, s: EreignisSortierung, gewicht: Float) {
                val pfeil = if (sortierung == s) (if (absteigend) " ▼" else " ▲") else ""
                Text(text + pfeil, Modifier.weight(gewicht).clickable { kopf(s) }.padding(vertical = 5.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Kopf(stringResource(Res.string.desk_col_event), EreignisSortierung.Art, 0.26f)
            Kopf(stringResource(Res.string.desk_col_date), EreignisSortierung.Datum, 0.2f)
            Kopf(stringResource(Res.string.desk_col_place), EreignisSortierung.Ort, 0.44f)
            Kopf(stringResource(Res.string.desk_col_age), EreignisSortierung.Alter, 0.06f)
            Merker(Icons.Default.Create, stringResource(Res.string.desk_tab_notes))
            Merker(Icons.Default.Info, stringResource(Res.string.desk_tab_sources))
        }
        val list = rememberLazyListState()
        Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(Modifier.fillMaxSize(), state = list) {
            itemsIndexed(rows) { i, row ->
                val f = row.fact
                val where = listOfNotNull(f.value.takeIf { it.isNotBlank() && f.tag != "NAME" }, Ortsanzeige.text(f.place?.name).takeIf { it.isNotBlank() }).joinToString(" · ")
                    .ifEmpty { if (f.tag == "NAME") de.bgghome.webtrees.nativ.data.GedcomName.aus(f.value).anzeige() else "" }
                val zeileKopieren = stringResource(Res.string.desk_copy_row)
                val textKopieren = stringResource(Res.string.desk_copy_text)
                val metaSuche = stringResource(Res.string.desk_meta_menu)
                // Rechtsklick (Issue 14): die Zeile als Text in die Zwischenablage, oder gleich der ganze Personentext
                ContextMenuArea(items = { listOf(
                    ContextMenuItem(zeileKopieren) { selected = i; inZwischenablage(listOf(row.label, f.date?.text.orEmpty(), where, alterBeiEreignis(geburtJd, f)?.toString().orEmpty()).filter(String::isNotBlank).joinToString("\t")) },
                    ContextMenuItem(textKopieren) { detail?.let(::personentextKopieren) },
                    ContextMenuItem(metaSuche) { detail?.let(MetaSuche::fuer) },
                ) }) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (i == selected) colors.secondaryContainer else if (i % 2 == 1) colors.surfaceContainerLow else colors.surface)
                        .fokusRahmen()
                        .combinedClickable(onClick = { selected = i }, onDoubleClick = { if (canEdit) onEdit(row) })
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text(row.label, Modifier.weight(0.26f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(f.date?.text.orEmpty(), Modifier.weight(0.2f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(where, Modifier.weight(0.44f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(alterBeiEreignis(geburtJd, f)?.toString().orEmpty(), Modifier.weight(0.06f), style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant, maxLines = 1)
                    MerkerWert(f.notizenOhnePaten().isNotEmpty())
                    MerkerWert(f.sources.isNotEmpty())
                }
                }
                HorizontalDivider(color = colors.outlineVariant)
            }
        }
        ListenLeiste(list)
        }
        val detailHoehe = rememberBreite("blatt_detail_hoehe", 260f)
        TrennerWaagrecht(detailHoehe, "blatt_detail_hoehe", 90f, 600f, unten = true)
        // Der Detailbereich ist nur so hoch wie sein Inhalt (hoechstens die gezogene Hoehe) - der Rest bleibt der Tabelle
        EreignisDetail(rows.getOrNull(selected), geburtJd, Modifier.fillMaxWidth().heightIn(max = detailHoehe.value.dp), canEdit, detail, viewModel)
        if (canEdit) {
            Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(shape = MaterialTheme.shapes.small, onClick = onNew) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(Res.string.action_add_event)) }
                OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { rows.getOrNull(selected)?.let(onEdit) }, enabled = selected >= 0) { Text(stringResource(Res.string.action_edit)) }
                OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { rows.getOrNull(selected)?.let(onDelete) }, enabled = selected >= 0 && rows.getOrNull(selected)?.fact?.tag != "NAME") { Text(stringResource(Res.string.action_delete)) }
            }
        }
    }
}

/**
 * Das gewaehlte Ereignis im Detail, mit Unter-Reitern: Daten (Datum, Ort, Beschreibung, Alter), Notizen und Quellen samt
 * Seitenangabe. Nur zum Lesen - geaendert wird wie bisher per Doppelklick bzw. "Bearbeiten".
 */
@Composable
private fun EreignisDetail(row: FactRow?, geburtJd: Int, modifier: Modifier, canEdit: Boolean = false, detail: IndividualDetail? = null, viewModel: AppViewModel? = null) {
    var reiter by remember { mutableStateOf(0) }
    val colors = MaterialTheme.colorScheme
    val f = row?.fact
    // Verweise bearbeiten (Stufe 2): nur mit Bearbeitungsrecht und einem Server ab API-Stufe 18 (LocalQuelleOeffnen gesetzt)
    val schreiben = canEdit && detail != null && viewModel != null && LocalQuelleOeffnen.current != null
    val record = row?.record ?: detail?.person?.xref.orEmpty()
    var gewaehlt by remember(f?.id) { mutableStateOf(0) }
    var zitat by remember { mutableStateOf<ZitatZiel?>(null) }
    var kopieren by remember { mutableStateOf<SourceRef?>(null) }
    var entfernen by remember { mutableStateOf<ZitatZiel?>(null) }
    var patenFuer by remember { mutableStateOf<FactJson?>(null) }
    val tree = viewModel?.state?.value?.tree?.name.orEmpty()
    Column(modifier.background(colors.surface)) {
        Row(Modifier.fillMaxWidth().background(colors.surfaceVariant.copy(alpha = 0.5f))) {
            listOf(stringResource(Res.string.desk_detail_data), stringResource(Res.string.desk_tab_notes) + (f?.notizenOhnePaten()?.size?.takeIf { it > 0 }?.let { " ($it)" } ?: ""),
                stringResource(Res.string.desk_tab_sources) + (f?.sources?.size?.takeIf { it > 0 }?.let { " ($it)" } ?: "")).forEachIndexed { i, t ->
                Text(t, Modifier.clickable { reiter = i }.background(if (reiter == i) colors.surface else androidx.compose.ui.graphics.Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (reiter == i) FontWeight.SemiBold else FontWeight.Normal, color = if (reiter == i) colors.primary else colors.onSurface)
            }
        }
        val scroll = rememberScrollState()
        Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
            androidx.compose.foundation.text.selection.SelectionContainer { Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (f == null) {
                    Text(stringResource(Res.string.desk_detail_choose), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                } else when (reiter) {
                    0 -> {
                        @Composable
                        fun Zeile(label: String, wert: String) {
                            if (wert.isBlank()) return
                            Row { Text(label, Modifier.width(130.dp), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                                Text(wert, style = MaterialTheme.typography.bodyMedium) }
                        }
                        Text(row.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        val alter = alterBeiEreignis(geburtJd, f)
                        Zeile(stringResource(Res.string.desk_col_date), f.date?.text.orEmpty() + (alter?.let { "   (" + stringResource(Res.string.desk_detail_age, it) + ")" } ?: ""))
                        val ortOeffnen = de.bgghome.webtrees.nativ.ui.LocalPlaceOpener.current
                        val ort = f.place?.name.orEmpty()
                        if (ortOeffnen != null && ort.isNotBlank()) Row {
                            Text(stringResource(Res.string.fact_place), Modifier.width(130.dp), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            Text(Ortsanzeige.text(ort), Modifier.clickable { ortOeffnen(ort) }, style = MaterialTheme.typography.bodyMedium, color = colors.primary)
                        } else Zeile(stringResource(Res.string.fact_place), Ortsanzeige.text(ort))
                        Zeile(stringResource(Res.string.desk_detail_value),
                            if (f.tag == "NAME") de.bgghome.webtrees.nativ.data.GedcomName.aus(f.value).anzeige() else f.value)
                        // Adresse (ab API-Stufe 33) und GEDCOM-L-Angaben am Ort und an der Adresse (ab Stufe 34)
                        Zeile(stringResource(Res.string.desk_detail_address), f.address.orEmpty())
                        f.extras.forEach { z -> Zeile(gedcomLLabel(z.tag), gedcomLWert(z.value, z.date)) }
                        // Uebrige Angaben (ab API-Stufe 37), vom Server fertig beschriftet
                        f.details.forEach { d -> Zeile("   ".repeat(d.level) + d.label, d.value) }
                        val civil = heiratsartText(Heiratsart.Standesamtlich); val reli = heiratsartText(Heiratsart.Kirchlich)
                        val partners = heiratsartText(Heiratsart.Partnerschaft); val common = heiratsartText(Heiratsart.OhneTrauschein)
                        f.artZusatz { when (it) { Heiratsart.Standesamtlich -> civil; Heiratsart.Kirchlich -> reli; Heiratsart.Partnerschaft -> partners; Heiratsart.OhneTrauschein -> common } }
                            ?.let { Zeile(stringResource(Res.string.desk_detail_type), it) }
                        // Paten und Trauzeugen (ab API-Stufe 19): anklickbar, mit Notiz (i) und Quelle; "an der Person erfasst" nur fuer Redakteure
                        PatenZeilen(f, onPerson = { x -> viewModel?.select(x) }, zeigeLevel1 = canEdit, modifier = Modifier.padding(top = 4.dp))
                        // Paten/Trauzeugen bearbeiten (Server ab API-Stufe 20): bei Taufe und Heirat immer, sonst wenn es schon Beteiligte gibt
                        val apiStufe = viewModel?.state?.value?.info?.api ?: 0
                        if (canEdit && viewModel != null && apiStufe >= de.bgghome.webtrees.nativ.api.API_ASSOCIATES_WRITE &&
                            (f.tag in setOf("CHR", "BAPM", "MARR") || f.associates.isNotEmpty() || f.freeAssociates.isNotEmpty())) {
                            TextButton(onClick = { patenFuer = f }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
                                Text(patenKnopfText(f.tag))
                            }
                        }
                    }
                    1 -> if (f.notizenOhnePaten().isEmpty()) Text(stringResource(Res.string.desk_detail_no_notes), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        else f.notizenOhnePaten().forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    else -> if (f.sources.isEmpty()) Text(stringResource(Res.string.desk_detail_no_sources), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        else f.sources.forEachIndexed { i, q ->
                            Box(Modifier.fillMaxWidth().background(if (schreiben && i == gewaehlt) colors.secondaryContainer.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.shapes.extraSmall)
                                .clickable { gewaehlt = i }.padding(horizontal = 4.dp)) {
                                VerweisAnzeige(q, LocalQuelleOeffnen.current, LocalOpenWeb.current)
                            }
                        }
                }
            } }
            SenkrechteLeiste(scroll)
        }
        if (reiter == 2 && schreiben && f != null) {
            val q = f.sources.getOrNull(gewaehlt)
            val ziel = q?.let { ZitatZiel(record, f.id, gewaehlt, it) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { zitat = ZitatZiel(record, f.id, null, null) }) { Text("+ " + stringResource(Res.string.desk_cite_add)) }
                TextButton(onClick = { zitat = ziel }, enabled = ziel != null) { Text(stringResource(Res.string.action_edit)) }
                TextButton(onClick = { entfernen = ziel }, enabled = ziel != null) { Text(stringResource(Res.string.desk_cite_remove)) }
                TextButton(onClick = { ziel?.let { viewModel!!.saveCitations(listOf(record to zitatVerschieben(it, gewaehlt - 1))) { gewaehlt-- } } }, enabled = ziel != null && gewaehlt > 0) { Text("▲") }
                TextButton(onClick = { ziel?.let { viewModel!!.saveCitations(listOf(record to zitatVerschieben(it, gewaehlt + 1))) { gewaehlt++ } } }, enabled = ziel != null && gewaehlt < f.sources.lastIndex) { Text("▼") }
                TextButton(onClick = { kopieren = q }, enabled = q != null) { Text(stringResource(Res.string.desk_cite_copy)) }
            }
        }
    }
    zitat?.let { z -> ZitatDialog(z, tree, viewModel!!, onDismiss = { zitat = null }) }
    patenFuer?.let { pf -> PatenDialog(pf, record, row?.label ?: pf.label, tree, viewModel!!, onDismiss = { patenFuer = null }) }
    kopieren?.let { q -> ZitatKopierenDialog(q, detail!!, f?.id.orEmpty(), viewModel!!, onDismiss = { kopieren = null }) }
    entfernen?.let { z ->
        ConfirmDialog(
            title = stringResource(Res.string.desk_cite_remove), text = stringResource(Res.string.desk_cite_remove_text, z.alt?.title.orEmpty()),
            confirm = stringResource(Res.string.desk_cite_remove), onDismiss = { entfernen = null },
            onConfirm = { entfernen = null; viewModel!!.saveCitations(listOf(record to zitatLoeschen(z))) { gewaehlt = 0 } },
        )
    }
}

/** Aus dem Personenblatt eine Quelle in der Quellenverwaltung oeffnen (null: Server kennt keine Quellen-Routen). */
val LocalQuelleOeffnen = androidx.compose.runtime.staticCompositionLocalOf<((String) -> Unit)?> { null }
val LocalOpenWeb = androidx.compose.runtime.staticCompositionLocalOf<(String) -> Unit> { {} }

/** Kopf einer schmalen Merkerspalte: nur das Symbol, der Name beim Ueberfahren. */
@Composable
private fun Merker(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String) {
    Tipp(name) { Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = name, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@Composable
private fun MerkerWert(da: Boolean) {
    Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { if (da) Text("✓", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
}

/** Rechts: Vater, Mutter, Geschwister, Partner, Kinder - jede Gruppe mit einer farbigen Kopfzeile. */
@Composable
private fun RelativesColumn(detail: IndividualDetail, viewModel: AppViewModel, modifier: Modifier) {
    val self = detail.person.xref
    val parents = detail.parentFamilies.firstOrNull()
    val siblings = detail.parentFamilies.flatMap { it.children }.filter { it.xref != self }.distinctBy { it.xref }
    val children = detail.spouseFamilies.flatMap { it.children }
    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        val list = rememberLazyListState()
        Box(Modifier.weight(1f)) {
        LazyColumn(Modifier.fillMaxSize(), state = list) {
            item { Group(stringResource(Res.string.rel_father), listOfNotNull(parents?.husband), viewModel) }
            item { Group(stringResource(Res.string.rel_mother), listOfNotNull(parents?.wife), viewModel) }
            item { Group(stringResource(Res.string.desk_siblings), siblings, viewModel) }
            // Nur wenn es welche gibt - ein weiterer Strich "–" waere bei den meisten Personen nur Rauschen.
            val half = detail.halfSiblings().map { it.person }
            if (half.isNotEmpty()) item { Group(stringResource(Res.string.rel_half_siblings), half, viewModel) }
            item {
                // Alle Partnerschaften in ihrer Reihenfolge, auch ohne eingetragene Partnerin (wie im Infokasten).
                Group(stringResource(Res.string.rel_partner), emptyList(), viewModel, leerStrich = detail.spouseFamilies.isEmpty())
                val unbekannt = unbekannterPartner(detail.person.sex)
                detail.spouseFamilies.forEach { fam ->
                    val sp = fam.spouse
                    if (sp != null) VerwandtenZeile(sp, viewModel)
                    else Text(unbekannt, Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
            }
            item { Group(stringResource(Res.string.desk_children), children, viewModel) }
        }
        ListenLeiste(list)
        }
        if (detail.canEdit) {
            TextButton(onClick = { viewModel.requestAddRelative(self) }, modifier = Modifier.padding(4.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(Res.string.action_add_relative))
            }
        }
    }
}

@Composable
private fun Group(title: String, people: List<Person>, viewModel: AppViewModel, leerStrich: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Text(title, Modifier.fillMaxWidth().background(colors.primary).padding(horizontal = 8.dp, vertical = 3.dp), color = colors.onPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    people.forEach { p -> VerwandtenZeile(p, viewModel) }
    if (people.isEmpty() && leerStrich) Text("–", Modifier.padding(horizontal = 10.dp, vertical = 3.dp), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun VerwandtenZeile(p: Person, viewModel: AppViewModel) {
    Text(
        registerName(p, stringResource(Res.string.person_private), stringResource(Res.string.person_no_name)) + jahre(p).let { if (it.isNotEmpty()) "  $it" else "" },
        Modifier.fillMaxWidth().clickable(enabled = !p.isPrivate) { viewModel.select(p.xref) }.padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SheetFooter(
    state: UiState, detail: IndividualDetail, viewModel: AppViewModel,
    canPrev: Boolean, canNext: Boolean, onStep: (Int) -> Unit, onFirst: () -> Unit, onLast: () -> Unit, onClose: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (detail.canEdit) TextButton(onClick = { confirmDelete = true }) { Text(stringResource(Res.string.action_delete_person)) }
        val appName = LocalAppName.current
        val baum = state.tree?.title.orEmpty()
        val titel = stringResource(Res.string.desk_title_sheet, detail.person.name)
        TextButton(onClick = { drucken(listenPdf(personenblattZeilen(detail), appName, baum), titel) }) { Text(stringResource(Res.string.desk_print)) }
        TextButton(onClick = { alsPdf(listenPdf(personenblattZeilen(detail), appName, baum), titel) }) { Text("PDF") }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onFirst, enabled = canPrev) { Text("⏮", fontSize = 16.sp) }
        IconButton(onClick = { onStep(-1) }, enabled = canPrev) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
        IconButton(onClick = { onStep(1) }, enabled = canNext) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) }
        IconButton(onClick = onLast, enabled = canNext) { Text("⏭", fontSize = 16.sp) }
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
