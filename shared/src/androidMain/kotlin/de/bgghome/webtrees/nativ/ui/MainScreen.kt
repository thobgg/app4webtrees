package de.bgghome.webtrees.nativ.ui

import org.jetbrains.compose.resources.StringResource
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.shared.BuildConfig
import de.bgghome.webtrees.nativ.res.*

/** Ab dieser Breite: seitliche Leiste und Profil dauerhaft neben dem Baum (Tablet, aufgeklapptes Foldable). */
private val WIDE_MIN_WIDTH = 840.dp

private data class NavItem(val section: Section, val label: StringResource, val icon: ImageVector)

// Baum und Fotos haben eigene Zeichen (Icons.kt), der Rest kommt aus dem Material-Grundsatz.
private val NAV = listOf(
    NavItem(Section.Home, Res.string.nav_home, Icons.Default.Home),
    NavItem(Section.Tree, Res.string.nav_tree, TreeIcon),
    NavItem(Section.Search, Res.string.nav_search, Icons.Default.Search),
    NavItem(Section.Photos, Res.string.nav_photos, PhotoIcon),
)

@Composable
fun AppRoot(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    var webUrl by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = webUrl != null || viewModel.canGoBack()) {
        if (webUrl != null) webUrl = null else viewModel.back()
    }

    webUrl?.let { url ->
        WebFallbackScreen(url = url, onClose = { webUrl = null; viewModel.refresh() })
        return
    }

    // Die Betrachter liegen ueber allem: Vollbild, eigene Zurueck-Behandlung (BackHandler oben). Meldungen
    // ("Beschriftung geschrieben", Fehler) brauchen hier eine eigene Snackbar - die des Hauptbildschirms ist verdeckt.
    state.pdf?.let { pdf ->
        WithMessages(state, viewModel) { PdfViewer(pdf, onClose = viewModel::closePdf, onOpenWeb = { webUrl = it }) }
        return
    }
    state.viewer?.let { viewer ->
        WithMessages(state, viewModel) {
            PhotoViewer(
                viewer, onIndex = viewModel::viewerMoved, onClose = viewModel::closeViewer, onOpenWeb = { webUrl = it },
                canEdit = viewModel::canEditExif, editing = state.exifEditing, suggestPersons = viewModel.personSuggestions(),
                onEdit = viewModel::editExif, onCancelEdit = viewModel::cancelExif, onSaveExif = viewModel::writeExif,
            )
        }
        return
    }

    // Kopplungs-Link: erst bestaetigen lassen - er kann von jeder Webseite kommen.
    state.pendingConnect?.let { request ->
        ConfirmDialog(
            title = stringResource(Res.string.connect_confirm_title),
            text = stringResource(
                Res.string.connect_confirm_text,
                request.url,
                request.user.ifEmpty { "–" },
                request.tree.ifEmpty { "–" },
            ),
            confirm = stringResource(Res.string.connect_confirm_action),
            onDismiss = viewModel::cancelConnect,
            onConfirm = viewModel::confirmConnect,
        )
    }

    when (state.screen) {
        Screen.Loading -> LoadingScreen()
        Screen.Setup -> SetupScreen(state, viewModel::submitUrl)
        Screen.Login -> LoginScreen(state, viewModel::login, viewModel::continueAsGuest, viewModel::changeServer)
        Screen.Trees -> TreesScreen(state, viewModel::chooseTree, viewModel::logout, viewModel::showLogin, onCancel = if (state.tree != null) viewModel::cancelTreePicker else null)
        Screen.Main -> MainScreen(state, viewModel, openWeb = { webUrl = it })
    }
}

/** Inhalt mit eigener Snackbar fuer die einmaligen Meldungen aus dem View-Model. */
@Composable
private fun WithMessages(state: UiState, viewModel: AppViewModel, content: @Composable () -> Unit) {
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        content()
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}

@Composable
private fun MainScreen(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit) {
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_MIN_WIDTH
        LaunchedEffect(wide) { viewModel.setWide(wide) }

        // Vollbild-Baum und (am Handy) die Profilseite bekommen den ganzen Bildschirm.
        val chrome = !state.treeFullscreen && !(state.profileOpen && !wide)

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!wide && chrome) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        NAV.forEach { item ->
                            NavigationBarItem(
                                selected = state.section == item.section, onClick = { viewModel.setSection(item.section) },
                                icon = { Icon(item.icon, contentDescription = null) }, label = { Text(stringResource(item.label)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.padding(padding).fillMaxSize()) {
                if (wide && chrome) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        NAV.forEach { item ->
                            NavigationRailItem(
                                selected = state.section == item.section, onClick = { viewModel.setSection(item.section) },
                                icon = { Icon(item.icon, contentDescription = null) }, label = { Text(stringResource(item.label)) },
                            )
                        }
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                Column(Modifier.weight(1f).fillMaxSize()) {
                    if (state.busy && state.section != Section.Tree) LinearProgressIndicator(Modifier.fillMaxWidth())

                    when (state.section) {
                        Section.Home -> HomeSection(state, viewModel, openWeb)
                        Section.Tree -> TreeSection(state, viewModel, wide, openWeb)
                        Section.Search -> SearchSection(state, viewModel, openWeb)
                        Section.Photos -> PhotosSection(state, viewModel, openWeb)
                    }
                }
            }
        }
    }

    // Verwandte hinzufuegen - aus dem Profil oder von der "+"-Lasche einer Karte.
    // Die Dialoge haengen hier oben, weil am Handy das Profil gar nicht offen sein muss.
    val detail = state.detail
    if (state.addRelativeFor != null && detail != null && detail.person.xref == state.addRelativeFor && !state.loadingDetail) {
        RelativeDialog(
            target = RelativeTarget.of(detail),
            suggestPlaces = viewModel.placeSuggestions(),
            onDismiss = viewModel::addRelativeHandled,
            onSave = { viewModel.addRelativeHandled(); viewModel.addRelative(it) },
        )
    }
}

/** Das Drei-Punkte-Menue, in allen Bereichen gleich. */
@Composable
fun MainMenu(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }

    // Benachrichtigungen brauchen ab Android 13 eine Erlaubnis - sie wird erst beim Einschalten erfragt.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setReminders(true)
    }

    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.action_menu)) }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.action_reload)) }, onClick = { open = false; viewModel.refresh() })
            if (viewModel.anniversariesSupported) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (state.reminders) Res.string.menu_reminders_off else Res.string.menu_reminders_on)) },
                    onClick = {
                        open = false
                        when {
                            state.reminders -> viewModel.setReminders(false)
                            Build.VERSION.SDK_INT >= 33 -> askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> viewModel.setReminders(true)
                        }
                    },
                )
            }
            if ((state.info?.trees?.size ?: 0) > 1) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.menu_switch_tree)) }, onClick = { open = false; viewModel.showTreePicker() })
            }
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.menu_open_web)) },
                onClick = { open = false; openWeb(state.detail?.person?.url ?: state.baseUrl) },
            )
            if (state.info?.user?.loggedIn == true) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.menu_sign_out_user, state.info.user.userName)) },
                    onClick = { open = false; viewModel.logout() },
                )
            } else {
                DropdownMenuItem(text = { Text(stringResource(Res.string.action_sign_in)) }, onClick = { open = false; viewModel.showLogin() })
            }

            // Wer und welche Fassung - hilft bei Rueckfragen ("welche Version hast du?"). Nur Anzeige, kein Knopf.
            HorizontalDivider()
            DropdownMenuItem(
                enabled = false,
                onClick = {},
                text = {
                    Column {
                        Text(
                            stringResource(Res.string.menu_about, stringResource(Res.string.app_name), BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(stringResource(Res.string.app_author), style = MaterialTheme.typography.labelSmall)
                        // Beide Server-Module, jedes mit Namen: api4webtrees immer, Sammlungen nur, wo das Archiv antwortet.
                        state.info?.module?.takeIf { it.isNotEmpty() }?.let { module ->
                            Text(stringResource(Res.string.menu_about_module, "api4webtrees $module"), style = MaterialTheme.typography.labelSmall)
                        }
                        state.archive?.modul?.takeIf { it.isNotEmpty() }?.let { module ->
                            Text(stringResource(Res.string.menu_about_module, "Sammlungen $module"), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
            )
        }
    }
}
