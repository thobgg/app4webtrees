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
}
