package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.api.Associate
import de.bgghome.webtrees.nativ.api.DateJson
import de.bgghome.webtrees.nativ.api.EventJson
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.FamilyJson
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.api.Person
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Issue 21: Buecher zum Weitergeben zeigen nur, was Besucher in webtrees sehen duerften. */
class BuchOeffentlichTest {
    private fun ev(j: Int) = EventJson(date = DateJson(text = "$j", year = j))

    @Test
    fun eingeschraenkteEreignissePatenUndPersonen() {
        val kind = Person("I3", name = "Lebhart Offen", birth = ev(1990), lifespan = "1990–", access = "member", nameAccess = "member")
        val tochter = Person("I6", name = "Lina Offen", birth = ev(1995), lifespan = "1995–", access = "member", nameAccess = "visitor")
        val ehe = FamilyJson("F1", children = listOf(kind, tochter), marriage = ev(1828), access = "manager",
            facts = listOf(FactJson("m", tag = "MARR", access = "visitor")))
        val d = IndividualDetail(
            person = Person("I1", name = "Theodor Offen", isDead = true, birth = ev(1800), death = ev(1870), occupation = "Markerberuf",
                lifespan = "1800–1870", access = "visitor", restricted = listOf("OCCU")),
            facts = listOf(
                FactJson("n", tag = "NAME", access = "visitor"),
                FactJson("b", tag = "CHR", access = "visitor", associates = listOf(Associate("I2", access = "visitor"), Associate("I3", access = "member"))),
                FactJson("o1", tag = "OCCU", value = "Schmied", access = "visitor"),
                FactJson("o2", tag = "OCCU", value = "Markerberuf", access = "member"),
                FactJson("r", tag = "RELI", value = "geheim", access = "manager"),
            ),
            spouseFamilies = listOf(ehe),
        )
        val o = d.fuer(Sichtbarkeit.Besucher)
        assertEquals(listOf("NAME", "CHR", "OCCU"), o.facts.map { it.tag })
        assertEquals("Schmied", o.facts.first { it.tag == "OCCU" }.value)
        // der lebende Pate faellt weg, der verstorbene bleibt
        assertEquals(listOf("I2"), o.facts.first { it.tag == "CHR" }.associates.map { it.xref })
        // Beruf aus der Kurzfassung weg, Lebensdaten bleiben
        assertNull(o.person.occupation)
        assertEquals("1800 – 1870", o.person.lifespan)
        // Familie nur fuer Verwalter: ohne Ereignisse und Heirat; das lebende Kind nur mit Namen
        val f = o.spouseFamilies.single()
        assertTrue(f.facts.isEmpty())
        assertNull(f.marriage)
        // Name verborgen (Baum zeigt Namen Lebender nur Mitgliedern) -> "Privat"; Name freigegeben -> bleibt, Daten weg
        assertTrue(f.children[0].name != "Lebhart Offen" && f.children[0].given.isEmpty())
        assertEquals("Lina Offen", f.children[1].name)
        assertNull(f.children[1].birth)
        assertEquals("", f.children[1].lifespan)
    }

    @Test
    fun mitgliederSehenMehrAlsBesucher() {
        val kind = Person("I3", name = "Lebhart Offen", birth = ev(1990), lifespan = "1990–", access = "member", nameAccess = "member")
        val geheim = Person("I4", name = "Gerda Geheim", birth = ev(1992), access = "manager", nameAccess = "manager")
        val ehe = FamilyJson("F1", children = listOf(kind, geheim), marriage = ev(1828), access = "member",
            facts = listOf(FactJson("m", tag = "MARR", access = "member")))
        val d = IndividualDetail(
            person = Person("I1", name = "Theodor Offen", isDead = true, birth = ev(1800), death = ev(1870), occupation = "Markerberuf",
                access = "visitor", restricted = listOf("OCCU", "BURI"), buri = ev(1870)),
            facts = listOf(
                FactJson("b", tag = "CHR", access = "visitor", associates = listOf(Associate("I2", access = "visitor"), Associate("I3", access = "member"), Associate("I4", access = "manager"))),
                FactJson("o2", tag = "OCCU", value = "Markerberuf", access = "member"),
                FactJson("u", tag = "BURI", access = "manager"),
                FactJson("r", tag = "RELI", value = "geheim", access = "manager"),
            ),
            spouseFamilies = listOf(ehe),
        )
        val o = d.fuer(Sichtbarkeit.Mitglieder)
        assertEquals(listOf("CHR", "OCCU"), o.facts.map { it.tag })
        assertEquals(listOf("I2", "I3"), o.facts.first { it.tag == "CHR" }.associates.map { it.xref })
        // Beruf nur fuer Mitglieder -> bleibt; Begraebnis nur fuer Verwalter -> weg
        assertEquals("Markerberuf", o.person.occupation)
        assertNull(o.person.buri)
        val f = o.spouseFamilies.single()
        assertEquals(listOf("MARR"), f.facts.map { it.tag })
        assertEquals("Lebhart Offen", f.children[0].name)
        assertEquals(1990, f.children[0].birth?.date?.year)
        // nur fuer Verwalter: ohne Daten und als "Privat"
        assertNull(f.children[1].birth)
        assertTrue(f.children[1].name != "Gerda Geheim")
        // Alles: unveraendert
        assertEquals(d, d.fuer(Sichtbarkeit.Alle))
    }

    @Test
    fun ohneAngabeBleibtAlles() {
        // aelteres Modul ohne access: nichts wird weggelassen
        val d = IndividualDetail(person = Person("I1", birth = ev(1800)), facts = listOf(FactJson("x", tag = "OCCU", value = "Bauer")))
        assertEquals(d.facts, d.fuer(Sichtbarkeit.Besucher).facts)
        assertEquals(1800, d.fuer(Sichtbarkeit.Besucher).person.birth?.date?.year)
    }
}
