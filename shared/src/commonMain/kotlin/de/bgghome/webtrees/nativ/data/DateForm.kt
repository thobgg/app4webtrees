package de.bgghome.webtrees.nativ.data

/**
 * Ein GEDCOM-Datum als Formular: Genauigkeit plus Tag, Monat, Jahr - so waehlt man es in der App aus.
 *
 * Abgebildet wird nur, was sich mit diesen Feldern eindeutig hin- und zurueckrechnen laesst:
 *   "12 MAR 1890", "MAR 1890", "1890", "ABT 1850", "BEF 1900", "AFT 1 MAY 1900", "BET 1900 AND 1910".
 * Alles andere (CAL, EST, FROM/TO, INT mit Freitext, andere Kalender, Doppeljahre "1750/51") liefert
 * fromGedcom() als null - dann bearbeitet die App das Datum als Text, damit nichts verloren geht.
 */
data class DateForm(
    val qualifier: DateQualifier = DateQualifier.EXACT,
    val first: DatePart = DatePart(),
    /** Nur bei BETWEEN: das spaetere Ende der Spanne. */
    val second: DatePart = DatePart(),
) {
    val isEmpty: Boolean get() = first.isEmpty && (qualifier != DateQualifier.BETWEEN || second.isEmpty)

    /** Das Datum im GEDCOM-Format; "" = kein Datum, null = so nicht speicherbar (z. B. Tag ohne Monat). */
    fun toGedcom(): String? {
        if (isEmpty) return ""

        val start = first.toGedcom()?.takeIf { it.isNotEmpty() } ?: return null

        return when (qualifier) {
            DateQualifier.EXACT -> start
            DateQualifier.BETWEEN -> {
                val end = second.toGedcom()?.takeIf { it.isNotEmpty() } ?: return null
                "BET $start AND $end"
            }
            else -> "${qualifier.keyword} $start"
        }
    }

    companion object {
        private val between = Regex("^BET (.+) AND (.+)$")
        private val qualified = Regex("^(ABT|BEF|AFT) (.+)$")

        fun fromGedcom(gedcom: String): DateForm? {
            val text = gedcom.trim().uppercase().replace(Regex("\\s+"), " ")

            if (text.isEmpty()) return DateForm()

            between.find(text)?.let { m ->
                val start = DatePart.fromGedcom(m.groupValues[1]) ?: return null
                val end = DatePart.fromGedcom(m.groupValues[2]) ?: return null
                return DateForm(DateQualifier.BETWEEN, start, end)
            }
            qualified.find(text)?.let { m ->
                val qualifier = DateQualifier.entries.first { it.keyword == m.groupValues[1] }
                return DatePart.fromGedcom(m.groupValues[2])?.let { DateForm(qualifier, it) }
            }

            return DatePart.fromGedcom(text)?.let { DateForm(DateQualifier.EXACT, it) }
        }
    }
}

/** Die fuenf Genauigkeiten, die die App zur Wahl stellt (Reihenfolge = Reihenfolge der Chips). */
enum class DateQualifier(val keyword: String) {
    EXACT(""), ABOUT("ABT"), BEFORE("BEF"), AFTER("AFT"), BETWEEN("BET")
}

/**
 * Ein einzelnes Datum. Tag und Jahr bleiben Text, weil sie so im Eingabefeld stehen (auch halb getippt);
 * month = 0 heisst "Monat unbekannt".
 */
data class DatePart(val day: String = "", val month: Int = 0, val year: String = "") {

    val isEmpty: Boolean get() = day.isBlank() && month == 0 && year.isBlank()

    /** "12 MAR 1890" / "MAR 1890" / "1890"; "" = leer, null = unvollstaendig oder unmoeglich. */
    fun toGedcom(): String? {
        if (isEmpty) return ""

        // Ohne Jahr gibt es in GEDCOM kein Datum, und ein Tag braucht seinen Monat.
        val y = year.trim().toIntOrNull()?.takeIf { it in 1..9999 } ?: return null
        if (month !in 0..12) return null
        if (day.isBlank()) return if (month == 0) "$y" else "${MONTHS[month - 1]} $y"
        if (month == 0) return null

        // Den 29. Februar immer zulassen: ob das Jahr ein Schaltjahr war, prueft der Server (Julianischer Kalender!).
        val d = day.trim().toIntOrNull()?.takeIf { it in 1..DAYS_IN_MONTH[month - 1] } ?: return null

        return "$d ${MONTHS[month - 1]} $y"
    }

    companion object {
        val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        private val DAYS_IN_MONTH = listOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

        // [[Tag] Monat] Jahr - ein Tag ohne Monat ist in GEDCOM nicht vorgesehen.
        private val pattern = Regex("^(?:(?:(\\d{1,2}) )?(${MONTHS.joinToString("|")}) )?(\\d{1,4})$")

        fun fromGedcom(text: String): DatePart? {
            val m = pattern.find(text.trim()) ?: return null
            val (day, month, year) = m.destructured
            // "05" -> "5"; ein Tag "0" bleibt stehen und faellt unten durch.
            val part = DatePart(day.toIntOrNull()?.toString().orEmpty(), MONTHS.indexOf(month) + 1, year)

            // Nur uebernehmen, was sich unveraendert zurueckschreiben laesst (kein 31. April, kein Jahr 0).
            return part.takeIf { it.toGedcom() != null }
        }
    }
}
