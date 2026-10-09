package de.bgghome.webtrees.nativ.lokal

import java.io.File
import java.time.LocalDateTime

/**
 * wtwin.log neben php.log (Issue 9): was auf wtWins Seite mit dem Stammbaum auf diesem PC schiefging - gescheiterte
 * Anfragen mit Uhrzeit und genauer Fehlermeldung, Wiederholungen, Neustarts, Startpruefung. php.log zeigt nur, was beim
 * Server ankam; ohne diese Seite liess sich nicht sagen, wo eine Antwort verloren ging. Keine Passwoerter, keine Inhalte.
 */
object LokalProtokoll {
    val datei: File get() = File(LokalOrte.basis, "wtwin.log")
    private const val GRENZE = 1_000_000L

    @Synchronized
    fun schreiben(text: String) {
        runCatching {
            LokalOrte.basis.mkdirs()
            val d = datei
            if (d.length() > GRENZE) d.renameTo(File(LokalOrte.basis, "wtwin.log.1").also { it.delete() })
            d.appendText("${LocalDateTime.now().withNano(0)} $text\n")
        }
    }
}
