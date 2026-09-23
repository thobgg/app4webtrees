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

        // Karte (osmdroid): Kacheln im eigenen Cache-Ordner, ehrliche Kennung gegenueber den OSM-Servern.
        Configuration.getInstance().apply {
            userAgentValue = client.userAgent
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
    }

    // Bilder sind signierte webtrees-Routen und brauchen dieselbe Sitzung (Cookie) wie die API.
    override fun newImageLoader(context: PlatformContext): ImageLoader = wtImageLoader(context, client)
}
