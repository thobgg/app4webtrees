package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.data.notizenOhnePaten
import de.bgghome.webtrees.nativ.data.ohneDoppelteAsso
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.WtClient
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
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination
import java.awt.Color
import java.awt.image.BufferedImage

/*
 * Karteikarten zur Tafel (E1, 28.09.2026): je Person eine A4-Seite hinter der Tafel - Foto, Namen, Lebensdaten und
 * weitere Fakten, Eltern, Ehen mit Kindern, Notizen, Quellen. Ein Klick auf den Kasten im Poster-PDF springt zur
 * Karte, "zurueck zur Tafel" wieder an die Stelle. Die Daten kommen aus dem Baum-Export (BaumSpeicher), bei aelteren
 * Servern Person fuer Person.
 */

/** Ausfuehrliche Daten fuer die Karten; Private und Personen ohne Kennung bleiben aussen vor. */
suspend fun kartenLaden(client: WtClient, tree: String, xrefs: Collection<String>): Map<String, IndividualDetail> {
    val gesucht = xrefs.filter { it.isNotEmpty() }.distinct()
    BaumSpeicher.holen(client, tree, gesucht.size)?.let { b -> return gesucht.mapNotNull { x -> b.detail(x)?.let { x to it } }.toMap() }
    return personenLaden(client, tree, gesucht)
}

private val REIHENFOLGE = listOf("BIRT", "CHR", "BAPM")
private val ENDE = listOf("DEAT", "BURI", "CREM")
private val OHNE = setOf("NAME", "SEX", "NOTE", "FAMS", "FAMC", "OBJE", "SOUR", "CHAN", "_UID", "RIN", "REFN")

private fun leben(p: Person) = p.lifespan.ifBlank { listOfNotNull(p.birth?.date?.year?.takeIf { it > 0 }, p.death?.date?.year?.takeIf { it > 0 }).joinToString("–") }
private fun mitLeben(p: Person) = p.name.ifBlank { "?" } + leben(p).takeIf(String::isNotBlank)?.let { " ($it)" }.orEmpty()

/** Schreibt fortlaufend auf A4-Seiten; reicht eine Seite nicht, geht es auf der naechsten weiter. */
private class KartenSchreiber(val doc: PDDocument, val s: Schriften, val fuss: String) {
    val format = PDRectangle.A4
    val rand = 50f
    val breite = format.width - 2 * rand
    lateinit var seite: PDPage
    private var cs: PDPageContentStream? = null
    var y = 0f

    fun neueSeite(): PDPage {
        cs?.close()
        seite = PDPage(format).also { doc.addPage(it) }
        cs = PDPageContentStream(doc, seite)
        y = format.height - rand
        fussZeile()
        return seite
    }

    fun stream() = cs!!

    fun schliessen() { cs?.close(); cs = null }

    private fun fussZeile() {
        val c = stream()
        c.setNonStrokingColor(Color(0x88, 0x88, 0x88))
        c.beginText(); c.setFont(s.normal, 7f); c.newLineAtOffset(rand, rand * 0.5f); c.schreibe(s.normal, 7f, fuss); c.endText()
    }

    private fun platz(h: Float) { if (y - h < rand + 14f) neueSeite() }

    /** Text umbrechen auf [max] Punkt Breite. */
    fun umbrechen(text: String, schrift: PDFont, g: Float, max: Float): List<String> {
        val zeilen = ArrayList<String>()
        var zeile = ""
        text.split(' ').forEach { wort ->
            val probe = if (zeile.isEmpty()) wort else "$zeile $wort"
            if (schrift.breite(probe, g) <= max || zeile.isEmpty()) zeile = probe else { zeilen += zeile; zeile = wort }
        }
        if (zeile.isNotEmpty()) zeilen += zeile
        return zeilen
    }

    fun text(text: String, schrift: PDFont = s.normal, g: Float = 10f, einzug: Float = 0f, farbe: Color = Color(0x22, 0x22, 0x22), max: Float = breite - einzug) {
        umbrechen(text, schrift, g, max).forEach { z ->
            platz(g * 1.35f)
            y -= g * 1.35f
            val c = stream()
            c.setNonStrokingColor(farbe)
            c.beginText(); c.setFont(schrift, g); c.newLineAtOffset(rand + einzug, y); c.schreibe(schrift, g, z); c.endText()
        }
    }

    fun abschnitt(titel: String) {
        platz(40f)
        y -= 12f
        text(titel, s.fett, 11f, farbe = Color(0x1F, 0x3A, 0x6B))
        y -= 3f
        val c = stream()
        c.setStrokingColor(Color(0xCC, 0xCC, 0xCC)); c.setLineWidth(0.6f); c.moveTo(rand, y); c.lineTo(rand + breite, y); c.stroke()
        y -= 4f
    }
}

private fun ereignisZeile(f: FactJson): String {
    val wert = f.value.takeIf { it.isNotBlank() && f.tag !in REIHENFOLGE && f.tag !in ENDE }
    return listOfNotNull(wert, buchDatum(f.date).takeIf(String::isNotBlank), f.place?.name?.takeIf(String::isNotBlank)).joinToString(", ")
}

/**
 * Haengt je Person eine Karte an [doc] an und gibt die Seite (Index) je xref zurueck. [personen]: in dieser Folge.
 * [zurueck]: Ziel "zurueck zur Tafel" je xref (Seite, Punkt oben links der Karte in PDF-Koordinaten).
 */
internal fun karteikartenAnhaengen(
    doc: PDDocument, personen: List<Person>, details: Map<String, IndividualDetail>, bilder: (Person) -> BufferedImage?,
    zurueck: Map<String, Triple<Int, Float, Float>>, fuss: String,
): Map<String, Int> {
    val s = Schriften(doc)
    val w = KartenSchreiber(doc, s, fuss)
    val seiten = HashMap<String, Int>()
    personen.forEach { p ->
        val det = details[p.xref] ?: return@forEach
        val erste = w.neueSeite()
        seiten[p.xref] = doc.numberOfPages - 1
        // Kopf: Name, Lebensdaten, rechts das Foto
        val foto = bilder(p)
        val fotoB = 92f; val fotoH = 115f
        val textMax = if (foto != null) w.breite - fotoB - 14f else w.breite
        if (foto != null) {
            val img = JPEGFactory.createFromImage(doc, foto, 0.88f)
            val k = minOf(fotoB / img.width, fotoH / img.height)
            val iw = img.width * k; val ih = img.height * k
            w.stream().drawImage(img, w.rand + w.breite - iw, w.format.height - w.rand - ih, iw, ih)
        }
        w.text(p.name.ifBlank { "?" }, s.fett, 18f, max = textMax)
        leben(p).takeIf(String::isNotBlank)?.let { w.text(it, s.normal, 11f, farbe = Color(0x55, 0x55, 0x55), max = textMax) }
        val fakten = det.facts.filter { it.known }.ohneDoppelteAsso()
        fakten.filter { it.tag == "NAME" && it.value.isNotBlank() && it.value.replace("/", "").trim() != p.name }.forEach {
            w.text(listOfNotNull(it.type.takeIf(String::isNotBlank), it.value.replace("/", "").trim()).joinToString(": "), s.normal, 9.5f, farbe = Color(0x55, 0x55, 0x55), max = textMax)
        }
        if (foto != null) w.y = minOf(w.y, w.format.height - w.rand - fotoH - 4f)
        // Lebensdaten und weitere Fakten in fester Folge
        val ereignisse = fakten.filter { it.tag in REIHENFOLGE }.sortedBy { REIHENFOLGE.indexOf(it.tag) } +
            fakten.filter { it.tag !in REIHENFOLGE && it.tag !in ENDE && it.tag !in OHNE && (it.date != null || it.place != null || it.value.isNotBlank()) } +
            fakten.filter { it.tag in ENDE }.sortedBy { ENDE.indexOf(it.tag) }
        if (ereignisse.isNotEmpty()) {
            w.abschnitt(Texte.t(Res.string.desk_card_facts))
            ereignisse.forEach { f ->
                val rest = ereignisZeile(f)
                w.text(f.label.ifBlank { f.tag } + if (rest.isNotBlank()) ": $rest" else "")
                f.notizenOhnePaten().filter(String::isNotBlank).forEach { n -> w.text(n.replace(Regex("\\s*\\n\\s*"), " "), g = 9f, einzug = 14f, farbe = Color(0x55, 0x55, 0x55)) }
                patenZeilenText(f).forEach { n -> w.text(n, g = 9f, einzug = 14f, farbe = Color(0x55, 0x55, 0x55)) }
            }
        }
        // Eltern
        val eltern = det.parentFamilies.firstOrNull()
        if (eltern != null && (eltern.husband != null || eltern.wife != null)) {
            w.abschnitt(Texte.t(Res.string.desk_card_parents))
            eltern.husband?.let { w.text(Texte.t(Res.string.desk_card_father) + ": " + mitLeben(it)) }
            eltern.wife?.let { w.text(Texte.t(Res.string.desk_card_mother) + ": " + mitLeben(it)) }
        }
        // Ehen mit Kindern
        if (det.spouseFamilies.isNotEmpty()) {
            w.abschnitt(Texte.t(Res.string.desk_card_families))
            det.spouseFamilies.forEach { f ->
                // Alle Heiraten (standesamtlich und kirchlich), sonst die Kurzform der Familie
                val heiraten = f.facts.filter { it.tag == "MARR" }
                val heirat = if (heiraten.isEmpty()) f.marriage?.let { e -> listOf(buchDatum(e.date), e.place?.name.orEmpty()).filter(String::isNotBlank).joinToString(", ") }.orEmpty()
                    else heiraten.joinToString("; ") { h -> listOf(buchDatum(h.date), h.place?.name.orEmpty()).filter(String::isNotBlank).joinToString(", ") + if (heiraten.size > 1) heiratsartKlammer(h) else "" }
                w.text("⚭ " + (f.spouse?.let(::mitLeben) ?: "?") + heirat.takeIf(String::isNotBlank)?.let { " – $it" }.orEmpty(), s.fett, 10f)
                f.children.forEach { k -> w.text(mitLeben(k), g = 9.5f, einzug = 14f) }
            }
        }
        // Notizen und Quellen
        val notizen = fakten.filter { it.tag == "NOTE" && it.value.isNotBlank() }.map { it.value }
        if (notizen.isNotEmpty()) {
            w.abschnitt(Texte.t(Res.string.desk_card_notes))
            notizen.flatMap { it.split(Regex("\\n\\s*\\n")) }.map { it.trim().replace(Regex("\\s*\\n\\s*"), " ") }.filter(String::isNotBlank)
                .forEach { w.text(it, g = 9.5f); w.y -= 3f }
        }
        val quellen = fakten.flatMap { it.sources }.map { it.title }.filter(String::isNotBlank).distinct()
        if (quellen.isNotEmpty()) {
            w.abschnitt(Texte.t(Res.string.desk_card_sources))
            quellen.forEach { w.text("• $it", g = 9f) }
        }
        // Zurueck zur Tafel: Link oben rechts unter dem Foto bzw. am Kopf
        zurueck[p.xref]?.let { (zs, zx, zy) ->
            val t = Texte.t(Res.string.desk_card_back)
            val g = 8.5f; val tb = s.normal.breite(t, g)
            val x = w.rand + w.breite - tb; val y0 = w.rand * 0.5f
            val c = w.stream()
            c.setNonStrokingColor(Color(0x1F, 0x3A, 0x6B)); c.beginText(); c.setFont(s.normal, g); c.newLineAtOffset(x, y0); c.schreibe(s.normal, g, t); c.endText()
            erste.annotations.add(link(PDRectangle(x, y0 - 2f, tb, g + 4f), PDPageXYZDestination().apply { page = doc.getPage(zs); left = zx.toInt(); top = zy.toInt() }))
        }
    }
    w.schliessen()
    return seiten
}

internal fun link(rect: PDRectangle, ziel: PDPageDestination) = PDAnnotationLink().apply {
    rectangle = rect
    borderStyle = PDBorderStyleDictionary().apply { width = 0f }
    action = PDActionGoTo().apply { destination = ziel }
}

/** Auf der Tafelseite (Index 0) ueber jeder Karte einen Link zu ihrer Karteikarte. */
internal fun kartenLinks(doc: PDDocument, info: TafelInfo, seiten: Map<String, Int>) = kartenLinksAuf(doc, 0, info.karten, info.seiteH, seiten)

/** Links auf Seite [seite]: [karten] (y von oben, Seitenhoehe [hoehe]) fuehren zu ihren Karteikarten. */
internal fun kartenLinksAuf(doc: PDDocument, seite: Int, karten: List<KartenOrt>, hoehe: Float, seiten: Map<String, Int>) {
    val tafel = doc.getPage(seite)
    karten.forEach { k ->
        val nr = seiten[k.person.xref] ?: return@forEach
        tafel.annotations.add(link(PDRectangle(k.x, hoehe - k.y - k.h, k.b, k.h), PDPageFitDestination().apply { page = doc.getPage(nr) }))
    }
}

/**
 * Karteikarten fuer mehrseitige Tafeln: [orte] je Seite (Index, Karten mit y von oben). Karten nach Namen hinten an,
 * Links von jeder Stelle zur Karte, "zurueck" an die erste Stelle der Person.
 */
internal fun karteikartenFuerSeiten(doc: PDDocument, orte: List<Pair<Int, List<KartenOrt>>>, details: Map<String, IndividualDetail>,
                                    bilder: (Person) -> BufferedImage?, fuss: String) {
    val alle = orte.flatMap { (s, k) -> k.map { s to it } }.filter { it.second.person.xref.isNotEmpty() && !it.second.person.isPrivate }
    val personen = alle.map { it.second.person }.distinctBy { it.xref }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { registerName(it) })
    val zurueck = alle.groupBy { it.second.person.xref }.mapValues { (_, v) ->
        val (s, k) = v.first(); Triple(s, k.x, doc.getPage(s).mediaBox.height - k.y)
    }
    val seiten = karteikartenAnhaengen(doc, personen, details, bilder, zurueck, fuss)
    orte.forEach { (s, k) -> kartenLinksAuf(doc, s, k, doc.getPage(s).mediaBox.height, seiten) }
}
