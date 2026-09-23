package de.bgghome.webtrees.nativ.api

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Haelt die webtrees-Sitzung ueber App-Neustarts hinweg - wie es der WebView-Wrapper auch tut.
 * webtrees setzt ein Sitzungs-Cookie ohne Ablaufdatum; wie lange es gilt, bestimmt der Server.
 * Gespeichert wird in den privaten App-Daten (allowBackup=false, siehe Manifest).
 */
class PersistentCookieJar(context: Context) : CookieJar {

    private val prefs = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)
    private val cookies = mutableMapOf<String, Cookie>()

    init {
        prefs.all.forEach { (key, value) ->
            val parts = (value as? String)?.split('\n') ?: return@forEach
            if (parts.size == 2) {
                val url = HttpUrl.Builder().scheme("https").host(parts[0]).build()
                Cookie.parse(url, parts[1])?.let { cookies[key] = it }
            }
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val editor = prefs.edit()

        cookies.forEach { cookie ->
            val key = cookie.domain + '|' + cookie.path + '|' + cookie.name
            this.cookies[key] = cookie
            editor.putString(key, cookie.domain + '\n' + cookie.toString())
        }

        editor.apply()
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
        prefs.edit().clear().apply()
    }
}
