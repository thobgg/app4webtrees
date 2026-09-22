package de.bgghome.webtrees.nativ

import de.bgghome.webtrees.nativ.api.Ancestor
import de.bgghome.webtrees.nativ.api.DescendantFamily
import de.bgghome.webtrees.nativ.api.DescendantNode
import de.bgghome.webtrees.nativ.api.Pedigree
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.ui.tree.Sibling
import de.bgghome.webtrees.nativ.ui.tree.TreeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeLayoutTest {

    private fun p(xref: String, sex: String = "M") = Person(xref = xref, name = xref, sex = sex)

    /** Mittelperson mit zwei Ehen, Kindern, Enkeln; Ahnen lueckenhaft (Mutter fehlt, Grossvater ohne Eltern). */
    private fun sample(canEdit: Boolean, siblings: Map<String, List<Sibling>> = emptyMap(), cousins: Boolean = false): TreeLayout {
        val pedigree = Pedigree(
            root = "I1", generations = 4,
            ancestors = listOf(
                Ancestor(1, p("I1")), Ancestor(2, p("I2")), Ancestor(4, p("I4")), Ancestor(5, p("I5", "F")),
                Ancestor(8, p("I8")), Ancestor(9, p("I9", "F")), Ancestor(11, p("I11", "F")),
            ),
        )
        val grandchildren = listOf(DescendantNode(p("G1")), DescendantNode(p("G2")), DescendantNode(p("G3")))
        val descendants = DescendantNode(
            p("I1"),
            listOf(
                DescendantFamily("F1", spouse = p("S1", "F"), children = listOf(
                    DescendantNode(p("C1"), listOf(DescendantFamily("F3", spouse = p("S3", "F"), children = grandchildren))),
                    DescendantNode(p("C2", "F")),
                )),
                DescendantFamily("F2", spouse = p("S2", "F"), children = listOf(DescendantNode(p("C3")))),
            ),
        )

        return TreeLayout.build(pedigree, descendants, canEdit, siblings, cousins)
    }

    private fun assertNoOverlap(layout: TreeLayout) {
        val boxes = layout.boxes
        for (i in boxes.indices) for (j in i + 1 until boxes.size) {
            val a = boxes[i]; val b = boxes[j]
            val apart = a.right <= b.x || b.right <= a.x ||
                a.y + TreeLayout.BOX_H <= b.y || b.y + TreeLayout.BOX_H <= a.y
            assertTrue("Ueberlappung: ${a.person?.xref ?: a.placeholder} / ${b.person?.xref ?: b.placeholder}", apart)
        }
    }

    @Test
    fun readOnlyTreeHasEveryPersonOnceAndNoPlaceholders() {
        val layout = sample(canEdit = false)

        assertNoOverlap(layout)
        assertTrue(layout.boxes.none { it.placeholder != null })
        // 7 Ahnen (inkl. Mittelperson) + 3 Partner + 3 Kinder + 3 Enkel
        assertEquals(16, layout.boxes.size)
        assertEquals(1, layout.boxes.count { it.isFocus })
        assertEquals(layout.boxes.size, layout.boxes.map { it.person!!.xref }.toSet().size)
    }

    @Test
    fun editableTreeOffersMissingRelatives() {
        val layout = sample(canEdit = true)

        assertNoOverlap(layout)

        val placeholders = layout.boxes.mapNotNull { it.placeholder }
        // Mutter der Mittelperson fehlt
        assertNotNull(placeholders.firstOrNull { it.relation == "mother" && it.relativeTo.xref == "I1" })
        // Fehlende Eltern werden nur fuer die Mittelperson angeboten - I5 (Generation 2) hat zwar nur die Mutter,
        // bekommt aber keinen Platzhalter: jede Karte hat ihre "+"-Lasche, und die oberen Reihen bleiben schlank.
        assertNull(placeholders.firstOrNull { it.relation == "father" && it.relativeTo.xref == "I5" })
        assertNull(placeholders.firstOrNull { it.relativeTo.xref == "I8" })
        // Partner und Kind nur fuer die Mittelperson, das Kind mit beiden Verbindungen zur Wahl
        assertEquals(1, placeholders.count { it.relation == "spouse" })
        assertEquals(listOf("F1", "F2"), placeholders.single { it.relation == "child" }.families.map { it.first })
    }

    @Test
    fun generationsAreRowsAndFocusSitsBetweenParentsAndChildren() {
        val layout = sample(canEdit = false)
        val y = layout.boxes.associate { it.person!!.xref to it.y }

        assertTrue(y.getValue("I8") < y.getValue("I4"))
        assertTrue(y.getValue("I4") < y.getValue("I2"))
        assertTrue(y.getValue("I2") < y.getValue("I1"))
        assertEquals(y.getValue("I1"), y.getValue("S1"), 0.01f)
        assertTrue(y.getValue("I1") < y.getValue("C1"))
        assertTrue(y.getValue("C1") < y.getValue("G1"))
        assertEquals(y.getValue("I8"), y.getValue("I11"), 0.01f)
    }

    @Test
    fun siblingsSitBesideTheirPersonOnTheSameRow() {
        // Zwei Geschwister der Mittelperson (eines verheiratet), ein Bruder des Vaters, eine Schwester der Grossmutter I5
        val layout = sample(
            canEdit = true,
            siblings = mapOf(
                "I1" to listOf(Sibling(p("B1")), Sibling(p("B2", "F"), listOf(p("B2S")))),
                "I2" to listOf(Sibling(p("U1"))),
                "I5" to listOf(Sibling(p("A1", "F"), listOf(p("A1S")))),
            ),
        )
        assertNoOverlap(layout)

        val box = layout.boxes.filter { it.person != null }.associateBy { it.person!!.xref }
        // Mittelperson und Vater: Geschwister links; Mutter-Seite (I5): rechts
        assertEquals(box.getValue("I1").y, box.getValue("B1").y, 0.01f)
        assertTrue(box.getValue("B2").x < box.getValue("B1").x || box.getValue("B1").x < box.getValue("I1").x)
        assertTrue(box.getValue("B2S").x > box.getValue("B2").x && box.getValue("B2S").x < box.getValue("I1").x)
        assertTrue(box.getValue("U1").x < box.getValue("I2").x && box.getValue("U1").y == box.getValue("I2").y)
        assertTrue(box.getValue("A1").x > box.getValue("I5").x && box.getValue("A1S").x > box.getValue("A1").x)
        // Der Ahnenbaum steht weiter mittig ueber der Mittelperson: Vater links, Mutter-Platzhalter rechts
        val mother = layout.boxes.first { it.placeholder?.relation == "mother" && it.placeholder.relativeTo.xref == "I1" }
        assertTrue(box.getValue("I2").centerX < box.getValue("I1").centerX && mother.centerX > box.getValue("I1").centerX)
        assertTrue(layout.boxes.all { it.x >= 0 && it.right <= layout.width })
    }

    @Test
    fun cousinsHangBelowTheirParentsWithoutTouchingTheFocusRow() {
        // Onkel U1 mit drei Kindern (breiter als seine Karte), Tante A1 (Schwester der fehlenden Mutter gibt es nicht ->
        // Cousins nur vaeterlicherseits); die Mittelperson hat selbst zwei Geschwister in derselben Reihe.
        val siblings = mapOf(
            "I1" to listOf(Sibling(p("B1")), Sibling(p("B2", "F"), listOf(p("B2S")))),
            "I2" to listOf(Sibling(p("U1"), listOf(p("U1S", "F")), listOf(p("K1"), p("K2"), p("K3")))),
        )
        val without = sample(canEdit = true, siblings = siblings, cousins = false)
        val with = sample(canEdit = true, siblings = siblings, cousins = true)
        assertNoOverlap(without)
        assertNoOverlap(with)

        assertNull(without.boxes.firstOrNull { it.person?.xref == "K1" })

        val box = with.boxes.filter { it.person != null }.associateBy { it.person!!.xref }
        // Cousins in der Reihe der Mittelperson, unter dem Onkel, links von deren Geschwistern
        listOf("K1", "K2", "K3").forEach { assertEquals(box.getValue("I1").y, box.getValue(it).y, 0.01f) }
        assertTrue(box.getValue("K3").x + TreeLayout.BOX_W <= box.getValue("B1").x)
        assertTrue(box.getValue("K1").x < box.getValue("K2").x && box.getValue("K2").x < box.getValue("K3").x)
        assertTrue(box.getValue("K1").y > box.getValue("U1").y)
        assertTrue(with.boxes.all { it.x >= 0 && it.right <= with.width })
    }

    @Test
    fun childrenAndSpousesKeepTheirOrder() {
        val layout = sample(canEdit = false)
        val box = layout.boxes.associateBy { it.person!!.xref }

        // Partner rechts von der Person, die Kinder der ersten Verbindung links von denen der zweiten
        assertTrue(box.getValue("I1").x < box.getValue("S1").x && box.getValue("S1").x < box.getValue("S2").x)
        assertTrue(box.getValue("C1").x < box.getValue("C2").x && box.getValue("C2").x < box.getValue("C3").x)
        assertTrue(box.getValue("G1").x < box.getValue("G2").x && box.getValue("G2").x < box.getValue("G3").x)
        // Vater links von der Mutter, in jeder Reihe
        assertTrue(box.getValue("I4").x < box.getValue("I5").x)
        assertTrue(box.getValue("I8").x < box.getValue("I9").x && box.getValue("I9").x < box.getValue("I11").x)
        // Der Enkel-Block steht unter seinem Vater C1, nicht unter der Mittelperson
        val enkelMitte = (box.getValue("G1").centerX + box.getValue("G3").centerX) / 2
        assertEquals(box.getValue("C1").centerX, enkelMitte, TreeLayout.BOX_W)
    }

    @Test
    fun layoutIsDeterministic() {
        val a = sample(canEdit = true)
        val b = sample(canEdit = true)

        assertEquals(a.boxes, b.boxes)
        assertEquals(a.connectors, b.connectors)
        assertEquals(a.width, b.width, 0f)
        // Eine leere Geschwister-Tabelle aendert nichts
        assertEquals(a.boxes, sample(canEdit = true, siblings = emptyMap()).boxes)
    }

    @Test
    fun topRowOffersExpandingWhenParentsExistButAreNotLoaded() {
        val pedigree = Pedigree(
            root = "I1", generations = 2,
            ancestors = listOf(
                Ancestor(1, p("I1")),
                Ancestor(2, p("I2"), hasParents = true),
                Ancestor(3, p("I3", "F"), hasParents = false),
            ),
        )
        val layout = TreeLayout.build(pedigree, DescendantNode(p("I1")), canEdit = true)
        assertNoOverlap(layout)

        val father = layout.boxes.first { it.person?.xref == "I2" }
        val mother = layout.boxes.first { it.person?.xref == "I3" }
        assertTrue(father.canExpand)
        assertEquals(2, father.ahnen)
        // Die Mutter hat keine Eltern: kein Symbol - und auch keine "+"-Kaesten, die gibt es nur fuer die Mittelperson
        assertTrue(!mother.canExpand)
        assertEquals(0, layout.boxes.count { it.placeholder?.relativeTo?.xref == "I3" })
        // Dem Vater wird nichts angeboten: seine Eltern gibt es schon, sie sind nur nicht geladen
        assertEquals(0, layout.boxes.count { it.placeholder?.relativeTo?.xref == "I2" })
        // Das Symbol sitzt mittig ueber der Karte und ist dort treffbar - daneben nicht
        assertEquals("I2", layout.expandAt(father.centerX, father.y - TreeLayout.EXPAND_OFFSET)?.person?.xref)
        assertNull(layout.expandAt(father.centerX + 60, father.y - TreeLayout.EXPAND_OFFSET))
    }

    @Test
    fun expandingAddsARowAboveAndRemovesTheSymbol() {
        // So baut das View-Model den Baum nach "nach oben aufklappen": die Ahnen von Platz 2 kommen als 4 und 5 dazu.
        val before = Pedigree(root = "I1", generations = 2, ancestors = listOf(Ancestor(1, p("I1")), Ancestor(2, p("I2"), hasParents = true)))
        val after = before.copy(ancestors = before.ancestors + listOf(Ancestor(4, p("I4"), hasParents = true), Ancestor(5, p("I5", "F"))))

        val expanded = TreeLayout.build(after, DescendantNode(p("I1")), canEdit = false)
        assertNoOverlap(expanded)

        val box = expanded.boxes.associateBy { it.person!!.xref }
        assertTrue(!box.getValue("I2").canExpand)
        assertTrue(box.getValue("I4").canExpand)
        assertTrue(box.getValue("I4").y < box.getValue("I2").y && box.getValue("I2").y < box.getValue("I1").y)
        assertEquals(box.getValue("I4").y, box.getValue("I5").y, 0.01f)
        // Die Eltern stehen mittig ueber I2
        assertEquals(box.getValue("I2").centerX, (box.getValue("I4").centerX + box.getValue("I5").centerX) / 2, 0.01f)
    }

    @Test
    fun privatePeopleGetNoPlusAndNoPlaceholders() {
        val pedigree = Pedigree(
            root = "I1", generations = 2,
            ancestors = listOf(Ancestor(1, p("I1")), Ancestor(2, Person(xref = "I2", name = "Privat", isPrivate = true))),
        )
        val layout = TreeLayout.build(pedigree, DescendantNode(p("I1")), canEdit = true)

        val father = layout.boxes.first { it.person?.xref == "I2" }
        assertTrue(!layout.hasPlus(father))
        assertNull(layout.plusAt(father.centerX, father.bottom))
        assertEquals(0, layout.boxes.count { it.placeholder?.relativeTo?.xref == "I2" })

        val focus = layout.focus
        assertTrue(layout.hasPlus(focus))
        assertEquals("I1", layout.plusAt(focus.centerX, focus.bottom)?.person?.xref)
    }

    @Test
    fun siblingsOfTheTopRowAreDrawnWithoutAParentLine() {
        // Geschwister gibt es nur, wenn die Eltern geladen wurden - kommt trotzdem eine Gruppe fuer die oberste
        // Reihe, wird sie gezeichnet, aber nicht an eine Linie gehaengt.
        val pedigree = Pedigree(root = "I1", generations = 1, ancestors = listOf(Ancestor(1, p("I1"))))
        val layout = TreeLayout.build(pedigree, DescendantNode(p("I1")), canEdit = false, siblings = mapOf("I1" to listOf(Sibling(p("B1")))))
        assertNoOverlap(layout)

        val box = layout.boxes.associateBy { it.person!!.xref }
        assertEquals(box.getValue("I1").y, box.getValue("B1").y, 0.01f)
        assertTrue(box.getValue("B1").x + TreeLayout.BOX_W + TreeLayout.SIBLING_GAP <= box.getValue("I1").x + 0.01f)
        assertTrue(layout.connectors.isEmpty())
    }

    @Test
    fun tapHitsTheRightBox() {
        val layout = sample(canEdit = false)
        val box = layout.boxes.first { it.person?.xref == "C3" }

        assertEquals("C3", layout.boxAt(box.centerX, box.centerY)?.person?.xref)
        assertNull(layout.boxAt(-5f, -5f))
        assertTrue(layout.width > 0 && layout.height > 0)
        assertTrue(layout.boxes.all { it.x >= 0 && it.y >= 0 && it.right <= layout.width })
    }
}
