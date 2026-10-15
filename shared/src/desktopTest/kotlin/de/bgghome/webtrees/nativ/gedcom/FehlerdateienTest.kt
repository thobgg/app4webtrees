package de.bgghome.webtrees.nativ.gedcom

import de.bgghome.webtrees.nativ.pruefung.PruefOptionen
import de.bgghome.webtrees.nativ.pruefung.Regelkatalog
import de.bgghome.webtrees.nativ.pruefung.pruefen
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Fehlerdateien zum Demo-Stammbaum (testdaten/falkenrath-1.7-fehler*.ged): jede Regel muss bei ihrem Fall
 * anschlagen. Die Faelle stehen in fehlerfaelle.tsv (Regel, Version, Kennungen, Fehler); "(Kopf)" und "(Datei)"
 * heissen: irgendein Treffer der Regel genuegt.
 */
class FehlerdateienTest {

    private val faelle = File("../testdaten/fehlerfaelle.tsv").readLines().drop(1).map { it.split('\t') }

    private fun pruefe(datei: String, version: String, nichtAnwendbar: String) {
        val f = File("../testdaten/$datei")
        val (baum, befund) = GedcomLeser.auswerten(GedcomLeser.lesen(f), jetzt = 2026, dateiname = f.name)
        assertEquals(version, befund.version)
        val e = pruefen(baum, PruefOptionen(aus = emptySet(), jetzt = 2026, datei = befund))
        val fehlt = Regelkatalog.alle.map { it.id }.filter { it != nichtAnwendbar }.filterNot { id ->
            val fall = faelle.firstOrNull { it[0] == id && it[1] == version } ?: return@filterNot false
            val erwartet = fall[2].split(' ')
            val t = e.treffer[id].orEmpty()
            if (erwartet.any { it.startsWith("(") }) t.isNotEmpty() else t.any { it.person in erwartet || it.familie in erwartet }
        }
        assertTrue(fehlt.isEmpty(), "$datei: Regeln ohne Treffer bei ihrem Fall: $fehlt")
        assertTrue(e.treffer[nichtAnwendbar].orEmpty().isEmpty(), "$datei: $nichtAnwendbar gilt hier nicht")
    }

    @Test
    fun gedcom551() = pruefe("falkenrath-1.7-fehler.ged", "5.5.1", nichtAnwendbar = "624")

    @Test
    fun gedcom7() = pruefe("falkenrath-1.7-fehler-gedcom7.ged", "7.0", nichtAnwendbar = "621")
}
