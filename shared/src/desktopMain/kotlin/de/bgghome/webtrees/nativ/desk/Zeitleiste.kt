package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.res.*
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.util.Matrix
import java.awt.Color
import java.time.LocalDate

/*
 * Zeitleiste der Vorfahren (28.09.2026): je Person ein Lebensbalken ueber einer Jahresachse - wer lebte wann, wer
 * gleichzeitig. Aelteste Generation oben, darin nach Kekule-Nummer. Unbekannte Enden gestrichelt geschaetzt, Lebende
 * bis heute. Auf Wunsch Zeitereignisse als blasse Baender (Texte in den Sprachdateien: desk_timeline_events).
 */

/** Eine Zeile der Zeitleiste: Person, Generation (fuer die Trennstriche), Farbe (Platz in KASTEN_FARBEN oder null). */
class ZeitEintrag(val person: Person, val generation: Int, val farbe: Int?)

/** Vorfahren: aelteste Generation oben, darin nach Kekule; Farbe nach Grosseltern-Linie. */
fun zeitVorfahren(ahnen: Map<Long, AhnenEintrag>, generationen: Int): List<ZeitEintrag> =
    ahnen.filterKeys { reihe(it) < generationen }.entries.sortedWith(compareBy({ -reihe(it.key) }, { it.key }))
        .map { (n, a) -> ZeitEintrag(a.person, reihe(n), if (n >= 4) ((n shr (reihe(n) - 2)) - 4).toInt().coerceIn(0, 3) else null) }

/** Nachfahren: der Stammvater oben, dann Generation fuer Generation in Baumfolge; Farbe je Zweig (Kind des Stammvaters). */
fun zeitNachfahren(wurzel: de.bgghome.webtrees.nativ.api.DescendantNode, generationen: Int): List<ZeitEintrag> {
    val ebenen = HashMap<Int, MutableList<ZeitEintrag>>()
    fun gehen(k: de.bgghome.webtrees.nativ.api.DescendantNode, tiefe: Int, zweig: Int?) {
        if (tiefe >= generationen) return
        ebenen.getOrPut(tiefe) { mutableListOf() } += ZeitEintrag(k.person, tiefe, zweig)
        k.families.flatMap { it.children }.forEachIndexed { i, c -> gehen(c, tiefe + 1, zweig ?: (i % KASTEN_FARBEN.size)) }
    }
    gehen(wurzel, 0, null)
    return ebenen.keys.sorted().flatMap { ebenen.getValue(it) }
}

private class Balken(val n: Long, val p: Person, val von: Int, val bis: Int, val vonGeschaetzt: Boolean, val bisGeschaetzt: Boolean, val lebt: Boolean)

/** Lebensspanne einer Person; null, wenn weder Geburt noch Tod ein Jahr haben. */
private fun balken(n: Long, p: Person, heute: Int): Balken? {
    val g = p.birth?.date?.year?.takeIf { it > 0 } ?: p.chr?.date?.year?.takeIf { it > 0 }
    val t = p.death?.date?.year?.takeIf { it > 0 } ?: p.buri?.date?.year?.takeIf { it > 0 }
    return when {
        g != null && t != null -> Balken(n, p, g, maxOf(t, g), false, false, false)
        g != null && !p.isDead -> Balken(n, p, g, heute, false, false, true)
        g != null -> Balken(n, p, g, minOf(g + 65, heute), false, true, false)
        t != null -> Balken(n, p, t - 60, t, true, false, false)
        else -> null
    }
}

/** Zeitereignisse aus der Sprachdatei: "1618|1648|Dreissigjaehriger Krieg;..." */
private fun ereignisse(): List<Triple<Int, Int, String>> = Texte.t(Res.string.desk_timeline_events).split(';').mapNotNull { e ->
    val t = e.split('|'); if (t.size < 3) null else Triple(t[0].trim().toIntOrNull() ?: return@mapNotNull null, t[1].trim().toIntOrNull() ?: return@mapNotNull null, t[2].trim())
}

fun zeitleistePdf(eintraege: List<ZeitEintrag>, o: TafelOptionen, fuss: String, legendeFarben: List<Pair<Int, String>>): Pair<PDDocument, TafelInfo>? {
    val heute = LocalDate.now().year
    val farbeVon = HashMap<Long, Int?>()
    val alle = eintraege.filter { it.person.xref !in o.ausgeblendet && !it.person.isPrivate }.withIndex()
        .mapNotNull { (i, e) -> balken(e.generation.toLong() * 100000 + i, e.person, heute)?.also { farbeVon[it.n] = e.farbe } }
    if (alle.isEmpty()) return null
    val doc = PDDocument()
    val f = farben(o.stil)
    val s = TafelSchriften(doc, o.stil)
    val r = o.rahmenMm * 72f / 25.4f
    // Masse aus der Rahmenbreite: Schrift, Zeilenhoehe, Punkt je Jahr
    val g = (r * 0.1f).coerceIn(7f, 14f)
    val zeileH = g * 2.1f
    val jahrB = r * 0.09f
    val von = (alle.minOf { it.von } - 5) / 10 * 10
    val bis = ((alle.maxOf { it.bis } + 14) / 10) * 10
    val namenB = alle.maxOf { s.fett.breite(it.p.name.ifBlank { "?" }, g) } + g * 3f
    val achseB = (bis - von) * jahrB
    val rand = maxOf(r * 0.5f, 28f) * schmuckRand(o.schmuck)
    val titelGroesse = (r * 0.55f).coerceIn(22f, 72f)
    val titelBreite = if (o.titel.isBlank()) 0f else s.titel.breite(o.titel, titelGroesse)
    val untertitel = untertitelText(o)
    val ug = (titelGroesse * 0.36f).coerceIn(10f, 26f)
    val untertitelBreite = if (untertitel.isEmpty()) 0f else s.normal.breite(untertitel, ug)
    val titelH = (if (o.titel.isBlank()) 0f else titelGroesse * 1.9f) + (if (untertitel.isEmpty()) 0f else if (o.titel.isBlank()) ug * 2f else ug * 1.3f)
    val ereig = if (o.zeitereignisse) ereignisse().filter { it.second >= von && it.first <= bis } else emptyList()
    val legende = if (!o.legende) emptyList() else buildList {
        if (o.stil == TafelStil.Farbig) legendeFarben.forEach { (f, t) -> add(LegendenEintrag(LegendenArt.Farbe, "", t, f)) }
        add(LegendenEintrag(LegendenArt.Zeichen, "- - -", Texte.t(Res.string.desk_legend_estimated)))
    }
    val kopfH = g * 2.5f   // Jahreszahlen oben
    // Ueber den Jahreszahlen ein Streifen fuer die Namen der Zeitereignisse (senkrecht, von unten nach oben)
    val ereignisH = if (ereig.isEmpty()) 0f else ereig.maxOf { s.normal.breite(it.third, g * 0.8f) } + g
    val grafikB = namenB + achseB
    val grafikH = ereignisH + kopfH + alle.size * zeileH + kopfH
    val lm = LegendenMass(legende, s.normal, s.fett, r, grafikB)
    val legendeH = if (lm.h > 0f) lm.h + lm.g else 0f
    val fussH = 18f
    val inhaltB = maxOf(grafikB, titelBreite, untertitelBreite, lm.b)
    val b = inhaltB + 2 * rand
    val h = rand + titelH + grafikH + legendeH + fussH + rand
    val skala = minOf(1f, PDF_MAX / b, PDF_MAX / h)
    val page = PDPage(PDRectangle(b * skala, h * skala))
    if (skala < 1f) page.userUnit = 1f / skala
    doc.addPage(page)
    val x0 = rand + (inhaltB - grafikB) / 2
    val y0 = rand + titelH + ereignisH
    fun py(y: Float) = h - y
    fun xJahr(j: Int) = x0 + namenB + (j - von) * jahrB
    PDPageContentStream(doc, page).use { cs ->
        fun text(t: String, schrift: org.apache.pdfbox.pdmodel.font.PDFont, gr: Float, x: Float, y: Float) { cs.beginText(); cs.setFont(schrift, gr); cs.newLineAtOffset(x, y); cs.schreibe(schrift, gr, t); cs.endText() }
        if (skala < 1f) cs.transform(Matrix.getScaleInstance(skala, skala))
        cs.tafelHintergrund(doc, o.hintergrund, b, h, f.hintergrundOben, f.hintergrundUnten, o.hintergrundBild)
        cs.schmuckrahmen(o.schmuck, b, h, rand, f.titel)
        if (o.titel.isNotBlank()) { cs.setNonStrokingColor(f.titel); text(o.titel, s.titel, titelGroesse, (b - titelBreite) / 2, py(rand + titelGroesse * 1.05f)) }
        if (untertitel.isNotEmpty()) {
            cs.setNonStrokingColor(f.linie)
            text(untertitel, s.normal, ug, (b - untertitelBreite) / 2, py(if (o.titel.isBlank()) rand + ug * 1.1f else rand + titelGroesse * 1.05f + ug * 1.9f))
        }
        val oben = y0 + kopfH; val unten = y0 + kopfH + alle.size * zeileH
        // Zeitereignisse: blasse Baender ueber die ganze Hoehe, Name oben schraeg
        ereig.forEach { (a, e, name) ->
            val xa = xJahr(maxOf(a, von)); val xe = xJahr(minOf(maxOf(e, a + 1), bis))
            cs.saveGraphicsState(); cs.setGraphicsStateParameters(PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.13f })
            cs.setNonStrokingColor(Color(0x80, 0x6A, 0x50)); cs.addRect(xa, py(unten), xe - xa, unten - oben); cs.fill(); cs.restoreGraphicsState()
            cs.setNonStrokingColor(Color(0x70, 0x60, 0x50))
            cs.saveGraphicsState(); cs.transform(Matrix.getRotateInstance(Math.PI / 2, (xa + xe) / 2 + g * 0.3f, py(y0 - g * 0.2f)))
            text(name, s.normal, g * 0.8f, 0f, 0f); cs.restoreGraphicsState()
        }
        // Jahresraster: feine Linie je Jahrzehnt, kraeftiger und beschriftet alle 50 Jahre
        var j = von
        while (j <= bis) {
            val stark = j % 50 == 0
            cs.setStrokingColor(if (stark) Color(0xB0, 0xB0, 0xB0) else Color(0xE0, 0xE0, 0xE0)); cs.setLineWidth(if (stark) 0.6f else 0.3f)
            cs.moveTo(xJahr(j), py(oben)); cs.lineTo(xJahr(j), py(unten)); cs.stroke()
            if (stark || jahrB * 10 > g * 3.5f) {
                val t = j.toString(); val tb = s.normal.breite(t, g * 0.85f)
                cs.setNonStrokingColor(f.linie)
                text(t, s.normal, g * 0.85f, xJahr(j) - tb / 2, py(oben - g * 0.8f)); text(t, s.normal, g * 0.85f, xJahr(j) - tb / 2, py(unten + g * 1.6f))
            }
            j += 10
        }
        // Balken, Namen, Jahre; ein feiner Strich trennt die Generationen
        var vorher = -1
        alle.forEachIndexed { i, z ->
            val yt = oben + i * zeileH
            val gen = (z.n / 100000).toInt()
            if (vorher >= 0 && gen != vorher) { cs.setStrokingColor(f.linie); cs.setLineWidth(0.4f); cs.moveTo(x0, py(yt)); cs.lineTo(x0 + grafikB, py(yt)); cs.stroke() }
            vorher = gen
            val eigene = farbeVon[z.n]?.takeIf { o.stil == TafelStil.Farbig }
            val farbe = if (eigene != null) KASTEN_FARBEN[eigene.coerceIn(0, KASTEN_FARBEN.size - 1)] else KastenFarbe(f.fuellung(z.p.sex).let { if (it == Color.WHITE) Color(0xE8, 0xE2, 0xD6) else it }, f.rahmen(z.p.sex))
            val xa = xJahr(z.von); val xe = xJahr(z.bis)
            val bh = zeileH * 0.62f; val by = py(yt + (zeileH + bh) / 2)
            cs.setNonStrokingColor(farbe.fuellung); cs.setStrokingColor(farbe.rahmen); cs.setLineWidth(0.7f)
            cs.rechteck(xa, by, maxOf(xe - xa, 2f), bh, bh * 0.25f); cs.fill()
            // Rahmen: geschaetzte Enden gestrichelt, Lebende ohne rechten Rand
            if (z.vonGeschaetzt || z.bisGeschaetzt || z.lebt) {
                cs.setLineDashPattern(floatArrayOf(2f, 2f), 0f); cs.rechteck(xa, by, maxOf(xe - xa, 2f), bh, bh * 0.25f); cs.stroke(); cs.setLineDashPattern(floatArrayOf(), 0f)
            } else { cs.rechteck(xa, by, maxOf(xe - xa, 2f), bh, bh * 0.25f); cs.stroke() }
            cs.setNonStrokingColor(f.text)
            val name = z.p.name.ifBlank { "?" }
            text(name, s.fett, g, x0 + namenB - g * 1.5f - s.fett.breite(name, g), py(yt + zeileH / 2 + g * 0.35f))
            val jahre = when {
                o.lebendeNurNamen && z.lebt -> ""
                z.lebt -> "* ${z.von}"
                else -> (if (z.vonGeschaetzt) "?" else "${z.von}") + "–" + (if (z.bisGeschaetzt) "?" else "${z.bis}")
            }
            if (jahre.isNotEmpty()) {
                val jg = g * 0.8f; val jb = s.normal.breite(jahre, jg)
                // In den Balken, wenn er lang genug ist, sonst dahinter
                val jx = if (xe - xa > jb + g) xa + g * 0.4f else xe + g * 0.4f
                text(jahre, s.normal, jg, jx, py(yt + zeileH / 2 + jg * 0.35f))
            }
        }
        if (legende.isNotEmpty()) {
            val heirat = if (runCatching { s.normal.encode("⚭") }.isSuccess) "⚭" else "oo"
            cs.legendeZeichnen(legende, lm, b - rand - lm.b, y0 - ereignisH + grafikH + lm.g, ::py, s, f, heirat, r)
        }
        cs.setNonStrokingColor(Color(0x66, 0x66, 0x66))
        val fx = if (o.schmuck == Schmuckrahmen.Keiner) rand else (b - s.normal.breite(fuss, 7f)) / 2
        text(fuss, s.normal, 7f, fx, rand * if (o.schmuck == Schmuckrahmen.Keiner) 0.6f else 0.85f)
    }
    return doc to TafelInfo(alle.size, (b / 72f * 2.54f).toInt(), (h / 72f * 2.54f).toInt(), seiteB = b * skala, seiteH = h * skala, einheit = 1f / skala)
}
