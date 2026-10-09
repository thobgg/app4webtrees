package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.halfSiblings
import de.bgghome.webtrees.nativ.data.notizenOhnePaten
import de.bgghome.webtrees.nativ.data.ohneDoppelteAsso
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.res.*
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.printing.PDFPageable
import java.awt.Color
import java.awt.FileDialog
import java.awt.Frame
import java.awt.print.PrinterJob
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/*
 * Druck und PDF: Personenblatt und Listen als PDF (die Tafeln: Stammtafel.kt) - gedruckt ueber den
 * Druckdialog des Systems oder als Datei gespeichert. PDFBox ist schon da (PDF-Betrachter).
 */

/** Ein Textabschnitt einer Liste: Einrueckung, fett oder normal, Text. */
data class Zeile(
    val text: String, val einzug: Int = 0, val fett: Boolean = false, val gross: Boolean = false,
    /** Tabellenzeile: Spalten mit ihren Anteilen an der Breite (leer = Fliesstext). */
    val spalten: List<String> = emptyList(), val anteile: List<Float> = emptyList(),
)

/** Schriften: eine Systemschrift mit Umlauten und Akzenten, sonst Helvetica (dann ohne Sonderzeichen ausserhalb Latin-1). */
internal class Schriften(private val doc: PDDocument) {
    internal fun ladenAus(namen: List<String>): PDFont? = laden(namen)
    private fun laden(namen: List<String>): PDFont? =
        namen.map(::File).firstOrNull { it.isFile }?.let { f -> runCatching { PDType0Font.load(doc, f) }.getOrNull() }?.alsTeilVon(doc)
    val normal: PDFont = laden(listOf(
        "C:/Windows/Fonts/segoeui.ttf", "C:/Windows/Fonts/arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf", "/System/Library/Fonts/Supplemental/Arial.ttf",
    )) ?: PDType1Font(Standard14Fonts.FontName.HELVETICA)
    val fett: PDFont = laden(listOf(
        "C:/Windows/Fonts/segoeuib.ttf", "C:/Windows/Fonts/arialbd.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf", "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    )) ?: PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
}

/*
 * Ersatzschrift fuer Zeichen, die die Hauptschrift nicht hat: Segoe UI, Arial und Times kennen das Heiratszeichen nicht,
 * darum stand unter Windows in Listen, Personenblatt und Tafeln "?" statt dessen (Issue 10). Jede geladene Schrift merkt
 * sich ihr Dokument, die Ersatzschrift wird je Dokument einmal eingebettet - nur wenn ein Zeichen sie braucht.
 */
private val dokumentJe = java.util.Collections.synchronizedMap(java.util.WeakHashMap<PDFont, PDDocument>())
private val ersatzJe = java.util.Collections.synchronizedMap(java.util.WeakHashMap<PDDocument, Any>())
private val kannJe = java.util.Collections.synchronizedMap(java.util.WeakHashMap<PDFont, HashMap<Int, Boolean>>())
private val KEINE_ERSATZSCHRIFT = Any()
private val ERSATZ_DATEIEN = listOf(
    "C:/Windows/Fonts/seguisym.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/usr/share/fonts/truetype/noto/NotoSansSymbols-Regular.ttf", "/System/Library/Fonts/Apple Symbols.ttf",
)
/** Letzter Ausweg, wenn auch die Ersatzschrift fehlt: das Heiratszeichen als ∞ (in der Genealogie ebenso gebraeuchlich). */
private val ZEICHEN_ERSATZ = mapOf(0x26AD to "∞", 0x26AE to "∞")

/** Schrift als Teil von [doc] vormerken, damit sie fehlende Zeichen aus der Ersatzschrift holen kann. */
internal fun PDFont.alsTeilVon(doc: PDDocument): PDFont = also { dokumentJe[it] = doc }

private fun PDFont.kann(cp: Int): Boolean = kannJe.getOrPut(this) { HashMap() }.getOrPut(cp) {
    runCatching { encode(String(Character.toChars(cp))) }.isSuccess
}

private fun PDFont.ersatz(): PDFont? {
    val doc = dokumentJe[this] ?: return null
    return ersatzJe.getOrPut(doc) {
        ERSATZ_DATEIEN.map(::File).firstOrNull { it.isFile }?.let { f -> runCatching { PDType0Font.load(doc, f) }.getOrNull() } ?: KEINE_ERSATZSCHRIFT
    } as? PDFont
}

/** Text in Stuecke je Schrift: was die Schrift nicht hat, aus der Ersatzschrift, sonst ein Ersatzzeichen oder "?". */
internal fun PDFont.stuecke(text: String): List<Pair<String, PDFont>> {
    val aus = mutableListOf<Pair<String, PDFont>>()
    val sb = StringBuilder(); var aktuell: PDFont = this
    fun dazu(f: PDFont, teil: String) {
        if (f !== aktuell && sb.isNotEmpty()) { aus += sb.toString() to aktuell; sb.clear() }
        aktuell = f; sb.append(teil)
    }
    text.codePoints().forEach { cp ->
        val e by lazy { ersatz() }
        when {
            kann(cp) -> dazu(this, String(Character.toChars(cp)))
            e?.kann(cp) == true -> dazu(e!!, String(Character.toChars(cp)))
            else -> dazu(this, ZEICHEN_ERSATZ[cp]?.takeIf { z -> z.codePoints().allMatch { kann(it) } } ?: "?")
        }
    }
    if (sb.isNotEmpty()) aus += sb.toString() to aktuell
    return aus
}

/** Text nur in dieser Schrift (ohne Ersatzschrift) - unbekannte Zeichen werden zum Ersatzzeichen oder zu "?". */
internal fun PDFont.sicher(text: String): String = buildString {
    text.codePoints().forEach { cp ->
        append(if (kann(cp)) String(Character.toChars(cp)) else ZEICHEN_ERSATZ[cp]?.takeIf { z -> z.codePoints().allMatch { kann(it) } } ?: "?")
    }
}

internal fun PDFont.breite(text: String, groesse: Float) = stuecke(text).sumOf { (t, f) -> (f.getStringWidth(t) / 1000f * groesse).toDouble() }.toFloat()

/** Text ausgeben (innerhalb beginText/endText, nach setFont([schrift], [groesse])); wechselt fuer fehlende Zeichen die Schrift. */
internal fun PDPageContentStream.schreibe(schrift: PDFont, groesse: Float, text: String) {
    var aktuell = schrift
    for ((t, f) in schrift.stuecke(text)) {
        if (f !== aktuell) { setFont(f, groesse); aktuell = f }
        showText(t)
    }
    if (aktuell !== schrift) setFont(schrift, groesse)
}

/**
 * Fliesstext-Seiten im Hochformat: Titel, Zeilen mit Einzug, automatischer Umbruch und Seitenwechsel,
 * Fusszeile mit Programm, Datum und Baum.
 */
private class Textseiten(val doc: PDDocument, val fuss: String) {
    val s = Schriften(doc)
    private val rand = 48f
    private val format = PDRectangle.A4
    private var stream: PDPageContentStream? = null
    private var y = 0f
    private var seite = 0

    private fun neueSeite() {
        stream?.close()
        val page = PDPage(format); doc.addPage(page); seite++
        stream = PDPageContentStream(doc, page).also { cs ->
            cs.beginText(); cs.setFont(s.normal, 7.5f); cs.newLineAtOffset(rand, 24f)
            cs.schreibe(s.normal, 7.5f, "$fuss · $seite"); cs.endText()
        }
        y = format.height - rand
    }

    fun zeile(z: Zeile) {
        if (stream == null) neueSeite()
        val groesse = if (z.gross) 16f else 10f
        val schrift = if (z.fett || z.gross) s.fett else s.normal
        val x = rand + z.einzug * 16f
        val maxBreite = format.width - rand - x
        if (z.spalten.isNotEmpty()) {
            // Tabellenzeile: jede Spalte an ihrer festen Position, zu lange Texte gekuerzt
            val hoehe = groesse * 1.35f
            if (y - hoehe < rand) neueSeite()
            y -= hoehe
            val anteile = z.anteile.takeIf { it.size == z.spalten.size } ?: List(z.spalten.size) { 1f / z.spalten.size }
            var sx = x
            z.spalten.forEachIndexed { i, t ->
                val w = maxBreite * anteile[i]
                val (tx, g) = passend(schrift, t, groesse, w - 6f)
                stream!!.apply { beginText(); setFont(schrift, g); newLineAtOffset(sx, y); schreibe(schrift, g, tx); endText() }
                sx += w
            }
            return
        }
        // Woerter umbrechen
        val zeilen = mutableListOf<String>()
        var aktuell = ""
        z.text.split(' ').forEach { wort ->
            val probe = if (aktuell.isEmpty()) wort else "$aktuell $wort"
            if (schrift.breite(probe, groesse) > maxBreite && aktuell.isNotEmpty()) { zeilen += aktuell; aktuell = wort } else aktuell = probe
        }
        if (aktuell.isNotEmpty() || zeilen.isEmpty()) zeilen += aktuell
        val hoehe = groesse * 1.35f
        zeilen.forEachIndexed { i, t ->
            if (y - hoehe < rand) neueSeite()
            y -= hoehe
            stream!!.apply { beginText(); setFont(schrift, groesse); newLineAtOffset(if (i == 0) x else x + 8f, y); schreibe(schrift, groesse, t); endText() }
        }
        if (z.gross) y -= 6f
    }

    fun abstand(h: Float = 6f) { y -= h }
    fun schliessen() { stream?.close() }
}

/** Fusszeile: "Erstellt mit wtWin am 23.09.2026 · Die Medici". */
internal fun fusszeile(appName: String, baum: String): String =
    Texte.t(Res.string.desk_created, appName, LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), baum)

/** Liste als PDF-Dokument. */
fun listenPdf(zeilen: List<Zeile>, appName: String, baum: String): PDDocument {
    val doc = PDDocument()
    val t = Textseiten(doc, fusszeile(appName, baum))
    zeilen.forEach(t::zeile)
    t.schliessen()
    return doc
}

private fun ereignis(f: FactJson?): String = f?.let {
    listOfNotNull(it.date?.text?.takeIf(String::isNotBlank), it.place?.name?.takeIf(String::isNotBlank), it.value.takeIf { v -> v.isNotBlank() })
        .joinToString(", ")
}.orEmpty()

private fun kurz(p: Person) = p.name.ifBlank { "?" } + jahre(p).let { if (it.isNotEmpty()) " ($it)" else "" }

/** Personenblatt: alle Daten einer Person auf einer (oder mehr) Seiten. */
fun personenblattZeilen(d: IndividualDetail): List<Zeile> = buildList {
    val p = d.person
    add(Zeile(p.name, gross = true))
    listOf(p.lifespan, d.relationship.replaceFirstChar { it.uppercase() }).filter(String::isNotBlank).joinToString(" · ").takeIf(String::isNotBlank)?.let { add(Zeile(it)) }
    add(Zeile(""))
    add(Zeile(Texte.t(Res.string.desk_tab_data), fett = true))
    d.facts.filter { it.known && it.tag != "NOTE" }.ohneDoppelteAsso().forEach { f ->
        add(Zeile("${f.label}: ${ereignis(f)}".trimEnd(':', ' '), 1))
        patenZeilenText(f).forEach { add(Zeile(it, 2)) }
    }
    d.parentFamilies.firstOrNull()?.let { fam ->
        add(Zeile("")); add(Zeile(Texte.t(Res.string.desk_tab_parents), fett = true))
        fam.husband?.let { add(Zeile("${Texte.t(Res.string.rel_father)}: ${kurz(it)}", 1)) }
        fam.wife?.let { add(Zeile("${Texte.t(Res.string.rel_mother)}: ${kurz(it)}", 1)) }
        d.parentFamilies.flatMap { it.children }.filter { it.xref != p.xref }.distinctBy { it.xref }.forEach { add(Zeile("${Texte.t(Res.string.desk_siblings)}: ${kurz(it)}", 1)) }
        d.halfSiblings().forEach { add(Zeile("${Texte.t(if (it.paternal) Res.string.half_siblings_paternal else Res.string.half_siblings_maternal)}: ${kurz(it.person)}", 1)) }
    }
    if (d.spouseFamilies.isNotEmpty()) {
        add(Zeile("")); add(Zeile(Texte.t(Res.string.desk_tab_partners), fett = true))
        d.spouseFamilies.forEach { fam ->
            val heiraten = fam.facts.filter { it.tag == "MARR" }
            val heirat = if (heiraten.isEmpty()) fam.marriage?.let { m -> listOfNotNull(m.date?.text?.takeIf(String::isNotBlank), m.place?.name?.takeIf(String::isNotBlank)).joinToString(", ") }.orEmpty()
                else heiraten.joinToString("; ") { h -> listOfNotNull(h.date?.text?.takeIf(String::isNotBlank), h.place?.name?.takeIf(String::isNotBlank)).joinToString(", ") + if (heiraten.size > 1) heiratsartKlammer(h) else "" }
            val wer = fam.spouse?.let(::kurz) ?: Texte.t(when (p.sex) { "M" -> Res.string.desk_unknown_mother; "F" -> Res.string.desk_unknown_father; else -> Res.string.desk_unknown_partner })
            add(Zeile(listOfNotNull(wer, heirat.takeIf(String::isNotBlank)?.let { "⚭ $it" }).joinToString("  "), 1, fett = true))
            heiraten.forEach { h -> patenZeilenText(h).forEach { add(Zeile(it, 2)) } }
            fam.children.forEach { add(Zeile(kurz(it), 2)) }
        }
    }
    val notizen = notizenVon(d)
    if (notizen.isNotEmpty()) { add(Zeile("")); add(Zeile(Texte.t(Res.string.desk_tab_notes), fett = true)); notizen.forEach { (wo, text) -> add(Zeile(if (wo.isBlank()) text else "$wo: $text", 1)) } }
    val quellen = quellenVon(d)
    if (quellen.isNotEmpty()) { add(Zeile("")); add(Zeile(Texte.t(Res.string.desk_tab_sources), fett = true)); quellen.forEach { (titel, wo) -> add(Zeile("$titel – ${wo.joinToString(", ")}", 1)) } }
}

/** Notizen einer Person: eigene NOTE-Eintraege und die Notizen an Ereignissen, jeweils mit dem Ereignis davor. */
fun notizenVon(d: IndividualDetail): List<Pair<String, String>> =
    d.facts.filter { it.tag == "NOTE" && it.value.isNotBlank() }.map { "" to it.value } +
        (d.facts + d.spouseFamilies.flatMap { it.facts }).filter { it.tag != "NOTE" }.flatMap { f -> f.notizenOhnePaten().filter(String::isNotBlank).map { f.label to it } }

/** Quellen einer Person: Titel und die Ereignisse, die sie belegen (mit Seitenangabe in Klammern). */
fun quellenVon(d: IndividualDetail): List<Pair<String, List<String>>> =
    (d.facts + d.spouseFamilies.flatMap { it.facts }).flatMap { f ->
        f.sources.map { q -> q.title.ifBlank { q.xref } to f.label + q.page.replace('\n', ' ').trim().let { if (it.isEmpty()) "" else " ($it)" } }
    }
        .groupBy({ it.first }, { it.second }).map { (titel, wo) -> titel to wo.distinct() }

/** Den Druckdialog des Systems zeigen und drucken; das Dokument wird danach geschlossen. */
fun drucken(doc: PDDocument, titel: String) {
    val job = PrinterJob.getPrinterJob()
    job.jobName = titel
    job.setPageable(PDFPageable(doc))
    if (job.printDialog()) {
        Thread { runCatching { job.print() }; doc.close() }.start()
    } else doc.close()
}

/**
 * Die erste Seite als PNG (E3, 28.09.2026): 150 dpi im echten Mass, die lange Seite hoechstens 8000 Pixel (sonst wird
 * es fuer den Speicher zu viel). Fuer Webseiten, E-Mail und Vereinsblatt.
 */
fun alsPng(original: PDDocument, vorschlag: String) {
    val dialog = FileDialog(null as Frame?, Texte.t(Res.string.desk_save_png), FileDialog.SAVE).apply {
        file = vorschlag.replace(Regex("[\\/:*?\"<>|]"), "_") + ".png"
        isVisible = true
    }
    val name = dialog.file ?: run { original.close(); return }
    val ziel = File(dialog.directory, if (name.endsWith(".png", true)) name else "$name.png")
    // Einmal speichern und laden, damit die Schriften eingebettet sind (sonst Ersatzschrift im Bild)
    eingebettet(original).use { doc ->
        val seite = doc.getPage(0)
        val lang = maxOf(seite.mediaBox.width, seite.mediaBox.height)
        val skala = minOf(150f / 72f * seite.userUnit, 8000f / lang)
        javax.imageio.ImageIO.write(org.apache.pdfbox.rendering.PDFRenderer(doc).renderImage(0, skala), "png", ziel)
    }
    original.close()
    runCatching { java.awt.Desktop.getDesktop().open(ziel) }
}

/** Speichern-Dialog, PDF schreiben, mit dem Standardprogramm oeffnen. */
fun alsPdf(doc: PDDocument, vorschlag: String) {
    val dialog = FileDialog(null as Frame?, Texte.t(Res.string.desk_save_pdf), FileDialog.SAVE).apply {
        file = vorschlag.replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".pdf"
        isVisible = true
    }
    val name = dialog.file
    if (name == null) { doc.close(); return }
    val ziel = File(dialog.directory, if (name.endsWith(".pdf", true)) name else "$name.pdf")
    doc.use { it.save(ziel) }
    runCatching { java.awt.Desktop.getDesktop().open(ziel) }
}

