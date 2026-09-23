package de.bgghome.webtrees.nativ.data

import java.util.prefs.Preferences

/**
 * java.util.prefs als [Ablage] fuer den Desktop-Client: unter Linux eine Datei in ~/.java/.userPrefs, unter Windows
 * die Registry (HKCU). Je Zweck ein eigener Knoten, wie die Dateien auf Android ("settings", "cookies", "wtclient").
 */
class DesktopAblage(datei: String) : Ablage {
    private val prefs: Preferences = Preferences.userRoot().node("de/bgghome/app4webtrees/$datei")

    override fun getString(key: String, default: String?): String? = prefs.get(key, default)
    override fun putString(key: String, value: String?) {
        if (value == null) prefs.remove(key) else prefs.put(key, value)
        prefs.flush()
    }
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) { prefs.putBoolean(key, value); prefs.flush() }
    override fun alle(): Map<String, String> = prefs.keys().mapNotNull { k -> prefs.get(k, null)?.let { k to it } }.toMap()
    override fun putStrings(values: Map<String, String>) { values.forEach { (k, v) -> prefs.put(k, v) }; prefs.flush() }
    override fun leeren() { prefs.clear(); prefs.flush() }
}
