package de.bgghome.webtrees.nativ.ui

import org.jetbrains.compose.resources.StringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.api.FamilyJson
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.api.Person

/**
 * Reiter "Familie" im Profil: Eltern und Geschwister, dann je Partnerschaft Partner und Kinder.
 * Bearbeiter koennen jede Verknuepfung loesen; die Person bleibt dabei im Baum.
 *
 * @param onUnlink Familien-XREF und die Person, die aus dieser Familie geloest werden soll
 */
@Composable
fun Relatives(detail: IndividualDetail, canEdit: Boolean, onSelect: (String) -> Unit, onUnlink: (String, Person) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        detail.parentFamilies.forEach { family ->
            item { SectionTitle(stringResource(Res.string.family_parents_siblings)) }
            item { FamilyMembers(family, self = detail.person, asChild = true, canEdit = canEdit, onSelect = onSelect, onUnlink = onUnlink) }
        }
        detail.spouseFamilies.forEach { family ->
            item {
                val title = family.marriage?.date?.text?.let { stringResource(Res.string.family_partnership_married, it) }
                SectionTitle(title ?: stringResource(Res.string.family_partnership))
            }
            item { FamilyMembers(family, self = detail.person, asChild = false, canEdit = canEdit, onSelect = onSelect, onUnlink = onUnlink) }
        }
        if (detail.parentFamilies.isEmpty() && detail.spouseFamilies.isEmpty()) {
            item { Text(stringResource(Res.string.family_none), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(), Modifier.padding(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Die Mitglieder einer Familie aus Sicht von self: als Kind (Vater, Mutter, Geschwister) oder als Partner (Partner, Kinder).
 */
@Composable
private fun FamilyMembers(
    family: FamilyJson,
    self: Person,
    asChild: Boolean,
    canEdit: Boolean,
    onSelect: (String) -> Unit,
    onUnlink: (String, Person) -> Unit,
) {
    @Composable
    fun row(person: Person, label: String) {
        PersonRow(
            person, label = label, onClick = { onSelect(person.xref) },
            trailing = if (!canEdit || person.isPrivate) null else {
                {
                    IconButton(onClick = { onUnlink(family.xref, person) }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.action_unlink), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
        )
    }

    @Composable
    fun sexLabel(person: Person, male: StringResource, female: StringResource, unknown: StringResource) =
        stringResource(when (person.sex) { "M" -> male; "F" -> female; else -> unknown })

    Column {
        if (asChild) {
            family.husband?.let { row(it, stringResource(Res.string.rel_father)) }
            family.wife?.let { row(it, stringResource(Res.string.rel_mother)) }
            family.children.filter { it.xref != self.xref }.forEach { sibling ->
                row(sibling, sexLabel(sibling, Res.string.rel_brother, Res.string.rel_sister, Res.string.rel_sibling))
            }
            if (canEdit) {
                TextButton(onClick = { onUnlink(family.xref, self) }, modifier = Modifier.padding(start = 8.dp)) {
                    Text(stringResource(Res.string.unlink_self_from_parents))
                }
            }
        } else {
            family.spouse?.let { row(it, sexLabel(it, Res.string.rel_partner_m, Res.string.rel_partner_f, Res.string.rel_partner)) }
            family.children.forEach { child ->
                row(child, sexLabel(child, Res.string.rel_son, Res.string.rel_daughter, Res.string.rel_child))
            }
        }
    }
}
