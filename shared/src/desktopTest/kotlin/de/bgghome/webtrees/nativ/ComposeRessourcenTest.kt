package de.bgghome.webtrees.nativ

import de.bgghome.webtrees.nativ.res.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Texte kommen seit 23.09.2026 aus Compose-Ressourcen statt aus Android-R.
 * Android hat Escapes (\n, \') und Platzhalter (%1$d) selbst aufgeloest; hier
 * wird festgehalten, dass die Compose-Ressourcen auf dem Desktop dasselbe tun.
 */
class ComposeRessourcenTest {
    @Test fun zeilenumbruchUndApostroph() {
        val s = Texte.t(Res.string.connect_confirm_text, "srv", "konto", "baum")
        assertTrue(s.contains('\n'), "\\n nicht aufgeloest: $s")
        assertTrue(!s.contains('\\'), "Backslash im Text: $s")
        // Sprache haengt vom System ab (hier meist Deutsch): nur pruefen, dass kein Escape stehen bleibt.
        val a = Texte.t(Res.string.err_http_only)
        assertTrue(!a.contains('\\'), "Backslash im Text: $a")
        assertTrue(a.contains("https://"), "Text unvollstaendig: $a")
    }

    @Test fun platzhalter() {
        val s = Texte.t(Res.string.home_greeting, "Thomas")
        assertTrue(s.contains("Thomas"), "Platzhalter nicht ersetzt: $s")
        assertTrue(!s.contains("%1"), "Platzhalter stehen geblieben: $s")
    }

    @Test fun mengenform() {
        val eins = Texte.plural(Res.plurals.archive_files, 1)
        val viele = Texte.plural(Res.plurals.archive_files, 3)
        assertTrue(viele.contains("3"), "Zahl fehlt: $viele")
        assertTrue(!viele.contains("%"), "Platzhalter stehen geblieben: $viele")
        assertTrue(eins != viele, "Einzahl und Mehrzahl gleich: $eins")
    }

    @Test fun unbekanntBleibtLesbar() {
        assertEquals("#msg_saved", "#" + Res.string.msg_saved.key)
    }
}
