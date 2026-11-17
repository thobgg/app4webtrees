package de.bgghome.webtrees.nativ.data

import de.bgghome.webtrees.nativ.api.FactRequest
import de.bgghome.webtrees.nativ.api.NameParts
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NamensteileTest {
    private val json = Json { explicitNulls = false }
    private val hans = NameParts(given = "Hans", surname = "Beispiel")

    @Test fun passendeTeileFolgenDenFeldern() {
        val neu = Namensteile.folgen(Namensteile.felder(hans), GedcomName.aus("Hans /Beispiel/"), GedcomName.aus("Hanns /Beispiel/"))
        assertEquals("Hanns", neu.given)
        assertEquals(NameParts(given = "Hanns"), Namensteile.geaendert(neu, hans))
    }

    @Test fun abweichendeVornamenBleiben() {
        val gespeichert = NameParts(given = "Hans Peter Josef", surname = "Beispiel")
        val neu = Namensteile.folgen(Namensteile.felder(gespeichert), GedcomName.aus("Hans /Beispiel/"), GedcomName.aus("Hans /Muster/"))
        assertEquals("Hans Peter Josef", neu.given)
        assertEquals("Muster", neu.surname)
        assertEquals(NameParts(surname = "Muster"), Namensteile.geaendert(neu, gespeichert))
        assertEquals(listOf(Namensteile.Teil.Vornamen to "Hans Peter Josef"), Namensteile.abweichungen(GedcomName.aus("Hans /Beispiel/"), gespeichert))
    }

    @Test fun nurAnzeigeGeaendertSendetLeeresObjekt() {
        val gespeichert = NameParts(given = "Hans Peter Josef", surname = "Beispiel")
        val teile = Namensteile.folgen(Namensteile.felder(gespeichert), GedcomName.aus("Hans /Beispiel/"), GedcomName.aus("Hanns /Beispiel/"))
        assertEquals(NameParts(), Namensteile.zumSenden(teile, gespeichert, nameGeaendert = true))
        assertNull(Namensteile.zumSenden(Namensteile.felder(gespeichert), gespeichert, nameGeaendert = false))
        // Als JSON: "nameParts":{} - der Server leitet dann nichts aus value ab
        val text = json.encodeToString(FactRequest.serializer(), FactRequest(factId = "f1", value = "Hanns /Beispiel/", nameParts = NameParts()))
        assertTrue("\"nameParts\":{}" in text, text)
    }

    @Test fun geleerterTeilGehtAlsLeererText() {
        val gespeichert = NameParts(given = "Hans", nickname = "Hansi", surname = "Beispiel")
        val geaendert = Namensteile.geaendert(Namensteile.felder(gespeichert).copy(nickname = ""), gespeichert)
        assertEquals(NameParts(nickname = ""), geaendert)
        val text = json.encodeToString(NameParts.serializer(), geaendert!!)
        assertEquals("{\"nickname\":\"\"}", text)
    }

    @Test fun vorsatzwortUndPraefixBleibenAusserhalbDerTeile() {
        val gespeichert = NameParts(prefix = "Dr.", given = "Johannes", nickname = "Jan", surnamePrefix = "von", surname = "Berg")
        val alt = GedcomName.aus("Dr. Johannes \"Jan\" /von Berg/")
        assertTrue(Namensteile.abweichungen(alt, gespeichert).isEmpty())
        val neu = Namensteile.folgen(Namensteile.felder(gespeichert), alt, GedcomName.aus("Dr. Johann \"Jan\" /von Bergen/"))
        assertEquals("Johann", neu.given)
        assertEquals("Bergen", neu.surname)
    }

    @Test fun rufnamenSternZaehltNicht() {
        val gespeichert = NameParts(given = "Johann Heinrich", callName = "Heinrich")
        assertTrue(Namensteile.vornamenPassen(GedcomName.aus("Johann Heinrich* /Mohwinkel/"), gespeichert))
    }

    @Test fun fehlendeTeileFolgenImmer() {
        val neu = Namensteile.folgen(Namensteile.felder(null), GedcomName.aus("Hans /Beispiel/"), GedcomName.aus("Hans /Beispiel/ jun."))
        assertEquals("jun.", neu.suffix)
        assertEquals(NameParts(suffix = "jun."), Namensteile.geaendert(neu, null))
    }
}
