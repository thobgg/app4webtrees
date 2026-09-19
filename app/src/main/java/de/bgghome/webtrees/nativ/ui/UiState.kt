package de.bgghome.webtrees.nativ.ui

import de.bgghome.webtrees.nativ.api.Anniversary
import de.bgghome.webtrees.nativ.api.Descendants
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.api.Info
import de.bgghome.webtrees.nativ.api.MediaJson
import de.bgghome.webtrees.nativ.api.Pedigree
import de.bgghome.webtrees.nativ.api.PendingRecord
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.TagInfo
import de.bgghome.webtrees.nativ.api.TreeInfo
import de.bgghome.webtrees.nativ.ui.tree.Sibling

// Der gesamte Zustand der Oberflaeche, den AppViewModel fuehrt und die Compose-Bildschirme nur lesen.

/** Die Bildschirme vor dem eigentlichen Hauptbildschirm: Adresse eingeben, anmelden, Baum waehlen. */
enum class Screen { Loading, Setup, Login, Trees, Main }

/** Die vier Bereiche der unteren Leiste (Tablet: seitliche Leiste). */
enum class Section { Home, Tree, Search, Photos }

/** Ein Kopplungs-Link (webtreesand://connect), der auf die Bestaetigung des Nutzers wartet. */
data class ConnectRequest(val url: String, val tree: String, val code: String, val user: String)

data class UiState(
    val screen: Screen = Screen.Loading,
    val busy: Boolean = false,
    /** Fehler auf den Startbildschirmen (unter dem Formular); im Hauptbildschirm laufen Fehler ueber message. */
    val error: String? = null,
    /** Einmalige Meldung fuer die Snackbar; die Oberflaeche setzt sie nach dem Anzeigen zurueck. */
    val message: String? = null,
    val baseUrl: String = "",
    val userName: String = "",
    /** Kopplungs-Link, der noch bestaetigt werden muss - jede Webseite koennte einen solchen Link ausloesen. */
    val pendingConnect: ConnectRequest? = null,
    val info: Info? = null,
    val tree: TreeInfo? = null,
    val section: Section = Section.Tree,
    /** Bezugsperson fuer "Urgrossmutter von ...": eigene Person des Benutzers, sonst Startperson des Baums. */
    val home: String? = null,

    // ── Suche ────────────────────────────────────────────────────────
    val query: String = "",
    val people: List<Person> = emptyList(),
    val nextPage: Int? = null,
    val loadingPeople: Boolean = false,

    // ── Baum: die Mittelperson (root) ist unabhaengig von der Person im Profil-Panel (selected) ──
    val root: String? = null,
    /** Fruehere Mittelpersonen - die Zurueck-Taste geht sie rueckwaerts durch. */
    val rootHistory: List<String> = emptyList(),
    val ancestorGenerations: Int = 4,
    val pedigree: Pedigree? = null,
    val descendants: Descendants? = null,
    /** Geschwister (mit Partnern) je XREF fuer die unteren Ahnenreihen; null = noch nicht geladen */
    val siblings: Map<String, List<Sibling>>? = null,
    val showSiblings: Boolean = true,
    /** Cousins der Mittelperson unter den Geschwistern ihrer Eltern (nur zusammen mit showSiblings) */
    val showCousins: Boolean = true,
    val treeFullscreen: Boolean = false,

    // ── Profil-Panel ─────────────────────────────────────────────────
    val selected: String? = null,
    val detail: IndividualDetail? = null,
    val loadingDetail: Boolean = false,
    /** Handy: das Profil als eigene Seite (am Tablet steht es immer neben dem Baum). */
    val profileOpen: Boolean = false,
    /** Gewaehlter Reiter im Profil; liegt hier, damit er beim Personenwechsel erhalten bleibt. */
    val detailTab: Int = 0,
    /** "+" an einer Karte getippt: sobald die Details dieser Person da sind, oeffnet sich der Hinzufuegen-Dialog. */
    val addRelativeFor: String? = null,
    /** Ereignisarten, die sich einer Person hinzufuegen lassen (vom Server, in der Sprache der App) */
    val tags: List<TagInfo> = emptyList(),
    /** Ereignisarten fuer Familien (Heirat, Scheidung ...) */
    val familyTags: List<TagInfo> = emptyList(),

    // ── Start ────────────────────────────────────────────────────────
    val recent: List<Person> = emptyList(),
    val anniversaries: List<Anniversary> = emptyList(),
    /** Fuer Moderatoren: Datensaetze, deren Aenderungen auf Freigabe warten */
    val pending: List<PendingRecord> = emptyList(),
    val reminders: Boolean = false,

    // ── Fotos ────────────────────────────────────────────────────────
    val media: List<MediaJson> = emptyList(),
    val mediaNextPage: Int? = null,
    val loadingMedia: Boolean = false,
    val mediaLoaded: Boolean = false,
)
