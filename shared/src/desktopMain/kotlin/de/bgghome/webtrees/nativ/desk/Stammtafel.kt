package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.res.*
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
 * Stammtafel und Ahnentafel (25.09.2026, Wunsch Thomas: "Apps brauchen schoene Tafeln"): alle Nachfahren oder
 * Vorfahren einer Person als ein grosses Blatt - jede Generation eine Reihe, ueber jedem Kasten Portraet oder
 * Silhouette. Die Ahnentafel ist dieselbe Zeichnung, gespiegelt: Ausgangsperson unten. Ein Zeichenkern fuer Vorschau und Ausgabe: das Layout rechnet in Punkt (1/72 Zoll), daraus
 * entsteht EIN PDF-Blatt; die Vorschau im Fenster ist dieses Blatt, gedruckt wird es verkleinert auf ein
 * Blatt oder in Originalgroesse auf mehrere A4-Blaetter zum Zusammenkleben.
 */

/**
 * Eine Person der Tafel mit den Personen der naechsten Reihe: bei der Stammtafel ihre Kinder (aller
 * Partnerschaften, in Familienfolge), bei der Ahnentafel Vater und Mutter. [nummer]: Kekule-Nummer (Ahnentafel).
 * [partner]: Ehepartner, im Kasten genannt. [verweis]: Ahnenschwund - die Person steht schon unter dieser Nummer,
 * ihre Vorfahren dort. [hinweis]: kleiner Text ueber dem Kasten ("→ S. 3"). [aufLinie]: gehoert zur Linie
 * (Stammlinie, Mutterstamm, aeltester Vorfahr).
 */
class TafelPerson(
    val person: Person, val kinder: List<TafelPerson>, val nummer: Long? = null,
    val partner: List<Person> = emptyList(), val verweis: Long? = null, val hinweis: String? = null, val aufLinie: Boolean = false,
    /** Ahnentafel: Geschwister neben der Person, an derselben Elternlinie - beim Vater links, sonst rechts. */
    val geschwister: List<Person> = emptyList(), val geschwisterLinks: Boolean = false,
)

/**
 * Stammtafel: Ausgangsperson oben, Nachfahren darunter. Ahnentafel: Ausgangsperson unten, Vorfahren darueber.
 * Sanduhr: beides. Linien (Stammlinie, Mutterstamm, aeltester Vorfahr): eine Folge von Elternpaaren.
 * AhnenSeiten: die Ahnentafel in Stuecken zu vier Generationen je A4-Seite. Faecher und Kreis: Ringe um den
 * Probanden (Faechertafel.kt).
 */
enum class TafelArt { Ahnen, AhnenSeiten, Faecher, Kreis, Stammlinie, Mutterstamm, Aeltester, Stamm, Sanduhr }

enum class TafelStil { Pergament, Klassisch, Farbig, Schwarzweiss }

data class TafelOptionen(
    val generationen: Int = 6,
    /** Kekule-Nummern an den Kaesten (Tafeln mit Vorfahren). */
    val nummern: Boolean = true,
    val stil: TafelStil = TafelStil.Pergament,
    val rahmenMm: Int = 30,
    val bilder: Boolean = true,
    val titel: String = "",
    /** Nur Ahnentafel: Ausgangsperson oben, die Vorfahren darunter. */
    val ausgangOben: Boolean = false,
    /** Nur Sanduhr: Generationen der Nachfahren ([generationen] zaehlt dort die Vorfahren). */
    val nachfahren: Int = 3,
    /** Stammtafel, Sanduhr: nur die Kinder der Soehne weiterverfolgen. */
    val namenstraeger: Boolean = false,
    /** Stammtafel, Sanduhr: Ehepartner im Kasten. Linien: beide Eltern statt nur der Linie. */
    val partner: Boolean = true,
    val orte: Boolean = false,
    val volleDaten: Boolean = false,
    /** Generationen als Spalten von links nach rechts statt als Reihen. */
    val waagerecht: Boolean = false,
    /** Nur Ahnentafel: 0 keine Geschwister, 1 die des Probanden, 2 die aller Vorfahren. */
    val geschwister: Int = 0,
    /** Grosse Tafeln lesbar machen: Gitter am Rand (Spalten A, B ... / Generationen I, II ...), Personenverzeichnis
     * mit Gitterposition auf eigenen A4-Seiten, doppelte Personen mit farbiger Kurve verbinden. */
    val gitter: Boolean = false,
    val verzeichnis: Boolean = false,
    val kurven: Boolean = false,
)

/** Was eine Tafel zeichnet: Vorfahren nach oben, Nachfahren nach unten (je nach Art einer oder beide Teile). */
class TafelInhalt(val vorfahren: TafelPerson? = null, val nachfahren: TafelPerson? = null, val linie: Boolean = false)

/** Ein platzierter Kasten: Mitte waagerecht, Oberkante des Bildes, Ebene (0 = Ausgangsperson). */
class TafelPlatz(val knoten: TafelPerson, val mitteX: Float, val obenY: Float, val ebene: Int, val eltern: TafelPlatz?)

/**
 * Masse eines Kastens in Punkt, alle aus der Rahmenbreite abgeleitet. [zusatz]: Zeilen fuer Orte und Partner.
 * Das Layout rechnet in zwei Achsen: Generation (Reihe) und Geschwister (nebeneinander). [waagerecht]: die
 * Generationen liegen als Spalten nebeneinander, das Bild steht links neben dem Text statt darueber.
 */
class TafelMasse(val rahmen: Float, val bilder: Boolean, zusatz: Int = 0, val waagerecht: Boolean = false) {
    val bild = if (bilder) rahmen * 0.78f else 0f
    val bildAbstand = if (bilder) rahmen * 0.06f else 0f
    val schriftKlein = rahmen * 0.085f
    val schriftName = rahmen * 0.12f
    val kastenH = schriftKlein * (3 + zusatz) * 1.3f + schriftName * 1.3f + rahmen * 0.12f
    val spalt = maxOf(rahmen * 0.12f, 6f)
    val verbinder = maxOf(rahmen * 0.36f, 18f)
    /** Eine Karte (Bild und Kasten) in Blattrichtung. */
    val karteB = if (waagerecht) bild + bildAbstand + rahmen else rahmen
    val karteH = if (waagerecht) maxOf(bild, kastenH) else bild + bildAbstand + kastenH
    /** Laenge der Karte entlang der Generationen und entlang der Geschwister. */
    val laengeG = if (waagerecht) karteB else karteH
    val laengeQ = if (waagerecht) karteH else karteB
    val ebeneH = laengeG + verbinder
    val slot = laengeQ + spalt
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
    // Geschwister verbreitern die Person nach einer Seite (Mitte der aeussersten Geschwisterkarte)
    fun lw(k: TafelPerson) = if (k.geschwisterLinks) k.geschwister.size * masse.slot else 0f
    fun rw(k: TafelPerson) = if (k.geschwisterLinks) 0f else k.geschwister.size * masse.slot
    fun rechnen(k: TafelPerson): Teil {
        val kinder = k.kinder.map(::rechnen)
        if (kinder.isEmpty()) return Teil(emptyList(), mutableListOf(-lw(k)), mutableListOf(rw(k))).also { teile[k] = it }
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
        val teil = Teil(versatz.map { it - mitte }, mutableListOf(-lw(k)).apply { addAll(accL.map { it - mitte }) }, mutableListOf(rw(k)).apply { addAll(accR.map { it - mitte }) })
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

/**
 * Linie (Stammlinie, Mutterstamm, aeltester Vorfahr): die Personen der Linie senkrecht uebereinander, der andere
 * Elternteil daneben - der Vater links, die Mutter rechts. Kein Schraegwandern wie bei zentrierten Paaren.
 */
fun linienLayout(wurzel: TafelPerson, masse: TafelMasse): TafelLayout {
    class Roh(val k: TafelPerson, val x: Float, val ebene: Int, val eltern: Int?)
    val roh = mutableListOf<Roh>()
    var k: TafelPerson? = wurzel; var ebene = 0; var eltern: Int? = null
    while (k != null) {
        roh += Roh(k, 0f, ebene, eltern)
        val hier = roh.size - 1
        val linie = k.kinder.firstOrNull { it.aufLinie }
        k.kinder.forEachIndexed { i, andere ->
            if (andere === linie) return@forEachIndexed
            val links = if (linie != null) i < k.kinder.indexOf(linie) else andere.person.sex == "M"
            roh += Roh(andere, if (links) -masse.slot else masse.slot, ebene + 1, hier)
        }
        k = linie; ebene++; eltern = hier
    }
    val links = roh.minOf { it.x }
    val plaetze = ArrayList<TafelPlatz>()
    roh.forEach { r -> plaetze += TafelPlatz(r.k, r.x - links + masse.slot / 2, r.ebene * masse.ebeneH, r.ebene, r.eltern?.let { plaetze[it] }) }
    val breite = roh.maxOf { it.x } - links + masse.slot
    return TafelLayout(plaetze, breite, (roh.maxOf { it.ebene } + 1) * masse.ebeneH - masse.verbinder, masse)
}

/** Wer mehrfach vorkommt (Nachfahren, die untereinander geheiratet haben), bekommt auf der Tafel eine Nummer. */
fun doppelteNummern(plaetze: List<TafelPlatz>): Map<String, Int> =
    plaetze.filter { it.knoten.verweis == null }.groupBy { it.knoten.person.xref }.filter { it.value.size > 1 && it.key.isNotEmpty() }.keys.withIndex().associate { (i, x) -> x to i + 1 }

internal class StilFarben(
    val hintergrundOben: Color, val hintergrundUnten: Color, val titel: Color, val linie: Color, val text: Color,
    val rahmenBreite: Float, val rund: Boolean, val grau: Boolean,
    val fuellung: (String) -> Color, val rahmen: (String) -> Color,
)

internal fun farben(stil: TafelStil): StilFarben = when (stil) {
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
internal class TafelSchriften(doc: PDDocument, stil: TafelStil) {
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
internal fun PDPageContentStream.verlauf(b: Float, h: Float, oben: Color, unten: Color) {
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

internal fun grau(b: BufferedImage): BufferedImage = ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null).filter(b, null)

/** Text in eine Breite zwingen: erst kleiner (bis 70 %), dann kuerzen. */
internal fun passend(schrift: PDFont, text: String, groesse: Float, breite: Float): Pair<String, Float> {
    var g = groesse
    while (schrift.breite(text, g) > breite && g > groesse * 0.7f) g -= groesse * 0.05f
    var t = text
    while (schrift.breite(t, g) > breite && t.length > 3) t = t.dropLast(2) + "…"
    return t to g
}

/** Groesste Seitenlaenge eines PDF-Blatts (200 Zoll); groessere Tafeln werden verkleinert. */
internal const val PDF_MAX = 14400f

/** Masse des Blatts in Zentimetern und die Personenzahl, fuer die Anzeige im Fenster. [seiten] > 0: A4-Seiten. */
class TafelInfo(val personen: Int, val breiteCm: Int, val hoeheCm: Int, val seiten: Int = 0)

/** Eine Zeile im Kasten. */
private class KastenZeile(val text: String, val schrift: PDFont, val groesse: Float)

/** Ort gekuerzt auf den ersten Teil ("Celle, Niedersachsen, Deutschland" -> "Celle"). */
private fun ort(e: de.bgghome.webtrees.nativ.api.EventJson?): String =
    e?.place?.name?.substringBefore(',')?.trim().orEmpty()

private fun datum(e: de.bgghome.webtrees.nativ.api.EventJson?, voll: Boolean): String =
    e?.date?.let { d -> if (voll) d.text.ifBlank { d.year.takeIf { it > 0 }?.toString().orEmpty() } else d.year.takeIf { it > 0 }?.toString().orEmpty() }.orEmpty()

/** Zusatzzeilen, die die Kaesten dieser Tafel brauchen: je ein Ort unter Geburt und Tod, bis zu zwei Partner. */
private fun zusatzZeilen(knoten: List<TafelPerson>, o: TafelOptionen): Int =
    (if (o.orte) 2 else 0) + knoten.maxOf { it.partner.size }.coerceAtMost(2)

private fun alleKnoten(k: TafelPerson): List<TafelPerson> = listOf(k) + k.kinder.flatMap(::alleKnoten)

/**
 * Eine Tafel als PDF mit einem Blatt. [bilder] liefert je Person ihr Portraet (null = Silhouette); [privat] ist
 * der Text fuer Personen, die der Server nicht zeigt. Vorfahren wachsen nach oben, Nachfahren nach unten; hat die
 * Tafel beides (Sanduhr), steht die Ausgangsperson einmal in der Mitte.
 */
fun tafelPdf(
    inhalt: TafelInhalt, o: TafelOptionen, bilder: (Person) -> BufferedImage?, privat: String, fuss: String,
): Pair<PDDocument, TafelInfo> {
    val knoten = listOfNotNull(inhalt.vorfahren, inhalt.nachfahren).flatMap(::alleKnoten)
    val masse = TafelMasse(o.rahmenMm * 72f / 25.4f, o.bilder, zusatzZeilen(knoten, o), o.waagerecht)
    val w = o.waagerecht
    // Teile: Layout, Richtung, waagerechter Versatz, Reihe der Ausgangsperson
    class Teil(val layout: TafelLayout, val aufwaerts: Boolean, var dx: Float = 0f)
    val teile = buildList {
        inhalt.vorfahren?.let { add(Teil(if (inhalt.linie) linienLayout(it, masse) else stammtafelLayout(it, masse), true)) }
        inhalt.nachfahren?.let { add(Teil(stammtafelLayout(it, masse), false)) }
    }
    // Geschwister: eigene Plaetze in der Reihe der Person, nur wenn deren Eltern auf der Tafel stehen
    val geschwisterVon = HashMap<TafelPlatz, List<TafelPlatz>>()
    teile.forEach { t ->
        t.layout.plaetze.filter { it.knoten.geschwister.isNotEmpty() && it.knoten.kinder.isNotEmpty() }.forEach { pl ->
            val richtung = if (pl.knoten.geschwisterLinks) -1 else 1
            geschwisterVon[pl] = pl.knoten.geschwister.mapIndexed { i, g ->
                TafelPlatz(TafelPerson(g, emptyList()), pl.mitteX + richtung * (i + 1) * masse.slot, pl.obenY, pl.ebene, null)
            }
        }
    }
    fun Teil.alle() = layout.plaetze + layout.plaetze.flatMap { geschwisterVon[it].orEmpty() }
    // Ausgangspersonen uebereinander, dann alles an den linken Rand
    if (teile.size == 2) teile[0].dx = teile[1].layout.plaetze.first().mitteX - teile[0].layout.plaetze.first().mitteX
    val minX = teile.minOf { t -> t.alle().minOf { it.mitteX } + t.dx } - masse.slot / 2
    teile.forEach { it.dx -= minX }
    val layoutBreite = teile.maxOf { t -> t.alle().maxOf { it.mitteX } + t.dx } + masse.slot / 2
    val oben = teile.firstOrNull { it.aufwaerts }?.layout?.plaetze?.maxOf { it.ebene } ?: 0
    val unten = teile.firstOrNull { !it.aufwaerts }?.layout?.plaetze?.maxOf { it.ebene } ?: 0
    val layoutHoehe = (oben + unten + 1) * masse.ebeneH - masse.verbinder
    fun Teil.reihe(pl: TafelPlatz) = if (aufwaerts) oben - pl.ebene else oben + pl.ebene
    fun Teil.oberkante(pl: TafelPlatz) = reihe(pl) * masse.ebeneH
    fun Teil.x(pl: TafelPlatz) = pl.mitteX + dx
    // Jeder Platz einmal: bei der Sanduhr steht die Ausgangsperson nur im unteren Teil
    val gezeichnet = teile.flatMap { t ->
        t.layout.plaetze.filter { !(teile.size == 2 && t.aufwaerts && it.ebene == 0) }.flatMap { pl -> listOf(t to pl) + geschwisterVon[pl].orEmpty().map { t to it } }
    }
    val nummern = doppelteNummern(gezeichnet.map { it.second })
    // Gitter: Spalten zu drei Kaestenbreiten entlang der Geschwister, Zeilen = Generationen vom Probanden aus
    // (Sanduhr: von oben durchgezaehlt, weil Vor- und Nachfahren sonst dieselbe Nummer haetten)
    val mitGitter = o.gitter || o.verzeichnis
    val zelle = masse.slot * 3
    val zeilenZahl = oben + unten + 1
    fun spalteName(i: Int): String = if (i < 26) "${'A' + i}" else spalteName(i / 26 - 1) + ('A' + i % 26)
    fun roemisch(n: Int): String {
        val werte = listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
        var r = n; val sb = StringBuilder()
        werte.forEach { (v, z) -> while (r >= v) { sb.append(z); r -= v } }
        return sb.toString()
    }
    val zeilenName = HashMap<Int, String>()
    gezeichnet.forEach { (t, pl) -> zeilenName.putIfAbsent(t.reihe(pl), roemisch(if (teile.size == 2) t.reihe(pl) + 1 else pl.ebene + 1)) }
    fun position(t: Teil, pl: TafelPlatz) = "${spalteName((t.x(pl) / zelle).toInt())} ${zeilenName[t.reihe(pl)].orEmpty()}"
    val positionen = gezeichnet.filter { it.second.knoten.person.xref.isNotEmpty() }.groupBy({ it.second.knoten.person.xref }, { position(it.first, it.second) })
    val doc = PDDocument()
    val f = farben(o.stil)
    val s = TafelSchriften(doc, o.stil)
    // Heiratszeichen: nicht jede Schrift hat ⚭ - dann das uebliche "oo"
    val heirat = if (runCatching { s.normal.encode("⚭") }.isSuccess) "⚭" else "oo"

    val rand = maxOf(masse.rahmen * 0.5f, 28f)
    val titelGroesse = (masse.rahmen * 0.55f).coerceIn(22f, 72f)
    val titelBreite = if (o.titel.isBlank()) 0f else s.titel.breite(o.titel, titelGroesse)
    val titelH = if (o.titel.isBlank()) 0f else titelGroesse * 1.9f
    val fussH = 18f
    val gitterRand = if (mitGitter) maxOf(18f, masse.rahmen * 0.2f) else 0f
    // Auf dem Blatt: senkrecht liegen die Geschwister nebeneinander, waagerecht die Generationen
    val blattB = if (w) layoutHoehe else layoutBreite
    val blattH = if (w) layoutBreite else layoutHoehe
    val inhaltB = maxOf(blattB, titelBreite)
    val b = inhaltB + 2 * rand + 2 * gitterRand
    val h = rand + titelH + blattH + fussH + rand + 2 * gitterRand
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
        val x0 = rand + gitterRand + (inhaltB - blattB) / 2
        val y0 = rand + titelH + gitterRand
        fun py(y: Float) = h - y
        // Punkt aus Generations- und Geschwisterachse in Blattkoordinaten (x, y von oben)
        fun px(g: Float, q: Float) = x0 + if (w) g else q
        fun pyv(g: Float, q: Float) = py(y0 + if (w) q else g)

        if (o.titel.isNotBlank()) {
            cs.setNonStrokingColor(f.titel)
            cs.beginText(); cs.setFont(s.titel, titelGroesse)
            cs.newLineAtOffset((b - titelBreite) / 2, py(rand + titelGroesse * 1.05f)); cs.showText(s.titel.sicher(o.titel)); cs.endText()
        }

        // Gitter am Rand: Buchstaben fuer die Spalten, roemische Zahlen fuer die Generationen, dazwischen kleine Striche
        if (mitGitter) {
            val g = maxOf(8f, masse.rahmen * 0.09f)
            cs.setNonStrokingColor(f.linie); cs.setStrokingColor(f.linie); cs.setLineWidth(0.5f)
            fun beschriftung(t: String, x: Float, yVonOben: Float) {
                cs.beginText(); cs.setFont(s.normal, g); cs.newLineAtOffset(x - s.normal.breite(t, g) / 2, py(yVonOben + g * 0.35f)); cs.showText(s.normal.sicher(t)); cs.endText()
            }
            val zellen = Math.ceil((layoutBreite / zelle).toDouble()).toInt().coerceAtLeast(1)
            for (i in 0 until zellen) {
                val q = i * zelle + zelle / 2
                val name = spalteName(i)
                if (!w) { beschriftung(name, x0 + q, y0 - gitterRand / 2); beschriftung(name, x0 + q, y0 + blattH + gitterRand / 2) }
                else { beschriftung(name, x0 - gitterRand / 2, y0 + q); beschriftung(name, x0 + blattB + gitterRand / 2, y0 + q) }
                if (i > 0) {
                    val grenze = i * zelle
                    if (!w) listOf(y0 - gitterRand * 0.8f to y0 - gitterRand * 0.2f, y0 + blattH + gitterRand * 0.2f to y0 + blattH + gitterRand * 0.8f)
                        .forEach { (a, e) -> cs.moveTo(x0 + grenze, py(a)); cs.lineTo(x0 + grenze, py(e)) }
                    else listOf(x0 - gitterRand * 0.8f to x0 - gitterRand * 0.2f, x0 + blattB + gitterRand * 0.2f to x0 + blattB + gitterRand * 0.8f)
                        .forEach { (a, e) -> cs.moveTo(a, py(y0 + grenze)); cs.lineTo(e, py(y0 + grenze)) }
                }
            }
            cs.stroke()
            for (r in 0 until zeilenZahl) {
                val name = zeilenName[r] ?: continue
                val gm = r * masse.ebeneH + masse.laengeG / 2
                if (!w) { beschriftung(name, x0 - gitterRand / 2, y0 + gm); beschriftung(name, x0 + blattB + gitterRand / 2, y0 + gm) }
                else { beschriftung(name, x0 + gm, y0 - gitterRand / 2); beschriftung(name, x0 + gm, y0 + blattH + gitterRand / 2) }
            }
        }

        // Doppelte Personen: farbige Kurven zwischen den Vorkommen, halbtransparent unter allem anderen
        if (o.kurven) {
            val palette = listOf(Color(0xD9, 0x4F, 0x4F), Color(0x3F, 0x7F, 0xD0), Color(0x4C, 0xA6, 0x4C), Color(0xE0, 0x9A, 0x2B), Color(0x8E, 0x5C, 0xC4), Color(0x2B, 0xA3, 0xA3))
            val dunst = org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState().apply { strokingAlphaConstant = 0.45f }
            cs.saveGraphicsState(); cs.setGraphicsStateParameters(dunst)
            cs.setLineWidth(maxOf(2.5f, masse.rahmen * 0.03f))
            gezeichnet.filter { it.second.knoten.person.xref.isNotEmpty() }.groupBy { it.second.knoten.person.xref }.values.filter { it.size > 1 }
                .forEachIndexed { i, vorkommen ->
                    cs.setStrokingColor(palette[i % palette.size])
                    val punkte = vorkommen.map { (t, pl) -> val gm = t.oberkante(pl) + masse.laengeG / 2; px(gm, t.x(pl)) to pyv(gm, t.x(pl)) }.sortedBy { it.first }
                    punkte.zipWithNext().forEach { (a, e) ->
                        val d = Math.hypot((e.first - a.first).toDouble(), (e.second - a.second).toDouble()).toFloat()
                        if (d < masse.slot * 2) return@forEach
                        val bogen = d * 0.3f
                        cs.moveTo(a.first, a.second)
                        cs.curveTo(a.first, a.second + bogen, e.first, e.second + bogen, e.first, e.second)
                    }
                    cs.stroke()
                }
            cs.restoreGraphicsState()
        }

        // Verbindungen: von der Unterkante der Eltern senkrecht auf halbe Hoehe, waagerecht ueber alle Kinder,
        // senkrecht hinunter zu jedem Kind (Vorfahren spiegelbildlich: vom Bild nach oben zu Vater und Mutter)
        cs.setStrokingColor(f.linie); cs.setLineWidth(maxOf(0.6f, masse.rahmen * 0.009f))
        teile.forEach { t ->
            t.layout.plaetze.groupBy { it.eltern }.forEach { (eltern, kinder) ->
                if (eltern == null) return@forEach
                val start = if (t.aufwaerts) t.oberkante(eltern) else t.oberkante(eltern) + masse.laengeG
                val mitte = if (t.aufwaerts) start - masse.verbinder / 2 else start + masse.verbinder / 2
                val ex = t.x(eltern)
                cs.moveTo(px(start, ex), pyv(start, ex)); cs.lineTo(px(mitte, ex), pyv(mitte, ex))
                val xs = kinder.map { t.x(it) }
                // Geschwister haengen an derselben Linie wie die Person
                val gs = geschwisterVon[eltern].orEmpty().map { t.x(it) }
                gs.forEach { q -> cs.moveTo(px(start, q), pyv(start, q)); cs.lineTo(px(mitte, q), pyv(mitte, q)) }
                val q1 = minOf(xs.min(), ex, gs.minOrNull() ?: ex); val q2 = maxOf(xs.max(), ex, gs.maxOrNull() ?: ex)
                cs.moveTo(px(mitte, q1), pyv(mitte, q1)); cs.lineTo(px(mitte, q2), pyv(mitte, q2))
                kinder.forEach { k ->
                    val ende = if (t.aufwaerts) t.oberkante(k) + masse.laengeG else t.oberkante(k)
                    val q = t.x(k)
                    cs.moveTo(px(mitte, q), pyv(mitte, q)); cs.lineTo(px(ende, q), pyv(ende, q))
                }
                cs.stroke()
            }
        }

        // Kleines Schild an der rechten oberen Bildecke (Nummer fuer Doppelte, "= 8" fuer Ahnenschwund)
        fun schild(text: String, cx: Float, cy: Float) {
            val r = masse.rahmen * 0.075f; val g = r * 1.3f
            val w = maxOf(2 * r, s.fett.breite(text, g) + r)
            cs.setNonStrokingColor(Color(0xFF, 0xF3, 0x9A)); cs.setStrokingColor(f.linie); cs.setLineWidth(0.5f)
            cs.rechteck(cx - w / 2, cy - r, w, 2 * r, r); cs.fillAndStroke()
            cs.setNonStrokingColor(f.text); cs.beginText(); cs.setFont(s.fett, g)
            cs.newLineAtOffset(cx - s.fett.breite(text, g) / 2, cy - g * 0.35f); cs.showText(s.fett.sicher(text)); cs.endText()
        }

        gezeichnet.forEach { (t, platz) ->
            val k = platz.knoten
            val p = k.person
            // Karte: linke obere Ecke auf dem Blatt (y von oben)
            val karteL = if (w) x0 + t.oberkante(platz) else x0 + t.x(platz) - masse.karteB / 2
            val karteO = if (w) y0 + t.x(platz) - masse.karteH / 2 else y0 + t.oberkante(platz)
            // Senkrecht: Bild oben mittig, Kasten darunter. Waagerecht: Bild links, Kasten rechts daneben, beide mittig.
            val bildL = if (w) karteL else karteL + (masse.karteB - masse.bild) / 2
            val oben = if (w) karteO + (masse.karteH - masse.bild) / 2 else karteO
            val links = if (w) karteL + masse.bild + masse.bildAbstand else karteL
            val mx = links + masse.rahmen / 2
            k.hinweis?.let { hw ->
                // Auf der Seite, wo die Vorfahren weitergehen
                val g = masse.schriftKlein
                val tb = s.fett.breite(hw, g)
                val (hx, hy) = when {
                    !w -> (karteL + masse.karteB / 2 - tb / 2) to (if (t.aufwaerts) karteO - g * 0.6f else karteO + masse.karteH + g * 1.3f)
                    t.aufwaerts -> (karteL - tb - g * 0.5f) to (karteO + masse.karteH / 2 + g * 0.35f)
                    else -> (karteL + masse.karteB + g * 0.5f) to (karteO + masse.karteH / 2 + g * 0.35f)
                }
                cs.setNonStrokingColor(f.linie); cs.beginText(); cs.setFont(s.fett, g)
                cs.newLineAtOffset(hx, py(hy)); cs.showText(s.fett.sicher(hw)); cs.endText()
            }
            // Mit Gitter: unter der Karte (waagerecht rechts daneben), wo die Person noch steht
            if (mitGitter) positionen[p.xref]?.takeIf { it.size > 1 }?.let { alle ->
                val andere = alle - position(t, platz)
                if (andere.isNotEmpty()) {
                    val hw = "= " + andere.distinct().joinToString(", ")
                    val g = masse.schriftKlein * 0.9f
                    val (hx, hy) = if (!w) (karteL + masse.karteB / 2 + g * 0.4f) to (karteO + masse.karteH + g * 1.2f)
                        else (karteL + masse.karteB + g * 0.4f) to (karteO + masse.karteH - g * 0.2f)
                    cs.setNonStrokingColor(f.linie); cs.beginText(); cs.setFont(s.normal, g); cs.newLineAtOffset(hx, py(hy)); cs.showText(s.normal.sicher(hw)); cs.endText()
                }
            }
            val schildText = k.verweis?.let { "= $it" } ?: nummern[p.xref]?.toString()
            // Bild mit feinem Rand
            bildFuer(p)?.let { img ->
                val bx = bildL
                cs.drawImage(img, bx, py(oben + masse.bild), masse.bild, masse.bild)
                cs.setStrokingColor(f.linie); cs.setLineWidth(0.5f); cs.addRect(bx, py(oben + masse.bild), masse.bild, masse.bild); cs.stroke()
                schildText?.let { schild(it, bx + masse.bild, py(oben)) }
            }
            // Kasten
            val ky = if (w) karteO + (masse.karteH - masse.kastenH) / 2 else oben + masse.bild + masse.bildAbstand
            val kx = links + f.rahmenBreite / 2; val kw = masse.rahmen - f.rahmenBreite
            cs.setNonStrokingColor(f.fuellung(p.sex)); cs.setStrokingColor(f.rahmen(p.sex)); cs.setLineWidth(f.rahmenBreite)
            cs.rechteck(kx, py(ky + masse.kastenH), kw, masse.kastenH, if (f.rund) masse.rahmen * 0.05f else 0f); cs.fillAndStroke()
            if (!o.bilder) schildText?.let { schild(it, links + masse.rahmen, py(ky)) }
            // Zeilen: Vorname klein, Nachname fett, Geburt (und Ort), Tod (und Ort), Partner
            val zeilen = buildList {
                if (p.isPrivate) add(KastenZeile(privat, s.normal, masse.schriftKlein))
                else {
                    val vor = p.given.ifBlank { if (p.surname.isBlank()) p.name else "" }
                    add(KastenZeile(vor, s.fett, masse.schriftKlein))
                    add(KastenZeile(p.surname, s.fett, masse.schriftName))
                    listOf("*" to p.birth, "†" to p.death).forEach { (zeichen, e) ->
                        val d = datum(e, o.volleDaten)
                        if (d.isNotBlank()) add(KastenZeile("$zeichen $d", s.normal, masse.schriftKlein))
                        if (o.orte) ort(e).takeIf(String::isNotBlank)?.let { add(KastenZeile(it, s.normal, masse.schriftKlein * 0.92f)) }
                    }
                    k.partner.take(2).forEach { add(KastenZeile("$heirat ${it.name.ifBlank { "?" }}", s.normal, masse.schriftKlein * 0.92f)) }
                }
            }
            val innen = masse.rahmen * 0.84f
            var y = ky + masse.rahmen * 0.06f
            // Kekule-Nummer klein oben links im Kasten; die erste Zeile weicht ihr beidseitig aus
            val nummer = k.nummer?.takeIf { o.nummern }?.toString()
            val nummerG = masse.schriftKlein * 0.85f
            val nummerB = nummer?.let { s.normal.breite(it, nummerG) + masse.rahmen * 0.04f } ?: 0f
            if (nummer != null) {
                cs.setNonStrokingColor(f.linie)
                cs.beginText(); cs.setFont(s.normal, nummerG); cs.newLineAtOffset(links + masse.rahmen * 0.05f, py(ky + nummerG * 1.25f)); cs.showText(nummer); cs.endText()
            }
            cs.setNonStrokingColor(f.text)
            zeilen.forEachIndexed { i, z ->
                y += z.groesse * 1.3f
                if (z.text.isNotBlank()) {
                    val (tx, g) = passend(z.schrift, z.text, z.groesse, if (i == 0) innen - 2 * nummerB else innen)
                    cs.beginText(); cs.setFont(z.schrift, g)
                    cs.newLineAtOffset(mx - z.schrift.breite(tx, g) / 2, py(y - z.groesse * 0.25f)); cs.showText(z.schrift.sicher(tx)); cs.endText()
                }
            }
        }

        cs.setNonStrokingColor(Color(0x66, 0x66, 0x66))
        cs.beginText(); cs.setFont(s.normal, 7f); cs.newLineAtOffset(rand, rand * 0.6f); cs.showText(s.normal.sicher(fuss)); cs.endText()
    }
    // Personenverzeichnis: eigene A4-Seiten hinter der Tafel, jede Zeile ein Link auf den Kasten
    if (o.verzeichnis) {
        val x0 = rand + gitterRand + (inhaltB - blattB) / 2
        val y0 = rand + titelH + gitterRand
        val eintraege = gezeichnet.filter { it.second.knoten.person.xref.isNotEmpty() && !it.second.knoten.person.isPrivate }
            .groupBy { it.second.knoten.person.xref }.map { (_, v) ->
                val (t, pl) = v.first()
                val gm = t.oberkante(pl); val q = t.x(pl)
                // Ziel im Blatt (PDF-Koordinaten, schon mit der Verkleinerung)
                val zx = (x0 + if (w) gm else q - masse.karteB / 2) * skala
                val zy = (h - (y0 + if (w) q - masse.karteH / 2 else gm)) * skala
                Triple(pl.knoten.person, v.map { (tt, p2) -> position(tt, p2) }.distinct(), zx to zy)
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { registerName(it.first) })
        tafelVerzeichnis(doc, o.titel, eintraege)
    }
    val info = TafelInfo(gezeichnet.map { it.second.knoten.person.xref }.distinct().size, (b * skala / 72f * 2.54f).toInt(), (h * skala / 72f * 2.54f).toInt())
    return doc to info
}

/** Personenverzeichnis zur Tafel: zweispaltig auf A4, "Name (Lebensdaten) ..... C III", Links auf die erste Seite. */
private fun tafelVerzeichnis(doc: PDDocument, titel: String, eintraege: List<Triple<Person, List<String>, Pair<Float, Float>>>) {
    val schrift = Schriften(doc)
    val a4 = PDRectangle.A4
    val rand = 50f; val abstand = 20f
    val sw = (a4.width - 2 * rand - abstand) / 2
    val g = 8.5f; val zh = g * 1.4f
    val ziel = doc.getPage(0)
    var cs: PDPageContentStream? = null
    var seite: PDPage? = null
    var y = 0f; var spalte = 2
    fun neueSpalte() {
        spalte++
        if (spalte >= 2) {
            cs?.close()
            seite = PDPage(a4).also { doc.addPage(it) }
            cs = PDPageContentStream(doc, seite)
            val kopf = Texte.t(Res.string.desk_chart_index_title) + if (titel.isNotBlank()) " – $titel" else ""
            cs!!.beginText(); cs!!.setFont(schrift.fett, 12f); cs!!.newLineAtOffset(rand, a4.height - rand); cs!!.showText(schrift.fett.sicher(kopf)); cs!!.endText()
            spalte = 0
        }
        y = a4.height - rand - 26f
    }
    neueSpalte()
    eintraege.forEach { (p, pos, zielPunkt) ->
        if (y < rand) neueSpalte()
        val x = rand + spalte * (sw + abstand)
        val name = registerName(p) + p.lifespan.takeIf(String::isNotBlank)?.let { " ($it)" }.orEmpty()
        val rechts = pos.joinToString(", ")
        val rb = schrift.normal.breite(rechts, g)
        val (nt, ng) = passend(schrift.normal, name, g, sw - rb - 10f)
        val c = cs!!
        c.beginText(); c.setFont(schrift.normal, ng); c.newLineAtOffset(x, y); c.showText(schrift.normal.sicher(nt)); c.endText()
        val nb = schrift.normal.breite(nt, ng)
        val punkt = schrift.normal.breite(".", g)
        val sb = StringBuilder(); var px = x + nb + 3f
        while (px + punkt < x + sw - rb - 3f) { sb.append('.'); px += punkt }
        c.beginText(); c.setFont(schrift.normal, g); c.newLineAtOffset(x + nb + 3f, y); c.showText(sb.toString()); c.endText()
        c.beginText(); c.setFont(schrift.normal, g); c.newLineAtOffset(x + sw - rb, y); c.showText(schrift.normal.sicher(rechts)); c.endText()
        seite!!.annotations.add(org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink().apply {
            rectangle = PDRectangle(x, y - g * 0.3f, sw, zh)
            borderStyle = org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary().apply { width = 0f }
            action = org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo().apply {
                destination = org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination().apply {
                    page = ziel; left = zielPunkt.first.toInt(); top = zielPunkt.second.toInt()
                }
            }
        })
        y -= zh
    }
    cs?.close()
}

/**
 * Das Blatt einmal gespeichert und neu geladen: PDFBox bettet die Schriften (Teilmengen) erst beim Speichern ein.
 * Wer die Seite vorher in ein anderes Dokument uebernimmt, bekommt leere Schriften - der Titel in Great Vibes
 * wurde dort zu Zeichensalat (26.09.2026). Das Original bleibt unveraendert nutzbar.
 */
private fun eingebettet(poster: PDDocument): PDDocument =
    org.apache.pdfbox.Loader.loadPDF(java.io.ByteArrayOutputStream().also { poster.save(it) }.toByteArray())

/** Das Blatt verkleinert auf eine A4-Seite (hoch oder quer, was besser passt). */
fun aufEinBlatt(poster: PDDocument): PDDocument = PDDocument().also { aufEinBlatt(poster, it) }

/** Wie [aufEinBlatt], haengt die Seite aber an [ziel] an (fuer mehrseitige Tafeln). */
fun aufEinBlatt(original: PDDocument, ziel: PDDocument, querErzwingen: Boolean = false) {
    val poster = eingebettet(original)
    try { aufEinBlattSeite(poster, ziel, querErzwingen) } finally { anhangSeiten(poster, ziel) }
}

/** Seiten hinter dem Blatt (Personenverzeichnis) unveraendert anhaengen, ohne Links auf das Poster. */
private fun anhangSeiten(poster: PDDocument, ziel: PDDocument) {
    for (i in 1 until poster.numberOfPages) ziel.importPage(poster.getPage(i)).annotations = emptyList()
}

private fun aufEinBlattSeite(poster: PDDocument, ziel: PDDocument, querErzwingen: Boolean) {
    val quelle = poster.getPage(0).mediaBox
    val a4 = PDRectangle.A4
    val quer = querErzwingen || quelle.width > quelle.height
    val format = if (quer) PDRectangle(a4.height, a4.width) else a4
    val rand = 28f
    val form = LayerUtility(ziel).importPageAsForm(poster, 0)
    val page = PDPage(format); ziel.addPage(page)
    val f = minOf((format.width - 2 * rand) / quelle.width, (format.height - 2 * rand) / quelle.height, 1f)
    PDPageContentStream(ziel, page).use { cs ->
        cs.saveGraphicsState()
        cs.transform(Matrix.getTranslateInstance((format.width - quelle.width * f) / 2, (format.height - quelle.height * f) / 2))
        cs.transform(Matrix.getScaleInstance(f, f))
        cs.drawForm(form); cs.restoreGraphicsState()
    }
}

/**
 * Das Blatt in Originalgroesse auf A4-Seiten zum Zusammenkleben: 10 mm Rand, 10 mm Ueberlappung, jede Seite
 * mit Zeile/Spalte und Schnittmarken. Hoch- oder Querformat - was weniger Seiten braucht.
 */
fun aufA4Blaetter(original: PDDocument): PDDocument {
    val poster = eingebettet(original)
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
    anhangSeiten(poster, doc)
    return doc
}
