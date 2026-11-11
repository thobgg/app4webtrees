#!/usr/bin/env python3
"""Baut aus den Hilfetexten des Programms (hilfe/<sprache>/*.md) ein LaTeX-Handbuch im wtWin-Erscheinungsbild.

Aufruf: md2tex.py <sprache> <zielordner> [gedaudit]   (Sprachen: de en fr nl es; "gedaudit": Handbuch von gedaudit statt wtWin)
Schreibt <zielordner>/handbuch.tex und <zielordner>/kapitel/<id>.tex. Setzen mit bauen.sh.
"""
import re
import sys
from datetime import date
from pathlib import Path

WURZEL = Path(__file__).resolve().parents[2]
HILFE = WURZEL / "shared/src/desktopMain/resources/hilfe"
HILFE_KT = WURZEL / "shared/src/desktopMain/kotlin/de/bgghome/webtrees/nativ/desk/Hilfe.kt"
BILDER = WURZEL / "docs/screenshots"
CI = WURZEL / "docs/ci"

TEXTE = {
    "de": dict(babel="ngerman", titel="Handbuch", kapitel="Kapitel", version="Version", stand="Stand",
               claim="Dein webtrees-Stammbaum als Desktop-Programm",
               vorwort="Dieses Handbuch entsteht aus der Hilfe des Programms (Taste F1) und hat denselben Stand. Verbesserungen und Fragen bitte als Issue unter github.com/thobgg/app4webtrees.",
               monate="Januar Februar März April Mai Juni Juli August September Oktober November Dezember", datum="{t}. {m} {j}",
               achtung=("achtung", "warnung")),
    "en": dict(babel="british", titel="Manual", kapitel="chapter", version="Version", stand="as of",
               claim="Your webtrees family tree as a desktop program",
               vorwort="This manual is generated from the program's built-in help (key F1) and matches it exactly. Suggestions and questions: github.com/thobgg/app4webtrees/issues.",
               monate="January February March April May June July August September October November December", datum="{t} {m} {j}",
               achtung=("warning", "caution", "attention")),
    "fr": dict(babel="french", titel="Manuel", kapitel="chapitre", version="Version", stand="au",
               claim="Votre arbre webtrees comme programme de bureau",
               vorwort="Ce manuel est généré à partir de l’aide intégrée du programme (touche F1) et en reflète exactement l’état. Suggestions et questions : github.com/thobgg/app4webtrees/issues.",
               monate="janvier février mars avril mai juin juillet août septembre octobre novembre décembre", datum="{t} {m} {j}",
               achtung=("attention", "avertissement")),
    "nl": dict(babel="dutch", titel="Handleiding", kapitel="hoofdstuk", version="Versie", stand="stand",
               claim="Uw webtrees-stamboom als desktopprogramma",
               vorwort="Deze handleiding wordt gemaakt uit de ingebouwde hulp van het programma (toets F1) en heeft dezelfde stand. Suggesties en vragen: github.com/thobgg/app4webtrees/issues.",
               monate="januari februari maart april mei juni juli augustus september oktober november december", datum="{t} {m} {j}",
               achtung=("let op", "waarschuwing")),
    "es": dict(babel="spanish,es-noshorthands", titel="Manual", kapitel="capítulo", version="Versión", stand="a",
               claim="Tu árbol genealógico de webtrees como programa de escritorio",
               vorwort="Este manual se genera a partir de la ayuda integrada del programa (tecla F1) y refleja exactamente su estado. Sugerencias y preguntas: github.com/thobgg/app4webtrees/issues.",
               monate="enero febrero marzo abril mayo junio julio agosto septiembre octubre noviembre diciembre", datum="{t} de {m} de {j}",
               achtung=("atención", "advertencia", "aviso")),
}


UEBERSCHRIFT = re.compile(r"^(#{1,3})\s+(.*)$")
NUMMER = re.compile(r"^\d+\.\s+(.*)$")
TRENNZEILE = re.compile(r"^:?-+:?$")
BILDZEILE = re.compile(r"^!\[([^\]]*)]\(([^)]+)\)$")
INLINE = re.compile(r"\*\*(.+?)\*\*|`([^`]+)`|\[([^\]]+)]\(([^)]+)\)|\*(.+?)\*")
ESC = {"\\": r"\textbackslash{}", "&": r"\&", "%": r"\%", "$": r"\$", "#": r"\#", "_": r"\_",
       "{": r"\{", "}": r"\}", "~": r"\textasciitilde{}", "^": r"\textasciicircum{}"}


def esc(s: str) -> str:
    return "".join(ESC.get(c, c) for c in s).replace("ⓘ", r"\textcircled{\scriptsize i}")


def inline(text: str, kapitelwort: str) -> str:
    out, pos = [], 0
    for m in INLINE.finditer(text):
        out.append(esc(text[pos:m.start()]))
        fett, taste, linktext, ziel, kursiv = m.groups()
        if fett:
            out.append(r"\textbf{" + inline(fett, kapitelwort) + "}")
        elif taste:
            out.append(r"\taste{" + esc(taste) + "}")
        elif linktext:
            if ziel.startswith("hilfe:"):
                kid = ziel[6:].split("#")[0]
                out.append(r"\hyperref[kap:%s]{%s}\,(%s~\ref{kap:%s})" % (kid, inline(linktext, kapitelwort), kapitelwort, kid))
            else:
                out.append(r"\href{%s}{%s}" % (ziel.replace("%", r"\%").replace("#", r"\#"), inline(linktext, kapitelwort)))
        else:
            out.append(r"\emph{" + inline(kursiv, kapitelwort) + "}")
        pos = m.end()
    out.append(esc(text[pos:]))
    return "".join(out)


def kapitel_tex(kid: str, md: str, t: dict) -> str:
    zeilen = md.splitlines()
    tex, i, erste = [], 0, True
    kw = t["kapitel"]

    def punkt(z): return z.startswith("- ") or z.startswith("* ")

    while i < len(zeilen):
        z = zeilen[i].rstrip()
        if not z.strip():
            i += 1; continue
        m = UEBERSCHRIFT.match(z)
        if m:
            stufe, text = len(m.group(1)), inline(m.group(2), kw)
            if stufe == 1 and erste:
                tex.append(r"\chapter{%s}\label{kap:%s}" % (text, kid)); erste = False
            else:
                tex.append({1: r"\section", 2: r"\section", 3: r"\subsection"}[stufe] + "{" + text + "}")
            i += 1; continue
        if BILDZEILE.match(z):
            bilder = []
            while i < len(zeilen) and BILDZEILE.match(zeilen[i].rstrip()):
                m = BILDZEILE.match(zeilen[i].rstrip()); bilder.append((m.group(1), m.group(2))); i += 1
            bilder = [(a, b) for a, b in bilder if (BILDER / b).exists()]
            if len(bilder) == 1:
                tex.append(r"\noindent\bild{%s}" % bilder[0][1])
            else:
                for j in range(0, len(bilder), 2):
                    paar = bilder[j:j + 2]
                    tex.append(r"\noindent" + r"\hfill".join(r"\bild[0.485\linewidth]{%s}" % b for _, b in paar) + (r"\hfill\mbox{}" if len(paar) == 1 else ""))
                    tex.append(r"\par\vspace{4pt}")
            tex.append(""); continue
        if z.startswith(">"):
            teile = []
            while i < len(zeilen) and zeilen[i].rstrip().startswith(">"):
                teile.append(zeilen[i].rstrip().removeprefix(">").strip()); i += 1
            text = " ".join(teile)
            art = "achtung" if text.lower().lstrip("*").startswith(t["achtung"]) else "hinweis"
            tex.append(r"\begin{%s}%s\end{%s}" % (art, inline(text, kw), art)); continue
        if punkt(z) or NUMMER.match(z):
            nummeriert = bool(NUMMER.match(z)); punkte = []
            while i < len(zeilen):
                p = zeilen[i].rstrip()
                if nummeriert and NUMMER.match(p): punkte.append(NUMMER.match(p).group(1))
                elif not nummeriert and punkt(p): punkte.append(p[2:].strip())
                elif p.startswith("  ") and p.strip() and punkte: punkte[-1] += " " + p.strip()
                else: break
                i += 1
            umg = "enumerate" if nummeriert else "itemize"
            tex.append(r"\begin{%s}" % umg)
            tex += [r"\item " + inline(p, kw) for p in punkte]
            tex.append(r"\end{%s}" % umg); continue
        if z.startswith("|"):
            reihen = []
            while i < len(zeilen) and zeilen[i].rstrip().startswith("|"):
                zellen = [c.strip() for c in zeilen[i].strip().strip("|").split("|")]
                if not all(TRENNZEILE.match(c) for c in zellen): reihen.append(zellen)
                i += 1
            if reihen:
                n = max(len(r) for r in reihen)
                spalten = "l" + "Y" * (n - 1) if n <= 3 else "Y" * n
                tex.append(r"\begin{tabularx}{\linewidth}{%s}\toprule" % spalten)
                tex.append(" & ".join(r"\wtKopf{%s}" % inline(c, kw) for c in reihen[0]) + r" \\ \midrule")
                for r in reihen[1:]:
                    r = r + [""] * (n - len(r))
                    tex.append(" & ".join(inline(c, kw) for c in r) + r" \\")
                tex.append(r"\bottomrule\end{tabularx}"); tex.append("")
            continue
        teile = []
        while i < len(zeilen):
            p = zeilen[i].rstrip()
            if not p.strip() or BILDZEILE.match(p) or UEBERSCHRIFT.match(p) or p.startswith(">") or punkt(p) or NUMMER.match(p) or p.startswith("|"): break
            teile.append(p.strip()); i += 1
        tex.append(inline(" ".join(teile), kw)); tex.append("")
    return "\n".join(tex) + "\n"


PROGRAMM = "wtWin"   # oder "gedaudit" (Argument 3)

GEDAUDIT = {
    "de": dict(claim="Prüft GEDCOM-Dateien auf Widersprüche", vorwort="Dieses Handbuch entsteht aus der Hilfe des Programms (Taste F1) und hat denselben Stand. Das Kapitel zur Plausibilitätsprüfung stammt aus wtWin; Menüwege und das Bearbeiten aus dem Treffer gelten dort, in gedaudit wird nur gelesen. Verbesserungen und Fragen bitte als Issue unter github.com/thobgg/app4webtrees."),
    "en": dict(claim="Checks GEDCOM files for contradictions", vorwort="This manual is generated from the program’s built-in help (key F1) and matches it exactly. The chapter on the plausibility check comes from wtWin; its menu paths and editing from a finding apply there, gedaudit only reads. Suggestions and questions: github.com/thobgg/app4webtrees."),
    "fr": dict(claim="Vérifie les fichiers GEDCOM", vorwort="Ce manuel est généré à partir de l’aide intégrée du programme (touche F1) et en reflète exactement l’état. Le chapitre sur la vérification de cohérence vient de wtWin ; ses chemins de menu et la modification depuis un résultat s’appliquent là-bas, gedaudit ne fait que lire. Suggestions et questions : github.com/thobgg/app4webtrees."),
    "nl": dict(claim="Controleert GEDCOM-bestanden op tegenstrijdigheden", vorwort="Deze handleiding wordt gemaakt uit de ingebouwde hulp van het programma (toets F1) en heeft dezelfde stand. Het hoofdstuk over de plausibiliteitscontrole komt uit wtWin; de menupaden en het bewerken vanuit een bevinding gelden daar, gedaudit leest alleen. Suggesties en vragen: github.com/thobgg/app4webtrees."),
    "es": dict(claim="Comprueba archivos GEDCOM", vorwort="Este manual se genera a partir de la ayuda integrada del programa (tecla F1) y coincide con ella. El capítulo sobre la comprobación de plausibilidad procede de wtWin; sus rutas de menú y la edición desde un hallazgo se aplican allí, gedaudit solo lee. Sugerencias y preguntas: github.com/thobgg/app4webtrees."),
}


def reihenfolge() -> list[str]:
    if PROGRAMM == "gedaudit":
        return ["gedaudit", "pruefung"]
    m = re.search(r"REIHENFOLGE\s*=\s*listOf\(([^)]*)\)", HILFE_KT.read_text())
    return re.findall(r'"([a-z]+)"', m.group(1))


def version() -> str:
    if PROGRAMM == "gedaudit":
        # eigenes Repository (thobgg/gedaudit) unter ~/AI-Projects; dort steht die Nummer
        datei = next(p for p in (WURZEL.parent.parent / "gedaudit" / "gradle.properties",
                                 WURZEL.parent / "gedaudit" / "gradle.properties") if p.exists())
        m = re.search(r"^gedaudit\.versionName=(.+)$", datei.read_text(), re.M)
        return m.group(1).strip()
    m = re.search(r"^wtand\.versionName=(.+)$", (WURZEL / "gradle.properties").read_text(), re.M)
    return m.group(1).strip()


def hauptdatei(sprache: str, t: dict, ids: list[str]) -> str:
    heute = date.today()
    datum = t["datum"].format(t=heute.day, m=t["monate"].split()[heute.month - 1], j=heute.year)
    g = PROGRAMM == "gedaudit"
    zeile = "%s %s · %s %s · %s" % (t["version"], version(), t["stand"], datum, "gedaudit.bgg-home.de" if g else "wtwin.bgg-home.de")
    inputs = "\n".join(r"\input{kapitel/%s}" % k for k in ids)
    name = "gedaudit" if g else "wtWin"
    logo = "gedaudit.png" if g else "ahnenzeichen.png"
    untertitel = ("gedaudit\\\\[2pt]" + GEDAUDIT[sprache]["claim"]) if g else ("wtWin · wtTux · wtMac\\\\[2pt]" + t["claim"])
    vorwort = GEDAUDIT[sprache]["vorwort"] if g else t["vorwort"]
    return r"""%% erzeugt von md2tex.py – nicht von Hand aendern
\documentclass[a4paper, 11pt, parskip=half, headings=small, numbers=noenddot, chapterprefix=false, open=any]{scrbook}
\usepackage[%(babel)s]{babel}
\usepackage[margin=24mm, top=22mm, bottom=24mm]{geometry}
\usepackage{wtwinci}
\usepackage{csquotes}
\graphicspath{{%(bilder)s/}{%(ci)s/logo/}}
\wtFusszeile{%(name)s · %(titel)s · %(zeile)s}
\hypersetup{pdftitle={%(name)s %(titel)s}, pdfauthor={%(name)s}, pdflang={%(sprache)s}}
\setcounter{tocdepth}{1}
\begin{document}
\wtTitelseite{%(logo)s}{%(name)s\\%(titel)s}{%(untertitel)s}{%(zeile)s}
\frontmatter
{\small\sffamily\color{wtGrau}\noindent %(vorwort)s\par}
\tableofcontents
\mainmatter
%(inputs)s
\end{document}
""" % dict(babel=t["babel"], bilder=BILDER, ci=CI, titel=t["titel"], zeile=zeile, sprache=sprache,
           name=name, logo=logo, untertitel=untertitel, vorwort=esc(vorwort), inputs=inputs)


def main():
    global PROGRAMM
    sprache, ziel = sys.argv[1], Path(sys.argv[2])
    if len(sys.argv) > 3 and sys.argv[3] == "gedaudit":
        PROGRAMM = "gedaudit"
    t = TEXTE[sprache]
    ids = reihenfolge()
    (ziel / "kapitel").mkdir(parents=True, exist_ok=True)
    for kid in ids:
        quelle = next(p for p in (HILFE / sprache / f"{kid}.md", HILFE / "en" / f"{kid}.md", HILFE / "de" / f"{kid}.md") if p.exists())
        (ziel / "kapitel" / f"{kid}.tex").write_text(kapitel_tex(kid, quelle.read_text(encoding="utf-8"), t), encoding="utf-8")
    (ziel / "handbuch.tex").write_text(hauptdatei(sprache, t, ids), encoding="utf-8")
    print(f"{sprache}: {len(ids)} Kapitel → {ziel}")


if __name__ == "__main__":
    main()
