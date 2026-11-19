package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.ui.meldung

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.debounce
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import coil3.compose.AsyncImage
import de.bgghome.webtrees.nativ.api.ArchiveCollection
import de.bgghome.webtrees.nativ.api.ArchiveEntry
import de.bgghome.webtrees.nativ.api.ArchiveOverview
import de.bgghome.webtrees.nativ.api.ArchiveUploadRequest
import de.bgghome.webtrees.nativ.api.CollectionPage
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.ExifEditDialog
import de.bgghome.webtrees.nativ.ui.LinkBadge
import de.bgghome.webtrees.nativ.ui.PdfTarget
import de.bgghome.webtrees.nativ.ui.PdfViewer
import de.bgghome.webtrees.nativ.ui.PhotoViewer
import de.bgghome.webtrees.nativ.ui.UiState
import de.bgghome.webtrees.nativ.ui.ViewerSource
import de.bgghome.webtrees.nativ.ui.ViewerState
import de.bgghome.webtrees.nativ.ui.WtAlertDialog
import de.bgghome.webtrees.nativ.ui.canEditExif
import de.bgghome.webtrees.nativ.ui.personSuggestions
import de.bgghome.webtrees.nativ.ui.probeArchive
import de.bgghome.webtrees.nativ.ui.setRoot
import de.bgghome.webtrees.nativ.ui.viewerItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jetbrains.compose.resources.stringResource
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/*
 * Das Archiv des Moduls Sammlungen als eigenes Fenster (Ansicht › Archiv, Strg+9): links die Sammlungen, in der Mitte
 * das Raster der gewaehlten, rechts die Angaben zur gewaehlten Datei. Auswahl mit Strg/Umschalt, Pfeiltasten, Enter
 * oeffnet, Rechtsklick fuer die Handgriffe. Dateien hineinziehen oder "Dateien hinzufuegen" legt die Originale ins
 * Archiv (Route hochladen des Moduls) - an den Stammbaum haengt sich dabei nichts. Lesen, Schreiben und Rechte kommen
 * allein vom Modul; der Bereich Fotos › Archiv im Hauptfenster bleibt daneben bestehen.
 */

/** Was links gewaehlt ist: eine Sammlung des Moduls oder die nicht eingebundenen Medien eines Typs. */
private data class ArchivPunkt(val slug: String, val typ: String = "")

/** Kantenlaenge der Vorschaubilder je Stufe (klein, mittel, gross). */
private val KANTEN = listOf(96, 150, 230)

/** So viele Eintraege holt das Fenster je Seite (das Modul erlaubt bis 200). */
private const val ARCHIV_SEITE = 120

private fun schluessel(e: ArchiveEntry) = e.pfad ?: e.original.ifEmpty { e.datei }

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun ArchivFenster(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit, onClose: () -> Unit) {
    val tree = state.tree?.name ?: return
    val archive = state.archive ?: return
    val client = viewModel.client
    val scope = rememberCoroutineScope()

    var wahl by remember { mutableStateOf(archive.sammlungen.firstOrNull()?.let { ArchivPunkt(it.slug) }) }
    val eintraege = remember { mutableStateListOf<ArchiveEntry>() }
    var seite by remember { mutableStateOf<CollectionPage?>(null) }
    var naechste by remember { mutableStateOf<Int?>(null) }
    var laden by remember { mutableStateOf(false) }
    var neu by remember { mutableIntStateOf(0) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var meldung by remember { mutableStateOf<String?>(null) }
    val auswahl = remember { mutableStateListOf<String>() }
    var fokus by remember { mutableStateOf<Int?>(null) }
    var stufe by remember { mutableIntStateOf((DeskLayout.prefs.getString("archiv_groesse", null)?.toIntOrNull() ?: 1).coerceIn(0, 2)) }
    var betrachter by remember { mutableStateOf<ViewerState?>(null) }
    var pdf by remember { mutableStateOf<PdfTarget?>(null) }
    var bearbeiten by remember { mutableStateOf<ArchiveEntry?>(null) }
    var hochladen by remember { mutableStateOf<List<File>?>(null) }
    // Gesichter (Stufe 5): ansehen kann jeder, markieren nur, wem das Modul es erlaubt
    var gesichter by remember { mutableStateOf<ArchiveEntry?>(null) }
    val gesichterDa = archive.api >= 5
    val darfBereiche = gesichterDa && archive.darfBereiche
    var ziehtDarueber by remember { mutableStateOf(false) }
    // Strg/Umschalt beim Mausklick: combinedClickable kennt die Tasten nicht, darum beim Druecken merken
    var tasten by remember { mutableStateOf<PointerKeyboardModifiers?>(null) }
    val darfHochladen = archive.api >= 2 && archive.darfHochladen
    // Stufe 6: Verwaltung aus der App - ordnen (Verwalter), verwalten und Einstellungen (Administrator)
    val stufe6 = archive.api >= 6
    val darfOrdnen = stufe6 && archive.darfOrdnen
    val darfVerwalten = stufe6 && archive.darfVerwalten
    var neuVerw by remember { mutableIntStateOf(0) }
    val verwaltung by androidx.compose.runtime.produceState<de.bgghome.webtrees.nativ.api.Verwaltung?>(null, tree, neuVerw, darfOrdnen) {
        value = if (!darfOrdnen) null else withContext(Dispatchers.IO) { runCatching { client.archiveAdmin(tree).verwaltung }.getOrNull() }
    }
    var sammlungBearbeiten by remember { mutableStateOf<de.bgghome.webtrees.nativ.api.SammlungDaten?>(null) }
    var sammlungNeu by remember { mutableStateOf(false) }
    var sammlungLoeschen by remember { mutableStateOf<de.bgghome.webtrees.nativ.api.SammlungDaten?>(null) }
    var umbenennen by remember { mutableStateOf<ArchiveEntry?>(null) }
    var albenFuer by remember { mutableStateOf<List<ArchiveEntry>?>(null) }
    var postkarteBearbeiten by remember { mutableStateOf<ArchiveEntry?>(null) }
    var einstellungen by remember { mutableStateOf(false) }
    fun nachVerwaltung() { viewModel.probeArchive(); neuVerw++; neu++ }
    fun daten(slug: String) = verwaltung?.sammlungen?.firstOrNull { it.slug == slug }
    val rasterFokus = remember { FocusRequester() }

    suspend fun seiteLaden(w: ArchivPunkt, nr: Int) {
        laden = true
        runCatching { withContext(Dispatchers.IO) { client.collection(tree, w.slug, w.typ, nr, ARCHIV_SEITE) } }
            .onSuccess { p ->
                if (wahl != w) return@onSuccess
                seite = p
                // weitere (Video, Ton, Dokumente) kommen nicht seitenweise - nur mit der ersten Seite nehmen
                eintraege += p.eintraege + (if (nr == 1) p.weitere else emptyList())
                naechste = if (p.seite < p.seiten) p.seite + 1 else null
            }
            .onFailure { fehler = meldung(it) }
        laden = false
    }
    LaunchedEffect(wahl, neu) {
        eintraege.clear(); auswahl.clear(); fokus = null; seite = null; naechste = null; fehler = null
        wahl?.let { seiteLaden(it, 1) }
    }
    val raster = rememberLazyGridState()
    // Nachladen, sobald das Ende des Rasters in Sicht kommt
    LaunchedEffect(raster, wahl) {
        snapshotFlow { raster.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }.collect { letzter ->
            val w = wahl; val n = naechste
            if (w != null && n != null && !laden && letzter >= eintraege.size - 12) seiteLaden(w, n)
        }
    }

    val gewaehlte = eintraege.filter { schluessel(it) in auswahl }
    val einer = gewaehlte.singleOrNull()

    // Postkarte: an sich selbst erkennbar oder weil die offene Sammlung eine Postkarten-Sammlung ist
    fun postkarte(e: ArchiveEntry) = e.istPostkarte || seite?.ansicht == de.bgghome.webtrees.nativ.ui.ANSICHT_POSTKARTE
    fun oeffnen(e: ArchiveEntry) {
        when {
            // Postkarte: im Betrachter Vorder- und Rueckseite, blaettern = wenden
            e.istBild && e.rueckseite != null -> betrachter = ViewerState(listOf(viewerItem(e).copy(text = e.motiv), viewerItem(e.rueckseite!!).copy(text = e.transkription)), 0, ViewerSource.Collection)
            e.istBild -> {
                val bilder = eintraege.filter { it.istBild }
                betrachter = ViewerState(bilder.map(::viewerItem), bilder.indexOf(e).coerceAtLeast(0), ViewerSource.Collection)
            }
            e.format.equals("pdf", ignoreCase = true) -> pdf = PdfTarget(e.original, e.bildunterschrift.ifEmpty { e.datei }, e.seite)
            else -> openWeb(e.seite ?: e.original)
        }
    }
    fun waehlen(i: Int, strg: Boolean, umschalt: Boolean) {
        val k = schluessel(eintraege[i])
        val von = fokus
        when {
            umschalt && von != null -> {
                if (!strg) auswahl.clear()
                (minOf(von, i)..maxOf(von, i)).map { schluessel(eintraege[it]) }.filter { it !in auswahl }.let(auswahl::addAll)
            }
            strg -> { if (k in auswahl) auswahl.remove(k) else auswahl.add(k); fokus = i }
            else -> { auswahl.clear(); auswahl.add(k); fokus = i }
        }
    }
    fun kopienSpeichern(liste: List<ArchiveEntry>) {
        val ordner = ordnerWaehlen() ?: return
        scope.launch {
            meldung = null
            val n = runCatching { withContext(Dispatchers.IO) { kopien(client, liste, ordner) } }.getOrElse { fehler = meldung(it); return@launch }
            meldung = de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_archive_downloaded, n, ordner.absolutePath)
        }
    }
    fun pfadeKopieren(liste: List<ArchiveEntry>) =
        runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(liste.joinToString("\n") { it.pfad ?: it.original }), null) }

    // Dateien aus dem Dateimanager hineinziehen; waehrend des Ziehens ist das Raster umrandet
    val ablage = remember(darfHochladen) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { ziehtDarueber = true }
            override fun onExited(event: DragAndDropEvent) { ziehtDarueber = false }
            override fun onEnded(event: DragAndDropEvent) { ziehtDarueber = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                ziehtDarueber = false
                val liste = event.dragData() as? DragData.FilesList ?: return false
                val dateien = liste.readFiles().mapNotNull { runCatching { File(java.net.URI(it)) }.getOrNull() }.filter { it.isFile }
                if (dateien.isNotEmpty()) hochladen = dateien
                return dateien.isNotEmpty()
            }
        }
    }

    Window(
        onCloseRequest = onClose, title = stringResource(Res.string.desk_archive_window) + " – " + state.tree?.title.orEmpty(),
        state = arbeitsfenster("fenster_archiv", 1280, 820),
        onPreviewKeyEvent = { e ->
            when {
                e.type != KeyEventType.KeyDown -> false
                e.key == Key.Escape && betrachter != null -> { betrachter = null; true }
                e.key == Key.Escape && pdf != null -> { pdf = null; true }
                e.key == Key.Escape -> { onClose(); true }
                e.key == Key.F1 -> { Hilfe.oeffnen("archiv"); true }
                else -> false
            }
        },
    ) {
        DeskTheme {
            val farben = MaterialTheme.colorScheme
            Box(Modifier.fillMaxSize().background(farben.background)) {
                Row(Modifier.fillMaxSize()) {
                    // ── Sammlungen ──
                    val links = remember { mutableStateOf(DeskLayout.prefs.getString("archiv_liste", null)?.toFloatOrNull() ?: 270f) }
                    Column(Modifier.width(links.value.dp).fillMaxHeight().background(farben.surface)) {
                        Box(Modifier.weight(1f)) {
                            SammlungenListe(archive, wahl, inaktive = verwaltung?.sammlungen?.filter { !it.aktiv }.orEmpty(),
                                onBearbeiten = if (darfVerwalten) ({ slug -> sammlungBearbeiten = daten(slug) }) else null,
                                onSichtbar = if (darfOrdnen) ({ slug, an ->
                                    scope.launch {
                                        runCatching { withContext(Dispatchers.IO) { client.archiveVisible(tree, slug, an) } }
                                            .onSuccess { nachVerwaltung() }.onFailure { fehler = explainOf(it) }
                                    }
                                }) else null,
                                onLoeschen = if (darfVerwalten) ({ slug -> sammlungLoeschen = daten(slug) }) else null,
                            ) { wahl = it }
                        }
                        if (darfVerwalten || (stufe6 && archive.darfEinstellungen)) Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (darfVerwalten) TextButton(onClick = { sammlungNeu = true }) { Text(stringResource(Res.string.desk_archive_new_collection)) }
                            if (stufe6 && archive.darfEinstellungen) TextButton(onClick = { einstellungen = true }) { Text(stringResource(Res.string.desk_archive_settings)) }
                        }
                        HorizontalDivider(color = farben.outlineVariant)
                        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End).padding(4.dp)) { Text(stringResource(Res.string.action_close)) }
                    }
                    Trenner(links, "archiv_liste", 200f, 480f)

                    // ── Raster ──
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        val s = seite
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(s?.name.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val zeile = listOfNotNull(
                                    s?.let { stringResource(Res.string.desk_archive_count, maxOf(it.anzahl, eintraege.size)) },
                                    auswahl.size.takeIf { it > 1 }?.let { stringResource(Res.string.desk_archive_selected, it) },
                                    s?.beschreibung?.takeIf { it.isNotBlank() },
                                ).joinToString(" · ")
                                Text(zeile, style = MaterialTheme.typography.labelMedium, color = farben.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Tipp(stringResource(Res.string.desk_archive_size)) {
                                Row(Modifier.border(1.dp, farben.outlineVariant, MaterialTheme.shapes.small)) {
                                    listOf(Res.string.desk_archive_size_small, Res.string.desk_archive_size_medium, Res.string.desk_archive_size_large).forEachIndexed { i, t ->
                                        Text(stringResource(t), Modifier.background(if (stufe == i) farben.secondaryContainer else Color.Transparent, MaterialTheme.shapes.small)
                                            .clickable { stufe = i; DeskLayout.prefs.putString("archiv_groesse", i.toString()) }.padding(horizontal = 10.dp, vertical = 5.dp),
                                            style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            if (darfHochladen) OutlinedButton(onClick = { dateienWaehlen()?.let { hochladen = it } }, shape = MaterialTheme.shapes.small) {
                                Text(stringResource(Res.string.desk_archive_add))
                            }
                            if (archive.galerie.isNotBlank()) TextButton(onClick = { openWeb(archive.galerie) }) { Text(stringResource(Res.string.viewer_open_web)) }
                        }
                        HorizontalDivider(color = farben.outlineVariant)
                        BoxWithConstraints(
                            (if (darfHochladen) Modifier.dragAndDropTarget(shouldStartDragAndDrop = { true }, target = ablage) else Modifier)
                                .weight(1f).fillMaxWidth()
                                .border(if (ziehtDarueber) 2.dp else 0.dp, if (ziehtDarueber) farben.primary else Color.Transparent),
                        ) {
                            val kante = KANTEN[stufe]
                            val spalten = maxOf(1, ((maxWidth.value - 16) / (kante + 10)).toInt())
                            when {
                                wahl == null -> Text(stringResource(Res.string.desk_archive_choose), Modifier.padding(24.dp), color = farben.onSurfaceVariant)
                                fehler != null && eintraege.isEmpty() -> Text(fehler!!, Modifier.padding(24.dp), color = farben.error)
                                laden && eintraege.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                                eintraege.isEmpty() -> Text(stringResource(Res.string.archive_none), Modifier.padding(24.dp), color = farben.onSurfaceVariant)
                                else -> {
                                    LazyVerticalGrid(
                                        GridCells.Fixed(spalten), state = raster,
                                        modifier = Modifier.fillMaxSize().focusRequester(rasterFokus).focusable()
                                            .onPreviewKeyEvent { e ->
                                                if (e.type != KeyEventType.KeyDown || eintraege.isEmpty()) return@onPreviewKeyEvent false
                                                val strg = e.isCtrlPressed || e.isMetaPressed
                                                val ziel = (fokus ?: -1).let { f ->
                                                    when (e.key) {
                                                        Key.DirectionRight -> f + 1
                                                        Key.DirectionLeft -> f - 1
                                                        Key.DirectionDown -> if (f < 0) 0 else f + spalten
                                                        Key.DirectionUp -> f - spalten
                                                        Key.MoveHome -> 0
                                                        Key.MoveEnd -> eintraege.lastIndex
                                                        else -> null
                                                    }
                                                }
                                                when {
                                                    ziel != null -> {
                                                        val i = ziel.coerceIn(0, eintraege.lastIndex)
                                                        waehlen(i, false, e.isShiftPressed)
                                                        if (e.isShiftPressed) fokus = i
                                                        scope.launch { raster.animateScrollToItem(i) }
                                                        true
                                                    }
                                                    e.key == Key.Enter -> { fokus?.let { eintraege.getOrNull(it) }?.let(::oeffnen); true }
                                                    e.key == Key.A && strg -> { auswahl.clear(); auswahl.addAll(eintraege.map(::schluessel)); true }
                                                    e.key == Key.C && strg && gewaehlte.isNotEmpty() -> { pfadeKopieren(gewaehlte); true }
                                                    else -> false
                                                }
                                            },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        itemsIndexed(eintraege, key = { _, e -> schluessel(e) }) { i, e ->
                                            val markiert = schluessel(e) in auswahl
                                            ContextMenuArea(items = {
                                                // Rechtsklick auf eine nicht gewaehlte Datei waehlt sie allein
                                                val ziel = if (markiert) gewaehlte else listOf(e)
                                                buildList {
                                                    if (ziel.size == 1) add(ContextMenuItem(de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_archive_open)) { oeffnen(e) })
                                                    if (ziel.size == 1 && e.istBild && e.pfad != null && gesichterDa && (darfBereiche || e.bereiche.isNotEmpty()))
                                                        add(ContextMenuItem(de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_faces_menu)) { gesichter = e })
                                                    if (ziel.size == 1 && e.istBild && e.pfad != null && viewModel.canEditExif && !postkarte(e))
                                                        add(ContextMenuItem(de.bgghome.webtrees.nativ.Texte.t(Res.string.viewer_edit_exif) + " …") { bearbeiten = e })
                                                    if (ziel.size == 1 && e.istBild && e.pfad != null && stufe6 && viewModel.canEditExif && postkarte(e))
                                                        add(ContextMenuItem(de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_postcard_edit)) { postkarteBearbeiten = e })
                                                    if (ziel.size == 1 && e.pfad != null && darfOrdnen && e.rueckseite == null)
                                                        add(ContextMenuItem(de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_archive_rename)) { umbenennen = e })
                                                    if (darfOrdnen && ziel.all { it.pfad != null })
                                                        add(ContextMenuItem(de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_archive_albums)) { albenFuer = ziel })
                                                    if (darfOrdnen && archive.sammlungen.firstOrNull { it.slug == wahl?.slug }?.album == true)
                                                        add(ContextMenuItem(de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_archive_remove_from)) {
                                                            val slug = wahl?.slug ?: return@ContextMenuItem
                                                            scope.launch {
                                                                runCatching { withContext(Dispatchers.IO) { ziel.forEach { z -> z.pfad?.let { client.archiveAssign(tree, it, slug, false) } } } }
                                                                    .onSuccess { nachVerwaltung() }.onFailure { fehler = explainOf(it) }
                                                            }
                                                        })
                                                    add(ContextMenuItem(de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_archive_copy_path)) { pfadeKopieren(ziel) })
                                                    add(ContextMenuItem(de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_archive_download)) { kopienSpeichern(ziel) })
                                                    if (ziel.size == 1) (e.seite ?: e.original).takeIf { it.isNotBlank() }?.let { url ->
                                                        add(ContextMenuItem(de.bgghome.webtrees.nativ.Texte.t(Res.string.viewer_open_web)) { openWeb(url) })
                                                    }
                                                }
                                            }) {
                                                Tipp(listOf(e.bildunterschrift.ifEmpty { e.datei }, e.datum, e.personen.joinToString(", ") { it.name }).filter { it.isNotBlank() }.joinToString("\n")) {
                                                    Kachel(e, kante, markiert,
                                                        Modifier.onPointerEvent(PointerEventType.Press) { tasten = it.keyboardModifiers }
                                                            .combinedClickable(
                                                                onClick = {
                                                                    val t = tasten
                                                                    waehlen(i, t?.isCtrlPressed == true || t?.isMetaPressed == true, t?.isShiftPressed == true)
                                                                    runCatching { rasterFokus.requestFocus() }
                                                                },
                                                                onDoubleClick = { waehlen(i, false, false); oeffnen(e) },
                                                            ))
                                                }
                                            }
                                        }
                                    }
                                    Box(Modifier.matchParentSize()) { androidx.compose.foundation.VerticalScrollbar(androidx.compose.foundation.rememberScrollbarAdapter(raster), Modifier.align(Alignment.CenterEnd).fillMaxHeight()) }
                                }
                            }
                            if (laden && eintraege.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.BottomCenter))
                        }
                        val unten = meldung ?: fehler?.takeIf { eintraege.isNotEmpty() }
                        Text(unten ?: if (darfHochladen) stringResource(Res.string.desk_archive_drop_hint) else "",
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall,
                            color = if (unten != null && unten == fehler) farben.error else farben.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }

                    // ── Angaben zur Datei ──
                    val rechts = remember { mutableStateOf(DeskLayout.prefs.getString("archiv_detail", null)?.toFloatOrNull() ?: 330f) }
                    Trenner(rechts, "archiv_detail", 240f, 560f, rechts = true)
                    Box(Modifier.width(rechts.value.dp).fillMaxHeight().background(farben.surface)) {
                        when {
                            einer != null -> DateiAngaben(einer, viewModel.canEditExif && einer.istBild && einer.pfad != null, postkarte = postkarte(einer),
                                onAbgleich = { u -> scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { client.writeExif(tree, einer.pfad.orEmpty(), de.bgghome.webtrees.nativ.data.ArchivAbgleich.anfrage(einer, u)) } }
                                        .onSuccess { r -> r.eintrag?.let { ersetzen(eintraege, it) }; meldung = de.bgghome.webtrees.nativ.Texte.t(Res.string.msg_exif_saved) }
                                        .onFailure { fehler = meldung(it) }
                                } },
                                onOeffnen = { oeffnen(einer) }, onBearbeiten = { bearbeiten = einer },
                                onPostkarte = if (stufe6 && viewModel.canEditExif && einer.istBild && einer.pfad != null) ({ postkarteBearbeiten = einer }) else null,
                                onGesichter = if (gesichterDa && einer.istBild && einer.pfad != null && (darfBereiche || einer.bereiche.isNotEmpty())) ({ gesichter = einer }) else null,
                                onPerson = { viewModel.setRoot(it) }, onKopien = { kopienSpeichern(listOf(einer)) })
                            gewaehlte.size > 1 -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(Res.string.desk_archive_selected, gewaehlte.size), style = MaterialTheme.typography.titleSmall)
                                OutlinedButton(onClick = { kopienSpeichern(gewaehlte) }, shape = MaterialTheme.shapes.small) { Text(stringResource(Res.string.desk_archive_download)) }
                                OutlinedButton(onClick = { pfadeKopieren(gewaehlte) }, shape = MaterialTheme.shapes.small) { Text(stringResource(Res.string.desk_archive_copy_path)) }
                            }
                        }
                    }
                }

                // Betrachter liegen ueber dem Fenster, wie im Hauptfenster
                pdf?.let { PdfViewer(it, onClose = { pdf = null }, onOpenWeb = openWeb) }
                betrachter?.let { v ->
                    PhotoViewer(v, onIndex = { betrachter = betrachter?.copy(index = it) }, onClose = { betrachter = null }, onOpenWeb = openWeb,
                        canEdit = { viewModel.canEditExif && it.entry?.let { e -> !postkarte(e) } == true }, editing = bearbeiten.takeIf { betrachter != null },
                        suggestPersons = viewModel.personSuggestions(),
                        onEdit = { item -> bearbeiten = item.entry }, onCancelEdit = { bearbeiten = null },
                        onSaveExif = { entry, req ->
                            bearbeiten = null
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { client.writeExif(tree, entry.pfad.orEmpty(), req) } }
                                    .onSuccess { r -> r.eintrag?.let { n -> ersetzen(eintraege, n); betrachter = betrachter?.let { b -> b.copy(items = b.items.map { x -> if (x.entry?.pfad == n.pfad) viewerItem(n) else x }) } } }
                                    .onFailure { fehler = meldung(it) }
                            }
                        })
                }
            }

            // Beschriftung aendern ausserhalb des Betrachters (Rechtsklick, rechte Spalte)
            if (betrachter == null) bearbeiten?.let { entry ->
                ExifEditDialog(entry, viewModel.personSuggestions(), onDismiss = { bearbeiten = null }, onSave = { req ->
                    bearbeiten = null
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { client.writeExif(tree, entry.pfad.orEmpty(), req) } }
                            .onSuccess { r -> r.eintrag?.let { ersetzen(eintraege, it) }; meldung = de.bgghome.webtrees.nativ.Texte.t(Res.string.msg_exif_saved) }
                            .onFailure { fehler = meldung(it) }
                    }
                })
            }
            // ── Verwaltung (Stufe 6) ──
            val auswahlListen = verwaltung?.auswahl ?: de.bgghome.webtrees.nativ.api.VerwaltungsAuswahl()
            if (sammlungNeu) SammlungDialog(tree, client, null, auswahlListen, onDismiss = { sammlungNeu = false },
                onGespeichert = { slug -> sammlungNeu = false; nachVerwaltung(); wahl = ArchivPunkt(slug) })
            sammlungBearbeiten?.let { d ->
                SammlungDialog(tree, client, d, auswahlListen, onDismiss = { sammlungBearbeiten = null }, onGespeichert = { sammlungBearbeiten = null; nachVerwaltung() })
            }
            sammlungLoeschen?.let { d ->
                de.bgghome.webtrees.nativ.ui.ConfirmDialog(
                    title = stringResource(Res.string.desk_archive_delete_collection).removeSuffix(" …"),
                    text = stringResource(Res.string.desk_archive_delete_text, d.name.ifBlank { d.slug }),
                    confirm = stringResource(Res.string.desk_archive_delete_collection).removeSuffix(" …"), onDismiss = { sammlungLoeschen = null },
                    onConfirm = {
                        sammlungLoeschen = null
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { client.archiveDeleteCollection(tree, d.slug) } }
                                .onSuccess { if (wahl?.slug == d.slug) wahl = null; nachVerwaltung() }.onFailure { fehler = explainOf(it) }
                        }
                    },
                )
            }
            umbenennen?.let { e ->
                UmbenennenDialog(tree, client, e, onDismiss = { umbenennen = null }, onFertig = { neuE ->
                    umbenennen = null
                    if (neuE != null) { val i = eintraege.indexOfFirst { it.pfad == e.pfad }; if (i >= 0) eintraege[i] = neuE; auswahl.clear(); auswahl.add(schluessel(neuE)) } else neu++
                })
            }
            albenFuer?.let { bilder ->
                AlbenDialog(tree, client, bilder, archive.sammlungen.filter { it.album }, onDismiss = { albenFuer = null }, onGeaendert = { nachVerwaltung() })
            }
            postkarteBearbeiten?.let { e ->
                PostkarteDialog(e, onDismiss = { postkarteBearbeiten = null }, onSave = { a ->
                    postkarteBearbeiten = null
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { client.writePostcard(tree, e.pfad.orEmpty(), a) } }
                            .onSuccess { neu++; meldung = de.bgghome.webtrees.nativ.Texte.t(Res.string.msg_exif_saved) }.onFailure { fehler = explainOf(it) }
                    }
                })
            }
            if (einstellungen) ArchivEinstellungenDialog(tree, client, onDismiss = { einstellungen = false })
            gesichter?.let { e ->
                GesichterFenster(e, tree, viewModel, bearbeiten = darfBereiche,
                    onGespeichert = { neu -> ersetzen(eintraege, neu); gesichter = neu; meldung = de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_faces_saved) },
                    onClose = { gesichter = null })
            }
            hochladen?.let { dateien ->
                val s = seite
                HochladenDialog(tree, client, archive, dateien,
                    ordner = s?.takeIf { it.art == "ordner" }?.ordner.orEmpty(),
                    sammlung = s?.takeIf { it.art == "thematisch" }?.slug.orEmpty(),
                    maxUpload = state.info?.maxUpload ?: 0L,
                    onClose = { geaendert ->
                        hochladen = null
                        if (geaendert) { viewModel.probeArchive(); neu++ }
                    })
            }
        }
    }
}

private fun ersetzen(liste: MutableList<ArchiveEntry>, neu: ArchiveEntry) {
    val i = liste.indexOfFirst { it.pfad == neu.pfad }
    if (i >= 0) liste[i] = neu
}

/** Links: Ordner-, thematische und Typ-Sammlungen in der Reihenfolge des Moduls, darunter Medien ohne Person und der freie Bestand. */
@Composable
private fun SammlungenListe(
    archive: ArchiveOverview, wahl: ArchivPunkt?,
    inaktive: List<de.bgghome.webtrees.nativ.api.SammlungDaten> = emptyList(),
    onBearbeiten: ((String) -> Unit)? = null, onSichtbar: ((String, Boolean) -> Unit)? = null, onLoeschen: ((String) -> Unit)? = null,
    onWahl: (ArchivPunkt) -> Unit,
) {
    // Rechtsklick auf eine Sammlung: bearbeiten, aus-/einblenden, loeschen - nur, was die Rechte erlauben
    @Composable
    fun mitMenue(slug: String, aktiv: Boolean, inhalt: @Composable () -> Unit) {
        if (onBearbeiten == null && onSichtbar == null && onLoeschen == null) { inhalt(); return }
        ContextMenuArea(items = {
            buildList {
                onBearbeiten?.let { f -> add(ContextMenuItem(de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_archive_edit_collection)) { f(slug) }) }
                onSichtbar?.let { f -> add(ContextMenuItem(de.bgghome.webtrees.nativ.Texte.t(if (aktiv) Res.string.desk_archive_hide else Res.string.desk_archive_show)) { f(slug, !aktiv) }) }
                onLoeschen?.let { f -> add(ContextMenuItem(de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_archive_delete_collection)) { f(slug) }) }
            }
        }) { inhalt() }
    }
    val farben = MaterialTheme.colorScheme
    val gruppen = listOf("ordner" to Res.string.archive_folders, "thematisch" to Res.string.archive_thematic, "medientyp" to Res.string.archive_by_type)
    LazyColumn(Modifier.fillMaxSize().padding(vertical = 6.dp)) {
        if (archive.sammlungen.isEmpty()) item { Text(stringResource(Res.string.archive_none), Modifier.padding(12.dp), color = farben.onSurfaceVariant) }
        gruppen.forEach { (art, titel) ->
            val liste = archive.sammlungen.filter { it.art == art }
            if (liste.isNotEmpty()) {
                item { Ueberschrift(stringResource(titel)) }
                items(liste, key = { "s:" + it.slug }) { c ->
                    mitMenue(c.slug, true) { Zeile(c.name.ifBlank { c.slug }, c.anzahl, wahl == ArchivPunkt(c.slug), farbe(c)) { onWahl(ArchivPunkt(c.slug)) } }
                }
            }
        }
        // Ausgeblendete Sammlungen (nur fuer Verwalter sichtbar): blass, per Rechtsklick wieder einblenden
        if (inaktive.isNotEmpty()) {
            item { Ueberschrift(stringResource(Res.string.desk_archive_hidden)) }
            items(inaktive, key = { "i:" + it.slug }) { d ->
                mitMenue(d.slug, false) {
                    Box(Modifier.alpha(0.55f)) { Zeile(d.name.ifBlank { d.slug }, d.anzahl, false, farbeAus(d.farbe)) {} }
                }
            }
        }
        if (archive.unverknuepft.isNotEmpty()) {
            item { Ueberschrift(stringResource(Res.string.archive_unlinked)) }
            items(archive.unverknuepft, key = { "u:" + it.typ }) { t ->
                val p = ArchivPunkt("__unlinked__", t.typ)
                Zeile(t.name.ifBlank { t.typ }, t.anzahl, wahl == p, null) { onWahl(p) }
            }
        }
        if (archive.frei.gesamt > 0) item {
            Tipp(stringResource(Res.string.tipp_archive_free)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(stringResource(Res.string.archive_free), style = MaterialTheme.typography.labelLarge, color = farben.onSurfaceVariant)
                    Text(stringResource(Res.string.archive_free_text, archive.frei.dateien, archive.frei.gesamt), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
                }
            }
        }
    }
}

private fun farbe(c: ArchiveCollection): Color? = c.farbe?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Color(0xFF000000 or it) }

@Composable
private fun Ueberschrift(text: String) =
    Text(text, Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 2.dp), style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)

@Composable
private fun Zeile(name: String, anzahl: Int, gewaehlt: Boolean, farbe: Color?, onClick: () -> Unit) {
    val farben = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().fokusRahmen().background(if (gewaehlt) farben.secondaryContainer else Color.Transparent).clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(farbe ?: Color.Transparent, MaterialTheme.shapes.extraSmall))
        Text(name, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            fontWeight = if (gewaehlt) FontWeight.SemiBold else FontWeight.Normal)
        Text(anzahl.toString(), style = MaterialTheme.typography.labelMedium, color = farben.onSurfaceVariant)
    }
}

/** Ein Eintrag im Raster: Vorschau (oder das Format bei Dokumenten), Kennzeichen der Verknuepfung, Bildunterschrift. */
@Composable
private fun Kachel(e: ArchiveEntry, kante: Int, gewaehlt: Boolean, modifier: Modifier) {
    val farben = MaterialTheme.colorScheme
    Column(modifier.background(if (gewaehlt) farben.secondaryContainer else Color.Transparent, MaterialTheme.shapes.small).padding(3.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).border(if (gewaehlt) 2.dp else 1.dp, if (gewaehlt) farben.primary else farben.outlineVariant, MaterialTheme.shapes.extraSmall)
            .background(farben.surfaceVariant.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
            if (e.kachel != null) AsyncImage(model = e.kachel, contentDescription = e.bildunterschrift, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text(e.format.uppercase().ifEmpty { "?" }, style = MaterialTheme.typography.titleMedium, color = farben.onSurfaceVariant)
            if (kante >= 120) LinkBadge(e, Modifier.align(Alignment.BottomStart).padding(4.dp))
            if (e.rueckseite != null) Tipp(stringResource(Res.string.desk_postcard_back)) {
                androidx.compose.material3.Surface(Modifier.align(Alignment.TopEnd).padding(4.dp), shape = MaterialTheme.shapes.small, color = farben.surface.copy(alpha = 0.9f)) {
                    Text("↻", Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (kante >= 120) Text(e.bildunterschrift.ifEmpty { e.datei }, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Rechte Spalte: Vorschau und alles, was das Modul zur Datei weiss. */
@Composable
private fun DateiAngaben(e: ArchiveEntry, darfBearbeiten: Boolean, onOeffnen: () -> Unit, onBearbeiten: () -> Unit, onPerson: (String) -> Unit, onKopien: () -> Unit,
    onGesichter: (() -> Unit)? = null, onPostkarte: (() -> Unit)? = null, postkarte: Boolean = false, onAbgleich: ((de.bgghome.webtrees.nativ.data.ArchivAbgleich.Unterschied) -> Unit)? = null) {
    val farben = MaterialTheme.colorScheme
    // Postkarte: Vorschau wenden
    var hinten by remember(e.pfad) { mutableStateOf(false) }
    val zeige = if (hinten) e.rueckseite ?: e else e
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(220.dp).border(1.dp, farben.outlineVariant).clickable(onClick = onOeffnen), contentAlignment = Alignment.Center) {
            if (zeige.kachel != null) AsyncImage(model = zeige.kachel, contentDescription = e.bildunterschrift, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            else Text(e.format.uppercase(), style = MaterialTheme.typography.headlineSmall, color = farben.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onOeffnen, shape = MaterialTheme.shapes.small) { Text(stringResource(Res.string.desk_archive_open)) }
            if (e.rueckseite != null) OutlinedButton(onClick = { hinten = !hinten }, shape = MaterialTheme.shapes.small) { Text("↻ " + stringResource(Res.string.desk_postcard_turn)) }
            if (darfBearbeiten && !postkarte) OutlinedButton(onClick = onBearbeiten, shape = MaterialTheme.shapes.small) { Text(stringResource(Res.string.viewer_edit_exif)) }
        }
        if (postkarte && onPostkarte != null) OutlinedButton(onClick = onPostkarte, shape = MaterialTheme.shapes.small) { Text(stringResource(Res.string.desk_postcard_edit)) }
        else if (darfBearbeiten && postkarte) Text(stringResource(Res.string.desk_postcard_edit_later), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
        if (onGesichter != null) OutlinedButton(onClick = onGesichter, shape = MaterialTheme.shapes.small) {
            Text(stringResource(Res.string.desk_faces_menu) + if (e.bereiche.isNotEmpty()) "  (${e.bereiche.size})" else "")
        }
        TextButton(onClick = onKopien) { Text(stringResource(Res.string.desk_archive_download)) }
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(e.bildunterschrift.ifEmpty { e.datei }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Angabe(stringResource(Res.string.field_date), de.bgghome.webtrees.nativ.ui.datumText(e))
                Angabe(stringResource(Res.string.postcard_motif), e.motiv)
                Angabe(stringResource(Res.string.postcard_transcription), e.transkription)
                Angabe(stringResource(Res.string.postcard_note), e.notiz)
                Angabe(stringResource(Res.string.field_persons), e.exifPersonen.joinToString(", "))
                Angabe(stringResource(Res.string.field_keywords), e.keywords.joinToString(", "))
                Angabe(stringResource(Res.string.desk_archive_in_collections), e.inSammlungen.joinToString(", "))
                Angabe(stringResource(Res.string.desk_archive_dimensions), listOfNotNull(
                    if (e.breite > 0 && e.hoehe > 0) "${e.breite} × ${e.hoehe} px" else null,
                    if (e.groesseKb > 0) (if (e.groesseKb >= 1024) "%.1f MB".format(e.groesseKb / 1024.0) else "${e.groesseKb} KB") else null,
                    e.format.uppercase().takeIf { it.isNotBlank() },
                ).joinToString(" · "))
                Angabe(stringResource(Res.string.desk_archive_path), e.pfad.orEmpty())
            }
        }
        // Verknuepfung: neutral angeben - eine Datei ohne Person ist kein Mangel, sondern Archivbestand
        Text(stringResource(Res.string.desk_archive_in_tree), style = MaterialTheme.typography.labelMedium, color = farben.onSurfaceVariant)
        when {
            e.personen.isNotEmpty() -> Column {
                e.personen.forEach { p ->
                    Text(p.name.ifBlank { p.xref }, Modifier.clickable { onPerson(p.xref) }.padding(vertical = 2.dp), style = MaterialTheme.typography.bodyMedium, color = farben.primary)
                }
                if (e.personenGesamt > e.personen.size) Text("… (${e.personenGesamt})", style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
            }
            e.xref != null -> Text(stringResource(Res.string.archive_link_object), style = MaterialTheme.typography.bodySmall)
            else -> Text(stringResource(Res.string.archive_link_file), style = MaterialTheme.typography.bodySmall)
        }
        if (e.notiz.isNotBlank() && !postkarte) Angabe(stringResource(Res.string.desk_tab_notes), e.notiz)
        // Abgleich mit webtrees: nur Medienobjekte; uebernommen wird aus webtrees in die Datei (wie in der Lightbox)
        val unterschiede = if (postkarte) emptyList() else de.bgghome.webtrees.nativ.data.ArchivAbgleich.unterschiede(e)
        if (unterschiede.isNotEmpty()) {
            HorizontalDivider(color = farben.outlineVariant)
            Text(stringResource(Res.string.desk_sync_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            val leer = stringResource(Res.string.desk_sync_empty)
            unterschiede.forEach { u ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(if (u.feld == de.bgghome.webtrees.nativ.data.ArchivAbgleich.Feld.Beschreibung) Res.string.desk_sync_description else Res.string.field_persons),
                        style = MaterialTheme.typography.labelMedium, color = farben.onSurfaceVariant)
                    Text(stringResource(Res.string.desk_sync_file) + ": " + u.datei.ifBlank { leer }, style = MaterialTheme.typography.bodySmall)
                    Text("webtrees: " + u.webtrees.ifBlank { leer } + if (u.gekuerzt) " " + stringResource(Res.string.desk_sync_cut, e.personenGesamt) else "",
                        style = MaterialTheme.typography.bodySmall)
                    if (darfBearbeiten && onAbgleich != null && u.uebernahme != null)
                        TextButton(onClick = { onAbgleich(u) }) { Text(stringResource(Res.string.desk_sync_apply)) }
                }
            }
        }
    }
}

@Composable
private fun Angabe(titel: String, wert: String) {
    if (wert.isBlank()) return
    Column {
        Text(titel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(wert, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Mehrere Dateien ins Archiv: Ordner und thematische Sammlung (vorbelegt mit der offenen Sammlung), wahlweise eine
 * gemeinsame Beschriftung fuer die Bilder. Hochgeladen wird das Original, Datei fuer Datei, mit Fortschritt; was groesser
 * ist, als der Server annimmt, wird uebersprungen und genannt (ein Archivscan wird nie still verkleinert).
 * [onClose] bekommt, ob etwas abgelegt wurde.
 */
@Composable
private fun HochladenDialog(
    tree: String, client: WtClient, archive: ArchiveOverview, dateien: List<File>,
    ordner: String, sammlung: String, maxUpload: Long, onClose: (Boolean) -> Unit,
) {
    var zielOrdner by remember { mutableStateOf(ordner.takeIf { it in archive.ordnerListe }.orEmpty()) }
    var zielSammlung by remember { mutableStateOf(sammlung) }
    var beschreibung by remember { mutableStateOf("") }
    var datum by remember { mutableStateOf("") }
    var personen by remember { mutableStateOf("") }
    var schlagwoerter by remember { mutableStateOf("") }
    var laeuft by remember { mutableStateOf(false) }
    var fertig by remember { mutableIntStateOf(0) }
    var abgelegt by remember { mutableIntStateOf(0) }
    val hinweise = remember { mutableStateListOf<String>() }
    var ende by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val datumOk = datum.isBlank() || Regex("""\d{4}(-\d{2}(-\d{2})?)?""").matches(datum.trim())
    val haupt = stringResource(Res.string.archive_root_folder)
    val keine = stringResource(Res.string.option_none)
    val thematisch = archive.sammlungen.filter { it.art == "thematisch" }

    WtAlertDialog(
        onDismissRequest = { if (!laeuft) onClose(abgelegt > 0) },
        title = { Text(stringResource(Res.string.desk_archive_upload_title)) },
        breite = 640.dp,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.desk_archive_count, dateien.size) + ": " + dateien.take(6).joinToString(", ") { it.name } + if (dateien.size > 6) ", …" else "",
                    style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(stringResource(Res.string.field_folder), style = MaterialTheme.typography.labelMedium)
                Auswahl((listOf("" to haupt) + archive.ordnerListe.map { it to it }).firstOrNull { it.first == zielOrdner }?.second ?: haupt,
                    listOf(haupt) + archive.ordnerListe) { w -> zielOrdner = if (w == haupt) "" else w }
                if (thematisch.isNotEmpty()) {
                    Text(stringResource(Res.string.field_collection), style = MaterialTheme.typography.labelMedium)
                    Auswahl(thematisch.firstOrNull { it.slug == zielSammlung }?.name ?: keine, listOf(keine) + thematisch.map { it.name }) { w ->
                        zielSammlung = thematisch.firstOrNull { it.name == w }?.slug.orEmpty()
                    }
                }
                OutlinedTextField(beschreibung, { beschreibung = it }, label = { Text(stringResource(Res.string.field_description)) }, minLines = 2, modifier = Modifier.fillMaxWidth(), enabled = !laeuft)
                OutlinedTextField(datum, { datum = it }, label = { Text(stringResource(Res.string.field_date)) }, supportingText = { Text(stringResource(Res.string.hint_iso_date)) },
                    isError = !datumOk, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !laeuft)
                OutlinedTextField(personen, { personen = it }, label = { Text(stringResource(Res.string.field_persons)) },
                    supportingText = { Text(stringResource(Res.string.desk_archive_persons_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !laeuft)
                OutlinedTextField(schlagwoerter, { schlagwoerter = it }, label = { Text(stringResource(Res.string.field_keywords)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !laeuft)
                Text(stringResource(Res.string.desk_archive_upload_meta_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (laeuft || ende) {
                    LinearProgressIndicator(progress = { fertig.toFloat() / dateien.size }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(Res.string.desk_archive_upload_progress, abgelegt, dateien.size), style = MaterialTheme.typography.bodySmall)
                }
                hinweise.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (ende) TextButton(onClick = { onClose(abgelegt > 0) }) { Text(stringResource(Res.string.action_close)) }
            else TextButton(enabled = !laeuft && datumOk, onClick = {
                laeuft = true
                val anfrage = ArchiveUploadRequest(
                    ordner = zielOrdner, beschreibung = beschreibung.trim(), datum = datum.trim(),
                    personen = personen.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    keywords = schlagwoerter.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    sammlung = zielSammlung,
                )
                scope.launch {
                    dateien.forEach { d ->
                        if (maxUpload > 0 && d.length() > maxUpload) {
                            hinweise += de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_archive_upload_too_big, d.name, "%.1f".format(maxUpload / 1048576.0))
                        } else runCatching {
                            val bytes = withContext(Dispatchers.IO) { d.readBytes() }
                            withContext(Dispatchers.IO) { client.uploadArchive(tree, anfrage, bytes, d.name, mimeVon(d)) }
                        }.onSuccess { r ->
                            abgelegt++
                            if (r.hinweis == "exif-failed") hinweise += de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_archive_upload_no_meta, d.name)
                        }.onFailure { hinweise += d.name + ": " + (meldung(it)) }
                        fertig++
                    }
                    laeuft = false; ende = true
                    if (hinweise.isEmpty()) onClose(abgelegt > 0)
                }
            }) { Text(stringResource(Res.string.desk_archive_upload_start)) }
        },
        dismissButton = { if (!ende) TextButton(enabled = !laeuft, onClick = { onClose(abgelegt > 0) }) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Mehrere Dateien waehlen (Systemdialog); null = abgebrochen. */
private fun dateienWaehlen(): List<File>? {
    val dialog = FileDialog(null as Frame?, de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_archive_add), FileDialog.LOAD).apply { isMultipleMode = true; isVisible = true }
    return dialog.files?.toList()?.filter { it.isFile }?.takeIf { it.isNotEmpty() }
}

/** Zielordner waehlen (Systemdialog); null = abgebrochen. */
private fun ordnerWaehlen(): File? {
    val wahl = javax.swing.JFileChooser().apply {
        fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        dialogTitle = de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_archive_download)
    }
    return if (wahl.showSaveDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) wahl.selectedFile else null
}

/**
 * Originale der Eintraege in [ordner] speichern - mit der Sitzung, denn das Archiv ist nur fuer Angemeldete. Vorhandene
 * Dateien werden nicht ueberschrieben, sondern mit -2, -3 … daneben gelegt. Liefert die Zahl der gespeicherten Dateien.
 */
internal fun kopien(client: WtClient, liste: List<ArchiveEntry>, ordner: File): Int {
    ordner.mkdirs()
    var n = 0
    liste.forEach { e ->
        if (e.original.isBlank()) return@forEach
        val name = e.datei.ifBlank { e.original.substringAfterLast('/').substringBefore('?') }.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "datei" }
        val ziel = freierName(ordner, name)
        client.http.newCall(Request.Builder().url(e.original).build()).execute().use { r ->
            check(r.isSuccessful) { "${e.datei}: HTTP ${r.code}" }
            r.body?.byteStream()?.use { ein -> ziel.outputStream().use { ein.copyTo(it) } }
        }
        n++
    }
    return n
}

internal fun freierName(ordner: File, name: String): File {
    var f = File(ordner, name)
    var i = 2
    while (f.exists()) { f = File(ordner, name.substringBeforeLast('.') + "-" + i + (if ('.' in name) "." + name.substringAfterLast('.') else "")); i++ }
    return f
}

/**
 * Datei › Stammbaum und Archiv sichern (nur Stammbaum auf diesem PC): Ziel waehlen, dann Datenbank und Medienordner als
 * ZIP ([LokalBetrieb.sichern]) - mit den freien Bestaenden und den Sammlungen des Moduls, die eine GEDZIP-Datei nicht kennt.
 */
@Composable
internal fun SicherungDialog(onClose: () -> Unit) {
    var ziel by remember { mutableStateOf<File?>(null) }
    var stand by remember { mutableStateOf("") }
    var ergebnis by remember { mutableStateOf<Result<File>?>(null) }
    LaunchedEffect(Unit) {
        val dialog = FileDialog(null as Frame?, de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_archive_backup), FileDialog.SAVE).apply {
            file = "wtwin-sicherung-" + java.time.LocalDate.now() + ".zip"
            isVisible = true
        }
        val name = dialog.file ?: run { onClose(); return@LaunchedEffect }
        val f = File(dialog.directory, if (name.endsWith(".zip", ignoreCase = true)) name else "$name.zip")
        ziel = f
        ergebnis = withContext(Dispatchers.IO) { runCatching { de.bgghome.webtrees.nativ.lokal.LokalBetrieb.sichern(f) { stand = it }; f } }
    }
    if (ziel == null) return
    WtAlertDialog(
        onDismissRequest = { if (ergebnis != null) onClose() },
        title = { Text(stringResource(Res.string.desk_archive_backup).removeSuffix(" …")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val e = ergebnis
                when {
                    e == null -> {
                        Text(stringResource(Res.string.desk_archive_backup_running))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stand, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    e.isSuccess -> Text(stringResource(Res.string.desk_archive_backup_done, e.getOrThrow().absolutePath))
                    else -> Text(meldung(e.exceptionOrNull()), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { if (ergebnis != null) TextButton(onClick = onClose) { Text(stringResource(Res.string.action_close)) } },
    )
}

/**
 * Datei > Als GEDCOM speichern (nur Stammbaum auf diesem PC): webtrees' Export ueber die angemeldete Verwalter-Sitzung,
 * ohne Browser. Endet der Dateiname auf .gdz, entsteht ein GEDZIP mit den Bildern.
 */
@Composable
internal fun GedcomSpeichernDialog(viewModel: de.bgghome.webtrees.nativ.ui.AppViewModel, tree: String, onClose: () -> Unit) {
    var ziel by remember { mutableStateOf<File?>(null) }
    var ergebnis by remember { mutableStateOf<Result<File>?>(null) }
    LaunchedEffect(Unit) {
        val dialog = FileDialog(null as Frame?, de.bgghome.webtrees.nativ.Texte.t(Res.string.desk_file_gedcom), FileDialog.SAVE).apply {
            file = "$tree.ged"
            isVisible = true
        }
        val name = dialog.file ?: run { onClose(); return@LaunchedEffect }
        val gedzip = name.endsWith(".gdz", ignoreCase = true)
        val f = File(dialog.directory, if (gedzip || name.endsWith(".ged", ignoreCase = true)) name else "$name.ged")
        ziel = f
        ergebnis = withContext(Dispatchers.IO) { runCatching { f.writeBytes(viewModel.client.exportGedcom(tree, gedzip)); f } }
    }
    if (ziel == null) return
    WtAlertDialog(
        onDismissRequest = { if (ergebnis != null) onClose() },
        title = { Text(stringResource(Res.string.desk_file_gedcom).removeSuffix(" …")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val e = ergebnis
                when {
                    e == null -> { Text(stringResource(Res.string.desk_file_gedcom_running)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    e.isSuccess -> Text(stringResource(Res.string.desk_file_gedcom_done, e.getOrThrow().absolutePath))
                    else -> Text(explainOf(e.exceptionOrNull()!!), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { if (ergebnis != null) TextButton(onClick = onClose) { Text(stringResource(Res.string.action_close)) } },
    )
}

private fun explainOf(e: Throwable): String = meldung(e)
