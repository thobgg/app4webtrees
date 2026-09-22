package de.bgghome.webtrees.nativ.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.bgghome.webtrees.nativ.R
import de.bgghome.webtrees.nativ.api.ArchiveEntry
import de.bgghome.webtrees.nativ.api.ExifRequest

/**
 * Vollbild-Betrachter: wischen zum naechsten Bild, kneifen zum Vergroessern (bis 5x), ein Finger schiebt das vergroesserte
 * Bild, Doppeltipp springt an die Stelle heran und wieder zurueck, ein Tipp blendet Kopf- und Fusszeile aus. Gewischt
 * wird nur, solange nicht vergroessert ist - sonst wuerde jedes Schieben das Bild wechseln.
 *
 * Kurz vor dem Ende meldet onIndex die Stelle; das View-Model laedt dann die naechste Seite nach, und die Liste waechst.
 */
@Composable
fun PhotoViewer(
    viewer: ViewerState,
    onIndex: (Int) -> Unit,
    onClose: () -> Unit,
    onOpenWeb: (String) -> Unit,
    /** Beschriftung aendern - nur fuer Archivbilder und nur, wenn der Server es diesem Nutzer erlaubt */
    canEditExif: Boolean = false,
    suggestPersons: PlaceSuggest? = null,
    onSaveExif: (ArchiveEntry, ExifRequest) -> Unit = { _, _ -> },
) {
    val pagerState = rememberPagerState(initialPage = viewer.index) { viewer.items.size }
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ArchiveEntry?>(null) }

    LaunchedEffect(pagerState.currentPage) { onIndex(pagerState.currentPage) }

    val item = viewer.items.getOrNull(pagerState.currentPage)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, userScrollEnabled = !zoomed, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { page ->
            ZoomableImage(viewer.items[page], onTap = { chrome = !chrome }, onZoom = { if (page == pagerState.currentPage) zoomed = it })
        }

        AnimatedVisibility(chrome, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).statusBarsPadding().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White) }
                Text(
                    stringResource(R.string.viewer_position, pagerState.currentPage + 1, viewer.items.size),
                    Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleMedium,
                )
                item?.entry?.takeIf { canEditExif && it.istBild && it.pfad != null }?.let { entry ->
                    IconButton(onClick = { editing = entry }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.viewer_edit_exif), tint = Color.White)
                    }
                }
                item?.webUrl?.let { url ->
                    IconButton(onClick = { onOpenWeb(url) }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = stringResource(R.string.viewer_open_web), tint = Color.White)
                    }
                }
            }
        }

        editing?.let { entry ->
            ExifEditDialog(entry, suggestPersons, onDismiss = { editing = null }, onSave = { editing = null; onSaveExif(entry, it) })
        }

        AnimatedVisibility(chrome && item != null, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding()) {
                Text(item?.caption.orEmpty(), color = Color.White, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val link = when (item?.link) {
                    LinkState.ObjectOnly -> stringResource(R.string.archive_link_object)
                    LinkState.FileOnly -> stringResource(R.string.archive_link_file)
                    else -> null
                }
                val subtitle = listOfNotNull(item?.subtitle?.takeIf { it.isNotBlank() }, link).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/** Ein Bild mit Kneifzoom, Verschieben und Doppeltipp. Zoom und Lage gehoeren zum Bild - beim Wechsel beginnt das naechste bei 1x. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomableImage(item: ViewerItem, onTap: () -> Unit, onZoom: (Boolean) -> Unit) {
    var scale by remember(item) { mutableFloatStateOf(1f) }
    var offset by remember(item) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    // Das vergroesserte Bild darf nicht aus dem Rahmen rutschen: so weit, dass sein Rand hoechstens den Rahmen erreicht.
    fun clamp(o: Offset, s: Float): Offset {
        val maxX = (size.width * (s - 1f)) / 2f
        val maxY = (size.height * (s - 1f)) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    fun apply(newScale: Float, newOffset: Offset) {
        scale = newScale.coerceIn(1f, MAX_ZOOM)
        offset = if (scale > 1f) clamp(newOffset, scale) else Offset.Zero
        onZoom(scale > 1f)
    }

    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        apply(scale * zoomChange, offset + panChange)
    }

    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(item) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            apply(1f, Offset.Zero)
                        } else {
                            // Die angetippte Stelle bleibt unter dem Finger: Verschiebung = Abstand zur Mitte * (1 - Zoom).
                            val centre = Offset(size.width / 2f, size.height / 2f)
                            apply(DOUBLE_TAP_ZOOM, (tap - centre) * (1f - DOUBLE_TAP_ZOOM))
                        }
                    },
                )
            }
            // Ein Finger schiebt nur das vergroesserte Bild; bei 1x geht die Bewegung an den Pager (wischen).
            .transformable(transform, canPan = { scale > 1f }),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = item.image, contentDescription = item.caption, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            },
        )
    }
}
