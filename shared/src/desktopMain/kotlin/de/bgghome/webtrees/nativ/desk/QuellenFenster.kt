package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.ui.meldung

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import coil3.compose.AsyncImage
import de.bgghome.webtrees.nativ.api.MediaJson
import de.bgghome.webtrees.nativ.api.SourceDetail
import de.bgghome.webtrees.nativ.api.SourceList
import de.bgghome.webtrees.nativ.api.SourceRef
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.UiState
import de.bgghome.webtrees.nativ.ui.select
import de.bgghome.webtrees.nativ.ui.setRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * Quellen (ab API-Stufe 18): links alle Quellen des Baums mit Suche, rechts die gewaehlte mit Autor, Publikation,
 * Archiv und Signatur, Text, Notizen, Medien und wer sie zitiert - Personen und Familien mit den Ereignissen.
 * Stufe 1 liest nur; anlegen und aendern geht vorerst in webtrees ("In webtrees oeffnen").
 */

/** Die Qualitaet eines Verweises (GEDCOM QUAY) als Text. */
fun qualitaet(q: Int?): StringResource? = when (q) {
    0 -> Res.string.desk_quality_0
    1 -> Res.string.desk_quality_1
    2 -> Res.string.desk_quality_2
    3 -> Res.string.desk_quality_3
    else -> null
}

@Composable
fun QuellenFenster(state: UiState, viewModel: AppViewModel, start: String?, openWeb: (String) -> Unit, onClose: () -> Unit) {
    val tree = state.tree?.name
    var neu by remember { mutableStateOf(0) }
    val liste by produceState<Result<SourceList>?>(null, tree, neu) {
        value = null
        if (tree != null) value = withContext(Dispatchers.IO) { runCatching { viewModel.client.sources(tree) } }
    }
    var suche by remember { mutableStateOf("") }
    var gewaehlt by remember(start) { mutableStateOf(start?.takeIf { it.isNotEmpty() }) }
    val canEdit = state.tree?.canEdit == true
    var dialog by remember { mutableStateOf<QuellenDialog?>(null) }
    var fehler by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun neuLaden() { neu++ }
    val detail by produceState<Result<SourceDetail>?>(null, tree, gewaehlt, neu) {
        value = null
        val x = gewaehlt
        if (tree != null && x != null) value = withContext(Dispatchers.IO) { runCatching { viewModel.client.source(tree, x) } }
    }

    androidx.compose.ui.window.Window(
        onCloseRequest = onClose, title = stringResource(Res.string.desk_sources_window) + " – " + state.tree?.title.orEmpty(),
        state = arbeitsfenster("fenster_quellen", 1180, 800),
        onPreviewKeyEvent = { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onClose(); true } else false },
    ) {
        DeskTheme {
            // Breite der Liste: am Trennstrich ziehen, gemerkt
            var listeBreite by remember { mutableStateOf(DeskLayout.prefs.getString("quellen_liste", null)?.toFloatOrNull() ?: 380f) }
            val dichte = androidx.compose.ui.platform.LocalDensity.current.density
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                // ── Liste ──
                Column(Modifier.width(listeBreite.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                    Box(Modifier.fillMaxWidth().padding(10.dp)) {
                        BasicTextField(suche, { suche = it }, singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small).padding(8.dp))
                        if (suche.isEmpty()) Text(stringResource(Res.string.desk_sources_search), Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                    val alle = liste?.getOrNull()?.sources.orEmpty()
                    val treffer = if (suche.isBlank()) alle else alle.filter { q ->
                        listOf(q.title, q.author, q.publication, q.abbreviation, q.repository, q.callNumber).any { it.contains(suche.trim(), ignoreCase = true) }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.desk_sources_count, treffer.size, alle.size), Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (canEdit && alle.any { it.uses == 0 }) TextButton(onClick = { dialog = QuellenDialog.Unbenutzte }) { Text(stringResource(Res.string.desk_sources_unused), style = MaterialTheme.typography.labelMedium) }
                    }
                    if (canEdit) Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { dialog = QuellenDialog.Neu }, shape = MaterialTheme.shapes.small) { Text("+ " + stringResource(Res.string.desk_source_new)) }
                    }
                    HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.weight(1f)) {
                        val fehler = liste?.exceptionOrNull()
                        when {
                            fehler != null -> Text(meldung(fehler), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                            liste == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                            else -> QuellenListe(treffer, gewaehlt) { gewaehlt = it }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End).padding(4.dp)) { Text(stringResource(Res.string.action_close)) }
                }
                Ziehgriff(onZiehen = { listeBreite = (listeBreite + it / dichte).coerceIn(220f, 720f) }) { DeskLayout.prefs.putString("quellen_liste", listeBreite.toString()) }

                // ── Die gewaehlte Quelle ──
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val d = detail
                    when {
                        gewaehlt == null -> Text(stringResource(Res.string.desk_sources_choose), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        d == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        d.exceptionOrNull() != null -> Text(meldung(d.exceptionOrNull()), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
                        else -> QuelleDetail(d.getOrThrow(), onPerson = viewModel::select, onMitte = viewModel::setRoot, openWeb = openWeb, canEdit = canEdit,
                            onBearbeiten = { dialog = QuellenDialog.Bearbeiten(it) },
                            onVorhanden = { dialog = QuellenDialog.Medium(it) },
                            onMediumLoesen = { q, m ->
                                scope.launch { runCatching { viewModel.client.saveSource(tree.orEmpty(), q.xref, de.bgghome.webtrees.nativ.api.SourceRequest(media = q.media.map { it.xref }.filter { it != m.xref }.distinct())) }.onSuccess { neuLaden() }.onFailure { fehler = meldung(it) } }
                            },
                            onLoeschen = { dialog = QuellenDialog.Loeschen(it) },
                            onDatei = { q ->
                                dateiOeffnen(scanDialogTitel())?.let { datei ->
                                    scope.launch { runCatching { scanHochladen(viewModel.client, tree.orEmpty(), q.xref, datei) }.onSuccess { neuLaden() }.onFailure { fehler = meldung(it) } }
                                }
                            })
                    }
                    fehler?.let { Text(it, Modifier.align(Alignment.BottomStart).padding(12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
            val t = tree.orEmpty()
            when (val dlg = dialog) {
                null -> {}
                QuellenDialog.Neu -> QuelleDialog(t, null, viewModel, onDismiss = { dialog = null }, onSaved = { gewaehlt = it; neuLaden() })
                is QuellenDialog.Bearbeiten -> QuelleDialog(t, dlg.quelle, viewModel, onDismiss = { dialog = null }, onSaved = { neuLaden() })
                is QuellenDialog.Loeschen -> {
                    val q = dlg.quelle; val n = q.individuals.size + q.families.size + q.moreIndividuals + q.moreFamilies
                    de.bgghome.webtrees.nativ.ui.ConfirmDialog(
                        title = stringResource(Res.string.desk_source_delete),
                        text = if (n > 0) stringResource(Res.string.desk_source_delete_text, q.title, n) else stringResource(Res.string.desk_source_delete_unused_text, q.title),
                        confirm = stringResource(Res.string.desk_source_delete), onDismiss = { dialog = null },
                        onConfirm = { dialog = null; scope.launch { runCatching { viewModel.client.deleteRecord(t, q.xref) }.onSuccess { gewaehlt = null; neuLaden() }.onFailure { fehler = meldung(it) } } },
                    )
                }
                is QuellenDialog.Medium -> MedienWahlDialog(t, viewModel.client, dlg.quelle.media.map { it.xref }.toSet(), onDismiss = { dialog = null },
                    archive = state.archive, rechteXref = dlg.quelle.xref) { m ->
                    scope.launch { runCatching { viewModel.client.saveSource(t, dlg.quelle.xref, de.bgghome.webtrees.nativ.api.SourceRequest(media = (dlg.quelle.media.map { it.xref } + m.xref).distinct())) }.onSuccess { neuLaden() }.onFailure { fehler = meldung(it) } }
                }
                QuellenDialog.Unbenutzte -> UnbenutzteDialog(t, liste?.getOrNull()?.sources.orEmpty(), viewModel, onDismiss = { dialog = null }, onFertig = { gewaehlt = null; neuLaden() })
            }
        }
    }
}

/** Die Liste der Quellen: Titel, Zahl der Verweise, darunter Autor und Archiv mit Signatur; ein Klick waehlt. */
@Composable
internal fun QuellenListe(treffer: List<de.bgghome.webtrees.nativ.api.SourceSummary>, gewaehlt: String?, onWahl: (String) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        val ls = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize(), state = ls) {
            items(treffer, key = { it.xref }) { q ->
                val aktiv = q.xref == gewaehlt
                Column(Modifier.fillMaxWidth().background(if (aktiv) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                    .fokusRahmen().clickable { onWahl(q.xref) }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(q.title.ifBlank { q.xref }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (aktiv) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${q.uses}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val unter = listOf(q.author, listOf(q.repository, q.callNumber).filter(String::isNotBlank).joinToString(", ")).filter(String::isNotBlank).joinToString(" · ")
                    if (unter.isNotBlank()) Text(unter, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        ListenLeiste(ls)
    }
}

private sealed interface QuellenDialog {
    data object Neu : QuellenDialog
    data class Bearbeiten(val quelle: SourceDetail) : QuellenDialog
    data class Loeschen(val quelle: SourceDetail) : QuellenDialog
    data object Unbenutzte : QuellenDialog
    data class Medium(val quelle: SourceDetail) : QuellenDialog
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun QuelleDetail(
    q: SourceDetail, onPerson: (String) -> Unit, onMitte: (String) -> Unit, openWeb: (String) -> Unit, canEdit: Boolean = false,
    onBearbeiten: (SourceDetail) -> Unit = {}, onLoeschen: (SourceDetail) -> Unit = {}, onDatei: (SourceDetail) -> Unit = {},
    onVorhanden: (SourceDetail) -> Unit = {}, onMediumLoesen: (SourceDetail, MediaJson) -> Unit = { _, _ -> },
) {
    val scroll = rememberScrollState()
    val farben = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(q.title.ifBlank { q.xref }, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (canEdit && q.canEdit) {
                    OutlinedButton(onClick = { onBearbeiten(q) }, shape = MaterialTheme.shapes.small) { Text(stringResource(Res.string.action_edit)) }
                    Tipp(stringResource(Res.string.tipp_source_from_file)) { OutlinedButton(onClick = { onDatei(q) }, shape = MaterialTheme.shapes.small) { Text(stringResource(Res.string.desk_source_add_file)) } }
                    OutlinedButton(onClick = { onLoeschen(q) }, shape = MaterialTheme.shapes.small) { Text(stringResource(Res.string.desk_source_delete)) }
                }
                OutlinedButton(onClick = { openWeb(q.url) }, shape = MaterialTheme.shapes.small) { Text(stringResource(Res.string.chip_open_web)) }
            }
            @Composable
            fun Zeile(label: StringResource, wert: String) {
                if (wert.isBlank()) return
                Row { Text(stringResource(label), Modifier.width(140.dp), style = MaterialTheme.typography.bodyMedium, color = farben.onSurfaceVariant)
                    Text(wert, style = MaterialTheme.typography.bodyMedium) }
            }
            Zeile(Res.string.desk_source_author, q.author)
            Zeile(Res.string.desk_source_publication, q.publication)
            Zeile(Res.string.desk_source_abbreviation, q.abbreviation)
            q.repositories.forEach { r -> Zeile(Res.string.desk_source_repository, listOf(r.name, r.callNumber).filter(String::isNotBlank).joinToString(", ")) }
            Zeile(Res.string.desk_source_id, q.xref)
            if (q.text.isNotBlank()) { Abschnitt(Res.string.desk_source_text); Text(q.text, style = MaterialTheme.typography.bodyMedium) }
            if (q.notes.isNotEmpty()) { Abschnitt(Res.string.desk_tab_notes); q.notes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) } }
            if (q.media.isNotEmpty() || (canEdit && q.canEdit)) {
                Abschnitt(Res.string.tab_media)
                if (q.media.isNotEmpty()) MedienReihe(q.media, openWeb, onLoesen = if (canEdit && q.canEdit) ({ m -> onMediumLoesen(q, m) }) else null)
                if (canEdit && q.canEdit) TextButton(onClick = { onVorhanden(q) }) { Text(stringResource(Res.string.desk_media_existing)) }
            }

            Abschnitt(Res.string.desk_source_cited_by)
            if (q.individuals.isEmpty() && q.families.isEmpty()) Text(stringResource(Res.string.desk_source_unused), color = farben.onSurfaceVariant)
            q.individuals.forEach { p ->
                Row(Modifier.fillMaxWidth().fokusRahmen().combinedClickable(enabled = !p.isPrivate, onDoubleClick = { onMitte(p.xref) }) { onPerson(p.xref) }
                    .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(registerName(p.person(), stringResource(Res.string.person_private), stringResource(Res.string.person_no_name)) + jahre(p.person()).let { if (it.isNotEmpty()) "  $it" else "" },
                        Modifier.width(320.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(p.facts.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
                }
            }
            if (q.moreIndividuals > 0) Text(stringResource(Res.string.desk_source_more, q.moreIndividuals), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
            q.families.forEach { f ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(f.name, Modifier.width(320.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(f.facts.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
                }
            }
            if (q.moreFamilies > 0) Text(stringResource(Res.string.desk_source_more, q.moreFamilies), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
        }
        SenkrechteLeiste(scroll)
    }
}

@Composable
private fun Abschnitt(titel: StringResource) {
    Text(stringResource(titel), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** Vorschaubilder von Scans und Fotos; ein Klick oeffnet die Datei. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedienReihe(media: List<MediaJson>, openWeb: (String) -> Unit, onLoesen: ((MediaJson) -> Unit)? = null, onBearbeiten: ((MediaJson) -> Unit)? = null,
                onOeffnen: ((MediaJson) -> Unit)? = null, onHauptbild: ((MediaJson) -> Unit)? = null) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        media.forEach { m ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Beim Darueberfahren: Titel, Dateiname, Format und Art, bei Orten auch Groesse und Bildmasse
                val hervor = if (m.highlighted) stringResource(Res.string.desk_gl_highlighted) else ""
                val tipp = (listOf(m.title, m.path?.substringAfterLast('/').orEmpty(), listOfNotNull(m.format?.uppercase(), m.type).joinToString(" · ")) + m.info + hervor)
                    .filter(String::isNotBlank).distinct().joinToString("\n")
                Tipp(tipp) {
                    Box(Modifier.size(96.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant).clickable { if (onOeffnen != null) onOeffnen(m) else openWeb(m.file.ifBlank { m.url }) },
                        contentAlignment = Alignment.Center) {
                        if (m.thumb != null) AsyncImage(model = m.thumb, contentDescription = m.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Text(m.title, Modifier.padding(6.dp), style = MaterialTheme.typography.labelSmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        // GEDCOM-L "_PRIM Y": das Bild, das andere Programme als Hauptbild zeigen
                        if (m.highlighted) Text("★", Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)).padding(horizontal = 3.dp),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                // Nur die Verknuepfung loesen - Medium und Datei bleiben, wie beim Loesen in webtrees
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (onBearbeiten != null) Text(stringResource(Res.string.desk_media_edit), Modifier.clickable { onBearbeiten(m) }.padding(2.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (onLoesen != null) Text(stringResource(Res.string.desk_media_unlink), Modifier.clickable { onLoesen(m) }.padding(2.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (onHauptbild != null && m.isImage && !m.highlighted) Text(stringResource(Res.string.desk_media_make_primary), Modifier.clickable { onHauptbild(m) }.padding(2.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/**
 * Ein Quellenverweis im Detailbereich eines Ereignisses: Quelle (Klick oeffnet sie in der Quellenverwaltung), Seite,
 * Qualitaet, Datum, Zitat, Notizen und Medien. Eine Text-Quelle ohne Datensatz steht kursiv.
 */
@Composable
fun VerweisAnzeige(q: SourceRef, onQuelle: ((String) -> Unit)?, openWeb: (String) -> Unit) {
    val farben = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val titel = q.title.ifBlank { q.xref }
        if (q.istText) Text(titel, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
        else Text(titel, Modifier.then(if (onQuelle != null) Modifier.clickable { onQuelle(q.xref) } else Modifier),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (onQuelle != null) farben.primary else farben.onSurface)
        @Composable
        fun Zeile(label: StringResource, wert: String) {
            if (wert.isBlank()) return
            Row { Text(stringResource(label), Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
                Text(wert, style = MaterialTheme.typography.bodySmall) }
        }
        Zeile(Res.string.desk_citation_page, q.page)
        qualitaet(q.quality)?.let { Zeile(Res.string.desk_citation_quality, stringResource(it)) }
        Zeile(Res.string.desk_citation_date, q.date?.text.orEmpty())
        Zeile(Res.string.desk_citation_text, q.text)
        q.notes.forEach { Zeile(Res.string.desk_tab_notes, it) }
        if (q.media.isNotEmpty()) MedienReihe(q.media, openWeb)
    }
}
