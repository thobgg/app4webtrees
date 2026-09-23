package de.bgghome.webtrees.nativ

import android.app.Application
import android.os.Build
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.data.AndroidAblage
import de.bgghome.webtrees.nativ.data.Settings
import de.bgghome.webtrees.nativ.shared.BuildConfig
import de.bgghome.webtrees.nativ.ui.AnniversaryWorker
import java.io.File

/** Android-Umsetzung von [Plattform]: SharedPreferences, WorkManager-Erinnerung, Cache der App. */
class AndroidPlattform(private val app: Application) : Plattform {
    override val versionName: String = BuildConfig.VERSION_NAME
    override val isDebug: Boolean = (app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    override val settings = Settings(AndroidAblage(app, "settings"))
    override val client = WtClient(
        AndroidAblage(app, "wtclient"), AndroidAblage(app, "cookies"),
        userAgent = "wtAnd/$versionName (Android ${Build.VERSION.RELEASE})",
    ).also { it.baseUrl = settings.baseUrl }
    override val cacheOrdner: File get() = app.cacheDir
    override val kannErinnern = true
    override fun setReminders(on: Boolean) = AnniversaryWorker.schedule(app, on)
}
