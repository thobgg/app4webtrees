package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
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
) {
    var tag by remember { mutableStateOf(tags.firstOrNull()) }
    var tagMenu by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(fact?.value.orEmpty()) }
    val date = rememberDateInput(gedcom = fact?.date?.gedcom.orEmpty(), displayText = fact?.date?.text.orEmpty())
    var place by remember { mutableStateOf(fact?.place?.name.orEmpty()) }
    var note by remember { mutableStateOf(fact?.notes?.firstOrNull().orEmpty()) }

    val originalNote = fact?.notes?.firstOrNull().orEmpty()
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
                if (fact?.tag == "NAME") {
                    Text(stringResource(Res.string.fact_name_hint), style = MaterialTheme.typography.labelMedium)
                }
                val isNote = fact?.tag == "NOTE" || (fact == null && tag?.tag == "NOTE")
                Field(value, { value = it }, if (isNameOrNote) Res.string.fact_text else Res.string.fact_value_hint, minLines = if (isNote) 3 else 1)
                if (!isNameOrNote) {
                    DateInput(date, Res.string.fact_date)
                    PlaceField(place, { place = it }, Res.string.fact_place, suggestPlaces, hint = Res.string.fact_place_hint)
                    Field(note, { note = it }, Res.string.fact_note, minLines = 2)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = (fact != null || tag != null) && date.result != null,
                onClick = {
                    onSave(
                        if (fact == null) {
                            FactRequest(
                                tag = tag?.tag, value = value.trim(),
                                date = date.result?.ifEmpty { null },
                                place = place.trim().ifEmpty { null },
                                note = note.trim().ifEmpty { null },
                            )
                        } else {
                            // Nur senden, was geaendert wurde - alles andere (Quellen, Koordinaten ...) bleibt auf dem Server unberuehrt.
                            FactRequest(
                                factId = fact.id,
                                value = value.trim().takeIf { it != fact.value },
                                date = if (date.changed) date.result else null,
                                place = place.trim().takeIf { it != fact.place?.name.orEmpty() },
                                note = note.trim().takeIf { it != originalNote },
                            )
                        }
                    )
                },
            ) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
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
fun RelativeDialog(target: RelativeTarget, suggestPlaces: PlaceSuggest?, onDismiss: () -> Unit, onSave: (AddIndividualRequest) -> Unit) {
    val person = target.person
    val ownSurname = person.sortName.substringBefore(',', "").trim()

    var relation by remember { mutableStateOf(target.relations.first()) }
    var family by remember { mutableStateOf(target.families.firstOrNull()?.first) }
    var given by remember { mutableStateOf("") }
    // Naheliegender Nachname: der Vater heisst meist wie das Kind, das Kind meist wie der Vater.
    var surname by remember(relation) {
        mutableStateOf(if (relation == "father" || (relation == "child" && person.sex != "F")) ownSurname else "")
    }
    var sex by remember { mutableStateOf("U") }
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
) {
    if (LocalDeskMode.current && dismissButton != null) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = title, text = text,
            confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { confirmButton(); dismissButton() } },
        )
    } else {
        AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, dismissButton = dismissButton, title = title, text = text)
    }
}
