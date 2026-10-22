package de.bgghome.webtrees.nativ.desk

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Update-Pruefung: einmal am Tag beim Start eine kleine Textdatei mit der aktuellen Versionsnummer von der Homepage
 * holen und mit der eigenen vergleichen. Gesendet wird nichts ausser der Anfrage selbst (kein Stammbaum, keine Kennung);
 * ohne Netz oder bei Fehlern passiert still nichts. Eine neuere Version erscheint als Zeile in der Statusleiste, kein
 * Dialog. Abschaltbar unter Hilfe > Nach Updates suchen; eine Version laesst sich ueberspringen.
 */
object UpdatePruefung {
    const val QUELLE = "https://wtwin.bgg-home.de/version.txt"
    const val SEITE_DE = "https://wtwin.bgg-home.de/de/"
    const val SEITE_EN = "https://wtwin.bgg-home.de/"

    private val prefs get() = DeskLayout.prefs

    /** Neuere Version als die eigene, nicht uebersprungen - sonst null. */
    val neueVersion = mutableStateOf<String?>(null)
    val aktiv = mutableStateOf(prefs.getBoolean("update_pruefen", true))

    sealed interface Ergebnis { data class Neuer(val version: String) : Ergebnis; data object Aktuell : Ergebnis; data object Fehler : Ergebnis }

    fun setzen(an: Boolean) { aktiv.value = an; prefs.putBoolean("update_pruefen", an); if (!an) neueVersion.value = null }

    fun ueberspringen(version: String) { prefs.putString("update_uebersprungen", version); neueVersion.value = null }

    val seite: String get() = if (de.bgghome.webtrees.nativ.Sprache.aktiv == "de") SEITE_DE else SEITE_EN

    /** "1.52" ist neuer als "1.51", "1.49.1" neuer als "1.49"; Buchstaben und Leerzeilen zaehlen nicht. */
    fun istNeuer(server: String, eigene: String): Boolean {
        fun teile(v: String) = v.trim().split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val s = teile(server); val e = teile(eigene)
        if (s.all { it == 0 }) return false
        for (i in 0 until maxOf(s.size, e.size)) {
            val a = s.getOrElse(i) { 0 }; val b = e.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Die Versionsnummer von der Homepage, null bei jedem Fehler. */
    suspend fun abrufen(userAgent: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(QUELLE).openConnection() as HttpURLConnection
            c.connectTimeout = 5000; c.readTimeout = 5000
            c.setRequestProperty("User-Agent", userAgent)
            c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }.lineSequence().map(String::trim).firstOrNull { it.isNotEmpty() }
                ?.takeIf { v -> v.length <= 20 && v.all { it.isDigit() || it == '.' } }
        }.getOrNull()
    }

    /** Von Hand (Hilfe > Jetzt nach Updates suchen): immer abrufen, Ergebnis zurueck - und die Statuszeile mitziehen. */
    suspend fun jetzt(eigene: String, userAgent: String): Ergebnis {
        val v = abrufen(userAgent) ?: return Ergebnis.Fehler
        prefs.putString("update_letzte", LocalDate.now().toString())
        return if (istNeuer(v, eigene)) { neueVersion.value = v; Ergebnis.Neuer(v) } else Ergebnis.Aktuell
    }

    /** Beim Start: hoechstens einmal am Tag, nur wenn eingeschaltet; die uebersprungene Version bleibt still. */
    fun beimStart(scope: CoroutineScope, eigene: String, userAgent: String) {
        if (!aktiv.value || de.bgghome.webtrees.nativ.Variante.kennung.isNotEmpty()) return
        val heute = LocalDate.now().toString()
        if (prefs.getString("update_letzte", null) == heute) return
        scope.launch {
            val v = abrufen(userAgent) ?: return@launch
            prefs.putString("update_letzte", heute)
            if (istNeuer(v, eigene) && prefs.getString("update_uebersprungen", null) != v) neueVersion.value = v
        }
    }
}
