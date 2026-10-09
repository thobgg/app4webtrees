package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.res.*
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDFont

/*
 * Legende der Tafel (C4, 28.09.2026): unten rechts, nur mit dem, was auf dieser Tafel vorkommt - die Farben (Linien,
 * Regeln, gefaerbte Zweige) und die Zeichen (* † ⚭, Kekule-Nummer, Doppelte, Gitter- und Seitenverweise).
 */

/** Wie ein Eintrag links gezeichnet wird: Farbfeld, Zeichen als Text, gelbes Schild (wie an doppelten Personen). */
internal enum class LegendenArt { Farbe, Zeichen, Schild, Nummer }

internal class LegendenEintrag(val art: LegendenArt, val zeichen: String, val text: String, val farbe: Int = 0)

internal fun legendenEintraege(a: TafelAnordnung): List<LegendenEintrag> = buildList {
    a.farbBedeutung.forEach { (f, t) -> add(LegendenEintrag(LegendenArt.Farbe, "", t, f)) }
    val knoten = a.gezeichnet.map { it.second.knoten }
    fun z(zeichen: String, text: StringResource) = add(LegendenEintrag(LegendenArt.Zeichen, zeichen, Texte.t(text)))
    z("*", Res.string.desk_legend_born)
    z("†", Res.string.desk_legend_died)
    if (knoten.any { it.partner.isNotEmpty() }) add(LegendenEintrag(LegendenArt.Zeichen, "⚭", Texte.t(Res.string.desk_legend_married)))
    if (a.nummerText.isNotEmpty()) when (a.o.nummernArt) {
        1 -> add(LegendenEintrag(LegendenArt.Nummer, "IV-12", Texte.t(Res.string.desk_legend_chronik)))
        2 -> add(LegendenEintrag(LegendenArt.Nummer, "1.2.3", Texte.t(Res.string.desk_legend_aboville)))
        else -> add(LegendenEintrag(LegendenArt.Nummer, "4", Texte.t(Res.string.desk_legend_kekule)))
    }
    if (a.nummern.isNotEmpty()) add(LegendenEintrag(LegendenArt.Schild, "1", Texte.t(Res.string.desk_legend_double)))
    if (knoten.any { it.verweis != null }) add(LegendenEintrag(LegendenArt.Schild, "= 8", Texte.t(Res.string.desk_legend_ref)))
    if (a.mitGitter && a.positionen.values.any { it.distinct().size > 1 }) add(LegendenEintrag(LegendenArt.Zeichen, "= C III", Texte.t(Res.string.desk_legend_grid)))
    if (knoten.any { it.hinweis?.startsWith("→") == true }) add(LegendenEintrag(LegendenArt.Zeichen, "→ S. 5", Texte.t(Res.string.desk_legend_page)))
}

/** Kleines gelbes Schild (Nummer fuer Doppelte, "= 8" fuer Ahnenschwund), Groesse aus der Rahmenbreite. */
internal fun PDPageContentStream.schildZeichnen(text: String, cx: Float, cy: Float, rahmen: Float, s: TafelSchriften, f: StilFarben) {
    val r = rahmen * 0.075f; val g = r * 1.3f
    val sw = maxOf(2 * r, s.fett.breite(text, g) + r)
    setNonStrokingColor(java.awt.Color(0xFF, 0xF3, 0x9A)); setStrokingColor(f.linie); setLineWidth(0.5f)
    rechteck(cx - sw / 2, cy - r, sw, 2 * r, r); fillAndStroke()
    setNonStrokingColor(f.text)
    beginText(); setFont(s.fett, g); newLineAtOffset(cx - s.fett.breite(text, g) / 2, cy - g * 0.35f); schreibe(s.fett, g, text); endText()
}

/** Die Legende als Block ab (lx, oben) - y von oben, [py] rechnet in PDF-Koordinaten um; spaltenweise von oben nach unten. */
internal fun PDPageContentStream.legendeZeichnen(
    eintraege: List<LegendenEintrag>, lm: LegendenMass, lx: Float, oben: Float, py: (Float) -> Float,
    s: TafelSchriften, f: StilFarben, heirat: String, rahmen: Float,
) {
    val g = lm.g
    fun text(t: String, schrift: PDFont, gr: Float, x: Float, y: Float) { beginText(); setFont(schrift, gr); newLineAtOffset(x, y); schreibe(schrift, gr, t); endText() }
    setNonStrokingColor(f.text)
    text(Texte.t(Res.string.desk_legend_title), s.fett, g * 1.1f, lx, py(oben + g * 1.2f))
    eintraege.forEachIndexed { i, e ->
        val x = lx + (i / lm.zeilen) * lm.spaltenB
        val cy = oben + lm.kopfH + (i % lm.zeilen) * lm.zeileH + lm.zeileH / 2
        when (e.art) {
            LegendenArt.Farbe -> {
                val k = KASTEN_FARBEN[e.farbe.coerceIn(0, KASTEN_FARBEN.size - 1)]
                setNonStrokingColor(k.fuellung); setStrokingColor(k.rahmen); setLineWidth(0.8f)
                rechteck(x, py(cy + g * 0.6f), lm.feldB * 0.75f, g * 1.2f, g * 0.2f); fillAndStroke()
            }
            LegendenArt.Schild -> schildZeichnen(e.zeichen, x + lm.feldB * 0.35f, py(cy), rahmen, s, f)
            LegendenArt.Nummer -> { setNonStrokingColor(f.linie); text(e.zeichen, s.normal, g * 0.85f, x + lm.feldB * 0.3f, py(cy + g * 0.3f)) }
            LegendenArt.Zeichen -> {
                // Heiratszeichen wie in den Kaesten (ohne ⚭ in der Schrift "oo")
                val zeichen = if (e.zeichen == "⚭") heirat else e.zeichen
                val zg = if (zeichen.length > 2) g * 0.9f else g * 1.2f
                setNonStrokingColor(f.text); text(zeichen, s.normal, zg, x + (lm.feldB * 0.75f - s.normal.breite(zeichen, zg)) / 2, py(cy + zg * 0.35f))
            }
        }
        setNonStrokingColor(f.text)
        text(e.text, s.normal, g, x + lm.feldB, py(cy + g * 0.35f))
    }
}

/** Zeile unter dem Titel: freier Text und "zusammengestellt von". */
internal fun untertitelText(o: TafelOptionen): String = listOfNotNull(o.untertitel.trim().takeIf(String::isNotBlank),
    o.ersteller.trim().takeIf(String::isNotBlank)?.let { Texte.t(Res.string.desk_legend_by, it) }).joinToString("  ·  ")

private typealias StringResource = org.jetbrains.compose.resources.StringResource

/** Masse der Legende: Schrift, Spalten, Zeilen; [b]/[h] in Punkt (0, wenn keine Legende). */
internal class LegendenMass(eintraege: List<LegendenEintrag>, schrift: PDFont, fett: PDFont, rahmen: Float, verfuegbar: Float) {
    val g = maxOf(9f, rahmen * 0.09f)
    val zeileH = g * 1.7f
    val feldB = g * 3.4f
    val spaltenB = feldB + (eintraege.maxOfOrNull { schrift.breite(it.text, g) } ?: 0f) + g * 2f
    /** Hoechstens vier Spalten: die Legende bleibt ein kompakter Block, auch unter sehr breiten Tafeln. */
    val spalten = if (eintraege.isEmpty()) 0 else (verfuegbar / spaltenB).toInt().coerceIn(1, minOf(4, eintraege.size))
    val zeilen = if (spalten == 0) 0 else (eintraege.size + spalten - 1) / spalten
    val kopfH = g * 2.2f
    val b = maxOf(spalten * spaltenB, if (eintraege.isEmpty()) 0f else fett.breite(Texte.t(Res.string.desk_legend_title), g * 1.1f))
    val h = if (eintraege.isEmpty()) 0f else kopfH + zeilen * zeileH + g
}
