package de.bgghome.webtrees.nativ.ui

import androidx.lifecycle.viewModelScope
import de.bgghome.webtrees.nativ.api.DescendantFamily
import de.bgghome.webtrees.nativ.api.DescendantNode
import de.bgghome.webtrees.nativ.api.Pedigree
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.ui.tree.Sibling
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Baum und Navigation: Personenliste und Suche, Mittelperson und Profil-Panel, Ahnen/Nachkommen/Geschwister laden,
// nach oben aufklappen, Bereiche und Zurueck-Taste. Erweiterungen von AppViewModel.

/** Handy kompakter: beim ersten Start nur 3 Ahnen-Generationen statt 4 (spaeter frei waehlbar). */
fun AppViewModel.setWide(wide: Boolean) {
    if (layoutKnown) return
    layoutKnown = true
    if (!wide) uiState.update { it.copy(ancestorGenerations = 3) }
}

fun AppViewModel.setSection(section: Section) {
    uiState.update { it.copy(section = section, treeFullscreen = false, profileOpen = false) }
    if (section == Section.Photos && !uiState.value.mediaLoaded) loadMedia(reset = true)
}

// ── Liste ────────────────────────────────────────────────────────

fun AppViewModel.search(query: String) {
    uiState.update { it.copy(query = query) }
    searchJob?.cancel()
    searchJob = viewModelScope.launch {
        delay(300)
        loadPeople(reset = true)
    }
}

fun AppViewModel.loadMore() {
    if (uiState.value.nextPage != null && !uiState.value.loadingPeople) loadPeople(reset = false)
}

internal fun AppViewModel.loadPeople(reset: Boolean) {
    val tree = uiState.value.tree ?: return
    val page = if (reset) 1 else uiState.value.nextPage ?: return
    val query = uiState.value.query

    uiState.update { it.copy(loadingPeople = true) }

    viewModelScope.launch {
        try {
            val result = client.individuals(tree.name, query, page)
            uiState.update {
                // Antwort einer ueberholten Suche verwerfen
                if (it.query != query) it else it.copy(
                    people = if (reset) result.data else it.people + result.data,
                    nextPage = result.nextPage,
                    loadingPeople = false,
                )
            }
            if (uiState.value.root == null && query.isEmpty()) {
                result.data.firstOrNull { !it.isPrivate }?.let { first ->
                    uiState.update { it.copy(home = it.home ?: first.xref) }
                    setRoot(first.xref, remember = false)
                }
            }
        } catch (e: Exception) {
            fail(e)
            uiState.update { it.copy(loadingPeople = false) }
        }
    }
}

// ── Baum und Profil-Panel ────────────────────────────────────────

/** Neue Mittelperson des Baums; sie erscheint auch im Profil-Panel. */
fun AppViewModel.setRoot(xref: String, remember: Boolean = true) {
    uiState.update {
        val history = if (remember && it.root != null && it.root != xref) it.rootHistory + it.root else it.rootHistory
        it.copy(root = xref, rootHistory = history, pedigree = null, descendants = null, section = Section.Tree, profileOpen = false)
    }
    select(xref)
}

/** Person ins Profil-Panel holen. Die Mittelperson des Baums bleibt. */
fun AppViewModel.select(xref: String) {
    val tree = uiState.value.tree ?: return
    val home = uiState.value.home

    uiState.update { it.copy(selected = xref, loadingDetail = true) }

    viewModelScope.launch {
        try {
            val detail = client.individual(tree.name, xref, relativeTo = home.takeIf { it != xref }.orEmpty())
            uiState.update {
                if (it.selected != xref) it else it.copy(
                    detail = detail, loadingDetail = false,
                    recent = (listOf(detail.person) + it.recent.filter { p -> p.xref != xref }).take(12),
                )
            }
        } catch (e: Exception) {
            fail(e)
            uiState.update { it.copy(loadingDetail = false, addRelativeFor = null) }
        }
    }
}

fun AppViewModel.closePanel() = uiState.update {
    it.copy(selected = null, detail = null, profileOpen = false, addRelativeFor = null)
}

/**
 * Tipp auf eine Karte. Tablet: das Profil daneben zeigt die Person. Handy: ihr Profil oeffnet sich als eigene
 * Seite - wie in gaengigen Stammbaum-Apps; "Zurueck" fuehrt in den Baum, der unveraendert stehen bleibt.
 */
fun AppViewModel.openPerson(xref: String, wide: Boolean) {
    select(xref)
    if (!wide) uiState.update { it.copy(profileOpen = true) }
}

/** Nachkommen-Generationen im Baum (0 bis 4); weiter unten bleibt das Aufklapp-Symbol. */
fun AppViewModel.setDescendantRows(rows: Int) {
    uiState.update { it.copy(descendantRows = rows.coerceIn(0, 4), descendants = null) }
}

fun AppViewModel.setAncestorGenerations(generations: Int) {
    uiState.update { it.copy(ancestorGenerations = generations, pedigree = null, descendants = null) }
}

/** Geschwister ein-/ausblenden; der Baum wird neu geladen, damit die Geschwister nachkommen. */
fun AppViewModel.setShowSiblings(on: Boolean) {
    settings.showSiblings = on
    uiState.update { it.copy(showSiblings = on, pedigree = null, descendants = null) }
}

/** Cousins ein-/ausblenden - die Daten sind mit den Geschwistern schon da, nur das Layout aendert sich. */
fun AppViewModel.setShowCousins(on: Boolean) {
    settings.showCousins = on
    uiState.update { it.copy(showCousins = on) }
}

/** Partner der Cousins ein-/ausblenden (Issue 33) - auch sie sind mit den Geschwistern schon geladen. */
fun AppViewModel.setShowCousinSpouses(on: Boolean) {
    settings.showCousinSpouses = on
    uiState.update { it.copy(showCousinSpouses = on) }
}

/** Daten fuer den Baum: Ahnen und Nachkommen der Mittelperson, beide Anfragen gleichzeitig. */
fun AppViewModel.loadChart() {
    val tree = uiState.value.tree ?: return
    val xref = uiState.value.root ?: return
    val generations = uiState.value.ancestorGenerations
    // Reihen samt Mittelperson; die Generation darunter nur fuer Partner und das Aufklapp-Symbol
    val reihen = uiState.value.descendantRows + 1

    viewModelScope.launch {
        try {
            // coroutineScope: scheitert eine der beiden Anfragen, landet der Fehler unten im catch.
            val (p, d) = coroutineScope {
                val pedigree = async { client.pedigree(tree.name, xref, generations) }
                // Eine Generation mehr: der Server liefert zur untersten Generation keine Familien - verheiratete
                // Enkel stuenden sonst ohne Ehepartner da. Aus der Zusatzgeneration bleiben nur die Partner.
                val descendants = async {
                    client.descendants(tree.name, xref, reihen + 1)
                        .let { it.copy(generations = reihen, tree = it.tree.bisTiefe(reihen)) }
                }
                pedigree.await() to descendants.await()
            }
            uiState.update { if (it.root == xref) it.copy(pedigree = p, descendants = d, siblings = null) else it }
            // Geschwister erst danach: der Baum steht schon, sie kommen nach
            if (uiState.value.showSiblings) loadSiblings(tree.name, xref, p)
        } catch (e: Exception) {
            fail(e)
        }
    }
}

/**
 * Geschwister (mit Partnern und Kindern) der Mittelperson, ihrer Eltern und Grosseltern - wie in einer
 * Familienansicht ueblich. Das Modul hat dafuer keinen eigenen Aufruf; die Nachkommen eines Elternteils liefern sie mit.
 * Die oberste Reihe bleibt ohne: ihre Eltern sind nicht geladen. Kommen sie spaeter dazu ("nach oben aufklappen"),
 * holt ein weiterer Aufruf nur die noch fehlenden Gruppen nach.
 */
internal suspend fun AppViewModel.loadSiblings(tree: String, root: String, pedigree: Pedigree) {
    val byNumber = pedigree.ancestors.associate { it.n to it.person }
    val known = uiState.value.siblings.orEmpty()
    val wanted = byNumber.keys.filter { n ->
        generationOf(n) < AppViewModel.SIBLING_ROWS && byNumber.getValue(n).xref !in known &&
            (byNumber.containsKey(2 * n) || byNumber.containsKey(2 * n + 1))
    }
    if (wanted.isEmpty()) return

    val found = coroutineScope {
        wanted.map { n ->
            // Ein Elternteil ohne Leserecht o. ae. kostet nur dessen Geschwistergruppe, nicht den Baum.
            async { byNumber.getValue(n).xref to runCatching { siblingsFromParents(tree, byNumber, n) }.getOrDefault(emptyList()) }
        }.awaitAll()
    }.toMap()

    uiState.update { if (it.root == root) it.copy(siblings = it.siblings.orEmpty() + found) else it }
}

internal suspend fun AppViewModel.siblingsFromParents(tree: String, byNumber: Map<Int, Person>, n: Int): List<Sibling> {
    val self = byNumber.getValue(n)
    val father = byNumber[2 * n]
    val mother = byNumber[2 * n + 1]
    val parent = father ?: mother ?: return emptyList()
    val other = if (parent === father) mother else null

    // Nachkommen eines Elternteils, 3 Stufen: Kinder (= Geschwister) samt deren Partnern und Kindern (= Neffen,
    // bei den Eltern-Geschwistern die Cousins). Bei den Eltern der Mittelperson eine Stufe mehr, denn zur untersten
    // angeforderten Generation liefert der Server keine Familien - so bekommen die Cousins ihre Partner (Issue 33).
    val depth = if (n == 2 || n == 3) 4 else 3

    return siblingsAusNachkommen(client.descendants(tree, parent.xref, depth).tree.families, self, other)
}

/**
 * Die Geschwister von [self] aus den Familien eines Elternteils: nur die Verbindung mit dem anderen Elternteil
 * ([other]) - Halbgeschwister haengen an einer anderen Familie. Je Geschwister seine Partner und Kinder, zu jedem
 * Kind dessen Partner (wenn der Server die Familien dieser Generation mitgeliefert hat).
 */
internal fun siblingsAusNachkommen(families: List<DescendantFamily>, self: Person, other: Person?): List<Sibling> =
    families
        .filter { other == null || it.spouse?.xref == other.xref }
        .flatMap { it.children }
        // Private Personen ("Privat", ohne Daten) wuerden die Reihe nur verbreitern
        .filter { it.person.xref != self.xref && !it.person.isPrivate }
        .map { node ->
            val children = node.families.flatMap { family -> family.children }.filter { !it.person.isPrivate }
            Sibling(
                node.person,
                spouses = node.families.mapNotNull { family -> family.spouse },
                children = children.map { it.person },
                childSpouses = children.associate { child -> child.person.xref to child.families.mapNotNull { it.spouse }.filter { !it.isPrivate } }
                    .filterValues { it.isNotEmpty() },
            )
        }

/**
 * Nachkommenbaum auf [generationen] Reihen kuerzen: die unterste Reihe behaelt ihre Ehepartner (und Heiraten), aber keine
 * Kinder mehr. So zeigt der Baum auch bei verheirateten Enkeln das Paar.
 */
internal fun DescendantNode.bisTiefe(generationen: Int): DescendantNode =
    if (generationen <= 1) copy(families = families.map { it.copy(children = emptyList()) }, weitere = families.any { it.children.isNotEmpty() })
    else copy(families = families.map { f -> f.copy(children = f.children.map { it.bisTiefe(generationen - 1) }) })

/** Generation zu einer Kekule-Nummer: 1 -> 0, 2..3 -> 1, 4..7 -> 2 ... */
internal fun AppViewModel.generationOf(n: Int) = 31 - Integer.numberOfLeadingZeros(n)

/**
 * "Weiter nach oben": die Ahnen von Platz n nachladen und in den Baum einhaengen.
 * Im nachgeladenen Teilbaum ist diese Person die Nummer 1; ihr Platz m wird zu n * 2^g + (m - 2^g),
 * wobei g die Generation von m ist.
 */
fun AppViewModel.expandAncestors(n: Int, xref: String) {
    val tree = uiState.value.tree ?: return
    val root = uiState.value.root ?: return

    viewModelScope.launch {
        try {
            val branch = client.pedigree(tree.name, xref, AppViewModel.EXPAND_GENERATIONS)
            uiState.update { state ->
                val pedigree = state.pedigree
                if (state.root != root || pedigree == null) return@update state

                val added = branch.ancestors.filter { it.n > 1 }.map { ancestor ->
                    val g = generationOf(ancestor.n)
                    ancestor.copy(n = n * (1 shl g) + (ancestor.n - (1 shl g)))
                }
                val known = pedigree.ancestors.map { it.n }.toSet()
                state.copy(pedigree = pedigree.copy(ancestors = pedigree.ancestors + added.filter { it.n !in known }))
            }
            // Die Reihe, die eben ihre Eltern bekommen hat, kann jetzt auch Geschwister zeigen
            val expanded = uiState.value.pedigree
            if (uiState.value.showSiblings && expanded != null) loadSiblings(tree.name, root, expanded)
        } catch (e: Exception) {
            fail(e)
        }
    }
}

/**
 * "Weiter nach unten": die Nachkommen einer Person der untersten Reihe nachladen und an ihrer Stelle einhaengen. Wie beim
 * ersten Laden eine Generation mehr, damit die neue unterste Reihe ihre Partner zeigt und wieder aufklappbar ist.
 */
fun AppViewModel.expandDescendants(xref: String) {
    val tree = uiState.value.tree ?: return
    val root = uiState.value.root ?: return

    viewModelScope.launch {
        try {
            val zweig = client.descendants(tree.name, xref, AppViewModel.DESCENDANT_GENERATIONS + 1).tree.bisTiefe(AppViewModel.DESCENDANT_GENERATIONS)
            uiState.update { state ->
                val d = state.descendants
                if (state.root != root || d == null) state else state.copy(descendants = d.copy(tree = d.tree.einhaengen(xref, zweig)))
            }
        } catch (e: Exception) {
            fail(e)
        }
    }
}

/** Zuklappen: die nachgeladenen Nachkommen wieder ausblenden; die Partner bleiben, das Aufklapp-Symbol kommt zurueck. */
fun AppViewModel.collapseDescendants(xref: String) = uiState.update { state ->
    val d = state.descendants ?: return@update state
    state.copy(descendants = d.copy(tree = d.tree.zuklappen(xref)))
}

internal fun DescendantNode.zuklappen(xref: String): DescendantNode =
    if (aufgeklappt && person.xref == xref) copy(families = families.map { it.copy(children = emptyList()) }, weitere = true, aufgeklappt = false)
    else copy(families = families.map { f -> f.copy(children = f.children.map { it.zuklappen(xref) }) })

/** Den aufklappbaren Knoten [xref] durch den nachgeladenen Zweig ersetzen (Person bleibt, Familien kommen dazu). */
internal fun DescendantNode.einhaengen(xref: String, zweig: DescendantNode): DescendantNode =
    if (weitere && person.xref == xref) copy(families = zweig.families, weitere = false, aufgeklappt = true)
    else copy(families = families.map { f -> f.copy(children = f.children.map { it.einhaengen(xref, zweig) }) })

fun AppViewModel.setDetailTab(tab: Int) = uiState.update { it.copy(detailTab = tab) }

fun AppViewModel.setTreeFullscreen(on: Boolean) = uiState.update { it.copy(treeFullscreen = on) }

/** "+" an einer Karte: Details der Person holen; der Dialog oeffnet sich, sobald sie da sind. */
fun AppViewModel.requestAddRelative(xref: String) {
    uiState.update { it.copy(addRelativeFor = xref) }
    if (uiState.value.detail?.person?.xref != xref) select(xref)
}

fun AppViewModel.addRelativeHandled() = uiState.update { it.copy(addRelativeFor = null) }

/** Zurueck innerhalb der App. false = nichts mehr zu tun, das System darf die App schliessen. */
fun AppViewModel.back(): Boolean {
    val state = uiState.value

    return when {
        state.screen == Screen.Trees && state.tree != null -> { cancelTreePicker(); true }
        state.screen != Screen.Main -> false
        state.pdf != null -> { closePdf(); true }
        state.exifEditing != null -> { cancelExif(); true }
        state.viewer != null -> { closeViewer(); true }
        state.section == Section.Photos && state.photosTab == PhotosTab.Archive && (state.collection != null || state.loadingCollection) -> {
            closeCollection(); true
        }
        state.treeFullscreen -> { uiState.update { it.copy(treeFullscreen = false) }; true }
        state.profileOpen -> { uiState.update { it.copy(profileOpen = false) }; true }
        state.section == Section.Tree && state.rootHistory.isNotEmpty() -> {
            val previous = state.rootHistory.last()
            uiState.update { it.copy(rootHistory = it.rootHistory.dropLast(1)) }
            setRoot(previous, remember = false)
            true
        }
        state.section != Section.Home -> { uiState.update { it.copy(section = Section.Home) }; true }
        else -> false
    }
}

fun AppViewModel.canGoBack(): Boolean = uiState.value.let {
    (it.screen == Screen.Trees && it.tree != null) || it.screen == Screen.Main &&
        (it.pdf != null || it.viewer != null || it.treeFullscreen || it.profileOpen || it.section != Section.Home || it.rootHistory.isNotEmpty())
}
