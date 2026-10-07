package de.bgghome.webtrees.nativ.data

/*
 * Ortsnamen und Ortsarten fuer Haeuserbuch und Ortsverwaltung (07.10.2026).
 *
 * Ein Ortsname ist eine Kette von Ebenen, in webtrees mit Komma getrennt ("Hof Nr. 3, Bienenbuettel, Uelzen"); manche
 * Bestaende gliedern den Namen zusaetzlich mit Semikolon ("Klosterstrasse 6; Ennetach; Mengen"). Beides gilt hier als
 * Trenner, damit das Blatt (das Haus) und der Ort darueber in beiden Schreibweisen gefunden werden.
 *
 * Die Art eines Orts (TYPE am _LOC) entscheidet, was im Haeuserbuch ein Haus ist: Haus, Hof, Muehle, Kirche ... in den
 * fuenf Sprachen der App - und was eine hoehere Ebene ist (Stadtteil, Gemeinde, Kreis ...), die nur gliedert. Ohne Art
 * zaehlt eine Hausnummer im Namen.
 */

object Ortsnamen {
    private val TRENNER = Regex("\\s*[,;]\\s*")

    /** Die Ebenen eines Ortsnamens, Blatt zuerst, ohne leere Teile. */
    fun teile(name: String): List<String> = name.split(TRENNER).map(String::trim).filter(String::isNotEmpty)

    /** Das Blatt: der erste Teil ("Hof Nr. 3"). */
    fun blatt(name: String): String = teile(name).firstOrNull().orEmpty()

    /** Der Ort darueber, wieder als Kette ("Bienenbuettel, Uelzen"). */
    fun oberort(name: String): String = teile(name).drop(1).joinToString(", ")

    /** Schluessel zum Vergleichen: Ebenen kleingeschrieben, einheitlich mit Komma. */
    fun schluessel(name: String): String = teile(name).joinToString(", ").lowercase()

    /** "Hof Nr. 3" vor "Hof Nr. 12": Zahlen im Namen als Zahlen vergleichen, sonst ohne Gross/Klein. */
    val NATUERLICH: Comparator<String> = Comparator { a, b ->
        val stuecke = Regex("\\d+|\\D+")
        val x = stuecke.findAll(a.lowercase()).map { it.value }.toList()
        val y = stuecke.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val c = if (x[i][0].isDigit() && y[i][0].isDigit()) x[i].trimStart('0').length.compareTo(y[i].trimStart('0').length).takeIf { it != 0 } ?: x[i].trimStart('0').compareTo(y[i].trimStart('0'))
                else x[i].compareTo(y[i])
            if (c != 0) return@Comparator c
        }
        x.size - y.size
    }
}

/** Was ein Ort im Haeuserbuch ist: ein Gebaeude, eine Ebene darueber oder nicht zu entscheiden. */
enum class OrtsKlasse { HAUS, OBER, UNBEKANNT }

object Haustypen {
    /** Arten, die ein Gebaeude bezeichnen (TYPE am _LOC), de/en/fr/nl/es, kleingeschrieben. */
    val HAUS: Set<String> = setOf(
        "haus", "hof", "hofstelle", "hofstatt", "bauernhof", "gehöft", "gehoeft", "häusergruppe", "haeusergruppe", "anwesen", "gebäude", "gebaeude",
        "mühle", "muehle", "wassermühle", "windmühle", "gasthaus", "gasthof", "wirtshaus", "krug", "kirche", "kapelle", "kloster", "friedhof",
        "schule", "pfarrhaus", "burg", "schloss", "gut", "rittergut", "vorwerk", "kate", "kotten", "kötterei", "häuslingshaus", "brauerei",
        "schmiede", "villa", "fabrik", "bahnhof", "wohnhaus",
        "house", "farm", "farmstead", "homestead", "cottage", "manor", "estate", "mill", "inn", "church", "chapel", "cemetery", "churchyard",
        "school", "castle", "building", "dwelling",
        "maison", "ferme", "métairie", "metairie", "moulin", "auberge", "église", "eglise", "chapelle", "cimetière", "cimetiere", "château", "chateau", "domaine", "école", "ecole",
        "huis", "boerderij", "hoeve", "molen", "herberg", "kerk", "kapel", "kerkhof", "begraafplaats", "kasteel", "landgoed", "school",
        "casa", "granja", "finca", "cortijo", "molino", "posada", "iglesia", "capilla", "cementerio", "castillo", "escuela",
    )

    /** Arten einer Ebene ueber dem Haus - nur Gliederung, nie im Haeuserteil. */
    val OBER: Set<String> = setOf(
        "stadtteil", "ortsteil", "ortschaft", "gemeinde", "stadt", "dorf", "ort", "flecken", "kirchspiel", "pfarrei", "amt", "kreis", "landkreis",
        "bezirk", "regierungsbezirk", "land", "bundesland", "staat", "provinz", "region", "siedlung", "bauerschaft", "gemarkung",
        "part of town", "quarter", "district", "borough", "village", "town", "city", "municipality", "parish", "county", "state", "province", "country", "region", "locality",
        "quartier", "commune", "village", "ville", "paroisse", "canton", "département", "departement", "pays",
        "wijk", "stadsdeel", "dorp", "stad", "gemeente", "parochie", "provincie", "land",
        "barrio", "pueblo", "ciudad", "municipio", "parroquia", "comarca", "provincia", "país", "pais",
    )

    /** Endet das Blatt auf eine Hausnummer ("Klosterstrasse 6", "Haus Nr. 12", "Nr. 7a")? */
    private val HAUSNUMMER = Regex("(?i)(^|\\s)(nr\\.?\\s*)?\\d+\\s*[a-z]?(\\s*\\(.*\\))?$")

    private fun arten(typ: String?): List<String> = typ.orEmpty().split(Regex("[,;/]")).map { it.trim().lowercase() }.filter(String::isNotEmpty)

    fun istHaus(typ: String?): Boolean = arten(typ).any { it in HAUS }
    fun istOber(typ: String?): Boolean = arten(typ).any { it in OBER }

    /**
     * Einordnung eines Orts: zuerst nach der GOV-Typnummer (2 _GOVTYPE, eindeutig), dann nach dem Text der Art, sonst
     * nach Lage und Namen. [hatUnterorte]: andere Orte stehen unter ihm.
     */
    fun klasse(typ: String?, govType: String?, blatt: String, hatUnterorte: Boolean): OrtsKlasse =
        GovTypen.klasse(govType) ?: klasse(typ, blatt, hatUnterorte)

    /** Einordnung ohne GOV-Typnummer: nach der Art, sonst nach Lage und Namen. */
    fun klasse(typ: String?, blatt: String, hatUnterorte: Boolean): OrtsKlasse = when {
        istHaus(typ) -> OrtsKlasse.HAUS
        istOber(typ) -> OrtsKlasse.OBER
        hatUnterorte -> OrtsKlasse.OBER
        typ.isNullOrBlank() && HAUSNUMMER.containsMatchIn(blatt.trim()) -> OrtsKlasse.HAUS
        else -> OrtsKlasse.UNBEKANNT
    }
}

/**
 * Einzelne Berufe aus einem OCCU-Wert fuer das Berufsverzeichnis: "Maurer (1882), später Polizeidiener (1914)" ->
 * Maurer, Polizeidiener. Klammerzusaetze fallen weg, getrennt wird an Komma, Semikolon, Schraegstrich und "später"/"dann".
 */
fun berufe(wert: String): List<String> =
    wert.replace(Regex("\\([^)]*\\)"), " ")
        .split(Regex("(?i)\\s*(,|;|/|\\bspäter\\b|\\bdann\\b|\\blater\\b|\\bthen\\b|\\bpuis\\b|\\bdaarna\\b|\\bluego\\b|\\bund\\b|\\band\\b)\\s*"))
        .map { it.trim().trimEnd('.') }.filter { it.length > 1 }.distinct()
