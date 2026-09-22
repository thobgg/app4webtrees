package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.bgghome.webtrees.nativ.R
import de.bgghome.webtrees.nativ.api.MediaJson

/**
 * Bereich "Fotos": links die Medienobjekte des Baums (neueste zuerst, mit den verknuepften Personen; braucht Modul ab
 * API-Stufe 2), rechts das Archiv des Sammlungen-Moduls - sofern der Server es anbietet, sonst gibt es keine Umschaltung.
 */
@Composable
fun PhotosSection(state: UiState, viewModel: AppViewModel, openWeb: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TreeTitleBar(state, viewModel, openWeb)

        // Umschaltung Stammbaum/Archiv (nur mit Modul) und rechts der Schalter fuer das dichte Raster
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.archiveStatus == ArchiveStatus.Ready) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f).widthIn(max = 480.dp)) {
                    PhotosTab.entries.forEachIndexed { index, tab ->
                        SegmentedButton(
                            selected = state.photosTab == tab,
                            onClick = { viewModel.setPhotosTab(tab) },
                            shape = SegmentedButtonDefaults.itemShape(index, PhotosTab.entries.size),
                        ) {
                            Text(stringResource(if (tab == PhotosTab.Tree) R.string.photos_tab_tree else R.string.photos_tab_archive))
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconToggleButton(checked = state.denseGrid, onCheckedChange = viewModel::setDenseGrid) {
                Icon(GridIcon, contentDescription = stringResource(R.string.photos_dense), tint = if (state.denseGrid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (state.photosTab == PhotosTab.Archive && state.archiveStatus == ArchiveStatus.Ready) {
            Box(Modifier.fillMaxSize()) {
                if (state.collection != null || state.loadingCollection) {
                    CollectionView(state, viewModel, openWeb)
                } else {
                    ArchiveOverviewList(state, viewModel)
                }
                // Festhalten: nur, wenn das Modul es anbietet (Stufe 2) und dieser Nutzer hochladen darf.
                if (viewModel.canCaptureToArchive) {
                    ArchiveCaptureButton(state, viewModel, Modifier.align(Alignment.BottomEnd).padding(16.dp))
                }
            }
            return@Column
        }

        if (state.loadingMedia) LinearProgressIndicator(Modifier.fillMaxWidth())

        when {
            !viewModel.photosSupported ->
                Text(stringResource(R.string.photos_needs_update), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.media.isEmpty() && state.mediaLoaded && !state.loadingMedia ->
                Text(stringResource(R.string.photos_none), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.media.isNotEmpty() ->
                // "Fotos" heisst Fotos: Bilder im Raster, alles andere (Urkunden als PDF, Ton, Video) als Liste darunter.
                MediaGrid(
                    state.media.filter { it.isImage },
                    onOpen = { item -> viewModel.openMediaViewer(ViewerSource.Tree, state.media, item) },
                    showPeople = true, onEnd = viewModel::loadMoreMedia, dense = state.denseGrid,
                    documents = state.media.filter { !it.isImage },
                    onOpenDocument = { item -> if (item.mime == "application/pdf") viewModel.openPdf(item.file, item.title, item.url) else openWeb(item.url) },
                )
        }
    }
}

/**
 * Raster quadratischer Vorschaubilder - fuer die Fotouebersicht und den Reiter "Medien" im Profil.
 * Ein Tipp oeffnet ein Bild im Betrachter; was kein Bild ist (PDF, Ton), oeffnet der Aufrufer als webtrees-Seite.
 *
 * @param onEnd wird gerufen, sobald das letzte Bild sichtbar ist - zum Nachladen der naechsten Seite
 */
@Composable
fun MediaGrid(
    media: List<MediaJson>,
    onOpen: (MediaJson) -> Unit,
    showPeople: Boolean = false,
    onEnd: (() -> Unit)? = null,
    /** Was kein Bild ist, als Liste unter dem Raster - PDFs mit Vorschau der ersten Seite */
    documents: List<MediaJson> = emptyList(),
    onOpenDocument: (MediaJson) -> Unit = onOpen,
    /** Dicht: drei und mehr Spalten, kaum Luft, keine Unterschriften - die Details zeigt der Betrachter */
    dense: Boolean = false,
) {
    if (media.isEmpty() && documents.isEmpty()) {
        Text(stringResource(R.string.media_none), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    // Nachladen, sobald das letzte Element der Seite sichtbar ist - egal ob Bild oder Dokument
    val last = documents.lastOrNull() ?: media.lastOrNull()

    val gap = if (dense) 2.dp else 8.dp

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (dense) 110.dp else 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = gap, end = gap, top = gap, bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        items(media) { item ->
            if (onEnd != null && item === last) LaunchedEffect(media.size + documents.size) { onEnd() }

            Column(Modifier.clickable { onOpen(item) }) {
                Thumbnail(item.thumb, item.title, rounded = !dense)
                if (!dense) Text(
                    item.title.ifEmpty { item.mime }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                )
                if (!dense && showPeople && item.people.isNotEmpty()) {
                    Text(
                        item.people.joinToString(", ") { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        }
        // Dokumente unter den Fotos: Urkunden als PDF, Ton, Film - was kein Bild ist
        if (documents.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeading(stringResource(R.string.photos_documents)) }
            items(documents, span = { GridItemSpan(maxLineSpan) }) { item ->
                if (onEnd != null && item === last) LaunchedEffect(media.size + documents.size) { onEnd() }
                DocumentRow(item, onClick = { onOpenDocument(item) })
            }
        }
    }
}

/** Ein Medienobjekt, das kein Bild ist: PDF mit Vorschau der ersten Seite und Kennzeichen, sonst das Format im Kasten. */
@Composable
private fun DocumentRow(item: MediaJson, onClick: () -> Unit) {
    val format = item.mime.substringAfter('/').substringBefore(';').uppercase().ifEmpty { "?" }
    val chip: @Composable () -> Unit = {
        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(format.take(5), Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (item.mime == "application/pdf") {
            PdfThumbnail(item.file, Modifier.width(64.dp).height(84.dp)) { bitmap ->
                if (bitmap == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { chip() }
                } else {
                    Image(
                        bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
                    )
                    Surface(Modifier.align(Alignment.BottomEnd).padding(3.dp), shape = RoundedCornerShape(5.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f)) {
                        Text("PDF", Modifier.padding(horizontal = 5.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        } else {
            chip()
        }
        Column(Modifier.weight(1f)) {
            Text(item.title.ifEmpty { item.mime }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.people.isNotEmpty()) {
                Text(item.people.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Quadratische Kachel mit abgerundeten Ecken; ohne Bild bleibt eine graue Flaeche. */
@Composable
fun Thumbnail(url: String?, description: String, modifier: Modifier = Modifier, rounded: Boolean = true) {
    Box(modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(if (rounded) 8.dp else 0.dp)), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {}
        if (url != null) {
            AsyncImage(model = url, contentDescription = description, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
