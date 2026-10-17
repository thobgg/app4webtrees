package de.bgghome.webtrees.nativ.ui

import de.bgghome.webtrees.nativ.api.DescendantFamily
import de.bgghome.webtrees.nativ.api.DescendantNode
import de.bgghome.webtrees.nativ.api.Person
import kotlin.test.Test
import kotlin.test.assertEquals

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
    }
}
