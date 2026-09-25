package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.api.Person
import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSBoolean
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSFloat
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.multipdf.LayerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.common.function.PDFunctionType2
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.graphics.shading.PDShadingType2
import org.apache.pdfbox.util.Matrix
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.ColorConvertOp
import java.awt.color.ColorSpace

/*
 * Stammtafel (25.09.2026, Wunsch Thomas: "Apps brauchen schoene Tafeln"): alle Nachfahren einer Person als
 * ein grosses Blatt - Ausgangsperson oben, jede Generation eine Reihe, ueber jedem Kasten Portraet oder
 * Silhouette. Ein Zeichenkern fuer Vorschau und Ausgabe: das Layout rechnet in Punkt (1/72 Zoll), daraus
 * entsteht EIN PDF-Blatt; die Vorschau im Fenster ist dieses Blatt, gedruckt wird es verkleinert auf ein
 * Blatt oder in Originalgroesse auf mehrere A4-Blaetter zum Zusammenkleben.
 */

/** Eine Person der Tafel mit ihren Kindern (aller Partnerschaften, in Familienfolge). */
class TafelPerson(val person: Person, val kinder: List<TafelPerson>)

enum class TafelStil { Pergament, Klassisch, Farbig, Schwarzweiss }

data class TafelOptionen(
    val generationen: Int = 6,
    val stil: TafelStil = TafelStil.Pergament,
    val rahmenMm: Int = 30,
    val bilder: Boolean = true,
    val titel: String = "",
)

/** Ein platzierter Kasten: Mitte waagerecht, Oberkante des Bildes, Ebene (0 = Ausgangsperson). */
class TafelPlatz(val knoten: TafelPerson, val mitteX: Float, val obenY: Float, val ebene: Int, val eltern: TafelPlatz?)

/** Masse eines Kastens in Punkt, alle aus der Rahmenbreite abgeleitet. */
class TafelMasse(val rahmen: Float, val bilder: Boolean) {
    val bild = if (bilder) rahmen * 0.78f else 0f
    val bildAbstand = if (bilder) rahmen * 0.06f else 0f
    val schriftKlein = rahmen * 0.085f
    val schriftName = rahmen * 0.12f
    val kastenH = schriftKlein * 3 * 1.3f + schriftName * 1.3f + rahmen * 0.12f
    val spalt = maxOf(rahmen * 0.12f, 6f)
    val verbinder = maxOf(rahmen * 0.36f, 18f)
    val ebeneH = bild + bildAbstand + kastenH + verbinder
    val slot = rahmen + spalt
}

class TafelLayout(val plaetze: List<TafelPlatz>, val breite: Float, val hoehe: Float, val masse: TafelMasse)

/**
 * Baumlayout nach Konturen (Art Reingold-Tilford): Geschwister-Teilbaeume ruecken so eng zusammen, wie es ihre
 * Umrisse in JEDER Reihe erlauben - ein tiefer Zweig darf unter kinderlose Geschwister reichen. Jede Person steht
 * mittig ueber ihrem ersten und letzten Kind. So wird die Tafel nicht breiter als noetig (Vorbild: 78 statt 140 cm).
 */
fun stammtafelLayout(wurzel: TafelPerson, masse: TafelMasse): TafelLayout {
    // Ein Teilbaum: Versatz jedes Kindes zur Mitte der Person, Umriss je Tiefe (linkeste und rechteste Mitte).
    class Teil(val kinderVersatz: List<Float>, val links: MutableList<Float>, val rechts: MutableList<Float>)
    val teile = HashMap<TafelPerson, Teil>()
    fun rechnen(k: TafelPerson): Teil {
        val kinder = k.kinder.map(::rechnen)
        if (kinder.isEmpty()) return Teil(emptyList(), mutableListOf(0f), mutableListOf(0f)).also { teile[k] = it }
        // Kinder von links nach rechts ansetzen; jedes so weit rechts, dass es in keiner Tiefe den bisherigen Umriss beruehrt
        val versatz = ArrayList<Float>()
        val accL = ArrayList<Float>(); val accR = ArrayList<Float>()
        kinder.forEach { t ->
            var x = 0f
            if (versatz.isNotEmpty()) {
                x = Float.NEGATIVE_INFINITY
                for (d in 0 until minOf(accR.size, t.links.size)) x = maxOf(x, accR[d] + masse.slot - t.links[d])
            }
            versatz += x
            t.links.indices.forEach { d ->
                val l = t.links[d] + x; val r = t.rechts[d] + x
                if (d < accL.size) { accL[d] = minOf(accL[d], l); accR[d] = maxOf(accR[d], r) } else { accL += l; accR += r }
            }
        }
        val mitte = (versatz.first() + versatz.last()) / 2
        val teil = Teil(versatz.map { it - mitte }, mutableListOf(0f).apply { addAll(accL.map { it - mitte }) }, mutableListOf(0f).apply { addAll(accR.map { it - mitte }) })
        teile[k] = teil
        return teil
    }
    val ganz = rechnen(wurzel)
    val links = ganz.links.min()
    val plaetze = mutableListOf<TafelPlatz>()
    fun setzen(k: TafelPerson, mitte: Float, ebene: Int, eltern: TafelPlatz?) {
        val platz = TafelPlatz(k, mitte, ebene * masse.ebeneH, ebene, eltern)
        plaetze += platz
        val t = teile.getValue(k)
        k.kinder.forEachIndexed { i, kind -> setzen(kind, mitte + t.kinderVersatz[i], ebene + 1, platz) }
    }
    // Die linkeste Kastenmitte liegt eine halbe Slotbreite vom linken Rand
    setzen(wurzel, -links + masse.slot / 2, 0, null)
    val breite = ganz.rechts.max() - links + masse.slot
    val hoehe = ganz.links.size * masse.ebeneH - masse.verbinder
    return TafelLayout(plaetze, breite, hoehe, masse)
}

/** Wer mehrfach vorkommt (Nachfahren, die untereinander geheiratet haben), bekommt auf der Tafel eine Nummer. */
fun doppelteNummern(plaetze: List<TafelPlatz>): Map<String, Int> =
    plaetze.groupBy { it.knoten.person.xref }.filter { it.value.size > 1 && it.key.isNotEmpty() }.keys.withIndex().associate { (i, x) -> x to i + 1 }

private class StilFarben(
    val hintergrundOben: Color, val hintergrundUnten: Color, val titel: Color, val linie: Color, val text: Color,
    val rahmenBreite: Float, val rund: Boolean, val grau: Boolean,
    val fuellung: (String) -> Color, val rahmen: (String) -> Color,
)

private fun farben(stil: TafelStil): StilFarben = when (stil) {
    TafelStil.Pergament -> StilFarben(Color(0xFF, 0xFF, 0xFF), Color(0xFD, 0xEE, 0xBE), Color(0x1E, 0x14, 0x0A), Color(0x3A, 0x2E, 0x22), Color(0x1E, 0x14, 0x0A),
        2.2f, true, false, { Color.WHITE }, { Color(0x1E, 0x14, 0x0A) })
    TafelStil.Klassisch -> StilFarben(Color.WHITE, Color.WHITE, Color.BLACK, Color(0x44, 0x44, 0x44), Color.BLACK,
        1f, false, false, { Color.WHITE }, { Color.BLACK })
    TafelStil.Farbig -> StilFarben(Color.WHITE, Color.WHITE, Color(0x1F, 0x3A, 0x6B), Color(0x6A, 0x74, 0x73), Color(0x24, 0x30, 0x2F),
        1.2f, true, false,
        { s -> when (s) { "M" -> Color(0xB9, 0xD0, 0xE8); "F" -> Color(0xF3, 0xC4, 0xBE); else -> Color(0xDD, 0xE2, 0xE2) } },
        { s -> when (s) { "M" -> Color(0x4F, 0x7F, 0xAE); "F" -> Color(0xC2, 0x70, 0x6A); else -> Color(0x8A, 0x94, 0x93) } })
    TafelStil.Schwarzweiss -> StilFarben(Color.WHITE, Color.WHITE, Color.BLACK, Color.BLACK, Color.BLACK,
        0.8f, false, true, { Color.WHITE }, { Color.BLACK })
}

/** Schriften der Tafel: Serifenschrift fuer Pergament und Klassisch, Titel in Schreibschrift (Great Vibes, OFL). */
private class TafelSchriften(doc: PDDocument, stil: TafelStil) {
    private val basis = Schriften(doc)
    private val serif = stil == TafelStil.Pergament || stil == TafelStil.Klassisch
    val normal: PDFont = (if (serif) basis.ladenAus(listOf(
        "C:/Windows/Fonts/times.ttf", "/usr/share/fonts/truetype/liberation/LiberationSerif-Regular.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf",
        "/usr/share/fonts/truetype/noto/NotoSerif-Regular.ttf",
    )) else null) ?: basis.normal
    val fett: PDFont = (if (serif) basis.ladenAus(listOf(
        "C:/Windows/Fonts/timesbd.ttf", "/usr/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf",
        "/usr/share/fonts/truetype/noto/NotoSerif-Bold.ttf",
    )) else null) ?: basis.fett
    val titel: PDFont = if (stil == TafelStil.Pergament) {
        runCatching { TafelSchriften::class.java.getResourceAsStream("/fonts/GreatVibes-Regular.ttf")!!.use { PDType0Font.load(doc, it) } }.getOrNull() ?: fett
    } else fett
}

private fun COSArray.zahlen(vararg z: Float) = apply { z.forEach { add(COSFloat(it)) } }

/** Senkrechter Farbverlauf ueber das ganze Blatt (Pergament). */
private fun PDPageContentStream.verlauf(b: Float, h: Float, oben: Color, unten: Color) {
    val fn = COSDictionary().apply {
        setInt(COSName.FUNCTION_TYPE, 2)
        setItem(COSName.DOMAIN, COSArray().zahlen(0f, 1f))
        setItem(COSName.C0, COSArray().zahlen(unten.red / 255f, unten.green / 255f, unten.blue / 255f))
        setItem(COSName.C1, COSArray().zahlen(oben.red / 255f, oben.green / 255f, oben.blue / 255f))
        setInt(COSName.N, 1)
    }
    val shading = PDShadingType2(COSDictionary()).apply {
        shadingType = 2
        colorSpace = PDDeviceRGB.INSTANCE
        coords = COSArray().zahlen(0f, 0f, 0f, h)
        function = PDFunctionType2(fn)
        extend = COSArray().apply { add(COSBoolean.TRUE); add(COSBoolean.TRUE) }
    }
    saveGraphicsState(); addRect(0f, 0f, b, h); clip(); shadingFill(shading); restoreGraphicsState()
}

private fun PDPageContentStream.rechteck(x: Float, y: Float, w: Float, h: Float, r: Float) {
    if (r <= 0f) { addRect(x, y, w, h); return }
    val k = 0.5523f * r
    moveTo(x + r, y); lineTo(x + w - r, y); curveTo(x + w - r + k, y, x + w, y + r - k, x + w, y + r)
    lineTo(x + w, y + h - r); curveTo(x + w, y + h - r + k, x + w - r + k, y + h, x + w - r, y + h)
    lineTo(x + r, y + h); curveTo(x + r - k, y + h, x, y + h - r + k, x, y + h - r)
    lineTo(x, y + r); curveTo(x, y + r - k, x + r - k, y, x + r, y); closePath()
}

private fun grau(b: BufferedImage): BufferedImage = ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null).filter(b, null)

/** Text in eine Breite zwingen: erst kleiner (bis 70 %), dann kuerzen. */
private fun passend(schrift: PDFont, text: String, groesse: Float, breite: Float): Pair<String, Float> {
    var g = groesse
    while (schrift.breite(text, g) > breite && g > groesse * 0.7f) g -= groesse * 0.05f
    var t = text
    while (schrift.breite(t, g) > breite && t.length > 3) t = t.dropLast(2) + "…"
    return t to g
}

/** Groesste Seitenlaenge eines PDF-Blatts (200 Zoll); groessere Tafeln werden verkleinert. */
private const val PDF_MAX = 14400f

/** Masse des Blatts in Zentimetern und die Personenzahl, fuer die Anzeige im Fenster. */
class TafelInfo(val personen: Int, val breiteCm: Int, val hoeheCm: Int)

/**
 * Die Stammtafel als PDF mit einem Blatt. [bilder] liefert je Person ihr Portraet (null = Silhouette);
 * [privat] ist der Text fuer Personen, die der Server nicht zeigt.
 */
fun stammtafelPdf(
    wurzel: TafelPerson, o: TafelOptionen, bilder: (Person) -> BufferedImage?, privat: String, fuss: String,
): Pair<PDDocument, TafelInfo> {
    val masse = TafelMasse(o.rahmenMm * 72f / 25.4f, o.bilder)
    val layout = stammtafelLayout(wurzel, masse)
    val nummern = doppelteNummern(layout.plaetze)
    val doc = PDDocument()
    val f = farben(o.stil)
    val s = TafelSchriften(doc, o.stil)

    val rand = maxOf(masse.rahmen * 0.5f, 28f)
    val titelGroesse = (masse.rahmen * 0.55f).coerceIn(22f, 72f)
    val titelBreite = if (o.titel.isBlank()) 0f else s.titel.breite(o.titel, titelGroesse)
    val titelH = if (o.titel.isBlank()) 0f else titelGroesse * 1.9f
    val fussH = 18f
    val inhaltB = maxOf(layout.breite, titelBreite)
    val b = inhaltB + 2 * rand
    val h = rand + titelH + layout.hoehe + fussH + rand
    val skala = minOf(1f, PDF_MAX / b, PDF_MAX / h)
    val page = PDPage(PDRectangle(b * skala, h * skala))
    doc.addPage(page)

    val bildCache = HashMap<String, PDImageXObject>()
    fun bildFuer(p: Person): PDImageXObject? {
        if (!o.bilder) return null
        val schluessel = if (p.isPrivate) "privat" else p.xref
        return bildCache.getOrPut(schluessel) {
            val foto = if (p.isPrivate) null else bilder(p)
            if (foto != null) JPEGFactory.createFromImage(doc, if (f.grau) grau(foto) else foto, 0.88f)
            else {
                val sil = silhouetteBild(p.sex, jahrhundert(p), 160)
                LosslessFactory.createFromImage(doc, if (f.grau) grau(sil) else sil)
            }
        }
    }

    PDPageContentStream(doc, page).use { cs ->
        if (skala < 1f) cs.transform(Matrix.getScaleInstance(skala, skala))
        if (f.hintergrundOben != f.hintergrundUnten) cs.verlauf(b, h, f.hintergrundOben, f.hintergrundUnten)
        // Oben links des Inhalts in PDF-Koordinaten; y waechst nach unten, darum: pdfY = h - y
        val x0 = rand + (inhaltB - layout.breite) / 2
        val y0 = rand + titelH
        fun py(y: Float) = h - y

        if (o.titel.isNotBlank()) {
            cs.setNonStrokingColor(f.titel)
            cs.beginText(); cs.setFont(s.titel, titelGroesse)
            cs.newLineAtOffset((b - titelBreite) / 2, py(rand + titelGroesse * 1.05f)); cs.showText(s.titel.sicher(o.titel)); cs.endText()
        }

        // Verbindungen: von der Unterkante der Eltern senkrecht auf halbe Hoehe, waagerecht ueber alle Kinder,
        // senkrecht hinunter zu jedem Kind
        cs.setStrokingColor(f.linie); cs.setLineWidth(maxOf(0.6f, masse.rahmen * 0.009f))
        layout.plaetze.groupBy { it.eltern }.forEach { (eltern, kinder) ->
            if (eltern == null) return@forEach
            val unten = eltern.obenY + masse.bild + masse.bildAbstand + masse.kastenH
            val mitte = unten + masse.verbinder / 2
            cs.moveTo(x0 + eltern.mitteX, py(y0 + unten)); cs.lineTo(x0 + eltern.mitteX, py(y0 + mitte))
            val xs = kinder.map { it.mitteX }
            cs.moveTo(x0 + minOf(xs.min(), eltern.mitteX), py(y0 + mitte)); cs.lineTo(x0 + maxOf(xs.max(), eltern.mitteX), py(y0 + mitte))
            kinder.forEach { k -> cs.moveTo(x0 + k.mitteX, py(y0 + mitte)); cs.lineTo(x0 + k.mitteX, py(y0 + k.obenY)) }
            cs.stroke()
        }

        layout.plaetze.forEach { platz ->
            val p = platz.knoten.person
            val links = x0 + platz.mitteX - masse.rahmen / 2
            val oben = y0 + platz.obenY
            // Bild mit feinem Rand
            bildFuer(p)?.let { img ->
                val bx = x0 + platz.mitteX - masse.bild / 2
                cs.drawImage(img, bx, py(oben + masse.bild), masse.bild, masse.bild)
                cs.setStrokingColor(f.linie); cs.setLineWidth(0.5f); cs.addRect(bx, py(oben + masse.bild), masse.bild, masse.bild); cs.stroke()
                nummern[p.xref]?.let { n ->
                    val r = masse.rahmen * 0.075f
                    val cx = bx + masse.bild; val cy = py(oben)
                    cs.setNonStrokingColor(Color(0xFF, 0xF3, 0x9A)); cs.setStrokingColor(f.linie); cs.setLineWidth(0.5f)
                    cs.rechteck(cx - r, cy - r, 2 * r, 2 * r, r); cs.fillAndStroke()
                    val t = n.toString(); val g = r * 1.3f
                    cs.setNonStrokingColor(f.text); cs.beginText(); cs.setFont(s.fett, g)
                    cs.newLineAtOffset(cx - s.fett.breite(t, g) / 2, cy - g * 0.35f); cs.showText(t); cs.endText()
                }
            }
            // Kasten
            val ky = oben + masse.bild + masse.bildAbstand
            val kx = links + f.rahmenBreite / 2; val kw = masse.rahmen - f.rahmenBreite
            cs.setNonStrokingColor(f.fuellung(p.sex)); cs.setStrokingColor(f.rahmen(p.sex)); cs.setLineWidth(f.rahmenBreite)
            cs.rechteck(kx, py(ky + masse.kastenH), kw, masse.kastenH, if (f.rund) masse.rahmen * 0.05f else 0f); cs.fillAndStroke()
            // Zeilen: Vorname klein, Nachname fett, Geburt, Tod
            val zeilen = buildList {
                if (p.isPrivate) add(Triple(privat, s.normal, masse.schriftKlein))
                else {
                    val vor = p.given.ifBlank { if (p.surname.isBlank()) p.name else "" }
                    add(Triple(vor, s.fett, masse.schriftKlein))
                    add(Triple(p.surname, s.fett, masse.schriftName))
                    p.birth?.date?.year?.takeIf { it > 0 }?.let { add(Triple("* $it", s.normal, masse.schriftKlein)) }
                    p.death?.date?.year?.takeIf { it > 0 }?.let { add(Triple("† $it", s.normal, masse.schriftKlein)) }
                }
            }
            val innen = masse.rahmen * 0.84f
            var y = ky + masse.rahmen * 0.06f
            cs.setNonStrokingColor(f.text)
            zeilen.forEach { (text, schrift, groesse) ->
                y += groesse * 1.3f
                if (text.isNotBlank()) {
                    val (t, g) = passend(schrift, text, groesse, innen)
                    cs.beginText(); cs.setFont(schrift, g)
                    cs.newLineAtOffset(x0 + platz.mitteX - schrift.breite(t, g) / 2, py(y - groesse * 0.25f)); cs.showText(schrift.sicher(t)); cs.endText()
                }
            }
        }

        cs.setNonStrokingColor(Color(0x66, 0x66, 0x66))
        cs.beginText(); cs.setFont(s.normal, 7f); cs.newLineAtOffset(rand, rand * 0.6f); cs.showText(s.normal.sicher(fuss)); cs.endText()
    }
    val info = TafelInfo(layout.plaetze.size, (b * skala / 72f * 2.54f).toInt(), (h * skala / 72f * 2.54f).toInt())
    return doc to info
}

/** Das Blatt verkleinert auf eine A4-Seite (hoch oder quer, was besser passt). */
fun aufEinBlatt(poster: PDDocument): PDDocument {
    val quelle = poster.getPage(0).mediaBox
    val a4 = PDRectangle.A4
    val quer = quelle.width > quelle.height
    val format = if (quer) PDRectangle(a4.height, a4.width) else a4
    val rand = 28f
    val doc = PDDocument()
    val form = LayerUtility(doc).importPageAsForm(poster, 0)
    val page = PDPage(format); doc.addPage(page)
    val f = minOf((format.width - 2 * rand) / quelle.width, (format.height - 2 * rand) / quelle.height, 1f)
    PDPageContentStream(doc, page).use { cs ->
        cs.saveGraphicsState()
        cs.transform(Matrix.getTranslateInstance((format.width - quelle.width * f) / 2, (format.height - quelle.height * f) / 2))
        cs.transform(Matrix.getScaleInstance(f, f))
        cs.drawForm(form); cs.restoreGraphicsState()
    }
    return doc
}

/**
 * Das Blatt in Originalgroesse auf A4-Seiten zum Zusammenkleben: 10 mm Rand, 10 mm Ueberlappung, jede Seite
 * mit Zeile/Spalte und Schnittmarken. Hoch- oder Querformat - was weniger Seiten braucht.
 */
fun aufA4Blaetter(poster: PDDocument): PDDocument {
    val quelle = poster.getPage(0).mediaBox
    val mm = 72f / 25.4f
    val rand = 10 * mm; val ueber = 10 * mm
    fun zahl(format: PDRectangle): Pair<Int, Int> {
        val sx = format.width - 2 * rand - ueber; val sy = format.height - 2 * rand - ueber
        return Math.ceil(((quelle.width - ueber) / sx).toDouble()).toInt().coerceAtLeast(1) to Math.ceil(((quelle.height - ueber) / sy).toDouble()).toInt().coerceAtLeast(1)
    }
    val hoch = PDRectangle.A4; val quer = PDRectangle(hoch.height, hoch.width)
    val (hs, hz) = zahl(hoch); val (qs, qz) = zahl(quer)
    val format = if (qs * qz < hs * hz) quer else hoch
    val (spalten, zeilen) = zahl(format)
    val schrittX = format.width - 2 * rand - ueber; val schrittY = format.height - 2 * rand - ueber
    val doc = PDDocument()
    val form = LayerUtility(doc).importPageAsForm(poster, 0)
    val schrift = Schriften(doc).normal
    for (z in 0 until zeilen) for (sp in 0 until spalten) {
        val page = PDPage(format); doc.addPage(page)
        PDPageContentStream(doc, page).use { cs ->
            // Ausschnitt: Spalte sp von links, Zeile z von oben
            val qx = sp * schrittX
            val qyOben = quelle.height - z * schrittY
            cs.saveGraphicsState()
            cs.addRect(rand, rand, format.width - 2 * rand, format.height - 2 * rand); cs.clip()
            cs.transform(Matrix.getTranslateInstance(rand - qx, format.height - rand - qyOben))
            cs.drawForm(form)
            cs.restoreGraphicsState()
            // Schnittmarken an den Ecken des bedruckten Bereichs, Kennung der Seite
            cs.setStrokingColor(Color(0x99, 0x99, 0x99)); cs.setLineWidth(0.4f)
            val l = 4 * mm
            listOf(rand to rand, format.width - rand to rand, rand to format.height - rand, format.width - rand to format.height - rand).forEach { (x, y) ->
                cs.moveTo(x - l, y); cs.lineTo(x + l, y); cs.moveTo(x, y - l); cs.lineTo(x, y + l)
            }
            cs.stroke()
            cs.setNonStrokingColor(Color(0x88, 0x88, 0x88))
            cs.beginText(); cs.setFont(schrift, 7f); cs.newLineAtOffset(rand, rand * 0.4f)
            cs.showText(schrift.sicher("${z + 1}/${sp + 1}  ·  ${zeilen}×${spalten}")); cs.endText()
        }
    }
    return doc
}
