package de.bgghome.webtrees.nativ.ui

import de.bgghome.webtrees.nativ.api.hat
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Merkliste: gemerkte Personen des Benutzers je Baum, auf dem Server als Benutzereinstellung (ab API-Stufe 11).
// Erweiterungen von AppViewModel.

val AppViewModel.bookmarksSupported: Boolean
    get() = uiState.value.info.hat(AppViewModel.API_BOOKMARKS) && uiState.value.info?.user?.loggedIn == true

fun AppViewModel.isBookmarked(xref: String): Boolean = uiState.value.bookmarks.any { it.xref == xref }

internal fun AppViewModel.loadBookmarks() {
    val tree = uiState.value.tree ?: return
    if (!bookmarksSupported) return
    viewModelScope.launch {
        runCatching { client.bookmarks(tree.name) }.onSuccess { list -> uiState.update { it.copy(bookmarks = list.data, treeBookmarks = list.treeFavorites) } }
    }
}

fun AppViewModel.toggleBookmark(xref: String) {
    val tree = uiState.value.tree ?: return
    val add = !isBookmarked(xref)
    viewModelScope.launch {
        try {
            val list = client.setBookmark(tree.name, xref, add)
            uiState.update { it.copy(bookmarks = list.data, treeBookmarks = list.treeFavorites) }
        } catch (e: Exception) {
            fail(e)
        }
    }
}

/** Ab API-Stufe 30: Aufgaben, Reihenfolge, Aenderungsverlauf, Favoriten des Stammbaums. */
val AppViewModel.tasksSupported: Boolean
    get() = uiState.value.info.hat(de.bgghome.webtrees.nativ.api.API_TASKS) && uiState.value.info?.user?.loggedIn == true

fun AppViewModel.isTreeBookmarked(xref: String): Boolean = uiState.value.treeBookmarks.any { it.xref == xref }

/** Favoriten des Stammbaums setzen oder entfernen (nur Verwalter, ab Stufe 30). */
fun AppViewModel.toggleTreeBookmark(xref: String) {
    val tree = uiState.value.tree ?: return
    val add = !isTreeBookmarked(xref)
    viewModelScope.launch {
        try {
            val list = client.setBookmark(tree.name, xref, add, forTree = true)
            uiState.update { it.copy(bookmarks = list.data, treeBookmarks = list.treeFavorites) }
        } catch (e: Exception) {
            fail(e)
        }
    }
}

/** Reihenfolge aendern und die Person danach neu laden (ab Stufe 30). */
fun AppViewModel.reorder(xref: String, type: String, order: List<String>, reload: String, onDone: () -> Unit = {}) {
    val tree = uiState.value.tree ?: return
    viewModelScope.launch {
        try {
            client.reorder(tree.name, xref, type, order)
            select(reload)
            uiState.update { it.copy(pedigree = null, descendants = null) }
            onDone()
        } catch (e: Exception) {
            fail(e)
        }
    }
}

// Startperson festlegen (ab API-Stufe 24): eigene Standardperson oder, fuer Verwalter, die des Stammbaums.

val AppViewModel.startPersonSupported: Boolean
    get() = uiState.value.info.hat(de.bgghome.webtrees.nativ.api.API_START_PERSON) && uiState.value.info?.user?.loggedIn == true

fun AppViewModel.setStartPerson(xref: String, forTree: Boolean, onDone: () -> Unit = {}) {
    val tree = uiState.value.tree ?: return
    viewModelScope.launch {
        try {
            val r = client.setStartPerson(tree.name, xref, forTree)
            uiState.update {
                it.copy(
                    home = r.startXref.ifEmpty { it.home },
                    tree = it.tree?.copy(startXref = r.startXref, defaultXref = r.defaultXref, treeDefaultXref = r.treeDefaultXref),
                )
            }
            onDone()
        } catch (e: Exception) {
            fail(e)
        }
    }
}
