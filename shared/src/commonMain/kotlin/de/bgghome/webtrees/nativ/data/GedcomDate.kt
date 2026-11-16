package de.bgghome.webtrees.nativ.data

/**
 * Turns what people type into a GEDCOM date - German and English:
 *   "12.3.1890" -> "12 MAR 1890",  "3.1890" -> "MAR 1890",  "1890" -> "1890",
 *   "12. März 1890" / "12 March 1890" / "March 12, 1890" -> "12 MAR 1890",
 *   "um 1850" / "ca. 1850" / "about 1850" -> "ABT 1850",  "vor 1900" / "before 1900" -> "BEF 1900",
 *   "nach 1.5.1900" / "after ..." -> "AFT 1 MAY 1900",  "ab 1950" / "since 1950" -> "FROM 1950",
 *   "1900-1910" / "zwischen 1900 und 1910" / "between 1900 and 1910" -> "BET 1900 AND 1910".
 * Whatever already is GEDCOM ("ABT 1850", "12 MAR 1890") stays as it is (upper-cased).
 * Slashes are left alone on purpose: 3/12/1890 means March 12 in the US and 3 December elsewhere.
 * The server validates the result; nonsense comes back as "invalid-date".
 */
object GedcomDate {

    private val months = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

    private val monthNames = mapOf(
        // Deutsch
        "januar" to 1, "jan" to 1, "februar" to 2, "feb" to 2, "märz" to 3, "maerz" to 3, "mär" to 3, "mrz" to 3,
        "april" to 4, "apr" to 4, "mai" to 5, "juni" to 6, "jun" to 6, "juli" to 7, "jul" to 7,
        "august" to 8, "aug" to 8, "september" to 9, "sep" to 9, "sept" to 9, "oktober" to 10, "okt" to 10,
        "november" to 11, "nov" to 11, "dezember" to 12, "dez" to 12,
        // English (GEDCOM's own three-letter forms are among them)
        "january" to 1, "february" to 2, "march" to 3, "mar" to 3, "may" to 5, "june" to 6, "july" to 7,
        "october" to 10, "oct" to 10, "december" to 12, "dec" to 12,
        // Français
        "janvier" to 1, "janv" to 1, "février" to 2, "fevrier" to 2, "févr" to 2, "fév" to 2, "fev" to 2, "mars" to 3,
        "avril" to 4, "avr" to 4, "juin" to 6, "juillet" to 7, "juil" to 7, "août" to 8, "aout" to 8,
        "septembre" to 9, "octobre" to 10, "novembre" to 11, "décembre" to 12, "decembre" to 12, "déc" to 12,
        // Nederlands
        "januari" to 1, "februari" to 2, "maart" to 3, "mrt" to 3, "mei" to 5, "augustus" to 8, "oktober" to 10, "december" to 12,
        // Español
        "enero" to 1, "ene" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4, "abr" to 4, "mayo" to 5, "junio" to 6,
        "julio" to 7, "agosto" to 8, "ago" to 8, "septiembre" to 9, "setiembre" to 9, "set" to 9, "octubre" to 10,
        "noviembre" to 11, "diciembre" to 12, "dic" to 12,
    )

    private val qualifiers = listOf(
        // geschaetzt und berechnet, auch in der Form, in der webtrees sie anzeigt (sonst ginge EST/CAL beim Bearbeiten verloren)
        listOf("geschätzt ", "geschaetzt ", "estimated ", "est. ", "estimé ", "estime ", "geschat ", "estimado ", "estimada ", "estimadas ", "estimados ") to "EST",
        listOf("berechnet ", "errechnet ", "calculated ", "calc. ", "calculé ", "calcule ", "berekend ", "calculado ", "calculada ", "calculadas ", "calculados ") to "CAL",
        listOf("um ", "ca. ", "ca ", "circa ", "etwa ", "about ", "around ", "abt. ", "vers ", "env. ", "environ ", "rond ", "omstreeks ", "ongeveer ", "hacia ", "sobre ", "aprox. ", "aproximadamente ") to "ABT",
        listOf("vor ", "before ", "avant ", "voor ", "vóór ", "antes de ", "antes ") to "BEF",
        listOf("nach ", "after ", "après ", "apres ", "na ", "después de ", "despues de ", "después ") to "AFT",
        listOf("ab ", "seit ", "since ", "von ", "from ", "vanaf ", "desde ", "de ") to "FROM",
        listOf("bis ", "until ", "to ", "tot ", "hasta ", "à ") to "TO",
    )

    fun fromInput(input: String): String {
        val text = input.trim().replace(Regex("\\s+"), " ")

        if (text.isEmpty()) return ""

        alsGedcom(text)?.let { return it }

        val lower = text.lowercase()

        // "von 1915 bis 1918" - auch so, wie webtrees FROM ... TO anzeigt
        Regex("^(?:von|from|vanaf|desde|de) (.+) (?:bis|to|tot|hasta|à) (.+)$").find(lower)?.let {
            return "FROM " + simple(it.groupValues[1]) + " TO " + simple(it.groupValues[2])
        }
        // "1865 (geschat)", "1865 estimadas": nachgestellt wie in der niederlaendischen und spanischen Anzeige von webtrees
        Regex("^(.+?) (?:\\(geschat\\)|estimad[ao]s?)$").find(lower)?.let { return "EST " + simple(it.groupValues[1]) }
        Regex("^(.+?) (?:\\(berekend\\)|calculad[ao]s?)$").find(lower)?.let { return "CAL " + simple(it.groupValues[1]) }

        Regex("^(?:zwischen|zw\\.?|between|entre|tussen) (.+) (?:und|u\\.|and|et|en|y) (.+)$").find(lower)?.let {
            return "BET " + simple(it.groupValues[1]) + " AND " + simple(it.groupValues[2])
        }
        Regex("^(\\d{3,4}) ?[-–] ?(\\d{3,4})$").find(lower)?.let {
            return "BET " + it.groupValues[1] + " AND " + it.groupValues[2]
        }

        for ((prefixes, keyword) in qualifiers) {
            prefixes.firstOrNull { lower.startsWith(it) }?.let { prefix ->
                return keyword + " " + simple(lower.removePrefix(prefix))
            }
        }

        return simple(lower)
    }

    private const val TAG_MONAT = "(?:\\d{1,2} )?(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC|VEND|BRUM|FRIM|NIVO|PLUV|VENT|GERM|FLOR|PRAI|MESS|THER|FRUC|COMP|TSH|CSH|KSL|TVT|SHV|ADR|ADS|NSN|IYR|SVN|TMZ|AAV|ELL) "
    private const val EIN_DATUM = "(?:@#D[A-Z ]+@ )?(?:$TAG_MONAT)?\\d{1,4}(?:/\\d{2})?(?: B\\.C\\.)?"
    private val GEDCOM_FORM = Regex(
        "^(?:@#D[A-Z ]+@ )?(?:$EIN_DATUM|(?:ABT|CAL|EST|BEF|AFT|TO) $EIN_DATUM|BET $EIN_DATUM AND $EIN_DATUM|" +
            "FROM $EIN_DATUM(?: TO $EIN_DATUM)?|INT $EIN_DATUM \\(.*\\)|\\(.*\\))$",
    )

    /**
     * Ist die Eingabe schon ein GEDCOM-Datum (gleich in welcher Schreibung der Schluesselwoerter), kommt sie mit
     * grossgeschriebenen Schluesselwoertern zurueck - der Text in Klammern ("INT 1865 (Ostern)") bleibt, wie er ist.
     */
    fun alsGedcom(input: String): String? {
        val t = input.trim().replace(Regex("\\s+"), " ")
        val i = t.indexOf('(')
        val kopf = (if (i < 0) t else t.substring(0, i)).trim().uppercase()
        val kandidat = if (i < 0) kopf else (kopf + " " + t.substring(i)).trim()
        return kandidat.takeIf { it.isNotEmpty() && GEDCOM_FORM.matches(it) }
    }

    /** Ob ein Text ein GEDCOM-Datum ist (fuer die Pruefung vor dem Speichern). */
    fun istGedcom(text: String): Boolean = alsGedcom(text) == text.trim()

    /** A single date without qualifier. */
    private fun simple(text: String): String {
        val t = text.trim()

        // 12.3.1890 / 12-3-1890
        Regex("^(\\d{1,2})[.-](\\d{1,2})[.-](\\d{3,4})$").find(t)?.let { m ->
            val month = m.groupValues[2].toInt()
            if (month in 1..12) return "${m.groupValues[1].toInt()} ${months[month - 1]} ${m.groupValues[3]}"
        }
        // 3.1890
        Regex("^(\\d{1,2})[.-](\\d{3,4})$").find(t)?.let { m ->
            val month = m.groupValues[1].toInt()
            if (month in 1..12) return "${months[month - 1]} ${m.groupValues[2]}"
        }
        // 12. März 1890 / 12 March 1890 / März 1890
        Regex("^(?:(\\d{1,2})\\.? )?(\\p{L}+)\\.? (\\d{3,4})$").find(t)?.let { m ->
            monthNames[m.groupValues[2]]?.let { month ->
                val day = m.groupValues[1]
                return (if (day.isEmpty()) "" else "${day.toInt()} ") + "${months[month - 1]} ${m.groupValues[3]}"
            }
        }
        // March 12, 1890
        Regex("^(\\p{L}+)\\.? (\\d{1,2}),? (\\d{3,4})$").find(t)?.let { m ->
            monthNames[m.groupValues[1]]?.let { month ->
                return "${m.groupValues[2].toInt()} ${months[month - 1]} ${m.groupValues[3]}"
            }
        }

        return t.uppercase()
    }
}
