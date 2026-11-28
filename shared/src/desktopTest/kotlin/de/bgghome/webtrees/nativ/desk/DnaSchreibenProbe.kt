package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.api.DnaTestEingabe
import de.bgghome.webtrees.nativ.api.DnaTrefferEingabe
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.data.Ablage
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Kein Test im ueblichen Lauf, sondern eine Probe gegen einen echten Server mit dem Modul dna4webtrees: legt ueber den
 * Client der App einen DNA-Test und einen DNA-Treffer an, liest beide ueber das Modul zurueck, prueft den Schutzvermerk
 * und loescht sie wieder (Loeschen wie bei jedem Ereignis ueber api4webtrees). Die Sprache geht mit Region an den Server,
 * wie von einem echten Geraet. Laeuft nur mit gesetztem WT_DNAPROBE:
 *
 *   WT_DNAPROBE=1 WT_URL=http://... WT_BAUM=baum WT_XREF=I21 WT_USER=admin WT_PASS=... ./gradlew :shared:desktopTest --rerun --tests '*DnaSchreibenProbe*'
 *
 * Das Konto braucht Sofortfreigabe (auto_accept), sonst warten die Aenderungen und das Zuruecklesen schlaegt fehl.
 */
class DnaSchreibenProbe {
    private class Speicher : Ablage {
        private val m = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = m[key] as? String ?: default
        override fun putString(key: String, value: String?) { m[key] = value }
        override fun getBoolean(key: String, default: Boolean) = m[key] as? Boolean ?: default
        override fun putBoolean(key: String, value: Boolean) { m[key] = value }
        override fun alle() = m.filterValues { it is String }.mapValues { it.value as String }
        override fun leeren() = m.clear()
    }

    @Test
    fun anlegenLesenLoeschen() {
        System.getenv("WT_DNAPROBE") ?: return
        Locale.setDefault(Locale.GERMANY)
        val client = WtClient(Speicher(), Speicher(), "wtTux/dev (DNA-Probe)").apply { baseUrl = System.getenv("WT_URL")!! }
        val tree = System.getenv("WT_BAUM")!!; val xref = System.getenv("WT_XREF")!!
        runBlocking {
            client.login(System.getenv("WT_USER")!!, System.getenv("WT_PASS").orEmpty())
            val modul = client.dnaModul()
            println("Modul ${modul.version}, Stufe ${modul.level}, Testarten ${modul.testTypes}, Schutz ${modul.restrictions}")
            val vorher = client.dnaPerson(tree, xref)

            client.dnaTestAnlegen(tree, xref, DnaTestEingabe("Y-DNA", anbieter = modul.providers.first(), datum = "MAR 2022", haplogruppe = "R-U106", notiz = "Probe wtTux"))
            client.dnaTrefferAnlegen(tree, xref, DnaTrefferEingabe(name = "P. R. (Probe wtTux)", cm = "55,5", segmente = "3", laengstes = "21,4", datum = "2024"))
            val danach = client.dnaPerson(tree, xref)
            val neu = danach.entries.filter { e -> vorher.entries.none { it.factId == e.factId } }
            val neuTreffer = danach.matches.filter { m -> vorher.matches.none { it.factId == m.factId } }
            println("neu: " + neu.map { "${it.kind} ${it.value} ${it.provider} ${it.date} Schutz=${it.restriction}" })
            println("neuer Treffer: " + neuTreffer.map { "${it.name} ${it.cm} cM ${it.segments} Segmente laengstes ${it.longest} Schutz=${it.restriction} Kandidaten=${it.candidates.size}" })
            try {
                // Test mit Haplogruppe ergibt zwei Eintraege: den Test und die Haplogruppe
                assertEquals(setOf("test", "y-haplogroup"), neu.map { it.kind }.toSet())
                assertTrue(neu.all { !it.restriction.isNullOrEmpty() && it.restriction != "none" }, "jeder neue Eintrag traegt einen Schutzvermerk")
                val t = neuTreffer.single()
                assertEquals(55.5, t.cm); assertEquals(3, t.segments); assertEquals(21.4, t.longest)
                assertEquals("P. R. (Probe wtTux)", t.name)
                assertTrue(!t.restriction.isNullOrEmpty() && t.restriction != "none", "der Treffer traegt einen Schutzvermerk")
            } finally {
                (neu.map { it.factId } + neuTreffer.map { it.factId }).forEach { client.deleteFact(tree, xref, it) }
                val ende = client.dnaPerson(tree, xref)
                println("nach dem Loeschen: ${ende.entries.size} Eintraege (vorher ${vorher.entries.size}), ${ende.matches.size} Treffer (vorher ${vorher.matches.size})")
                assertEquals(vorher.entries.map { it.factId }.toSet(), ende.entries.map { it.factId }.toSet())
                assertEquals(vorher.matches.map { it.factId }.toSet(), ende.matches.map { it.factId }.toSet())
            }
        }
    }
}
