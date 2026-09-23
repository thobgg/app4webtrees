package de.bgghome.webtrees.nativ.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Jahrestage: die naechsten Geburts-, Heirats- und Todestage fuer die Startseite und die taegliche Erinnerung.
// Erweiterungen von AppViewModel.

val AppViewModel.anniversariesSupported: Boolean get() = (uiState.value.info?.api ?: 0) >= AppViewModel.API_ANNIVERSARIES

internal fun AppViewModel.loadAnniversaries() {
    val tree = uiState.value.tree ?: return
    if (!anniversariesSupported) return

    viewModelScope.launch {
        runCatching { client.anniversaries(tree.name, 14) }
            .onSuccess { list -> uiState.update { it.copy(anniversaries = list.data) } }
    }
}

/** Taegliche Erinnerung ein-/ausschalten. Die Erlaubnis fuer Benachrichtigungen holt die Oberflaeche vorher ein. */
fun AppViewModel.setReminders(on: Boolean) {
    settings.reminders = on
    uiState.update { it.copy(reminders = on) }
    AnniversaryWorker.schedule(getApplication(), on)
}
