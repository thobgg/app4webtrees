package de.bgghome.webtrees.nativ.ui

import de.bgghome.webtrees.nativ.api.DescendantFamily
import de.bgghome.webtrees.nativ.api.DescendantNode
import de.bgghome.webtrees.nativ.api.Person
import kotlin.test.Test
import kotlin.test.assertEquals

/** Baum: Geschwister aus den Nachkommen eines Elternteils, mit Partnern, Cousins und deren Partnern (Issue 33). */
class GeschwisterAusNachkommenTest {
    private fun p(x: String, private: Boolean = false) = Person(x, name = x, isPrivate = private)

    @Test
    fun cousinsBekommenIhrePartner() {
        // Vater V mit Mutter M: Kinder sind die Mittelperson I1 und Onkel U1; U1 hat mit U1S die Kinder K1 (verheiratet mit K1S,
        // mit einem eigenen Kind E1 aus der Zusatzgeneration) und K2 (ledig). Aus einer anderen Ehe des Vaters: Halbbruder H1.
        val k1 = DescendantNode(p("K1"), listOf(DescendantFamily("F4", spouse = p("K1S"), children = listOf(DescendantNode(p("E1"))))))
        val k2 = DescendantNode(p("K2"))
        val u1 = DescendantNode(p("U1"), listOf(DescendantFamily("F3", spouse = p("U1S"), children = listOf(k1, k2))))
        val families = listOf(
            DescendantFamily("F1", spouse = p("M"), children = listOf(DescendantNode(p("I1")), u1)),
            DescendantFamily("F2", spouse = p("M2"), children = listOf(DescendantNode(p("H1")))),
        )

        val siblings = siblingsAusNachkommen(families, p("I1"), p("M"))

        val onkel = siblings.single()
        assertEquals("U1", onkel.person.xref)
        assertEquals(listOf("U1S"), onkel.spouses.map { it.xref })
        assertEquals(listOf("K1", "K2"), onkel.children.map { it.xref })
        // Partner nur, wo es welche gibt; Enkel der Zusatzgeneration bleiben draussen
        assertEquals(mapOf("K1" to listOf("K1S")), onkel.childSpouses.mapValues { (_, v) -> v.map { it.xref } })
    }

    @Test
    fun ohneZweitenElternteilZaehlenAlleFamilien() {
        val families = listOf(
            DescendantFamily("F1", spouse = p("M"), children = listOf(DescendantNode(p("I1")), DescendantNode(p("B1")))),
            DescendantFamily("F2", children = listOf(DescendantNode(p("H1")))),
        )

        assertEquals(listOf("B1", "H1"), siblingsAusNachkommen(families, p("I1"), null).map { it.person.xref })
    }

    @Test
    fun privateBleibenDraussen() {
        val k1 = DescendantNode(p("K1"), listOf(DescendantFamily("F4", spouse = p("K1S", private = true))))
        val k2 = DescendantNode(p("K2", private = true))
        val u1 = DescendantNode(p("U1"), listOf(DescendantFamily("F3", children = listOf(k1, k2))))
        val families = listOf(DescendantFamily("F1", spouse = p("M"), children = listOf(DescendantNode(p("I1")), u1, DescendantNode(p("P1", private = true)))))

        val onkel = siblingsAusNachkommen(families, p("I1"), p("M")).single()
        assertEquals(listOf("K1"), onkel.children.map { it.xref })
        assertEquals(emptyMap(), onkel.childSpouses)
    }
}
