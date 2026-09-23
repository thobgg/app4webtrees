package de.bgghome.webtrees.nativ.api

import de.bgghome.webtrees.nativ.data.Ablage
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Haelt die webtrees-Sitzung ueber App-Neustarts hinweg - wie es der WebView-Wrapper auch tut.
 * webtrees setzt ein Sitzungs-Cookie ohne Ablaufdatum; wie lange es gilt, bestimmt der Server.
 * Gespeichert wird in den privaten App-Daten (Android: allowBackup=false, siehe Manifest; Desktop: Benutzerprofil).
 */
class PersistentCookieJar(private val prefs: Ablage) : CookieJar {
    private val cookies = mutableMapOf<String, Cookie>()

    init {
        prefs.alle().forEach { (key, value) ->
            val parts = value.split('\n')
            if (parts.size == 2) {
                val url = HttpUrl.Builder().scheme("https").host(parts[0]).build()
                Cookie.parse(url, parts[1])?.let { cookies[key] = it }
            }
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val changed = mutableMapOf<String, String>()

        cookies.forEach { cookie ->
            val key = cookie.domain + '|' + cookie.path + '|' + cookie.name
            this.cookies[key] = cookie
            changed[key] = cookie.domain + '\n' + cookie.toString()
        }

        prefs.putStrings(changed)
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        cookies.values.filter { it.matches(url) && (!it.persistent || it.expiresAt > System.currentTimeMillis()) }

    /** Fuer die WebView-Rueckfallansicht: dieselbe Sitzung im eingebetteten Browser. */
    @Synchronized
    fun cookiesFor(url: HttpUrl): List<Cookie> = loadForRequest(url)

    @Synchronized
    fun clear() {
        cookies.clear()
        prefs.leeren()
    }
}
