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
                partnerKasten = "kasten" in schalter, kinderlos = "kinderlos" in schalter,
                partner = "partner" in schalter || "kasten" in schalter || (art in setOf(TafelArt.Stammlinie, TafelArt.Mutterstamm, TafelArt.Aeltester) && "allein" !in schalter),
                ausgangOben = "oben" in schalter, waagerecht = "quer" in schalter, bilder = "ohnebild" !in schalter, gitter = "gitter" in schalter, verzeichnis = "verz" in schalter, kurven = "kurven" in schalter, geschwister = if ("geschwalle" in schalter) 2 else if ("geschw" in schalter) 1 else 0, namenstraeger = "namen" in schalter, nummern = "ohnenr" !in schalter,
                nachfahren = schalter.firstOrNull { it.startsWith("nach") }?.drop(4)?.toInt() ?: 3,
                rahmenMm = schalter.firstOrNull { it.startsWith("rahmen") }?.drop(6)?.toInt() ?: 30,
                // Titelblock: "legende", "unter=Familie_Falkenrath", "von=Anna_Falkenrath" (Unterstrich = Leerzeichen)
                legende = "legende" in schalter,
                // Kastenform: "form=Oval", "schatten", "fotolinks"
                form = schalter.firstOrNull { it.startsWith("form=") }?.drop(5)?.let { KastenForm.valueOf(it) } ?: KastenForm.Stil,
                schatten = "schatten" in schalter, fotoLinks = "fotolinks" in schalter,
                // "buendig1"/"buendig2", "geschwungen", "lebende"
                buendig = schalter.firstOrNull { it.startsWith("buendig") }?.drop(7)?.toInt() ?: 0,
                geschwungen = "geschwungen" in schalter, lebendeNurNamen = "lebende" in schalter,
                // Kasteninhalt: "datum1"/"datum2", "ort0"/"ort2", "ersatz", "alter", "beruf", "rufname"
                datumsArt = schalter.firstOrNull { it.startsWith("datum") }?.drop(5)?.toInt() ?: 0,
                ortTeile = schalter.firstOrNull { it.matches(Regex("ort\\d")) }?.drop(3)?.toInt() ?: 1,
                mehrHinweise = "mehr" in schalter, zeitNachfahren = "zeitnach" in schalter,
                // Filter: "nurpartner", "frueh5", "aus=I60"
                nurMitPartner = "nurpartner" in schalter,
                mindestalter = schalter.firstOrNull { it.startsWith("frueh") }?.drop(5)?.toInt() ?: 0,
                ausgeblendet = schalter.filter { it.startsWith("aus=") }.map { it.drop(4) }.toSet(),
                nummernArt = schalter.firstOrNull { it.matches(Regex("nr\\d")) }?.drop(2)?.toInt() ?: 0,
                ersatz = "ersatz" in schalter, alter = "alter" in schalter, beruf = "beruf" in schalter, rufname = "rufname" in schalter,
                // Hintergrund und Rahmen: "hg=Papier", "hgbild" (WT_HGBILD), "schmuck=Ranken"
                hintergrund = schalter.firstOrNull { it.startsWith("hg=") }?.drop(3)?.let { TafelHintergrund.valueOf(it) } ?: TafelHintergrund.Stil,
                hintergrundBild = System.getenv("WT_HGBILD").orEmpty(),
                schmuck = schalter.firstOrNull { it.startsWith("schmuck=") }?.drop(8)?.let { Schmuckrahmen.valueOf(it) } ?: Schmuckrahmen.Keiner,
                untertitel = schalter.firstOrNull { it.startsWith("unter=") }?.drop(6)?.replace('_', ' ').orEmpty(),
                ersteller = schalter.firstOrNull { it.startsWith("von=") }?.drop(4)?.replace('_', ' ').orEmpty(),
                ohneStamm = schalter.filter { it.startsWith("ohne=") }.map { it.drop(5) }.toSet(),
                jeSeite = schalter.firstOrNull { it.startsWith("je") }?.drop(2)?.toInt() ?: 3, uebersicht = "ohneuebersicht" !in schalter,
                // Farben: "linie"/"zweig" als Schema, "regel=Feld/enthaelt|gleich/Text/Farbe", "markiert=I8/4"
                farbe = if ("linie" in schalter) FarbSchema.Linie else if ("zweig" in schalter) FarbSchema.Zweig else FarbSchema.Geschlecht,
                regeln = schalter.filter { it.startsWith("regel=") }.map { r -> r.drop(6).split('/').let { (fe, v, t, c) -> FarbRegel(RegelFeld.valueOf(fe), v == "enthaelt", t, c.toInt()) } },
                zweige = schalter.filter { it.startsWith("markiert=") }.associate { m -> m.drop(9).split('/').let { (x, c) -> x to c.toInt() } },
            )
            // Schalter "ehe2": beim Paar die zweite Familie der Person
            val ehe = schalter.firstOrNull { it.startsWith("ehe") }?.drop(3)?.toInt()?.minus(1) ?: 0
            val daten = runBlocking { tafelDatenLaden(client, baumName, xref, art, if (art == TafelArt.Stamm || art == TafelArt.StammSeiten || art == TafelArt.Cousins) maxGen(art) else o0.generationen, o0.geschwister, ehe, if (art == TafelArt.Verwandt) o0.nachfahren else 0, schalter.firstOrNull { it.startsWith("mit=") }?.drop(4)) }
            val name0 = daten.ahnen[1L]?.person?.name ?: daten.nachfahren?.person?.name.orEmpty()
            val o = daten.weg?.let { w -> o0.copy(titel = "${w.personA.name} und ${w.personB.name}", untertitel = wegeText(daten.wege)) }
                ?: o0.copy(titel = tafelTitel(art, name0, daten.partnerNamen.getOrNull(daten.paarFamilie).orEmpty()))
            // Schalter "karten": Karteikarten mit den Daten aller geladenen Personen
            val details = if ("karten" in schalter) runBlocking {
                kartenLaden(client, baumName, (daten.ahnen.values.map { it.person.xref } + (daten.nachfahren?.let { n -> generateSequence(listOf(n)) { e -> e.flatMap { it.families.flatMap { f -> f.children } }.takeIf { it.isNotEmpty() } }.flatten().map { it.person.xref }.toList() } ?: emptyList())))
            } else null
            val (doc, groesse) = tafelErzeugen(art, daten, o, ::bild, "Privat", fusszeile("wtTux", baumTitel), details)!!
            // Schalter "blatt": zusaetzlich der Druckweg "auf ein Blatt" (A4), um die Uebernahme der Schriften zu pruefen
            val blatt = if ("blatt" in schalter) ByteArrayOutputStream().also { out -> aufEinBlatt(doc).use { it.save(out) } }.toByteArray() else null
            val bytes = ByteArrayOutputStream().also { out -> doc.use { it.save(out) } }.toByteArray()
            val name = "tafel-${art.name.lowercase()}-$xref-$gen-${stilName.lowercase()}" + (teile.getOrNull(4)?.let { "-" + it.replace(',', '-').replace('/', '_').replace("=", "") } ?: "")
            File(ziel, "$name.pdf").writeBytes(bytes)
            // Druckweg "auf ein Blatt" (mit angehaengten Verzeichnisseiten) zur Kontrolle
            if ("blatt" in schalter) ByteArrayOutputStream().also { out -> aufEinBlatt(Loader.loadPDF(bytes)).use { it.save(out) } }.toByteArray().let { File(ziel, "$name-blatt.pdf").writeBytes(it) }
            Loader.loadPDF(bytes).use { d ->
                val box = d.getPage(0).mediaBox
                // lange Seite etwa 3000 Pixel; bei Seiten die ersten beiden
                (0 until minOf(2, d.numberOfPages)).forEach { i ->
                    ImageIO.write(PDFRenderer(d).renderImage(i, 3000f / maxOf(box.width, box.height)), "png", File(ziel, if (i == 0) "$name.png" else "$name-s${i + 1}.png"))
                }
            }
            // Grossdruck: "kachel=O" (Original), "kachel=B150" (Breite cm), "kachel=3x2" (Blaetter); "rolle=91.4" (eingepasst), "rolle=61fix"
            schalter.firstOrNull { it.startsWith("kachel=") }?.drop(7)?.let { k ->
                val g = when { k == "O" -> DruckGroesse.Original; k.startsWith("B") -> DruckGroesse.Breite(k.drop(1).toFloat()); else -> k.split('x').let { (a, b) -> DruckGroesse.Blaetter(a.toInt(), b.toInt()) } }
                val kd = aufBlaetter(Loader.loadPDF(bytes), g, groesse.bereich)
                val kb = ByteArrayOutputStream().also { out -> kd.use { it.save(out) } }.toByteArray()
                File(ziel, "$name-kacheln.pdf").writeBytes(kb)
                Loader.loadPDF(kb).use { d -> (0 until minOf(3, d.numberOfPages)).forEach { i -> ImageIO.write(PDFRenderer(d).renderImage(i, 1.5f), "png", File(ziel, "$name-kachel${i + 1}.png")) }
                    println("$name: ${d.numberOfPages} Seiten Kacheldruck") }
            }
            schalter.firstOrNull { it.startsWith("rolle=") }?.drop(6)?.let { r ->
                val fix = r.endsWith("fix")
                val s0 = Loader.loadPDF(bytes).use { it.getPage(0).let { p -> druckBereich(p, groesse.bereich).let { b -> Triple(b.b, b.h, p.userUnit) } } }
                val plan = rollenPlan(s0.first, s0.second, s0.third, r.removeSuffix("fix").toFloat(), o.let { DruckGroesse.Original }, !fix)
                val rb = ByteArrayOutputStream().also { out -> aufRolle(Loader.loadPDF(bytes), plan, groesse.bereich).use { it.save(out) } }.toByteArray()
                File(ziel, "$name-rolle.pdf").writeBytes(rb)
                Loader.loadPDF(rb).use { d -> val p = d.getPage(0)
                    ImageIO.write(PDFRenderer(d).renderImage(0, 1500f / maxOf(p.mediaBox.width, p.mediaBox.height)), "png", File(ziel, "$name-rolle.png"))
                    println("$name: Rolle ${d.numberOfPages} Bahn(en), Seite ${p.mediaBox.width * p.userUnit / 72 * 2.54} x ${p.mediaBox.height * p.userUnit / 72 * 2.54} cm, UserUnit ${p.userUnit}") }
            }
            blatt?.let { b -> Loader.loadPDF(b).use { d -> ImageIO.write(PDFRenderer(d).renderImage(0, 2f), "png", File(ziel, "$name-a4.png")) } }
            println("$name: ${groesse.personen} Personen, ${groesse.breiteCm} x ${groesse.hoeheCm} cm, ${groesse.seiten} Seiten")
        }
    }
}
