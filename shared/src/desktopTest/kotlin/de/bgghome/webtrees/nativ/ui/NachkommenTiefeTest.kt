package de.bgghome.webtrees.nativ.ui

import de.bgghome.webtrees.nativ.api.DescendantFamily
import de.bgghome.webtrees.nativ.api.DescendantNode
import de.bgghome.webtrees.nativ.api.Person
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Baum: die unterste Reihe behaelt ihre Ehepartner, verliert aber die Kinder der Zusatzgeneration. */
class NachkommenTiefeTest {
    private fun p(x: String) = Person(x)

    @Test
    fun untersteReiheBehaeltPartner() {
        val urenkel = DescendantNode(p("I4"))
        val enkel = DescendantNode(p("I3"), listOf(DescendantFamily("F3", spouse = p("I30"), children = listOf(urenkel))))
        val kind = DescendantNode(p("I2"), listOf(DescendantFamily("F2", spouse = p("I20"), children = listOf(enkel))))
        val wurzel = DescendantNode(p("I1"), listOf(DescendantFamily("F1", spouse = p("I10"), children = listOf(kind))))

        val k = wurzel.bisTiefe(3)
        val e = k.families.single().children.single().families.single().children.single()
        assertEquals("I3", e.person.xref)
        // Ehefrau des Enkels bleibt, seine Kinder (Zusatzgeneration) nicht
        assertEquals("I30", e.families.single().spouse?.xref)
        assertEquals(emptyList(), e.families.single().children)
        // die oberen Reihen unveraendert
        assertEquals("I20", k.families.single().children.single().families.single().spouse?.xref)
        // der Enkel hat weitere Nachkommen und ist aufklappbar
        assertTrue(e.weitere)
        assertFalse(k.weitere)
    }

    @Test
    fun aufklappenHaengtZweigEin() {
        val enkel = DescendantNode(p("I3"), listOf(DescendantFamily("F3", spouse = p("I30"))), weitere = true)
        val wurzel = DescendantNode(p("I1"), listOf(DescendantFamily("F1", children = listOf(DescendantNode(p("I2"), listOf(DescendantFamily("F2", children = listOf(enkel))))))))
        val zweig = DescendantNode(p("I3"), listOf(DescendantFamily("F3", spouse = p("I30"), children = listOf(DescendantNode(p("I4"), weitere = true)))))

        val neu = wurzel.einhaengen("I3", zweig)
        val e = neu.families.single().children.single().families.single().children.single()
        assertFalse(e.weitere)
        assertEquals(listOf("I4"), e.families.single().children.map { it.person.xref })
        assertTrue(e.families.single().children.single().weitere)

        // Im Baum: die Karte des Urenkels traegt das Symbol, ein Tipp darauf trifft sie
        val layout = de.bgghome.webtrees.nativ.ui.tree.TreeLayout.build(de.bgghome.webtrees.nativ.api.Pedigree(root = "I1", generations = 1,
            ancestors = listOf(de.bgghome.webtrees.nativ.api.Ancestor(1, p("I1")))), neu, canEdit = true)
        val karte = layout.boxes.single { it.person.xref == "I4" }
        assertTrue(karte.canExpandDown)
        val x = karte.centerX + layout.expandDownDx(karte)
        assertEquals("I4", layout.expandDownAt(x, karte.bottom + de.bgghome.webtrees.nativ.ui.tree.TreeLayout.EXPAND_OFFSET - 6f)?.person?.xref)
        assertEquals(null, layout.expandDownAt(karte.centerX - 60f, karte.bottom + 14f))

        // Der aufgeklappte Enkel laesst sich wieder zuklappen: Kinder weg, Partner bleibt, wieder aufklappbar
        val enkelKarte = layout.boxes.single { it.person.xref == "I3" }
        assertTrue(enkelKarte.canCollapse)
        assertEquals("I3", layout.collapseAt(enkelKarte.centerX + layout.expandDownDx(enkelKarte), enkelKarte.bottom + de.bgghome.webtrees.nativ.ui.tree.TreeLayout.EXPAND_OFFSET - 6f)?.person?.xref)
        val zu = neu.zuklappen("I3").families.single().children.single().families.single().children.single()
        assertTrue(zu.weitere)
        assertFalse(zu.aufgeklappt)
        assertEquals("I30", zu.families.single().spouse?.xref)
        assertEquals(emptyList(), zu.families.single().children)
    }
}
