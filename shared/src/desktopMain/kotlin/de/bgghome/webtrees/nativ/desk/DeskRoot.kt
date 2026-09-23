package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.filled.Clear
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.ui.input.key.key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.*
import de.bgghome.webtrees.nativ.ui.tree.FamilyTreeView
import de.bgghome.webtrees.nativ.ui.tree.TreeLayout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jetbrains.compose.resources.stringResource

/*
 * Aufbau "Baum im Mittelpunkt" (Etappe 3, 23.09.2026): Menueleiste, Arbeitsbereiche als Knoepfe, links die
 * Personenliste, in der Mitte der Baum, rechts die Personentafel, unten die Statuszeile. Dieselben Bausteine und
 * dasselbe ViewModel wie am Handy - nur anders angeordnet und dichter. Weitere Aufbauten (Liste und Personenblatt)
 * sollen aus denselben Bausteinen entstehen.
 */

@Composable
fun FrameWindowScope.DeskRoot(viewModel: AppViewModel, onQuit: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val appName = LocalAppName.current
    val openWeb: (String) -> Unit = { openBrowser(it) }
    val search = remember { FocusRequester() }
    var about by remember { mutableStateOf(false) }
    var layout by remember { mutableStateOf(DeskLayout.load()) }
    var sheetOpen by remember { mutableStateOf(false) }
    var goTo by remember { mutableStateOf(false) }
    val openSheet: (String) -> Unit = { xref -> viewModel.select(xref); sheetOpen = true }

    DeskMenuBar(
        state, viewModel, openWeb, layout = layout, onLayout = { layout = it; DeskLayout.save(it) },
        onSearch = { if (layout == DeskLayout.Navigator) goTo = true else runCatching { search.requestFocus() } },
        onSheet = { (state.detail?.person?.xref ?: state.root)?.let(openSheet) },
        onAbout = { about = true }, onQuit = onQuit,
    )

    // Vor der Anmeldung und bei der Baumwahl: die Startbildschirme der App, mittig im Fenster.
    if (state.screen != Screen.Main) {
        AppRoot(viewModel)
        return
    }

    LaunchedEffect(Unit) { viewModel.setWide(true) }

    BackHandler(enabled = state.viewer != null || state.pdf != null) {
        if (state.viewer != null) viewModel.closeViewer() else viewModel.closePdf()
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            if (layout == DeskLayout.Navigator) {
                ClassicToolbar(state, viewModel, openWeb, onGoTo = { goTo = true }, onSheet = { (state.root)?.let(openSheet) }, onAbout = { about = true })
            } else {
                WorkspaceBar(state, viewModel)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.fillMaxWidth().height(3.dp)) {
                if (state.busy || state.loadingDetail || state.loadingPeople) LinearProgressIndicator(Modifier.fillMaxSize())
            }
            if (layout == DeskLayout.Navigator) Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.section) {
                    Section.Home -> HomeSection(state, viewModel, openWeb)
                    Section.Photos -> PhotosSection(state, viewModel, openWeb)
                    else -> Navigator(state, viewModel, openSheet, openWeb)
                }
            } else Row(Modifier.weight(1f).fillMaxWidth()) {
                PersonIndex(state, viewModel, openWeb, search, Modifier.width(280.dp).fillMaxHeight())
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (state.section) {
                        Section.Home -> HomeSection(state, viewModel, openWeb)
                        Section.Photos -> PhotosSection(state, viewModel, openWeb)
                        else -> DeskTree(state, viewModel, openWeb)
                    }
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.width(400.dp).fillMaxHeight()) {
                    val detail = state.detail
                    if (detail != null) {
                        ProfilePanel(state, detail, viewModel, openWeb, onClose = null)
                    } else {
                        Text(
                            stringResource(Res.string.detail_choose), Modifier.align(Alignment.Center).padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StatusBar(state, viewModel.versionName, appName)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp))

        // Betrachter liegen ueber allem, wie am Handy.
        state.pdf?.let { pdf -> PdfViewer(pdf, onClose = viewModel::closePdf, onOpenWeb = openWeb) }
        state.viewer?.let { viewer ->
            PhotoViewer(
                viewer, onIndex = viewModel::viewerMoved, onClose = viewModel::closeViewer, onOpenWeb = openWeb,
                canEdit = viewModel::canEditExif, editing = state.exifEditing, suggestPersons = viewModel.personSuggestions(),
                onEdit = viewModel::editExif, onCancelEdit = viewModel::cancelExif, onSaveExif = viewModel::writeExif,
            )
        }
    }

    // Verwandte hinzufuegen - aus der Tafel, vom Kontextmenue oder von der "+"-Lasche einer Karte.
    val detail = state.detail
    if (state.addRelativeFor != null && detail != null && detail.person.xref == state.addRelativeFor && !state.loadingDetail) {
        RelativeDialog(
            target = RelativeTarget.of(detail),
            suggestPlaces = viewModel.placeSuggestions(),
            onDismiss = viewModel::addRelativeHandled,
            onSave = { viewModel.addRelativeHandled(); viewModel.addRelative(it) },
        )
    }

    if (sheetOpen && state.detail != null) PersonSheet(state, viewModel, openWeb, onClose = { sheetOpen = false })
    if (goTo) GoToDialog(state, viewModel, openWeb, onClose = { goTo = false })

    if (about) {
        AlertDialog(
            onDismissRequest = { about = false },
            title = { Text(stringResource(Res.string.desk_about, appName)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.desk_about_text, appName, viewModel.versionName))
                    Text(stringResource(Res.string.app_author), style = MaterialTheme.typography.bodySmall)
                    state.info?.module?.takeIf { it.isNotEmpty() }?.let {
                        Text(stringResource(Res.string.menu_about_module, "api4webtrees $it"), style = MaterialTheme.typography.bodySmall)
                    }
                    state.archive?.modul?.takeIf { it.isNotEmpty() }?.let {
                        Text(stringResource(Res.string.menu_about_module, "Sammlungen $it"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { about = false }) { Text(stringResource(Res.string.action_close)) } },
        )
    }
}

// ── Menueleiste ──────────────────────────────────────────────────────

@Composable
private fun FrameWindowScope.DeskMenuBar(
    state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit,
    layout: DeskLayout, onLayout: (DeskLayout) -> Unit,
    onSearch: () -> Unit, onSheet: () -> Unit, onAbout: () -> Unit, onQuit: () -> Unit,
) {
    val main = state.screen == Screen.Main
    val loggedIn = state.info?.user?.loggedIn == true
    val selected = state.detail
    MenuBar {
        Menu(stringResource(Res.string.desk_menu_file)) {
            if (main && (state.info?.trees?.size ?: 0) > 1) Item(stringResource(Res.string.menu_switch_tree), onClick = viewModel::showTreePicker)
            Item(stringResource(Res.string.action_reload), enabled = main, shortcut = KeyShortcut(Key.F5), onClick = viewModel::refresh)
            Item(stringResource(Res.string.desk_open_browser), enabled = state.baseUrl.isNotEmpty(), onClick = { openWeb(state.detail?.person?.url ?: state.baseUrl) })
            Separator()
            if (loggedIn) {
                Item(stringResource(Res.string.menu_sign_out_user, state.info?.user?.userName.orEmpty()), onClick = viewModel::logout)
            } else if (main) {
                Item(stringResource(Res.string.action_sign_in), onClick = viewModel::showLogin)
            }
            Item(stringResource(Res.string.desk_quit), shortcut = KeyShortcut(Key.Q, ctrl = true), onClick = onQuit)
        }
        Menu(stringResource(Res.string.desk_menu_person), enabled = main) {
            Item(stringResource(Res.string.desk_goto), shortcut = KeyShortcut(Key.F, ctrl = true), onClick = onSearch)
            Item(stringResource(Res.string.desk_sheet), enabled = state.root != null, shortcut = KeyShortcut(Key.E, ctrl = true), onClick = onSheet)
            state.home?.let { home -> Item(stringResource(Res.string.home_start_person), shortcut = KeyShortcut(Key.MoveHome, alt = true), onClick = { viewModel.setRoot(home) }) }
            Item(stringResource(Res.string.action_make_root), enabled = selected != null && selected.person.xref != state.root,
                shortcut = KeyShortcut(Key.Enter, ctrl = true), onClick = { selected?.let { viewModel.setRoot(it.person.xref) } })
            Item(stringResource(Res.string.action_add_relative), enabled = selected?.canEdit == true,
                shortcut = KeyShortcut(Key.N, ctrl = true), onClick = { selected?.let { viewModel.requestAddRelative(it.person.xref) } })
            Item(stringResource(Res.string.chip_open_web), enabled = selected != null, onClick = { selected?.let { openWeb(it.person.url) } })
            Separator()
            Item(stringResource(Res.string.action_back), enabled = viewModel.canGoBack(), shortcut = KeyShortcut(Key.DirectionLeft, alt = true), onClick = { viewModel.back() })
        }
        Menu(stringResource(Res.string.desk_menu_view), enabled = main) {
            Menu(stringResource(Res.string.desk_layout)) {
                RadioButtonItem(stringResource(Res.string.desk_layout_navigator), selected = layout == DeskLayout.Navigator, onClick = { onLayout(DeskLayout.Navigator) })
                RadioButtonItem(stringResource(Res.string.desk_layout_tree), selected = layout == DeskLayout.TreeCentre, onClick = { onLayout(DeskLayout.TreeCentre) })
            }
            Separator()
            Item(stringResource(Res.string.nav_home), shortcut = KeyShortcut(Key.One, ctrl = true), onClick = { viewModel.setSection(Section.Home) })
            Item(stringResource(Res.string.nav_tree), shortcut = KeyShortcut(Key.Two, ctrl = true), onClick = { viewModel.setSection(Section.Tree) })
            Item(stringResource(Res.string.nav_photos), shortcut = KeyShortcut(Key.Three, ctrl = true), onClick = { viewModel.setSection(Section.Photos) })
            Separator()
            Menu(stringResource(Res.string.tree_generations, state.ancestorGenerations)) {
                (2..6).forEach { n ->
                    CheckboxItem(stringResource(Res.string.tree_generations, n), checked = state.ancestorGenerations == n, onCheckedChange = { viewModel.setAncestorGenerations(n) })
                }
            }
            CheckboxItem(stringResource(Res.string.tree_show_siblings), checked = state.showSiblings, onCheckedChange = viewModel::setShowSiblings)
            CheckboxItem(stringResource(Res.string.tree_show_cousins), checked = state.showCousins && state.showSiblings, enabled = state.showSiblings, onCheckedChange = viewModel::setShowCousins)
        }
        Menu(stringResource(Res.string.desk_menu_help)) {
            Item(stringResource(Res.string.desk_about, LocalAppName.current), onClick = onAbout)
        }
    }
}

// ── Aufbau ───────────────────────────────────────────────────────────

/** Die waehlbaren Aufbauten des Hauptfensters; die Wahl bleibt in den Desktop-Einstellungen. */
enum class DeskLayout {
    Navigator, TreeCentre;

    companion object {
        private val prefs = de.bgghome.webtrees.nativ.data.DesktopAblage("desk")
        fun load(): DeskLayout = entries.firstOrNull { it.name == prefs.getString("layout", null) } ?: Navigator
        fun save(layout: DeskLayout) = prefs.putString("layout", layout.name)
    }
}

// ── Klassische Symbolleiste (Aufbau Navigator) ───────────────────────

@Composable
private fun ClassicToolbar(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit, onGoTo: () -> Unit, onSheet: () -> Unit, onAbout: () -> Unit) {
    val canEdit = state.tree?.canEdit == true
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            TreePicker(state, viewModel)
            ToolSeparator()
            ToolItem(Icons.Default.Search, stringResource(Res.string.desk_goto), onClick = onGoTo)
            ToolItem(Icons.Default.Edit, stringResource(Res.string.action_edit), enabled = state.root != null, onClick = onSheet)
            ToolItem(Icons.Default.Add, stringResource(Res.string.action_add_relative), enabled = canEdit && state.root != null) { state.root?.let(viewModel::requestAddRelative) }
            ToolSeparator()
            ToolItem(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back), enabled = viewModel.canGoBack()) { viewModel.back() }
            ToolItem(Icons.Default.Person, stringResource(Res.string.home_start_person), enabled = state.home != null) { state.home?.let(viewModel::setRoot) }
            ToolItem(Icons.Default.Refresh, stringResource(Res.string.action_reload), onClick = viewModel::refresh)
            ToolSeparator()
            ToolItem(Icons.Default.Home, stringResource(Res.string.nav_home), active = state.section == Section.Home) { viewModel.setSection(Section.Home) }
            ToolItem(TreeIcon, stringResource(Res.string.desk_layout_navigator), active = state.section == Section.Tree || state.section == Section.Search) { viewModel.setSection(Section.Tree) }
            ToolItem(PhotoIcon, stringResource(Res.string.nav_photos), active = state.section == Section.Photos) { viewModel.setSection(Section.Photos) }
            ToolSeparator()
            ToolItem(Icons.AutoMirrored.Filled.ExitToApp, "webtrees") { openWeb(state.detail?.person?.url ?: state.baseUrl) }
            ToolItem(Icons.Default.Info, stringResource(Res.string.desk_menu_help), onClick = onAbout)
            Spacer(Modifier.weight(1f))
            if (state.section == Section.Tree || state.section == Section.Search) GenerationsChip(state, viewModel)
        }
    }
}

@Composable
private fun ToolItem(icon: ImageVector, label: String, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint = if (!enabled) colors.onSurfaceVariant.copy(alpha = 0.4f) else if (active) colors.primary else colors.onSurface
    Column(
        Modifier
            .background(if (active) colors.surface else colors.surfaceVariant, MaterialTheme.shapes.extraSmall)
            .fokusRahmen()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, Modifier.size(22.dp), tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ToolSeparator() {
    VerticalDivider(Modifier.height(36.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outline)
}

/** "Gehe zu": die Personenliste in einem kleinen Fenster; eine Wahl macht die Person zur Zentralperson. */
@Composable
private fun GoToDialog(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    val startRoot = remember { state.root }
    LaunchedEffect(state.root) { if (state.root != startRoot) onClose() }
    androidx.compose.ui.window.DialogWindow(
        onCloseRequest = onClose, title = stringResource(Res.string.desk_goto),
        state = androidx.compose.ui.window.rememberDialogState(width = 380.dp, height = 560.dp),
        onPreviewKeyEvent = { e -> if (e.key == Key.Escape) { onClose(); true } else false },
    ) {
        DeskTheme {
            PersonIndex(state, viewModel, openWeb, focus, Modifier.fillMaxSize())
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        }
    }
}

// ── Arbeitsbereiche ──────────────────────────────────────────────────

@Composable
private fun WorkspaceBar(state: UiState, viewModel: AppViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TreePicker(state, viewModel)
            Spacer(Modifier.width(16.dp))
            Workspace(Icons.Default.Home, stringResource(Res.string.nav_home), state.section == Section.Home) { viewModel.setSection(Section.Home) }
            Workspace(TreeIcon, stringResource(Res.string.nav_tree), state.section == Section.Tree || state.section == Section.Search) { viewModel.setSection(Section.Tree) }
            Workspace(PhotoIcon, stringResource(Res.string.nav_photos), state.section == Section.Photos) { viewModel.setSection(Section.Photos) }
            Spacer(Modifier.weight(1f))
            if (state.section == Section.Tree || state.section == Section.Search) {
                GenerationsChip(state, viewModel)
                TreeSettings(state, viewModel)
            }
        }
    }
}

@Composable
private fun Workspace(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 2.dp)
            .background(if (active) colors.secondaryContainer else colors.surface, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, Modifier.size(20.dp), tint = if (active) colors.onSecondaryContainer else colors.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) colors.onSecondaryContainer else colors.onSurface)
    }
}

/** Name des Baums; mit mehreren Baeumen eine Aufklappliste zum Wechseln. */
@Composable
private fun TreePicker(state: UiState, viewModel: AppViewModel) {
    val trees = state.info?.trees.orEmpty()
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clickable(enabled = trees.size > 1) { open = true }.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(state.tree?.title.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if ((state.tree?.individuals ?: 0) > 0) {
                    Text(stringResource(Res.string.tree_people_count, state.tree!!.individuals), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (trees.size > 1) Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(Res.string.menu_switch_tree))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            trees.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(candidate.title, fontWeight = if (candidate.name == state.tree?.name) FontWeight.SemiBold else FontWeight.Normal) },
                    onClick = { open = false; if (candidate.name != state.tree?.name) viewModel.chooseTree(candidate) },
                )
            }
        }
    }
}

// ── Personenliste links ──────────────────────────────────────────────

@Composable
private fun PersonIndex(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit, focus: FocusRequester, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            SearchField(state.query, viewModel::search, focus)
            if (state.people.isEmpty() && !state.loadingPeople) {
                Text(
                    stringResource(if (state.query.isEmpty()) Res.string.list_empty else Res.string.list_nothing_found),
                    Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
            }
            val list = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = list) {
                itemsIndexed(state.people, key = { _, p -> p.xref }) { index, person ->
                    if (index >= state.people.size - 10) LaunchedEffect(state.nextPage) { viewModel.loadMore() }
                    IndexRow(person, selected = person.xref == state.selected, root = person.xref == state.root, viewModel, openWeb)
                }
                if (state.loadingPeople) {
                    item { Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) } }
                }
            }
            ListenLeiste(list)
            }
        }
    }
}

/** Suchfeld in Zeilenhoehe, wie in Arbeitsprogrammen - kein 56-dp-Feld vom Handy. */
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, focus: FocusRequester) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(8.dp)
            .border(1.dp, colors.outline, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(stringResource(Res.string.tree_find), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            BasicTextField(
                value = query, onValueChange = onChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (query.isNotEmpty()) {
            Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.search_clear), Modifier.size(16.dp).clickable { onChange("") }, tint = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun IndexRow(person: Person, selected: Boolean, root: Boolean, viewModel: AppViewModel, openWeb: (String) -> Unit) {
    val name = if (person.isPrivate) stringResource(Res.string.person_private) else person.name.ifEmpty { stringResource(Res.string.person_no_name) }
    val makeRoot = stringResource(Res.string.action_make_root)
    val profile = stringResource(Res.string.action_profile)
    val web = stringResource(Res.string.chip_open_web)
    ContextMenuArea(items = {
        if (person.isPrivate) emptyList() else listOf(
            ContextMenuItem(makeRoot) { viewModel.setRoot(person.xref) },
            ContextMenuItem(profile) { viewModel.select(person.xref) },
            ContextMenuItem(web) { openWeb(person.url) },
        )
    }) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                .fokusRahmen()
                .clickable(enabled = !person.isPrivate) { viewModel.setRoot(person.xref) }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(person, 28.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (root) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (person.lifespan.isNotBlank()) {
                    Text(person.lifespan, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

// ── Baum in der Mitte ────────────────────────────────────────────────

@Composable
private fun DeskTree(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit) {
    val pedigree = state.pedigree
    val descendants = state.descendants
    val canEdit = state.tree?.canEdit == true

    LaunchedEffect(state.root, pedigree == null || descendants == null) {
        if (state.root != null && (pedigree == null || descendants == null)) viewModel.loadChart()
    }
    val siblings = if (state.showSiblings) state.siblings.orEmpty() else emptyMap()
    val cousins = state.showSiblings && state.showCousins
    val layout = remember(pedigree, descendants, canEdit, siblings, cousins) {
        if (pedigree != null && descendants != null) TreeLayout.build(pedigree, descendants.tree, canEdit, siblings, cousins) else null
    }

    // Kontextmenue einer Karte: Person und Stelle des Rechtsklicks
    var menu by remember { mutableStateOf<Pair<Person, Offset>?>(null) }
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        FamilyTreeView(
            layout = layout,
            selected = state.selected,
            fullscreen = false,
            initialScale = 1f,
            compact = false,
            onToggleFullscreen = {},
            onPerson = { viewModel.select(it.xref) },
            onPlus = { viewModel.requestAddRelative(it.xref) },
            onExpand = viewModel::expandAncestors,
            onSecondary = { person, at -> viewModel.select(person.xref); menu = person to at },
            onOpen = { viewModel.setRoot(it.xref) },
            showFullscreen = false,
        )
        menu?.let { (person, at) ->
            val offset = with(density) { DpOffset(at.x.toDp(), at.y.toDp()) }
            Box(Modifier.offset(offset.x, offset.y)) {
                DropdownMenu(expanded = true, onDismissRequest = { menu = null }) {
                    DropdownMenuItem(text = { Text(stringResource(Res.string.action_make_root)) }, onClick = { menu = null; viewModel.setRoot(person.xref) })
                    if (canEdit) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.action_add_relative)) }, onClick = { menu = null; viewModel.requestAddRelative(person.xref) })
                    }
                    DropdownMenuItem(text = { Text(stringResource(Res.string.chip_open_web)) }, onClick = { menu = null; openWeb(person.url) })
                }
            }
        }
    }
}

// ── Statuszeile ──────────────────────────────────────────────────────

@Composable
private fun StatusBar(state: UiState, version: String, appName: String) {
    val host = state.baseUrl.toHttpUrlOrNull()?.let { url -> url.host + (if (url.port != 80 && url.port != 443) ":${url.port}" else "") } ?: state.baseUrl
    val user = state.info?.user
    val who = if (user?.loggedIn == true) stringResource(Res.string.trees_signed_in_as, user.userName) else stringResource(Res.string.desk_guest)
    val parts = listOfNotNull(host.takeIf { it.isNotEmpty() }, state.tree?.title, who, state.tree?.role?.let { roleLabel(it) })
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(parts.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
            Text("$appName $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
