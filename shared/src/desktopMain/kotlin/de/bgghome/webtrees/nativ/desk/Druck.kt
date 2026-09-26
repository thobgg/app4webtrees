package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.halfSiblings
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
 * Druck und PDF (Etappe 4, 23.09.2026): Personenblatt und Listen als PDF (die Tafeln: Stammtafel.kt) - gedruckt ueber den
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
    private fun laden(namen: List<String>): PDFont? = namen.map(::File).firstOrNull { it.isFile }?.let { f -> runCatching { PDType0Font.load(doc, f) }.getOrNull() }
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

/** Text, den die Schrift auch darstellen kann - unbekannte Zeichen werden zu "?". */
internal fun PDFont.sicher(text: String): String = buildString {
    text.forEach { c -> append(if (runCatching { encode(c.toString()) }.isSuccess) c else '?') }
}

internal fun PDFont.breite(text: String, groesse: Float) = getStringWidth(sicher(text)) / 1000f * groesse

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
            cs.showText(s.normal.sicher("$fuss · $seite")); cs.endText()
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
                stream!!.apply { beginText(); setFont(schrift, g); newLineAtOffset(sx, y); showText(schrift.sicher(tx)); endText() }
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
            stream!!.apply { beginText(); setFont(schrift, groesse); newLineAtOffset(if (i == 0) x else x + 8f, y); showText(schrift.sicher(t)); endText() }
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
    d.facts.filter { it.known && it.tag != "NOTE" }.forEach { f -> add(Zeile("${f.label}: ${ereignis(f)}".trimEnd(':', ' '), 1)) }
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
            val heirat = fam.marriage?.let { m -> listOfNotNull(m.date?.text?.takeIf(String::isNotBlank), m.place?.name?.takeIf(String::isNotBlank)).joinToString(", ") }.orEmpty()
            val wer = fam.spouse?.let(::kurz) ?: Texte.t(when (p.sex) { "M" -> Res.string.desk_unknown_mother; "F" -> Res.string.desk_unknown_father; else -> Res.string.desk_unknown_partner })
            add(Zeile(listOfNotNull(wer, heirat.takeIf(String::isNotBlank)?.let { "⚭ $it" }).joinToString("  "), 1, fett = true))
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
        (d.facts + d.spouseFamilies.flatMap { it.facts }).filter { it.tag != "NOTE" }.flatMap { f -> f.notes.filter(String::isNotBlank).map { f.label to it } }

/** Quellen einer Person: Titel und die Ereignisse, die sie belegen. */
fun quellenVon(d: IndividualDetail): List<Pair<String, List<String>>> =
    (d.facts + d.spouseFamilies.flatMap { it.facts }).flatMap { f -> f.sources.map { it.title.ifBlank { it.xref } to f.label } }
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
