package de.bgghome.webtrees.nativ.data

/**
 * Schluessel-Wert-Ablage, hinter der die Plattform steckt (Android: SharedPreferences, Desktop: java.util.prefs).
 * Bewusst so schmal wie SharedPreferences, damit die Android-Fassung eine reine Weiterleitung ist und die
 * Schluessel bestehender Installationen weitergelten. Je Zweck eine eigene Ablage ("settings", "cookies",
 * "wtclient"), wie vor der Aufteilung (23.09.2026).
 */
interface Ablage {
    fun getString(key: String, default: String?): String?
    fun putString(key: String, value: String?)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    /** Alle Eintraege, deren Wert ein Text ist (fuer die Cookie-Ablage). */
    fun alle(): Map<String, String>
    /** Mehrere Texte auf einmal schreiben - auf Android ein einziger apply(). */
    fun putStrings(values: Map<String, String>) = values.forEach { (k, v) -> putString(k, v) }
    fun leeren()
}
