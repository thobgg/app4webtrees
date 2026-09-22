package de.bgghome.webtrees.nativ.ui

import android.content.Context
import de.bgghome.webtrees.nativ.R
import de.bgghome.webtrees.nativ.api.ApiException
import de.bgghome.webtrees.nativ.api.NotJsonException
import de.bgghome.webtrees.nativ.api.WriteInterruptedException
import java.io.IOException

/** Ein Fehler, dessen Text schon fuer den Benutzer formuliert ist (z. B. "Bild liess sich nicht verkleinern"). */
class UserMessageException(message: String) : Exception(message)

/**
 * Macht aus einer Ausnahme einen Satz fuer den Benutzer. Die Fehlercodes des Moduls (siehe dessen README) bekommen
 * je eine eigene Meldung; was der Server sonst ablehnt, wird mit seinem Code genannt, damit man danach suchen kann.
 */
fun Context.explain(e: Exception): String = when (e) {
    is ApiException -> when (e.code) {
        "private" -> getString(R.string.err_private)
        "not-found" -> getString(R.string.err_not_found)
        "not-editable", "not-editor" -> getString(R.string.err_not_editable)
        "fact-locked", "family-locked" -> getString(R.string.err_locked)
        "invalid-date" -> getString(R.string.err_invalid_date)
        "parent-exists" -> getString(R.string.err_parent_exists)
        "family-required" -> getString(R.string.err_family_required)
        "name-required" -> getString(R.string.err_name_required)
        "upload-not-allowed" -> getString(R.string.err_upload_not_allowed)
        "upload-failed" -> getString(R.string.err_upload_failed)
        "link-not-found" -> getString(R.string.err_link_not_found)
        "not-moderator" -> getString(R.string.err_not_moderator)
        "tree-disabled" -> getString(R.string.err_tree_disabled)
        "pair-invalid", "pair-expired" -> getString(R.string.err_pair)
        "not-supported" -> getString(R.string.err_not_supported)
        "not-manager" -> getString(R.string.err_not_manager)
        "exif-failed" -> getString(R.string.err_exif_failed)
        "folder-not-found" -> getString(R.string.err_folder_not_found)
        "blocked-extension", "bad-filename" -> getString(R.string.err_bad_file)
        else -> getString(R.string.err_rejected, e.code)
    }
    // Keine JSON-Antwort: bei 404 fehlt das Modul auf dem Server, sonst hat der Webserver etwas anderes geliefert.
    is NotJsonException -> when (e.httpStatus) {
        404 -> getString(R.string.err_module_missing)
        else -> getString(R.string.err_unexpected, e.httpStatus)
    }
    is UserMessageException -> e.message.orEmpty()
    is WriteInterruptedException -> getString(R.string.err_write_interrupted)
    is IOException -> getString(R.string.err_no_connection, e.message ?: getString(R.string.err_unreachable))
    else -> e.message ?: e.javaClass.simpleName
}
