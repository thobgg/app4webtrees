package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.api.DNA_STOP_GESCHLECHT
import de.bgghome.webtrees.nativ.api.DNA_STOP_NICHT_LEIBLICH
import de.bgghome.webtrees.nativ.api.DnaGeneration
import de.bgghome.webtrees.nativ.api.DnaLinie
import de.bgghome.webtrees.nativ.api.DnaLinien
import de.bgghome.webtrees.nativ.api.DnaNachkomme
import de.bgghome.webtrees.nativ.api.DescendantNode
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.res.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/*
 * DNA-Vererbungslinien einer Person: von welchen Vorfahren sie Y-DNA, mitochondriale DNA oder X-DNA haben kann und
 * welche Nachkommen sie von ihr haben koennen. Das Modul dna4webtrees rechnet, hier wird nur gezeigt - dieselbe Ansicht
 * fuer Handy und PC. Das Modul nennt Personen nur als Kennung; Namen und Bilder kommen wie ueberall aus api4webtrees. Die Linien sind aus den eingetragenen Verwandtschaften abgeleitet, kein
 * Testergebnis; das sagt der Hinweis ueber der Liste.
 */

/** So viele Generationen liefert der Server hoechstens: die X-Linie verzweigt sich, Y und mt sind eine Person je Generation. */
fun dnaHoechstens(linie: DnaLinie): Int = if (linie == DnaLinie.X) 12 else 30

/** Eine Zeile der Nachkommenliste: der Knoten und wie tief er unter der Ausgangsperson steht. */
class DnaZeile(val tiefe: Int, val knoten: DnaNachkomme)

/** Der Nachkommenbaum als eingerueckte Liste, Kinder direkt unter ihrem Elternteil. */
fun DnaNachkomme.zeilen(tiefe: Int = 0): List<DnaZeile> = listOf(DnaZeile(tiefe, this)) + children.flatMap { it.zeilen(tiefe + 1) }

/** Welcher Hinweis ueber den Nachkommen steht, wenn die Ausgangsperson die Linie nicht weitergibt oder niemand folgt. */
enum class DnaNachkommenLage { KeinY, MannMt, Keine, Liste }

fun dnaNachkommenLage(linie: DnaLinie, wurzel: DnaNachkomme): DnaNachkommenLage = when {
    !wurzel.carrier -> DnaNachkommenLage.KeinY
    linie == DnaLinie.Mt && wurzel.sex == "M" -> DnaNachkommenLage.MannMt
    wurzel.stop == null && wurzel.children.isEmpty() -> DnaNachkommenLage.Keine
    else -> DnaNachkommenLage.Liste
}

/** Nur die Ausgangsperson und kein Vermerk, warum es nicht weitergeht: der Baum kennt auf der Linie keine Vorfahren. */
fun dnaOhneVorfahren(generationen: List<DnaGeneration>): Boolean =
    generationen.size == 1 && generationen[0].people.firstOrNull()?.stop == null

/** Die Linien samt den Personen dazu. */
class DnaLinienErgebnis(val linien: DnaLinien, private val personen: Map<String, Person>) {
    /** Die Person zur Kennung; war sie nicht zu laden, steht ihre Kennung als Name, vertrauliche bleiben "Privat". */
    fun person(xref: String, sex: String, privat: Boolean): Person =
        personen[xref] ?: Person(xref, name = if (privat) "" else xref, sex = sex, isPrivate = privat)
}

/**
 * Linien vom Modul holen und die Personen dazu ueber api4webtrees. Fuer die verzweigte X-Linie und fuer Nachkommen
 * liefert ein Abruf (Ahnentafel bzw. Nachkommenbaum der Ausgangsperson) fast alle; der Rest kommt einzeln.
 */
suspend fun dnaLinienLaden(client: WtClient, tree: String, xref: String, linie: DnaLinie, nachkommen: Boolean, generationen: Int): DnaLinienErgebnis = coroutineScope {
    val l = client.dnaLinien(tree, xref, linie, nachkommen, generationen)
    val gesucht = (l.generationsList.flatMap { g -> g.people.filter { !it.private }.map { it.xref } } +
        l.tree?.zeilen().orEmpty().filter { !it.knoten.private }.map { it.knoten.xref }).toSet()
    val personen = HashMap<String, Person>()
    runCatching {
        if (nachkommen) {
            fun sammeln(n: DescendantNode) { personen[n.person.xref] = n.person; n.families.forEach { f -> f.children.forEach(::sammeln) } }
            sammeln(client.descendants(tree, xref, minOf(10, generationen + 1)).tree)
        } else if (linie == DnaLinie.X) {
            client.pedigree(tree, xref, minOf(12, generationen + 1)).ancestors.forEach { personen[it.person.xref] = it.person }
        }
    }
    (gesucht - personen.keys).chunked(8).forEach { gruppe ->
        gruppe.map { x -> async { runCatching { client.individual(tree, x).person }.getOrNull() } }.awaitAll().filterNotNull().forEach { personen[it.xref] = it }
    }
    DnaLinienErgebnis(l, personen)
}

@Composable
private fun stopText(stop: String?): String? = when (stop) {
    DNA_STOP_NICHT_LEIBLICH -> stringResource(Res.string.dna_stop_nonbio)
    DNA_STOP_GESCHLECHT -> stringResource(Res.string.dna_stop_sex)
    else -> null
}

/**
 * Auswahl (Linie, Richtung, Generationen) und die Liste dazu. [onPerson]: Tipp bzw. Klick auf eine Person; private
 * Personen sind nicht waehlbar. [zuerst], [zuerstNachkommen]: womit die Ansicht aufgeht.
 */
@Composable
fun DnaLinienInhalt(
    client: WtClient, tree: String, xref: String, onPerson: (String) -> Unit, modifier: Modifier = Modifier,
    zuerst: DnaLinie = DnaLinie.Y, zuerstNachkommen: Boolean = false,
) {
    var linie by remember { mutableStateOf(zuerst) }
    var nachkommen by remember { mutableStateOf(zuerstNachkommen) }
    var gewuenscht by remember { mutableIntStateOf(8) }
    val generationen = gewuenscht.coerceIn(1, dnaHoechstens(linie))
    val farben = MaterialTheme.colorScheme

    val ergebnis by produceState<Result<DnaLinienErgebnis>?>(null, tree, xref, linie, nachkommen, generationen) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { dnaLinienLaden(client, tree, xref, linie, nachkommen, generationen) } }
    }

    // Unten Luft, damit die letzte Zeile am Handy nicht an der Navigationsleiste klebt
    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item("wahl") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ChipRow(
                    listOf("y" to stringResource(Res.string.dna_line_y), "mt" to stringResource(Res.string.dna_line_mt), "x" to stringResource(Res.string.dna_line_x)),
                    linie.code,
                ) { c -> linie = DnaLinie.entries.first { it.code == c } }
                ChipRow(listOf("a" to stringResource(Res.string.dna_ancestors), "d" to stringResource(Res.string.dna_descendants)), if (nachkommen) "d" else "a") { nachkommen = it == "d" }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.desk_chart_generations), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { gewuenscht = generationen - 1 }, enabled = generationen > 1) { Text("−") }
                    Text(generationen.toString(), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { gewuenscht = generationen + 1 }, enabled = generationen < dnaHoechstens(linie)) { Text("+") }
                }
                Hinweis(stringResource(Res.string.dna_hint) + if (linie == DnaLinie.X) " " + stringResource(Res.string.dna_hint_x) else "", warnung = false)
            }
        }
        val e = ergebnis
        when {
            e == null -> item("laden") { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            e.isFailure -> item("fehler") { Text(meldung(e.exceptionOrNull()), Modifier.padding(16.dp), color = farben.error) }
            nachkommen -> e.getOrThrow().linien.tree?.let { wurzel ->
                item("lage") {
                    when (dnaNachkommenLage(linie, wurzel)) {
                        DnaNachkommenLage.KeinY -> Hinweis(stringResource(Res.string.dna_y_no_desc), Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp))
                        DnaNachkommenLage.MannMt -> Hinweis(stringResource(Res.string.dna_mt_man), Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp))
                        DnaNachkommenLage.Keine -> Zwischenzeile(stringResource(Res.string.dna_no_descendants))
                        DnaNachkommenLage.Liste -> Zwischenzeile(stringResource(Res.string.dna_desc_count, e.getOrThrow().linien.count))
                    }
                }
                items(wurzel.zeilen()) { z ->
                    val k = z.knoten
                    val vermerk = stopText(k.stop) ?: if (k.carrier && !k.passesOn && linie == DnaLinie.Mt && z.tiefe > 0) stringResource(Res.string.dna_mt_no_pass) else null
                    Column(Modifier.padding(start = (z.tiefe * 20).dp)) {
                        PersonRow(e.getOrThrow().person(k.xref, k.sex, k.private), selected = z.tiefe == 0, onClick = { onPerson(k.xref) })
                        vermerk?.let { Vermerk(it) }
                    }
                }
            }
            else -> {
                val liste = e.getOrThrow().linien.generationsList
                val start = liste.firstOrNull()?.people?.firstOrNull()
                if (linie == DnaLinie.Y && start?.sex == "F") item("frau") { Hinweis(stringResource(Res.string.dna_y_woman), Modifier.padding(horizontal = 12.dp)) }
                liste.forEach { g ->
                    item("g${g.generation}") {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
                            Text(
                                if (g.generation == 0) stringResource(Res.string.dna_start) else stringResource(Res.string.dna_generation, g.generation),
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                            )
                            if (g.generation > 0 && linie == DnaLinie.X) {
                                Text(stringResource(Res.string.dna_x_count, g.people.size, g.possible), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
                            }
                        }
                    }
                    items(g.people, key = { "g${g.generation}-${it.xref}-${it.sosa.firstOrNull()}" }) { v ->
                        Column {
                            PersonRow(
                                e.getOrThrow().person(v.xref, v.sex, v.private), selected = g.generation == 0, onClick = { onPerson(v.xref) },
                                trailing = if (g.generation > 0) ({ Text(stringResource(Res.string.dna_number, v.sosa.joinToString(", ")), style = MaterialTheme.typography.labelMedium) }) else null,
                            )
                            stopText(v.stop)?.let { Vermerk(it) }
                        }
                    }
                }
                if (dnaOhneVorfahren(liste)) item("leer") { Zwischenzeile(stringResource(Res.string.dna_no_ancestors)) }
            }
        }
    }
}

/** Kasten mit Erklaerung; [warnung]: betrifft die gewaehlte Person (z. B. Y-Linie einer Frau). */
@Composable
private fun Hinweis(text: String, modifier: Modifier = Modifier, warnung: Boolean = true) {
    val farben = MaterialTheme.colorScheme
    Text(
        text,
        modifier.fillMaxWidth().background(if (warnung) farben.tertiaryContainer else farben.surfaceVariant, RoundedCornerShape(8.dp)).padding(10.dp),
        style = MaterialTheme.typography.bodySmall, color = if (warnung) farben.onTertiaryContainer else farben.onSurfaceVariant,
    )
}

@Composable
private fun Zwischenzeile(text: String) {
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
}

/** Warum die Linie an dieser Person nicht weitergeht - steht unter ihrer Zeile, eingerueckt bis zum Namen. */
@Composable
private fun Vermerk(text: String) {
    Text(text, Modifier.padding(start = 76.dp, end = 16.dp, bottom = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Handy: die Linien bildschirmfuellend ueber dem Profil; ein Tipp auf eine Person schliesst und oeffnet ihr Profil. */
@Composable
fun DnaLinienDialog(client: WtClient, tree: String, xref: String, name: String, onPerson: (String) -> Unit, onDismiss: () -> Unit, onLink: (String) -> Unit = {}) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.IconButton(onClick = onDismiss) {
                        androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                    Text(stringResource(Res.string.dna_title_of, name), style = MaterialTheme.typography.titleMedium, maxLines = 2)
                }
                DnaAnsicht(client, tree, xref, onPerson = { onDismiss(); onPerson(it) }, Modifier.weight(1f), onLink = onLink)
            }
        }
    }
}
