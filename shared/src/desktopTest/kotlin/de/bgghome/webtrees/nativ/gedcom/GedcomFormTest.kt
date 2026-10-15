package de.bgghome.webtrees.nativ.gedcom

import de.bgghome.webtrees.nativ.pruefung.PruefErgebnis
import de.bgghome.webtrees.nativ.pruefung.PruefOptionen
import de.bgghome.webtrees.nativ.pruefung.pruefen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Regeln 620-625: Kopf, FILE, GEDCOM-L-Addendum, Datum im Kopf, GEDCOM 7, Text hinter Ereignissen. */
class GedcomFormTest {

    private fun pruefe(text: String, dateiname: String? = "test.ged", pruefenAls: String? = null): PruefErgebnis {
        val (baum, befund) = GedcomLeser.auswerten(GedcomLeser.zerlegen(text.trimIndent()), jetzt = 2026, dateiname = dateiname, pruefenAls = pruefenAls)
        return pruefen(baum, PruefOptionen(aus = emptySet(), jetzt = 2026, datei = befund))
    }

    private fun texte(e: PruefErgebnis, regel: String) = e.treffer[regel].orEmpty().map { it.text }

    private val person = """
        0 @I1@ INDI
        1 NAME Anna /Muster/
        1 SEX F
        1 BIRT
        2 DATE 1 JAN 1900
    """.trimIndent()

    @Test
    fun sauberer551KopfOhneTreffer() {
        val e = pruefe("""
            0 HEAD
            1 SOUR Python-Skripte
            2 VERS 1.7
            1 DATE 15 OCT 2026
            1 SUBM @U1@
            1 FILE test.ged
            1 GEDC
            2 VERS 5.5.1
            3 _ADDENDUM GEDCOM-L Addendum
            4 VERS R2 (2020-12-01)
            4 WWW https://genealogy.net/GEDCOM
            2 FORM LINEAGE-LINKED
            1 CHAR UTF-8
            0 @U1@ SUBM
            1 NAME Thomas Bugge
        """ + "\n" + person + "\n0 TRLR")
        listOf("620", "621", "622", "623", "624", "625").forEach { assertEquals(emptyList(), texte(e, it), it) }
    }

    @Test
    fun maengel551() {
        val e = pruefe("""
            0 HEAD
            1 DATE 15.10.2026
            1 SUBM @U9@
            1 FILE alt/anders.ged
            1 GEDC
            2 VERS 5.5.1
            3 _ADDENDUM GEDCOM-L
            4 VERS 2.0
            4 WWW https://genealogy.net/GEDCOM/
            0 @I1@ INDI
            1 NAME Ernst /Muster/
            1 EMIG Mit der „Fulda“ des Norddeutschen Lloyd
            0 TRLR
        """)
        assertEquals(listOf("Programm (SOUR) fehlt", "GEDC FORM fehlt", "Zeichensatz (CHAR) fehlt",
            "Einsender (SUBM) zeigt auf keinen Datensatz: @U9@"), texte(e, "620"))
        assertEquals(listOf("FILE alt/anders.ged, Datei test.ged"), texte(e, "621"))
        // Schraegstrich am Ende der Adresse ist kein Fehler
        assertEquals(listOf("Name nicht wie empfohlen (GEDCOM-L Addendum): GEDCOM-L",
            "Version nicht wie empfohlen, z. B. R2 (2020-12-01): 2.0"), texte(e, "622"))
        assertEquals(1, texte(e, "623").size)
        assertEquals(emptyList(), texte(e, "624"))   // als 5.5.1 geprueft
        assertEquals(listOf("Text hinter dem Ereignis (erlaubt ist nur Y): EMIG „Mit der „Fulda“ des Norddeutschen Lloyd“"), texte(e, "625"))
        assertEquals("I1", e.treffer.getValue("625").single().person)
    }

    @Test
    fun verstoesseGegenSieben() {
        val e = pruefe("""
            0 HEAD
            1 GEDC
            2 VERS 7.0
            3 _ADDENDUM GEDCOM-L Addendum
            2 FORM LINEAGE-LINKED
            1 CHAR UTF-8
            1 LANG German
            0 @I1@ INDI
            1 NAME Anna /Muster/
            1 SEX f
            1 NOTE Erste Zeile
            2 CONC und weiter
            1 CHR
            2 DATE 2 JAN 1900
            2 ASSO @I2@
            3 RELA godparent
            2 ASSO @I2@
            3 ROLE godparent
            2 PLAC Celle
            3 EXID CELLE1JO52
            0 @I2@ INDI
            1 NAME Paul /Pate/
            0 @M1@ OBJE
            1 FILE bild.jpg
            2 FORM jpg
            3 TYPE photo
            0 TRLR
        """)
        val t = texte(e, "624")
        listOf("CONC gibt es in GEDCOM 7 nicht mehr", "Im Kopf von GEDCOM 7 nicht erlaubt: CHAR", "Im Kopf von GEDCOM 7 nicht erlaubt: GEDC FORM",
            "Gibt es nur in GEDCOM 5.5.1: RELA", "Wert nicht aus der Liste von GEDCOM 7: ROLE godparent", "Wert nicht aus der Liste von GEDCOM 7: SEX f",
            "Externe Kennung ohne TYPE: EXID CELLE1JO52", "Kein Medientyp (z. B. image/jpeg): FORM jpg", "Gibt es nur in GEDCOM 5.5.1: FORM TYPE",
        ).forEach { assertTrue(it in t, "$it fehlt in $t") }
        assertEquals(listOf("Das GEDCOM-L-Addendum gilt für GEDCOM 5.5.1, nicht für 7: GEDCOM-L Addendum"), texte(e, "622"))
        // in 7 sind SOUR, SUBM, FORM und CHAR keine Pflicht
        assertEquals(emptyList(), texte(e, "620"))
    }

    @Test
    fun sauberesSiebenMitVoidUndSnote() {
        val text = """
            0 HEAD
            1 SOUR Python-Skripte
            1 GEDC
            2 VERS 7.0
            1 LANG de
            0 @N1@ SNOTE Geteilte Notiz
            0 @I1@ INDI
            1 NAME Anna /Muster/
            1 SEX F
            1 FAMC @VOID@
            1 CHR
            2 DATE 2 JAN 1900
            2 SNOTE @N1@
            2 ASSO @VOID@
            3 PHRASE Martin Hoppe, Bauingenieur
            3 ROLE GODP
            0 @M1@ OBJE
            1 FILE bild.jpg
            2 FORM image/jpeg
            3 MEDI PHOTO
            0 TRLR
        """
        val e = pruefe(text)
        listOf("611", "620", "621", "622", "623", "624", "625").forEach { assertEquals(emptyList(), texte(e, it), it) }
        val (baum, _) = GedcomLeser.auswerten(GedcomLeser.zerlegen(text.trimIndent()), jetzt = 2026)
        val taufe = baum.individuals.getValue("I1").facts.first { it.tag == "CHR" }
        assertEquals(listOf("Martin Hoppe, Bauingenieur"), taufe.freeAssociates.map { it.name })
        assertEquals("godparent", taufe.freeAssociates.single().role)
        assertEquals(listOf("Geteilte Notiz"), taufe.notes)
    }

    @Test
    fun versionErzwingen() {
        val text = """
            0 HEAD
            1 SOUR X
            1 SUBM @U1@
            1 GEDC
            2 VERS 5.5.1
            2 FORM LINEAGE-LINKED
            1 CHAR UTF-8
            0 @U1@ SUBM
            1 NAME Jemand
        """ + "\n" + person + "\n0 TRLR"
        assertEquals(emptyList(), texte(pruefe(text), "624"))
        val als7 = texte(pruefe(text, pruefenAls = "7.0"), "624")
        assertTrue("Im Kopf von GEDCOM 7 nicht erlaubt: CHAR" in als7, als7.toString())
    }
}
