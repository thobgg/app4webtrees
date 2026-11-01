package de.bgghome.webtrees.nativ.desk

import de.bgghome.webtrees.nativ.api.hat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.debounce
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import de.bgghome.webtrees.nativ.api.ArchiveEntry
import de.bgghome.webtrees.nativ.api.Bereich
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.data.Gesichter
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.ConfirmDialog
import de.bgghome.webtrees.nativ.ui.WtAlertDialog
import de.bgghome.webtrees.nativ.ui.setRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/*
 * Gesichter auf einem Archivbild (Modul Sammlungen ab Stufe 5): das Bild gross, darueber die markierten Rahmen. Beim
 * Darueberfahren steht der Name am Rahmen, rechts die Liste in Lesereihenfolge (Reihe fuer Reihe, von links). Wer darf
 * ([bearbeiten]), zieht neue Rahmen auf, verschiebt den gewaehlten oder zieht ihn an der Ecke groesser und ordnet ihn
 * einer Person des Stammbaums zu, einem Namen ohne Datensatz (der Trauzeuge, den niemand angelegt hat) oder "unbekannt".
 * Gespeichert wird in der Datei (XMP) ueber die Route des Moduls; an die Person haengt sich das Bild dabei nicht.
 */

/** Was im Gesichter-Fenster gerade ist - eigene Klasse, damit Fenster und Inhalt (auch ohne Fenster zeichenbar) sie teilen. */
class GesichterZustand(val eintrag: ArchiveEntry, bearbeiten: Boolean) {
    val bereiche = mutableStateListOf<Bereich>().apply { addAll(eintrag.bereiche) }
    var geaendert by mutableStateOf(false)
    var ueber by mutableStateOf<Int?>(null)
    var gewaehlt by mutableStateOf<Int?>(null)
    var markieren by mutableStateOf(eintrag.bereiche.isEmpty() && bearbeiten)
    var zug by mutableStateOf<Pair<Offset, Offset>?>(null)
    var zuordnen by mutableStateOf<Int?>(null)
    var verwerfen by mutableStateOf(false)
    var speichert by mutableStateOf(false)
    var fehler by mutableStateOf<String?>(null)
    // Bildmasse: aus dem Eintrag, sonst vom geladenen Bild
    var bildB by mutableStateOf(eintrag.breite)
    var bildH by mutableStateOf(eintrag.hoehe)

    /** Personen, an die das Bild beim Speichern zusaetzlich gehaengt wird (Haken im Zuordnen-Dialog, Vorgabe aus) */
    val anhaengen = mutableStateListOf<String>()

    fun entfernen(i: Int) { bereiche.removeAt(i); gewaehlt = null; ueber = null; geaendert = true }
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun GesichterFenster(
    eintrag: ArchiveEntry, tree: String, viewModel: AppViewModel, bearbeiten: Boolean,
    onGespeichert: (ArchiveEntry) -> Unit, onClose: () -> Unit,
) {
    val z = remember(eintrag.pfad) { GesichterZustand(eintrag, bearbeiten) }
    val schliessen = { if (z.geaendert) z.verwerfen = true else onClose() }
    Window(
        onCloseRequest = schliessen,
        title = stringResource(Res.string.desk_faces_window) + " – " + eintrag.bildunterschrift.ifEmpty { eintrag.datei },
        state = arbeitsfenster("fenster_gesichter", 1300, 860),
        onPreviewKeyEvent = { e ->
            when {
                e.type != KeyEventType.KeyDown -> false
                e.key == Key.Escape && z.zuordnen == null -> { schliessen(); true }
                e.key == Key.F1 -> { Hilfe.oeffnen("archiv"); true }
                (e.key == Key.Delete || e.key == Key.Backspace) && bearbeiten && z.gewaehlt != null && z.zuordnen == null -> { z.entfernen(z.gewaehlt!!); true }
                else -> false
            }
        },
    ) {
        DeskTheme {
            GesichterInhalt(z, bearbeiten,
                suche = { q -> viewModel.client.individuals(tree, q, 1).data.filter { !it.isPrivate } },
                speichern = { liste -> viewModel.client.writeRegions(tree, eintrag.pfad.orEmpty(), liste).eintrag },
                onPerson = { viewModel.setRoot(it) }, onGespeichert = onGespeichert, onSchliessen = schliessen,
                // Bild an Personen haengen: nur mit Bearbeitungsrecht und api4webtrees ab Stufe 23 (Media verknuepfen)
                anhaengen = if (viewModel.state.value.tree?.canEdit == true && viewModel.state.value.info.hat(23)) ({ xrefs ->
                    val pfad = eintrag.pfad.orEmpty()
                    val medium = eintrag.xref ?: viewModel.client.mediaFromFile(tree, xrefs.first(), pfad,
                        eintrag.titel.ifBlank { eintrag.bildunterschrift.ifBlank { eintrag.datei.substringBeforeLast('.') } }, type = "photo").media
                        ?: error("MediaFromFile ohne Kennung")
                    xrefs.forEach { viewModel.client.linkMedia(tree, it, medium) }
                }) else null)
            if (z.verwerfen) ConfirmDialog(
                title = stringResource(Res.string.desk_faces_discard_title), text = stringResource(Res.string.desk_faces_discard_text),
                confirm = stringResource(Res.string.desk_faces_discard), onDismiss = { z.verwerfen = false }, onConfirm = { z.verwerfen = false; onClose() },
            )
        }
    }
}

/**
 * Der Inhalt des Gesichter-Fensters ohne Fensterrahmen. [suche] liefert Personen zum getippten Text, [speichern] schreibt
 * die Bereiche und gibt den neuen Eintrag zurueck (null: Antwort ohne Eintrag).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GesichterInhalt(
    z: GesichterZustand, bearbeiten: Boolean,
    suche: suspend (String) -> List<Person>, speichern: suspend (List<Bereich>) -> ArchiveEntry?,
    onPerson: (String) -> Unit, onGespeichert: (ArchiveEntry) -> Unit, onSchliessen: () -> Unit,
    anhaengen: (suspend (List<String>) -> Unit)? = null,
) {
    val eintrag = z.eintrag
    val bereiche = z.bereiche
    val scope = rememberCoroutineScope()
    val unbekannt = stringResource(Res.string.desk_faces_unknown)
    fun beschriftung(b: Bereich) = b.name.ifBlank { unbekannt }
    val farben = MaterialTheme.colorScheme
    Row(Modifier.fillMaxSize().background(farben.background)) {
        // ── Bild mit Rahmen ──
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF202020))) {
            val dichte = LocalDensity.current.density
            val lage = Gesichter.eingepasst(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), z.bildB, z.bildH)
            AsyncImage(
                model = eintrag.vollbild ?: eintrag.original, contentDescription = eintrag.bildunterschrift,
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                onState = { s ->
                    if (s is AsyncImagePainter.State.Success && (z.bildB <= 0 || z.bildH <= 0)) {
                        val g = s.painter.intrinsicSize
                        z.bildB = g.width.toInt(); z.bildH = g.height.toInt()
                    }
                },
            )
            // Die Rahmen; Positionen in Pixeln des Feldes, fuer offset/size in dp umgerechnet
            bereiche.forEachIndexed { i, b ->
                val hervor = i == z.ueber || i == z.gewaehlt
                val links = lage.links + (b.x * lage.breite).toFloat()
                val oben = lage.oben + (b.y * lage.hoehe).toFloat()
                val breite = (b.w * lage.breite).toFloat()
                val hoehe = (b.h * lage.hoehe).toFloat()
                Box(Modifier.offset((links / dichte).dp, (oben / dichte).dp).size((breite / dichte).dp, (hoehe / dichte).dp)
                    .border(if (hervor) 2.5.dp else 1.dp, when {
                        hervor -> Color(0xFFFFD54F)
                        b.xref == null && b.name.isBlank() -> Color.White.copy(alpha = 0.45f)
                        else -> Color.White.copy(alpha = 0.75f)
                    }))
                // Griff zum Groesserziehen am gewaehlten Rahmen
                if (bearbeiten && i == z.gewaehlt) Box(Modifier.offset(((links + breite) / dichte).dp - 5.dp, ((oben + hoehe) / dichte).dp - 5.dp)
                    .size(10.dp).background(Color(0xFFFFD54F)))
                if (hervor) Surface(Modifier.offset((links / dichte).dp, ((oben + hoehe) / dichte).dp + 4.dp), color = Color.Black.copy(alpha = 0.78f),
                    shape = MaterialTheme.shapes.extraSmall) {
                    Text(beschriftung(b) + if (b.xref == null && b.name.isNotBlank()) " · " + stringResource(Res.string.desk_faces_not_in_tree) else "",
                        Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            z.zug?.let { (a, e) ->
                Box(Modifier.offset((minOf(a.x, e.x) / dichte).dp, (minOf(a.y, e.y) / dichte).dp)
                    .size((kotlin.math.abs(e.x - a.x) / dichte).dp, (kotlin.math.abs(e.y - a.y) / dichte).dp)
                    .border(2.dp, Color(0xFFFFD54F)))
            }
            // Maus: Darueberfahren zeigt den Namen, Klick waehlt. Beim Bearbeiten verschiebt ein Zug im gewaehlten Rahmen
            // ihn, ein Zug am Griff aendert die Groesse, sonst zieht ein Zug (im Markiermodus) einen neuen Rahmen.
            Box(Modifier.fillMaxSize()
                .onPointerEvent(PointerEventType.Move) { e ->
                    val p = e.changes.first().position
                    val (nx, ny) = Gesichter.normiert(lage, p.x, p.y)
                    z.ueber = Gesichter.treffer(bereiche, nx, ny)
                }
                .onPointerEvent(PointerEventType.Exit) { z.ueber = null }
                .onPointerEvent(PointerEventType.Press) { e ->
                    val p = e.changes.first().position
                    val (nx, ny) = Gesichter.normiert(lage, p.x, p.y)
                    val g = z.gewaehlt?.let { bereiche.getOrNull(it) }
                    val imGewaehlten = g != null && nx >= g.x && nx <= g.x + g.w && ny >= g.y && ny <= g.y + g.h
                    if (!imGewaehlten && !nahAmGriff(g, lage, p)) z.gewaehlt = Gesichter.treffer(bereiche, nx, ny)
                }
                .then(if (bearbeiten) Modifier.pointerInput(lage) {
                    var art = 0 // 0 neuer Rahmen, 1 verschieben, 2 Groesse
                    var letzter = Offset.Zero
                    detectDragGestures(
                        onDragStart = { p ->
                            val (nx, ny) = Gesichter.normiert(lage, p.x, p.y)
                            val g = z.gewaehlt?.let { bereiche.getOrNull(it) }
                            art = when {
                                nahAmGriff(g, lage, p) -> 2
                                g != null && nx >= g.x && nx <= g.x + g.w && ny >= g.y && ny <= g.y + g.h -> 1
                                z.markieren -> 0
                                else -> -1
                            }
                            letzter = p
                            if (art == 0) z.zug = p to p
                        },
                        onDrag = { c, _ ->
                            val p = c.position
                            val i = z.gewaehlt
                            when (art) {
                                0 -> z.zug = z.zug?.let { it.first to p }
                                1 -> if (i != null) {
                                    bereiche[i] = Gesichter.verschoben(bereiche[i], ((p.x - letzter.x) / lage.breite).toDouble(), ((p.y - letzter.y) / lage.hoehe).toDouble())
                                    z.geaendert = true
                                }
                                2 -> if (i != null) {
                                    val (nx, ny) = Gesichter.normiert(lage, p.x, p.y)
                                    bereiche[i] = Gesichter.vergroessert(bereiche[i], nx, ny); z.geaendert = true
                                }
                            }
                            letzter = p
                        },
                        onDragCancel = { z.zug = null },
                        onDragEnd = {
                            val (a, e) = z.zug ?: return@detectDragGestures
                            z.zug = null
                            val (ax, ay) = Gesichter.normiert(lage, a.x, a.y)
                            val (bx, by) = Gesichter.normiert(lage, e.x, e.y)
                            Gesichter.gezogen(ax, ay, bx, by)?.let { neu ->
                                bereiche.add(neu); z.geaendert = true
                                z.gewaehlt = bereiche.lastIndex; z.zuordnen = bereiche.lastIndex
                            }
                        },
                    )
                } else Modifier))
        }

        // ── Liste und Knoepfe ──
        Column(Modifier.width(320.dp).fillMaxHeight().background(farben.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (bereiche.isEmpty()) stringResource(Res.string.desk_faces_none) else stringResource(Res.string.desk_faces_count, bereiche.size),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (bearbeiten) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(checked = z.markieren, onCheckedChange = { z.markieren = it })
                Text(stringResource(Res.string.desk_faces_mark), style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(if (bearbeiten && z.markieren) Res.string.desk_faces_mark_hint else Res.string.desk_faces_hover_hint),
                style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
            HorizontalDivider(color = farben.outlineVariant)
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(Gesichter.reihenfolge(bereiche)) { i ->
                    val b = bereiche[i]
                    Row(Modifier.fillMaxWidth()
                        .background(if (i == z.gewaehlt || i == z.ueber) farben.secondaryContainer else Color.Transparent)
                        .onPointerEvent(PointerEventType.Enter) { z.ueber = i }
                        .onPointerEvent(PointerEventType.Exit) { if (z.ueber == i) z.ueber = null }
                        .combinedClickable(onClick = { z.gewaehlt = i; b.xref?.let(onPerson) }, onDoubleClick = { if (bearbeiten) z.zuordnen = i })
                        .padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(beschriftung(b), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontStyle = if (b.name.isBlank()) FontStyle.Italic else FontStyle.Normal,
                                color = if (b.xref != null) farben.primary else farben.onSurface)
                            if (b.xref == null && b.name.isNotBlank()) Text(stringResource(Res.string.desk_faces_not_in_tree), style = MaterialTheme.typography.labelSmall, color = farben.onSurfaceVariant)
                        }
                        b.xref?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = farben.onSurfaceVariant) }
                    }
                }
            }
            if (bearbeiten && z.gewaehlt != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { z.zuordnen = z.gewaehlt }, shape = MaterialTheme.shapes.small) { Text(stringResource(Res.string.desk_faces_assign)) }
                OutlinedButton(onClick = { z.gewaehlt?.let(z::entfernen) }, shape = MaterialTheme.shapes.small) { Text(stringResource(Res.string.desk_faces_delete)) }
            }
            if (bearbeiten) Text(stringResource(Res.string.desk_faces_note), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
            z.fehler?.let { Text(it, color = farben.error, style = MaterialTheme.typography.bodySmall) }
            HorizontalDivider(color = farben.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSchliessen) { Text(stringResource(Res.string.action_close)) }
                if (bearbeiten) OutlinedButton(enabled = z.geaendert && !z.speichert, shape = MaterialTheme.shapes.small, onClick = {
                    z.speichert = true; z.fehler = null
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val neu = speichern(bereiche.toList())
                                // nur Personen, deren Rahmen noch da ist
                                val an = z.anhaengen.filter { x -> bereiche.any { it.xref == x } }.distinct()
                                if (an.isNotEmpty()) anhaengen?.invoke(an)
                                z.anhaengen.clear()
                                neu
                            }
                        }
                            .onSuccess { neu -> z.geaendert = false; onGespeichert(neu ?: eintrag.copy(bereiche = bereiche.toList())) }
                            .onFailure { z.fehler = it.message ?: "?" }
                        z.speichert = false
                    }
                }) { Text(stringResource(Res.string.action_save)) }
            }
        }
    }

    z.zuordnen?.let { i ->
        bereiche.getOrNull(i)?.let { b ->
            // Ein frisch gezogener Rahmen ohne Zuordnung bleibt als "unbekannt" stehen - entfernen geht jederzeit
            ZuordnenDialog(b, suche, anhaengenMoeglich = anhaengen != null && eintrag.pfad != null, anhaengenAn = b.xref in z.anhaengen,
                onDismiss = { z.zuordnen = null },
                onWahl = { name, xref, haengen ->
                    bereiche[i] = b.copy(name = name, xref = xref); z.geaendert = true; z.zuordnen = null
                    b.xref?.let { z.anhaengen.remove(it) }
                    if (haengen && xref != null) z.anhaengen.add(xref)
                })
        } ?: run { z.zuordnen = null }
    }
}

/** Ob der Punkt [p] (Feld-Pixel) am Griff rechts unten des Rahmens [b] liegt. */
private fun nahAmGriff(b: Bereich?, lage: Gesichter.Lage, p: Offset): Boolean {
    if (b == null) return false
    val gx = lage.links + ((b.x + b.w) * lage.breite).toFloat()
    val gy = lage.oben + ((b.y + b.h) * lage.hoehe).toFloat()
    return kotlin.math.abs(p.x - gx) <= 12f && kotlin.math.abs(p.y - gy) <= 12f
}

/**
 * Einem Rahmen eine Person zuordnen: Personen des Stammbaums zum getippten Text (Name aus webtrees, mit Lebensdaten), den
 * getippten Namen ohne Datensatz uebernehmen oder "unbekannt".
 */
@Composable
private fun ZuordnenDialog(
    b: Bereich, suche: suspend (String) -> List<Person>, anhaengenMoeglich: Boolean, anhaengenAn: Boolean,
    onDismiss: () -> Unit, onWahl: (String, String?, Boolean) -> Unit,
) {
    var haengen by remember { mutableStateOf(anhaengenAn) }
    var text by remember { mutableStateOf(b.name) }
    var treffer by remember { mutableStateOf<List<Person>>(emptyList()) }
    LaunchedEffect(text) {
        val q = text.trim()
        if (q.length < 2) { treffer = emptyList(); return@LaunchedEffect }
        delay(250)
        treffer = withContext(Dispatchers.IO) { runCatching { suche(q) }.getOrDefault(emptyList()) }
    }
    WtAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.desk_faces_assign).removeSuffix(" …")) },
        text = {
            Column(Modifier.width(460.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text(stringResource(Res.string.desk_faces_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (anhaengenMoeglich) Tipp(stringResource(Res.string.tipp_faces_attach)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = haengen, onCheckedChange = { haengen = it })
                        Text(stringResource(Res.string.desk_faces_attach), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Box(Modifier.fillMaxWidth().height(260.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(treffer, key = { it.xref }) { p ->
                            Row(Modifier.fillMaxWidth().clickable { onWahl(p.name.replace("/", "").trim(), p.xref, haengen) }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOf(p.lifespan, p.xref).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (text.isNotBlank()) TextButton(onClick = { onWahl(text.trim(), null, false) }) { Text(stringResource(Res.string.desk_faces_use_name, text.trim())) }
                TextButton(onClick = { onWahl("", null, false) }) { Text(stringResource(Res.string.desk_faces_set_unknown)) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
