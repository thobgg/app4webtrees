package de.bgghome.webtrees.nativ

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/*
 * Texte fuer Code, der keinen Composable-Kontext hat - ViewModel, API-Fehler, Worker.
 *
 * Seit der Aufteilung in shared/app/desktop (23.09.2026) kommen die Texte aus den
 * Compose-Ressourcen (composeResources/values*), nicht mehr aus Android-R:
 * dieselben Dateien gelten fuer Android und Desktop. Composables nehmen
 * stringResource(Res.string.x); hier steht der Weg fuer alles andere.
 *
 * Warum runBlocking: getString der Compose-Ressourcen ist suspend, weil die
 * Datei beim ersten Zugriff gelesen wird. Danach kommt der Wert aus dem Cache.
 *
 * Ohne Laufzeit - in Unit-Tests - kommt der Schluessel als "#name" zurueck
 * statt eines Absturzes.
 */
object Texte {
    fun t(res: StringResource, vararg args: Any?): String = try {
        runBlocking {
            if (args.isEmpty()) getString(res)
            else getString(res, *args.map { it ?: "null" }.toTypedArray())
        }
    } catch (e: Throwable) {
        "#${res.key}"
    }

    /** Mengenform (`<plurals>`); die Zahl ist immer auch %1$d-Argument. */
    fun plural(res: PluralStringResource, n: Int, vararg args: Any?): String = try {
        runBlocking {
            val a = if (args.isEmpty()) arrayOf<Any>(n) else args.map { it ?: "null" }.toTypedArray()
            getPluralString(res, n, *a)
        }
    } catch (e: Throwable) {
        "#${res.key}×$n"
    }
}
