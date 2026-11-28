package de.bgghome.webtrees.nativ.ui

import de.bgghome.webtrees.nativ.api.DNA_STOP_NICHT_LEIBLICH
import de.bgghome.webtrees.nativ.api.DnaLinie
import de.bgghome.webtrees.nativ.api.DnaLinien
import de.bgghome.webtrees.nativ.api.DnaNachkomme
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.kennungen
import de.bgghome.webtrees.nativ.api.selbstGetestet
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

/** Tests, Treffer und Testkandidaten: die Antworten von ApiPerson und ApiTesters des Moduls dna4webtrees lesen. */
class DnaModulTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val person = json.decodeFromString(de.bgghome.webtrees.nativ.api.DnaPerson.serializer(), """
        {"ok":true,"level":3,"xref":"I1","canEdit":true,"unprotected":8,
         "entries":[{"xref":"I1","factId":"a","kind":"y-other","value":"M269+, U106+","type":"Y-DNA","provider":"","date":"",
                     "calculated":{"clade":"R-U106","lineage":["R","R-U106"],"tree":"YFull 14.06.0","tie":false,"unknown":[]},"restriction":"",
                     "links":[{"site":"YFull","url":"https://www.yfull.com/tree/R-U106/"}]}],
         "lines":{"y":{"entries":[{"xref":"I27","factId":"b","kind":"y-haplogroup","value":"I-DF29","type":"Y-DNA Haplogroup","provider":"FamilyTreeDNA","date":"SEP 2023","calculated":null}],
                       "contradiction":{"by":"tree","names":["R-U106","I-DF29"]}},
                  "mt":{"entries":[],"contradiction":null}},
         "matches":[
           {"recordedAt":"I1","factId":"m1","other":"I88","private":false,"name":null,"cm":900,"segments":27,"longest":51.5,"segmentsOnX":1,"hasSegmentTable":true,
            "provider":"","date":"","relationship":{"code":"1C","text":"Cousin","half":false,"multiple":false,"distant":false},"commonAncestors":["I19","I20"],
            "usual":{"average":866,"minimum":396,"maximum":1397},"outsideRange":false,"candidates":[],"x":{"possible":true,"nearestCommonAncestor":"I19"},
            "warnings":["unrestricted"],"restriction":"","sharedCmUrl":"https://dnapainter.com/tools/sharedcmv4/900"},
           {"recordedAt":"I1","factId":"m2","other":null,"private":false,"name":"J. S.","cm":62,"segments":3,"longest":28.4,"segmentsOnX":null,"hasSegmentTable":false,
            "provider":"FamilyTreeDNA","date":"2023","relationship":null,"commonAncestors":[],"usual":null,"outsideRange":null,
            "candidates":[{"code":"2C1R","text":"Cousin 2. Grades, um 1 Generation versetzt"}],"x":null,"warnings":[]}]}
    """.trimIndent())

    @Test
    fun trefferLesen() {
        val im = person.matches[0]
        assertEquals("1C", im.relationship?.code)
        assertEquals(866.0, im.usual?.average)
        assertEquals("I19", im.x?.nearestCommonAncestor)
        assertEquals(listOf(de.bgghome.webtrees.nativ.api.DnaWarnung.OHNE_SCHUTZ), im.warnings)
        val ausserhalb = person.matches[1]
        assertNull(ausserhalb.other)
        assertEquals("J. S.", ausserhalb.name)
        assertNull(ausserhalb.segmentsOnX)
        assertEquals("2C1R", ausserhalb.candidates.single().code)
    }

    @Test
    fun eintraegeUndWiderspruch() {
        assertEquals("R-U106", person.entries.single().calculated?.clade)
        assertEquals("tree", person.lines.getValue("y").contradiction?.by)
        assertNull(person.lines.getValue("mt").contradiction)
    }

    @Test
    fun kennungenFuerDieNamen() {
        // die Person selbst und Treffer ausserhalb des Baums brauchen keinen Abruf
        assertEquals(setOf("I27", "I88", "I19", "I20"), person.kennungen())
        val t = json.decodeFromString(de.bgghome.webtrees.nativ.api.DnaTester.serializer(),
            """{"ok":true,"level":3,"xref":"I21","lines":{"y":{"earliestAncestor":"I54","onLine":23,"living":[{"xref":"I27","hasEntry":true},{"xref":"I3","hasEntry":false}]},"mt":{"earliestAncestor":null,"onLine":1,"living":[]}}}""")
        assertEquals(setOf("I54", "I27", "I3"), t.kennungen())
        assertTrue(t.lines.getValue("y").living[0].hasEntry)
    }

    @Test
    fun centimorganInDerSchreibweiseDerSprache() {
        assertEquals("900", cmText(900.0, java.util.Locale.GERMAN))
        assertEquals("849,9", cmText(849.9, java.util.Locale.GERMAN))
        assertEquals("849.9", cmText(849.9, java.util.Locale.ENGLISH))
    }
}

/** Abdeckung, gestuetzte Vorfahren und das Datum der DNA-Eintraege. */
class DnaAuswertungTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun datumInDerSpracheDerApp() {
        assertEquals("März 2022", de.bgghome.webtrees.nativ.api.dnaDatum("MAR 2022", java.util.Locale.GERMAN))
        assertEquals("February 2024", de.bgghome.webtrees.nativ.api.dnaDatum("FEB 2024", java.util.Locale.ENGLISH))
        assertEquals("2022", de.bgghome.webtrees.nativ.api.dnaDatum("2022", java.util.Locale.GERMAN))
        assertTrue(de.bgghome.webtrees.nativ.api.dnaDatum("12 MAR 2022", java.util.Locale.GERMAN).contains("März"))
        // was kein einfaches Datum ist, bleibt stehen
        assertEquals("ABT 2020", de.bgghome.webtrees.nativ.api.dnaDatum("ABT 2020", java.util.Locale.GERMAN))
        assertEquals("", de.bgghome.webtrees.nativ.api.dnaDatum("", java.util.Locale.GERMAN))
    }

    @Test
    fun selbstGetestet() {
        fun p(eintraege: String, treffer: String = "[]") = json.decodeFromString(de.bgghome.webtrees.nativ.api.DnaPerson.serializer(),
            """{"xref":"I21","entries":$eintraege,"lines":{},"matches":$treffer}""")
        assertTrue(p("""[{"xref":"I21","factId":"a","kind":"test","value":"Autosomal"}]""").selbstGetestet())
        assertTrue(p("[]", """[{"recordedAt":"I21","factId":"m","other":"I1","cm":200}]""").selbstGetestet())
        // ein Y-DNA-Test allein deckt die autosomale DNA nicht ab
        assertFalse(p("""[{"xref":"I21","factId":"a","kind":"test","value":"Y-DNA"},{"xref":"I21","factId":"b","kind":"y-haplogroup","value":"R-U106"}]""").selbstGetestet())
        assertFalse(p("[]").selbstGetestet())
    }

    @Test
    fun abdeckungUndGestuetztLesen() {
        val a = json.decodeFromString(de.bgghome.webtrees.nativ.api.DnaAbdeckung.serializer(),
            """{"ok":true,"level":6,"xref":"I21","generations":8,"coverage":0.4351,"descendants":13,"tested":["I1","I5"],"candidates":[{"xref":"I27","gain":0.0435}]}""")
        assertEquals(0.4351, a.coverage)
        // aeltere Module kennen selfTested nicht
        kotlin.test.assertNull(a.selfTested)
        assertTrue(json.decodeFromString(de.bgghome.webtrees.nativ.api.DnaAbdeckung.serializer(), """{"xref":"I1","selfTested":true,"coverage":1}""").selfTested == true)
        assertEquals(setOf("I1", "I5", "I27"), a.kennungen())
        val g = json.decodeFromString(de.bgghome.webtrees.nativ.api.DnaGestuetzt.serializer(), """
            {"ok":true,"level":6,"xref":"I1","generations":5,"rule":"wikitree-single-match","links":[
             {"generation":1,"child":"I1","parent":"I8","supported":true,"matches":[{"recordedAt":"I1","factId":"a","other":"I12","cm":849.9,"relationship":"1C"}]},
             {"generation":1,"child":"I1","parent":"I9","supported":false,"matches":[]}]}
        """.trimIndent())
        assertEquals(1, g.links.count { it.supported })
        assertEquals(setOf("I1", "I8", "I9", "I12"), g.kennungen())
    }
}
