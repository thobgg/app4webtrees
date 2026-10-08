package de.bgghome.webtrees.nativ.ui

import androidx.lifecycle.viewModelScope
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.api.BasicAuth
import de.bgghome.webtrees.nativ.api.Info
import de.bgghome.webtrees.nativ.api.LoginWallException
import de.bgghome.webtrees.nativ.api.TreeInfo
import de.bgghome.webtrees.nativ.api.WtClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

// Sitzung und Koppeln: Adresse eingeben, anmelden, per Link/QR-Code verbinden, abmelden - und die Wahl des Stammbaums.
// Erweiterungen von AppViewModel (siehe dort); der Zustand liegt in uiState.

internal fun AppViewModel.start() {
    uiState.update { it.copy(basicAuth = client.basicAuth) }
    if (settings.baseUrl.isEmpty()) {
        uiState.update { it.copy(screen = Screen.Setup) }
        return
    }

    viewModelScope.launch {
        try {
            applyInfo(client.info())
        } catch (e: Exception) {
            // Server gerade nicht erreichbar o. ae.: zur Adress-Eingabe, Adresse bleibt vorbelegt.
            uiState.update { it.copy(screen = Screen.Setup, error = explain(e), loginWall = e is LoginWallException) }
        }
    }
}

/**
 * @param basicAuth Zugangsdaten eines Verzeichnisschutzes vor webtrees (Felder "Verzeichnisschutz" auf dem
 *                  Adressbildschirm), null = keiner. Werden mit der Adresse gespeichert, auch bei einem Fehlschlag,
 *                  damit sie beim naechsten Versuch noch dastehen.
 */
fun AppViewModel.submitUrl(input: String, basicAuth: BasicAuth? = null) {
    val url = WtClient.normalizeBaseUrl(input)

    if (url.isEmpty()) return

    uiState.update { it.copy(busy = true, error = null, loginWall = false) }

    viewModelScope.launch {
        if (rejectCleartext(input)) {
            uiState.update { it.copy(busy = false, error = text(Res.string.err_http_only)) }
            return@launch
        }
        client.baseUrl = input
        client.basicAuth = basicAuth?.takeIf { it.user.isNotBlank() }
        uiState.update { it.copy(baseUrl = url, basicAuth = client.basicAuth) }
        try {
            val info = client.info()
            settings.baseUrl = url
            applyInfo(info)
        } catch (e: Exception) {
            uiState.update { it.copy(busy = false, error = explain(e), loginWall = e is LoginWallException) }
        }
    }
}

fun AppViewModel.login(user: String, password: String) {
    uiState.update { it.copy(busy = true, error = null) }

    viewModelScope.launch {
        try {
            val info = client.login(user.trim(), password)

            if (info.user.loggedIn) {
                settings.userName = user.trim()
                uiState.update { it.copy(userName = user.trim()) }
                applyInfo(info)
            } else {
                uiState.update { it.copy(busy = false, error = text(Res.string.login_failed)) }
            }
        } catch (e: Exception) {
            uiState.update { it.copy(busy = false, error = explain(e)) }
        }
    }
}

/**
 * "Verbinden" aus webtrees (Link webtreesand://connect): erst nachfragen. Einen solchen Link kann jede Webseite
 * und jeder QR-Code ausloesen - ohne Rueckfrage liesse sich die App still an einen fremden Server binden und
 * eine bestehende Anmeldung ersetzen. Eingeloest wird der Code erst in confirmConnect().
 */
fun AppViewModel.connect(url: String, tree: String, code: String, user: String) {
    if (url.isBlank() || code.isBlank()) return

    viewModelScope.launch {
        if (rejectCleartext(url)) {
            uiState.update { it.copy(screen = Screen.Setup, busy = false, error = text(Res.string.err_http_only)) }
            return@launch
        }
        uiState.update { it.copy(pendingConnect = ConnectRequest(url = url.trim(), tree = tree, code = code, user = user)) }
    }
}

fun AppViewModel.cancelConnect() = uiState.update { it.copy(pendingConnect = null) }

/**
 * Verbinden-Link aus webtrees (api4webtrees, Seite "App"): `wtwin://connect?url=…&tree=…&code=…&user=…`, am Desktop
 * auch `wttux://`, `wtmac://` und `webtreesand://`. Kommt per Zwischenablage (Knopf "Mit wtWin verbinden") oder als
 * Startargument (das Programm ist fuer sein Schema angemeldet). Alles andere: null.
 */
fun verbindungAusText(text: String?): ConnectRequest? {
    val t = text?.trim() ?: return null
    if (t.length > 1000 || t.any { it.isWhitespace() }) return null
    if (t.substringBefore("://", "").lowercase() !in setOf("wtwin", "wttux", "wtmac", "webtreesand")) return null
    // Windows reicht den Link mitunter kanonisiert weiter (wtwin://connect/?url=…) - der Schraegstrich zaehlt nicht.
    val rest = t.substringAfter("://").removePrefix("connect/").let { if (it.startsWith("?")) "connect$it" else it }
    if (!rest.startsWith("connect?")) return null
    val q = rest.substringAfter('?').split('&').mapNotNull { teil ->
        val kv = teil.split('=', limit = 2)
        if (kv.size != 2) null else runCatching { java.net.URLDecoder.decode(kv[0], "UTF-8") to java.net.URLDecoder.decode(kv[1], "UTF-8") }.getOrNull()
    }.toMap()
    val url = q["url"].orEmpty()
    val code = q["code"].orEmpty()
    if (code.isEmpty() || !(url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true))) return null
    return ConnectRequest(url = url, tree = q["tree"].orEmpty(), code = code, user = q["user"].orEmpty())
}

/** Verbinden-Link (siehe [verbindungAusText]) - fragt wie beim Handy erst nach. */
fun AppViewModel.connectLink(link: String): Boolean {
    val r = verbindungAusText(link) ?: return false
    connect(r.url, r.tree, r.code, r.user)
    return true
}

/**
 * http:// nur im Heimnetz (nas4webtrees: NAS ohne Zertifikat) - ausser im Debug-Build, der jeden Server erlaubt.
 * Die eigentliche Sperre sitzt im Client vor jeder Anfrage; dies hier gibt beim Eingeben eine verstaendliche Meldung.
 */
internal suspend fun AppViewModel.rejectCleartext(input: String): Boolean =
    !plattform.isDebug && WtClient.isCleartext(input) && !withContext(Dispatchers.IO) { WtClient.cleartextHome(input) }

/** Adresse setzen, Einmal-Code einloesen, Baum oeffnen. Eine bestehende Anmeldung an einem anderen Server wird ersetzt. */
fun AppViewModel.confirmConnect() {
    val request = uiState.value.pendingConnect ?: return

    client.cookieJar.clear()
    // Zugangsdaten des Verzeichnisschutzes gelten fuer den Server: bei einem anderen Host verfallen sie.
    val previousHost = client.baseUrl.substringAfter("://").substringBefore("/")
    client.baseUrl = request.url
    if (!client.baseUrl.substringAfter("://").substringBefore("/").equals(previousHost, ignoreCase = true)) client.basicAuth = null
    uiState.update { UiState(screen = Screen.Loading, baseUrl = client.baseUrl, basicAuth = client.basicAuth, busy = true) }

    viewModelScope.launch {
        try {
            val paired = client.pair(request.code)
            settings.baseUrl = client.baseUrl
            settings.userName = paired.user
            settings.tree = paired.tree.ifEmpty { request.tree }
            uiState.update { it.copy(userName = paired.user) }
            applyInfo(client.info())
        } catch (e: Exception) {
            // Anmeldewand: zur Adress-Eingabe mit aufgeklappten Feldern fuer den Verzeichnisschutz; der Einmal-Code
            // ist noch nicht eingeloest (die Anfrage kam nie bei webtrees an), danach geht es per Benutzername weiter.
            uiState.update { it.copy(screen = Screen.Setup, busy = false, error = explain(e), loginWall = e is LoginWallException) }
        }
    }
}

/** Ohne Anmeldung weiter - zeigt, was Besucher sehen duerfen. */
fun AppViewModel.continueAsGuest() {
    val info = uiState.value.info ?: return
    if (info.trees.isNotEmpty()) showTrees(info)
}

fun AppViewModel.showLogin() = uiState.update { it.copy(screen = Screen.Login, error = null) }

fun AppViewModel.changeServer() = uiState.update { it.copy(screen = Screen.Setup, error = null) }

fun AppViewModel.logout() {
    viewModelScope.launch {
        client.logout()
        settings.tree = ""
        uiState.update { UiState(screen = Screen.Login, baseUrl = settings.baseUrl, userName = settings.userName, basicAuth = client.basicAuth) }
        runCatching { client.info() }.onSuccess { info -> uiState.update { it.copy(info = info) } }
    }
}

internal fun AppViewModel.applyInfo(info: Info) {
    // Aelteres Modul als diese App braucht: klare Ansage statt spaeter raetselhafter Fehler.
    if (info.api < AppViewModel.MIN_API) {
        uiState.update {
            it.copy(info = info, busy = false, screen = Screen.Setup, error = text(Res.string.err_module_too_old, info.module))
        }
        return
    }

    uiState.update { it.copy(info = info, busy = false, error = null) }

    if (!info.user.loggedIn) {
        uiState.update { it.copy(screen = Screen.Login) }
        return
    }

    showTrees(info)
}

internal fun AppViewModel.showTrees(info: Info) {
    val remembered = info.trees.firstOrNull { it.name == settings.tree }
    val only = info.trees.singleOrNull()

    when {
        remembered != null -> chooseTree(remembered)
        only != null -> chooseTree(only)
        else -> uiState.update { it.copy(screen = Screen.Trees) }
    }
}

/**
 * Baumliste neu lesen, nachdem Baeume umbenannt oder geloescht wurden (Stammbaum auf diesem PC). Der offene Baum bleibt
 * offen, nur sein Titel wird frisch; ist er weg, geht es weiter wie nach der Anmeldung.
 */
fun AppViewModel.reloadTrees() {
    viewModelScope.launch {
        val info = runCatching { client.info() }.getOrNull() ?: return@launch
        val offen = uiState.value.tree?.name
        val frisch = info.trees.firstOrNull { it.name == offen }
        if (frisch != null) uiState.update { it.copy(info = info, tree = frisch) }
        else { uiState.update { it.copy(info = info) }; showTrees(info) }
    }
}

fun AppViewModel.showTreePicker() = uiState.update { it.copy(screen = Screen.Trees) }

/** Baumwahl ohne Wechsel verlassen - geht nur, wenn schon ein Baum gewaehlt ist. */
fun AppViewModel.cancelTreePicker() = uiState.update { if (it.tree != null) it.copy(screen = Screen.Main) else it }

fun AppViewModel.chooseTree(tree: TreeInfo) {
    settings.tree = tree.name
    // Ab Stufe 24 sagt der Server, mit wem webtrees startet (eigene Standardperson vor "Das bin ich" vor der des
    // Stammbaums); aeltere Module kennen nur die beiden Benutzereinstellungen.
    val home = tree.startXref.ifEmpty { tree.userXref.ifEmpty { tree.defaultXref } }.ifEmpty { null }

    uiState.update {
        UiState(
            screen = Screen.Main, baseUrl = it.baseUrl, userName = it.userName, info = it.info,
            tree = tree, home = home, section = Section.Tree, ancestorGenerations = it.ancestorGenerations,
            reminders = settings.reminders, showSiblings = settings.showSiblings, showCousins = settings.showCousins,
            denseGrid = settings.denseGrid,
        )
    }
    loadPeople(reset = true)

    // "Das bin ich": mit der eigenen Person starten, sonst mit der Startperson des Baums.
    // Gibt es keine, wird die erste sichtbare Person genommen, sobald die Liste da ist (loadPeople).
    if (home != null) setRoot(home, remember = false)

    loadAnniversaries()
    loadPending()
    loadBookmarks()
    probeArchive()

    if (tree.canEdit) {
        viewModelScope.launch {
            runCatching { client.tags(tree.name, "INDI") }
                .onSuccess { list -> uiState.update { it.copy(tags = list.data) } }
            runCatching { client.tags(tree.name, "FAM") }
                .onSuccess { list -> uiState.update { it.copy(familyTags = list.data) } }
        }
    }
}
