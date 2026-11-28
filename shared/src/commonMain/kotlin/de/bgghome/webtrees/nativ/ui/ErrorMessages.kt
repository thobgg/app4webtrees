package de.bgghome.webtrees.nativ.ui

import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.api.ApiException
import de.bgghome.webtrees.nativ.api.KlartextException
import de.bgghome.webtrees.nativ.api.LoginWallException
import de.bgghome.webtrees.nativ.api.LokalVerbindungException
import de.bgghome.webtrees.nativ.api.NotJsonException
import de.bgghome.webtrees.nativ.api.WriteInterruptedException
import de.bgghome.webtrees.nativ.api.WriteUnclearException
import java.io.IOException

/**
 * Technische Einzelheiten zu einem Fehler fuer den Dialog "Details" und zum Kopieren in eine Fehlermeldung: Art und
 * HTTP-Code, die aufgerufene Adresse, der Fehlercode des Moduls, die Fehlerseite des Servers im Klartext. Ohne
 * uebersetzte Beschriftungen, damit der Text in jeder Fehlermeldung an den Entwickler gleich aussieht.
 */
fun details(e: Exception): String = buildString {
    when (e) {
        is NotJsonException -> {
            appendLine("HTTP ${e.httpStatus}" + if (e.umgeleitet) " (redirect)" else "")
            if (e.url.isNotEmpty()) appendLine(e.url)
            e.serverSeite?.let { appendLine(); append(it) }
        }
        is WriteUnclearException -> {
            appendLine("HTTP ${e.httpStatus} (write)")
            if (e.url.isNotEmpty()) appendLine(e.url)
            e.serverSeite?.let { appendLine(); append(it) }
        }
        is ApiException -> {
            appendLine("api4webtrees: ${e.code}" + (e.status?.let { " (HTTP $it)" } ?: ""))
            e.element?.let { append(it.toString()) }
        }
        is LoginWallException -> appendLine("HTTP ${e.httpStatus} ${e.host}".trim())
        else -> {
            appendLine(e.javaClass.simpleName)
            e.message?.let { appendLine(it) }
            e.cause?.let { appendLine("${it.javaClass.simpleName}: ${it.message.orEmpty()}") }
        }
    }
}.trim()

/**
 * Fuer runCatching-Stellen in den Fenstern: die uebersetzte Meldung samt Servertext statt des rohen Ausnahmetexts
 * ("Keine JSON-Antwort (HTTP 500)") - so erfaehrt der Nutzer auch dort, was der Server gemeldet hat.
 */
fun meldung(t: Throwable?): String = when (t) { null -> "?"; is Exception -> explain(t); else -> t.message ?: t.javaClass.simpleName }

/** Ein Fehler, dessen Text schon fuer den Benutzer formuliert ist (z. B. "Bild liess sich nicht verkleinern"). */
class UserMessageException(message: String) : Exception(message)

/**
 * Macht aus einer Ausnahme einen Satz fuer den Benutzer. Die Fehlercodes des Moduls (siehe dessen README) bekommen
 * je eine eigene Meldung; was der Server sonst ablehnt, wird mit seinem Code genannt, damit man danach suchen kann.
 */
fun explain(e: Exception): String = when (e) {
    is ApiException -> when (e.code) {
        "private" -> Texte.t(Res.string.err_private)
        "not-found", "individual-not-found" -> Texte.t(Res.string.err_not_found)
        "not-editable", "not-editor" -> Texte.t(Res.string.err_not_editable)
        "fact-locked", "family-locked" -> Texte.t(Res.string.err_locked)
        "invalid-date" -> Texte.t(Res.string.err_invalid_date)
        "parent-exists" -> Texte.t(Res.string.err_parent_exists)
        "family-required" -> Texte.t(Res.string.err_family_required)
        "name-required" -> Texte.t(Res.string.err_name_required)
        "upload-not-allowed" -> Texte.t(Res.string.err_upload_not_allowed)
        "upload-failed" -> Texte.t(Res.string.err_upload_failed)
        "link-not-found" -> Texte.t(Res.string.err_link_not_found)
        "not-moderator" -> Texte.t(Res.string.err_not_moderator)
        "tree-disabled" -> Texte.t(Res.string.err_tree_disabled)
        "pair-invalid", "pair-expired" -> Texte.t(Res.string.err_pair)
        "not-supported" -> Texte.t(Res.string.err_not_supported)
        "not-manager" -> Texte.t(Res.string.err_not_manager)
        "not-logged-in" -> Texte.t(Res.string.err_not_logged_in)
        "exif-failed" -> Texte.t(Res.string.err_exif_failed)
        "folder-not-found" -> Texte.t(Res.string.err_folder_not_found)
        "blocked-extension", "bad-filename" -> Texte.t(Res.string.err_bad_file)
        // Modul Sammlungen, Stufe 5/6
        "not-admin" -> Texte.t(Res.string.err_archive_not_admin)
        "name-exists" -> Texte.t(Res.string.err_archive_name_exists)
        "bad-name", "missing-name" -> Texte.t(Res.string.err_archive_bad_name)
        "extension-changed" -> Texte.t(Res.string.err_archive_extension)
        "postcard-pair" -> Texte.t(Res.string.err_archive_postcard_pair)
        "not-album" -> Texte.t(Res.string.err_archive_not_album)
        "file-not-found" -> Texte.t(Res.string.err_archive_file_missing)
        "unknown-collection" -> Texte.t(Res.string.err_archive_unknown_collection)
        "bad-color" -> Texte.t(Res.string.err_archive_bad_color)
        "bad-regions" -> Texte.t(Res.string.err_archive_bad_regions)
        // Nachrichten (Stufe 42)
        "unknown-user", "missing-to" -> Texte.t(Res.string.err_message_recipient)
        "contact-none" -> Texte.t(Res.string.err_message_contact_none)
        "missing-body" -> Texte.t(Res.string.err_message_empty)
        "send-failed" -> Texte.t(Res.string.err_message_send_failed)
        else -> Texte.t(Res.string.err_rejected, e.code)
    }
    // Keine JSON-Antwort: bei 404 fehlt das Modul auf dem Server, sonst hat der Webserver etwas anderes geliefert.
    // Mit dem Kern der Fehlerseite (PHP-Fehler, Speicher- oder Laufzeitgrenze), damit der Nutzer ihn melden kann.
    is NotJsonException -> when {
        e.httpStatus == 404 -> Texte.t(Res.string.err_module_missing)
        e.serverText != null -> Texte.t(Res.string.err_unexpected_detail, e.httpStatus, e.serverText)
        else -> Texte.t(Res.string.err_unexpected, e.httpStatus)
    }
    is LoginWallException -> if (e.zugangsdatenAbgelehnt) Texte.t(Res.string.err_basic_auth_rejected) else Texte.t(Res.string.err_login_wall)
    is UserMessageException -> e.message.orEmpty()
    is WriteInterruptedException -> Texte.t(Res.string.err_write_interrupted)
    is WriteUnclearException -> if (e.serverText != null) Texte.t(Res.string.err_write_unclear_detail, e.serverText) else Texte.t(Res.string.err_write_unclear)
    is KlartextException -> Texte.t(Res.string.err_http_only)
    is LokalVerbindungException -> Texte.t(Res.string.err_local_blocked)
    is IOException -> Texte.t(Res.string.err_no_connection, e.message ?: Texte.t(Res.string.err_unreachable))
    else -> e.message ?: e.javaClass.simpleName
}
