package de.bgghome.webtrees.nativ

import de.bgghome.webtrees.nativ.data.GedcomDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * GedcomDate.fromInput: aus dem, was Menschen tippen, wird ein GEDCOM-Datum. Reine JVM-Tests.
 * Was die Funktion nicht versteht, gibt sie in Grossbuchstaben unveraendert weiter - die Pruefung macht der Server.
 */
class GedcomDateTest {

    // ── Genaue Daten ─────────────────────────────────────────────────

    @Test
    fun germanInput() {
        assertEquals("12 MAR 1890", GedcomDate.fromInput("12.3.1890"))
        assertEquals("12 MAR 1890", GedcomDate.fromInput("12.03.1890"))
        assertEquals("1 JAN 2000", GedcomDate.fromInput("1.1.2000"))
        assertEquals("MAR 1890", GedcomDate.fromInput("3.1890"))
        assertEquals("1890", GedcomDate.fromInput(" 1890 "))
        assertEquals("12 MAR 1890", GedcomDate.fromInput("12. März 1890"))
        assertEquals("12 MAR 1890", GedcomDate.fromInput("12 Maerz 1890"))
        assertEquals("DEC 1901", GedcomDate.fromInput("Dezember 1901"))
        assertEquals("3 MAR 1890", GedcomDate.fromInput("3. Mrz 1890"))
        assertEquals("15 OCT 1917", GedcomDate.fromInput("15. Okt. 1917"))
    }

    @Test
    fun englishInput() {
        assertEquals("12 MAR 1890", GedcomDate.fromInput("12 March 1890"))
        assertEquals("12 MAR 1890", GedcomDate.fromInput("March 12, 1890"))
        assertEquals("12 MAR 1890", GedcomDate.fromInput("March 12 1890"))
        assertEquals("MAY 1901", GedcomDate.fromInput("May 1901"))
        assertEquals("4 JUL 1776", GedcomDate.fromInput("4 Jul 1776"))
        assertEquals("1 SEP 1939", GedcomDate.fromInput("Sept 1, 1939"))
    }

    @Test
    fun separatorsAndSpacing() {
        assertEquals("12 MAR 1890", GedcomDate.fromInput("12-3-1890"))
        assertEquals("MAR 1890", GedcomDate.fromInput("3-1890"))
        assertEquals("12 MAR 1890", GedcomDate.fromInput("  12.3.1890  "))
        assertEquals("12 MAR 1890", GedcomDate.fromInput("12.   März   1890"))
        assertEquals("950", GedcomDate.fromInput("950"))
    }

    // ── Naeherungen und Zeitraeume ───────────────────────────────────

    @Test
    fun about() {
        assertEquals("ABT 1850", GedcomDate.fromInput("um 1850"))
        assertEquals("ABT 1850", GedcomDate.fromInput("Um 1850"))
        assertEquals("ABT 1850", GedcomDate.fromInput("ca. 1850"))
        assertEquals("ABT 1850", GedcomDate.fromInput("ca 1850"))
        assertEquals("ABT 1850", GedcomDate.fromInput("etwa 1850"))
        assertEquals("ABT 1850", GedcomDate.fromInput("circa 1850"))
        assertEquals("ABT 1850", GedcomDate.fromInput("about 1850"))
        assertEquals("ABT 1850", GedcomDate.fromInput("around 1850"))
        assertEquals("ABT 1850", GedcomDate.fromInput("abt. 1850"))
        assertEquals("ABT 12 MAR 1890", GedcomDate.fromInput("um 12.3.1890"))
        assertEquals("ABT MAY 1901", GedcomDate.fromInput("about May 1901"))
    }

    @Test
    fun beforeAndAfter() {
        assertEquals("BEF 1900", GedcomDate.fromInput("vor 1900"))
        assertEquals("BEF 12 MAR 1890", GedcomDate.fromInput("before 12 March 1890"))
        assertEquals("AFT 1 MAY 1900", GedcomDate.fromInput("nach 1.5.1900"))
        assertEquals("AFT 1900", GedcomDate.fromInput("after 1900"))
    }

    @Test
    fun fromAndTo() {
        assertEquals("FROM 1950", GedcomDate.fromInput("ab 1950"))
        assertEquals("FROM 1950", GedcomDate.fromInput("seit 1950"))
        assertEquals("FROM 1950", GedcomDate.fromInput("since 1950"))
        assertEquals("TO 1960", GedcomDate.fromInput("bis 1960"))
        assertEquals("TO 1960", GedcomDate.fromInput("until 1960"))
    }

    @Test
    fun between() {
        assertEquals("BET 1900 AND 1910", GedcomDate.fromInput("1900-1910"))
        assertEquals("BET 1900 AND 1910", GedcomDate.fromInput("1900 – 1910"))
        assertEquals("BET 1900 AND 1910", GedcomDate.fromInput("zwischen 1900 und 1910"))
        assertEquals("BET 1900 AND 1910", GedcomDate.fromInput("zw. 1900 u. 1910"))
        assertEquals("BET 1900 AND 1910", GedcomDate.fromInput("between 1900 and 1910"))
        assertEquals("BET 1 JAN 1900 AND 1910", GedcomDate.fromInput("zwischen 1.1.1900 und 1910"))
        assertEquals("BET MAR 1900 AND 12 MAY 1901", GedcomDate.fromInput("between March 1900 and 12 May 1901"))
    }

    // ── Durchreichen ─────────────────────────────────────────────────

    @Test
    fun gedcomStaysGedcom() {
        assertEquals("ABT 1850", GedcomDate.fromInput("abt 1850"))
        assertEquals("12 MAR 1890", GedcomDate.fromInput("12 mar 1890"))
        assertEquals("BET 1900 AND 1910", GedcomDate.fromInput("BET 1900 AND 1910"))
        assertEquals("FROM 1950 TO 1960", GedcomDate.fromInput("from 1950 to 1960"))
        assertEquals("EST 1850", GedcomDate.fromInput("est 1850"))
        assertEquals("@#DJULIAN@ 12 MAR 1690", GedcomDate.fromInput("@#DJULIAN@ 12 MAR 1690"))
    }

    @Test
    fun unknownInputPassesThroughUppercased() {
        // Der Server prueft und antwortet mit "invalid-date"; die App raet nicht.
        assertEquals("", GedcomDate.fromInput("  "))
        assertEquals("ABC", GedcomDate.fromInput("abc"))
        assertEquals("32.13.1890", GedcomDate.fromInput("32.13.1890"))
        assertEquals("12.3.", GedcomDate.fromInput("12.3."))
        assertEquals("12.3.90", GedcomDate.fromInput("12.3.90"))
        assertEquals("FOOBAR 1890", GedcomDate.fromInput("Foobar 1890"))
        // Schraegstriche bleiben unangetastet: 3/12/1890 ist in den USA der 12. Maerz, anderswo der 3. Dezember.
        assertEquals("3/12/1890", GedcomDate.fromInput("3/12/1890"))
    }

    @Test
    fun onlyTheMonthIsChecked() {
        // Tag und Jahr werden nicht geprueft - der Server kennt die Kalenderregeln (Schaltjahre, Julianisch ...).
        assertEquals("31 FEB 1890", GedcomDate.fromInput("31.2.1890"))
        assertEquals("0 MAR 1890", GedcomDate.fromInput("0.3.1890"))
    }
}
