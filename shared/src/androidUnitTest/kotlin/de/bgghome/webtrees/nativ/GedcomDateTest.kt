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
    fun frenchDutchSpanishInput() {
        assertEquals("12 MAR 1890", GedcomDate.fromInput("12 mars 1890"))
        assertEquals("1 AUG 1890", GedcomDate.fromInput("1er août 1890".replace("1er", "1")))
        assertEquals("FEB 1901", GedcomDate.fromInput("Février 1901"))
        assertEquals("ABT 1850", GedcomDate.fromInput("vers 1850"))
        assertEquals("BEF 12 DEC 1850", GedcomDate.fromInput("avant 12 décembre 1850"))
        assertEquals("BET 1850 AND 1860", GedcomDate.fromInput("entre 1850 et 1860"))
        assertEquals("3 MAR 1890", GedcomDate.fromInput("3 maart 1890"))
        assertEquals("5 MAY 1890", GedcomDate.fromInput("5 mei 1890"))
        assertEquals("ABT 1850", GedcomDate.fromInput("rond 1850"))
        assertEquals("AFT 1850", GedcomDate.fromInput("na 1850"))
        assertEquals("BET MAR 1850 AND 1851", GedcomDate.fromInput("tussen maart 1850 en 1851"))
        assertEquals("12 JAN 1890", GedcomDate.fromInput("12 enero 1890"))
        assertEquals("AUG 1901", GedcomDate.fromInput("agosto 1901"))
        assertEquals("ABT 1850", GedcomDate.fromInput("hacia 1850"))
        assertEquals("BEF 1850", GedcomDate.fromInput("antes de 1850"))
        assertEquals("AFT 1850", GedcomDate.fromInput("después de 1850"))
        assertEquals("BET 1850 AND 1860", GedcomDate.fromInput("entre 1850 y 1860"))
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

    @Test
    fun geschaetztBerechnetUndZeitraumWieWebtreesSieAnzeigt() {
        // de, en, fr, nl, es - so zeigt webtrees EST, CAL und FROM ... TO an; wer es so stehen laesst, verliert nichts
        assertEquals("EST 1850", GedcomDate.fromInput("geschätzt 1850"))
        assertEquals("EST 12 MAR 1850", GedcomDate.fromInput("geschätzt 12. März 1850"))
        assertEquals("CAL 1850", GedcomDate.fromInput("berechnet 1850"))
        assertEquals("CAL 1850", GedcomDate.fromInput("errechnet 1850"))
        assertEquals("EST 1850", GedcomDate.fromInput("estimated 1850"))
        assertEquals("CAL 1850", GedcomDate.fromInput("calculated 1850"))
        assertEquals("EST 1850", GedcomDate.fromInput("estimé 1850"))
        assertEquals("CAL 1850", GedcomDate.fromInput("calculé 1850"))
        assertEquals("EST 1850", GedcomDate.fromInput("1850 (geschat)"))
        assertEquals("CAL 1850", GedcomDate.fromInput("1850 (berekend)"))
        assertEquals("EST 1850", GedcomDate.fromInput("1850 estimadas"))
        assertEquals("CAL 1850", GedcomDate.fromInput("1850 calculadas"))
        assertEquals("ABT 1850", GedcomDate.fromInput("sobre 1850"))
        assertEquals("FROM 1915 TO 1918", GedcomDate.fromInput("von 1915 bis 1918"))
        assertEquals("FROM 1915 TO 1918", GedcomDate.fromInput("from 1915 to 1918"))
        assertEquals("FROM 1915 TO 1918", GedcomDate.fromInput("de 1915 à 1918"))
        assertEquals("FROM 1915 TO 1918", GedcomDate.fromInput("vanaf 1915 tot 1918"))
        assertEquals("FROM 1915 TO 1918", GedcomDate.fromInput("desde 1915 hasta 1918"))
        assertEquals("FROM 1915", GedcomDate.fromInput("von 1915"))
        assertEquals("TO 1918", GedcomDate.fromInput("tot 1918"))
        assertEquals("BEF 1900", GedcomDate.fromInput("vor 1900"))   // "vor" ist nicht "von"
    }

    @Test
    fun gedcomMitFreitextBehaeltSeineSchreibung() {
        assertEquals("INT 1865 (Ostern)", GedcomDate.fromInput("INT 1865 (Ostern)"))
        assertEquals("INT 1865 (Ostern)", GedcomDate.fromInput("int 1865 (Ostern)"))
        assertEquals("(um die Jahrhundertwende)", GedcomDate.fromInput("(um die Jahrhundertwende)"))
        assertEquals("BET 1915 AND 1918", GedcomDate.fromInput("bet 1915 and 1918"))
        assertEquals("@#DJULIAN@ 1 JAN 1690", GedcomDate.fromInput("@#DJULIAN@ 1 JAN 1690"))
        assertEquals(true, GedcomDate.istGedcom("ABT @#DJULIAN@ 1690"))
        assertEquals(true, GedcomDate.istGedcom("EST 1750/51"))
        assertEquals(false, GedcomDate.istGedcom("FOOBAR 1890"))
        assertEquals(false, GedcomDate.istGedcom("12 3 1890"))
    }
}
