package de.bgghome.webtrees.nativ.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.api.DescendantNode
import de.bgghome.webtrees.nativ.api.EventJson
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.LocalAppName
import de.bgghome.webtrees.nativ.ui.UiState
import org.jetbrains.compose.resources.stringResource

/*
 * Listen (Etappe 4): Ahnenliste nach Kekule, Stammliste der Nachkommen, Ereignisliste des ganzen Baums.
 * Jede Liste erscheint erst in einer Vorschau; von dort drucken oder als PDF speichern.
 */

enum class ListenArt { Ahnen, Stamm, Ereignisse, Personenblatt }

private fun ev(e: EventJson?): String = e?.let { listOfNotNull(it.date?.text?.takeIf(String::isNotBlank), it.place?.name?.takeIf(String::isNotBlank)).joinToString(", ") }.orEmpty()

private fun lebensdaten(p: Person, einzug: Int): List<Zeile> = buildList {
    ev(p.birth).takeIf(String::isNotBlank)?.let { add(Zeile("* $it", einzug)) }
    ev(p.death).takeIf(String::isNotBlank)?.let { add(Zeile("† $it", einzug)) }
}

/** Ahnenliste: Generation fuer Generation, jede Person mit ihrer Kekule-Nummer. */
private fun ahnenliste(zentral: Person, ahnen: Map<Int, Person>): List<Zeile> = buildList {
    add(Zeile(Texte.t(Res.string.desk_title_ancestors, zentral.name), gross = true))
    ahnen.keys.sorted().groupBy { 31 - Integer.numberOfLeadingZeros(it) }.forEach { (g, nummern) ->
        add(Zeile("")); add(Zeile(Texte.t(Res.string.desk_generation, g + 1), fett = true))
        nummern.forEach { n ->
            val p = ahnen.getValue(n)
            add(Zeile("$n   ${p.name.ifBlank { "?" }}", 1, fett = true))
            addAll(lebensdaten(p, 2))
        }
    }
}

/** Stammliste: Nachkommen mit Nummern 1, 1.1, 1.1.2 ... und den Partnern jeder Ehe. */
private fun stammliste(wurzel: DescendantNode): List<Zeile> = buildList {
    add(Zeile(Texte.t(Res.string.desk_title_descendants, wurzel.person.name), gross = true))
    fun knoten(k: DescendantNode, nr: String, tiefe: Int) {
        add(Zeile("$nr   ${k.person.name.ifBlank { "?" }}", tiefe, fett = true))
        addAll(lebensdaten(k.person, tiefe + 1))
        var kind = 0
        k.families.forEach { fam ->
            val heirat = ev(fam.marriage)
            fam.spouse?.let { sp -> add(Zeile("⚭ ${sp.name}" + (if (sp.lifespan.isNotBlank()) " (${sp.lifespan})" else "") + (if (heirat.isNotBlank()) " – $heirat" else ""), tiefe + 1)) }
            fam.children.forEach { knoten(it, "$nr.${++kind}", tiefe + 1) }
        }
    }
    add(Zeile(""))
    knoten(wurzel, "1", 0)
}

/** Ereignisliste: Geburten und Todesfaelle aller sichtbaren Personen, zeitlich geordnet. */
private suspend fun ereignisliste(viewModel: AppViewModel, tree: String, titel: String): List<Zeile> {
    val alle = mutableListOf<Person>()
    var page: Int? = 1
    while (page != null && alle.size < 20000) {
        val r = viewModel.client.individuals(tree, "", page)
        alle += r.data; page = r.nextPage
    }
    val geburt = Texte.t(Res.string.desk_birth); val tod = Texte.t(Res.string.desk_death)
    data class E(val jd: Int, val jahr: Int, val text: String)
    val ereignisse = alle.filter { !it.isPrivate }.flatMap { p ->
        listOfNotNull(
            p.birth?.date?.takeIf { it.jd > 0 }?.let { d -> E(d.jd, d.year, "${d.text}   $geburt   ${p.name}" + (p.birth.place?.name?.takeIf(String::isNotBlank)?.let { ", $it" } ?: "")) },
            p.death?.date?.takeIf { it.jd > 0 }?.let { d -> E(d.jd, d.year, "${d.text}   $tod   ${p.name}" + (p.death.place?.name?.takeIf(String::isNotBlank)?.let { ", $it" } ?: "")) },
        )
    }.sortedBy { it.jd }
    return buildList {
        add(Zeile(Texte.t(Res.string.desk_title_events, titel), gross = true))
        ereignisse.groupBy { it.jahr / 100 }.forEach { (jh, liste) ->
            add(Zeile("")); add(Zeile("${jh * 100}–${jh * 100 + 99}", fett = true))
            liste.forEach { add(Zeile(it.text, 1)) }
        }
    }
}

/** Die Zeilen einer Liste fuer die aktuelle Zentralperson (bzw. den Baum). */
suspend fun listenZeilen(art: ListenArt, state: UiState, viewModel: AppViewModel): List<Zeile> {
    val tree = state.tree ?: return emptyList()
    val root = state.root ?: return emptyList()
    return when (art) {
        ListenArt.Ahnen -> {
            val ped = viewModel.client.pedigree(tree.name, root, 7)
            val ahnen = ped.ancestors.associate { it.n to it.person }
            ahnen[1]?.let { ahnenliste(it, ahnen) }.orEmpty()
        }
        ListenArt.Stamm -> stammliste(viewModel.client.descendants(tree.name, root, 4).tree)
        ListenArt.Ereignisse -> ereignisliste(viewModel, tree.name, tree.title)
        ListenArt.Personenblatt -> personenblattZeilen(viewModel.client.individual(tree.name, root))
    }
}

/** Vorschau einer Liste in einem eigenen Fenster, mit Drucken und PDF. */
@Composable
fun ListenFenster(art: ListenArt, state: UiState, viewModel: AppViewModel, onClose: () -> Unit) {
    val appName = LocalAppName.current
    val baum = state.tree?.title.orEmpty()
    val zeilen by produceState<List<Zeile>?>(null, art, state.root) { value = runCatching { listenZeilen(art, state, viewModel) }.getOrElse { listOf(Zeile(it.message ?: "Fehler")) } }
    val titel = zeilen?.firstOrNull()?.text ?: stringResource(Res.string.desk_building)
    DialogWindow(
        onCloseRequest = onClose, title = titel,
        state = rememberDialogState(width = 760.dp, height = 820.dp),
        onPreviewKeyEvent = { e -> if (e.key == Key.Escape) { onClose(); true } else false },
    ) {
        DeskTheme {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val z = zeilen
                    OutlinedButton(shape = MaterialTheme.shapes.small, enabled = !z.isNullOrEmpty(), onClick = { z?.let { drucken(listenPdf(it, appName, baum), titel) } }) { Text(stringResource(Res.string.desk_print)) }
                    OutlinedButton(shape = MaterialTheme.shapes.small, enabled = !z.isNullOrEmpty(), onClick = { z?.let { alsPdf(listenPdf(it, appName, baum), titel) } }) { Text(stringResource(Res.string.desk_save_pdf)) }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(shape = MaterialTheme.shapes.small, onClick = onClose) { Text(stringResource(Res.string.action_close)) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val z = zeilen
                if (z == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    val list = rememberLazyListState()
                    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                        LazyColumn(Modifier.fillMaxSize(), state = list) {
                            items(z) { zeile ->
                                Text(
                                    zeile.text,
                                    Modifier.padding(start = (zeile.einzug * 18).dp, top = if (zeile.gross) 12.dp else 1.dp, bottom = if (zeile.gross) 6.dp else 1.dp),
                                    style = if (zeile.gross) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (zeile.fett || zeile.gross) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                        ListenLeiste(list)
                    }
                }
            }
        }
    }
}
