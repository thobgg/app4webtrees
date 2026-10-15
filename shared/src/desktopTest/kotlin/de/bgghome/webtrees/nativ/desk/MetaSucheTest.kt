package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.FamilyJson
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.PlaceJson
import kotlin.test.Test
import kotlin.test.assertEquals

/** CompGen-Meta-Suche: welcher Name und welcher Ort an meta.genealogy.net gehen. */
class MetaSucheTest {
    @Test
    fun nameOhneZusatz() {
        assertEquals("Bülow", metaNachname("von Bülow"))
        assertEquals("Berg", metaNachname("van der Berg"))
        assertEquals("Medici", metaNachname("de' Medici"))
        assertEquals("Mohwinkel", metaNachname(" Mohwinkel "))
        // nur ein Zusatz: dann der ganze Name
        assertEquals("Von", metaNachname("Von"))
    }

    @Test
    fun adresseMitUmlautenUndOrt() {
        assertEquals("https://meta.genealogy.net/search?lastname=M%C3%BCller", metaSucheUrl("Müller"))
        assertEquals("https://meta.genealogy.net/search?lastname=Mohwinkel&placename=Gro%C3%9F%20Eicklingen", metaSucheUrl("Mohwinkel", "Groß Eicklingen"))
        assertEquals("https://meta.genealogy.net/search?lastname=Mohwinkel", metaSucheUrl("Mohwinkel", " "))
    }

    @Test
    fun namenUndOrteDerPerson() {
        fun ort(n: String) = PlaceJson(name = n)
        val d = IndividualDetail(
            person = Person("I130", name = "Anna Mohwinkel", surname = "Mohwinkel"),
            facts = listOf(
                FactJson("n1", tag = "NAME", value = "Anna /Mohwinkel/"),
                FactJson("n2", tag = "NAME", value = "Anna /von Hinrichs/"),
                FactJson("d", tag = "DEAT", place = ort("Uelzen, Niedersachsen, Deutschland")),
                FactJson("b", tag = "BIRT", place = ort("Hof Nr. 3, Bienenbüttel, Uelzen, Niedersachsen, Deutschland")),
                FactJson("t", tag = "CHR", place = ort("Bienenbüttel, Uelzen")),
            ),
            spouseFamilies = listOf(FamilyJson("F1", facts = listOf(FactJson("m", tag = "MARR", place = ort("Celle"))))),
        )
        assertEquals(listOf("Mohwinkel", "Hinrichs"), metaNamen(d))
        // Reihenfolge Geburt, Taufe, Heirat, Tod; Hausnummer uebersprungen; jeder Ort einmal
        assertEquals(listOf("Bienenbüttel", "Celle", "Uelzen"), metaOrte(d).map { it.first })
    }
}
