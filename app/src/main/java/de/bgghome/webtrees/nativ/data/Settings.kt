package de.bgghome.webtrees.nativ.data

import android.content.Context

/** Kleine, unkritische Einstellungen. Das Passwort wird nie gespeichert - nur der Sitzungs-Cookie. */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString("baseUrl", "").orEmpty()
        set(value) = prefs.edit().putString("baseUrl", value).apply()

    var userName: String
        get() = prefs.getString("userName", "").orEmpty()
        set(value) = prefs.edit().putString("userName", value).apply()

    /** Taegliche Erinnerung an Jahrestage (siehe AnniversaryWorker) */
    var reminders: Boolean
        get() = prefs.getBoolean("reminders", false)
        set(value) = prefs.edit().putBoolean("reminders", value).apply()

    /** Baum: Geschwister der Mittelperson und ihrer Ahnen samt Partnern zeigen (wie in einer Familienansicht ueblich) */
    var showSiblings: Boolean
        get() = prefs.getBoolean("showSiblings", true)
        set(value) = prefs.edit().putBoolean("showSiblings", value).apply()

    /** Baum: Cousins der Mittelperson (Kinder der Eltern-Geschwister) zeigen */
    var showCousins: Boolean
        get() = prefs.getBoolean("showCousins", true)
        set(value) = prefs.edit().putBoolean("showCousins", value).apply()

    var tree: String
        get() = prefs.getString("tree", "").orEmpty()
        set(value) = prefs.edit().putString("tree", value).apply()
}
