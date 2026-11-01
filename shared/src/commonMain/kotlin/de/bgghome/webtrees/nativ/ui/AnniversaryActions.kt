package de.bgghome.webtrees.nativ.ui

import de.bgghome.webtrees.nativ.api.hat
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Jahrestage: die naechsten Geburts-, Heirats- und Todestage fuer die Startseite und die taegliche Erinnerung.
// Erweiterungen von AppViewModel.

val AppViewModel.anniversariesSupported: Boolean get() = uiState.value.info.hat(AppViewModel.API_ANNIVERSARIES)

internal fun AppViewModel.loadAnniversaries() {
    val tree = uiState.value.tree ?: return
    if (!anniversariesSupported) return

    viewModelScope.launch {
        runCatching { client.anniversaries(tree.name, 14) }
            .onSuccess { list -> uiState.update { it.copy(anniversaries = list.data) } }
    }
}

/**
 * Gehoert der Jahrestag zu Lebenden? Todestage nie, Geburtstage nur lebender Personen, Hochzeitstage nur, wenn beide
 * Partner leben. Die Route liefert je Tag die Lebenden zuerst, darum fehlen beim Filtern keine Lebenden.
 */
fun de.bgghome.webtrees.nativ.api.Anniversary.vonLebenden(): Boolean =
    tag != "DEAT" && couple.ifEmpty { listOfNotNull(person) }.let { leute -> leute.isNotEmpty() && leute.none { it.isDead } }

/** Jahrestage nur von Lebenden zeigen (Startseite und taegliche Erinnerung). */
fun AppViewModel.setAnniversaryLivingOnly(on: Boolean) {
    settings.anniversaryLivingOnly = on
    uiState.update { it.copy(anniversaryLivingOnly = on) }
}

/** Taegliche Erinnerung ein-/ausschalten. Die Erlaubnis fuer Benachrichtigungen holt die Oberflaeche vorher ein. */
fun AppViewModel.setReminders(on: Boolean) {
    settings.reminders = on
    uiState.update { it.copy(reminders = on) }
    plattform.setReminders(on)
}
