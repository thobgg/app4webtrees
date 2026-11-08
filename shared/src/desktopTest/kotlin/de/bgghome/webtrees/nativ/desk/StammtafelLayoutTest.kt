package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.api.Person
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StammtafelLayoutTest {
    private fun p(x: String) = Person(xref = x, name = x)
    private fun k(x: String, vararg kinder: TafelPerson) = TafelPerson(p(x), kinder.toList())

    private val baum = k("A", k("B", k("D"), k("E"), k("F")), k("C", k("G")))
    private val masse = TafelMasse(85f, true)

    @Test
    fun elternStehenMittigUeberIhrenKindern() {
        val l = stammtafelLayout(baum, masse)
        l.plaetze.groupBy { it.eltern }.forEach { (eltern, kinder) ->
            if (eltern == null) return@forEach
            val mitte = (kinder.minOf { it.mitteX } + kinder.maxOf { it.mitteX }) / 2
            assertEquals(mitte, eltern.mitteX, 0.01f, "Eltern ${eltern.knoten.person.xref}")
        }
    }

    @Test
    fun kaestenEinerReiheUeberlappenNicht() {
        val l = stammtafelLayout(baum, masse)
        l.plaetze.groupBy { it.ebene }.values.forEach { reihe ->
            reihe.sortedBy { it.mitteX }.zipWithNext().forEach { (a, b) -> assertTrue(b.mitteX - a.mitteX >= masse.rahmen, "Abstand ${a.knoten.person.xref}-${b.knoten.person.xref}") }
        }
        assertEquals(4, l.plaetze.count { it.ebene == 2 })
        assertEquals(l.breite, masse.slot * 4, 0.01f)
    }

    @Test
    fun doppelteBekommenNummern() {
        val doppelt = k("A", k("B", k("X")), k("C", k("X")))
        val n = doppelteNummern(stammtafelLayout(doppelt, masse).plaetze)
        assertEquals(mapOf("X" to 1), n)
    }

    @Test
    fun ehenSitzenNebenDerPersonUndKinderUnterDerHeiratslinie() {
        val kind = k("K")
        val mann = TafelPerson(Person(xref = "M", name = "M", sex = "M"), listOf(kind), ehen = listOf(TafelEhe(k("F"), listOf(kind))))
        val l = stammtafelLayout(mann, masse)
        val m = l.plaetze.first { it.knoten === mann }; val kp = l.plaetze.first { it.knoten === kind }
        // Partner rechts im Abstand ehe: die Heiratslinie liegt bei +ehe/2, dort mittig das Kind
        assertEquals(m.mitteX + masse.ehe / 2, kp.mitteX, 0.01f)
        val frau = TafelPerson(Person(xref = "W", name = "W", sex = "F"), listOf(kind), ehen = listOf(TafelEhe(k("H"), listOf(kind))))
        val lw = stammtafelLayout(frau, masse)
        assertEquals(lw.plaetze.first { it.knoten === frau }.mitteX - masse.ehe / 2, lw.plaetze.first { it.knoten === kind }.mitteX, 0.01f)
    }

    @Test
    fun zweiEhenLinksUndRechts() {
        val a = k("A"); val b = k("B")
        val p = TafelPerson(Person(xref = "P", name = "P", sex = "M"), listOf(a, b), ehen = listOf(TafelEhe(k("S1"), listOf(a)), TafelEhe(k("S2"), listOf(b))))
        assertEquals(1, p.ehenLinks); assertEquals(1, p.ehenRechts)
        val l = stammtafelLayout(p, masse)
        val pp = l.plaetze.first { it.knoten === p }
        assertEquals(pp.mitteX, (l.plaetze.first { it.knoten === a }.mitteX + l.plaetze.first { it.knoten === b }.mitteX) / 2, 0.01f)
        // Breite: Partnerkasten beidseits der Person
        assertTrue(l.breite >= masse.ehe * 2 + masse.slot - 0.01f)
    }
}
