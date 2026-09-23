package de.bgghome.webtrees.nativ.data

import android.content.Context
import android.content.SharedPreferences

/** SharedPreferences als [Ablage] - Datei und Schluessel wie vor der Aufteilung, bestehende Installationen finden alles wieder. */
class AndroidAblage(context: Context, datei: String) : Ablage {
    private val prefs: SharedPreferences = context.getSharedPreferences(datei, Context.MODE_PRIVATE)

    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)
    override fun putString(key: String, value: String?) { prefs.edit().putString(key, value).apply() }
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    override fun alle(): Map<String, String> = prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
    override fun putStrings(values: Map<String, String>) {
        val editor = prefs.edit()
        values.forEach { (k, v) -> editor.putString(k, v) }
        editor.apply()
    }
    override fun leeren() { prefs.edit().clear().apply() }
}
