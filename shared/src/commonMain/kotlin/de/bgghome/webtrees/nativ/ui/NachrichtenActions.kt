package de.bgghome.webtrees.nativ.ui

import androidx.lifecycle.viewModelScope
import de.bgghome.webtrees.nativ.api.API_MESSAGES
import de.bgghome.webtrees.nativ.api.MessageJson
import de.bgghome.webtrees.nativ.api.MessageRequest
import de.bgghome.webtrees.nativ.res.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Nachrichten: webtrees' eigenes Nachrichtensystem (Block "Nachrichten" auf "Meine Seite"), ab API-Stufe 42.
// Ein Gelesen-Kennzeichen kennt webtrees nicht - die App merkt sich je Server und Benutzer die hoechste gesehene Nummer.
// Erweiterungen von AppViewModel.

val AppViewModel.messagesSupported: Boolean
    get() = (uiState.value.info?.api ?: 0) >= API_MESSAGES && uiState.value.info?.user?.loggedIn == true

/** Zahl der Nachrichten im Posteingang: aus der geladenen Liste, solange sie vollstaendig ist, sonst aus Info. */
val AppViewModel.messagesTotal: Int
    get() = uiState.value.let { if (it.messagesNextPage == null && it.messages.isNotEmpty()) it.messages.size else it.info?.user?.messages ?: 0 }

/** Nachrichten, die der Benutzer noch nicht gesehen hat (nur unter den geladenen). */
fun AppViewModel.unreadMessages(): List<MessageJson> = uiState.value.let { s -> s.messages.filter { it.id > s.messagesSeen } }

internal fun AppViewModel.loadMessages() {
    val tree = uiState.value.tree ?: return
    if (!messagesSupported) return
    viewModelScope.launch {
        runCatching { client.messages(tree.name, 1) }.onSuccess { page ->
            uiState.update { it.copy(messages = page.messages, messagesNextPage = page.nextPage, recipients = page.recipients, messagesSeen = settings.messagesSeen) }
        }
    }
}

fun AppViewModel.loadMoreMessages() {
    val tree = uiState.value.tree ?: return
    val next = uiState.value.messagesNextPage ?: return
    viewModelScope.launch {
        try {
            val page = client.messages(tree.name, next)
            uiState.update { it.copy(messages = it.messages + page.messages, messagesNextPage = page.nextPage, recipients = page.recipients) }
        } catch (e: Exception) {
            fail(e)
        }
    }
}

/** Die Liste wurde angesehen: alles bis zur hoechsten Nummer gilt als gesehen. */
fun AppViewModel.markMessagesSeen() {
    val top = uiState.value.messages.maxOfOrNull { it.id } ?: return
    if (top <= uiState.value.messagesSeen) return
    settings.messagesSeen = top
    uiState.update { it.copy(messagesSeen = top) }
}

/** Sendet an einen oder mehrere Benutzer; url = Seite eines geteilten Datensatzes (steht so in der E-Mail). */
fun AppViewModel.sendMessage(to: List<String>, subject: String, body: String, url: String? = null, onDone: (Boolean) -> Unit = {}) {
    val tree = uiState.value.tree ?: return
    viewModelScope.launch {
        try {
            uiState.update { it.copy(busy = true) }
            client.sendMessage(tree.name, MessageRequest(to, subject.trim(), body.trim(), url?.ifBlank { null }))
            uiState.update { it.copy(busy = false, message = text(Res.string.messages_sent)) }
            onDone(true)
        } catch (e: Exception) {
            uiState.update { it.copy(busy = false) }
            fail(e)
            onDone(false)
        }
    }
}

fun AppViewModel.deleteMessage(id: Int) {
    val tree = uiState.value.tree ?: return
    viewModelScope.launch {
        try {
            client.deleteMessages(tree.name, listOf(id))
            uiState.update { s -> s.copy(messages = s.messages.filter { it.id != id }, message = text(Res.string.messages_deleted)) }
        } catch (e: Exception) {
            fail(e)
        }
    }
}

/** Die App kommt wieder in den Vordergrund (Android onResume): Nachrichten und wartende Freigaben frisch holen. */
fun AppViewModel.resumed() {
    if (uiState.value.screen != Screen.Main) return
    loadMessages()
    loadPending()
}
