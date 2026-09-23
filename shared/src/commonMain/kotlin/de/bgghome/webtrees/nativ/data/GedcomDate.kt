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
    )

    private val qualifiers = listOf(
        listOf("um ", "ca. ", "ca ", "circa ", "etwa ", "about ", "around ", "abt. ") to "ABT",
        listOf("vor ", "before ") to "BEF",
        listOf("nach ", "after ") to "AFT",
        listOf("ab ", "seit ", "since ") to "FROM",
        listOf("bis ", "until ") to "TO",
    )

    fun fromInput(input: String): String {
        val text = input.trim().replace(Regex("\\s+"), " ")

        if (text.isEmpty()) return ""

        val lower = text.lowercase()

        Regex("^(?:zwischen|zw\\.?|between) (.+) (?:und|u\\.|and) (.+)$").find(lower)?.let {
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
        Regex("^(?:(\\d{1,2})\\.? )?([a-zäöü]+)\\.? (\\d{3,4})$").find(t)?.let { m ->
            monthNames[m.groupValues[2]]?.let { month ->
                val day = m.groupValues[1]
                return (if (day.isEmpty()) "" else "${day.toInt()} ") + "${months[month - 1]} ${m.groupValues[3]}"
            }
        }
        // March 12, 1890
        Regex("^([a-z]+)\\.? (\\d{1,2}),? (\\d{3,4})$").find(t)?.let { m ->
            monthNames[m.groupValues[1]]?.let { month ->
                return "${m.groupValues[2].toInt()} ${months[month - 1]} ${m.groupValues[3]}"
            }
        }

        return t.uppercase()
    }
}
