package de.bgghome.webtrees.nativ.ui.karte

import coil3.PlatformContext
import de.bgghome.webtrees.nativ.Desktop
import java.io.File

/** Neben dem PDF-Cache: ~/.cache/app4webtrees/tiles bzw. %LOCALAPPDATA%\app4webtrees\tiles. */
actual fun kachelCacheOrdner(context: PlatformContext): File = File(Desktop.plattform.cacheOrdner, "tiles")
