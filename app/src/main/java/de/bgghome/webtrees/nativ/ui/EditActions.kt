package de.bgghome.webtrees.nativ.ui

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import de.bgghome.webtrees.nativ.R
import de.bgghome.webtrees.nativ.api.AddIndividualRequest
import de.bgghome.webtrees.nativ.api.FactRequest
import de.bgghome.webtrees.nativ.api.WriteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Bearbeiten und Schreibzugriffe: Ereignisse, Verwandte, Verknuepfungen, Loeschen - und die Freigabe ausstehender
// Aenderungen durch Moderatoren. Jeder Schreibzugriff laeuft durch write(). Erweiterungen von AppViewModel.

/**
 * Ortsvorschlaege fuer die Formulare, oder null, wenn es keine gibt: das Modul kennt sie ab API-Stufe 8 und
 * liefert sie nur Bearbeitern (wer nicht bearbeiten darf, sieht die Formulare ohnehin nicht).
 */
fun AppViewModel.placeSuggestions(): PlaceSuggest? {
    val state = uiState.value
    val tree = state.tree?.takeIf { it.canEdit } ?: return null

    if ((state.info?.api ?: 0) < AppViewModel.API_PLACES) return null

    val lookup: PlaceSuggest = { query ->
        try {
            client.places(tree.name, query).data
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Vorschlaege sind Beiwerk: klappt die Anfrage nicht, tippt man den Ort eben aus - keine Fehlermeldung.
            emptyList()
        }
    }

    return lookup
}

fun AppViewModel.saveFact(request: FactRequest, record: String? = null) = write(R.string.msg_saved) { tree, xref ->
    client.saveFact(tree, record ?: xref, request)
}

fun AppViewModel.deleteFact(factId: String, record: String? = null) = write(R.string.msg_deleted) { tree, xref ->
    client.deleteFact(tree, record ?: xref, factId)
}

fun AppViewModel.unlink(family: String, individual: String) = write(R.string.msg_unlinked) { tree, _ ->
    client.unlink(tree, family, individual)
}

/** Person loeschen. Danach gibt es sie nicht mehr: Profil schliessen, notfalls eine andere Mittelperson nehmen. */
fun AppViewModel.deletePerson(xref: String) {
    val tree = uiState.value.tree ?: return

    uiState.update { it.copy(busy = true) }

    viewModelScope.launch {
        try {
            val result = client.deleteRecord(tree.name, xref)
            val done = text(R.string.msg_person_deleted)
            val message = if (result.pending) text(R.string.msg_pending, done) else done
            val wasRoot = uiState.value.root == xref

            uiState.update {
                it.copy(
                    busy = false, message = message, selected = null, detail = null, profileOpen = false,
                    pedigree = null, descendants = null, mediaLoaded = false,
                    recent = it.recent.filter { p -> p.xref != xref },
                    rootHistory = it.rootHistory.filter { r -> r != xref },
                )
            }
            loadPeople(reset = true)
            loadPending()

            if (wasRoot) {
                val next = uiState.value.rootHistory.lastOrNull() ?: uiState.value.home?.takeIf { it != xref }
                if (next != null) setRoot(next, remember = false) else uiState.update { it.copy(root = null) }
            }
        } catch (e: Exception) {
            uiState.update { it.copy(busy = false) }
            fail(e)
        }
    }
}

fun AppViewModel.addRelative(request: AddIndividualRequest) = write(R.string.msg_person_created) { tree, _ ->
    client.addIndividual(tree, request)
}

internal fun AppViewModel.write(@StringRes done: Int, action: suspend (tree: String, xref: String) -> WriteResult) {
    val tree = uiState.value.tree ?: return
    val xref = uiState.value.selected ?: return

    uiState.update { it.copy(busy = true) }

    viewModelScope.launch {
        try {
            val result = action(tree.name, xref)
            val message = if (result.pending) text(R.string.msg_pending, text(done)) else text(done)
            uiState.update { it.copy(busy = false, message = message) }

            // Panel und Baum zeigen den neuen Stand; die Mittelperson bleibt, wo sie ist.
            uiState.update { it.copy(pedigree = null, descendants = null, mediaLoaded = false) }
            select(xref)
            loadPeople(reset = true)
            loadAnniversaries()
            loadPending()
        } catch (e: Exception) {
            uiState.update { it.copy(busy = false) }
            fail(e)
        }
    }
}

// ── Freigabe ─────────────────────────────────────────────────────

internal fun AppViewModel.loadPending() {
    val tree = uiState.value.tree ?: return
    if (!tree.canModerate) return

    viewModelScope.launch {
        runCatching { client.pending(tree.name) }.onSuccess { list -> uiState.update { it.copy(pending = list.data) } }
    }
}

/** Freigabe: xref = null heisst "alle". Danach zeigen Baum und Profil den neuen Stand. */
fun AppViewModel.moderate(xref: String?, accept: Boolean) {
    val tree = uiState.value.tree ?: return

    uiState.update { it.copy(busy = true) }

    viewModelScope.launch {
        try {
            client.moderate(tree.name, xref, accept)
            val message = text(if (accept) R.string.msg_accepted else R.string.msg_rejected)
            uiState.update { it.copy(busy = false, message = message, pedigree = null, descendants = null) }
            loadPending()
            loadPeople(reset = true)

            // Eine verworfene neue Person gibt es nicht mehr - dann nicht im Profil stehen lassen.
            val selected = uiState.value.selected
            if (selected != null) select(selected)
        } catch (e: Exception) {
            uiState.update { it.copy(busy = false) }
            fail(e)
        }
    }
}
