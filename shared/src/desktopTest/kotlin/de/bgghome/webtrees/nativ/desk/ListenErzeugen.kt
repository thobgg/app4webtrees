package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.data.Ablage
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Kein Test, sondern ein Werkzeug: erzeugt Listen vom lokalen Testserver als PDF und die ersten Seiten als PNG.
 * Nur mit gesetztem WT_LISTEN_ZIEL, z. B.
 *   WT_LISTEN_ZIEL=/pfad WT_LISTEN="Ahnen:I1:6 Stamm:I52:5:Henry" ./gradlew :shared:desktopTest --rerun --tests '*ListenErzeugen*'
 * WT_URL, WT_BAUM, WT_USER, WT_PASS wie bei TafelBilderErzeugen.
 */
class ListenErzeugen {
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
    fun erzeugen() {
        val ziel = System.getenv("WT_LISTEN_ZIEL")?.let(::File) ?: return
        ziel.mkdirs()
        val baum = System.getenv("WT_BAUM") ?: "falkenrath"
        val client = WtClient(Speicher(), Speicher(), "wtTux/dev (Listen)").apply { baseUrl = System.getenv("WT_URL") ?: "http://127.0.0.1:8377" }
        val info = runBlocking { System.getenv("WT_USER")?.let { client.login(it, System.getenv("WT_PASS").orEmpty()) } ?: client.info() }
        val titel = info.trees.first { it.name == baum }.title
        (System.getenv("WT_LISTEN") ?: "Ahnen:I1:6").split(' ').filter(String::isNotBlank).forEach { auftrag ->
            val t = auftrag.split(':')
            val art = ListenArt.valueOf(t[0])
            // Art:Xref:Generationen[:Nummerierung] oder fuer Baumlisten Art:-:-:Schalter (kal, chrono, RELI, ort=Celle, ev=BIRT+CHR)
            val schalter = t.getOrNull(3)?.split(',').orEmpty()
            val o = ListenOptionen(generationen = t.getOrNull(2)?.toIntOrNull() ?: 6,
                nummerierung = t.getOrNull(3)?.let { runCatching { Nummerierung.valueOf(it) }.getOrNull() } ?: Nummerierung.Saragossa,
                kalender = "kal" in schalter, chronologisch = "chrono" in schalter, fakt = if ("RELI" in schalter) "RELI" else "OCCU",
                ortFilter = schalter.firstOrNull { it.startsWith("ort=") }?.substringAfter('=').orEmpty(),
                ereignisse = schalter.firstOrNull { it.startsWith("ev=") }?.substringAfter('=')?.split('+')?.toSet() ?: setOf("BIRT", "MARR", "DEAT"))
            val zeilen = runBlocking { listenZeilen(art, client, baum, titel, t[1], o) }
            val bytes = ByteArrayOutputStream().also { out -> listenPdf(zeilen, "wtTux", titel).use { it.save(out) } }.toByteArray()
            val name = "liste-" + auftrag.replace(':', '-').lowercase()
            File(ziel, "$name.pdf").writeBytes(bytes)
            Loader.loadPDF(bytes).use { d ->
                (0 until minOf(2, d.numberOfPages)).forEach { i -> ImageIO.write(PDFRenderer(d).renderImage(i, 1.6f), "png", File(ziel, "$name-s${i + 1}.png")) }
                println("$name: ${d.numberOfPages} Seiten, ${zeilen.size} Zeilen")
            }
        }
    }
}
