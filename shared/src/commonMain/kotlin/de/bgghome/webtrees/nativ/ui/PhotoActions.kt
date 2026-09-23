package de.bgghome.webtrees.nativ.ui

import androidx.lifecycle.viewModelScope
import de.bgghome.webtrees.nativ.res.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Fotos: die Fotouebersicht des Baums (seitenweise) und das Hochladen eines Bildes zu einer Person. Erweiterungen von AppViewModel.

val AppViewModel.photosSupported: Boolean get() = (uiState.value.info?.api ?: 0) >= AppViewModel.API_PHOTOS

fun AppViewModel.loadMoreMedia() {
    if (uiState.value.mediaNextPage != null && !uiState.value.loadingMedia) loadMedia(reset = false)
}

internal fun AppViewModel.loadMedia(reset: Boolean) {
    val tree = uiState.value.tree ?: return
    if (!photosSupported) {
        uiState.update { it.copy(mediaLoaded = true) }
        return
    }
    val page = if (reset) 1 else uiState.value.mediaNextPage ?: return

    uiState.update { it.copy(loadingMedia = true) }

    viewModelScope.launch {
        try {
            val result = client.mediaList(tree.name, page)
            uiState.update {
                val media = if (reset) result.data else it.media + result.data
                it.copy(
                    media = media,
                    mediaNextPage = result.nextPage, loadingMedia = false, mediaLoaded = true,
                    viewer = it.viewer?.let { v -> if (v.source == ViewerSource.Tree) v.copy(items = media.filter { m -> m.isImage }.map { m -> viewerItem(m) }) else v },
                )
            }
        } catch (e: Exception) {
            fail(e)
            uiState.update { it.copy(loadingMedia = false, mediaLoaded = true) }
        }
    }
}

fun AppViewModel.uploadPhoto(photo: PhotoFile, title: String) = write(Res.string.msg_photo_uploaded) { tree, xref ->
    // Verkleinern und drehen; was sich nicht als Bild lesen laesst, geht unveraendert hoch.
    // Limit des Servers (meist 2-8 MB) mit etwas Luft fuer den Rest der Anfrage; aeltere Module nennen es nicht.
    val limit = (uiState.value.info?.maxUpload?.takeIf { it > 0 } ?: AppViewModel.DEFAULT_MAX_UPLOAD) * 9 / 10
    val prepared = withContext(Dispatchers.IO) { runCatching { photo.prepareJpeg(limit) }.getOrNull() }

    when {
        prepared != null -> {
            val jpegName = photo.name.substringBeforeLast('.') + ".jpg"
            client.uploadMedia(tree, xref, prepared, jpegName, "image/jpeg", title)
        }
        // Ein Bild, das sich nicht verkleinern liess: nicht das riesige Original hinterherschicken - das scheitert
        // am Limit des Servers nur mit einer nichtssagenden Meldung.
        photo.mime.startsWith("image/") -> throw UserMessageException(text(Res.string.err_image_prepare))
        else -> {
            val bytes = withContext(Dispatchers.IO) { photo.readBytes() }
            if (bytes.size > limit) {
                throw UserMessageException(text(Res.string.err_file_too_large, bytes.size / 1048576 + 1, limit / 1048576))
            }
            client.uploadMedia(tree, xref, bytes, photo.name, photo.mime.ifEmpty { "application/octet-stream" }, title)
        }
    }
}
