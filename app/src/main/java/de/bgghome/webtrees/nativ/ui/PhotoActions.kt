package de.bgghome.webtrees.nativ.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.viewModelScope
import de.bgghome.webtrees.nativ.R
import de.bgghome.webtrees.nativ.data.ImagePrep
import java.io.IOException
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
                it.copy(
                    media = if (reset) result.data else it.media + result.data,
                    mediaNextPage = result.nextPage, loadingMedia = false, mediaLoaded = true,
                )
            }
        } catch (e: Exception) {
            fail(e)
            uiState.update { it.copy(loadingMedia = false, mediaLoaded = true) }
        }
    }
}

fun AppViewModel.uploadPhoto(uri: Uri, title: String) = write(R.string.msg_photo_uploaded) { tree, xref ->
    val resolver = getApplication<Application>().contentResolver
    var name = "foto.jpg"

    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0)?.let { name = it }
    }

    // Verkleinern und drehen (ImagePrep); was sich nicht als Bild lesen laesst, geht unveraendert hoch.
    // Limit des Servers (meist 2-8 MB) mit etwas Luft fuer den Rest der Anfrage; aeltere Module nennen es nicht.
    val limit = (uiState.value.info?.maxUpload?.takeIf { it > 0 } ?: AppViewModel.DEFAULT_MAX_UPLOAD) * 9 / 10
    val prepared = withContext(Dispatchers.IO) {
        runCatching { ImagePrep.toUploadJpeg(resolver, uri, limit) }
            .onFailure { Log.w("wtAnd", "Bild liess sich nicht verkleinern", it) }
            .getOrNull()
    }
    Log.i("wtAnd", "Upload $name: ${prepared?.size} Bytes vorbereitet, Limit $limit (Server: ${uiState.value.info?.maxUpload})")

    val mime = resolver.getType(uri).orEmpty()

    when {
        prepared != null -> {
            val jpegName = name.substringBeforeLast('.') + ".jpg"
            client.uploadMedia(tree, xref, prepared, jpegName, "image/jpeg", title)
        }
        // Ein Bild, das sich nicht verkleinern liess: nicht das riesige Original hinterherschicken - das scheitert
        // am Limit des Servers nur mit einer nichtssagenden Meldung.
        mime.startsWith("image/") -> throw UserMessageException(text(R.string.err_image_prepare))
        else -> {
            val bytes = withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("file not readable")
            }
            if (bytes.size > limit) {
                throw UserMessageException(text(R.string.err_file_too_large, bytes.size / 1048576 + 1, limit / 1048576))
            }
            client.uploadMedia(tree, xref, bytes, name, mime.ifEmpty { "application/octet-stream" }, title)
        }
    }
}
