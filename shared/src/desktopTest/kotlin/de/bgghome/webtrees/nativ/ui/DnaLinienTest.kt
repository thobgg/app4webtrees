package de.bgghome.webtrees.nativ.ui

import de.bgghome.webtrees.nativ.api.DNA_STOP_NICHT_LEIBLICH
import de.bgghome.webtrees.nativ.api.DnaLinie
import de.bgghome.webtrees.nativ.api.DnaLinien
import de.bgghome.webtrees.nativ.api.DnaNachkomme
import de.bgghome.webtrees.nativ.api.Person
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DNA-Vererbungslinien: die Antwort der Route ApiLines von dna4webtrees lesen und fuer die Liste aufbereiten. */
class DnaLinienTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun vorfahrenLesen() {
        // Form der Route, X-Linie: Grossmutter auf zwei Wegen (Kekule 5 und 7), Linie endet an einem Adoptivkind
        val r = json.decodeFromString(DnaLinien.serializer(), """
            {"root":"I1","line":"x","direction":"ancestors","generations":8,"neuesFeld":1,"generationsList":[
              {"generation":0,"possible":1,"people":[{"xref":"I1","sex":"F","sosa":[1],"carrier":true,"stop":null}]},
              {"generation":1,"possible":2,"people":[{"xref":"I2","sex":"M","sosa":[2],"carrier":true,"stop":null},
                                                     {"xref":"I3","sex":"F","private":true,"sosa":[3],"carrier":true,"stop":"non-biological"}]},
              {"generation":2,"possible":3,"people":[{"xref":"I5","sex":"F","sosa":[5,7],"carrier":true,"stop":null}]}]}
        """.trimIndent())
        assertEquals(listOf(1, 2, 3), r.generationsList.map { it.possible })
        assertEquals(listOf(5L, 7L), r.generationsList[2].people.single().sosa)
        assertTrue(r.generationsList[1].people[1].private)
        assertEquals(DNA_STOP_NICHT_LEIBLICH, r.generationsList[1].people[1].stop)
        assertNull(r.tree)
        assertFalse(dnaOhneVorfahren(r.generationsList))
    }

    @Test
    fun ohneVorfahrenNurOhneVermerk() {
        fun antwort(stop: String) = json.decodeFromString(DnaLinien.serializer(),
            """{"generationsList":[{"generation":0,"people":[{"xref":"I1","sosa":[1],"stop":$stop}]}]}""").generationsList
        assertTrue(dnaOhneVorfahren(antwort("null")))
        // Mit Vermerk steht der Grund schon an der Person - kein zweiter Satz "keine Vorfahren"
        assertFalse(dnaOhneVorfahren(antwort("\"unknown-sex\"")))
    }

    @Test
    fun nachkommenAlsEingerueckteListe() {
        val r = json.decodeFromString(DnaLinien.serializer(), """
            {"root":"I21","line":"y","direction":"descendants","generations":8,"count":3,"tree":
              {"xref":"I21","sex":"M","carrier":true,"passesOn":true,"stop":null,"children":[
                {"xref":"I15","sex":"M","carrier":true,"passesOn":true,"stop":null,"children":[
                  {"xref":"I8","sex":"M","carrier":true,"passesOn":true,"stop":null,"children":[]}]},
                {"xref":"I18","sex":"M","carrier":true,"passesOn":true,"stop":null,"children":[]}]}}
        """.trimIndent())
        val z = r.tree!!.zeilen()
        assertEquals(listOf("I21" to 0, "I15" to 1, "I8" to 2, "I18" to 1), z.map { it.knoten.xref to it.tiefe })
        assertEquals(r.count, z.size - 1)
        assertEquals(DnaNachkommenLage.Liste, dnaNachkommenLage(DnaLinie.Y, r.tree!!))
    }

    @Test
    fun hinweisUeberDenNachkommen() {
        fun k(sex: String, carrier: Boolean = true, passesOn: Boolean = true, kinder: List<DnaNachkomme> = emptyList()) =
            DnaNachkomme("I1", false, sex, carrier, passesOn, null, kinder)
        assertEquals(DnaNachkommenLage.KeinY, dnaNachkommenLage(DnaLinie.Y, k("F", carrier = false, passesOn = false)))
        assertEquals(DnaNachkommenLage.MannMt, dnaNachkommenLage(DnaLinie.Mt, k("M", passesOn = false)))
        assertEquals(DnaNachkommenLage.Keine, dnaNachkommenLage(DnaLinie.X, k("F")))
        assertEquals(DnaNachkommenLage.Liste, dnaNachkommenLage(DnaLinie.Mt, k("F", kinder = listOf(k("M", passesOn = false)))))
    }

    @Test
    fun personZurKennung() {
        val e = DnaLinienErgebnis(DnaLinien(), mapOf("I2" to Person("I2", name = "Carl Falkenrath", sex = "M")))
        assertEquals("Carl Falkenrath", e.person("I2", "M", false).name)
        // nicht zu laden: die Kennung steht da, nicht "Privat"
        assertEquals("I9", e.person("I9", "F", false).name)
        assertFalse(e.person("I9", "F", false).isPrivate)
        // vertraulich: bleibt vertraulich, das Geschlecht kommt vom Modul
        assertTrue(e.person("I7", "F", true).isPrivate)
        assertEquals("F", e.person("I7", "F", true).sex)
    }

    @Test
    fun obergrenzeJeLinie() {
        assertEquals(30, dnaHoechstens(DnaLinie.Y))
        assertEquals(30, dnaHoechstens(DnaLinie.Mt))
        assertEquals(12, dnaHoechstens(DnaLinie.X))
    }
}
