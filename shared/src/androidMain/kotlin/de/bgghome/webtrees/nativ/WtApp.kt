package de.bgghome.webtrees.nativ

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.data.Settings
import de.bgghome.webtrees.nativ.ui.wtImageLoader
import org.osmdroid.config.Configuration
import java.io.File

class WtApp : Application(), SingletonImageLoader.Factory {

    lateinit var plattform: AndroidPlattform
        private set
    val client: WtClient get() = plattform.client
    val settings: Settings get() = plattform.settings

    override fun onCreate() {
        super.onCreate()
        plattform = AndroidPlattform(this)
        Sprache.start(plattform.settings)
        de.bgghome.webtrees.nativ.data.Ortsanzeige.start(plattform.settings)
        // Zeitplan der Erinnerungen auffrischen (Periode und Inhalt koennen sich mit einer neuen Fassung aendern)
        if (plattform.settings.reminders) de.bgghome.webtrees.nativ.ui.AnniversaryWorker.schedule(this, true)

        // Karte (osmdroid): Kacheln im eigenen Cache-Ordner, ehrliche Kennung gegenueber den OSM-Servern.
        Configuration.getInstance().apply {
            userAgentValue = client.userAgent
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Sprache.anwenden()
    }

    // Bilder sind signierte webtrees-Routen und brauchen dieselbe Sitzung (Cookie) wie die API.
    override fun newImageLoader(context: PlatformContext): ImageLoader = wtImageLoader(context, client)
}
