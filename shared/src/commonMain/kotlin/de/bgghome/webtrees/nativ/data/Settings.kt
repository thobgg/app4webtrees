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

    /** Jahrestage nur von Lebenden: Geburtstage lebender Personen und Hochzeitstage, an denen beide noch leben (Issue 17) */
    var anniversaryLivingOnly: Boolean
        get() = prefs.getBoolean("anniversaryLivingOnly", false)
        set(value) = prefs.putBoolean("anniversaryLivingOnly", value)

    /** Baum: Geschwister der Mittelperson und ihrer Ahnen samt Partnern zeigen (wie in einer Familienansicht ueblich) */
    var showSiblings: Boolean
        get() = prefs.getBoolean("showSiblings", true)
        set(value) = prefs.putBoolean("showSiblings", value)

    /** Baum: Cousins der Mittelperson (Kinder der Eltern-Geschwister) zeigen */
    var showCousins: Boolean
        get() = prefs.getBoolean("showCousins", true)
        set(value) = prefs.putBoolean("showCousins", value)

    /** Baum: die Partner der Cousins daneben zeigen (nur zusammen mit showCousins; Issue 33) */
    var showCousinSpouses: Boolean
        get() = prefs.getBoolean("showCousinSpouses", false)
        set(value) = prefs.putBoolean("showCousinSpouses", value)

    /** Fotos: dichtes Raster ohne Unterschriften (drei und mehr Spalten) statt Kacheln mit Text */
    var denseGrid: Boolean
        get() = prefs.getBoolean("denseGrid", false)
        set(value) = prefs.putBoolean("denseGrid", value)

    var tree: String
        get() = prefs.getString("tree", "").orEmpty()
        set(value) = prefs.putString("tree", value)

    /** Sprache der Oberflaeche ("de", "en", …); leer = wie das System (siehe Sprache). */
    var language: String
        get() = prefs.getString("language", "").orEmpty()
        set(value) = prefs.putString("language", value)

    /** Ortsanzeige (Issue 18): Form (Ortsanzeige.Form) und Heimatland als ISO-3166-Kuerzel; leer = voll bzw. keins. */
    var placeFormat: String
        get() = prefs.getString("placeFormat", "").orEmpty()
        set(value) = prefs.putString("placeFormat", value)

    var homeCountry: String
        get() = prefs.getString("homeCountry", "").orEmpty()
        set(value) = prefs.putString("homeCountry", value)
}
