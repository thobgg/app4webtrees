package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.res.*
import org.apache.pdfbox.multipdf.LayerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject
import org.apache.pdfbox.util.Matrix
import java.awt.Color
import kotlin.math.ceil

/*
 * Grossdruck (B5, 28.09.2026): die Tafel (ohne ihren leeren Aussenrand, [Bereich]) in waehlbarer Groesse auf A4-Blaetter zum Zusammenkleben (mit Montageplan)
 * oder als PDF fuer den Plotter auf Rollenbreite. Die Tafel liegt als Vektor-PDF vor; jede Groesse ist nur ein
 * anderer Massstab derselben Seite. Poster ueber 5,08 m tragen eine UserUnit (Stammtafel.kt: PDF_MAX) - hier zaehlt
 * immer das echte Mass: Einheiten der Seite mal UserUnit.
 */

/** Ausschnitt der Tafelseite in ihren Einheiten, y von unten (wie PDF). */
class Bereich(val x: Float, val y: Float, val b: Float, val h: Float)

/** Der Bereich, der gedruckt wird: [bereich] oder die ganze Seite. */
internal fun druckBereich(seite: PDPage, bereich: Bereich?) = bereich ?: Bereich(0f, 0f, seite.mediaBox.width, seite.mediaBox.height)

/** Wie gross gedruckt wird: Originalgroesse, Zielbreite oder so gross, wie es auf Spalten x Zeilen A4-Blaetter passt. */
sealed interface DruckGroesse {
    data object Original : DruckGroesse
    data class Breite(val cm: Float) : DruckGroesse
    data class Blaetter(val spalten: Int, val zeilen: Int) : DruckGroesse
}

private const val MM = 72f / 25.4f
private const val KACHEL_RAND = 10 * MM
private const val UEBERLAPPUNG = 10 * MM
private const val ROLLEN_RAND = 10 * MM
private const val BAHN_UEBERLAPPUNG = 20 * MM

private fun cm(pt: Float) = Math.round(pt / 72f * 2.54f)

/** Zahl mit [stellen] Nachkommastellen und dem Dezimalzeichen der Programmsprache (0,33 bzw. 0.33); ",0" faellt weg. */
internal fun dezimal(x: Float, stellen: Int): String =
    String.format(java.util.Locale.ROOT, "%.${stellen}f", x).replace(".", Texte.t(Res.string.desk_decimal_point)).let { t ->
        if (stellen == 1) t.removeSuffix(Texte.t(Res.string.desk_decimal_point) + "0") else t
    }

/** Kacheldruck: Blattformat, Raster, Massstab (Einheit der Tafelseite -> Punkt auf Papier), Endmass in Punkt. */
class KachelPlan(val format: PDRectangle, val spalten: Int, val zeilen: Int, val massstab: Float, val endB: Float, val endH: Float) {
    val schrittX get() = format.width - 2 * KACHEL_RAND - UEBERLAPPUNG
    val schrittY get() = format.height - 2 * KACHEL_RAND - UEBERLAPPUNG
    val endBCm get() = cm(endB)
    val endHCm get() = cm(endH)
}

/**
 * [b] x [h]: die Tafelseite in ihren Einheiten, [einheit]: Punkt je Einheit. Hoch- oder Querformat - was weniger
 * Blaetter braucht; bei vorgegebener Blattzahl das, was die Tafel groesser macht.
 */
fun kachelPlan(b: Float, h: Float, einheit: Float, groesse: DruckGroesse): KachelPlan {
    val hoch = PDRectangle.A4
    fun plan(f: PDRectangle): KachelPlan {
        val sx = f.width - 2 * KACHEL_RAND - UEBERLAPPUNG; val sy = f.height - 2 * KACHEL_RAND - UEBERLAPPUNG
        val m = when (groesse) {
            DruckGroesse.Original -> einheit
            is DruckGroesse.Breite -> groesse.cm * 72f / 2.54f / b
            is DruckGroesse.Blaetter -> minOf((groesse.spalten * sx + UEBERLAPPUNG) / b, (groesse.zeilen * sy + UEBERLAPPUNG) / h)
        }
        // Kleiner Abzug: genau passende Blattzahlen sollen nicht durch Rundung ein Blatt mehr ergeben
        fun zahl(laenge: Float, schritt: Float) = ceil((laenge - UEBERLAPPUNG) / schritt - 1e-3).toInt().coerceAtLeast(1)
        return KachelPlan(f, zahl(b * m, sx), zahl(h * m, sy), m, b * m, h * m)
    }
    val a = plan(hoch); val q = plan(PDRectangle(hoch.height, hoch.width))
    return if (groesse is DruckGroesse.Blaetter) (if (q.massstab > a.massstab) q else a)
    else if (q.spalten * q.zeilen < a.spalten * a.zeilen) q else a
}

/** Kennung eines Blatts: Zeile als Buchstabe, Spalte als Zahl (A1 oben links). */
private fun kennung(zeile: Int, spalte: Int) = spalteName(zeile) + (spalte + 1)

/** Die Tafel auf A4-Blaettern zum Zusammenkleben, vorn der Montageplan. */
fun aufBlaetter(original: PDDocument, groesse: DruckGroesse, bereich: Bereich? = null, montageplan: Boolean = true): PDDocument {
    val poster = eingebettet(original)
    val seite = poster.getPage(0)
    val ber = druckBereich(seite, bereich)
    val p = kachelPlan(ber.b, ber.h, seite.userUnit, groesse)
    val doc = PDDocument()
    val form = LayerUtility(doc).importPageAsForm(poster, 0)
    val schrift = Schriften(doc).normal
    if (montageplan) montageplan(doc, form, p, ber)
    val f = p.format
    for (z in 0 until p.zeilen) for (sp in 0 until p.spalten) {
        val page = PDPage(f); doc.addPage(page)
        PDPageContentStream(doc, page).use { cs ->
            // Ausschnitt: Spalte sp von links, Zeile z von oben
            val qx = sp * p.schrittX
            val qyOben = p.endH - z * p.schrittY
            cs.saveGraphicsState()
            cs.addRect(KACHEL_RAND, KACHEL_RAND, f.width - 2 * KACHEL_RAND, f.height - 2 * KACHEL_RAND); cs.clip()
            cs.transform(Matrix.getTranslateInstance(KACHEL_RAND - qx, f.height - KACHEL_RAND - qyOben))
            cs.transform(Matrix.getScaleInstance(p.massstab, p.massstab))
            cs.transform(Matrix.getTranslateInstance(-ber.x, -ber.y))
            cs.addRect(ber.x, ber.y, ber.b, ber.h); cs.clip()
            cs.drawForm(form)
            cs.restoreGraphicsState()
            // Schnittmarken an den Ecken des bedruckten Bereichs, Kennung des Blatts
            cs.setStrokingColor(Color(0x99, 0x99, 0x99)); cs.setLineWidth(0.4f)
            val l = 4 * MM
            listOf(KACHEL_RAND to KACHEL_RAND, f.width - KACHEL_RAND to KACHEL_RAND, KACHEL_RAND to f.height - KACHEL_RAND, f.width - KACHEL_RAND to f.height - KACHEL_RAND).forEach { (x, y) ->
                cs.moveTo(x - l, y); cs.lineTo(x + l, y); cs.moveTo(x, y - l); cs.lineTo(x, y + l)
            }
            cs.stroke()
            cs.setNonStrokingColor(Color(0x88, 0x88, 0x88))
            cs.beginText(); cs.setFont(schrift, 7f); cs.newLineAtOffset(KACHEL_RAND, KACHEL_RAND * 0.4f)
            cs.schreibe(schrift, 7f, "${kennung(z, sp)}  ·  ${p.spalten} × ${p.zeilen}"); cs.endText()
        }
    }
    anhangSeiten(poster, doc)
    return doc
}

/** Erste Seite: die ganze Tafel verkleinert, darueber das Blattraster mit den Kennungen. */
private fun montageplan(doc: PDDocument, form: PDFormXObject, p: KachelPlan, ber: Bereich) {
    val a4 = PDRectangle.A4
    val f = if (p.endB > p.endH) PDRectangle(a4.height, a4.width) else a4
    val page = PDPage(f); doc.addPage(page)
    val s = Schriften(doc)
    val rand = 36f
    val kopf = 44f
    // Das ganze Raster (es reicht etwas ueber die Tafel hinaus) passt in den Platz unter dem Kopf
    val rasterB = p.spalten * p.schrittX + UEBERLAPPUNG; val rasterH = p.zeilen * p.schrittY + UEBERLAPPUNG
    val k = minOf((f.width - 2 * rand) / rasterB, (f.height - 2 * rand - kopf) / rasterH)
    val x0 = (f.width - rasterB * k) / 2
    val yOben = f.height - rand - kopf
    PDPageContentStream(doc, page).use { cs ->
        cs.setNonStrokingColor(Color.BLACK)
        cs.beginText(); cs.setFont(s.fett, 12f); cs.newLineAtOffset(rand, f.height - rand - 12f)
        cs.schreibe(s.fett, 12f, Texte.t(Res.string.desk_print_plan_title, p.spalten * p.zeilen, p.spalten, p.zeilen, p.endBCm, p.endHCm)); cs.endText()
        cs.beginText(); cs.setFont(s.normal, 9f); cs.newLineAtOffset(rand, f.height - rand - 28f)
        cs.schreibe(s.normal, 9f, Texte.t(Res.string.desk_print_plan_hint)); cs.endText()
        // Die Tafel an der linken oberen Ecke des Rasters
        cs.saveGraphicsState()
        cs.transform(Matrix.getTranslateInstance(x0, yOben - p.endH * k))
        cs.transform(Matrix.getScaleInstance(p.massstab * k, p.massstab * k))
        cs.transform(Matrix.getTranslateInstance(-ber.x, -ber.y))
        cs.addRect(ber.x, ber.y, ber.b, ber.h); cs.clip()
        cs.drawForm(form)
        cs.restoreGraphicsState()
        // Raster: je Blatt ein Feld (ohne die Ueberlappung), Kennung gross in der Mitte
        cs.setStrokingColor(Color(0xC0, 0x30, 0x30)); cs.setLineWidth(0.8f)
        val zb = p.schrittX * k; val zh = p.schrittY * k
        val g = (minOf(zb, zh) * 0.3f).coerceIn(8f, 40f)
        for (z in 0 until p.zeilen) for (sp in 0 until p.spalten) {
            val x = x0 + sp * zb; val y = yOben - (z + 1) * zh
            cs.addRect(x, y, zb, zh); cs.stroke()
            val t = kennung(z, sp)
            cs.setNonStrokingColor(Color(0xC0, 0x30, 0x30))
            cs.beginText(); cs.setFont(s.fett, g); cs.newLineAtOffset(x + zb / 2 - s.fett.breite(t, g) / 2, y + zh / 2 - g * 0.35f); cs.showText(t); cs.endText()
        }
    }
}

/**
 * Plotter: Seite so breit wie die Rolle. [einpassen]: die kuerzere Seite der Tafel fuellt die Rolle (die Tafel liegt
 * dann womoeglich quer); sonst die gewaehlte Groesse - passt sie nicht quer auf die Rolle, wird sie in Bahnen geteilt.
 * [laenge]: Seitenlaenge in Punkt samt Rand.
 */
class RollenPlan(val rolle: Float, val massstab: Float, val gedreht: Boolean, val bahnen: Int, val endB: Float, val endH: Float) {
    val nutz get() = rolle - 2 * ROLLEN_RAND
    val laenge get() = (if (gedreht) endB else endH) + 2 * ROLLEN_RAND
    val endBCm get() = cm(endB)
    val endHCm get() = cm(endH)
}

fun rollenPlan(b: Float, h: Float, einheit: Float, rolleCm: Float, groesse: DruckGroesse, einpassen: Boolean): RollenPlan {
    val rolle = rolleCm * 72f / 2.54f
    val nutz = rolle - 2 * ROLLEN_RAND
    // Ohne Einpassen dasselbe Endmass wie bei den Blaettern (auch "auf Spalten x Zeilen")
    val m = if (einpassen) nutz / minOf(b, h) else kachelPlan(b, h, einheit, groesse).massstab
    val w = b * m; val hh = h * m
    return when {
        einpassen -> RollenPlan(rolle, m, h < b, 1, w, hh)
        w <= nutz + 0.5f -> RollenPlan(rolle, m, false, 1, w, hh)
        hh <= nutz + 0.5f -> RollenPlan(rolle, m, true, 1, w, hh)
        else -> {
            // Zu breit und zu hoch: Bahnen in der Richtung, die weniger davon braucht (lange Bahnen, wenige Naehte)
            fun bahnen(quer: Float) = ceil((quer - BAHN_UEBERLAPPUNG) / (nutz - BAHN_UEBERLAPPUNG) - 1e-3).toInt()
            if (bahnen(hh) < bahnen(w)) RollenPlan(rolle, m, true, bahnen(hh), w, hh) else RollenPlan(rolle, m, false, bahnen(w), w, hh)
        }
    }
}

/** Die Tafel als PDF fuer den Plotter: je Bahn eine Seite in Rollenbreite; ueber 5,08 m mit UserUnit. */
fun aufRolle(original: PDDocument, plan: RollenPlan, bereich: Bereich? = null): PDDocument {
    val poster = eingebettet(original)
    val ber = druckBereich(poster.getPage(0), bereich)
    val doc = PDDocument()
    val form = LayerUtility(doc).importPageAsForm(poster, 0)
    val schrift = Schriften(doc).normal
    val lang = plan.laenge
    // Seiten laenger als PDF_MAX: Einheiten groesser machen, alles darin entsprechend kleiner zeichnen
    val k = maxOf(1f, lang / PDF_MAX, plan.rolle / PDF_MAX)
    for (i in 0 until plan.bahnen) {
        val page = PDPage(PDRectangle(plan.rolle / k, lang / k)); doc.addPage(page)
        if (k > 1f) page.userUnit = k
        PDPageContentStream(doc, page).use { cs ->
            if (k > 1f) cs.transform(Matrix.getScaleInstance(1f / k, 1f / k))
            cs.saveGraphicsState()
            cs.addRect(ROLLEN_RAND, ROLLEN_RAND, plan.nutz, lang - 2 * ROLLEN_RAND); cs.clip()
            // Bahn i zeigt quer zur Rolle den Abschnitt ab i * (nutz - Ueberlappung)
            val versatz = i * (plan.nutz - BAHN_UEBERLAPPUNG)
            if (plan.gedreht) {
                // Um 90 Grad gedreht: die Hoehe der Tafel liegt quer zur Rolle (oben links), ihre Breite laeuft die Rolle entlang
                cs.transform(Matrix.getTranslateInstance(ROLLEN_RAND + plan.endH - versatz, ROLLEN_RAND))
                cs.transform(Matrix.getRotateInstance(Math.PI / 2, 0f, 0f))
            } else cs.transform(Matrix.getTranslateInstance(ROLLEN_RAND - versatz, ROLLEN_RAND))
            cs.transform(Matrix.getScaleInstance(plan.massstab, plan.massstab))
            cs.transform(Matrix.getTranslateInstance(-ber.x, -ber.y))
            cs.addRect(ber.x, ber.y, ber.b, ber.h); cs.clip()
            cs.drawForm(form)
            cs.restoreGraphicsState()
            // Schnittlinien an Anfang und Ende, Passkreuze in der Ueberlappung zur Nachbarbahn
            cs.setStrokingColor(Color(0x99, 0x99, 0x99)); cs.setLineWidth(0.5f)
            listOf(ROLLEN_RAND, lang - ROLLEN_RAND).forEach { y ->
                cs.moveTo(0f, y); cs.lineTo(ROLLEN_RAND * 0.7f, y); cs.moveTo(plan.rolle - ROLLEN_RAND * 0.7f, y); cs.lineTo(plan.rolle, y)
            }
            val kreuzX = buildList {
                if (i > 0) add(ROLLEN_RAND + BAHN_UEBERLAPPUNG / 2)
                if (i < plan.bahnen - 1) add(ROLLEN_RAND + plan.nutz - BAHN_UEBERLAPPUNG / 2)
            }
            val l = 4 * MM
            kreuzX.forEach { x -> listOf(0.25f, 0.5f, 0.75f).forEach { t ->
                val y = ROLLEN_RAND + (lang - 2 * ROLLEN_RAND) * t
                cs.moveTo(x - l, y); cs.lineTo(x + l, y); cs.moveTo(x, y - l); cs.lineTo(x, y + l)
            } }
            cs.stroke()
            cs.setNonStrokingColor(Color(0x88, 0x88, 0x88))
            val rolleText = dezimal(plan.rolle / 72f * 2.54f, 1)
            val text = (if (plan.bahnen > 1) Texte.t(Res.string.desk_print_strip, i + 1, plan.bahnen) + "  ·  " else "") +
                "${plan.endBCm} × ${plan.endHCm} cm  ·  $rolleText cm"
            cs.beginText(); cs.setFont(schrift, 8f); cs.newLineAtOffset(ROLLEN_RAND, ROLLEN_RAND * 0.35f); cs.schreibe(schrift, 8f, text); cs.endText()
        }
    }
    return doc
}
