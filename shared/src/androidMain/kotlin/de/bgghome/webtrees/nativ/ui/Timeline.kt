package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.api.DateJson
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.api.Person

/** Eine Zeile der Zeitleiste: eigenes Ereignis, Familienereignis (Heirat) oder Geburt eines Kindes. */
private data class TimelineRow(
    val sortKey: Int,
    val year: Int?,
    val label: String,
    val fact: FactJson,
    val editable: Boolean,
    /** Gesetzt bei "Geburt des Sohnes ...": ein Tipp fuehrt zum Kind. */
    val child: Person? = null,
    /** Familien-XREF, wenn das Ereignis an der Familie haengt (Heirat ...); null = an der Person */
    val record: String? = null,
    /** Beim Tod: das erreichte Alter, unter der Jahreszahl */
    val age: String? = null,
)

/**
 * Reiter "Ereignisse" im Profil: der Lebenslauf als Zeitleiste mit grosser Jahreszahl links - wie beim Vorbild.
 * Eingereiht werden auch Heirat & Co. aus den Partnerschaften und die Geburten der Kinder.
 *
 * @param onEdit / onDelete bekommen neben dem Ereignis die Familien-XREF, wenn es an einer Familie haengt
 */
@Composable
fun Timeline(
    detail: IndividualDetail,
    canEdit: Boolean,
    onEdit: (FactJson, String?) -> Unit,
    onDelete: (FactJson, String?) -> Unit,
    onPerson: (String) -> Unit,
) {
    val rows = buildTimeline(detail, canEdit)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        // Kein key: Fakten-IDs sind Inhalts-Hashes, zwei gleichlautende Ereignisse haetten denselben.
        items(rows) { row ->
            TimelineItem(row, onEdit, onDelete, onPerson)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun buildTimeline(detail: IndividualDetail, canEdit: Boolean): List<TimelineRow> = buildList {
    // Eigene Ereignisse in der Reihenfolge von webtrees; undatierte erben den Platz des Vorgaengers.
    var last = Int.MIN_VALUE + 1

    val birth = detail.facts.firstOrNull { it.tag == "BIRT" }?.date

    // Geschlecht steckt in der Farbe des Portraets; unbekannte Hersteller-Tags (_INET ...) sagen dem Leser nichts.
    detail.facts.filter { it.tag != "SEX" && it.known }.forEach { fact ->
        val key = if (fact.tag == "NAME") Int.MIN_VALUE else fact.date?.jd?.takeIf { it > 0 } ?: last
        if (fact.tag != "NAME") last = key

        val label = fact.label + if (fact.type.isNotEmpty()) " · ${fact.type}" else ""
        val age = if (fact.tag == "DEAT") ageAt(birth, fact.date) else null
        // Der Sperrvermerk (RESN) haengt an Rechten - vorerst nur in webtrees aendern.
        add(TimelineRow(key, fact.date?.year?.takeIf { it != 0 }, label, fact, editable = canEdit && fact.tag != "RESN", age = age))
    }

    detail.spouseFamilies.forEach { family ->
        // Heirat, Scheidung ... stehen in webtrees bei der Familie; geschrieben wird dann an die Familie, nicht an die Person.
        family.facts.forEach { fact ->
            val label = family.spouse?.name?.let { stringResource(Res.string.fact_with_spouse, fact.label, it) } ?: fact.label
            add(
                TimelineRow(
                    fact.date?.jd?.takeIf { it > 0 } ?: Int.MAX_VALUE, fact.date?.year?.takeIf { it != 0 }, label, fact,
                    editable = canEdit && fact.known, record = family.xref,
                )
            )
        }
        // Geburten der Kinder gehoeren in die Lebenslinie - als Kunst-Ereignis, das sich nicht bearbeiten laesst.
        family.children.forEach { child ->
            val date = child.birth?.date ?: return@forEach
            val label = stringResource(
                when (child.sex) {
                    "M" -> Res.string.timeline_birth_son
                    "F" -> Res.string.timeline_birth_daughter
                    else -> Res.string.timeline_birth_child
                },
                child.name,
            )
            val fact = FactJson(id = "child-" + child.xref, date = date, place = child.birth.place)
            add(TimelineRow(date.jd, date.year.takeIf { it != 0 }, label, fact, editable = false, child = child))
        }
        // Und ihre Heiraten (ab API-Stufe 10) - undatierte ans Ende, wie bei den Familienereignissen
        family.children.forEach { child ->
            child.marriages.forEach { marriage ->
                val who = stringResource(
                    when (child.sex) {
                        "M" -> Res.string.timeline_marriage_son
                        "F" -> Res.string.timeline_marriage_daughter
                        else -> Res.string.timeline_marriage_child
                    },
                    child.name,
                )
                val label = if (marriage.spouse.isNotEmpty()) stringResource(Res.string.fact_with_spouse, who, marriage.spouse) else who
                val fact = FactJson(id = "marriage-" + marriage.family, date = marriage.date, place = marriage.place)
                add(TimelineRow(marriage.date?.jd?.takeIf { it > 0 } ?: Int.MAX_VALUE, marriage.date?.year?.takeIf { it != 0 }, label, fact, editable = false, child = child))
            }
        }
    }
}.sortedBy { it.sortKey }

/**
 * Das Alter beim Tod in ganzen Jahren, aus den Julianischen Tagen. Fehlt bei einem der Daten der Tag, ist es eine
 * Schaetzung ("etwa"); ohne beide Daten null.
 */
@Composable
private fun ageAt(birth: DateJson?, death: DateJson?): String? {
    if (birth == null || death == null || birth.jd <= 0 || death.jd <= 0 || death.jd < birth.jd) return null
    val years = ((death.jd - birth.jd) / 365.2425).toInt()
    val exact = isDayPrecise(birth) && isDayPrecise(death)
    return stringResource(if (exact) Res.string.timeline_age else Res.string.timeline_age_approx, years)
}

/** "14 MAR 1985" ist tagesgenau, "1985" oder "ABT 1985" nicht; ohne GEDCOM-Form (aeltere Module) gilt: nicht genau. */
private val DAY_PRECISE = Regex("^\\d{1,2} [A-Z]{3} \\d{1,4}$")

private fun isDayPrecise(date: DateJson): Boolean = DAY_PRECISE.matches(date.gedcom.trim())

@Composable
private fun TimelineItem(row: TimelineRow, onEdit: (FactJson, String?) -> Unit, onDelete: (FactJson, String?) -> Unit, onPerson: (String) -> Unit) {
    val fact = row.fact
    val child = row.child

    Row(
        Modifier.fillMaxWidth()
            .then(if (child != null && !child.isPrivate) Modifier.clickable { onPerson(child.xref) } else Modifier)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Grosse Jahreszahl links, beim Tod das Alter darunter
        Column(Modifier.width(64.dp)) {
            Text(
                row.year?.toString().orEmpty(),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.primary,
            )
            row.age?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Column(Modifier.weight(1f)) {
            Text(row.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (fact.value.isNotEmpty()) Text(fact.value, style = MaterialTheme.typography.bodyMedium)

            val sub = listOfNotNull(fact.date?.text, fact.place?.name).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            fact.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (fact.sources.isNotEmpty()) {
                Text(
                    stringResource(Res.string.fact_sources, fact.sources.joinToString("; ") { it.title }),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (row.editable) {
            IconButton(onClick = { onEdit(fact, row.record) }) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.action_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Den Namen kann man aendern, aber nicht loeschen - ohne Namen gibt es die Person in webtrees nicht.
            if (fact.tag != "NAME") {
                IconButton(onClick = { onDelete(fact, row.record) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.action_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
