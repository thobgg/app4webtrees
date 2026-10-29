package de.bgghome.webtrees.nativ.ui

import de.bgghome.webtrees.nativ.data.notizenOhnePaten
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import de.bgghome.webtrees.nativ.api.CitationRequest
import de.bgghome.webtrees.nativ.api.SourceSummary
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.api.AddIndividualRequest
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.FactRequest
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.TagInfo
import org.jetbrains.compose.resources.stringResource
import de.bgghome.webtrees.nativ.res.*

// Die Dialoge der App: Rueckfrage, Auswahl, Ereignis anlegen/aendern, Verwandte anlegen.

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    WtAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { if (text.isNotEmpty()) Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Einfache Auswahl aus einer kurzen Liste (z. B. zu welcher Partnerschaft ein Ereignis gehoert). */
@Composable
fun ChoiceDialog(title: String, options: List<Pair<String, String>>, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    WtAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (key, label) ->
                    TextButton(onClick = { onChoose(key) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** fact == null: neues Ereignis (mit Auswahl der Art), sonst Aendern. */
@Composable
fun FactDialog(
    fact: FactJson?,
    tags: List<TagInfo>,
    suggestPlaces: PlaceSuggest?,
    onDismiss: () -> Unit,
    onSave: (FactRequest) -> Unit,
    /** Feld "Art" bei der Heirat (standesamtlich, kirchlich ...) - nur mit Server ab API-Stufe 20. */
    typErlaubt: Boolean = false,
    /** Quellen des Baums zum Zitieren (ab API-Stufe 18); null = kein Abschnitt "Quelle zitieren" (Issue 39). */
    quellen: (suspend () -> List<SourceSummary>)? = null,
    /** Speichern mit Quellenverweis: erst das Ereignis, dann der Verweis daran. */
    onSaveMitZitat: ((FactRequest, CitationRequest) -> Unit)? = null,
) {
    var tag by remember { mutableStateOf(tags.firstOrNull()) }
    var tagMenu by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(fact?.value.orEmpty()) }
    val date = rememberDateInput(gedcom = fact?.date?.gedcom.orEmpty(), displayText = fact?.date?.text.orEmpty())
    var place by remember { mutableStateOf(fact?.place?.name.orEmpty()) }
    // Die Patenliste ("Paten: ...") ist keine gewoehnliche Notiz - sie wird ueber "Paten bearbeiten" gepflegt.
    val originalNote = fact?.notizenOhnePaten()?.firstOrNull().orEmpty()
    var note by remember { mutableStateOf(originalNote) }
    val originalTyp = de.bgghome.webtrees.nativ.data.Heiratsart.aus(fact?.type.orEmpty())
    var typ by remember { mutableStateOf(originalTyp) }
    // Name: Vornamen, Familienname und Zusatz getrennt; die Schraegstriche setzt das Programm selbst.
    val alterName = remember(fact) { de.bgghome.webtrees.nativ.data.GedcomName.aus(fact?.value.orEmpty()) }
    var name by remember(fact) { mutableStateOf(alterName) }
    // Quelle gleich mit erfassen (Issue 39): aus der Liste (Kennung) oder als freier Text, dazu die Seite.
    var zitieren by remember { mutableStateOf(false) }
    var textQuelle by remember { mutableStateOf(false) }
    var quelle by remember { mutableStateOf("") }
    var freitext by remember { mutableStateOf("") }
    var seite by remember { mutableStateOf("") }
    val quelleGewaehlt = if (textQuelle) freitext.isNotBlank() else quelle.isNotBlank()

    val isNameOrNote = fact?.tag == "NAME" || fact?.tag == "NOTE" || (fact == null && tag?.tag == "NOTE")

    WtAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(fact?.label ?: stringResource(Res.string.fact_new)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (fact == null) {
                    Box {
                        OutlinedButton(onClick = { tagMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(tag?.label ?: stringResource(Res.string.fact_choose_type))
                        }
                        DropdownMenu(expanded = tagMenu, onDismissRequest = { tagMenu = false }) {
                            tags.forEach { option ->
                                DropdownMenuItem(text = { Text(option.label) }, onClick = { tag = option; tagMenu = false })
                            }
                        }
                    }
                }
                val isNote = fact?.tag == "NOTE" || (fact == null && tag?.tag == "NOTE")
                if (fact?.tag == "NAME") {
                    Field(name.vornamen, { name = name.copy(vornamen = it) }, Res.string.field_given)
                    Field(name.familienname, { name = name.copy(familienname = it) }, Res.string.field_family_name)
                    Field(name.zusatz, { name = name.copy(zusatz = it) }, Res.string.field_name_suffix)
                } else {
                    Field(value, { value = it }, if (isNameOrNote) Res.string.fact_text else Res.string.fact_value_hint, minLines = if (isNote) 3 else 1)
                }
                if (!isNameOrNote) {
                    DateInput(date, Res.string.fact_date)
                    PlaceField(place, { place = it }, Res.string.fact_place, suggestPlaces, hint = Res.string.fact_place_hint)
                    if (typErlaubt && (fact?.tag ?: tag?.tag) == "MARR") {
                        Text(stringResource(Res.string.desk_detail_type), style = MaterialTheme.typography.labelMedium)
                        val arten = listOf("" to stringResource(Res.string.marr_none)) +
                            de.bgghome.webtrees.nativ.data.Heiratsart.entries.map { it.name to heiratsartText(it) }
                        ChipRow(arten, typ?.name.orEmpty()) { key -> typ = de.bgghome.webtrees.nativ.data.Heiratsart.entries.firstOrNull { it.name == key } }
                    }
                    Field(note, { note = it }, Res.string.fact_note, minLines = 2)
                    if (quellen != null && onSaveMitZitat != null) {
                        TextButton(onClick = { zitieren = !zitieren }) {
                            Text(stringResource(if (zitieren) Res.string.fact_source_none else Res.string.desk_cite_add))
                        }
                        if (zitieren) QuellenWahl(quellen, textQuelle, { textQuelle = it }, quelle, { quelle = it }, freitext, { freitext = it }, seite, { seite = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = (fact != null || tag != null) && date.result != null && (!zitieren || quelleGewaehlt),
                onClick = {
                    val zitat = if (zitieren && quelleGewaehlt) CitationRequest(source = if (textQuelle) freitext.trim() else quelle, page = seite.trim().ifEmpty { null }) else null
                    val speichern: (FactRequest) -> Unit = { r -> if (zitat != null && onSaveMitZitat != null) onSaveMitZitat(r, zitat) else onSave(r) }
                    speichern(
                        if (fact == null) {
                            FactRequest(
                                tag = tag?.tag, value = value.trim(),
                                date = date.result?.ifEmpty { null },
                                place = place.trim().ifEmpty { null },
                                note = note.trim().ifEmpty { null },
                                type = typ?.takeIf { typErlaubt }?.let(::typSchluessel),
                            )
                        } else {
                            // Nur senden, was geaendert wurde - alles andere (Quellen, Koordinaten ...) bleibt auf dem Server unberuehrt.
                            FactRequest(
                                factId = fact.id,
                                value = if (fact.tag == "NAME") name.takeIf { it != alterName }?.gedcom() else value.trim().takeIf { it != fact.value },
                                date = if (date.changed) date.result else null,
                                place = place.trim().takeIf { it != fact.place?.name.orEmpty() },
                                note = note.trim().takeIf { it != originalNote },
                                type = if (typErlaubt && typ != originalTyp) typ?.let(::typSchluessel) ?: "" else null,
                            )
                        }
                    )
                },
            ) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Abschnitt "Quelle zitieren" im Ereignis-Dialog (Issue 39): Quelle aus der Liste (mit Suche) oder als Text, dazu die Seite. */
@Composable
private fun QuellenWahl(
    quellen: suspend () -> List<SourceSummary>,
    textQuelle: Boolean, onTextQuelle: (Boolean) -> Unit,
    quelle: String, onQuelle: (String) -> Unit,
    freitext: String, onFreitext: (String) -> Unit,
    seite: String, onSeite: (String) -> Unit,
) {
    var suche by remember { mutableStateOf("") }
    val liste by produceState<List<SourceSummary>?>(null) { value = runCatching { quellen() }.getOrDefault(emptyList()) }

    ChipRow(listOf("list" to stringResource(Res.string.desk_cite_from_list), "text" to stringResource(Res.string.desk_cite_as_text)), if (textQuelle) "text" else "list") { onTextQuelle(it == "text") }
    if (textQuelle) {
        Field(freitext, onFreitext, Res.string.desk_cite_text_source, hint = Res.string.desk_cite_text_source_hint)
    } else {
        Field(suche, { suche = it }, Res.string.desk_sources_search)
        val treffer = liste.orEmpty().filter { q -> suche.isBlank() || listOf(q.title, q.author, q.abbreviation).any { it.contains(suche.trim(), ignoreCase = true) } }
        // Feste Hoehe: die Liste rollt in sich, der Dialog darum herum.
        Box(Modifier.fillMaxWidth().height(180.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)) {
            if (liste == null) Text(stringResource(Res.string.desk_chart_loading_any), Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else LazyColumn(Modifier.fillMaxWidth()) {
                items(treffer, key = { it.xref }) { q ->
                    val aktiv = q.xref == quelle
                    Text(
                        q.title.ifBlank { q.xref } + (q.author.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                        Modifier.fillMaxWidth().background(if (aktiv) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                            .clickable { onQuelle(q.xref) }.padding(horizontal = 8.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = if (aktiv) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    Field(seite, onSeite, Res.string.desk_citation_page, hint = Res.string.desk_cite_page_hint)
}

/** Wem wird jemand hinzugefuegt - und welche Beziehungen stehen zur Wahl. */
data class RelativeTarget(
    val person: Person,
    /** child | spouse | father | mother */
    val relations: List<String>,
    /** Verbindungen, aus denen ein Kind stammen kann: Familien-XREF -> Name des Partners (null = unbekannt) */
    val families: List<Pair<String, String?>> = emptyList(),
) {
    companion object {
        fun of(detail: IndividualDetail): RelativeTarget {
            val parents = detail.parentFamilies.firstOrNull()
            val relations = buildList {
                add("child"); add("spouse")
                if (parents?.husband == null) add("father")
                if (parents?.wife == null) add("mother")
            }

            return RelativeTarget(
                detail.person,
                relations,
                detail.spouseFamilies.map { it.xref to it.spouse?.name },
            )
        }
    }
}

@Composable
fun RelativeDialog(
    target: RelativeTarget, suggestPlaces: PlaceSuggest?, onDismiss: () -> Unit, onSave: (AddIndividualRequest) -> Unit,
    /** Vorauswahl (father, mother ...), etwa vom leeren Feld "Vater unbekannt"; ohne sie die erste moegliche Beziehung. */
    initialRelation: String? = null,
    /** Vorauswahl der Familie fuer ein Kind (Familien-XREF); ohne sie die erste. */
    initialFamily: String? = null,
    /** Vorauswahl des Geschlechts ("M"/"F"), etwa von "+ Sohn" / "+ Tochter". */
    initialSex: String? = null,
    /** Dublettenwarnung: sucht Personen nach Namen; null = keine Warnung (Handy). */
    suche: (suspend (String) -> List<Person>)? = null,
    /** "Anfuegen statt neu": die gefundene Person wird mit der gewaehlten Beziehung (und Familie) verknuepft. */
    onLink: ((Person, String, String?) -> Unit)? = null,
) {
    val person = target.person
    val ownSurname = person.sortName.substringBefore(',', "").trim()

    var relation by remember { mutableStateOf(initialRelation?.takeIf { it in target.relations } ?: target.relations.first()) }
    var family by remember { mutableStateOf(target.families.firstOrNull { it.first == initialFamily }?.first ?: target.families.firstOrNull()?.first) }
    var given by remember { mutableStateOf("") }
    // Naheliegender Nachname: der Vater heisst meist wie das Kind, das Kind meist wie der Vater.
    var surname by remember(relation) {
        mutableStateOf(if (relation == "father" || (relation == "child" && person.sex != "F")) ownSurname else "")
    }
    var sex by remember { mutableStateOf(initialSex ?: "U") }
    // Gibt es die Person vielleicht schon? Nach kurzer Pause beim Tippen suchen (gleicher Vor- und Nachname).
    var dubletten by remember { mutableStateOf(emptyList<Person>()) }
    if (suche != null) LaunchedEffect(given, surname) {
        if (given.isBlank() || surname.isBlank()) { dubletten = emptyList(); return@LaunchedEffect }
        delay(500)
        val vn = given.trim().lowercase(); val fn = surname.trim().lowercase()
        dubletten = runCatching { suche("${given.trim()} ${surname.trim()}") }.getOrDefault(emptyList())
            .filter { it.xref != person.xref && !it.isPrivate && it.given.lowercase().startsWith(vn) && it.surname.lowercase() == fn }.take(3)
    }
    val birthDate = rememberDateInput()
    var birthPlace by remember { mutableStateOf("") }
    // Eltern eines Verstorbenen sind fast immer selbst verstorben.
    var dead by remember(relation) { mutableStateOf((relation == "father" || relation == "mother") && person.isDead) }
    val deathDate = rememberDateInput()
    var deathPlace by remember { mutableStateOf("") }
    val marriageDate = rememberDateInput()
    var marriagePlace by remember { mutableStateOf("") }

    WtAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val only = target.relations.singleOrNull()
            Text(
                if (only != null) stringResource(Res.string.relative_title_single, relationLabel(only), person.name)
                else stringResource(Res.string.relative_title, person.name)
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (target.relations.size > 1) ChipRow(target.relations.map { it to relationLabel(it) }, relation) { relation = it }

                // Kind bei mehreren Partnerschaften: aus welcher Verbindung?
                if (relation == "child" && target.families.size > 1) {
                    Text(stringResource(Res.string.relative_child_of), style = MaterialTheme.typography.labelMedium)
                    val options = target.families.map { it.first to (it.second ?: stringResource(Res.string.unknown_person)) }
                    ChipRow(options, family.orEmpty()) { family = it }
                }

                Field(given, { given = it }, Res.string.field_given)
                Field(surname, { surname = it }, Res.string.field_surname)
                if (dubletten.isNotEmpty()) {
                    Text(stringResource(Res.string.relative_exists), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    dubletten.forEach { d ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(d.name + if (d.lifespan.isNotBlank()) "  (${d.lifespan})" else "", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            if (onLink != null) TextButton(onClick = { onLink(d, relation, family.takeIf { relation == "child" }) }) { Text(stringResource(Res.string.relative_link_instead)) }
                        }
                    }
                }

                // Vater und Mutter haben ihr Geschlecht schon durch die Beziehung
                if (relation != "father" && relation != "mother") {
                    val sexes = listOf(
                        "M" to stringResource(Res.string.sex_male),
                        "F" to stringResource(Res.string.sex_female),
                        "U" to stringResource(Res.string.sex_unknown),
                    )
                    ChipRow(sexes, sex) { sex = it }
                }

                DateInput(birthDate, Res.string.field_birth_date)
                PlaceField(birthPlace, { birthPlace = it }, Res.string.field_birth_place, suggestPlaces)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = dead, onCheckedChange = { dead = it })
                    Text(stringResource(Res.string.field_deceased))
                }
                if (dead) {
                    DateInput(deathDate, Res.string.field_death_date)
                    PlaceField(deathPlace, { deathPlace = it }, Res.string.field_death_place, suggestPlaces)
                }
                if (relation == "spouse") {
                    DateInput(marriageDate, Res.string.field_marriage_date)
                    PlaceField(marriagePlace, { marriagePlace = it }, Res.string.field_marriage_place, suggestPlaces)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = (given.isNotBlank() || surname.isNotBlank()) && birthDate.result != null &&
                    (!dead || deathDate.result != null) && (relation != "spouse" || marriageDate.result != null),
                onClick = {
                    onSave(
                        AddIndividualRequest(
                            relation = relation,
                            relativeTo = person.xref,
                            family = family.takeIf { relation == "child" },
                            given = given.trim(),
                            surname = surname.trim(),
                            sex = sex,
                            birthDate = birthDate.result?.ifEmpty { null },
                            birthPlace = birthPlace.trim().ifEmpty { null },
                            dead = dead,
                            deathDate = deathDate.result?.takeIf { dead }?.ifEmpty { null },
                            deathPlace = deathPlace.trim().takeIf { dead }?.ifEmpty { null },
                            marriageDate = marriageDate.result?.takeIf { relation == "spouse" }?.ifEmpty { null },
                            marriagePlace = marriagePlace.trim().takeIf { relation == "spouse" }?.ifEmpty { null },
                        )
                    )
                },
            ) { Text(stringResource(Res.string.action_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Beschriftung einer Beziehung (child | spouse | father | mother) - auch fuer die "+"-Kaestchen im Baum. */
@Composable
fun relationLabel(relation: String): String = stringResource(
    when (relation) {
        "father" -> Res.string.rel_father
        "mother" -> Res.string.rel_mother
        "spouse" -> Res.string.rel_partner
        else -> Res.string.rel_child
    }
)

/**
 * Der Dialog der App. Am Handy der Material-Dialog wie gehabt ("Abbrechen" vor "OK"); am Desktop in der
 * Windows-Reihenfolge - erst die Bestaetigung, dann "Abbrechen", beide rechts.
 */
@Composable
internal fun WtAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    /** Am Desktop breiter als die 560 dp des Material-Dialogs - fuer zweispaltige Dialoge, die sonst rollen muessten. */
    breite: androidx.compose.ui.unit.Dp? = null,
) {
    if (LocalDeskMode.current && breite != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            androidx.compose.material3.Surface(
                shape = androidx.compose.material3.AlertDialogDefaults.shape, color = androidx.compose.material3.AlertDialogDefaults.containerColor,
                tonalElevation = androidx.compose.material3.AlertDialogDefaults.TonalElevation,
                modifier = Modifier.widthIn(max = breite).padding(16.dp),
            ) {
                Column(Modifier.padding(24.dp)) {
                    title?.let {
                        androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.headlineSmall) { Box(Modifier.padding(bottom = 16.dp)) { it() } }
                    }
                    text?.let { Box(Modifier.weight(1f, fill = false)) { it() } }
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        confirmButton(); dismissButton?.invoke()
                    }
                }
            }
        }
    } else if (LocalDeskMode.current && dismissButton != null) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = title, text = text,
            confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { confirmButton(); dismissButton() } },
        )
    } else {
        // Handy: Fenster laesst der Tastatur Platz, der Inhalt rollt (Issue 34); am PC wirkt beides nicht.
        AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, dismissButton = dismissButton, title = title, text = text,
            modifier = Modifier.imePadding(), properties = tastaturDialog())
    }
}

/** Die Heiratsart, wie der Server sie annimmt (er schreibt daraus webtrees' Form CIVIL, RELIGIOUS ...). */
private fun typSchluessel(art: de.bgghome.webtrees.nativ.data.Heiratsart): String = when (art) {
    de.bgghome.webtrees.nativ.data.Heiratsart.Standesamtlich -> "civil"
    de.bgghome.webtrees.nativ.data.Heiratsart.Kirchlich -> "religious"
    de.bgghome.webtrees.nativ.data.Heiratsart.Partnerschaft -> "partners"
    de.bgghome.webtrees.nativ.data.Heiratsart.OhneTrauschein -> "common law"
}
