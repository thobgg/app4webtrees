package de.bgghome.webtrees.nativ.desk

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Zeichen, die die Hauptschrift nicht hat (Issue 10): unter Windows fehlt das Heiratszeichen in Segoe UI, Arial und Times.
 * Liberation Sans steht hier fuer diese Schriften - sie hat ∞, aber kein ⚭. Ohne Liberation oder DejaVu laeuft der Test leer.
 */
class PdfZeichenTest {
    private val ohneHeiratszeichen = File("/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf")
    private val ersatz = File("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")

    @Test
    fun heiratszeichenAusDerErsatzschrift() {
        if (!ohneHeiratszeichen.isFile || !ersatz.isFile) return
        PDDocument().use { doc ->
            val schrift = Schriften(doc).ladenAus(listOf(ohneHeiratszeichen.path))!!
            val stuecke = schrift.stuecke("⚭ 1890 Celle")
            assertEquals(listOf("⚭", " 1890 Celle"), stuecke.map { it.first })
            assertTrue(stuecke[0].second !== schrift && stuecke[1].second === schrift)
            assertTrue(schrift.breite("⚭ 1890", 10f) > schrift.breite(" 1890", 10f))
            val seite = PDPage(); doc.addPage(seite)
            PDPageContentStream(doc, seite).use { cs ->
                cs.beginText(); cs.setFont(schrift, 10f); cs.newLineAtOffset(50f, 700f)
                cs.schreibe(schrift, 10f, "Mohwinkel ⚭ Schulze"); cs.endText()
            }
            val text = PDFTextStripper().getText(doc)
            assertTrue("Mohwinkel ⚭ Schulze" in text, text)
        }
    }

    @Test
    fun ohneErsatzschriftWirdEsUnendlich() {
        if (!ohneHeiratszeichen.isFile) return
        PDDocument().use { doc ->
            // Nicht ueber Schriften geladen: kennt ihr Dokument nicht, also keine Ersatzschrift
            val schrift = PDType0Font.load(doc, ohneHeiratszeichen)
            assertEquals("∞ 1890", schrift.sicher("⚭ 1890"))
            assertEquals(listOf("∞ 1890"), schrift.stuecke("⚭ 1890").map { it.first })
            assertEquals("a?b", schrift.sicher("a一b"))
        }
    }
}
