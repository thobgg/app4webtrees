package de.bgghome.webtrees.nativ.ui.karte

import coil3.PlatformContext
import java.io.File

/** cacheDir/tiles der App (Android raeumt den Ordner bei Platznot selbst). */
actual fun kachelCacheOrdner(context: PlatformContext): File = File(context.cacheDir, "tiles")
