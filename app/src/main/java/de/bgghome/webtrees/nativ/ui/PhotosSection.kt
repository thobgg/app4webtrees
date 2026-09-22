package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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

        if (state.archiveStatus == ArchiveStatus.Ready) {
            SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
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
                MediaGrid(
                    state.media,
                    onOpen = { item ->
                        when {
                            item.isImage -> viewModel.openMediaViewer(ViewerSource.Tree, state.media, item)
                            item.mime == "application/pdf" -> viewModel.openPdf(item.file, item.title, item.url)
                            else -> openWeb(item.url)
                        }
                    },
                    showPeople = true, onEnd = viewModel::loadMoreMedia,
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
fun MediaGrid(media: List<MediaJson>, onOpen: (MediaJson) -> Unit, showPeople: Boolean = false, onEnd: (() -> Unit)? = null) {
    if (media.isEmpty()) {
        Text(stringResource(R.string.media_none), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(media) { item ->
            if (onEnd != null && item === media.last()) LaunchedEffect(media.size) { onEnd() }

            Column(Modifier.clickable { onOpen(item) }) {
                Thumbnail(item.thumb, item.title)
                Text(
                    item.title.ifEmpty { item.mime }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                )
                if (showPeople && item.people.isNotEmpty()) {
                    Text(
                        item.people.joinToString(", ") { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        }
    }
}

/** Quadratische Kachel mit abgerundeten Ecken; ohne Bild bleibt eine graue Flaeche. */
@Composable
fun Thumbnail(url: String?, description: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {}
        if (url != null) {
            AsyncImage(model = url, contentDescription = description, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
