package de.bgghome.webtrees.nativ.data

/**
 * Verschluesselt das Geraete-Token, bevor es in die [Ablage] geht. Android: Schluessel im Android Keystore (verlaesst das
 * Geraet nie). Am PC liegt das Token wie das Sitzungs-Cookie im Benutzerprofil ([Offen]); dort schuetzt der Wechsel des
 * Tokens bei jeder Benutzung: ein abgegriffenes Token ist nach dem naechsten Einloesen wertlos und sperrt das Geraet.
 */
interface Geheimfach {
    fun zu(klar: String): String
    /** null, wenn sich der Text nicht mehr entschluesseln laesst (Schluessel weg) - dann fragt die App nach dem Passwort. */
    fun auf(geheim: String): String?

    object Offen : Geheimfach {
        override fun zu(klar: String) = klar
        override fun auf(geheim: String) = geheim
    }
}
