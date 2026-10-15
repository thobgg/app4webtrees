package de.bgghome.webtrees.nativ.gedcom

import de.bgghome.webtrees.nativ.pruefung.FormFund

/*
 * Form der Datei fuer die Regeln 620-625 (gedaudit, 15.10.2026): Pflichtangaben im Kopf, FILE gegen den Dateinamen,
 * die Angabe zum GEDCOM-L-Addendum (Kap. 7.3: "GEDCOM-L Addendum", Version "R2 (2020-12-01)", https://genealogy.net/GEDCOM),
 * das Datum im Kopf, Verstoesse gegen GEDCOM 7 und Text hinter Ereignissen. Gibt nur Funde zurueck; die Saetze dazu
 * baut der Pruefmotor aus den Schluesseln (pruef_<schluessel>).
 */
object GedcomForm {

    /** Pflichtangaben im Kopf. In 5.5.1: SOUR, SUBM, GEDC mit VERS und FORM, CHAR; in 7: GEDC mit VERS. */
    fun kopf(d: GedcomDatei, sieben: Boolean): List<FormFund> {
        val k = d.kopf ?: return emptyList()   // fehlender Kopf: Regel 617
        val out = ArrayList<FormFund>()
        val gedc = k.erstes("GEDC")
        if (!sieben && k.wert("SOUR") == null) out += FormFund("kopf_ohne_sour")
        if (!sieben && k.erstes("SUBM") == null) out += FormFund("kopf_ohne_subm")
        if (gedc?.wert("VERS") == null) out += FormFund("kopf_ohne_gedc")
        if (!sieben && gedc != null && gedc.wert("FORM") == null) out += FormFund("kopf_ohne_form")
        if (!sieben && k.wert("CHAR") == null) out += FormFund("kopf_ohne_char")
        val einsender = d.saetze.filter { it.art == "SUBM" }.map { it.xref }.toSet()
        k.wert("SUBM")?.trim()?.let { v -> if (v.trim('@') !in einsender) out += FormFund("kopf_subm_leer", v) }
        return out
    }

    /** FILE im Kopf und der tatsaechliche Dateiname, wenn sie (ohne Pfad, Gross-/Kleinschreibung egal) abweichen. */
    fun dateiname(d: GedcomDatei, name: String?): Pair<String, String>? {
        val angabe = d.kopf?.wert("FILE")?.trim() ?: return null
        val ist = name?.substringAfterLast('/')?.substringAfterLast('\\') ?: return null
        val soll = angabe.substringAfterLast('/').substringAfterLast('\\')
        return if (soll.equals(ist, ignoreCase = true)) null else angabe to ist
    }

    private val ADDENDUM_VERS = Regex("^R\\d+ \\(\\d{4}-\\d{2}-\\d{2}\\)$")

    /** Die Angabe zum GEDCOM-L-Addendum, wo sie von der Empfehlung (Kap. 7.3) abweicht; in GEDCOM 7 gilt sie nicht. */
    fun addendum(d: GedcomDatei, sieben: Boolean): List<FormFund> {
        val angaben = d.kopf?.erstes("GEDC")?.erstes("VERS")?.alle("_ADDENDUM").orEmpty()
        return angaben.flatMap { a ->
            if (sieben) return@flatMap listOf(FormFund("addendum_sieben", a.wert.trim()))
            buildList {
                if (!a.wert.trim().equals("GEDCOM-L Addendum", ignoreCase = true)) add(FormFund("addendum_name", a.wert.trim()))
                val vers = a.wert("VERS")?.trim().orEmpty()
                if (!ADDENDUM_VERS.matches(vers)) add(FormFund("addendum_vers", vers))
                val www = a.wert("WWW")?.trim().orEmpty()
                if (www.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/').lowercase() != "genealogy.net/gedcom")
                    add(FormFund("addendum_www", www))
            }
        }
    }

    private val GENAUES_DATUM = Regex("^\\d{1,2} (JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC) \\d{3,4}$")

    /** Das Datum im Kopf, wenn es kein genaues Datum ist (1 DATE 15 OCT 2026). */
    fun kopfDatum(d: GedcomDatei): String? = d.kopf?.wert("DATE")?.trim()?.takeIf { !GENAUES_DATUM.matches(it.uppercase()) }

    /** Ereignisse mit Text dahinter: GEDCOM erlaubt dort nur Y (5.5.1: BIRT, CHR, DEAT, MARR) oder nichts. */
    fun ereignisText(d: GedcomDatei): List<FormFund> = d.saetze.filter { it.art == "INDI" || it.art == "FAM" }.flatMap { s ->
        s.knoten.kinder.filter { it.tag in EREIGNISSE && it.wert.isNotBlank() && it.wert.trim() != "Y" }
            .map { FormFund("ereignis_text", "${it.tag} „${it.wert.trim().take(60)}“", s.xref) }
    }

    private val EREIGNISSE = setOf("BIRT", "CHR", "DEAT", "BURI", "CREM", "ADOP", "BAPM", "BARM", "BASM", "BLES", "CHRA", "CONF",
        "FCOM", "ORDN", "NATU", "EMIG", "IMMI", "PROB", "WILL", "GRAD", "RETI", "CENS", "ANUL", "DIV", "DIVF", "ENGA", "MARB",
        "MARC", "MARL", "MARS", "MARR")

    /** Aufzaehlungen von GEDCOM 7 (Werte gross, aus einer festen Liste) und unter welchem Tag sie gelten (null = ueberall). */
    private class Aufzaehlung(val ueber: Set<String>?, val werte: Set<String>, val liste: Boolean = false)

    private val AUFZAEHLUNGEN = mapOf(
        "ROLE" to Aufzaehlung(null, setOf("CHIL", "CLERGY", "FATH", "FRIEND", "GODP", "HUSB", "MOTH", "MULTIPLE", "NGHBR",
            "OFFICIATOR", "OTHER", "PARENT", "SPOU", "WIFE", "WITN")),
        "MEDI" to Aufzaehlung(null, setOf("AUDIO", "BOOK", "CARD", "ELECTRONIC", "FICHE", "FILM", "MAGAZINE", "MANUSCRIPT",
            "MAP", "NEWSPAPER", "OTHER", "PHOTO", "TOMBSTONE", "VIDEO")),
        "PEDI" to Aufzaehlung(null, setOf("ADOPTED", "BIRTH", "FOSTER", "SEALING", "OTHER")),
        "SEX" to Aufzaehlung(null, setOf("M", "F", "X", "U")),
        "QUAY" to Aufzaehlung(null, setOf("0", "1", "2", "3")),
        "RESN" to Aufzaehlung(null, setOf("CONFIDENTIAL", "LOCKED", "PRIVACY"), liste = true),
        "TYPE" to Aufzaehlung(setOf("NAME"), setOf("AKA", "BIRTH", "IMMIGRANT", "MAIDEN", "MARRIED", "PROFESSIONAL", "OTHER")),
    )

    private val SPRACHCODE = Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]{1,8})*$")

    /**
     * Was eine Datei als GEDCOM 7 falsch macht: CONC, CHAR/FILE/GEDC.FORM im Kopf, FORM ohne Medientyp, Aufzaehlungswerte
     * ausserhalb der Liste, EXID ohne TYPE, LANG ohne Sprachcode, Tags, die es nur in 5.5.1 gibt. In Erweiterungen
     * (Tags mit Unterstrich) wird nicht hineingeschaut - deren Inhalt bestimmt die Erweiterung.
     */
    fun sieben(d: GedcomDatei): List<FormFund> {
        val zahl = LinkedHashMap<Pair<String, String>, Int>(); val beispiel = HashMap<Pair<String, String>, String>()
        fun fund(schluessel: String, detail: String, satz: String) {
            val key = schluessel to detail
            zahl.merge(key, 1, Int::plus); beispiel.putIfAbsent(key, satz)
        }
        d.kopf?.let { k ->
            listOf("CHAR", "FILE").forEach { t -> if (k.erstes(t) != null) fund("g7_kopf_tag", t, "") }
            if (k.erstes("GEDC")?.erstes("FORM") != null) fund("g7_kopf_tag", "GEDC FORM", "")
        }
        fun gehe(k: GedcomKnoten, satz: String) {
            k.kinder.forEach { n ->
                if (n.tag.startsWith("_")) return@forEach
                val w = n.wert.trim()
                when {
                    n.tag !in GedcomLeser.STANDARD_70 && n.tag in GedcomLeser.STANDARD_551 && !(k.tag == "HEAD" && n.tag in setOf("CHAR", "FILE")) ->
                        fund("g7_alt_tag", n.tag, satz)
                    n.tag == "FORM" && k.tag == "FILE" && !w.contains('/') -> fund("g7_medientyp", "FORM $w", satz)
                    n.tag == "TYPE" && k.tag == "FORM" -> fund("g7_alt_tag", "FORM TYPE", satz)
                    n.tag == "EXID" && n.erstes("TYPE") == null -> fund("g7_exid_type", "EXID $w", satz)
                    n.tag == "LANG" && w.isNotEmpty() && !SPRACHCODE.matches(w) -> fund("g7_sprache", "LANG $w", satz)
                }
                AUFZAEHLUNGEN[n.tag]?.let { a ->
                    if ((a.ueber == null || k.tag in a.ueber) && w.isNotEmpty()) {
                        val werte = if (a.liste) w.split(',').map { it.trim() } else listOf(w)
                        if (werte.any { it !in a.werte }) fund("g7_aufzaehlung", "${n.tag} $w", satz)
                    }
                }
                gehe(n, satz)
            }
        }
        d.saetze.forEach { s -> if (!s.art.startsWith("_") && s.art != "HEAD") gehe(s.knoten, s.xref) }
        val out = ArrayList<FormFund>()
        if (d.concZeilen > 0) out += FormFund("g7_conc", "", null, d.concZeilen)
        zahl.forEach { (key, n) -> out += FormFund(key.first, key.second, beispiel[key]?.takeIf { it.isNotEmpty() }, n) }
        return out
    }
}
