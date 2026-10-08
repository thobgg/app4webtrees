package de.bgghome.webtrees.nativ

import de.bgghome.webtrees.nativ.ui.verbindungAusText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Verbinden-Links von der Seite "App": wie api4webtrees sie baut, und wie Windows sie kanonisiert weiterreicht. */
class VerbindenLinkTest {
    private val url = "https%3A%2F%2Falm-226.servbay"

    @Test
    fun linkWieVonApi4webtrees() {
        val r = verbindungAusText("wtwin://connect?url=$url&code=66d7b82f&tree=demo&user=alice")!!
        assertEquals("https://alm-226.servbay", r.url)
        assertEquals("66d7b82f", r.code)
        assertEquals("demo", r.tree)
        assertEquals("alice", r.user)
    }

    @Test
    fun linkMitSchraegstrichWieVonWindows() {
        val r = verbindungAusText("wtwin://connect/?url=$url&code=66d7b82f")!!
        assertEquals("https://alm-226.servbay", r.url)
        assertEquals("66d7b82f", r.code)
        assertEquals("", r.tree)
    }

    @Test
    fun andereSchemataUndMuell() {
        assertEquals("abc", verbindungAusText("WTTUX://connect?url=http%3A%2F%2Fnas&code=abc")!!.code)
        assertNull(verbindungAusText("https://example.org/connect?url=x&code=y"))
        assertNull(verbindungAusText("wtwin://connect?url=ftp%3A%2F%2Fx&code=y"))
        assertNull(verbindungAusText("wtwin://connect?url=https%3A%2F%2Fx"))
        assertNull(verbindungAusText("wtwin://connectx?url=https%3A%2F%2Fx&code=y"))
        assertNull(verbindungAusText("wtwin://connect/x?url=https%3A%2F%2Fx&code=y"))
        assertNull(verbindungAusText(null))
        assertNull(verbindungAusText("Hallo Welt"))
    }
}
