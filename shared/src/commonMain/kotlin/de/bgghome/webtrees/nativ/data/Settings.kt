package de.bgghome.webtrees.nativ.data

/** Kleine, unkritische Einstellungen. Das Passwort wird nie gespeichert - nur der Sitzungs-Cookie. */
class Settings(private val prefs: Ablage) {

    var baseUrl: String
        get() = prefs.getString("baseUrl", "").orEmpty()
        set(value) = prefs.putString("baseUrl", value)

    var userName: String
        get() = prefs.getString("userName", "").orEmpty()
        set(value) = prefs.putString("userName", value)

    /** Taegliche Erinnerung an Jahrestage (siehe AnniversaryWorker) */
    var reminders: Boolean
        get() = prefs.getBoolean("reminders", false)
        set(value) = prefs.putBoolean("reminders", value)

    /** Baum: Geschwister der Mittelperson und ihrer Ahnen samt Partnern zeigen (wie in einer Familienansicht ueblich) */
    var showSiblings: Boolean
        get() = prefs.getBoolean("showSiblings", true)
        set(value) = prefs.putBoolean("showSiblings", value)

    /** Baum: Cousins der Mittelperson (Kinder der Eltern-Geschwister) zeigen */
    var showCousins: Boolean
        get() = prefs.getBoolean("showCousins", true)
        set(value) = prefs.putBoolean("showCousins", value)

    /** Fotos: dichtes Raster ohne Unterschriften (drei und mehr Spalten) statt Kacheln mit Text */
    var denseGrid: Boolean
        get() = prefs.getBoolean("denseGrid", false)
        set(value) = prefs.putBoolean("denseGrid", value)

    var tree: String
        get() = prefs.getString("tree", "").orEmpty()
        set(value) = prefs.putString("tree", value)
}
