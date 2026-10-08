@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.ui.StartpersonDialog
import de.bgghome.webtrees.nativ.ui.startPersonSupported
import de.bgghome.webtrees.nativ.ui.toggleTreeBookmark
import de.bgghome.webtrees.nativ.ui.isTreeBookmarked
import de.bgghome.webtrees.nativ.ui.tasksSupported
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import de.bgghome.webtrees.nativ.Sprache
import de.bgghome.webtrees.nativ.Texte
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.*
import de.bgghome.webtrees.nativ.ui.tree.FamilyTreeView
import de.bgghome.webtrees.nativ.ui.tree.TreeLayout
import de.bgghome.webtrees.nativ.lokal.LokalBetrieb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jetbrains.compose.resources.stringResource

/*
 * Aufbau "Baum im Mittelpunkt": Menueleiste, Arbeitsbereiche als Knoepfe, links die
 * Personenliste, in der Mitte der Baum, rechts die Personentafel, unten die Statuszeile. Dieselben Bausteine und
 * dasselbe ViewModel wie am Handy - nur anders angeordnet und dichter. Weitere Aufbauten (Liste und Personenblatt)
 * sollen aus denselben Bausteinen entstehen.
 */

@Composable
fun FrameWindowScope.DeskRoot(viewModel: AppViewModel, onQuit: () -> Unit) {
    // Beenden mit ungespeicherten Eingaben im Personenblatt: erst nachfragen (auch vom Fenster-X, siehe Main.kt).
    val beenden: () -> Unit = { if (Entwuerfe.abschliessen(viewModel)) Entwuerfe.beendenAnfrage = true else onQuit() }
    val state by viewModel.state.collectAsState()
    val appName = LocalAppName.current
    val openWeb: (String) -> Unit = { openBrowser(it) }
    val search = remember { FocusRequester() }
    var about by remember { mutableStateOf(false) }
    var layout by remember { mutableStateOf(DeskLayout.load()) }
    var sheetOpen by remember { mutableStateOf(false) }
    var goTo by remember { mutableStateOf(false) }
    var liste by remember { mutableStateOf<ListenArt?>(null) }
    var merkliste by remember { mutableStateOf(false) }
    // Tafelfenster: null = zu, sonst die Tafelart, mit der es oeffnet
    var tafel by remember { mutableStateOf<TafelArt?>(null) }
    var buch by remember { mutableStateOf(false) }
    var pruefung by remember { mutableStateOf(false) }
    var startperson by remember { mutableStateOf(false) }
    var tabelle by remember { mutableStateOf(false) }
    // Quellenverwaltung: null = zu, "" = offen ohne Auswahl, sonst die Quelle, mit der sie oeffnet
    var quellen by remember { mutableStateOf<String?>(null) }
    val quellenApi = (state.info?.api ?: 0) >= de.bgghome.webtrees.nativ.api.API_SOURCES
    var orte by remember { mutableStateOf<String?>(null) }
    // Orte bereinigen sofort oeffnen (aus der Pruefung)
    var orteBereinigen by remember { mutableStateOf(false) }
    val orteApi = (state.info?.api ?: 0) >= de.bgghome.webtrees.nativ.api.API_PLACE_LIST
    // Personen zusammenfuehren: nur Verwalter des Stammbaums, wie in webtrees selbst
    val mergeApi = state.tree?.role == "manager" && (state.info?.api ?: 0) >= de.bgghome.webtrees.nativ.api.API_MERGE
    var zusammen by remember { mutableStateOf<String?>(null) }
    // Stufe 30: Aufgaben, Letzte Aenderungen, Aufgabe aus Pruefung/Menue
    val stufe30 = viewModel.tasksSupported
    var aufgaben by remember { mutableStateOf(false) }
    var aenderungen by remember { mutableStateOf(false) }
    var aufgabeNeu by remember { mutableStateOf<AufgabeZiel?>(null) }
    var zusammenPaar by remember { mutableStateOf<de.bgghome.webtrees.nativ.data.Dublette?>(null) }
    val openSheet: (String) -> Unit = { xref -> viewModel.select(xref); sheetOpen = true }

    // Zurueck/Vor zwischen Zentralpersonen: das ViewModel kennt nur den Rueckweg, den Vorwaertsweg haelt der Desktop.
    val vorwaerts = remember { mutableStateListOf<String>() }
    var eigenerSchritt by remember { mutableStateOf(false) }
    LaunchedEffect(state.root) { if (!eigenerSchritt) vorwaerts.clear(); eigenerSchritt = false }
    val nav = DeskNav(
        kannZurueck = state.rootHistory.isNotEmpty(), kannVor = vorwaerts.isNotEmpty(),
        zurueck = { if (state.rootHistory.isNotEmpty()) { state.root?.let { vorwaerts.add(it) }; eigenerSchritt = true; viewModel.back() } },
        vor = { vorwaerts.removeLastOrNull()?.let { eigenerSchritt = true; viewModel.setRoot(it) } },
    )

    // Farbkodierung nach Mary Hill, bezogen auf die Startperson; die Wahl bleibt gespeichert.
    var farbkodierung by remember { mutableStateOf(DeskLayout.prefs.getBoolean("farbkodierung", false)) }
    val farben by produceState(emptyMap<String, Color>(), farbkodierung, state.home, state.tree?.name) {
        val tree = state.tree?.name; val home = state.home
        value = if (!farbkodierung || tree == null || home == null) emptyMap() else runCatching {
            val ahnen = viewModel.client.pedigree(tree, home, 7).ancestors.associate { it.person.xref to MaryHill.fuer(it.n) }
            val nachkommen = mutableMapOf<String, Color>()
            fun sammeln(k: de.bgghome.webtrees.nativ.api.DescendantNode) { k.families.forEach { f -> f.children.forEach { c -> nachkommen[c.person.xref] = MaryHill.nachkommen; sammeln(c) } } }
            sammeln(viewModel.client.descendants(tree, home, 4).tree)
            nachkommen + ahnen
        }.getOrElse { emptyMap() }
    }
    val drucke = DeskDruck(state, viewModel, LocalAppName.current)
    var zoom by remember { mutableStateOf(DeskLayout.prefs.getString("zoom", null)?.toFloatOrNull() ?: 1f) }
    // Texte unter den Symbolen (Ansicht -> Symboltexte); bei Platzmangel fallen sie ohnehin weg.
    var lokaleBaeume by remember { mutableStateOf(false) }
    // Stammbaum auf diesem PC ohne Startperson: einmal je Baum danach fragen (sonst beginnt er mit der ersten Person der Datei)
    var startFrage by remember { mutableStateOf(false) }
    LaunchedEffect(state.tree?.name, state.screen, state.tree?.startXref) {
        val t = state.tree ?: return@LaunchedEffect
        val merk = "startfrage_" + t.name
        if (state.screen == Screen.Main && LokalBetrieb.istLokal(state.baseUrl) && viewModel.startPersonSupported && t.individuals > 1 &&
            t.treeDefaultXref.isEmpty() && t.defaultXref.isEmpty() && t.userXref.isEmpty() && !DeskLayout.prefs.getBoolean(merk, false)) startFrage = true
    }
    var symboltexte by remember { mutableStateOf(DeskLayout.prefs.getBoolean("symboltexte", true)) }

    DeskMenuBar(
        state, viewModel, openWeb, layout = layout, onLayout = { layout = it; DeskLayout.save(it) },
        onSearch = { if (layout == DeskLayout.Navigator) goTo = true else runCatching { search.requestFocus() } },
        onSheet = { (state.detail?.person?.xref ?: state.root)?.let(openSheet) },
        onAbout = { about = true }, onQuit = beenden,
        nav = nav, drucke = drucke, onListe = { liste = it }, onHilfe = { Hilfe.oeffnen(if (state.screen == Screen.Main) "hauptfenster" else HilfeTexte.START) },
        farbkodierung = farbkodierung, onFarbkodierung = { farbkodierung = it; DeskLayout.prefs.putBoolean("farbkodierung", it) },
        symboltexte = symboltexte, onSymboltexte = { symboltexte = it; DeskLayout.prefs.putBoolean("symboltexte", it) },
        onMerkliste = { merkliste = true }, onTafel = { tafel = it }, onBuch = { buch = true }, onPruefung = { pruefung = true },
        onTabelle = { tabelle = true },
        onQuellen = if (quellenApi) ({ quellen = "" }) else null,
        onStartperson = if (viewModel.startPersonSupported) ({ startperson = true }) else null,
        onOrte = if (orteApi) ({ orte = "" }) else null,
        onZusammenfuehren = if (mergeApi) ({ zusammen = "" }) else null,
        onAufgaben = if (stufe30) ({ aufgaben = true }) else null,
        onAenderungen = if (stufe30) ({ aenderungen = true }) else null,
        onAufgabeNeu = if (stufe30 && state.tree?.canEdit == true) ({ aufgabeNeu = it }) else null,
        onLokaleBaeume = if (LokalBetrieb.istLokal(state.baseUrl) && LokalBetrieb.verfuegbar) ({ lokaleBaeume = true }) else null,
    )

    // Beim Sprachwechsel entsteht alles darunter neu und liest die Texte frisch. Die Menueleiste oben bleibt stehen:
    // baute Compose sie neu, setzte das Abbauen der alten die Leiste des Fensters auf null (Menues tot).
    de.bgghome.webtrees.nativ.Sprache.Umgebung {
        // Noch nicht verbunden: auf den Verbinden-Link aus webtrees warten (Knopf "Mit wtWin verbinden" legt ihn in die
        // Zwischenablage). Uebernommen wird er erst nach der Rueckfrage; der Einmal-Code bleibt nicht in der Ablage liegen.
        val wartet = (state.screen == Screen.Setup || state.screen == Screen.Login) && state.pendingConnect == null
        LaunchedEffect(wartet) {
            while (wartet) {
                val link = withContext(Dispatchers.IO) { zwischenablageText() }
                if (verbindungAusText(link) != null) {
                    zwischenablageLeeren()
                    viewModel.connectLink(link!!)
                    break
                }
                delay(1000)
            }
        }

        // Vor der Anmeldung und bei der Baumwahl: die Startbildschirme der App, mittig im Fenster.
        if (state.screen != Screen.Main) {
            // Erster Start (noch keine Adresse): daneben der Weg "Neuen Stammbaum auf diesem PC anlegen" (Stufe 4).
            if (state.screen == Screen.Setup && LokalBetrieb.verfuegbar) DeskStart(viewModel) else AppRoot(viewModel)
            // Hilfe und "Ueber" stehen im Menue schon vor der Anmeldung
            HilfeFenster()
            if (about) UeberDialog(state, viewModel, appName, onClose = { about = false })
            if (Entwuerfe.beendenAnfrage) UngespeichertDialog(viewModel, onWeiter = { Entwuerfe.beendenAnfrage = false; onQuit() }, onAbbrechen = { Entwuerfe.beendenAnfrage = false })
            return@Umgebung
        }

        // Verbinden-Link bei laufender Sitzung (wtwin:// aus dem Browser): wie vor der Anmeldung erst nachfragen.
        state.pendingConnect?.let { request ->
            ConfirmDialog(
                title = stringResource(Res.string.connect_confirm_title),
                text = stringResource(Res.string.connect_confirm_text, request.url, request.user.ifEmpty { "–" }, request.tree.ifEmpty { "–" }),
                confirm = stringResource(Res.string.connect_confirm_action),
                onDismiss = viewModel::cancelConnect,
                onConfirm = viewModel::confirmConnect,
            )
        }

        LaunchedEffect(Unit) { viewModel.setWide(true) }

        BackHandler(enabled = state.viewer != null || state.pdf != null || state.treeFullscreen) {
            if (state.viewer != null) viewModel.closeViewer() else if (state.pdf != null) viewModel.closePdf() else viewModel.setTreeFullscreen(false)
        }

        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(state.message) {
            state.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
        }

        // Ortsnamen (Personentafel, Ortsfelder in den Dialogen) oeffnen die Ortsverwaltung, wenn der Server sie kennt
        val ortOeffnen: ((String) -> Unit)? = if (orteApi) ({ n: String -> orte = n }) else null
        CompositionLocalProvider(de.bgghome.webtrees.nativ.ui.LocalPlaceOpener provides ortOeffnen) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Column(Modifier.fillMaxSize()) {
                    // Eine Symbolleiste fuer alle drei Aufbauten; "Gehe zu" springt im Navigator in den Dialog, sonst ins Suchfeld
                    ClassicToolbar(state, viewModel, openWeb, onGoTo = { if (layout == DeskLayout.Navigator) goTo = true else runCatching { search.requestFocus() } },
                        onSheet = { (state.root)?.let(openSheet) }, onAbout = { Hilfe.oeffnen("hauptfenster") }, nav = nav, drucke = drucke,
                        symboltexte = symboltexte, onMerkliste = { merkliste = true }, onListe = { liste = it }, onTabelle = { tabelle = true }, onTafel = { tafel = it }, onPruefung = { pruefung = true }, onQuit = beenden,
                        onQuellen = if (quellenApi) ({ quellen = "" }) else null, onOrte = if (orteApi) ({ orte = "" }) else null,
                        layout = layout, onAufgaben = if (stufe30) ({ aufgaben = true }) else null, onAenderungen = if (stufe30) ({ aenderungen = true }) else null)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.fillMaxWidth().height(3.dp)) {
                        if (state.busy || state.loadingDetail || state.loadingPeople) LinearProgressIndicator(Modifier.fillMaxSize())
                    }
                    if (layout == DeskLayout.Navigator) Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (state.section) {
                            Section.Home -> HomeSection(state, viewModel, openWeb)
                            Section.Photos -> PhotosSection(state, viewModel, openWeb)
                            else -> Navigator(state, viewModel, openSheet, openWeb, farben, zoom, onZoom = { zoom = it; DeskLayout.prefs.putString("zoom", it.toString()) })
                        }
                    } else Row(Modifier.weight(1f).fillMaxWidth()) {
                        // Vollbild (Knopf im Baum oder Esc zurueck): nur der Baum, ohne Personenliste und Personentafel.
                        // Fotos/Archiv brauchen die ganze Breite (wie im Aufbau Navigator) - dort ebenfalls ohne Seitenleisten.
                        val vollbild = (layout == DeskLayout.TreeCentre && state.treeFullscreen && state.section != Section.Home && state.section != Section.Photos) ||
                            state.section == Section.Photos
                        // Breite der Seitenleisten: am Griff ziehbar, bleibt gespeichert
                        var linksBreite by remember { mutableStateOf(DeskLayout.prefs.getString("panel_links", null)?.toFloatOrNull() ?: 280f) }
                        var rechtsBreite by remember { mutableStateOf(DeskLayout.prefs.getString("panel_rechts", null)?.toFloatOrNull() ?: 400f) }
                        val dichte = LocalDensity.current.density
                        if (!vollbild) {
                            PersonIndex(state, viewModel, openWeb, search, Modifier.width(linksBreite.dp).fillMaxHeight())
                            Ziehgriff(onZiehen = { linksBreite = (linksBreite + it / dichte).coerceIn(180f, 600f) }) { DeskLayout.prefs.putString("panel_links", linksBreite.toString()) }
                        }
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            when (state.section) {
                                Section.Home -> HomeSection(state, viewModel, openWeb)
                                Section.Photos -> PhotosSection(state, viewModel, openWeb)
                                else -> if (layout == DeskLayout.Family) DeskFamilie(state, viewModel, openWeb, openSheet, onSuche = { runCatching { search.requestFocus() } }) else DeskTree(state, viewModel, openWeb)
                            }
                        }
                        if (!vollbild) {
                            Ziehgriff(onZiehen = { rechtsBreite = (rechtsBreite - it / dichte).coerceIn(260f, 720f) }) { DeskLayout.prefs.putString("panel_rechts", rechtsBreite.toString()) }
                            Box(Modifier.width(rechtsBreite.dp).fillMaxHeight()) {
                                val detail = state.detail
                                if (detail != null) {
                                    CompositionLocalProvider(de.bgghome.webtrees.nativ.ui.LocalSourceOpener provides (if (quellenApi) ({ x: String -> quellen = x }) else null)) {
                                        ProfilePanel(state, detail, viewModel, openWeb, onClose = null)
                                    }
                                } else {
                                    Text(
                                        stringResource(Res.string.detail_choose), Modifier.align(Alignment.Center).padding(24.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
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
        }

        CompositionLocalProvider(de.bgghome.webtrees.nativ.ui.LocalPlaceOpener provides ortOeffnen) {
            // Verwandte hinzufuegen - aus der Tafel, vom Kontextmenue oder von der "+"-Lasche einer Karte.
            val detail = state.detail
            if (state.addRelativeFor != null && detail != null && detail.person.xref == state.addRelativeFor && !state.loadingDetail) {
                RelativeDialog(
                    target = RelativeTarget.of(detail),
                    suggestPlaces = viewModel.placeSuggestions(),
                    onDismiss = { Verwandtenwahl.leeren(); viewModel.addRelativeHandled() },
                    onSave = { Verwandtenwahl.leeren(); viewModel.addRelativeHandled(); viewModel.addRelative(it) },
                    initialRelation = Verwandtenwahl.vorwahl, initialFamily = Verwandtenwahl.familie, initialSex = Verwandtenwahl.geschlecht,
                    // Dublettenwarnung: gleichnamige Personen anzeigen und statt neu anzulegen anfuegen
                    suche = { q -> state.tree?.let { t -> runCatching { viewModel.client.individuals(t.name, q, 1).data }.getOrNull() }.orEmpty() },
                    onLink = { p, rel, fam ->
                        Verwandtenwahl.leeren(); viewModel.addRelativeHandled()
                        viewModel.linkRelative(de.bgghome.webtrees.nativ.api.LinkRequest(p.xref, rel, detail.person.xref, fam))
                    },
                )
            }

            if (sheetOpen && state.detail != null) PersonSheet(state, viewModel, openWeb, onClose = { sheetOpen = false }, onQuelle = if (quellenApi) ({ quellen = it }) else null)
        }
        quellen?.let { start -> if (state.tree != null) QuellenFenster(state, viewModel, start, openWeb, onClose = { quellen = null }) }
        orte?.let { start -> if (state.tree != null) OrteFenster(state, viewModel, start, openWeb, onClose = { orte = null; orteBereinigen = false }, onBlatt = openSheet, bereinigen = orteBereinigen) }
        if (goTo) GoToDialog(state, viewModel, openWeb, onClose = { goTo = false })
        LoeschRueckfrage(viewModel)
        Anfuegewahl.person?.let { z -> state.tree?.let { t -> if (t.canEdit) AnfuegenDialog(z, viewModel, t.name, onClose = { Anfuegewahl.person = null }) } }
        liste?.let { art -> ListenFenster(art, state, viewModel, onClose = { liste = null }) }
        HilfeFenster()
        if (startperson) StartpersonDialog(state, viewModel, onClose = { startperson = false })
        if (startFrage) StartpersonFrage(state, viewModel, onClose = {
            startFrage = false; state.tree?.let { DeskLayout.prefs.putBoolean("startfrage_" + it.name, true) }
        })
        if (merkliste) MerklisteFenster(state, viewModel, openSheet, onClose = { merkliste = false })
        tafel?.let { art -> if (state.root != null) TafelFenster(state, viewModel, art, onClose = { tafel = null }) }
        if (buch && state.root != null) BuchFenster(state, viewModel, onClose = { buch = false })
        if (pruefung && state.tree != null) PruefFenster(state, viewModel, openSheet, onClose = { pruefung = false },
            onOrteBereinigen = if (orteApi && state.tree?.canEdit == true && (state.info?.api ?: 0) >= de.bgghome.webtrees.nativ.api.API_PLACE_RENAME) ({ orteBereinigen = true; orte = "" }) else null,
            onZusammenfuehren = if (mergeApi) ({ zusammenPaar = it }) else null,
            onAufgabe = if (stufe30 && state.tree?.canEdit == true) ({ aufgabeNeu = it }) else null)
        if (aufgaben && state.tree != null) AufgabenFenster(state, viewModel, openSheet, onClose = { aufgaben = false })
        if (aenderungen && state.tree != null) AenderungenFenster(state, viewModel, openSheet, onClose = { aenderungen = false })
        aufgabeNeu?.let { z -> state.tree?.let { t -> AufgabeDialog(z, viewModel, t.name, onClose = { ok -> aufgabeNeu = null; if (ok) viewModel.setRoot(z.xref, remember = false) }) } }
        if (tabelle && state.tree != null) PersonenTabelle(state, viewModel, openSheet, openWeb, onClose = { tabelle = false },
            onZusammenfuehren = if (mergeApi) ({ zusammen = it }) else null)
        zusammen?.let { s -> if (state.tree != null) ZusammenfuehrenFenster(state, viewModel, s, openSheet, onClose = { zusammen = null }) }
        zusammenPaar?.let { p -> state.tree?.let { t -> ZusammenfuehrenDialog(p, viewModel, t.name, onClose = { geaendert -> zusammenPaar = null; if (geaendert) viewModel.refresh() }) } }

        if (about) UeberDialog(state, viewModel, appName, onClose = { about = false })
        if (lokaleBaeume) LokaleBaeumeDialog(state, viewModel, onClose = { lokaleBaeume = false })
        if (Entwuerfe.beendenAnfrage) UngespeichertDialog(viewModel, onWeiter = { Entwuerfe.beendenAnfrage = false; onQuit() }, onAbbrechen = { Entwuerfe.beendenAnfrage = false })
    }
}

// ── Menueleiste ──────────────────────────────────────────────────────

@Composable
private fun FrameWindowScope.DeskMenuBar(
    state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit,
    layout: DeskLayout, onLayout: (DeskLayout) -> Unit,
    onSearch: () -> Unit, onSheet: () -> Unit, onAbout: () -> Unit, onQuit: () -> Unit,
    nav: DeskNav, drucke: DeskDruck, onListe: (ListenArt) -> Unit, onHilfe: () -> Unit,
    farbkodierung: Boolean, onFarbkodierung: (Boolean) -> Unit,
    symboltexte: Boolean, onSymboltexte: (Boolean) -> Unit,
    onMerkliste: () -> Unit, onTafel: (TafelArt) -> Unit, onBuch: () -> Unit, onPruefung: () -> Unit, onTabelle: () -> Unit,
    onQuellen: (() -> Unit)? = null,
    onOrte: (() -> Unit)? = null,
    onZusammenfuehren: (() -> Unit)? = null,
    onAufgaben: (() -> Unit)? = null,
    onAenderungen: (() -> Unit)? = null,
    onAufgabeNeu: ((AufgabeZiel) -> Unit)? = null,
    onStartperson: (() -> Unit)? = null,
    onLokaleBaeume: (() -> Unit)? = null,
) {
    val main = state.screen == Screen.Main
    val loggedIn = state.info?.user?.loggedIn == true
    val selected = state.detail
    MenuBar {
        key(de.bgghome.webtrees.nativ.Sprache.aktiv) {
            Menu(stringResource(Res.string.desk_menu_file)) {
                if (main && (state.info?.trees?.size ?: 0) > 1) Item(stringResource(Res.string.menu_switch_tree), onClick = viewModel::showTreePicker)
                if (main && onLokaleBaeume != null) Item(stringResource(Res.string.lokal_trees_menu), onClick = onLokaleBaeume)
                Item(stringResource(Res.string.action_reload), enabled = main, shortcut = KeyShortcut(Key.F5), onClick = viewModel::refresh)
                Item(stringResource(Res.string.desk_open_browser), enabled = state.baseUrl.isNotEmpty(), onClick = { openWeb(state.detail?.person?.url ?: state.baseUrl) })
                Separator()
                Item(stringResource(Res.string.desk_print_sheet), enabled = main && state.root != null, shortcut = KeyShortcut(Key.P, ctrl = true), onClick = drucke::personenblatt)
                Item(stringResource(Res.string.desk_pdf_sheet), enabled = main && state.root != null, onClick = drucke::personenblattPdf)
                Item(stringResource(Res.string.desk_chart_ancestors_open), enabled = main && state.root != null, onClick = { onTafel(TafelArt.Ahnen) })
                Item(stringResource(Res.string.desk_chart_open), enabled = main && state.root != null, onClick = { onTafel(TafelArt.Stamm) })
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
                onStartperson?.let { Item(stringResource(Res.string.desk_set_start_person), enabled = state.root != null, onClick = it) }
                Item(stringResource(Res.string.action_make_root), enabled = selected != null && selected.person.xref != state.root,
                    shortcut = KeyShortcut(Key.Enter, ctrl = true), onClick = { selected?.let { viewModel.setRoot(it.person.xref) } })
                Item(stringResource(Res.string.action_add_relative), enabled = selected?.canEdit == true,
                    shortcut = KeyShortcut(Key.N, ctrl = true), onClick = { selected?.let { viewModel.requestAddRelative(it.person.xref) } })
                onZusammenfuehren?.let { Item(stringResource(Res.string.desk_merge_menu), enabled = state.tree != null, onClick = it) }
                Item(stringResource(Res.string.desk_link_existing), enabled = selected?.canEdit == true, onClick = { selected?.let { Anfuegewahl.person = it.person } })
                onAufgabeNeu?.let { f -> Item(stringResource(Res.string.desk_task_add_menu), enabled = selected != null, onClick = { selected?.let { f(AufgabeZiel(it.person.xref, it.person.name)) } }) }
                if (onAufgaben != null && state.tree?.role == "manager") {
                    val root = state.root
                    Item(stringResource(if (root != null && viewModel.isTreeBookmarked(root)) Res.string.desk_bookmark_tree_remove else Res.string.desk_bookmark_tree_add),
                        enabled = root != null, onClick = { root?.let(viewModel::toggleTreeBookmark) })
                }
                Item(stringResource(Res.string.action_delete_person), enabled = selected?.canEdit == true,
                    onClick = { selected?.let { Loeschwahl.person = it.person.xref to it.person.name } })
                Item(stringResource(Res.string.chip_open_web), enabled = selected != null, onClick = { selected?.let { openWeb(it.person.url) } })
                if (viewModel.bookmarksSupported) {
                    val root = state.root
                    Item(stringResource(if (root != null && viewModel.isBookmarked(root)) Res.string.desk_bookmark_remove else Res.string.desk_bookmark_add),
                        enabled = root != null, shortcut = KeyShortcut(Key.D, ctrl = true), onClick = { root?.let(viewModel::toggleBookmark) })
                    Item(stringResource(Res.string.desk_bookmarks), shortcut = KeyShortcut(Key.B, ctrl = true), onClick = onMerkliste)
                    if (state.bookmarks.isNotEmpty()) Menu(stringResource(Res.string.desk_bookmarks_mine)) {
                        state.bookmarks.take(25).forEach { p -> Item(p.name + if (p.lifespan.isNotBlank()) "  (${p.lifespan})" else "", onClick = { viewModel.setRoot(p.xref) }) }
                    }
                    if (state.treeBookmarks.isNotEmpty()) Menu(stringResource(Res.string.desk_bookmarks_tree)) {
                        state.treeBookmarks.take(25).forEach { p -> Item(p.name + if (p.lifespan.isNotBlank()) "  (${p.lifespan})" else "", onClick = { viewModel.setRoot(p.xref) }) }
                    }
                }
                Separator()
                Item(stringResource(Res.string.action_back), enabled = nav.kannZurueck, shortcut = KeyShortcut(Key.DirectionLeft, alt = true), onClick = nav.zurueck)
                Item(stringResource(Res.string.desk_forward), enabled = nav.kannVor, shortcut = KeyShortcut(Key.DirectionRight, alt = true), onClick = nav.vor)
                Item(stringResource(Res.string.desk_copy_text), enabled = state.detail != null, shortcut = KeyShortcut(Key.C, ctrl = true, shift = true), onClick = { state.detail?.let(::personentextKopieren) })
            }
            Menu(stringResource(Res.string.desk_menu_create), enabled = main) {
                Item(stringResource(Res.string.desk_list_ancestors), enabled = state.root != null, onClick = { onListe(ListenArt.Ahnen) })
                Item(stringResource(Res.string.desk_list_descendants), enabled = state.root != null, onClick = { onListe(ListenArt.Stamm) })
                Item(stringResource(Res.string.desk_list_events), onClick = { onListe(ListenArt.Ereignisse) })
                Separator()
                Item(stringResource(Res.string.desk_title_sheet, state.detail?.person?.name ?: "…"), enabled = state.root != null, onClick = { onListe(ListenArt.Personenblatt) })
                Separator()
                Item(stringResource(Res.string.desk_chart_ancestors_open), enabled = state.root != null, shortcut = KeyShortcut(Key.T, ctrl = true, shift = true), onClick = { onTafel(TafelArt.Ahnen) })
                Item(stringResource(Res.string.desk_chart_open), enabled = state.root != null, shortcut = KeyShortcut(Key.T, ctrl = true), onClick = { onTafel(TafelArt.Stamm) })
                Separator()
                Item(stringResource(Res.string.desk_book_window) + " …", enabled = state.root != null, onClick = onBuch)
                Separator()
                Item(stringResource(Res.string.desk_check_window) + " …", enabled = state.tree != null, shortcut = KeyShortcut(Key.P, ctrl = true, shift = true), onClick = onPruefung)
            }
            Menu(stringResource(Res.string.desk_menu_webtrees), enabled = main && state.tree != null) {
                val t = state.tree?.name.orEmpty()
                val manager = state.tree?.role == "manager"
                fun web(route: String) = runCatching { viewModel.client.url(route, emptyMap()).toString() }.getOrNull()?.let(openWeb)
                Item(stringResource(Res.string.desk_web_places), onClick = { web("/tree/$t/place-list") })
                Item(stringResource(Res.string.desk_web_sources), onClick = { web("/tree/$t/source-list") })
                Separator()
                Item(stringResource(Res.string.desk_web_merge), enabled = manager, onClick = { web("/tree/$t/merge-step1") })
                Item(stringResource(Res.string.desk_web_duplicates), enabled = manager, onClick = { web("/tree/$t/duplicates") })
                Item(stringResource(Res.string.desk_web_check), enabled = manager, onClick = { web("/tree/$t/check") })
                Item(stringResource(Res.string.desk_web_datafix), enabled = manager, onClick = { web("/tree/$t/data-fix") })
                Separator()
                Item(stringResource(Res.string.desk_web_export), enabled = manager, onClick = { web("/tree/$t/export") })
                Item(stringResource(Res.string.desk_web_import), enabled = manager, onClick = { web("/tree/$t/import") })
            }
            Menu(stringResource(Res.string.desk_menu_view), enabled = main) {
                Menu(stringResource(Res.string.desk_appearance)) {
                    val w = DeskErscheinung.wahl.value
                    RadioButtonItem(stringResource(Res.string.desk_light), selected = w == DeskErscheinung.Wahl.Hell, onClick = { DeskErscheinung.setzen(DeskErscheinung.Wahl.Hell) })
                    RadioButtonItem(stringResource(Res.string.desk_dark), selected = w == DeskErscheinung.Wahl.Dunkel, onClick = { DeskErscheinung.setzen(DeskErscheinung.Wahl.Dunkel) })
                    RadioButtonItem(stringResource(Res.string.desk_system), selected = w == DeskErscheinung.Wahl.System, onClick = { DeskErscheinung.setzen(DeskErscheinung.Wahl.System) })
                }
                Menu(stringResource(Res.string.menu_language)) {
                    // Wirkt sofort; Beschriftungen vom Server (Ereignisarten, Orte) kommen mit dem Neuladen nach.
                    val gewaehlt = Sprache.wahl.value
                    RadioButtonItem(stringResource(Res.string.language_system), selected = gewaehlt == null, onClick = { Sprache.setzen(null); viewModel.refresh() })
                    Sprache.ALLE.forEach { (code, name) ->
                        RadioButtonItem(name, selected = gewaehlt == code, onClick = { Sprache.setzen(code); viewModel.refresh() })
                    }
                }
                Menu(stringResource(Res.string.desk_layout)) {
                    RadioButtonItem(stringResource(Res.string.desk_layout_navigator), selected = layout == DeskLayout.Navigator, onClick = { onLayout(DeskLayout.Navigator) })
                    RadioButtonItem(stringResource(Res.string.desk_layout_tree), selected = layout == DeskLayout.TreeCentre, onClick = { onLayout(DeskLayout.TreeCentre) })
                    RadioButtonItem(stringResource(Res.string.desk_layout_family), selected = layout == DeskLayout.Family, onClick = { onLayout(DeskLayout.Family) })
                }
                CheckboxItem(stringResource(Res.string.desk_toolbar_labels), checked = symboltexte, enabled = layout == DeskLayout.Navigator, onCheckedChange = onSymboltexte)
                Separator()
                Item(stringResource(Res.string.nav_home), shortcut = KeyShortcut(Key.One, ctrl = true), onClick = { viewModel.setSection(Section.Home) })
                Item(stringResource(Res.string.nav_tree), shortcut = KeyShortcut(Key.Two, ctrl = true), onClick = { viewModel.setSection(Section.Tree) })
                Item(stringResource(Res.string.nav_photos), shortcut = KeyShortcut(Key.Three, ctrl = true), onClick = { viewModel.setSection(Section.Photos) })
                Item(stringResource(Res.string.desk_table_window), enabled = state.tree != null, shortcut = KeyShortcut(Key.Four, ctrl = true), onClick = onTabelle)
                if (onQuellen != null) Item(stringResource(Res.string.desk_sources_window), enabled = state.tree != null, shortcut = KeyShortcut(Key.Five, ctrl = true), onClick = onQuellen)
                if (onOrte != null) Item(stringResource(Res.string.desk_places_window), enabled = state.tree != null, shortcut = KeyShortcut(Key.Six, ctrl = true), onClick = onOrte)
                if (onAufgaben != null) Item(stringResource(Res.string.desk_tasks_window), enabled = state.tree != null, shortcut = KeyShortcut(Key.Seven, ctrl = true), onClick = onAufgaben)
                if (onAenderungen != null) Item(stringResource(Res.string.desk_changes_window), enabled = state.tree != null, shortcut = KeyShortcut(Key.Eight, ctrl = true), onClick = onAenderungen)
                Separator()
                Menu(stringResource(Res.string.tree_generations, state.ancestorGenerations)) {
                    (2..7).forEach { n ->
                        CheckboxItem(stringResource(Res.string.tree_generations, n), checked = state.ancestorGenerations == n, onCheckedChange = { viewModel.setAncestorGenerations(n) })
                    }
                }
                CheckboxItem(stringResource(Res.string.desk_color_coding), checked = farbkodierung, enabled = state.home != null, onCheckedChange = onFarbkodierung)
                CheckboxItem(stringResource(Res.string.tree_show_siblings), checked = state.showSiblings, onCheckedChange = viewModel::setShowSiblings)
                CheckboxItem(stringResource(Res.string.tree_show_cousins), checked = state.showCousins && state.showSiblings, enabled = state.showSiblings, onCheckedChange = viewModel::setShowCousins)
            }
            Menu(stringResource(Res.string.desk_menu_help)) {
                Item(stringResource(Res.string.desk_help), shortcut = KeyShortcut(Key.F1), onClick = onHilfe)
                Item(stringResource(Res.string.desk_about, LocalAppName.current), onClick = onAbout)
            }
        }
    }
}

/** Senkrechter Trennstrich, an dem sich die Breite der Seitenleiste ziehen laesst; [onEnde] speichert die Wahl. */

/**
 * Mauszeiger ↔ fuer Trennstriche. Java kennt keinen Doppelpfeil (E_RESIZE ist unter Linux ein Pfeil nach rechts), also
 * selbst gezeichnet: schwarz mit weissem Rand, sichtbar auf hellem und dunklem Grund.
 */
private val doppelpfeil: PointerIcon by lazy { pfeilZeiger(senkrecht = false) }
private val doppelpfeilSenkrecht: PointerIcon by lazy { pfeilZeiger(senkrecht = true) }

private fun pfeilZeiger(senkrecht: Boolean): PointerIcon =
    runCatching {
        val tk = java.awt.Toolkit.getDefaultToolkit()
        val groesse = tk.getBestCursorSize(32, 32).let { if (it.width < 16) java.awt.Dimension(32, 32) else it }
        val w = groesse.width; val h = groesse.height
        val bild = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = bild.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        // Senkrecht: derselbe Pfeil um 90 Grad gedreht
        if (senkrecht) g.rotate(Math.PI / 2, w / 2.0, h / 2.0)
        val cy = h / 2.0; val l = w * 0.12; val r = w * 0.88; val spitze = w * 0.24; val halb = h * 0.2; val schaft = h * 0.06
        val pfeil = java.awt.geom.Path2D.Double().apply {
            moveTo(l, cy); lineTo(l + spitze, cy - halb); lineTo(l + spitze, cy - schaft); lineTo(r - spitze, cy - schaft)
            lineTo(r - spitze, cy - halb); lineTo(r, cy); lineTo(r - spitze, cy + halb); lineTo(r - spitze, cy + schaft)
            lineTo(l + spitze, cy + schaft); lineTo(l + spitze, cy + halb); closePath()
        }
        g.color = java.awt.Color.WHITE; g.stroke = java.awt.BasicStroke(w / 10f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND); g.draw(pfeil)
        g.color = java.awt.Color.BLACK; g.fill(pfeil)
        g.dispose()
        PointerIcon(tk.createCustomCursor(bild, java.awt.Point(w / 2, h / 2), if (senkrecht) "doppelpfeil-senkrecht" else "doppelpfeil"))
    }.getOrElse { PointerIcon(java.awt.Cursor(if (senkrecht) java.awt.Cursor.N_RESIZE_CURSOR else java.awt.Cursor.E_RESIZE_CURSOR)) }

/** Breite einer Seitenleiste in dp, in den Desktop-Einstellungen unter [name] gemerkt. */
@Composable
internal fun rememberBreite(name: String, vorgabe: Float): MutableState<Float> =
    remember { mutableStateOf(DeskLayout.prefs.getString(name, null)?.toFloatOrNull() ?: vorgabe) }

/** Waagrechter Trennstrich zwischen zwei uebereinanderliegenden Bereichen; [onZiehen] bekommt die Verschiebung nach unten. */
@Composable
internal fun ZiehgriffWaagrecht(onZiehen: (Float) -> Unit, onEnde: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(7.dp)
            .pointerHoverIcon(doppelpfeilSenkrecht)
            .pointerInput(Unit) { detectVerticalDragGestures(onDragEnd = onEnde, onDragCancel = onEnde) { _, dy -> onZiehen(dy) } },
        contentAlignment = Alignment.Center,
    ) { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
}

/** Ziehbarer waagrechter Trennstrich fuer [hoehe]; [unten]: der Bereich liegt unter dem Strich (Ziehen nach oben macht ihn hoeher). */
@Composable
internal fun TrennerWaagrecht(hoehe: MutableState<Float>, name: String, min: Float, max: Float, unten: Boolean = false) {
    val dichte = LocalDensity.current.density
    ZiehgriffWaagrecht(onZiehen = { hoehe.value = (hoehe.value + (if (unten) -it else it) / dichte).coerceIn(min, max) }) {
        DeskLayout.prefs.putString(name, hoehe.value.toString())
    }
}

/** Ziehbarer Trennstrich fuer [breite]; [rechts]: die Leiste liegt rechts vom Strich (Ziehen nach links macht sie breiter). */
@Composable
internal fun Trenner(breite: MutableState<Float>, name: String, min: Float, max: Float, rechts: Boolean = false) {
    val dichte = LocalDensity.current.density
    Ziehgriff(onZiehen = { breite.value = (breite.value + (if (rechts) -it else it) / dichte).coerceIn(min, max) }) {
        DeskLayout.prefs.putString(name, breite.value.toString())
    }
}

@Composable
internal fun Ziehgriff(onZiehen: (Float) -> Unit, onEnde: () -> Unit) {
    Box(
        Modifier.fillMaxHeight().width(7.dp)
            // Doppelpfeil links-rechts - hier wird verschoben, nicht geklickt
            .pointerHoverIcon(doppelpfeil)
            .pointerInput(Unit) { detectHorizontalDragGestures(onDragEnd = onEnde, onDragCancel = onEnde) { _, dx -> onZiehen(dx) } },
        contentAlignment = Alignment.Center,
    ) { VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
}

// ── Aufbau ───────────────────────────────────────────────────────────

/** Die waehlbaren Aufbauten des Hauptfensters; die Wahl bleibt in den Desktop-Einstellungen. */
enum class DeskLayout {
    Navigator, TreeCentre, Family;

    companion object {
        val prefs = de.bgghome.webtrees.nativ.data.DesktopAblage("desk")
        fun load(): DeskLayout = entries.firstOrNull { it.name == prefs.getString("layout", null) } ?: Navigator
        fun save(layout: DeskLayout) = prefs.putString("layout", layout.name)
    }
}

// ── Klassische Symbolleiste (Aufbau Navigator) ───────────────────────

/** Ein Eintrag der Symbolleiste: Knopf (mit oder ohne Aufklappmenue) oder Trennstrich. */
private sealed interface LeistenEintrag
private object Trenner : LeistenEintrag
private class Knopf(
    val icon: ImageVector, val label: String, val enabled: Boolean = true, val active: Boolean = false,
    val menu: (@Composable (close: () -> Unit) -> Unit)? = null, val onClick: () -> Unit = {},
) : LeistenEintrag

// Breiten in dp, wie ToolItem und ToolSeparator sie zeichnen - die Leiste rechnet damit vor dem Zeichnen.
private const val KNOPF_TEXT = 62f
private const val KNOPF_SYMBOL = 36f
private const val TRENNER = 9f

/*
 * Symbolleiste, die Platzmangel vertraegt (unter Windows mit 125 % Skalierung reichte die Breite nicht): passt sie mit Texten nicht, zeigt sie nur Symbole (Name beim Ueberfahren); passt sie
 * auch so nicht, wandert der Rest in das Menue "Mehr" am rechten Ende. Die Texte lassen sich unter Ansicht
 * ganz abschalten. Generationen und Zoom stehen im Navigator selbst.
 */
@Composable
private fun ClassicToolbar(
    state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit, onGoTo: () -> Unit, onSheet: () -> Unit, onAbout: () -> Unit,
    nav: DeskNav, drucke: DeskDruck, symboltexte: Boolean,
    onMerkliste: () -> Unit, onListe: (ListenArt) -> Unit, onTabelle: () -> Unit, onTafel: (TafelArt) -> Unit, onPruefung: () -> Unit, onQuit: () -> Unit,
    onQuellen: (() -> Unit)? = null,
    onOrte: (() -> Unit)? = null,
    layout: DeskLayout = DeskLayout.Navigator,
    onAufgaben: (() -> Unit)? = null,
    onAenderungen: (() -> Unit)? = null,
) {
    val canEdit = state.tree?.canEdit == true
    val manager = state.tree?.role == "manager"
    val t = state.tree?.name.orEmpty()
    fun web(route: String) = runCatching { viewModel.client.url(route, emptyMap()).toString() }.getOrNull()?.let(openWeb)
    val eintraege: List<LeistenEintrag> = buildList {
        add(Knopf(Icons.Default.Search, stringResource(Res.string.desk_goto), onClick = onGoTo))
        add(Knopf(Icons.Default.Edit, stringResource(Res.string.action_edit), enabled = state.root != null, onClick = onSheet))
        add(Knopf(Icons.Default.Add, stringResource(Res.string.action_add_relative), enabled = canEdit && state.root != null) { state.root?.let(viewModel::requestAddRelative) })
        if (viewModel.bookmarksSupported) add(Knopf(Icons.Default.Star, stringResource(Res.string.desk_bookmarks), onClick = onMerkliste))
        add(Trenner)
        add(Knopf(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back), enabled = nav.kannZurueck, onClick = nav.zurueck))
        add(Knopf(Icons.AutoMirrored.Filled.ArrowForward, stringResource(Res.string.desk_forward), enabled = nav.kannVor, onClick = nav.vor))
        add(Knopf(Icons.Default.DateRange, stringResource(Res.string.desk_history), enabled = state.recent.isNotEmpty(), menu = { close ->
            state.recent.forEach { p ->
                DropdownMenuItem(text = { Text(p.name + if (p.lifespan.isNotBlank()) "  (${p.lifespan})" else "") }, onClick = { close(); viewModel.setRoot(p.xref) })
            }
        }))
        add(Knopf(Icons.Default.Person, stringResource(Res.string.home_start_person), enabled = state.home != null) { state.home?.let(viewModel::setRoot) })
        add(Trenner)
        add(Knopf(Icons.AutoMirrored.Filled.List, stringResource(Res.string.desk_list), enabled = state.root != null, menu = { close ->
            DropdownMenuItem(text = { Text(stringResource(Res.string.desk_table_window)) }, onClick = { close(); onTabelle() })
            HorizontalDivider()
            DropdownMenuItem(text = { Text(stringResource(Res.string.desk_list_ancestors)) }, onClick = { close(); onListe(ListenArt.Ahnen) })
            DropdownMenuItem(text = { Text(stringResource(Res.string.desk_list_descendants)) }, onClick = { close(); onListe(ListenArt.Stamm) })
            DropdownMenuItem(text = { Text(stringResource(Res.string.desk_list_events)) }, onClick = { close(); onListe(ListenArt.Ereignisse) })
            DropdownMenuItem(text = { Text(stringResource(Res.string.desk_title_sheet, state.detail?.person?.name ?: "…")) }, onClick = { close(); onListe(ListenArt.Personenblatt) })
        }))
        add(Knopf(TreeIcon, stringResource(Res.string.desk_chart), enabled = state.root != null, menu = { close ->
            DropdownMenuItem(text = { Text(stringResource(Res.string.desk_chart_ancestors_open)) }, onClick = { close(); onTafel(TafelArt.Ahnen) })
            DropdownMenuItem(text = { Text(stringResource(Res.string.desk_chart_open)) }, onClick = { close(); onTafel(TafelArt.Stamm) })
        }))
        add(Knopf(DruckerIcon, stringResource(Res.string.desk_print), enabled = state.root != null, onClick = drucke::personenblatt))
        add(Trenner)
        add(Knopf(Icons.Default.Home, stringResource(Res.string.nav_home), active = state.section == Section.Home) { viewModel.setSection(Section.Home) })
        add(Knopf(TreeIcon, stringResource(when (layout) { DeskLayout.Family -> Res.string.nav_family; DeskLayout.Navigator -> Res.string.desk_layout_navigator; else -> Res.string.nav_tree }),
            active = state.section == Section.Tree || state.section == Section.Search) { viewModel.setSection(Section.Tree) })
        add(Knopf(PhotoIcon, stringResource(Res.string.nav_photos), active = state.section == Section.Photos) { viewModel.setSection(Section.Photos) })
        add(Trenner)
        // Die Plausibilitaetspruefung des Programms; "Stammbaum pruefen" von webtrees bleibt im Menue webtrees.
        add(Knopf(Icons.Default.Check, stringResource(Res.string.desk_check), enabled = state.tree != null, onClick = onPruefung))
        add(Knopf(Icons.Default.Place, stringResource(Res.string.desk_web_places), enabled = state.tree != null) { onOrte?.invoke() ?: web("/tree/$t/place-list") })
        add(Knopf(SourceIcon, stringResource(Res.string.desk_web_sources), enabled = state.tree != null) { onQuellen?.invoke() ?: web("/tree/$t/source-list") })
        if (onAufgaben != null) add(Knopf(de.bgghome.webtrees.nativ.ui.TaskIcon, stringResource(Res.string.desk_tasks_window), enabled = state.tree != null, onClick = onAufgaben))
        if (onAenderungen != null) add(Knopf(de.bgghome.webtrees.nativ.ui.HistoryIcon, stringResource(Res.string.desk_changes_window), enabled = state.tree != null, onClick = onAenderungen))
        add(Knopf(Icons.AutoMirrored.Filled.ExitToApp, "webtrees") { openWeb(state.detail?.person?.url ?: state.baseUrl) })
        add(Trenner)
        add(Knopf(de.bgghome.webtrees.nativ.ui.LanguageIcon, stringResource(Res.string.menu_language), menu = { close ->
            DropdownMenuItem(text = { Text(stringResource(Res.string.language_system)) }, onClick = { close(); Sprache.setzen(null); viewModel.refresh() })
            Sprache.ALLE.forEach { (code, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { close(); Sprache.setzen(code); viewModel.refresh() }) }
        }))
        add(Knopf(HelpIcon, stringResource(Res.string.desk_help), onClick = onAbout))
        add(Knopf(Icons.Default.Close, stringResource(Res.string.desk_quit), onClick = onQuit))
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            TreePicker(state, viewModel)
            ToolSeparator()
            BoxWithConstraints(Modifier.weight(1f)) {
                val platz = maxWidth.value
                fun breite(e: LeistenEintrag, text: Boolean) = if (e is Knopf) (if (text) KNOPF_TEXT else KNOPF_SYMBOL) else TRENNER
                val mitText = symboltexte && eintraege.sumOf { breite(it, true).toDouble() } <= platz
                // Passt es auch ohne Texte nicht, bleibt vorne, was neben "Mehr" Platz hat; der Rest geht ins Menue.
                var sichtbar = eintraege
                var rest = emptyList<Knopf>()
                if (!mitText && eintraege.sumOf { breite(it, false).toDouble() } > platz) {
                    var summe = KNOPF_SYMBOL
                    val n = eintraege.indexOfFirst { e -> summe += breite(e, false); summe > platz }.let { if (it < 0) eintraege.size else it }
                    sichtbar = eintraege.take(n).dropLastWhile { it is Trenner }
                    rest = eintraege.drop(n).filterIsInstance<Knopf>()
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    sichtbar.forEach { e ->
                        when (e) {
                            is Trenner -> ToolSeparator()
                            is Knopf -> LeistenKnopf(e, mitText)
                        }
                    }
                    if (rest.isNotEmpty()) MehrKnopf(rest)
                }
            }
        }
    }
}

/** Ein Knopf der Symbolleiste, bei Bedarf mit Aufklappmenue; ohne Text zeigt er seinen Namen beim Ueberfahren. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LeistenKnopf(k: Knopf, mitText: Boolean) {
    var open by remember { mutableStateOf(false) }
    val knopf = @Composable {
        Box {
            ToolItem(k.icon, k.label, enabled = k.enabled, active = k.active, mitText = mitText) { if (k.menu != null) open = true else k.onClick() }
            k.menu?.let { inhalt -> DropdownMenu(expanded = open, onDismissRequest = { open = false }) { inhalt { open = false } } }
        }
    }
    if (mitText) knopf() else TooltipArea(tooltip = { Hinweis(k.label) }, delayMillis = 400) { knopf() }
}

/** "Mehr" am rechten Ende: was nicht in die Leiste passt. Knoepfe mit eigenem Menue zeigen es an derselben Stelle. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MehrKnopf(rest: List<Knopf>) {
    var open by remember { mutableStateOf(false) }
    var unter by remember { mutableStateOf<Knopf?>(null) }
    val schliessen = { open = false; unter = null }
    val label = stringResource(Res.string.desk_more)
    TooltipArea(tooltip = { Hinweis(label) }, delayMillis = 400) {
        Box {
            ToolItem(Icons.Default.MoreVert, label, mitText = false) { open = true }
            DropdownMenu(expanded = open, onDismissRequest = schliessen) {
                val u = unter
                if (u?.menu != null) u.menu.invoke(schliessen)
                else rest.forEach { k ->
                    DropdownMenuItem(
                        text = { Text(if (k.menu != null) "${k.label}  ▸" else k.label) },
                        leadingIcon = { Icon(k.icon, contentDescription = null, Modifier.size(18.dp)) },
                        enabled = k.enabled,
                        onClick = { if (k.menu != null) unter = k else { schliessen(); k.onClick() } },
                    )
                }
            }
        }
    }
}

@Composable
private fun Hinweis(text: String) {
    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, shadowElevation = 2.dp) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inverseOnSurface)
    }
}

/** Merkliste: die gemerkten Personen; Klick macht zur Zentralperson, Doppelklick oeffnet den Eingabedialog. */
@Composable
private fun MerklisteFenster(state: UiState, viewModel: AppViewModel, openSheet: (String) -> Unit, onClose: () -> Unit) {
    androidx.compose.ui.window.DialogWindow(
        onCloseRequest = onClose, title = stringResource(Res.string.desk_bookmarks),
        state = androidx.compose.ui.window.rememberDialogState(width = 420.dp, height = 560.dp),
        onPreviewKeyEvent = { e -> if (e.key == Key.Escape) { onClose(); true } else false },
    ) {
        DeskTheme {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                val manager = state.tree?.role == "manager"
                val stufe30 = viewModel.tasksSupported
                if (state.bookmarks.isEmpty() && state.treeBookmarks.isEmpty()) {
                    Text(stringResource(Res.string.desk_bookmarks_empty), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val list = rememberLazyListState()
                    @Composable
                    fun zeile(p: Person, entfernen: (() -> Unit)?) {
                        Row(
                            Modifier.fillMaxWidth().fokusRahmen()
                                .combinedClickable(onClick = { viewModel.setRoot(p.xref) }, onDoubleClick = { openSheet(p.xref) })
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Portrait(p, Modifier.size(32.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(registerName(p, stringResource(Res.string.person_private), stringResource(Res.string.person_no_name)), style = MaterialTheme.typography.bodyMedium, fontWeight = if (p.xref == state.root) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (p.lifespan.isNotBlank()) Text(p.lifespan, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (p.note.isNotBlank()) Text(p.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (entfernen != null) TextButton(onClick = entfernen) { Text(stringResource(Res.string.desk_remove)) }
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.fillMaxSize(), state = list) {
                            // Ab Stufe 30 zwei Gruppen: meine Merkliste und die Favoriten des Stammbaums (webtrees "Meine Seite")
                            if (stufe30 && state.treeBookmarks.isNotEmpty()) item { Text(stringResource(Res.string.desk_bookmarks_mine), Modifier.padding(10.dp, 8.dp, 10.dp, 2.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(state.bookmarks, key = { "m" + it.xref }) { p -> zeile(p) { viewModel.toggleBookmark(p.xref) } }
                            if (stufe30 && state.treeBookmarks.isNotEmpty()) {
                                item { Text(stringResource(Res.string.desk_bookmarks_tree), Modifier.padding(10.dp, 12.dp, 10.dp, 2.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                items(state.treeBookmarks, key = { "t" + it.xref }) { p -> zeile(p, if (manager) ({ viewModel.toggleTreeBookmark(p.xref) }) else null) }
                            }
                        }
                        ListenLeiste(list)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolItem(icon: ImageVector, label: String, enabled: Boolean = true, active: Boolean = false, mitText: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint = if (!enabled) colors.onSurfaceVariant.copy(alpha = 0.4f) else if (active) colors.primary else colors.onSurface
    Column(
        Modifier
            .background(if (active) colors.surface else colors.surfaceVariant, MaterialTheme.shapes.extraSmall)
            .fokusRahmen()
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .width(if (mitText) 54.dp else 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = if (mitText) null else label, Modifier.size(22.dp), tint = tint)
        if (mitText) Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
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

/** Zurueck und Vor zwischen Zentralpersonen. */
class DeskNav(val kannZurueck: Boolean, val kannVor: Boolean, val zurueck: () -> Unit, val vor: () -> Unit)

/** Druck und PDF fuer die Zentralperson: Personenblatt und Ahnentafel. */
class DeskDruck(private val state: UiState, private val viewModel: AppViewModel, private val appName: String) {
    private val baum get() = state.tree?.title.orEmpty()
    private fun blatt(pdf: Boolean) {
        val d = state.detail?.takeIf { it.person.xref == state.root } ?: state.detail ?: return
        val titel = Texte.t(Res.string.desk_title_sheet, d.person.name)
        val doc = listenPdf(personenblattZeilen(d), appName, baum)
        if (pdf) alsPdf(doc, titel) else drucken(doc, titel)
    }
    fun personenblatt() = blatt(false)
    fun personenblattPdf() = blatt(true)
}

/** Personentext in die Zwischenablage - fuer E-Mail oder Textverarbeitung. */
fun personentextKopieren(d: de.bgghome.webtrees.nativ.api.IndividualDetail) {
    val text = personenblattZeilen(d).joinToString("\n") { "  ".repeat(it.einzug) + it.text }
    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
}

// ── Baumwahl in der Symbolleiste ─────────────────────────────────────

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
                    text = {
                        val anzahl = if (candidate.individuals > 0) "  (" + stringResource(Res.string.tree_people_count, candidate.individuals) + ")" else ""
                        Text(candidate.title + anzahl, fontWeight = if (candidate.name == state.tree?.name) FontWeight.SemiBold else FontWeight.Normal)
                    },
                    onClick = { open = false; if (candidate.name != state.tree?.name) viewModel.chooseTree(candidate) },
                )
            }
        }
    }
}

// ── Personenliste links ──────────────────────────────────────────────

@Composable
internal fun PersonIndex(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit, focus: FocusRequester, modifier: Modifier) {
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
    // Eigener Textzustand: kaeme der Text verzoegert aus dem ViewModel zurueck, spraenge der Cursor an den Anfang.
    // Nur wenn der Zustand von aussen einen anderen Text bringt (Leeren), folgen wir ihm.
    var feld by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(query)) }
    LaunchedEffect(query) { if (query != feld.text) feld = androidx.compose.ui.text.input.TextFieldValue(query, androidx.compose.ui.text.TextRange(query.length)) }
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
                value = feld, onValueChange = { feld = it; onChange(it.text) }, singleLine = true,
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
    val merken = stringResource(Res.string.desk_bookmark_add); val merkWeg = stringResource(Res.string.desk_bookmark_remove)
    val anfuegen = stringResource(Res.string.desk_link_existing)
    ContextMenuArea(items = {
        if (person.isPrivate) emptyList() else listOf(
            ContextMenuItem(makeRoot) { viewModel.setRoot(person.xref) },
            ContextMenuItem(profile) { viewModel.select(person.xref) },
            if (viewModel.bookmarksSupported) ContextMenuItem(if (viewModel.isBookmarked(person.xref)) merkWeg else merken) { viewModel.toggleBookmark(person.xref) } else null,
            ContextMenuItem(web) { openWeb(person.url) },
            if (viewModel.uiState.value.tree?.canEdit == true) ContextMenuItem(anfuegen) { Anfuegewahl.person = person } else null,
        ).filterNotNull()
    }) {
      // Beim Darueberfahren: Bild und Lebensdaten
      TooltipArea(tooltip = { if (!person.isPrivate) PersonKurzinfo(person) }, delayMillis = 600) {
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
}

/** Kleine Karte fuer Tooltips: Bild, Name, Geburt, Tod, Beruf. */
@Composable
internal fun PersonKurzinfo(person: Person) {
    fun zeile(z: String, e: de.bgghome.webtrees.nativ.api.EventJson?): String? {
        val d = e?.date?.text.orEmpty(); val o = e?.place?.name.orEmpty()
        return if (d.isBlank() && o.isBlank()) null else "$z " + listOf(d, o).filter(String::isNotBlank).joinToString(", ")
    }
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp, shape = MaterialTheme.shapes.small, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(10.dp).widthIn(max = 420.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(person, 56.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(person.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                listOfNotNull(zeile("*", person.birth ?: person.chr), zeile("†", person.death ?: person.buri), person.occupation?.takeIf { it.isNotBlank() })
                    .forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
            fullscreen = state.treeFullscreen,
            initialScale = 1f,
            compact = false,
            onToggleFullscreen = { viewModel.setTreeFullscreen(!state.treeFullscreen) },
            onPerson = { viewModel.select(it.xref) },
            onPlus = { viewModel.requestAddRelative(it.xref) },
            onExpand = viewModel::expandAncestors,
            onSecondary = { person, at -> viewModel.select(person.xref); menu = person to at },
            onOpen = { viewModel.setRoot(it.xref) },
            showFullscreen = true,
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
    val parts = listOfNotNull(host.takeIf { it.isNotEmpty() }, state.tree?.title, state.tree?.individuals?.takeIf { it > 0 }?.let { stringResource(Res.string.desk_status_persons, it) },
        who, state.tree?.role?.let { roleLabel(it) },
        if (WtClient.isCleartext(state.baseUrl)) stringResource(Res.string.http_home_hint) else null)
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(parts.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
            Text("$appName $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UeberDialog(state: UiState, viewModel: AppViewModel, appName: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = { onClose() },
        title = { Text(stringResource(Res.string.desk_about, appName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Mit Build-Nummer wie im Namen der exe ("1.26.157"), damit man sieht, welches Paket laeuft
                val version = viewModel.versionName + (System.getProperty("wtand.build")?.let { ".$it" } ?: "")
                Text(stringResource(Res.string.desk_about_text, appName, version))
                Text(stringResource(Res.string.app_author), style = MaterialTheme.typography.bodySmall)
                state.info?.module?.takeIf { it.isNotEmpty() }?.let {
                    Text(stringResource(Res.string.menu_about_module, "api4webtrees $it"), style = MaterialTheme.typography.bodySmall)
                }
                state.archive?.modul?.takeIf { it.isNotEmpty() }?.let {
                    Text(stringResource(Res.string.menu_about_module, "Sammlungen $it"), style = MaterialTheme.typography.bodySmall)
                }
                // Stammbaum auf diesem PC (Stufe 4): mitgeliefertes webtrees nennen, sonst was im Paket fehlt
                val wt = LokalBetrieb.webtreesVersion()
                Text(
                    if (wt != null) stringResource(Res.string.desk_about_local, wt) else stringResource(Res.string.desk_about_local_missing, LokalBetrieb.fehlt()),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(stringResource(Res.string.desk_about_unofficial, appName), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onClose() }) { Text(stringResource(Res.string.action_close)) } },
    )
}
