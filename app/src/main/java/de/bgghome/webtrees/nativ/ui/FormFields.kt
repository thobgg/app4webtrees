package de.bgghome.webtrees.nativ.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import de.bgghome.webtrees.nativ.R
import de.bgghome.webtrees.nativ.data.DateForm
import de.bgghome.webtrees.nativ.data.DatePart
import de.bgghome.webtrees.nativ.data.DateQualifier
import de.bgghome.webtrees.nativ.data.GedcomDate
import kotlinx.coroutines.delay

// Die Eingabefelder der Formulare: Text, Auswahl-Chips, Ort mit Vorschlaegen, Datum zum Auswaehlen.

/** Liefert zu einem angefangenen Ortsnamen passende Orte des Baums (AppViewModel.placeSuggestions). */
typealias PlaceSuggest = suspend (String) -> List<String>

/** So lange wird nach dem letzten Tastendruck gewartet, bevor nach Orten gefragt wird - sonst eine Anfrage je Buchstabe. */
private const val PLACE_DEBOUNCE_MS = 300L

/** Textfeld ueber die volle Breite - die Formulare hier bestehen fast nur daraus. */
@Composable
internal fun Field(value: String, onChange: (String) -> Unit, @StringRes label: Int, @StringRes hint: Int? = null, minLines: Int = 1) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(stringResource(label)) },
        supportingText = hint?.let { { Text(stringResource(it)) } },
        singleLine = minLines == 1, minLines = minLines, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    // Mehrzeilig waere schoener (FlowRow), ist in dieser Compose-Version aber noch experimentell.
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (key, label) ->
                    FilterChip(selected = selected == key, onClick = { onSelect(key) }, label = { Text(label) })
                }
            }
        }
    }
}

// ─── Ort ──────────────────────────────────────────────────────────────

/**
 * Ortsfeld mit Vorschlagsliste aus den Orten des Baums - so bleiben Schreibweisen einheitlich
 * ("Hannover, Niedersachsen, Deutschland" statt dreier Varianten). Ohne suggest ein einfaches Textfeld.
 */
@Composable
internal fun PlaceField(value: String, onChange: (String) -> Unit, @StringRes label: Int, suggest: PlaceSuggest?, @StringRes hint: Int? = null) {
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    // Was vorbelegt war oder gerade aus der Liste kam, muss nicht noch einmal nachgeschlagen werden.
    var settled by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        val query = value.trim()

        if (suggest == null || value == settled || query.length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        // Tippt man weiter, bricht Compose diesen Block hier ab und startet ihn mit dem neuen Wert.
        delay(PLACE_DEBOUNCE_MS)
        suggestions = suggest(query).filter { it != query }
    }

    Box {
        Field(value, onChange, label, hint)
        // Nicht fokussierbar, damit die Tastatur offen bleibt und man einfach weitertippen kann.
        DropdownMenu(
            expanded = suggestions.isNotEmpty(),
            onDismissRequest = { suggestions = emptyList() },
            properties = PopupProperties(focusable = false),
        ) {
            suggestions.forEach { place ->
                DropdownMenuItem(
                    text = { Text(place) },
                    onClick = { settled = place; onChange(place); suggestions = emptyList() },
                )
            }
        }
    }
}

// ─── Datum ────────────────────────────────────────────────────────────

/**
 * Zustand eines Datumsfelds. Normal wird ausgewaehlt (Genauigkeit, Tag, Monat, Jahr); Daten, die sich so nicht
 * abbilden lassen (Kalender, FROM/TO, INT ...), und alle Daten von Servern vor API-Stufe 8 werden als Text bearbeitet.
 *
 * gedcom:      das gespeicherte Datum im GEDCOM-Format (DateJson.gedcom), falls der Server es liefert
 * displayText: sonst die Anzeige des Servers ("12. März 1890") - wird nur als Text angeboten, nie zurueckgerechnet
 */
@Stable
class DateInputState(gedcom: String, displayText: String) {

    private val initialForm: DateForm? = when {
        gedcom.isNotEmpty() -> DateForm.fromGedcom(gedcom)
        displayText.isEmpty() -> DateForm()
        else -> null
    }

    var form by mutableStateOf(initialForm ?: DateForm())
    var textMode by mutableStateOf(initialForm == null)
        private set
    var text by mutableStateOf(if (initialForm != null) "" else gedcom.ifEmpty { displayText })

    /** Das Datum fuer den Server: GEDCOM, "" = kein Datum, null = so nicht speicherbar (Formular unvollstaendig). */
    val result: String?
        get() = if (textMode) GedcomDate.fromInput(text) else form.toGedcom()

    private val initialResult = result

    /** Beim Aendern wird das Datum nur gesendet, wenn es sich wirklich geaendert hat. */
    val changed: Boolean get() = result != initialResult

    /** Ob sich der eingetippte Text ins Formular uebernehmen laesst. */
    val canPick: Boolean get() = DateForm.fromGedcom(GedcomDate.fromInput(text)) != null

    fun pick() {
        DateForm.fromGedcom(GedcomDate.fromInput(text))?.let { form = it; textMode = false }
    }

    fun type() {
        // Ein halb ausgefuelltes Formular hat noch kein GEDCOM - dann bleibt der vorige Text stehen.
        form.toGedcom()?.let { text = it }
        textMode = true
    }
}

@Composable
internal fun rememberDateInput(gedcom: String = "", displayText: String = ""): DateInputState =
    remember { DateInputState(gedcom, displayText) }

/** Datumsfeld wie in gaengigen Stammbaum-Apps: Genauigkeit oben rechts, darunter Tag · Monat · Jahr, bei "zwischen" zweimal. */
@Composable
internal fun DateInput(state: DateInputState, @StringRes label: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (!state.textMode) QualifierMenu(state.form.qualifier) { state.form = state.form.copy(qualifier = it) }
        }

        if (state.textMode) {
            Field(state.text, { state.text = it }, R.string.fact_date, hint = R.string.date_hint)
        } else {
            DatePartRow(state.form.first) { state.form = state.form.copy(first = it) }
            if (state.form.qualifier == DateQualifier.BETWEEN) {
                Text(stringResource(R.string.date_and), style = MaterialTheme.typography.labelMedium)
                DatePartRow(state.form.second) { state.form = state.form.copy(second = it) }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { if (!state.textMode) DateSummary(state.form) }
            if (!state.textMode) {
                TextButton(onClick = state::type) { Text(stringResource(R.string.date_as_text)) }
            } else if (state.canPick) {
                TextButton(onClick = state::pick) { Text(stringResource(R.string.date_pick)) }
            }
        }
    }
}

@Composable
private fun QualifierMenu(selected: DateQualifier, onSelect: (DateQualifier) -> Unit) {
    var open by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { open = true }) {
            Text(qualifierLabel(selected))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DateQualifier.entries.forEach { q ->
                DropdownMenuItem(text = { Text(qualifierLabel(q)) }, onClick = { onSelect(q); open = false })
            }
        }
    }
}

@Composable
private fun qualifierLabel(q: DateQualifier): String = stringResource(
    when (q) {
        DateQualifier.EXACT -> R.string.date_exact
        DateQualifier.ABOUT -> R.string.date_about
        DateQualifier.BEFORE -> R.string.date_before
        DateQualifier.AFTER -> R.string.date_after
        DateQualifier.BETWEEN -> R.string.date_between
    }
)

/** Tag · Monat · Jahr. Tag und Jahr nur Ziffern; der Monat kommt aus einer Liste, damit es keine Tippfehler gibt. */
@Composable
private fun DatePartRow(part: DatePart, onChange: (DatePart) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        DigitsField(part.day, maxLength = 2, R.string.date_day, Modifier.weight(0.8f)) { onChange(part.copy(day = it)) }
        MonthField(part.month, Modifier.weight(1.5f)) { onChange(part.copy(month = it)) }
        DigitsField(part.year, maxLength = 4, R.string.date_year, Modifier.weight(1f)) { onChange(part.copy(year = it)) }
    }
}

@Composable
private fun DigitsField(value: String, maxLength: Int, @StringRes label: Int, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter(Char::isDigit).take(maxLength)) },
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun MonthField(month: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    val names = stringArrayResource(R.array.date_months)
    var open by remember { mutableStateOf(false) }

    Box(modifier) {
        OutlinedTextField(
            value = if (month in 1..12) names[month - 1] else "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.date_month)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Ein nur lesbares Textfeld nimmt keine Tipps an - eine durchsichtige Flaeche darueber oeffnet die Liste.
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.date_month_unknown)) }, onClick = { onChange(0); open = false })
            names.forEachIndexed { index, name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onChange(index + 1); open = false })
            }
        }
    }
}

/** Das Datum in Worten ("um März 1850"), damit man sieht, was gespeichert wird - oder was noch fehlt. */
@Composable
private fun DateSummary(form: DateForm) {
    val gedcom = form.toGedcom()

    when {
        gedcom == null -> Text(
            stringResource(R.string.date_incomplete),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
        )
        gedcom.isNotEmpty() -> {
            val first = describe(form.first)
            val text = when (form.qualifier) {
                DateQualifier.EXACT -> first
                DateQualifier.ABOUT -> stringResource(R.string.date_text_about, first)
                DateQualifier.BEFORE -> stringResource(R.string.date_text_before, first)
                DateQualifier.AFTER -> stringResource(R.string.date_text_after, first)
                DateQualifier.BETWEEN -> stringResource(R.string.date_text_between, first, describe(form.second))
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun describe(part: DatePart): String {
    val month = if (part.month in 1..12) stringArrayResource(R.array.date_months)[part.month - 1] else ""
    val year = part.year.trim()

    return when {
        month.isEmpty() -> year
        part.day.isBlank() -> stringResource(R.string.date_text_month, month, year)
        else -> stringResource(R.string.date_text_day, part.day.trim().toInt(), month, year)
    }
}
