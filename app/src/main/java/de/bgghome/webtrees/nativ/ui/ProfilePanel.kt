package de.bgghome.webtrees.nativ.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.bgghome.webtrees.nativ.R
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.api.Person
import java.io.File

/** Reiter des Profils, in dieser Reihenfolge: Ereignisse (Timeline.kt), Medien, Familie (Relatives.kt), Karte (LifeMap.kt). */
private val TABS = listOf(R.string.tab_facts, R.string.tab_media, R.string.tab_family, R.string.tab_map)

/** Welcher Dialog gerade offen ist - hoechstens einer zur Zeit. */
private sealed interface ProfileDialog {
    data object DeletePerson : ProfileDialog
    data class Unlink(val family: String, val person: Person) : ProfileDialog
    data object NewFact : ProfileDialog
    /** Familien-XREF waehlen, an der das neue Ereignis haengen soll (bei mehreren Partnerschaften) */
    data object PickFamily : ProfileDialog
    data class NewFamilyFact(val family: String) : ProfileDialog
    /** record: Familien-XREF, wenn das Ereignis an einer Familie haengt, sonst null */
    data class EditFact(val fact: FactJson, val record: String?) : ProfileDialog
    data class DeleteFact(val fact: FactJson, val record: String?) : ProfileDialog
}

/**
 * Profil einer Person im Stil gaengiger Stammbaum-Apps: rundes Foto, Name, "Verwandtschaft | Jahre",
 * Reiter, runder Aktionsknopf. Am Tablet steht es dauerhaft links neben dem Baum, am Handy ist es eine eigene Seite.
 *
 * @param onClose null, wenn das Profil nicht geschlossen werden kann (Handy: dort fuehrt die Zurueck-Taste heraus)
 */
@Composable
fun ProfilePanel(state: UiState, detail: IndividualDetail, viewModel: AppViewModel, openWeb: (String) -> Unit, onClose: (() -> Unit)?) {
    var dialog by remember { mutableStateOf<ProfileDialog?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }

    val canEdit = detail.canEdit
    val canUpload = canEdit && state.tree?.canUpload == true
    val person = detail.person
    val photos = rememberPhotoSources { uri -> viewModel.uploadPhoto(uri, person.name) }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Box {
            Column(Modifier.fillMaxSize()) {
                if (state.loadingDetail || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

                Box(Modifier.fillMaxWidth()) {
                    ProfileHeader(
                        detail, isRoot = state.root == person.xref, canUpload = canUpload,
                        onMakeRoot = { viewModel.setRoot(person.xref) }, onOpenWeb = { openWeb(person.url) }, onPickPhoto = photos.pick,
                    )

                    if (onClose != null) {
                        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    }
                    // Seltenes und Endgueltiges steckt im Drei-Punkte-Menue, nicht im Aktionsknopf
                    if (canEdit) {
                        Box(Modifier.align(Alignment.TopStart)) {
                            IconButton(onClick = { moreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_menu))
                            }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete_person)) },
                                    onClick = { moreMenu = false; dialog = ProfileDialog.DeletePerson },
                                )
                            }
                        }
                    }
                }

                val tab = state.detailTab.coerceIn(0, TABS.lastIndex)

                // Verschiebbar: vier Reiter passen in das schmale Tablet-Panel sonst nur mit Zeilenumbruch.
                ScrollableTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface, edgePadding = 4.dp) {
                    TABS.forEachIndexed { index, title ->
                        val label = stringResource(title).uppercase()
                        val withCount = index == 1 && detail.media.isNotEmpty()
                        Tab(
                            selected = tab == index, onClick = { viewModel.setDetailTab(index) },
                            text = {
                                Text(
                                    if (withCount) stringResource(R.string.tab_with_count, label, detail.media.size) else label,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                        )
                    }
                }

                when (tab) {
                    0 -> Timeline(
                        detail, canEdit,
                        onEdit = { fact, record -> dialog = ProfileDialog.EditFact(fact, record) },
                        onDelete = { fact, record -> dialog = ProfileDialog.DeleteFact(fact, record) },
                        onPerson = viewModel::select,
                    )
                    1 -> MediaGrid(detail.media, onOpen = { item ->
                        when {
                            item.isImage -> viewModel.openMediaViewer(ViewerSource.Profile, detail.media, item, owner = detail.person.name)
                            item.mime == "application/pdf" -> viewModel.openPdf(item.file, item.title, item.url)
                            else -> openWeb(item.url)
                        }
                    })
                    2 -> Relatives(
                        detail, canEdit, onSelect = viewModel::select,
                        onUnlink = { family, who -> dialog = ProfileDialog.Unlink(family, who) },
                    )
                    3 -> LifeMap(mapFacts(detail))
                }
            }

            // Runder Aktionsknopf: Ereignis, Verwandte, Familienereignis, Foto
            if (canEdit) {
                Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                    // Klein gehalten: er liegt ueber der Liste und soll moeglichst wenig verdecken.
                    SmallFloatingActionButton(
                        onClick = { addMenu = true },
                        containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
                    }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_add_event)) },
                            onClick = { addMenu = false; dialog = ProfileDialog.NewFact },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_add_relative)) },
                            onClick = { addMenu = false; viewModel.requestAddRelative(person.xref) },
                        )
                        if (detail.spouseFamilies.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_add_family_event)) },
                                onClick = {
                                    addMenu = false
                                    val only = detail.spouseFamilies.singleOrNull()
                                    dialog = if (only != null) ProfileDialog.NewFamilyFact(only.xref) else ProfileDialog.PickFamily
                                },
                            )
                        }
                        if (canUpload) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_take_photo)) }, onClick = { addMenu = false; photos.take() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_pick_photo)) }, onClick = { addMenu = false; photos.pick() })
                        }
                    }
                }
            }
        }
    }

    ProfileDialogs(dialog, state, detail, viewModel, onDismiss = { dialog = null }, onPickFamily = { dialog = ProfileDialog.NewFamilyFact(it) })
}

/** Kopf des Profils: Portraet (Tipp fuegt ein Foto hinzu, wie beim Vorbild), Name, "Verwandtschaft | Jahre", zwei Knoepfe. */
@Composable
private fun ProfileHeader(
    detail: IndividualDetail,
    isRoot: Boolean,
    canUpload: Boolean,
    onMakeRoot: () -> Unit,
    onOpenWeb: () -> Unit,
    onPickPhoto: () -> Unit,
) {
    val person = detail.person
    val relationship = detail.relationship.replaceFirstChar { it.uppercase() }
    val subtitle = when {
        relationship.isNotEmpty() && person.lifespan.isNotBlank() -> stringResource(R.string.relation_and_years, relationship, person.lifespan)
        relationship.isNotEmpty() -> relationship
        else -> person.lifespan
    }

    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(person, 96.dp, if (canUpload) Modifier.clip(RoundedCornerShape(48.dp)).clickable(onClick = onPickPhoto) else Modifier)
        Text(
            person.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp),
        )
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isRoot) OutlinedButton(onClick = onMakeRoot) { Text(stringResource(R.string.action_make_root)) }
            OutlinedButton(onClick = onOpenWeb) { Text(stringResource(R.string.chip_open_web)) }
        }
    }
}

/** Die zwei Wege zu einem Foto: aus der Galerie waehlen oder mit der Kamera aufnehmen. */
internal class PhotoSources(val pick: () -> Unit, val take: () -> Unit)

@Composable
internal fun rememberPhotoSources(onPhoto: (Uri) -> Unit): PhotoSources {
    val context = LocalContext.current

    // Galerie: der Photo Picker des Systems braucht keine Berechtigung.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhoto(uri)
    }

    // Kamera: die Aufnahme landet in einer eigenen Datei im Cache, die nur die Kamera-App beschreiben darf (FileProvider).
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) onPhoto(uri)
    }

    return remember {
        PhotoSources(
            pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            take = {
                val folder = File(context.cacheDir, "camera").apply { mkdirs() }
                val file = File(folder, "aufnahme-${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                cameraUri = uri
                camera.launch(uri)
            },
        )
    }
}

/** Die Dialoge des Profils - je nach dem, was gerade in `dialog` steht. */
@Composable
private fun ProfileDialogs(
    dialog: ProfileDialog?,
    state: UiState,
    detail: IndividualDetail,
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onPickFamily: (String) -> Unit,
) {
    val person = detail.person
    val places = viewModel.placeSuggestions()

    when (dialog) {
        null -> Unit

        ProfileDialog.DeletePerson -> ConfirmDialog(
            title = stringResource(R.string.delete_person_title, person.name),
            text = stringResource(R.string.delete_person_text),
            confirm = stringResource(R.string.action_delete),
            onDismiss = onDismiss,
            onConfirm = { onDismiss(); viewModel.deletePerson(person.xref) },
        )

        is ProfileDialog.Unlink -> ConfirmDialog(
            title = stringResource(R.string.unlink_title),
            text = stringResource(R.string.unlink_text, dialog.person.name),
            confirm = stringResource(R.string.action_unlink),
            onDismiss = onDismiss,
            onConfirm = { onDismiss(); viewModel.unlink(dialog.family, dialog.person.xref) },
        )

        ProfileDialog.NewFact -> FactDialog(fact = null, tags = state.tags, suggestPlaces = places, onDismiss = onDismiss, onSave = { onDismiss(); viewModel.saveFact(it) })

        is ProfileDialog.NewFamilyFact -> FactDialog(
            fact = null, tags = state.familyTags, suggestPlaces = places, onDismiss = onDismiss,
            onSave = { onDismiss(); viewModel.saveFact(it, record = dialog.family) },
        )

        ProfileDialog.PickFamily -> ChoiceDialog(
            title = stringResource(R.string.family_pick_title),
            options = detail.spouseFamilies.map { it.xref to (it.spouse?.name ?: stringResource(R.string.unknown_person)) },
            onDismiss = onDismiss,
            onChoose = onPickFamily,
        )

        is ProfileDialog.EditFact -> FactDialog(
            fact = dialog.fact, tags = state.tags, suggestPlaces = places, onDismiss = onDismiss,
            onSave = { onDismiss(); viewModel.saveFact(it, dialog.record) },
        )

        is ProfileDialog.DeleteFact -> ConfirmDialog(
            title = stringResource(R.string.fact_delete_title, dialog.fact.label),
            text = listOfNotNull(dialog.fact.value.takeIf { it.isNotEmpty() }, dialog.fact.date?.text, dialog.fact.place?.name).joinToString(" · "),
            confirm = stringResource(R.string.action_delete),
            onDismiss = onDismiss,
            onConfirm = { onDismiss(); viewModel.deleteFact(dialog.fact.id, dialog.record) },
        )
    }
}

/** Ereignisse mit Ort, zeitlich geordnet: die eigenen plus Heirat & Co. aus den Partnerschaften - fuer die Karte. */
private fun mapFacts(detail: IndividualDetail): List<FactJson> =
    (detail.facts + detail.spouseFamilies.flatMap { it.facts })
        .filter { it.place != null }
        .sortedBy { it.date?.jd?.takeIf { jd -> jd > 0 } ?: Int.MAX_VALUE }
