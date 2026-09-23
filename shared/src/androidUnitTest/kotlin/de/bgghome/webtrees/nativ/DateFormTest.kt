package de.bgghome.webtrees.nativ

import de.bgghome.webtrees.nativ.data.DateForm
import de.bgghome.webtrees.nativ.data.DatePart
import de.bgghome.webtrees.nativ.data.DateQualifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DateForm: GEDCOM-Datum <-> Auswahlformular. Was fromGedcom annimmt, muss toGedcom unveraendert zurueckgeben -
 * sonst wuerde das blosse Oeffnen und Speichern eines Ereignisses dessen Datum veraendern.
 */
class DateFormTest {

    // ── Hin und zurueck ──────────────────────────────────────────────

    @Test
    fun representableDatesSurviveTheRoundTrip() {
        listOf(
            "12 MAR 1890", "MAR 1890", "1890", "850", "1 JAN 2000", "29 FEB 1900",
            "ABT 1850", "BEF 1900", "AFT 1 MAY 1900", "ABT DEC 1799",
            "BET 1900 AND 1910", "BET 3 MAR 1900 AND APR 1901",
        ).forEach { gedcom ->
            val form = DateForm.fromGedcom(gedcom)
            assertEquals(gedcom, form?.toGedcom())
        }
    }

    @Test
    fun readsTheFields() {
        assertEquals(
            DateForm(DateQualifier.ABOUT, DatePart("12", 3, "1890")),
            DateForm.fromGedcom("ABT 12 MAR 1890"),
        )
        assertEquals(
            DateForm(DateQualifier.BETWEEN, DatePart(year = "1900"), DatePart(month = 6, year = "1910")),
            DateForm.fromGedcom("BET 1900 AND JUN 1910"),
        )
    }

    @Test
    fun toleratesCaseSpacingAndLeadingZeros() {
        assertEquals("12 MAR 1890", DateForm.fromGedcom("  12  mar 1890 ")?.toGedcom())
        assertEquals("5 MAR 1890", DateForm.fromGedcom("05 MAR 1890")?.toGedcom())
    }

    @Test
    fun emptyGedcomIsAnEmptyForm() {
        val form = DateForm.fromGedcom("")
        assertTrue(form!!.isEmpty)
        assertEquals("", form.toGedcom())
    }

    // ── Was als Text bearbeitet werden muss ──────────────────────────

    @Test
    fun otherDateFormsAreLeftToTheTextField() {
        listOf(
            "CAL 1850", "EST 1850", "FROM 1950", "TO 1960", "FROM 1950 TO 1960",
            "INT 1850 (laut Kirchenbuch)", "(unbekannt)", "@#DJULIAN@ 1 JAN 1700", "@#DHEBREW@ 1 TSH 5600",
            "1750/51", "32 JAN 1900", "31 APR 1900", "0 JAN 1900", "12 1900", "MAR", "0",
        ).forEach { gedcom ->
            assertNull(gedcom, DateForm.fromGedcom(gedcom))
        }
    }

    // ── Formular -> GEDCOM ───────────────────────────────────────────

    @Test
    fun buildsGedcomFromTheFields() {
        assertEquals("12 MAR 1890", DateForm(first = DatePart("12", 3, "1890")).toGedcom())
        assertEquals("MAR 1890", DateForm(first = DatePart("", 3, "1890")).toGedcom())
        assertEquals("1890", DateForm(first = DatePart(year = " 1890 ")).toGedcom())
        assertEquals("BEF 1900", DateForm(DateQualifier.BEFORE, DatePart(year = "1900")).toGedcom())
        assertEquals("AFT 1900", DateForm(DateQualifier.AFTER, DatePart(year = "1900")).toGedcom())
        assertEquals(
            "BET 1900 AND 1910",
            DateForm(DateQualifier.BETWEEN, DatePart(year = "1900"), DatePart(year = "1910")).toGedcom(),
        )
    }

    @Test
    fun incompleteFormsCannotBeSaved() {
        assertNull(DateForm(first = DatePart("12", 3, "")).toGedcom())        // kein Jahr
        assertNull(DateForm(first = DatePart("12", 0, "1890")).toGedcom())    // Tag ohne Monat
        assertNull(DateForm(first = DatePart("31", 4, "1890")).toGedcom())    // 31. April
        assertNull(DateForm(first = DatePart(year = "0")).toGedcom())
        assertNull(DateForm(DateQualifier.ABOUT, DatePart(month = 5)).toGedcom())
        // "zwischen" braucht beide Enden
        assertNull(DateForm(DateQualifier.BETWEEN, DatePart(year = "1900")).toGedcom())
    }

    @Test
    fun anEmptyFormMeansNoDate() {
        DateQualifier.entries.forEach { q ->
            assertEquals("", DateForm(q).toGedcom())
        }
    }

    @Test
    fun theSecondDateOnlyCountsForBetween() {
        // Wer von "zwischen" auf "um" wechselt, laesst das zweite Datum stehen - es darf nicht mitgesendet werden.
        val form = DateForm(DateQualifier.ABOUT, DatePart(year = "1850"), DatePart(year = "1860"))
        assertEquals("ABT 1850", form.toGedcom())
    }
}
