package de.bgghome.webtrees.nativ.ui

import androidx.lifecycle.viewModelScope
import de.bgghome.webtrees.nativ.api.ApiException
import de.bgghome.webtrees.nativ.api.ArchiveEntry
import de.bgghome.webtrees.nativ.api.MediaJson
import de.bgghome.webtrees.nativ.api.NotJsonException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Archiv (Modul "Sammlungen" auf dem Server): Uebersicht, eine Sammlung seitenweise, und der Betrachter fuer Bilder -
// auch fuer die Fotos des Baums und einer Person. Erweiterungen von AppViewModel.

/** So viele Eintraege je Seite holt die App aus einer Sammlung (das Modul erlaubt 10 bis 200). */
internal const val COLLECTION_PAGE = 48

/**
 * Fragt beim Baumwechsel einmal nach, ob der Server das Archiv anbietet. Kein JSON heisst: das Modul fehlt (webtrees
 * antwortet mit seiner 404-Seite). JSON mit Fehler heisst: kein Zugriff (Gast oder kein Mitglied). Beides ist kein Fehler
 * fuer den Benutzer, der Archiv-Reiter bleibt einfach weg. Nur bei Netzproblemen bleibt es bei "unbekannt", damit der
 * naechste Versuch (refresh) es noch einmal probiert.
 */
internal fun AppViewModel.probeArchive() {
    val tree = uiState.value.tree ?: return
    uiState.update { it.copy(archiveStatus = ArchiveStatus.Loading) }

    viewModelScope.launch {
        val status = try {
            val overview = client.archive(tree.name)
            uiState.update {
                if (it.tree?.name == tree.name) it.copy(archive = overview, archiveStatus = ArchiveStatus.Ready) else it
            }
            return@launch
        } catch (e: NotJsonException) {
            ArchiveStatus.Missing
        } catch (e: ApiException) {
            ArchiveStatus.Forbidden
        } catch (e: Exception) {
            ArchiveStatus.Unknown
        }

        uiState.update {
            if (it.tree?.name == tree.name) it.copy(archive = null, archiveStatus = status, photosTab = PhotosTab.Tree) else it
        }
    }
}

fun AppViewModel.setPhotosTab(tab: PhotosTab) = uiState.update { it.copy(photosTab = tab) }

/** Oeffnet eine Sammlung mit ihrer ersten Seite. typ: nur fuer nicht eingebundene Medien (kategorie "__unlinked__"). */
fun AppViewModel.openCollection(slug: String, typ: String = "") {
    uiState.update { it.copy(collection = null, collectionEntries = emptyList(), collectionNextPage = null, photosTab = PhotosTab.Archive) }
    loadCollection(slug, typ, 1)
}

fun AppViewModel.loadMoreCollection() {
    val state = uiState.value
    val collection = state.collection ?: return
    val next = state.collectionNextPage ?: return
    if (!state.loadingCollection) loadCollection(collection.slug, collection.typ.orEmpty(), next)
}

fun AppViewModel.closeCollection() =
    uiState.update { it.copy(collection = null, collectionEntries = emptyList(), collectionNextPage = null, loadingCollection = false) }

internal fun AppViewModel.loadCollection(slug: String, typ: String, page: Int) {
    val tree = uiState.value.tree ?: return
    uiState.update { it.copy(loadingCollection = true) }

    viewModelScope.launch {
        try {
            val result = client.collection(tree.name, slug, typ, page, COLLECTION_PAGE)
            uiState.update {
                val entries = if (page == 1) result.eintraege else it.collectionEntries + result.eintraege
                it.copy(
                    collection = result,
                    collectionEntries = entries,
                    collectionNextPage = if (result.seite < result.seiten) result.seite + 1 else null,
                    loadingCollection = false,
                    // Ist der Betrachter gerade in dieser Sammlung unterwegs, bekommt er die neuen Bilder dazu.
                    viewer = it.viewer?.let { v -> if (v.source == ViewerSource.Collection) v.copy(items = viewerItems(entries)) else v },
                )
            }
        } catch (e: Exception) {
            fail(e)
            uiState.update { it.copy(loadingCollection = false) }
        }
    }
}

// ── Betrachter ───────────────────────────────────────────────────

fun AppViewModel.openArchiveViewer(entry: ArchiveEntry) {
    val images = uiState.value.collectionEntries.filter { it.istBild }
    val index = images.indexOf(entry)
    if (index < 0) return
    uiState.update { it.copy(viewer = ViewerState(viewerItems(images), index, ViewerSource.Collection)) }
}

/**
 * Fotos des Baums (Fotouebersicht) oder einer Person (Profil): nur Bilder, PDFs und anderes bleiben beim Web.
 * owner: im Profil die Person, der die Bilder gehoeren - die Profilantwort nennt keine verknuepften Personen.
 */
fun AppViewModel.openMediaViewer(source: ViewerSource, media: List<MediaJson>, item: MediaJson, owner: String? = null) {
    val images = media.filter { it.isImage }
    val index = images.indexOf(item)
    if (index < 0) return
    uiState.update { it.copy(viewer = ViewerState(images.map { m -> viewerItem(m, owner) }, index, source)) }
}

/** Der Betrachter ist weitergeblaettert - kurz vor dem Ende die naechste Seite holen, damit das Wischen nicht abreisst. */
fun AppViewModel.viewerMoved(index: Int) {
    uiState.update { it.copy(viewer = it.viewer?.copy(index = index)) }
    val viewer = uiState.value.viewer ?: return

    if (index >= viewer.items.size - 3) {
        when (viewer.source) {
            ViewerSource.Tree -> loadMoreMedia()
            ViewerSource.Collection -> loadMoreCollection()
            ViewerSource.Profile -> Unit
        }
    }
}

fun AppViewModel.closeViewer() = uiState.update { it.copy(viewer = null) }

internal fun viewerItems(entries: List<ArchiveEntry>): List<ViewerItem> = entries.filter { it.istBild }.map(::viewerItem)

internal fun linkState(entry: ArchiveEntry): LinkState = when {
    entry.personen.isNotEmpty() -> LinkState.Persons
    entry.xref != null -> LinkState.ObjectOnly
    else -> LinkState.FileOnly
}

internal fun viewerItem(entry: ArchiveEntry) = ViewerItem(
    image = entry.vollbild ?: entry.original,
    thumb = entry.kachel,
    caption = entry.bildunterschrift.ifEmpty { entry.datei },
    subtitle = listOf(entry.datum, entry.personen.joinToString(", ") { it.name }).filter { it.isNotBlank() }.joinToString(" · "),
    webUrl = entry.seite ?: entry.original,
    link = linkState(entry),
)

internal fun viewerItem(media: MediaJson, owner: String? = null) = ViewerItem(
    image = media.file.ifEmpty { media.thumb.orEmpty() },
    thumb = media.thumb,
    caption = media.title,
    subtitle = media.people.joinToString(", ") { it.name }.ifEmpty { owner.orEmpty() },
    webUrl = media.url,
    link = if (media.people.isNotEmpty() || owner != null) LinkState.Persons else LinkState.ObjectOnly,
)
