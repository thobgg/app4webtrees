package de.bgghome.webtrees.nativ.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Merkliste: gemerkte Personen des Benutzers je Baum, auf dem Server als Benutzereinstellung (ab API-Stufe 11).
// Erweiterungen von AppViewModel.

val AppViewModel.bookmarksSupported: Boolean
    get() = (uiState.value.info?.api ?: 0) >= AppViewModel.API_BOOKMARKS && uiState.value.info?.user?.loggedIn == true

fun AppViewModel.isBookmarked(xref: String): Boolean = uiState.value.bookmarks.any { it.xref == xref }

internal fun AppViewModel.loadBookmarks() {
    val tree = uiState.value.tree ?: return
    if (!bookmarksSupported) return
    viewModelScope.launch {
        runCatching { client.bookmarks(tree.name) }.onSuccess { list -> uiState.update { it.copy(bookmarks = list.data) } }
    }
}

fun AppViewModel.toggleBookmark(xref: String) {
    val tree = uiState.value.tree ?: return
    val add = !isBookmarked(xref)
    viewModelScope.launch {
        try {
            val list = client.setBookmark(tree.name, xref, add)
            uiState.update { it.copy(bookmarks = list.data) }
        } catch (e: Exception) {
            fail(e)
        }
    }
}
