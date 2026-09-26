package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.data.Ablage
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Kein Test, sondern ein Werkzeug fuer die README-Bilder: erzeugt Tafeln vom lokalen Testserver (testsite/README.md)
 * als PDF und PNG. Laeuft nur mit gesetztem WT_TAFELBILDER (Zielordner), sonst sofort fertig:
 *
 *   WT_TAFELBILDER=/pfad WT_TAFELN="Ahnen:I60:5:Pergament Stamm:I3:5:Farbig:partner,orte" ./gradlew :shared:desktopTest --tests '*TafelBilderErzeugen*'
 *
 * WT_URL (Vorgabe http://127.0.0.1:8377) und WT_BAUM (Vorgabe medici) waehlen Server und Baum, WT_USER und WT_PASS
 * melden an (ohne Anmeldung tragen die Portraets das Wasserzeichen fuer Gaeste).
 */
class TafelBilderErzeugen {
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
        val ziel = System.getenv("WT_TAFELBILDER")?.let(::File) ?: return
        ziel.mkdirs()
        val baumName = System.getenv("WT_BAUM") ?: "medici"
        val client = WtClient(Speicher(), Speicher(), "wtTux/dev (Tafelbilder)").apply { baseUrl = System.getenv("WT_URL") ?: "http://127.0.0.1:8377" }
        val bilder = mutableMapOf<String, BufferedImage?>()
        fun bild(p: Person): BufferedImage? = p.thumb?.let { url ->
            bilder.getOrPut(url) {
                runCatching {
                    client.http.newCall(Request.Builder().url(url).build()).execute().use { r -> r.body?.byteStream()?.use { ImageIO.read(it) } }
                }.getOrNull()?.let { b -> BufferedImage(b.width, b.height, BufferedImage.TYPE_INT_RGB).also { it.createGraphics().apply { color = java.awt.Color.WHITE; fillRect(0, 0, b.width, b.height); drawImage(b, 0, 0, null); dispose() } } }
            }
        }
        // Angemeldet: webtrees legt fuer Gaeste ein Wasserzeichen auf die Vorschaubilder
        val info = runBlocking {
            System.getenv("WT_USER")?.let { client.login(it, System.getenv("WT_PASS").orEmpty()) } ?: client.info()
        }
        val baumTitel = info.trees.first { it.name == baumName }.title
        // Auftrag: Art:Xref:Generationen:Gestaltung[:Schalter], Schalter z. B. "orte,voll,partner,oben,namen,nach4"
        (System.getenv("WT_TAFELN") ?: "Ahnen:I53:5:Pergament").split(' ').filter(String::isNotBlank).forEach { auftrag ->
            val teile = auftrag.split(':')
            val (artName, xref, gen, stilName) = teile
            val schalter = teile.getOrNull(4)?.split(',').orEmpty().toSet()
            val art = TafelArt.valueOf(artName)
            val o0 = TafelOptionen(
                generationen = gen.toInt(), stil = TafelStil.valueOf(stilName), orte = "orte" in schalter, volleDaten = "voll" in schalter,
                partner = "partner" in schalter || (art in setOf(TafelArt.Stammlinie, TafelArt.Mutterstamm, TafelArt.Aeltester) && "allein" !in schalter),
                ausgangOben = "oben" in schalter, waagerecht = "quer" in schalter, namenstraeger = "namen" in schalter, nummern = "ohnenr" !in schalter,
                nachfahren = schalter.firstOrNull { it.startsWith("nach") }?.drop(4)?.toInt() ?: 3,
            )
            val daten = runBlocking { tafelDatenLaden(client, baumName, xref, art, if (art == TafelArt.Stamm) maxGen(art) else o0.generationen) }
            val name0 = daten.ahnen[1L]?.person?.name ?: daten.nachfahren?.person?.name.orEmpty()
            val o = o0.copy(titel = tafelTitel(art, name0))
            val (doc, groesse) = tafelErzeugen(art, daten, o, ::bild, "Privat", fusszeile("wtTux", baumTitel))!!
            // Schalter "blatt": zusaetzlich der Druckweg "auf ein Blatt" (A4), um die Uebernahme der Schriften zu pruefen
            val blatt = if ("blatt" in schalter) ByteArrayOutputStream().also { out -> aufEinBlatt(doc).use { it.save(out) } }.toByteArray() else null
            val bytes = ByteArrayOutputStream().also { out -> doc.use { it.save(out) } }.toByteArray()
            val name = "tafel-${art.name.lowercase()}-$xref-$gen-${stilName.lowercase()}" + (teile.getOrNull(4)?.let { "-" + it.replace(',', '-') } ?: "")
            File(ziel, "$name.pdf").writeBytes(bytes)
            Loader.loadPDF(bytes).use { d ->
                val box = d.getPage(0).mediaBox
                // lange Seite etwa 3000 Pixel; bei Seiten die ersten beiden
                (0 until minOf(2, d.numberOfPages)).forEach { i ->
                    ImageIO.write(PDFRenderer(d).renderImage(i, 3000f / maxOf(box.width, box.height)), "png", File(ziel, if (i == 0) "$name.png" else "$name-s${i + 1}.png"))
                }
            }
            blatt?.let { b -> Loader.loadPDF(b).use { d -> ImageIO.write(PDFRenderer(d).renderImage(0, 2f), "png", File(ziel, "$name-a4.png")) } }
            println("$name: ${groesse.personen} Personen, ${groesse.breiteCm} x ${groesse.hoeheCm} cm, ${groesse.seiten} Seiten")
        }
    }
}
