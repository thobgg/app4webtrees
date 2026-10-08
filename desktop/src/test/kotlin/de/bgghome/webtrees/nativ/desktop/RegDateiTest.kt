package de.bgghome.webtrees.nativ.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Die .reg-Datei fuer wtwin://: Pfad mit Leerzeichen und Backslashes richtig maskiert, Befehl mit "%1". */
class RegDateiTest {
    @Test
    fun regDateiMitMaskiertemPfad() {
        val text = regDateiText("C:\\Users\\Rick M\\AppData\\Local\\wtWin\\wtWin.exe")
        val zeilen = text.split("\r\n")
        assertEquals("Windows Registry Editor Version 5.00", zeilen[0])
        assertTrue("[HKEY_CURRENT_USER\\Software\\Classes\\wtwin]" in zeilen)
        assertTrue("@=\"URL:wtWin\"" in zeilen)
        assertTrue("\"URL Protocol\"=\"\"" in zeilen)
        assertTrue("[HKEY_CURRENT_USER\\Software\\Classes\\wtwin\\shell\\open\\command]" in zeilen)
        assertTrue("@=\"\\\"C:\\\\Users\\\\Rick M\\\\AppData\\\\Local\\\\wtWin\\\\wtWin.exe\\\" \\\"%1\\\"\"" in zeilen, text)
        assertTrue("@=\"\\\"C:\\\\Users\\\\Rick M\\\\AppData\\\\Local\\\\wtWin\\\\wtWin.exe\\\",0\"" in zeilen, text)
        assertTrue(text.endsWith("\r\n"))
    }
}
