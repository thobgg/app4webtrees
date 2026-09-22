package de.bgghome.webtrees.nativ.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import de.bgghome.webtrees.nativ.BuildConfig
import de.bgghome.webtrees.nativ.R
import de.bgghome.webtrees.nativ.api.Info
import de.bgghome.webtrees.nativ.api.TreeInfo
import de.bgghome.webtrees.nativ.api.WtClient
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Sitzung und Koppeln: Adresse eingeben, anmelden, per Link/QR-Code verbinden, abmelden - und die Wahl des Stammbaums.
// Erweiterungen von AppViewModel (siehe dort); der Zustand liegt in uiState.

internal fun AppViewModel.start() {
    if (settings.baseUrl.isEmpty()) {
        uiState.update { it.copy(screen = Screen.Setup) }
        return
    }

    viewModelScope.launch {
        try {
            applyInfo(client.info())
        } catch (e: Exception) {
            // Server gerade nicht erreichbar o. ae.: zur Adress-Eingabe, Adresse bleibt vorbelegt.
            uiState.update { it.copy(screen = Screen.Setup, error = explain(e)) }
        }
    }
}

fun AppViewModel.submitUrl(input: String) {
    if (rejectCleartext(input)) {
        uiState.update { it.copy(error = text(R.string.err_http_only)) }
        return
    }

    client.baseUrl = input
    val url = client.baseUrl

    if (url.isEmpty()) return

    uiState.update { it.copy(busy = true, error = null, baseUrl = url) }

    viewModelScope.launch {
        try {
            val info = client.info()
            settings.baseUrl = url
            applyInfo(info)
        } catch (e: Exception) {
            uiState.update { it.copy(busy = false, error = explain(e)) }
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
                uiState.update { it.copy(busy = false, error = text(R.string.login_failed)) }
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

    if (rejectCleartext(url)) {
        uiState.update { it.copy(screen = Screen.Setup, busy = false, error = text(R.string.err_http_only)) }
        return
    }

    uiState.update { it.copy(pendingConnect = ConnectRequest(url = url.trim(), tree = tree, code = code, user = user)) }
}

fun AppViewModel.cancelConnect() = uiState.update { it.copy(pendingConnect = null) }

/**
 * http:// wird abgelehnt - ausser im Debug-Build, der Klartext erlaubt (debug/AndroidManifest.xml), damit die
 * lokale Testinstanz (php -S) erreichbar bleibt. Im Release blockiert Android Klartext ohnehin.
 */
internal fun AppViewModel.rejectCleartext(input: String): Boolean = !BuildConfig.DEBUG && WtClient.isCleartext(input)

/** Adresse setzen, Einmal-Code einloesen, Baum oeffnen. Eine bestehende Anmeldung an einem anderen Server wird ersetzt. */
fun AppViewModel.confirmConnect() {
    val request = uiState.value.pendingConnect ?: return

    client.cookieJar.clear()
    client.baseUrl = request.url
    uiState.update { UiState(screen = Screen.Loading, baseUrl = client.baseUrl, busy = true) }

    viewModelScope.launch {
        try {
            val paired = client.pair(request.code)
            settings.baseUrl = client.baseUrl
            settings.userName = paired.user
            settings.tree = paired.tree.ifEmpty { request.tree }
            uiState.update { it.copy(userName = paired.user) }
            applyInfo(client.info())
        } catch (e: Exception) {
            uiState.update { it.copy(screen = Screen.Setup, busy = false, error = explain(e)) }
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
        uiState.update { UiState(screen = Screen.Login, baseUrl = settings.baseUrl, userName = settings.userName) }
        runCatching { client.info() }.onSuccess { info -> uiState.update { it.copy(info = info) } }
    }
}

internal fun AppViewModel.applyInfo(info: Info) {
    // Aelteres Modul als diese App braucht: klare Ansage statt spaeter raetselhafter Fehler.
    if (info.api < AppViewModel.MIN_API) {
        uiState.update {
            it.copy(info = info, busy = false, screen = Screen.Setup, error = text(R.string.err_module_too_old, info.module))
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

fun AppViewModel.showTreePicker() = uiState.update { it.copy(screen = Screen.Trees) }

fun AppViewModel.chooseTree(tree: TreeInfo) {
    settings.tree = tree.name
    val home = tree.userXref.ifEmpty { tree.defaultXref }.ifEmpty { null }

    uiState.update {
        UiState(
            screen = Screen.Main, baseUrl = it.baseUrl, userName = it.userName, info = it.info,
            tree = tree, home = home, section = Section.Tree, ancestorGenerations = it.ancestorGenerations,
            reminders = settings.reminders, showSiblings = settings.showSiblings, showCousins = settings.showCousins,
        )
    }
    loadPeople(reset = true)

    // "Das bin ich": mit der eigenen Person starten, sonst mit der Startperson des Baums.
    // Gibt es keine, wird die erste sichtbare Person genommen, sobald die Liste da ist (loadPeople).
    if (home != null) setRoot(home, remember = false)

    loadAnniversaries()
    loadPending()
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
