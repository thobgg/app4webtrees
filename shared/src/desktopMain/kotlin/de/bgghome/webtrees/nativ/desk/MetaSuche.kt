package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.data.GedcomName
import de.bgghome.webtrees.nativ.data.Ortsnamen
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.WtAlertDialog
import de.bgghome.webtrees.nativ.ui.openBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.net.URLEncoder

/*
 * CompGen-Meta-Suche (meta.genealogy.net): sucht einen Familiennamen, wahlweise mit Ort, gleichzeitig in GEDBAS,
 * Adressbuechern, Online-OFBs, Verlustlisten und weiteren Datenbanken des Vereins fuer Computergenealogie. Die Seite
 * nimmt die Suche als Adresse entgegen und fragt die Datenbanken im Browser ab.
 *
 * Gesendet wird nur auf Klick und nur, was der Dialog zeigt: der Familienname und auf Wunsch ein Ortsname - keine
 * Vornamen, keine Daten. Der Ort ist eine Einschraenkung, die manche Datenbanken streng nehmen und manche nicht kennen;
 * darum ist "ohne Ort" vorgewaehlt.
 */

private val ZUSATZ = setOf("von", "vom", "van", "de", "der", "den", "die", "zu", "zum", "zur", "ten", "ter", "te", "du", "des", "la", "le", "di", "da", "dos", "y")

/** Der Familienname fuer die Suche: ohne Namenszusatz ("von Buelow" -> "Buelow", "van der Berg" -> "Berg"). */
internal fun metaNachname(familienname: String): String {
    val teile = familienname.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    val rest = teile.dropWhile { it.lowercase().trimEnd('\'', '’') in ZUSATZ || it.endsWith("'") || it.endsWith("’") }
    return (rest.ifEmpty { teile }).joinToString(" ").removePrefix("d'").removePrefix("d’")
}

/** Alle Familiennamen der Person (Hauptname zuerst, dann weitere Namen wie Ehename), je einmal. */
internal fun metaNamen(d: IndividualDetail): List<String> {
    val aus = d.facts.filter { it.tag == "NAME" }.map { GedcomName.aus(it.gedcomValue ?: it.value).familienname }
    return (listOf(d.person.surname) + aus).map(::metaNachname).filter { it.isNotBlank() && it != "@N.N." && it != "…" }.distinctBy { it.lowercase() }
}

private val ORT_TAGS = listOf("BIRT", "CHR", "BAPM", "MARR", "RESI", "DEAT", "BURI")

/** Ein Ortsname fuer die Suche: der erste Teil ohne Hausnummer ("Hof Nr. 3, Bienenbuettel, Uelzen" -> "Bienenbuettel"). */
internal fun metaOrt(name: String): String {
    val teile = Ortsnamen.teile(name)
    return teile.firstOrNull { t -> t.none(Char::isDigit) } ?: teile.firstOrNull().orEmpty()
}

/** Die Orte der Person mit dem Ereignis, an dem sie zuerst vorkommen: Geburt/Taufe, Heirat, Wohnort, Tod/Begraebnis. */
internal fun metaOrte(d: IndividualDetail): List<Pair<String, FactJson>> {
    val fakten = d.facts + d.spouseFamilies.flatMap { it.facts }
    return ORT_TAGS.flatMap { tag -> fakten.filter { it.tag == tag } }
        .mapNotNull { f -> f.place?.name?.let(::metaOrt)?.takeIf(String::isNotBlank)?.let { it to f } }
        .distinctBy { it.first.lowercase() }
}

/** Die Adresse der Ergebnisseite; Umlaute und Leerzeichen als UTF-8 kodiert. */
internal fun metaSucheUrl(nachname: String, ort: String? = null): String {
    fun k(s: String) = URLEncoder.encode(s.trim(), Charsets.UTF_8).replace("+", "%20")
    return "https://meta.genealogy.net/search?lastname=" + k(nachname) + (ort?.takeIf { it.isNotBlank() }?.let { "&placename=" + k(it) } ?: "")
}

/** Die Person, fuer die der Dialog offen ist; die Menues setzen sie, der Dialog wird im Fenster gezeichnet. */
open class MetaSucheWahl {
    var person: IndividualDetail? by mutableStateOf(null)

    fun fuer(d: IndividualDetail) { person = d }
}

/** Je Fenster eine Wahl: das Hauptfenster nimmt [MetaSuche], das Pruef-Fenster von gedaudit eine eigene. */
val LocalMetaSuche = androidx.compose.runtime.staticCompositionLocalOf<MetaSucheWahl> { MetaSuche }

object MetaSuche : MetaSucheWahl() {

    /** Aus Rechtsklick-Menues mit nur einer Kennung: die angezeigte Person nehmen oder nachladen. */
    fun fuer(viewModel: AppViewModel, xref: String) {
        val st = viewModel.uiState.value
        st.detail?.takeIf { it.person.xref == xref }?.let { fuer(it); return }
        val tree = st.tree?.name ?: return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { viewModel.client.individual(tree, xref) }.getOrNull()?.let { fuer(it) }
        }
    }
}

@Composable
fun MetaSucheDialog(wahl: MetaSucheWahl = MetaSuche) {
    val d = wahl.person ?: return
    val namen = remember(d) { metaNamen(d) }
    val orte = remember(d) { metaOrte(d) }
    var name by remember(d) { mutableStateOf(namen.firstOrNull()) }
    var ort by remember(d) { mutableStateOf<String?>(null) }
    val schliessen = { wahl.person = null }
    WtAlertDialog(
        onDismissRequest = schliessen,
        title = { Text(stringResource(Res.string.desk_meta_title)) },
        text = {
            Column(Modifier.width(460.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (namen.isEmpty()) {
                    Text(stringResource(Res.string.desk_meta_no_name), style = MaterialTheme.typography.bodyMedium)
                    return@Column
                }
                Text(stringResource(Res.string.desk_meta_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(Res.string.desk_meta_surname), style = MaterialTheme.typography.labelLarge)
                namen.forEach { n -> Wahl(n, name == n) { name = n } }
                Text(stringResource(Res.string.desk_meta_place), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                Wahl(stringResource(Res.string.desk_meta_no_place), ort == null) { ort = null }
                orte.forEach { (o, f) -> Wahl("$o  ·  ${f.label.ifBlank { f.tag }}", ort == o) { ort = o } }
                if (ort != null) Text(stringResource(Res.string.desk_meta_place_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            if (name != null) TextButton(onClick = { openBrowser(metaSucheUrl(name!!, ort)); schliessen() }) { Text(stringResource(Res.string.desk_meta_search)) }
        },
        dismissButton = { TextButton(onClick = schliessen) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
private fun Wahl(text: String, gewaehlt: Boolean, onClick: () -> Unit) {
    Row(Modifier.clickable(onClick = onClick).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(gewaehlt, onClick)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
