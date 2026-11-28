package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.api.DnaModulInfo
import de.bgghome.webtrees.nativ.api.DnaTestEingabe
import de.bgghome.webtrees.nativ.api.DnaTrefferEingabe
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.WtAlertDialog
import de.bgghome.webtrees.nativ.ui.meldung
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/*
 * DNA-Test oder DNA-Treffer fuer eine Person eintragen. Geschrieben wird ueber das Modul dna4webtrees, nicht ueber die
 * allgemeine Schreib-Route: das Modul setzt den Schutzvermerk nach seinen Regeln (Lebende nur Verwalter, Verstorbene
 * Mitglieder, Treffer je nach beiden Personen), so entsteht aus dem Programm kein ungeschuetzter DNA-Eintrag. Die
 * Auswahllisten (Testart, Anbieter, Schutz) kommen vom Modul. Aendern und Loeschen geht wie bei jedem Ereignis.
 */
enum class DnaFormular { Test, Treffer }

@Composable
fun DnaEintragenDialog(art: DnaFormular, client: WtClient, tree: String, xref: String, name: String, onClose: (gespeichert: Boolean) -> Unit) {
    val modul by produceState<DnaModulInfo?>(null) { value = withContext(Dispatchers.IO) { runCatching { client.dnaModul() }.getOrNull() } ?: DnaModulInfo() }
    var test by remember { mutableStateOf(DnaTestEingabe("")) }
    var treffer by remember { mutableStateOf(DnaTrefferEingabe()) }
    var andere by remember { mutableStateOf<Person?>(null) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var laeuft by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val m = modul
    // Vorgabe: die erste Testart des Moduls, sobald die Liste da ist
    if (m != null && test.art.isEmpty() && m.testTypes.isNotEmpty()) test = test.copy(art = m.testTypes.first())
    val bereit = m != null && !laeuft && if (art == DnaFormular.Test) test.art.isNotEmpty() else treffer.copy(andere = andere?.xref.orEmpty()).vollstaendig

    WtAlertDialog(
        onDismissRequest = { onClose(false) },
        title = { Text(stringResource(if (art == DnaFormular.Test) Res.string.dna_form_test_title else Res.string.dna_form_match_title, name)) },
        text = {
            Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val anbieter = listOf("") + m?.providers.orEmpty()
                val schutz = listOf("") + m?.restrictions.orEmpty()
                if (art == DnaFormular.Test) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Auswahlfeld(stringResource(Res.string.dna_form_type), test.art, m?.testTypes.orEmpty(), { it }) { test = test.copy(art = it) }
                        Auswahlfeld(stringResource(Res.string.dna_form_provider), test.anbieter, anbieter, { it.ifEmpty { "–" } }) { test = test.copy(anbieter = it) }
                        Auswahlfeld(stringResource(Res.string.dna_form_restriction), test.schutz, schutz, { schutzName(it) }) { test = test.copy(schutz = it) }
                    }
                    OutlinedTextField(test.datum, { test = test.copy(datum = it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.dna_form_date)) }, singleLine = true)
                    // Eine Haplogruppe gibt es nur fuer Y-DNA und mtDNA
                    if (test.art != "Autosomal") OutlinedTextField(test.haplogruppe, { test = test.copy(haplogruppe = it) }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Res.string.dna_form_haplogroup)) }, singleLine = true)
                    OutlinedTextField(test.notiz, { test = test.copy(notiz = it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.dna_form_note)) }, minLines = 2)
                } else {
                    PersonSuche(client, tree, xref, andere) { andere = it }
                    if (andere == null) {
                        OutlinedTextField(treffer.name, { treffer = treffer.copy(name = it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.dna_form_name)) }, singleLine = true)
                        Text(stringResource(Res.string.dna_form_living_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(treffer.cm, { treffer = treffer.copy(cm = it) }, Modifier.weight(1.4f), label = { Text(stringResource(Res.string.dna_form_cm)) }, singleLine = true)
                        OutlinedTextField(treffer.segmente, { treffer = treffer.copy(segmente = it) }, Modifier.weight(1f), label = { Text(stringResource(Res.string.dna_form_segments)) }, singleLine = true)
                        OutlinedTextField(treffer.laengstes, { treffer = treffer.copy(laengstes = it) }, Modifier.weight(1.4f), label = { Text(stringResource(Res.string.dna_form_longest)) }, singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Auswahlfeld(stringResource(Res.string.dna_form_provider), treffer.anbieter, anbieter, { it.ifEmpty { "–" } }) { treffer = treffer.copy(anbieter = it) }
                        Auswahlfeld(stringResource(Res.string.dna_form_restriction), treffer.schutz, schutz, { schutzName(it) }) { treffer = treffer.copy(schutz = it) }
                        OutlinedTextField(treffer.datum, { treffer = treffer.copy(datum = it) }, Modifier.weight(1f), label = { Text(stringResource(Res.string.dna_form_date)) }, singleLine = true)
                    }
                    OutlinedTextField(treffer.notiz, { treffer = treffer.copy(notiz = it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.dna_form_note)) }, minLines = 2)
                    OutlinedTextField(treffer.tabelle, { treffer = treffer.copy(tabelle = it) }, Modifier.fillMaxWidth().heightIn(max = 140.dp),
                        label = { Text(stringResource(Res.string.dna_form_table)) }, minLines = 2)
                }
                fehler?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = bereit, onClick = {
                laeuft = true; fehler = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            if (art == DnaFormular.Test) client.dnaTestAnlegen(tree, xref, test)
                            else client.dnaTrefferAnlegen(tree, xref, treffer.copy(andere = andere?.xref.orEmpty()))
                        }
                    }.onSuccess { onClose(true) }.onFailure { fehler = meldung(it) }
                    laeuft = false
                }
            }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = { onClose(false) }) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Der Schutz in Worten; leer heisst: das Modul entscheidet nach seiner Regel. */
@Composable
private fun schutzName(code: String): String = when (code) {
    "" -> stringResource(Res.string.dna_form_restriction_auto)
    "confidential" -> stringResource(Res.string.dna_restriction_confidential)
    "privacy" -> stringResource(Res.string.dna_restriction_privacy)
    "none" -> stringResource(Res.string.dna_restriction_none)
    else -> code
}

/** Eine kleine Auswahl mit Beschriftung: Knopf mit dem aktuellen Wert, Klick klappt die Werte auf. */
@Composable
private fun Auswahlfeld(label: String, wert: String, werte: List<String>, text: @Composable (String) -> String, onWahl: (String) -> Unit) {
    var offen by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { offen = true }, shape = MaterialTheme.shapes.small) { Text(text(wert)) }
            DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
                werte.forEach { w -> DropdownMenuItem(text = { Text(text(w)) }, onClick = { onWahl(w); offen = false }) }
            }
        }
    }
}

/**
 * Die andere Person des Treffers aus dem Stammbaum waehlen: Namen tippen, einen der Vorschlaege anklicken. Gewaehlt steht
 * sie mit Lebensdaten da; ein Klick darauf hebt die Wahl wieder auf.
 */
@Composable
private fun PersonSuche(client: WtClient, tree: String, selbst: String, gewaehlt: Person?, onWahl: (Person?) -> Unit) {
    var text by remember { mutableStateOf("") }
    val vorschlaege by produceState(emptyList<Person>(), text) {
        value = emptyList()
        if (text.trim().length >= 2) {
            delay(300)
            value = withContext(Dispatchers.IO) { runCatching { client.individuals(tree, text.trim(), 1).data }.getOrDefault(emptyList()) }
                .filter { it.xref != selbst && !it.isPrivate }.take(8)
        }
    }
    if (gewaehlt != null) {
        OutlinedButton(onClick = { onWahl(null) }, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
            Text(gewaehlt.name + (if (gewaehlt.lifespan.isNotBlank()) "  (${gewaehlt.lifespan})" else "") + "   ✕")
        }
    } else {
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.dna_form_other)) }, singleLine = true)
        vorschlaege.forEach { p ->
            Text(p.name + if (p.lifespan.isNotBlank()) "  (${p.lifespan})" else "", Modifier.fillMaxWidth().clickable { onWahl(p); text = "" }.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}
