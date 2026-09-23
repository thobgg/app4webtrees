package de.bgghome.webtrees.nativ

import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.data.Settings
import java.io.File

/**
 * Was das ViewModel von der Plattform braucht (Aufteilung 23.09.2026, Muster mpd-app). Android setzt alles um
 * (AndroidPlattform: SharedPreferences, WorkManager), der Desktop nimmt die Vorgaben - keine Erinnerung im
 * Hintergrund. Alles mit Android-Typen in der Signatur (Context, Uri) liegt nicht hier, sondern in androidMain.
 */
interface Plattform {
    val client: WtClient
    val settings: Settings

    /** Die Fassung der App, fuer die Ueber-Zeile im Menue. */
    val versionName: String

    /** Entwicklungsfassung: erlaubt http:// fuer den lokalen Testserver. */
    val isDebug: Boolean

    /** App-eigener Cache-Ordner (PDF-Dateien). */
    val cacheOrdner: File

    /** Kann diese Plattform taeglich an Jahrestage erinnern (Android: WorkManager und Benachrichtigung)? */
    val kannErinnern: Boolean get() = false

    /** Die taegliche Erinnerung ein- oder ausschalten. */
    fun setReminders(on: Boolean) {}
}
