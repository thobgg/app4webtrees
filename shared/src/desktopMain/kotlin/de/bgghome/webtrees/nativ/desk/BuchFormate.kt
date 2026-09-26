package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.res.*
import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/*
 * Weitere Ausgabeformate fuer Buecher (26.09.2026): HTML (eine Datei, Bilder eingebettet, Verweise als Links),
 * TeX/LaTeX (Bilder in einem Ordner daneben), reiner Text und DOCX (Word/LibreOffice, zum Weiterschreiben).
 * Alle aus demselben Dokument wie das PDF.
 */

enum class BuchFormat(val endung: String) { PDF("pdf"), DOCX("docx"), HTML("html"), TEX("tex"), TXT("txt") }

private fun jpeg(b: BufferedImage): ByteArray = ByteArrayOutputStream().also { ImageIO.write(b, "jpg", it) }.toByteArray()

private fun inhaltsZiele(buch: Buch) = buch.bloecke.mapNotNull { b -> when (b) { is Ueberschrift -> b.text to b.id; is Verzeichnis -> b.titel to b.id; else -> null } }

// ── HTML ──

private fun h(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

fun buchHtml(buch: Buch): String = buildString {
    append("<!doctype html>\n<html lang=\"de\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
    append("<title>${h(buch.titel)}</title>\n<style>\n")
    append("""body{font-family:Georgia,'Times New Roman',serif;max-width:46em;margin:2em auto;padding:0 1em;line-height:1.45;color:#1e1e1e;background:#fff}
h1.titel{text-align:center;font-size:2.4em;margin:1.5em 0 .2em}p.unter{text-align:center;font-weight:bold;font-size:1.2em}p.zeile{text-align:center;color:#555}
img.titelbild{display:block;margin:2em auto 1em;max-width:12em;border:1px solid #777}h2{margin-top:2.2em;border-bottom:1px solid #999;padding-bottom:.2em}
p.e{margin:.9em 0 .2em 2.6em;text-indent:-2.6em;overflow:auto}p.e .nr{display:inline-block;width:2.6em;text-indent:0;font-weight:bold}
p.z{margin:.1em 0 .1em 2.6em}p.e img{float:right;width:5em;margin:0 0 .4em .8em;border:1px solid #999}p.e.farbe{border-left:4px solid var(--f);padding-left:.4em}
a{color:#1f3a8a;text-decoration:none}a:hover{text-decoration:underline}nav a{display:block}.reg{columns:2;column-gap:2em}.reg1{columns:1}
.reg div{break-inside:avoid;display:flex}.reg div span.t{flex:1}.reg b{display:block;margin-top:.6em}footer{margin-top:3em;color:#777;font-size:.85em;text-align:center}
@media print{h2{break-before:page}}""")
    append("\n</style></head><body>\n")
    buch.bloecke.forEach { b ->
        when (b) {
            is Titelblatt -> {
                b.bild?.let { append("<img class=\"titelbild\" alt=\"\" src=\"data:image/jpeg;base64,${Base64.getEncoder().encodeToString(jpeg(it))}\">\n") }
                append("<h1 class=\"titel\">${h(b.titel)}</h1>\n")
                if (b.untertitel.isNotBlank()) append("<p class=\"unter\">${h(b.untertitel)}</p>\n")
                append("<p class=\"zeile\">${h(b.zeile)}</p>\n")
            }
            is Inhaltsverzeichnis -> {
                append("<h2>${h(Texte.t(Res.string.desk_book_contents))}</h2>\n<nav>\n")
                inhaltsZiele(buch).forEach { (t, id) -> append("<a href=\"#$id\">${h(t)}</a>\n") }
                append("</nav>\n")
            }
            is Ueberschrift -> append("<h2 id=\"${b.id}\">${h(b.text)}</h2>\n")
            is Absatz -> {
                val klasse = if (b.marke != null) "e" + (if (b.farbe != null) " farbe" else "") else "z"
                val stil = b.farbe?.let { " style=\"--f:#%02x%02x%02x\"".format(it.red, it.green, it.blue) } ?: ""
                append("<p class=\"$klasse\"$stil${b.anker?.let { " id=\"$it\"" } ?: ""}>")
                b.marke?.let { append("<span class=\"nr\">${h(it)}</span>") }
                b.bild?.let { append("<img alt=\"\" src=\"data:image/jpeg;base64,${Base64.getEncoder().encodeToString(jpeg(it))}\">") }
                b.laeufe.forEach { l ->
                    val t = h(l.text)
                    val s = when (l.stil) { Stil.Fett -> "<b>$t</b>"; Stil.Kursiv -> "<i>$t</i>"; else -> t }
                    append(l.ziel?.let { "<a href=\"#$it\">$s</a>" } ?: s)
                }
                append("</p>\n")
            }
            is Verzeichnis -> {
                append("<h2 id=\"${b.id}\">${h(b.titel)}</h2>\n<div class=\"${if (b.spalten > 1) "reg" else "reg reg1"}\">\n")
                b.gruppen.forEach { (kopf, zeilen) ->
                    if (kopf.isNotBlank()) append("<b>${h(kopf)}</b>\n")
                    zeilen.forEach { (t, nr) ->
                        append("<div><span class=\"t\">${h(t)}</span><span>${nr.distinct().sorted().joinToString(", ") { "<a href=\"#n$it\">$it</a>" }}</span></div>\n")
                    }
                }
                append("</div>\n")
            }
        }
    }
    append("<footer>${h(buch.fuss)}</footer>\n</body></html>\n")
}

// ── Text ──

fun buchText(buch: Buch): String = buildString {
    buch.bloecke.forEach { b ->
        when (b) {
            is Titelblatt -> { append(b.titel.uppercase()).append('\n'); if (b.untertitel.isNotBlank()) append(b.untertitel).append('\n'); append(b.zeile).append("\n\n") }
            is Inhaltsverzeichnis -> { append(Texte.t(Res.string.desk_book_contents)).append('\n'); inhaltsZiele(buch).forEach { append("  ").append(it.first).append('\n') }; append('\n') }
            is Ueberschrift -> append("\n").append(b.text).append('\n').append("=".repeat(b.text.length)).append("\n\n")
            is Absatz -> {
                val einr = "    ".repeat(b.einzug)
                val t = b.laeufe.joinToString("") { it.text }
                append(if (b.abstandVor) "\n" else "").append(einr)
                append(b.marke?.let { it.padEnd(6) } ?: if (b.einzug == 0) "" else "      ")
                append(t).append('\n')
            }
            is Verzeichnis -> {
                append("\n").append(b.titel).append('\n').append("=".repeat(b.titel.length)).append("\n")
                b.gruppen.forEach { (kopf, zeilen) ->
                    if (kopf.isNotBlank()) append('\n').append(kopf).append('\n')
                    zeilen.forEach { (t, nr) -> append("  ").append(t).append(" .... ").append(nummernText(nr)).append('\n') }
                }
            }
        }
    }
    append("\n").append(buch.fuss).append('\n')
}

// ── TeX ──

private val TEX_ZEICHEN = mapOf('\\' to "\\textbackslash{}", '&' to "\\&", '%' to "\\%", '$' to "\\$", '#' to "\\#", '_' to "\\_",
    '{' to "\\{", '}' to "\\}", '~' to "\\textasciitilde{}", '^' to "\\textasciicircum{}")

private fun tex(t: String) = buildString { t.forEach { c -> append(TEX_ZEICHEN[c] ?: c.toString()) } }

/** LaTeX-Quelltext; die Bilder landen unter [bilderOrdner] (relativ zur .tex-Datei) und werden in [bilder] gesammelt. */
fun buchTex(buch: Buch, bilderOrdner: String, bilder: MutableMap<String, BufferedImage>): String = buildString {
    append("""% Erzeugt mit ${buch.fuss}
% Uebersetzen: lualatex oder pdflatex (zweimal, wegen Inhaltsverzeichnis)
\documentclass[a4paper,11pt,oneside]{book}
\usepackage[T1]{fontenc}
\usepackage[utf8]{inputenc}
\usepackage[ngerman]{babel}
\usepackage{lmodern}
\usepackage{graphicx,wrapfig,multicol,xcolor,amssymb,wasysym,newunicodechar,fancyhdr}
\usepackage[hidelinks]{hyperref}
\newunicodechar{∞}{\ensuremath{\infty}}
\newunicodechar{▭}{\ensuremath{\square}}
\newunicodechar{♂}{\male}
\newunicodechar{♀}{\female}
\newunicodechar{→}{\ensuremath{\rightarrow}}
\newunicodechar{†}{\dag}
\newcommand{\eintrag}[2]{\par\medskip\noindent\hangindent=2.4em\hangafter=1\makebox[2.4em][l]{\textbf{#1}}#2\par}
\newcommand{\zusatz}[1]{{\par\leftskip=2.4em\noindent #1\par}}
\pagestyle{fancy}\fancyhf{}\fancyhead[C]{\small ${tex(buch.kopfzeile)}}\fancyfoot[C]{-- \thepage\ --}
\begin{document}
""")
    buch.bloecke.forEach { b ->
        when (b) {
            is Titelblatt -> {
                append("\\begin{titlepage}\\centering\n\\vspace*{3cm}\n")
                b.bild?.let { bilder["titel.jpg"] = it; append("\\includegraphics[width=5cm]{$bilderOrdner/titel.jpg}\\par\\vspace{1cm}\n") }
                append("{\\Huge\\bfseries ${tex(b.titel)}\\par}\\vspace{0.6cm}\n")
                if (b.untertitel.isNotBlank()) append("{\\Large\\bfseries ${tex(b.untertitel)}\\par}\n")
                append("\\vfill ${tex(b.zeile)}\\par\n\\end{titlepage}\n")
            }
            is Inhaltsverzeichnis -> append("\\tableofcontents\n")
            is Ueberschrift -> append("${if (b.neueSeite) "\\clearpage" else "\\bigskip"}\n\\section*{${tex(b.text)}}\\addcontentsline{toc}{section}{${tex(b.text)}}\\hypertarget{${b.id}}{}\n")
            is Absatz -> {
                val inhalt = b.laeufe.joinToString("") { l ->
                    val t = tex(l.text)
                    val s = when (l.stil) { Stil.Fett -> "\\textbf{$t}"; Stil.Kursiv -> "\\textit{$t}"; else -> t }
                    l.ziel?.let { "\\hyperlink{$it}{$s}" } ?: s
                }
                val ziel = b.anker?.let { "\\hypertarget{$it}{}" }.orEmpty()
                b.bild?.let { bild ->
                    val name = "${b.anker ?: "b${bilder.size}"}.jpg"; bilder[name] = bild
                    append("\\begin{wrapfigure}{r}{2.3cm}\\vspace{-10pt}\\includegraphics[width=2.1cm]{$bilderOrdner/$name}\\end{wrapfigure}\n")
                }
                append(if (b.marke != null) "\\eintrag{${tex(b.marke)}}{$ziel$inhalt}\n" else if (b.einzug > 0) "\\zusatz{$inhalt}\n" else "\\par $inhalt\\par\n")
            }
            is Verzeichnis -> {
                append("\\clearpage\n\\section*{${tex(b.titel)}}\\addcontentsline{toc}{section}{${tex(b.titel)}}\\hypertarget{${b.id}}{}\n")
                if (b.spalten > 1) append("\\begin{multicols}{${b.spalten}}\n")
                append("\\small\n")
                b.gruppen.forEach { (kopf, zeilen) ->
                    if (kopf.isNotBlank()) append("\\par\\medskip\\noindent\\textbf{${tex(kopf)}}\\par\n")
                    zeilen.forEach { (t, nr) -> append("\\noindent\\hspace*{1em}${tex(t)}\\dotfill ${nr.distinct().sorted().joinToString(", ") { "\\hyperlink{n$it}{$it}" }}\\par\n") }
                }
                append("\\normalsize\n")
                if (b.spalten > 1) append("\\end{multicols}\n")
            }
        }
    }
    append("\\end{document}\n")
}

// ── DOCX ──

private fun x(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun lauf(text: String, fett: Boolean = false, kursiv: Boolean = false, farbe: String? = null, groesse: Int? = null): String {
    val pr = buildString {
        if (fett) append("<w:b/>"); if (kursiv) append("<w:i/>"); farbe?.let { append("<w:color w:val=\"$it\"/>") }
        groesse?.let { append("<w:sz w:val=\"$it\"/><w:szCs w:val=\"$it\"/>") }
    }
    return "<w:r>${if (pr.isNotEmpty()) "<w:rPr>$pr</w:rPr>" else ""}<w:t xml:space=\"preserve\">${x(text)}</w:t></w:r>"
}

/** Bild als schwebendes Objekt rechts (Portraet) oder zentriert eingebettet (Titelbild). */
private fun bildXml(rid: String, id: Int, breiteCm: Double, verhaeltnis: Double, schwebend: Boolean): String {
    val cx = (breiteCm * 360000).toLong(); val cy = (breiteCm * verhaeltnis * 360000).toLong()
    val graphic = """<a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:nvPicPr><pic:cNvPr id="$id" name="Bild$id"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="$rid"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic>"""
    return if (schwebend) """<w:r><w:drawing><wp:anchor distT="0" distB="0" distL="114300" distR="0" simplePos="0" relativeHeight="$id" behindDoc="0" locked="0" layoutInCell="1" allowOverlap="0"><wp:simplePos x="0" y="0"/><wp:positionH relativeFrom="margin"><wp:align>right</wp:align></wp:positionH><wp:positionV relativeFrom="paragraph"><wp:posOffset>0</wp:posOffset></wp:positionV><wp:extent cx="$cx" cy="$cy"/><wp:effectExtent l="0" t="0" r="0" b="0"/><wp:wrapSquare wrapText="left"/><wp:docPr id="$id" name="Bild$id"/><wp:cNvGraphicFramePr/>$graphic</wp:anchor></w:drawing></w:r>"""
    else """<w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="$cx" cy="$cy"/><wp:effectExtent l="0" t="0" r="0" b="0"/><wp:docPr id="$id" name="Bild$id"/><wp:cNvGraphicFramePr/>$graphic</wp:inline></w:drawing></w:r>"""
}

fun buchDocx(buch: Buch): ByteArray {
    val medien = mutableListOf<ByteArray>()
    var lesezeichen = 0
    val body = StringBuilder()
    fun absatz(pPr: String, inhalt: String) = body.append("<w:p>${if (pPr.isNotEmpty()) "<w:pPr>$pPr</w:pPr>" else ""}$inhalt</w:p>")
    fun bild(b: BufferedImage, cm: Double, schwebend: Boolean): String {
        medien += jpeg(b)
        return bildXml("rIdBild${medien.size}", 100 + medien.size, cm, b.height.toDouble() / b.width, schwebend)
    }
    buch.bloecke.forEach { b ->
        when (b) {
            is Titelblatt -> {
                absatz("<w:spacing w:before=\"2400\"/>", "")
                b.bild?.let { absatz("<w:jc w:val=\"center\"/>", bild(it, 5.0, false)) }
                absatz("<w:pStyle w:val=\"Title\"/><w:jc w:val=\"center\"/>", lauf(b.titel))
                if (b.untertitel.isNotBlank()) absatz("<w:jc w:val=\"center\"/>", lauf(b.untertitel, fett = true, groesse = 28))
                absatz("<w:jc w:val=\"center\"/><w:spacing w:before=\"3600\"/>", lauf(b.zeile))
                absatz("", "<w:r><w:br w:type=\"page\"/></w:r>")
            }
            is Inhaltsverzeichnis -> {
                absatz("<w:pStyle w:val=\"TOCHeading\"/>", lauf(Texte.t(Res.string.desk_book_contents)))
                // Feld: Word/LibreOffice fuellt es beim Aktualisieren (Word fragt beim Oeffnen)
                body.append("<w:p><w:r><w:fldChar w:fldCharType=\"begin\" w:dirty=\"true\"/></w:r><w:r><w:instrText xml:space=\"preserve\"> TOC \\o \"1-1\" \\h \\z \\u </w:instrText></w:r><w:r><w:fldChar w:fldCharType=\"separate\"/></w:r>")
                body.append(lauf(Texte.t(Res.string.desk_book_toc_update), kursiv = true))
                body.append("<w:r><w:fldChar w:fldCharType=\"end\"/></w:r></w:p>")
            }
            is Ueberschrift -> {
                lesezeichen++
                absatz("<w:pStyle w:val=\"Heading1\"/>${if (b.neueSeite) "<w:pageBreakBefore/>" else ""}",
                    "<w:bookmarkStart w:id=\"$lesezeichen\" w:name=\"${b.id}\"/>${lauf(b.text)}<w:bookmarkEnd w:id=\"$lesezeichen\"/>")
            }
            is Absatz -> {
                val inhalt = StringBuilder()
                b.anker?.let { lesezeichen++; inhalt.append("<w:bookmarkStart w:id=\"$lesezeichen\" w:name=\"$it\"/><w:bookmarkEnd w:id=\"$lesezeichen\"/>") }
                b.bild?.let { inhalt.append(bild(it, 2.0, true)) }
                b.marke?.let { inhalt.append(lauf(it, fett = true)).append("<w:r><w:tab/></w:r>") }
                b.laeufe.forEach { l ->
                    val r = lauf(l.text, fett = l.stil == Stil.Fett, kursiv = l.stil == Stil.Kursiv, farbe = if (l.ziel != null) "1F3A8A" else null)
                    inhalt.append(l.ziel?.let { "<w:hyperlink w:anchor=\"$it\">$r</w:hyperlink>" } ?: r)
                }
                val farbe = b.farbe?.let { "<w:pBdr><w:left w:val=\"single\" w:sz=\"24\" w:space=\"6\" w:color=\"%02X%02X%02X\"/></w:pBdr>".format(it.red, it.green, it.blue) } ?: ""
                val pPr = when {
                    b.marke != null -> "<w:pStyle w:val=\"Eintrag\"/>$farbe"
                    b.einzug > 0 -> "<w:pStyle w:val=\"Zusatz\"/>"
                    else -> ""
                }
                absatz(pPr, inhalt.toString())
            }
            is Verzeichnis -> {
                lesezeichen++
                absatz("<w:pStyle w:val=\"Heading1\"/><w:pageBreakBefore/>", "<w:bookmarkStart w:id=\"$lesezeichen\" w:name=\"${b.id}\"/>${lauf(b.titel)}<w:bookmarkEnd w:id=\"$lesezeichen\"/>")
                b.gruppen.forEach { (kopf, zeilen) ->
                    if (kopf.isNotBlank()) absatz("<w:keepNext/><w:spacing w:before=\"120\" w:after=\"0\"/>", lauf(kopf, fett = true))
                    zeilen.forEach { (t, nr) ->
                        val nummern = nr.distinct().sorted().joinToString("") { n ->
                            (if (n == nr.distinct().sorted().first()) "" else lauf(", ")) + "<w:hyperlink w:anchor=\"n$n\">${lauf("$n")}</w:hyperlink>"
                        }
                        absatz("<w:pStyle w:val=\"Register\"/>", lauf(t) + "<w:r><w:tab/></w:r>" + nummern)
                    }
                }
            }
        }
    }
    val ns = """xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture""""
    val sect = """<w:sectPr><w:headerReference w:type="default" r:id="rIdKopf"/><w:footerReference w:type="default" r:id="rIdFuss"/><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1418" w:right="1134" w:bottom="1418" w:left="1361" w:header="709" w:footer="709" w:gutter="0"/><w:titlePg/></w:sectPr>"""
    val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document $ns><w:body>$body$sect</w:body></w:document>"""
    val styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman" w:cs="Times New Roman"/><w:sz w:val="21"/><w:szCs w:val="21"/><w:lang w:val="de-DE"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="0" w:line="276" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="480" w:after="120"/></w:pPr><w:rPr><w:b/><w:sz w:val="56"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:pPr><w:keepNext/><w:spacing w:before="240" w:after="240"/><w:outlineLvl w:val="0"/></w:pPr><w:rPr><w:b/><w:sz w:val="30"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="TOCHeading"><w:name w:val="TOC Heading"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:after="240"/></w:pPr><w:rPr><w:b/><w:sz w:val="30"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Eintrag"><w:name w:val="Eintrag"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="140"/><w:ind w:left="680" w:hanging="680"/></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="Zusatz"><w:name w:val="Zusatz"/><w:basedOn w:val="Normal"/><w:pPr><w:ind w:left="680"/></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="Register"><w:name w:val="Register"/><w:basedOn w:val="Normal"/><w:pPr><w:tabs><w:tab w:val="right" w:leader="dot" w:pos="9350"/></w:tabs><w:ind w:left="284"/></w:pPr><w:rPr><w:sz w:val="19"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Header"><w:name w:val="header"/><w:basedOn w:val="Normal"/><w:pPr><w:jc w:val="center"/><w:pBdr><w:bottom w:val="single" w:sz="4" w:space="1" w:color="888888"/></w:pBdr></w:pPr><w:rPr><w:sz w:val="17"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Footer"><w:name w:val="footer"/><w:basedOn w:val="Normal"/><w:pPr><w:jc w:val="center"/></w:pPr><w:rPr><w:sz w:val="18"/></w:rPr></w:style>
</w:styles>"""
    val kopf = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:pPr><w:pStyle w:val="Header"/></w:pPr>${lauf(buch.kopfzeile)}</w:p></w:hdr>"""
    val fuss = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:pPr><w:pStyle w:val="Footer"/></w:pPr>${lauf("- ")}<w:r><w:fldChar w:fldCharType="begin"/></w:r><w:r><w:instrText xml:space="preserve"> PAGE </w:instrText></w:r><w:r><w:fldChar w:fldCharType="separate"/></w:r>${lauf("1")}<w:r><w:fldChar w:fldCharType="end"/></w:r>${lauf(" -")}</w:p></w:ftr>"""
    val settings = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:settings xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:updateFields w:val="true"/><w:defaultTabStop w:val="680"/></w:settings>"""
    val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rIdStile" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/><Relationship Id="rIdEinst" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings" Target="settings.xml"/><Relationship Id="rIdKopf" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/header" Target="header1.xml"/><Relationship Id="rIdFuss" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>${
        medien.indices.joinToString("") { """<Relationship Id="rIdBild${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/bild${it + 1}.jpg"/>""" }}</Relationships>"""
    val typen = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="jpg" ContentType="image/jpeg"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/><Override PartName="/word/header1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/><Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/></Types>"""
    val wurzel = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
    return ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z ->
            fun datei(name: String, inhalt: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(inhalt); z.closeEntry() }
            datei("[Content_Types].xml", typen.toByteArray()); datei("_rels/.rels", wurzel.toByteArray())
            datei("word/document.xml", document.toByteArray()); datei("word/styles.xml", styles.toByteArray())
            datei("word/settings.xml", settings.toByteArray()); datei("word/header1.xml", kopf.toByteArray()); datei("word/footer1.xml", fuss.toByteArray())
            datei("word/_rels/document.xml.rels", rels.toByteArray())
            medien.forEachIndexed { i, m -> datei("word/media/bild${i + 1}.jpg", m) }
        }
    }.toByteArray()
}

/** Buch in eine Datei schreiben (ohne Dialog). TeX legt seine Bilder in "<name>-bilder" daneben. */
fun buchSchreiben(buch: Buch, format: BuchFormat, ziel: File) {
    when (format) {
        BuchFormat.PDF -> buchPdf(buch).use { it.save(ziel) }
        BuchFormat.DOCX -> ziel.writeBytes(buchDocx(buch))
        BuchFormat.HTML -> ziel.writeText(buchHtml(buch))
        BuchFormat.TXT -> ziel.writeText(buchText(buch))
        BuchFormat.TEX -> {
            val ordnerName = ziel.nameWithoutExtension + "-bilder"
            val bilder = LinkedHashMap<String, BufferedImage>()
            ziel.writeText(buchTex(buch, ordnerName, bilder))
            if (bilder.isNotEmpty()) File(ziel.parentFile, ordnerName).apply { mkdirs() }.let { o -> bilder.forEach { (n, b) -> File(o, n).writeBytes(jpeg(b)) } }
        }
    }
}

/** Speichern-Dialog fuer ein Format; danach mit dem Standardprogramm oeffnen (TeX nicht). */
fun buchSpeichern(buch: Buch, format: BuchFormat, vorschlag: String) {
    val dialog = FileDialog(null as Frame?, Texte.t(Res.string.desk_book_save, format.name), FileDialog.SAVE).apply {
        file = vorschlag.replace(Regex("[\\\\/:*?\"<>|]"), "_") + "." + format.endung
        isVisible = true
    }
    val name = dialog.file ?: return
    val ziel = File(dialog.directory, if (name.endsWith("." + format.endung, true)) name else "$name.${format.endung}")
    buchSchreiben(buch, format, ziel)
    if (format != BuchFormat.TEX) runCatching { java.awt.Desktop.getDesktop().open(ziel) }
}
