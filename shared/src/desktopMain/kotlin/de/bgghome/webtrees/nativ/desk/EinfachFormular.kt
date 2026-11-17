package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.api.DateJson
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.FactRequest
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.data.GedcomDate
import de.bgghome.webtrees.nativ.api.API_NAME_PARTS_WRITE
import de.bgghome.webtrees.nativ.api.hat
import de.bgghome.webtrees.nativ.data.GedcomName
import de.bgghome.webtrees.nativ.data.Namensteile
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.Field
import de.bgghome.webtrees.nativ.ui.PlaceField
import de.bgghome.webtrees.nativ.ui.WtAlertDialog
import de.bgghome.webtrees.nativ.ui.placeSuggestions
import de.bgghome.webtrees.nativ.ui.saveFacts
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * Einfacher Eingabemodus des Personenblatts: ein festes Formular mit den Angaben, die fast jeder erfasst - Name, Geburt,
 * Taufe, Religion, Beruf, Heirat je Partnerschaft, Tod, Bestattung. Oberster Grundsatz: nichts geht verloren.
 * - Gesendet wird nur, was jemand in einem Feld geaendert hat; unberuehrte Felder gehen nie an den Server, auch wenn
 *   ihr Datum anders geschrieben ist, als die Eingabe es erzeugen wuerde.
 * - Der Server aendert gezielt nur Datum, Ort bzw. Wert; Quellen, Notizen und weitere Unterangaben bleiben.
 * - Gibt es ein Ereignis mehrfach, bearbeitet das Formular das erste; die weiteren bleiben und sind vermerkt.
 * - Gespeichert wird sofort beim Verlassen eines Feldes (Tab, Klick woanders), wie ueberall sonst im Programm - kein
 *   Speichern-Knopf. Ein ungueltiges Datum bleibt rot stehen und geht nicht an den Server, bis es korrigiert oder
 *   ausdruecklich als Text uebernommen ist; nur dafuer fragt das Programm beim Schliessen und Beenden nach.
 */

/** Wie ein Feld aussieht: Name (Vornamen, Familienname), Ereignis (Datum, Ort) oder Angabe (ein Text). */
enum class FeldArt { Name, Ereignis, Angabe }

class EinfachFeld(val key: String, val tag: String, val record: String?, val art: FeldArt, val partner: String?) {
    var fact by mutableStateOf<FactJson?>(null)
    var weitere by mutableStateOf(0)
    var datum by mutableStateOf(""); private var datumAlt = ""
    var ort by mutableStateOf(""); private var ortAlt = ""
    var wert by mutableStateOf(""); private var wertAlt = ""
    var vornamen by mutableStateOf(""); private var vornamenAlt = ""
    var familienname by mutableStateOf(""); private var familiennameAlt = ""
    private var zusatz = ""
    /** Ein Datum, das das Programm nicht deuten kann, soll zur Korrektur auffordern; nur auf Wunsch geht es als Text durch. */
    var datumAlsText by mutableStateOf(false)
    val datumOk: Boolean get() = datumErkannt(datum) || datumAlsText

    val geaendert: Boolean
        get() = datum != datumAlt || ort != ortAlt || wert != wertAlt || vornamen != vornamenAlt || familienname != familiennameAlt

    /** Stand vom Server uebernehmen; eigene, ungespeicherte Eingaben bleiben stehen. */
    fun uebernehmen(f: FactJson?, anzahl: Int) {
        val warGeaendert = geaendert
        fact = f; weitere = (anzahl - 1).coerceAtLeast(0)
        val n = GedcomName.aus(f?.value.orEmpty())
        datumAlt = datumVorbelegen(f?.date); ortAlt = f?.place?.name.orEmpty()
        wertAlt = if (art == FeldArt.Angabe) f?.value.orEmpty() else ""
        vornamenAlt = n.vornamen; familiennameAlt = n.familienname; zusatz = n.zusatz
        if (!warGeaendert) verwerfen()
    }

    fun verwerfen() { datum = datumAlt; ort = ortAlt; wert = wertAlt; vornamen = vornamenAlt; familienname = familiennameAlt; datumAlsText = false }

    /** Nach erfolgreichem Speichern: das Eingetippte ist jetzt der Stand. */
    fun gespeichert() { datumAlt = datum; ortAlt = ort; wertAlt = wert; vornamenAlt = vornamen; familiennameAlt = familienname }

    /**
     * Die Anfrage an den Server - nur mit den geaenderten Teilen; null, wenn nichts zu senden ist. [namensteile]: der Server
     * schreibt die Namensteile (ab Stufe 46) - dann gehen GIVN/SURN/NSFX mit, soweit sie den Feldern folgen, und
     * abweichende gespeicherte Teile bleiben erhalten.
     */
    fun anfrage(namensteile: Boolean = false): FactRequest? {
        if (!geaendert) return null
        val f = fact
        return when (art) {
            FeldArt.Name -> {
                val alt = GedcomName(vornamenAlt, familiennameAlt, zusatz)
                val neuName = GedcomName(vornamen, familienname, zusatz)
                val neu = neuName.gedcom()
                if (f == null) FactRequest(tag = "NAME", value = neu)
                else FactRequest(
                    factId = f.id, value = neu,
                    nameParts = if (namensteile) Namensteile.zumSenden(Namensteile.folgen(Namensteile.felder(f.nameParts), alt, neuName), f.nameParts, true) else null,
                )
            }
            FeldArt.Angabe -> if (f == null) wert.trim().takeIf { it.isNotEmpty() }?.let { FactRequest(tag = tag, value = it) }
                else FactRequest(factId = f.id, value = wert.trim())
            FeldArt.Ereignis -> {
                val d = if (datum != datumAlt) datumGedcom(datum) else null
                val o = if (ort != ortAlt) ort.trim() else null
                if (f == null) {
                    if (d.isNullOrEmpty() && o.isNullOrEmpty()) null
                    else FactRequest(tag = tag, date = d?.ifEmpty { null }, place = o?.ifEmpty { null })
                } else FactRequest(factId = f.id, date = d, place = o)
            }
        }
    }
}

/** Eingabe als GEDCOM-Datum; was sich nicht deuten laesst, wird als Datumstext "(…)" gespeichert statt verfaelscht. */
fun datumGedcom(eingabe: String): String {
    if (eingabe.isBlank()) return ""
    val g = GedcomDate.fromInput(eingabe)
    return if (GedcomDate.istGedcom(g)) g else "(" + eingabe.trim().replace("(", "").replace(")", "") + ")"
}

fun datumErkannt(eingabe: String): Boolean = eingabe.isBlank() || GedcomDate.istGedcom(GedcomDate.fromInput(eingabe))

/**
 * Was im Datumsfeld vorbelegt wird: die Anzeige des Servers ("um 1850"), wenn sie beim Speichern wieder genau dasselbe
 * GEDCOM-Datum ergibt; sonst das GEDCOM-Datum selbst ("INT 1865 (Ostern)", "@#DJULIAN@ 1 JAN 1690"), damit das
 * Aendern einer Kleinigkeit nichts anderes am Datum verliert.
 */
fun datumVorbelegen(d: DateJson?): String {
    val text = d?.text.orEmpty()
    val gedcom = d?.gedcom.orEmpty().trim()
    return if (gedcom.isNotEmpty() && datumGedcom(text) != gedcom) gedcom else text
}

/** Das Formular einer Person. */
class EinfachFormular(val xref: String) {
    val felder = mutableStateListOf<EinfachFeld>()
    var speichert by mutableStateOf(false)
    var fehler by mutableStateOf(false)
    val geaendert: Boolean get() = felder.any { it.geaendert }
    /** Erst speichern, wenn kein Datum mehr zur Korrektur ansteht. */
    val speicherbar: Boolean get() = felder.all { it.datumOk }
    /** Name der Person fuer die Rueckfrage beim Schliessen. */
    var name: String = ""

    /** Felder aus dem Stand vom Server bilden bzw. auffrischen. */
    fun abgleichen(detail: IndividualDetail) {
        name = detail.person.name
        val facts = detail.facts
        fun erstes(vararg tags: String): Pair<String, List<FactJson>> {
            val tag = tags.firstOrNull { t -> facts.any { it.tag == t } } ?: tags.first()
            return tag to facts.filter { it.tag == tag }
        }
        val soll = buildList {
            add(Triple("NAME", null as String?, erstes("NAME")) to FeldArt.Name)
            add(Triple("BIRT", null, erstes("BIRT")) to FeldArt.Ereignis)
            add(Triple("CHR", null, erstes("CHR", "BAPM")) to FeldArt.Ereignis)
            add(Triple("RELI", null, erstes("RELI")) to FeldArt.Angabe)
            add(Triple("OCCU", null, erstes("OCCU")) to FeldArt.Angabe)
            detail.spouseFamilies.forEach { fam ->
                add(Triple("MARR:" + fam.xref, fam.xref, "MARR" to fam.facts.filter { it.tag == "MARR" }) to FeldArt.Ereignis)
            }
            add(Triple("DEAT", null, erstes("DEAT")) to FeldArt.Ereignis)
            add(Triple("BURI", null, erstes("BURI", "CREM")) to FeldArt.Ereignis)
        }
        val alt = felder.associateBy { it.key }
        val neu = soll.map { (t, art) ->
            val (key, record, gefunden) = t
            val (tag, liste) = gefunden
            val partner = record?.let { r -> detail.spouseFamilies.firstOrNull { it.xref == r }?.spouse?.name }
            (alt[key]?.takeIf { it.tag == tag } ?: EinfachFeld(key, tag, record, art, partner)).also { it.uebernehmen(liste.firstOrNull(), liste.size) }
        }
        // Eine Partnerschaft, die es nicht mehr gibt, aber noch Eingaben hat, bleibt stehen
        val verwaist = felder.filter { f -> f.geaendert && neu.none { it.key == f.key } }
        felder.clear(); felder.addAll(neu + verwaist)
    }

    /** Alle geaenderten Felder mit gueltigem Datum speichern; ungueltige bleiben als Entwurf stehen. */
    fun speichern(viewModel: AppViewModel, onFertig: (fehler: Boolean) -> Unit = {}) {
        val namensteile = viewModel.state.value.info.hat(API_NAME_PARTS_WRITE)
        val auftraege = felder.filter { it.datumOk }.mapNotNull { f -> f.anfrage(namensteile)?.let { f to it } }
        if (auftraege.isEmpty()) { onFertig(false); return }
        speichert = true; fehler = false
        viewModel.saveFacts(xref, auftraege.map { (f, r) -> f.record to r },
            onGespeichert = { i -> auftraege[i].first.gespeichert() },
            onFertig = { f -> speichert = false; fehler = f; onFertig(f) })
    }

    fun verwerfen() = felder.forEach { it.verwerfen() }

    /** Ein Feld beim Verlassen speichern, wenn es geaendert und gueltig ist. */
    fun feldSpeichern(f: EinfachFeld, viewModel: AppViewModel) {
        if (!f.geaendert || !f.datumOk || speichert) return
        val anfrage = f.anfrage(viewModel.state.value.info.hat(API_NAME_PARTS_WRITE)) ?: run { f.gespeichert(); return }
        speichert = true; fehler = false
        viewModel.saveFacts(xref, listOf(f.record to anfrage), onGespeichert = { f.gespeichert() }, onFertig = { e -> speichert = false; fehler = e })
    }
}

/** Alle Entwuerfe dieses Programmlaufs, je Person. */
object Entwuerfe {
    private val formulare = mutableStateMapOf<String, EinfachFormular>()
    /** Beenden wurde verlangt, obwohl noch etwas ungespeichert ist - das Hauptfenster fragt nach. */
    var beendenAnfrage by mutableStateOf(false)

    fun fuer(xref: String): EinfachFormular = formulare.getOrPut(xref) { EinfachFormular(xref) }
    fun offene(): List<EinfachFormular> = formulare.values.filter { it.geaendert }
    fun offen(): Boolean = offene().isNotEmpty()
    /** Beim Schliessen/Beenden: alles Gueltige still speichern; true, wenn danach noch ungueltige Eingaben offen sind. */
    fun abschliessen(viewModel: AppViewModel): Boolean {
        offene().forEach { it.speichern(viewModel) }
        return formulare.values.any { f -> f.felder.any { it.geaendert && !it.datumOk } }
    }

    /** Alle offenen Entwuerfe nacheinander speichern; [onFertig] mit true, wenn etwas schiefging. */
    fun alleSpeichern(viewModel: AppViewModel, onFertig: (fehler: Boolean) -> Unit) {
        val liste = offene()
        fun weiter(i: Int) {
            if (i >= liste.size) { onFertig(false); return }
            liste[i].speichern(viewModel) { f -> if (f) onFertig(true) else weiter(i + 1) }
        }
        weiter(0)
    }

    fun alleVerwerfen() = formulare.values.forEach { it.verwerfen() }
}

/** Rueckfrage bei ungespeicherten Eingaben: speichern, verwerfen oder abbrechen. [onWeiter] erst, wenn nichts mehr offen ist. */
@Composable
fun UngespeichertDialog(viewModel: AppViewModel, onWeiter: () -> Unit, onAbbrechen: () -> Unit) {
    var speichert by remember { mutableStateOf(false) }
    val offene = Entwuerfe.offene()
    WtAlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(stringResource(Res.string.desk_unsaved_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.desk_unsaved_intro))
                // Wo genau: je Person die Felder mit ungueltigem Datum (alles Gueltige ist schon gespeichert)
                offene.forEach { f ->
                    Text("• " + f.name.ifBlank { f.xref } + ": " + f.felder.filter { it.geaendert && !it.datumOk }.map { stringResource(titel(it.tag)) + (it.partner?.let { p -> " ($p)" } ?: "") }.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium)
                }
                Text(stringResource(Res.string.desk_unsaved_date_block), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { Entwuerfe.alleVerwerfen(); onWeiter() }, enabled = !speichert) { Text(stringResource(Res.string.desk_unsaved_discard)) }
        },
        dismissButton = { TextButton(onClick = onAbbrechen, enabled = !speichert) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

private fun titel(tag: String): StringResource = when (tag) {
    "NAME" -> Res.string.desk_ev_name
    "BIRT" -> Res.string.desk_ev_birth
    "CHR", "BAPM" -> Res.string.desk_ev_baptism
    "RELI" -> Res.string.desk_ev_religion
    "OCCU" -> Res.string.desk_ev_occupation
    "MARR" -> Res.string.desk_ev_marriage
    "DEAT" -> Res.string.desk_ev_death
    "CREM" -> Res.string.desk_ev_cremation
    else -> Res.string.desk_ev_burial
}

@Composable
fun EinfachDaten(formular: EinfachFormular, canEdit: Boolean, viewModel: AppViewModel) {
    val scroll = rememberScrollState()
    val vorschlaege = viewModel.placeSuggestions()
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                formular.felder.forEach { f ->
                    val name = stringResource(titel(f.tag)) + (f.partner?.let { " – $it" } ?: "")
                    Row(Modifier.onFocusChanged { st -> if (!st.hasFocus) formular.feldSpeichern(f, viewModel) }, verticalAlignment = Alignment.Top) {
                        Column(Modifier.width(150.dp).padding(top = 16.dp)) {
                            Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            if (f.weitere > 0) Tipp(stringResource(Res.string.tipp_simple_more)) {
                                Text(stringResource(Res.string.desk_simple_more, f.weitere), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (f.geaendert) Text("●", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (!canEdit) {
                            Text(listOf(f.vornamen, f.familienname, f.datum, f.ort, f.wert).filter(String::isNotBlank).joinToString("  ·  ").ifEmpty { "–" },
                                Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium)
                        } else when (f.art) {
                            FeldArt.Name -> Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) { Field(f.familienname, { f.familienname = it }, Res.string.field_family_name) }
                                Box(Modifier.weight(1f)) { Field(f.vornamen, { f.vornamen = it }, Res.string.field_given) }
                            }
                            FeldArt.Angabe -> Box(Modifier.weight(1f)) { Field(f.wert, { f.wert = it }, titel(f.tag)) }
                            FeldArt.Ereignis -> Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.width(230.dp)) {
                                    Field(f.datum, { f.datum = it; f.datumAlsText = false }, Res.string.fact_date)
                                    if (!datumErkannt(f.datum)) {
                                        if (f.datumAlsText) Text(stringResource(Res.string.desk_simple_date_text), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                        else {
                                            Text(stringResource(Res.string.desk_date_invalid), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                            TextButton(onClick = { f.datumAlsText = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                                                Text(stringResource(Res.string.desk_date_as_text), style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                                Box(Modifier.weight(1f)) { PlaceField(f.ort, { f.ort = it }, Res.string.fact_place, vorschlaege) }
                            }
                        }
                    }
                }
            }
            SenkrechteLeiste(scroll)
        }
        if (canEdit) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // Kein Speichern-Knopf: gespeichert wird beim Verlassen des Feldes. Hier nur der Stand.
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.desk_simple_autosave), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    formular.speichert -> Text(stringResource(Res.string.desk_simple_saving), style = MaterialTheme.typography.bodySmall)
                    !formular.speicherbar -> Text(stringResource(Res.string.desk_date_check), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    formular.fehler -> Text(stringResource(Res.string.desk_simple_error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    formular.geaendert -> Text(stringResource(Res.string.desk_simple_unsaved), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    else -> Text(stringResource(Res.string.msg_saved), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
