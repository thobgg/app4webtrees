package de.bgghome.webtrees.nativ.ui

import org.jetbrains.compose.resources.StringResource
import androidx.lifecycle.ViewModel
import de.bgghome.webtrees.nativ.Plattform
import androidx.lifecycle.viewModelScope
import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.api.NotJsonException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Das eine View-Model der App: haelt den UiState und spricht ueber WtClient mit dem Server.
 *
 * Die Aktionen sind nach Bereich auf Dateien verteilt, als Erweiterungsfunktionen dieser Klasse:
 *   SessionActions.kt      Adresse, Anmelden, Koppeln, Abmelden, Baumwahl
 *   TreeActions.kt         Personenliste, Mittelperson, Profil-Panel, Baum laden, Navigation
 *   EditActions.kt         Ereignisse, Verwandte, Loeschen, Freigabe
 *   PhotoActions.kt        Fotouebersicht, Hochladen
 *   AnniversaryActions.kt  Jahrestage, Erinnerung
 * Hier bleiben nur der Zustand, die gemeinsamen Helfer und das, was alle Bereiche zugleich anstoesst (refresh).
 *
 * Jeder Netzaufruf laeuft in viewModelScope; Ergebnisse kommen nur an, wenn der Zustand noch zu ihnen passt
 * (z. B. ist die Suche inzwischen eine andere, wird die Antwort verworfen).
 */
class AppViewModel(internal val plattform: Plattform) : ViewModel() {

    internal companion object {
        /** Kleinste API-Version des Server-Moduls, mit der diese App arbeiten kann (Feld "api" in Info). */
        const val MIN_API = 1
        /** So viele Generationen kommen je Tipp auf "weiter nach oben" dazu (die angetippte Person mitgezaehlt). */
        const val EXPAND_GENERATIONS = 3
        /** Nennt der Server sein Upload-Limit nicht: der PHP-Standard von 2 MB. */
        const val DEFAULT_MAX_UPLOAD = 2L * 1024 * 1024
        /** Ab dieser API-Stufe: Jahrestage, Datensatz loeschen, Verknuepfung loesen. */
        const val API_ANNIVERSARIES = 4
        /** Ab dieser API-Stufe kennt das Modul MediaList, relationship und die Personenzahl. */
        const val API_PHOTOS = 2
        /** Ab dieser API-Stufe: Ortsvorschlaege (Places) und Ereignisdaten zusaetzlich im GEDCOM-Format. */
        const val API_PLACES = 8
        /** Ab dieser API-Stufe: Merkliste je Benutzer und Baum. */
        const val API_BOOKMARKS = 11
        const val DESCENDANT_GENERATIONS = 3
        /** Geschwister gibt es fuer so viele Reihen von unten (Mittelperson, Eltern, Grosseltern) - je Person eine Anfrage. */
        const val SIBLING_ROWS = 3
    }

    internal val client = plattform.client
    internal val settings = plattform.settings

    /** Fassung der App fuer die Ueber-Zeile im Menue. */
    val versionName: String get() = plattform.versionName

    /** Ob das Menue die taegliche Erinnerung anbietet (Android ja, Desktop nicht). */
    val kannErinnern: Boolean get() = plattform.kannErinnern

    internal val uiState = MutableStateFlow(UiState(baseUrl = settings.baseUrl, userName = settings.userName))
    val state: StateFlow<UiState> = uiState

    /** Laufende, verzoegerte Suche - eine neue Eingabe bricht sie ab (TreeActions.kt). */
    internal var searchJob: Job? = null

    /** Ob die Breite des Bildschirms schon bekannt ist (setWide in TreeActions.kt). */
    internal var layoutKnown = false

    init {
        start()
    }

    /** Alles neu laden, was gerade sichtbar sein kann - nach dem Rueckfall ins Web und aus dem Menue. */
    fun refresh() {
        uiState.update { it.copy(pedigree = null, descendants = null) }
        uiState.value.selected?.let { select(it) }
        loadPeople(reset = true)
        if (uiState.value.mediaLoaded) loadMedia(reset = true)
        if (uiState.value.archiveStatus != ArchiveStatus.Loading) probeArchive()
        uiState.value.collection?.let { loadCollection(it.slug, it.typ.orEmpty(), 1) }
        loadAnniversaries()
        loadPending()
        loadBookmarks()
    }

    fun messageShown() = uiState.update { it.copy(message = null) }

    // ── Fehler ───────────────────────────────────────────────────────

    internal fun fail(e: Exception) {
        // Mitten in der Arbeit keine JSON-Antwort mehr: die Sitzung ist abgelaufen.
        val state = uiState.value
        if (e is NotJsonException && state.screen == Screen.Main && state.info?.user?.loggedIn == true) {
            uiState.update { it.copy(screen = Screen.Login, error = text(Res.string.session_expired)) }
            return
        }

        uiState.update { it.copy(message = explain(e)) }
    }

    /** Text ohne Composable-Kontext (Compose-Ressourcen, fuer Android und Desktop dieselben Dateien). */
    internal fun text(res: StringResource, vararg args: Any): String = Texte.t(res, *args)
}
