package de.bgghome.webtrees.nativ

import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.data.DesktopAblage
import de.bgghome.webtrees.nativ.data.Settings
import de.bgghome.webtrees.nativ.ui.karte.kachelUserAgent
import java.io.File

/**
 * Desktop-Umsetzung von [Plattform]: Einstellungen in java.util.prefs, Cache nach XDG bzw. %LOCALAPPDATA%.
 * Keine Erinnerung im Hintergrund (kannErinnern bleibt false, das Menue bietet sie nicht an).
 */
class DesktopPlattform : Plattform {
    /** wtWin unter Windows, wtTux unter Linux (Thomas, 23.09.2026). */
    val appName: String = if (System.getProperty("os.name").orEmpty().startsWith("Windows")) "wtWin" else "wtTux"
    /** Kommt als -Dwtand.versionName aus desktop/build.gradle.kts (dieselbe Nummer wie die APK). */
    override val versionName: String = System.getProperty("wtand.versionName") ?: "dev"
    /** http:// fuer den lokalen Testserver nur mit WTAND_DEBUG=1 - wie der Debug-Build auf Android. */
    override val isDebug: Boolean = System.getenv("WTAND_DEBUG") == "1"
    override val settings = Settings(DesktopAblage("settings"))
    override val client = WtClient(
        DesktopAblage("wtclient"), DesktopAblage("cookies"),
        userAgent = "$appName/$versionName (${System.getProperty("os.name")})",
    ).also {
        it.baseUrl = settings.baseUrl
        it.klartextUeberall = isDebug
        // Kachelserver sehen denselben ehrlichen User-Agent, mit Projektadresse (OSM-Regel).
        kachelUserAgent = "$appName/$versionName (https://github.com/thobgg/app4webtrees)"
    }
    override val cacheOrdner: File by lazy {
        val basis = System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(System.getProperty("user.home"), ".cache")
        File(basis, "app4webtrees").apply { mkdirs() }
    }
}

/** Die eine Plattform des Fensters - die Desktop-Plattformteile (PDF, Web) brauchen ihren Client und Cache. */
object Desktop {
    lateinit var plattform: DesktopPlattform
}
