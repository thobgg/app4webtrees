package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.res.*
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.util.Matrix
import java.awt.Color
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/*
 * Faechertafel und Ahnenkreis (26.09.2026): der Proband in der Mitte, jede Generation ein Ring darum, Vater
 * links, Mutter rechts. Halbkreis (Faecher) oder Vollkreis. Innen laeuft die Schrift quer zum Radius, in den
 * schmalen aeusseren Ringen entlang des Radius. "Farbig" faerbt die vier Grosseltern-Linien.
 */

/** Grosseltern-Linien: Vater des Vaters blau, Mutter des Vaters gruen, Vater der Mutter rot, Mutter der Mutter gelb. */
private val linienFarben = listOf(Color(0x9C, 0xBC, 0xE0), Color(0xA8, 0xD5, 0xA2), Color(0xEE, 0xA8, 0xA0), Color(0xF4, 0xDC, 0x8C))

/** Farbe eines Segments bei "Farbig": Eltern nach Geschlecht, danach die Linie des Grosselternteils, aussen heller. */
private fun linienFarbe(n: Long, g: Int, sex: String): Color {
    val basis = when {
        g <= 1 -> if (sex == "F") Color(0xF3, 0xC4, 0xBE) else Color(0xB9, 0xD0, 0xE8)
        else -> linienFarben[((n shr (g - 2)) - 4).toInt().coerceIn(0, 3)]
    }
    val hell = ((g - 2).coerceAtLeast(0) * 0.08f).coerceAtMost(0.4f)
    fun m(c: Int) = (c + (255 - c) * hell).toInt()
    return Color(m(basis.red), m(basis.green), m(basis.blue))
}

/** Kreisbogen als Bezierkurven (hoechstens 90° je Stueck), von [a1] nach [a2] im Bogenmass, ab dem aktuellen Punkt. */
private fun PDPageContentStream.bogen(cx: Float, cy: Float, r: Float, a1: Double, a2: Double) {
    val stuecke = ceil(abs(a2 - a1) / (PI / 2)).toInt().coerceAtLeast(1)
    val schritt = (a2 - a1) / stuecke
    val k = 4.0 / 3.0 * tan(schritt / 4)
    for (i in 0 until stuecke) {
        val a = a1 + i * schritt; val b = a + schritt
        val x0 = cx + r * cos(a); val y0 = cy + r * sin(a)
        val x3 = cx + r * cos(b); val y3 = cy + r * sin(b)
        curveTo(
            (x0 - k * r * sin(a)).toFloat(), (y0 + k * r * cos(a)).toFloat(),
            (x3 + k * r * sin(b)).toFloat(), (y3 - k * r * cos(b)).toFloat(),
            x3.toFloat(), y3.toFloat(),
        )
    }
}

/** Ringsegment zwischen [r1] und [r2], Winkel [a1] bis [a2]; r1 = 0 ergibt ein Tortenstueck. */
private fun PDPageContentStream.segment(cx: Float, cy: Float, r1: Float, r2: Float, a1: Double, a2: Double) {
    moveTo((cx + r2 * cos(a1)).toFloat(), (cy + r2 * sin(a1)).toFloat())
    bogen(cx, cy, r2, a1, a2)
    if (r1 <= 0f) lineTo(cx, cy)
    else { lineTo((cx + r1 * cos(a2)).toFloat(), (cy + r1 * sin(a2)).toFloat()); bogen(cx, cy, r1, a2, a1) }
    closePath()
}

/** Voller Kreis ohne Strich zur Mitte. */
private fun PDPageContentStream.kreis(cx: Float, cy: Float, r: Float) {
    moveTo(cx + r, cy); bogen(cx, cy, r, 0.0, 2 * PI); closePath()
}

/** Text gedreht um [winkel] (Bogenmass), mittig auf den Punkt, [dy] quer zur Leserichtung versetzt. */
private fun PDPageContentStream.gedreht(text: String, schrift: PDFont, groesse: Float, x: Float, y: Float, winkel: Double, dy: Float) {
    val w = schrift.breite(text, groesse)
    val at = AffineTransform().apply { translate(x.toDouble(), y.toDouble()); rotate(winkel); translate(-w / 2.0, dy.toDouble()) }
    beginText(); setFont(schrift, groesse); setTextMatrix(Matrix(at)); schreibe(schrift, groesse, text); endText()
}

/** Groesste Schrift, bei der alle [zeilen] in [breite] x [hoehe] passen (Zeilenhoehe 1,2), hoechstens [max]. */
private fun schriftFuer(zeilen: List<Pair<String, PDFont>>, breite: Float, hoehe: Float, max: Float): Float {
    val nachHoehe = hoehe / (zeilen.size * 1.2f)
    val nachBreite = zeilen.minOf { (t, s) -> val w = s.breite(t, 1f); if (w <= 0f) Float.MAX_VALUE else breite / w }
    return minOf(max, nachHoehe, nachBreite)
}

private fun faecherJahre(p: Person): String {
    val g = p.birth?.date?.year?.takeIf { it > 0 }; val t = p.death?.date?.year?.takeIf { it > 0 }
    return when {
        g != null && t != null -> "$g–$t"
        g != null -> "* $g"
        t != null -> "† $t"
        else -> ""
    }
}

/**
 * Faechertafel als PDF mit einem Blatt. [wurzel]: Ahnenbaum mit Kekule-Nummern (ahnenBaum), [generationen]
 * einschliesslich Proband. [vollkreis]: Ahnenkreis statt Halbkreis.
 */
fun faecherPdf(
    wurzel: TafelPerson, generationen: Int, vollkreis: Boolean, o: TafelOptionen, bilder: (Person) -> BufferedImage?, privat: String, fuss: String,
    details: Map<String, de.bgghome.webtrees.nativ.api.IndividualDetail>? = null,
): Pair<PDDocument, TafelInfo> {
    // Klickflaechen je Segment (Mitte, y von oben, in Seiteneinheiten) fuer die Links zu den Karteikarten
    val orte = ArrayList<KartenOrt>()
    // Lebende auf Wunsch ohne Jahre
    fun jahre(p: Person) = if (o.lebendeNurNamen && !p.isDead) "" else faecherJahre(p)
    val knoten = HashMap<Long, TafelPerson>()
    fun sammeln(k: TafelPerson) { knoten[k.nummer ?: 1L] = k; k.kinder.forEach(::sammeln) }
    sammeln(wurzel)
    val ringe = (generationen - 1).coerceAtLeast(1)
    val r = o.rahmenMm * 72f / 25.4f
    // Ringbreiten: innen knapp (Schrift quer), aussen mehr Tiefe fuer die Schrift entlang des Radius
    val r0 = r * 0.95f
    val breiten = (1..ringe).map { g -> if (g <= 3) r * 1.05f else r * 1.45f }
    val radien = breiten.runningFold(r0) { a, b -> a + b }   // radien[g] = Aussenradius von Ring g, radien[0] = Mitte
    val aussen = radien.last()
    val doc = PDDocument()
    val f = farben(o.stil)
    val s = TafelSchriften(doc, o.stil)

    val rand = maxOf(r * 0.5f, 28f) * schmuckRand(o.schmuck)
    val titelGroesse = (r * 0.55f).coerceIn(22f, 72f)
    val titelBreite = if (o.titel.isBlank()) 0f else s.titel.breite(o.titel, titelGroesse)
    // Zeile unter dem Titel und Legende wie bei den anderen Tafeln
    val untertitel = untertitelText(o)
    val ug = (titelGroesse * 0.36f).coerceIn(10f, 26f)
    val untertitelBreite = if (untertitel.isEmpty()) 0f else s.normal.breite(untertitel, ug)
    val titelH = (if (o.titel.isBlank()) 0f else titelGroesse * 1.9f) + (if (untertitel.isEmpty()) 0f else if (o.titel.isBlank()) ug * 2f else ug * 1.3f)
    val legende = if (!o.legende) emptyList() else buildList {
        // Farbig: die vier Grosseltern-Linien, benannt nach dem Grosselternteil
        if (o.stil == TafelStil.Farbig && ringe >= 2) (4L..7L).forEach { n -> knoten[n]?.let { add(LegendenEintrag(LegendenArt.Farbe, "", Texte.t(Res.string.desk_legend_line, it.person.name), (n - 4).toInt())) } }
        add(LegendenEintrag(LegendenArt.Zeichen, "*", Texte.t(Res.string.desk_legend_born)))
        add(LegendenEintrag(LegendenArt.Zeichen, "†", Texte.t(Res.string.desk_legend_died)))
        if (o.nummern) add(LegendenEintrag(LegendenArt.Nummer, "4", Texte.t(Res.string.desk_legend_kekule)))
        if (knoten.values.any { it.verweis != null }) add(LegendenEintrag(LegendenArt.Zeichen, "= 8", Texte.t(Res.string.desk_legend_ref)))
    }
    val fussH = 18f
    val grafikB = 2 * aussen
    val grafikH = if (vollkreis) 2 * aussen else aussen + r0 * 0.9f
    val lm = LegendenMass(legende, s.normal, s.fett, r, maxOf(grafikB, titelBreite))
    val legendeH = if (lm.h > 0f) lm.h + lm.g else 0f
    val inhaltB = maxOf(grafikB, titelBreite, untertitelBreite, lm.b)
    val b = inhaltB + 2 * rand
    val h = rand + titelH + grafikH + legendeH + fussH + rand
    val skala = minOf(1f, PDF_MAX / b, PDF_MAX / h)
    val page = PDPage(PDRectangle(b * skala, h * skala))
    if (skala < 1f) page.userUnit = 1f / skala
    doc.addPage(page)
    // Mittelpunkt in PDF-Koordinaten (y nach oben)
    val cx = b / 2
    val cy = h - (rand + titelH + aussen)
    // Winkelbereich: Halbkreis von links (180°) ueber oben nach rechts (0°), Vollkreis ab unten links im Uhrzeigersinn
    val start = if (vollkreis) PI * 1.5 else PI
    val umfang = if (vollkreis) 2 * PI else PI
    fun winkel(n: Long, g: Int): Pair<Double, Double> {
        val i = n - (1L shl g); val teil = umfang / (1L shl g)
        return (start - i * teil) to (start - (i + 1) * teil)
    }

    PDPageContentStream(doc, page).use { cs ->
        if (skala < 1f) cs.transform(Matrix.getScaleInstance(skala, skala))
        cs.tafelHintergrund(doc, o.hintergrund, b, h, f.hintergrundOben, f.hintergrundUnten, o.hintergrundBild)
        cs.schmuckrahmen(o.schmuck, b, h, rand, f.titel)
        if (o.titel.isNotBlank()) {
            cs.setNonStrokingColor(f.titel)
            cs.beginText(); cs.setFont(s.titel, titelGroesse)
            cs.newLineAtOffset((b - titelBreite) / 2, h - (rand + titelGroesse * 1.05f)); cs.schreibe(s.titel, titelGroesse, o.titel); cs.endText()
        }
        if (untertitel.isNotEmpty()) {
            val y = if (o.titel.isBlank()) rand + ug * 1.1f else rand + titelGroesse * 1.05f + ug * 1.9f
            cs.setNonStrokingColor(f.linie)
            cs.beginText(); cs.setFont(s.normal, ug); cs.newLineAtOffset((b - untertitelBreite) / 2, h - y); cs.schreibe(s.normal, ug, untertitel); cs.endText()
        }
        val linie = maxOf(0.5f, r * 0.008f)

        // Leere Plaetze als blasses Raster ueber alle Ringe - die Form bleibt geschlossen
        for (g in 1..ringe) for (n in (1L shl g) until (1L shl (g + 1))) {
            if (n in knoten) continue
            // Ueber einem Verweis ("= 8") ist nichts leer - diese Vorfahren stehen an der anderen Stelle
            val naechster = generateSequence(n / 2) { if (it > 1) it / 2 else null }.firstOrNull { it in knoten }
            if (naechster != null && knoten.getValue(naechster).verweis != null) continue
            val (a1, a2) = winkel(n, g)
            cs.setStrokingColor(Color(0xBB, 0xBB, 0xBB)); cs.setLineWidth(linie * 0.6f)
            cs.segment(cx, cy, radien[g - 1], radien[g], a1, a2); cs.stroke()
        }

        knoten.forEach { (n, k) ->
            val g = reihe(n)
            if (g == 0 || g > ringe) return@forEach
            val p = k.person
            val (a1, a2) = winkel(n, g)
            val fuellung = if (o.stil == TafelStil.Farbig) linienFarbe(n, g, p.sex) else f.fuellung(p.sex)
            cs.setNonStrokingColor(fuellung); cs.setStrokingColor(f.linie); cs.setLineWidth(linie)
            cs.segment(cx, cy, radien[g - 1], radien[g], a1, a2); cs.fillAndStroke()

            // Schrift: quer, wenn das Segment breiter als tief ist, sonst entlang des Radius
            val rm = (radien[g - 1] + radien[g]) / 2
            val am = (a1 + a2) / 2
            val bogenLaenge = (rm * abs(a2 - a1)).toFloat()
            val tiefe = radien[g] - radien[g - 1]
            val quer = bogenLaenge >= tiefe * 0.9f
            val zeilen = buildList<Pair<String, PDFont>> {
                if (p.isPrivate) add(privat to s.normal)
                else if (quer) {
                    p.given.ifBlank { if (p.surname.isBlank()) p.name else "" }.takeIf(String::isNotBlank)?.let { add(it to s.normal) }
                    add(p.surname.ifBlank { p.name } to s.fett)
                    jahre(p).takeIf(String::isNotBlank)?.let { add(it to s.normal) }
                } else {
                    add(p.name.ifBlank { "?" } to s.fett)
                    jahre(p).takeIf(String::isNotBlank)?.let { add(it to s.normal) }
                }
                k.verweis?.let { add("= $it" to s.fett) }
            }
            val (breite, hoehe) = if (quer) bogenLaenge * 0.86f to tiefe * 0.8f else tiefe * 0.88f to bogenLaenge * 0.8f
            val groesse = schriftFuer(zeilen, breite, hoehe, r * 0.13f)
            // Leserichtung: quer immer aufrecht (unten gedreht), entlang des Radius links von innen nach aussen gespiegelt
            val oberhalb = sin(am) >= -1e-6
            // Ein Segment ab einem halben Kreis (Eltern im Ahnenkreis) bekommt waagerechte Schrift
            val drehung = when {
                abs(a2 - a1) >= PI - 1e-6 -> 0.0
                quer -> if (oberhalb) am - PI / 2 else am + PI / 2
                else -> if (cos(am) >= 0) am else am + PI
            }
            val x = (cx + rm * cos(am)).toFloat(); val y = (cy + rm * sin(am)).toFloat()
            run {
                val seite = minOf(bogenLaenge, tiefe) * 0.8f
                orte += KartenOrt(p, (x - seite / 2) * skala, (h - y - seite / 2) * skala, seite * skala, seite * skala)
            }
            cs.setNonStrokingColor(f.text)
            val zh = groesse * 1.2f
            zeilen.forEachIndexed { i, (t, schrift) ->
                // Zeilen mittig um den Punkt: erste Zeile oben (in Leserichtung)
                val dy = (zeilen.size - 1) * zh / 2 - i * zh - groesse * 0.35f
                cs.gedreht(t, schrift, groesse, x, y, drehung, dy)
            }
            if (o.nummern && groesse > 3f) {
                val ng = minOf(groesse * 0.8f, r * 0.08f)
                val rn = radien[g] - ng * 1.1f
                val an = a1 - (a1 - a2) * 0.5
                cs.setNonStrokingColor(f.linie)
                cs.gedreht(n.toString(), s.normal, ng, (cx + rn * cos(an)).toFloat(), (cy + rn * sin(an)).toFloat(), if (oberhalb || !quer) an - PI / 2 else an + PI / 2, -ng * 0.35f)
            }
        }

        // Mitte: der Proband, mit Bild im Kreis (Halbkreis: nur die obere Haelfte als Flaeche)
        val p = wurzel.person
        cs.setNonStrokingColor(if (o.stil == TafelStil.Farbig) Color(0xDD, 0xE2, 0xE2) else f.fuellung(p.sex)); cs.setStrokingColor(f.linie); cs.setLineWidth(linie * 1.5f)
        if (vollkreis) cs.kreis(cx, cy, r0) else cs.segment(cx, cy, 0f, r0, PI, 0.0)
        cs.fillAndStroke()
        val foto = if (o.bilder && !p.isPrivate) bilder(p) else null
        val bildR = r0 * 0.55f
        val bildY = if (vollkreis) cy + r0 * 0.28f else cy + r0 * 0.42f
        if (o.bilder) {
            val bild = foto?.let { JPEGFactory.createFromImage(doc, if (f.grau) grau(it) else it, 0.9f) }
                ?: LosslessFactory.createFromImage(doc, silhouetteBild(p.sex, jahrhundert(p), 160).let { if (f.grau) grau(it) else it })
            cs.saveGraphicsState()
            cs.kreis(cx, bildY, bildR); cs.clip()
            cs.drawImage(bild, cx - bildR, bildY - bildR, 2 * bildR, 2 * bildR)
            cs.restoreGraphicsState()
            cs.setStrokingColor(f.linie); cs.setLineWidth(linie); cs.kreis(cx, bildY, bildR); cs.stroke()
        }
        val mitteZeilen = listOf(p.given.ifBlank { p.name } to s.fett, p.surname to s.fett, jahre(p) to s.normal).filter { it.first.isNotBlank() }
        val mg = schriftFuer(mitteZeilen, r0 * 1.5f, r0 * 0.5f, r * 0.12f)
        var ty = if (o.bilder) bildY - bildR - mg * 1.1f else cy + if (vollkreis) mitteZeilen.size * mg * 0.6f else r0 * 0.6f
        cs.setNonStrokingColor(f.text)
        mitteZeilen.forEach { (t, schrift) ->
            val w = schrift.breite(t, mg)
            cs.beginText(); cs.setFont(schrift, mg); cs.newLineAtOffset(cx - w / 2, ty); cs.schreibe(schrift, mg, t); cs.endText()
            ty -= mg * 1.2f
        }

        // Legende unten rechts unter der Grafik
        if (legende.isNotEmpty()) {
            val heirat = if (runCatching { s.normal.encode("⚭") }.isSuccess) "⚭" else "oo"
            cs.legendeZeichnen(legende, lm, b - rand - lm.b, rand + titelH + grafikH + lm.g, { h - it }, s, f, heirat, r)
        }
        cs.setNonStrokingColor(Color(0x66, 0x66, 0x66))
        val fx = if (o.schmuck == Schmuckrahmen.Keiner) rand else (b - s.normal.breite(fuss, 7f)) / 2
        cs.beginText(); cs.setFont(s.normal, 7f); cs.newLineAtOffset(fx, rand * if (o.schmuck == Schmuckrahmen.Keiner) 0.6f else 0.85f); cs.schreibe(s.normal, 7f, fuss); cs.endText()
    }
    // Der Proband in der Mitte ist auch anklickbar
    orte += KartenOrt(wurzel.person, (cx - r0 * 0.7f) * skala, (h - cy - r0 * 0.7f) * skala, r0 * 1.4f * skala, r0 * 1.4f * skala)
    val info = TafelInfo(knoten.values.map { it.person.xref }.distinct().size, (b / 72f * 2.54f).toInt(), (h / 72f * 2.54f).toInt(),
        seiteB = b * skala, seiteH = h * skala, einheit = 1f / skala, karten = orte)
    details?.let { karteikartenFuerSeiten(doc, listOf(0 to orte), it, bilder, fuss) }
    return doc to info
}
