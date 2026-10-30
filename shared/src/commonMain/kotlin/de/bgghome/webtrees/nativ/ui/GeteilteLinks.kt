package de.bgghome.webtrees.nativ.ui

import de.bgghome.webtrees.nativ.res.*
import kotlinx.coroutines.flow.update

/*
 * Geteilte Links: wtAnd nimmt Text aus dem Teilen-Menue anderer Apps an (Messenger, Mail). Steht darin ein Link auf
 * einen Datensatz des eigenen webtrees-Servers, oeffnet die App ihn nativ - Person als Profil, Quelle als Nachschlage-
 * Seite, alles andere als webtrees-Seite. Links fremder Server werden abgelehnt; nichts geht nach draussen.
 */

/** Ein Datensatz dieses Servers aus einem geteilten Link: Baum, Art (INDI, SOUR ...), Kennung und die Adresse selbst. */
data class GeteilterLink(val tree: String, val type: String, val xref: String, val url: String)

private val ARTEN = mapOf(
    "individual" to "INDI", "family" to "FAM", "source" to "SOUR", "media" to "OBJE",
    "repository" to "REPO", "note" to "NOTE", "location" to "_LOC", "submitter" to "SUBM",
)

/** Der erste Link im Text, der auf einen Datensatz des eigenen Servers zeigt; null, wenn keiner. */
fun datensatzAusText(text: String?, baseUrl: String): GeteilterLink? {
    if (text.isNullOrBlank() || baseUrl.isBlank()) return null
    val eigener = hostVon(baseUrl) ?: return null
    return Regex("""https?://[^\s<>"'()]+""", RegexOption.IGNORE_CASE).findAll(text)
        .map { it.value.trimEnd('.', ',', ';', ':', '!', '?') }
        .filter { hostVon(it) == eigener }
        .mapNotNull(::datensatzAusUrl)
        .firstOrNull()
}

/** Host und Port, klein geschrieben - das Schema zaehlt nicht (ein Server, der http und https spricht, ist derselbe). */
private fun hostVon(url: String): String? = runCatching {
    val uri = java.net.URI(url.trim())
    val host = uri.host?.lowercase() ?: return null
    if (uri.port > 0) "$host:${uri.port}" else host
}.getOrNull()

/** Schoene Adresse (/tree/x/individual/I1/Name) oder index.php?route=%2Ftree%2Fx%2Findividual%2FI1 - beide Formen von webtrees. */
internal fun datensatzAusUrl(url: String): GeteilterLink? {
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
    val route = uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("route=") }?.substringAfter("route=")
        ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }
    val pfad = route ?: uri.path ?: return null
    val treffer = Regex("""/tree/([^/]+)/(individual|family|source|media|repository|note|location|submitter)/([^/?#]+)""").find(pfad) ?: return null
    val tree = runCatching { java.net.URLDecoder.decode(treffer.groupValues[1], "UTF-8") }.getOrDefault(treffer.groupValues[1])
    return GeteilterLink(tree, ARTEN.getValue(treffer.groupValues[2]), treffer.groupValues[3], url)
}

/** Geteilter Text ist angekommen (Android, Teilen-Menue). Der Link wartet im Zustand, bis der Hauptbildschirm ihn oeffnet. */
fun AppViewModel.sharedText(text: String?) {
    val link = datensatzAusText(text, uiState.value.baseUrl)
    if (link == null) {
        val meldung = text(Res.string.share_link_foreign)
        uiState.update { if (it.screen == Screen.Main) it.copy(message = meldung) else it.copy(error = meldung) }
        return
    }
    uiState.update { it.copy(sharedLink = link) }
}

fun AppViewModel.sharedLinkHandled() = uiState.update { it.copy(sharedLink = null) }

/** Der Link gehoert zu einem Baum, den dieser Benutzer hier nicht sieht. */
fun AppViewModel.sharedLinkOtherTree() = uiState.update { it.copy(sharedLink = null, message = text(Res.string.share_link_other_tree)) }
