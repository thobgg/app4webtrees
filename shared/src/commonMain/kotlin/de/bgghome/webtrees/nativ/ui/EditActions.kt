package de.bgghome.webtrees.nativ.ui

import org.jetbrains.compose.resources.StringResource
import androidx.lifecycle.viewModelScope
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.api.AddIndividualRequest
import de.bgghome.webtrees.nativ.api.CitationRequest
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

fun AppViewModel.saveFact(request: FactRequest, record: String? = null) = write(Res.string.msg_saved) { tree, xref ->
    client.saveFact(tree, record ?: xref, request)
}

/**
 * Ereignis speichern und gleich einen Quellenverweis daran haengen (Issue 39). Die Route Fact liefert die Kennung des
 * Ereignisses nicht zurueck, und die Kennung ist der Hash des Ereignistexts - beim Aendern entsteht also auch eine neue.
 * Darum: Kennungen des Datensatzes vor und nach dem Speichern vergleichen; genau eine neue (oder beim unveraenderten
 * Ereignis die alte) bekommt den Verweis. Bleibt es mehrdeutig, ist das Ereignis trotzdem gespeichert, und die Meldung sagt,
 * dass die Quelle fehlt. [record] = Familie fuer Familienereignisse, null = die gewaehlte Person.
 */
fun AppViewModel.saveFactMitZitat(request: FactRequest, record: String?, zitat: CitationRequest) {
    val tree = uiState.value.tree ?: return
    val xref = uiState.value.selected ?: return
    val ziel = record ?: xref
    uiState.update { it.copy(busy = true) }
    viewModelScope.launch {
        var fehler: Exception? = null
        var meldung: String? = null
        try {
            val vorher = ereignisKennungen(tree.name, xref, ziel)
            var wartet = client.saveFact(tree.name, ziel, request).pending
            val nachher = ereignisKennungen(tree.name, xref, ziel)
            val neu = nachher - vorher
            val id = neu.singleOrNull() ?: request.factId?.takeIf { neu.isEmpty() && it in nachher }
            if (id == null) {
                meldung = text(Res.string.fact_source_not_attached)
            } else {
                wartet = client.citation(tree.name, ziel, zitat.copy(factId = id)).pending || wartet
                meldung = if (wartet) text(Res.string.msg_pending, text(Res.string.msg_saved)) else text(Res.string.msg_saved)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fehler = e
        }
        uiState.update { it.copy(busy = false, message = meldung ?: it.message, pedigree = null, descendants = null, mediaLoaded = false) }
        fehler?.let(::fail)
        select(xref)
        loadPeople(reset = true)
        loadAnniversaries()
        loadPending()
    }
}

/** Kennungen der Ereignisse von [record] (die Person selbst oder eine ihrer Familien), frisch vom Server. */
private suspend fun AppViewModel.ereignisKennungen(tree: String, person: String, record: String): Set<String> {
    val detail = client.individual(tree, person)
    val facts = if (record == person) detail.facts else (detail.spouseFamilies + detail.parentFamilies).firstOrNull { it.xref == record }?.facts.orEmpty()
    return facts.map { it.id }.toSet()
}

/**
 * Mehrere Ereignisse einer Person nacheinander speichern (Eingabeformular am Desktop). [items]: Datensatz (null = die
 * Person selbst, sonst die Familie) und Anfrage. Jedes gelungene meldet [onGespeichert] sofort - so legt ein Fehler
 * mittendrin beim naechsten Versuch nichts doppelt an. Neu geladen wird am Ende, sobald etwas gespeichert wurde.
 */
fun AppViewModel.saveFacts(xref: String, items: List<Pair<String?, FactRequest>>, onGespeichert: (Int) -> Unit, onFertig: (fehler: Boolean) -> Unit) {
    val tree = uiState.value.tree ?: return
    uiState.update { it.copy(busy = true) }
    viewModelScope.launch {
        var gespeichert = 0
        var wartet = false
        var fehler: Exception? = null
        for ((i, item) in items.withIndex()) {
            try {
                val result = client.saveFact(tree.name, item.first ?: xref, item.second)
                wartet = wartet || result.pending
                gespeichert++
                onGespeichert(i)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fehler = e
                break
            }
        }
        uiState.update { it.copy(busy = false, message = if (fehler == null) (if (wartet) text(Res.string.msg_pending, text(Res.string.msg_saved)) else text(Res.string.msg_saved)) else it.message) }
        fehler?.let(::fail)
        if (gespeichert > 0) {
            uiState.update { it.copy(pedigree = null, descendants = null, mediaLoaded = false) }
            if (uiState.value.selected == xref) select(xref)
            loadPeople(reset = true)
            loadAnniversaries()
            loadPending()
        }
        onFertig(fehler != null)
    }
}

/**
 * Quellenverweise schreiben, auch mehrere nacheinander (Kopieren): [items] = Datensatz (Person oder Familie) und
 * Anfrage. Bricht beim ersten Fehler ab, meldet dann aber trotzdem neu geladen, was schon gespeichert wurde.
 */
fun AppViewModel.saveCitations(items: List<Pair<String, CitationRequest>>, onFertig: (fehler: Boolean) -> Unit = {}) {
    val tree = uiState.value.tree ?: return
    val selected = uiState.value.selected
    uiState.update { it.copy(busy = true) }
    viewModelScope.launch {
        var gespeichert = 0
        var wartet = false
        var fehler: Exception? = null
        for ((xref, anfrage) in items) {
            try {
                wartet = wartet || client.citation(tree.name, xref, anfrage).pending
                gespeichert++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fehler = e
                break
            }
        }
        uiState.update { it.copy(busy = false, message = if (fehler == null) (if (wartet) text(Res.string.msg_pending, text(Res.string.msg_saved)) else text(Res.string.msg_saved)) else it.message) }
        fehler?.let(::fail)
        if (gespeichert > 0) {
            uiState.update { it.copy(pedigree = null, descendants = null) }
            selected?.let { select(it) }
            loadPending()
        }
        onFertig(fehler != null)
    }
}

/** Paten/Trauzeugen eines Ereignisses an [record] (Person oder Familie) schreiben, danach die Person neu laden. */
fun AppViewModel.saveAssociation(record: String, request: de.bgghome.webtrees.nativ.api.AssociationRequest, onFertig: (fehler: Boolean) -> Unit = {}) {
    val tree = uiState.value.tree ?: return
    val selected = uiState.value.selected
    uiState.update { it.copy(busy = true) }
    viewModelScope.launch {
        var fehler: Exception? = null
        var wartet = false
        try {
            wartet = client.association(tree.name, record, request).pending
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fehler = e
        }
        uiState.update { it.copy(busy = false, message = if (fehler == null) (if (wartet) text(Res.string.msg_pending, text(Res.string.msg_saved)) else text(Res.string.msg_saved)) else it.message) }
        fehler?.let(::fail)
        if (fehler == null) {
            uiState.update { it.copy(pedigree = null, descendants = null) }
            selected?.let { select(it) }
            loadPending()
        }
        onFertig(fehler != null)
    }
}

fun AppViewModel.deleteFact(factId: String, record: String? = null) = write(Res.string.msg_deleted) { tree, xref ->
    client.deleteFact(tree, record ?: xref, factId)
}

fun AppViewModel.unlink(family: String, individual: String) = write(Res.string.msg_unlinked) { tree, _ ->
    client.unlink(tree, family, individual)
}

/** Person loeschen. Danach gibt es sie nicht mehr: Profil schliessen, notfalls eine andere Mittelperson nehmen. */
fun AppViewModel.deletePerson(xref: String) {
    val tree = uiState.value.tree ?: return

    uiState.update { it.copy(busy = true) }

    viewModelScope.launch {
        try {
            val result = client.deleteRecord(tree.name, xref)
            val done = text(Res.string.msg_person_deleted)
            val message = if (result.pending) text(Res.string.msg_pending, done) else done
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

/** Vorhandene Person als Verwandten anfuegen (Route Link) - statt eine neue anzulegen. */
fun AppViewModel.linkRelative(request: de.bgghome.webtrees.nativ.api.LinkRequest) = write(Res.string.msg_person_linked) { tree, _ ->
    client.link(tree, request)
}

fun AppViewModel.addRelative(request: AddIndividualRequest) = write(Res.string.msg_person_created) { tree, _ ->
    client.addIndividual(tree, request)
}

internal fun AppViewModel.write(done: StringResource, action: suspend (tree: String, xref: String) -> WriteResult) {
    val tree = uiState.value.tree ?: return
    val xref = uiState.value.selected ?: return

    uiState.update { it.copy(busy = true) }

    viewModelScope.launch {
        try {
            val result = action(tree.name, xref)
            val message = if (result.pending) text(Res.string.msg_pending, text(done)) else text(done)
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
            val message = text(if (accept) Res.string.msg_accepted else Res.string.msg_rejected)
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
