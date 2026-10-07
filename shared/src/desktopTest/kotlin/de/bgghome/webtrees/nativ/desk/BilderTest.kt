package de.bgghome.webtrees.nativ.desk

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Bildspeicher: Pfade bleiben im Ordner, Verkleinern haelt das Seitenverhaeltnis, RGB ohne Transparenz. */
class BilderTest {
    @Test
    fun pfadeSicher() {
        assertEquals("portrait-i1.jpg", Bilder.sicher("portrait-i1.jpg"))
        assertEquals("Kirchenbuecher/taufe 1833.jpg", Bilder.sicher("/Kirchenbuecher\\taufe 1833.jpg"))
        assertNull(Bilder.sicher("../../etc/passwd"))
        assertNull(Bilder.sicher("a/../b.jpg"))
        assertNull(Bilder.sicher("C:/bilder/x.jpg"))
        assertNull(Bilder.sicher(""))
        assertNull(Bilder.sicher(null))
    }

    @Test
    fun verkleinernUndRgb() {
        val gross = BufferedImage(1600, 800, BufferedImage.TYPE_INT_ARGB)
        val k = Bilder.verkleinert(gross, 400)
        assertEquals(400 to 200, k.width to k.height)
        assertEquals(BufferedImage.TYPE_INT_RGB, k.type)
        val klein = BufferedImage(120, 90, BufferedImage.TYPE_INT_ARGB)
        val u = Bilder.verkleinert(klein, 400)
        assertEquals(120 to 90, u.width to u.height, "nie vergroessern")
        assertEquals(BufferedImage.TYPE_INT_RGB, u.type)
    }
}
