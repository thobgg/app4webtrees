package de.bgghome.webtrees.nativ

/**
 * Paketvariante (-Dwtand.variante aus desktop/build.gradle.kts): leer = wtWin/wtTux, "7" = wtWin7/wtTux7 fuer webtrees7
 * mit GEDCOM 7.0. Die Variante installiert und laeuft neben der normalen Fassung: eigene Ordner, eigene Einstellungen,
 * eigene Sperre, keine Update-Pruefung und kein Verbinden-Schema.
 */
object Variante {
    val kennung: String = System.getProperty("wtand.variante").orEmpty()

    /** Ordnername fuer Daten, Cache und Einstellungen: app4webtrees bzw. app4webtrees7 */
    val ordner: String = "app4webtrees$kennung"

    /** Fuer Fenstertitel und "Ueber", leer bei der normalen Fassung. */
    val zusatz: String = if (kennung == "7") "webtrees7 / GEDCOM 7.0" else ""
}
