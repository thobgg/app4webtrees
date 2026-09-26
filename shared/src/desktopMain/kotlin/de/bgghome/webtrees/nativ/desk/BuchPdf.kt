package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.res.*
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.awt.Color

/*
 * Seitensatz fuer Buecher (26.09.2026): A4, Serifenschrift, Kopfzeile mit Titel, Fusszeile "- 3 -", haengende
 * Nummern, Portraet rechts neben dem Eintrag, Farbbalken der Grosseltern-Linie, Verweise als Links, Inhaltsverzeichnis
 * mit Seitenzahlen (zwei Durchlaeufe: erst zaehlen, dann setzen) und PDF-Lesezeichen, Verzeichnisse ein- oder zweispaltig.
 */

/** Schriften des Buchs; Zeichen, die die Serifenschrift nicht hat (∞, ▭, ♂, ♀), kommen aus der Ersatzschrift. */
private class BuchSchriften(doc: PDDocument) {
    private val basis = Schriften(doc)
    val normal: PDFont = basis.ladenAus(listOf("C:/Windows/Fonts/times.ttf", "/usr/share/fonts/truetype/liberation/LiberationSerif-Regular.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf", "/usr/share/fonts/truetype/noto/NotoSerif-Regular.ttf")) ?: basis.normal
    val fett: PDFont = basis.ladenAus(listOf("C:/Windows/Fonts/timesbd.ttf", "/usr/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf", "/usr/share/fonts/truetype/noto/NotoSerif-Bold.ttf")) ?: basis.fett
    val kursiv: PDFont = basis.ladenAus(listOf("C:/Windows/Fonts/timesi.ttf", "/usr/share/fonts/truetype/liberation/LiberationSerif-Italic.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Italic.ttf", "/usr/share/fonts/truetype/noto/NotoSerif-Italic.ttf")) ?: normal
    val ersatz: PDFont = basis.ladenAus(listOf("C:/Windows/Fonts/seguisym.ttf", "C:/Windows/Fonts/segoeui.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf")) ?: basis.normal
    private val kann = HashMap<Pair<PDFont, Int>, Boolean>()
    fun kann(f: PDFont, cp: Int) = kann.getOrPut(f to cp) { runCatching { f.encode(String(Character.toChars(cp))) }.isSuccess }
    fun fuer(stil: Stil) = when (stil) { Stil.Fett -> fett; Stil.Kursiv -> kursiv; else -> normal }

    /** Text in Stuecke je Schrift zerlegen (Ersatzschrift fuer fehlende Zeichen). */
    fun stuecke(text: String, f: PDFont): List<Pair<String, PDFont>> {
        val aus = mutableListOf<Pair<String, PDFont>>()
        val sb = StringBuilder(); var aktuell: PDFont? = null
        text.codePoints().forEach { cp ->
            val g = if (kann(f, cp)) f else if (kann(ersatz, cp)) ersatz else f
            if (g !== aktuell && sb.isNotEmpty()) { aus += sb.toString() to aktuell!!; sb.clear() }
            aktuell = g
            if (g === f && !kann(f, cp)) sb.append('?') else sb.appendCodePoint(cp)
        }
        if (sb.isNotEmpty()) aus += sb.toString() to aktuell!!
        return aus
    }
    fun breite(text: String, f: PDFont, g: Float) = stuecke(text, f).sumOf { (t, s) -> (s.getStringWidth(t) / 1000f * g).toDouble() }.toFloat()
}

private class Wort(val text: String, val stil: Stil, val ziel: String?, val leerDavor: Boolean)

private class LinkZiel(val seite: Int, val rect: PDRectangle, val ziel: String)

/** Ein Buch als PDF. Zwei Durchlaeufe: der erste ermittelt die Seiten der Ueberschriften fuer das Inhaltsverzeichnis. */
fun buchPdf(buch: Buch): PDDocument {
    val probe = BuchSatz(buch, null).setzen()
    val seiten = probe.second
    probe.first.close()
    return BuchSatz(buch, seiten).setzen().first
}

private class BuchSatz(val buch: Buch, val tocSeiten: Map<String, Int>?) {
    val doc = PDDocument()
    val s = BuchSchriften(doc)
    val format = PDRectangle.A4
    val links = 68f; val rechts = 58f; val oben = 72f; val unten = 70f
    val breite = format.width - links - rechts
    val grund = 10.5f
    val zeilenH = grund * 1.38f
    var cs: PDPageContentStream? = null
    var seite = 0
    var y = 0f              // von oben gemessen
    var leer = true
    var mitKopf = false
    val ueberschriftSeite = LinkedHashMap<String, Int>()
    val anker = HashMap<String, Pair<Int, Float>>()
    val verweise = mutableListOf<LinkZiel>()
    val lesezeichen = mutableListOf<Triple<String, Int, Float>>()

    fun py(v: Float) = format.height - v

    fun neueSeite(kopf: Boolean = true) {
        cs?.close()
        val page = PDPage(format); doc.addPage(page); seite++
        cs = PDPageContentStream(doc, page)
        y = oben; leer = true; mitKopf = kopf
        if (kopf) {
            val c = cs!!
            c.setNonStrokingColor(Color(0x44, 0x44, 0x44))
            text(c, buch.kopfzeile, s.normal, 8.5f, links + breite / 2 - s.breite(buch.kopfzeile, s.normal, 8.5f) / 2, 48f)
            c.setStrokingColor(Color(0x88, 0x88, 0x88)); c.setLineWidth(0.5f)
            c.moveTo(links, py(54f)); c.lineTo(links + breite, py(54f)); c.stroke()
            c.moveTo(links, py(format.height - 52f)); c.lineTo(links + breite, py(format.height - 52f)); c.stroke()
            val nr = "- $seite -"
            text(c, nr, s.normal, 9f, links + breite / 2 - s.breite(nr, s.normal, 9f) / 2, format.height - 38f)
            c.setNonStrokingColor(Color.BLACK)
        }
    }

    fun platz(h: Float) { if (cs == null || y + h > format.height - unten) neueSeite() }

    /** Text an Position (y = Grundlinie von oben), mit Ersatzschrift. */
    fun text(c: PDPageContentStream, t: String, f: PDFont, g: Float, x: Float, grundlinie: Float): Float {
        var xx = x
        s.stuecke(t, f).forEach { (teil, schrift) ->
            c.beginText(); c.setFont(schrift, g); c.newLineAtOffset(xx, py(grundlinie)); c.showText(teil); c.endText()
            xx += schrift.getStringWidth(teil) / 1000f * g
        }
        return xx - x
    }

    fun setzen(): Pair<PDDocument, Map<String, Int>> {
        buch.bloecke.forEach { b ->
            when (b) {
                is Titelblatt -> titelblatt(b)
                is Inhaltsverzeichnis -> inhalt()
                is Ueberschrift -> ueberschrift(b.text, b.id, b.neueSeite)
                is Absatz -> absatz(b)
                is Verzeichnis -> verzeichnis(b)
            }
        }
        cs?.close()
        // Verweise erst jetzt: die Ziele koennen weiter hinten liegen
        verweise.forEach { v ->
            val (zs, zy) = anker[v.ziel] ?: return@forEach
            val link = PDAnnotationLink().apply {
                rectangle = v.rect
                borderStyle = PDBorderStyleDictionary().apply { width = 0f }
                action = PDActionGoTo().apply { destination = PDPageXYZDestination().apply { page = doc.getPage(zs - 1); top = py(zy - 14f).toInt() } }
            }
            doc.getPage(v.seite - 1).annotations.add(link)
        }
        // Lesezeichen fuer die Kapitel
        val outline = PDDocumentOutline()
        lesezeichen.forEach { (t, sNr, sy) ->
            outline.addLast(PDOutlineItem().apply { title = t; destination = PDPageXYZDestination().apply { page = doc.getPage(sNr - 1); top = py(sy - 20f).toInt() } })
        }
        doc.documentCatalog.documentOutline = outline
        doc.documentInformation.title = buch.titel
        return doc to ueberschriftSeite
    }

    fun titelblatt(b: Titelblatt) {
        neueSeite(kopf = false)
        val c = cs!!
        var ty = format.height * 0.36f
        b.bild?.let { bild ->
            val w = 150f; val h = w * bild.height / bild.width
            val img = JPEGFactory.createFromImage(doc, bild, 0.9f)
            c.drawImage(img, format.width / 2 - w / 2, py(ty - 30f), w, h)
            c.setStrokingColor(Color(0x55, 0x55, 0x55)); c.setLineWidth(0.6f); c.addRect(format.width / 2 - w / 2, py(ty - 30f), w, h); c.stroke()
            ty += 50f
        }
        val g = 30f
        var t = b.titel
        var gg = g
        while (s.breite(t, s.fett, gg) > breite && gg > 16f) gg -= 1f
        text(c, t, s.fett, gg, format.width / 2 - s.breite(t, s.fett, gg) / 2, ty + gg)
        if (b.untertitel.isNotBlank()) text(c, b.untertitel, s.fett, 14f, format.width / 2 - s.breite(b.untertitel, s.fett, 14f) / 2, ty + gg + 28f)
        text(c, b.zeile, s.normal, 10f, format.width / 2 - s.breite(b.zeile, s.normal, 10f) / 2, format.height * 0.8f)
        text(c, buch.fuss, s.normal, 8f, format.width / 2 - s.breite(buch.fuss, s.normal, 8f) / 2, format.height * 0.8f + 16f)
        cs!!.close(); cs = null
    }

    fun ziele(): List<Pair<String, String>> = buch.bloecke.mapNotNull { b ->
        when (b) { is Ueberschrift -> b.text to b.id; is Verzeichnis -> b.titel to b.id; else -> null }
    }

    fun inhalt() {
        neueSeite()
        val c = cs!!
        text(c, Texte.t(Res.string.desk_book_contents), s.fett, 15f, links, y + 15f)
        y += 34f
        ziele().forEach { (t, id) ->
            platz(zeilenH)
            val c2 = cs!!
            val nr = tocSeiten?.get(id)?.toString() ?: "00"
            val tb = text(c2, t, s.normal, grund, links, y + grund)
            val nb = s.breite(nr, s.normal, grund)
            text(c2, nr, s.normal, grund, links + breite - nb, y + grund)
            // Punktlinie
            val punkt = s.breite(".", s.normal, grund)
            var px = links + tb + 4f
            val sb = StringBuilder()
            while (px + punkt < links + breite - nb - 4f) { sb.append('.'); px += punkt }
            text(c2, sb.toString(), s.normal, grund, links + tb + 4f, y + grund)
            verweiseFuerZiel(id, links, y, breite, zeilenH)
            y += zeilenH
        }
        cs!!.close(); cs = null
    }

    fun verweiseFuerZiel(ziel: String, x: Float, oy: Float, w: Float, h: Float) {
        verweise += LinkZiel(seite, PDRectangle(x, py(oy + h), w, h), ziel)
    }

    fun ueberschrift(t: String, id: String, neu: Boolean) {
        if (neu || cs == null) { if (cs == null || !leer) neueSeite() } else { platz(60f); y += 14f }
        val c = cs!!
        y += 6f
        text(c, t, s.fett, 15f, links, y + 15f)
        anker[id] = seite to y
        ueberschriftSeite[id] = seite
        lesezeichen += Triple(t, seite, y)
        y += 30f
        leer = false
    }

    fun absatz(a: Absatz) {
        if (a.abstandVor) y += 5f
        val marke = a.marke
        val einzug = a.einzug * 18f
        val markeB = if (marke != null) 34f else 0f
        val x0 = links + einzug + markeB
        val bildW = if (a.bild != null) 56f else 0f
        val bildH = a.bild?.let { bildW * it.height / it.width } ?: 0f
        // Woerter mit Stil
        val woerter = mutableListOf<Wort>()
        var leerOffen = false   // endete das vorige Stueck mit einem Leerzeichen?
        a.laeufe.forEach { l ->
            l.text.replace('\n', ' ').split(' ').forEachIndexed { k, w ->
                if (w.isNotEmpty()) woerter += Wort(w, l.stil, l.ziel, k > 0 || leerOffen)
            }
            leerOffen = l.text.endsWith(" ")
        }
        // Mindestens die erste Zeile (mit Bild: das Bild) muss auf die Seite
        platz(maxOf(zeilenH * 2, bildH + 4f))
        val start = y; val startSeite = seite
        if (a.anker != null) anker[a.anker] = seite to y
        val c0 = cs!!
        a.bild?.let { b ->
            val img = JPEGFactory.createFromImage(doc, b, 0.88f)
            c0.drawImage(img, links + breite - bildW, py(y + bildH), bildW, bildH)
            c0.setStrokingColor(Color(0x88, 0x88, 0x88)); c0.setLineWidth(0.4f); c0.addRect(links + breite - bildW, py(y + bildH), bildW, bildH); c0.stroke()
        }
        if (marke != null) { c0.setNonStrokingColor(Color.BLACK); text(c0, marke, s.fett, grund, links + einzug, y + grund) }
        val bildEnde = y + bildH + 4f
        var i = 0
        var balkenStart = y
        while (i < woerter.size) {
            if (y + zeilenH > format.height - unten) {
                a.farbe?.let { balken(it, balkenStart, y) }
                neueSeite(); balkenStart = y
            }
            val maxW = (links + breite) - x0 - (if (seite == startSeite && y < bildEnde) bildW + 10f else 0f) - (if (i > 0) 0f else 0f)
            // Zeile fuellen
            val zeile = mutableListOf<Wort>()
            var w = 0f
            while (i < woerter.size) {
                val wo = woerter[i]
                val lw = s.breite(wo.text, s.fuer(wo.stil), grund) + (if (zeile.isNotEmpty() && wo.leerDavor) s.breite(" ", s.normal, grund) else 0f)
                if (zeile.isNotEmpty() && w + lw > maxW) break
                zeile += wo; w += lw; i++
            }
            var x = x0 + (if (y != start || seite != startSeite) 0f else 0f)
            val c = cs!!
            zeile.forEachIndexed { k, wo ->
                if (k > 0 && wo.leerDavor) x += s.breite(" ", s.normal, grund)
                val f = s.fuer(wo.stil)
                c.setNonStrokingColor(if (wo.ziel != null) Color(0x1F, 0x3A, 0x8A) else Color.BLACK)
                val wb = text(c, wo.text, f, grund, x, y + grund)
                wo.ziel?.let { verweise += LinkZiel(seite, PDRectangle(x, py(y + zeilenH), wb, zeilenH), it) }
                x += wb
            }
            y += zeilenH
        }
        if (seite == startSeite && y < bildEnde) y = bildEnde
        a.farbe?.let { balken(it, balkenStart, y) }
        cs!!.setNonStrokingColor(Color.BLACK)
        leer = false
    }

    fun balken(farbe: Color, von: Float, bis: Float) {
        val c = cs!!
        c.setNonStrokingColor(farbe); c.addRect(links - 9f, py(bis - 2f), 3.5f, bis - von - 2f); c.fill(); c.setNonStrokingColor(Color.BLACK)
    }

    fun verzeichnis(v: Verzeichnis) {
        if (cs == null || !leer) neueSeite()
        val c = cs!!
        text(c, v.titel, s.fett, 15f, links, y + 15f)
        anker[v.id] = seite to y
        ueberschriftSeite[v.id] = seite
        lesezeichen += Triple(v.titel, seite, y)
        y += 30f
        val spaltenAbstand = 22f
        val sw = (breite - (v.spalten - 1) * spaltenAbstand) / v.spalten
        var spalte = 0
        val oberkante = y
        val g = grund * 0.92f
        val zh = g * 1.35f
        fun naechste() {
            spalte++
            if (spalte >= v.spalten) { neueSeite(); spalte = 0; oberkanteSeite = oben }
            y = oberkanteSeite
        }
        oberkanteSeite = oberkante
        fun zeile(t: String, f: PDFont, einr: Float, rechtsText: String?) {
            if (y + zh > format.height - unten) naechste()
            val x = links + spalte * (sw + spaltenAbstand) + einr
            val cc = cs!!
            val verfuegbar = sw - einr
            if (rechtsText == null) { text(cc, t, f, g, x, y + g); y += zh; return }
            val rb = s.breite(rechtsText, s.normal, g)
            val tb = s.breite(t, f, g)
            if (tb + rb + 12f <= verfuegbar) {
                text(cc, t, f, g, x, y + g)
                val punkt = s.breite(".", s.normal, g)
                var px = x + tb + 3f; val sb = StringBuilder()
                while (px + punkt < x + verfuegbar - rb - 3f) { sb.append('.'); px += punkt }
                text(cc, sb.toString(), s.normal, g, x + tb + 3f, y + g)
                text(cc, rechtsText, s.normal, g, x + verfuegbar - rb, y + g)
                y += zh
            } else {
                // Nummern zu lang: umbrechen, rechtsbuendig in Folgezeilen
                text(cc, t, f, g, x, y + g); y += zh
                var rest: String = rechtsText
                while (rest.isNotEmpty()) {
                    var teil = rest
                    while (s.breite(teil, s.normal, g) > verfuegbar - 12f && teil.contains(", ")) teil = teil.substringBeforeLast(", ")
                    if (y + zh > format.height - unten) naechste()
                    text(cs!!, teil, s.normal, g, links + spalte * (sw + spaltenAbstand) + einr + verfuegbar - s.breite(teil, s.normal, g), y + g)
                    y += zh
                    rest = rest.removePrefix(teil).removePrefix(", ")
                }
            }
        }
        v.gruppen.forEach { (kopf, zeilen) ->
            if (kopf.isNotBlank()) { if (y + zh * 3 > format.height - unten) naechste(); y += 3f; zeile(kopf, s.fett, 0f, null) }
            zeilen.forEach { (t, nummern) -> zeile(t, s.normal, if (kopf.isNotBlank()) 10f else 0f, nummernText(nummern)) }
        }
        y = format.height - unten   // Verzeichnis endet die Seite
        leer = false
    }

    var oberkanteSeite = 0f
}
