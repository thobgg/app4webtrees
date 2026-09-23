package de.bgghome.webtrees.nativ.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.WtApp
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Schlanker PDF-Betrachter fuer Urkunden, Kirchenbuchseiten und Briefe im Archiv: die Datei kommt mit dem
 * Sitzungs-Cookie der App in den Cache, Android selbst (PdfRenderer) zeichnet die Seiten, untereinander in
 * Bildschirmbreite. Keine Bibliothek, kein Browser-Fenster, kein Download in fremde Apps.
 *
 * PdfRenderer ist nicht nebenlaeufig: alle Seiten zeichnet ein Schloss nacheinander. Eine Seite wird erst gezeichnet,
 * wenn sie in Sicht kommt, und verworfen, wenn sie hinausrollt - so bleibt auch ein Kirchenbuch mit 300 Seiten im
 * Speicher.
 */
@Composable
actual fun PdfViewer(target: PdfTarget, onClose: () -> Unit, onOpenWeb: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as WtApp
    var document by remember { mutableStateOf<PdfDocument?>(null) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(target.url) {
        document = withContext(Dispatchers.IO) {
            runCatching { PdfDocument.open(app, target.url, context.cacheDir) }
                .onFailure { Log.w("wtAnd", "PDF liess sich nicht oeffnen: ${target.url}", it) }
                .getOrNull()
        }
        error = document == null
    }
    // Das Dokument schliessen, das dieser Durchlauf kennt - nicht das, das beim Aufraeumen gerade im Zustand steht
    // (sonst schliesst der Wechsel von "laedt" zu "geladen" das frisch geoeffnete).
    DisposableEffect(document) {
        val geoeffnet = document
        onDispose { geoeffnet?.close() }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_close)) }
            Column(Modifier.weight(1f)) {
                Text(target.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                document?.let {
                    Text(stringResource(Res.string.pdf_pages, it.pageCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            target.webUrl?.let { url ->
                IconButton(onClick = { onOpenWeb(url) }) { Icon(Icons.Default.ExitToApp, contentDescription = stringResource(Res.string.viewer_open_web)) }
            }
        }

        val doc = document
        when {
            error -> Text(stringResource(Res.string.pdf_error), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            doc == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                // Seiten in Bildschirmbreite zeichnen - in Pixeln, damit Schrift auf dem Handy scharf bleibt.
                val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }

                // Gezoomt wird je Seite; solange eine Seite vergroessert ist, schiebt der Finger die Seite, nicht die Liste.
                var zoomedPage by remember { mutableStateOf(-1) }

                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), userScrollEnabled = zoomedPage < 0) {
                    items((0 until doc.pageCount).toList()) { index ->
                        PdfPage(doc, index, widthPx, onZoom = { zoomedPage = if (it) index else if (zoomedPage == index) -1 else zoomedPage })
                    }
                }
            }
        }
    }
}

private const val PDF_MAX_ZOOM = 4f
private const val PDF_DOUBLE_TAP_ZOOM = 2.5f

/** Eine Seite: Kneifzoom bis 4x, ein Finger schiebt die vergroesserte Seite, Doppeltipp heran und zurueck - wie im Bildbetrachter. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PdfPage(doc: PdfDocument, index: Int, widthPx: Int, onZoom: (Boolean) -> Unit) {
    val ratio = doc.ratio(index)
    val bitmap by produceState<Bitmap?>(null, doc, index, widthPx) {
        value = withContext(Dispatchers.IO) {
            runCatching { doc.render(index, widthPx) }.onFailure { Log.w("wtAnd", "PDF-Seite $index", it) }.getOrNull()
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = (size.width * (s - 1f)) / 2f
        val maxY = (size.height * (s - 1f)) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    fun apply(newScale: Float, newOffset: Offset) {
        scale = newScale.coerceIn(1f, PDF_MAX_ZOOM)
        offset = if (scale > 1f) clamp(newOffset, scale) else Offset.Zero
        onZoom(scale > 1f)
    }

    val transform = rememberTransformableState { zoomChange, panChange, _ -> apply(scale * zoomChange, offset + panChange) }

    Box(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).aspectRatio(ratio).clipToBounds().background(Color.White)
            .onSizeChanged { size = it }
            .pointerInput(index) {
                detectTapGestures(onDoubleTap = { tap ->
                    if (scale > 1f) apply(1f, Offset.Zero)
                    else apply(PDF_DOUBLE_TAP_ZOOM, (tap - Offset(size.width / 2f, size.height / 2f)) * (1f - PDF_DOUBLE_TAP_ZOOM))
                })
            }
            .transformable(transform, canPan = { scale > 1f })
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxSize()) }
            ?: CircularProgressIndicator()
    }
}

/**
 * Vorschau der ersten Seite fuer Listen: die Datei kommt in denselben Cache wie beim Oeffnen, aber nur, wenn sie
 * nicht groesser als PDF_THUMB_MAX_BYTES ist - ein Kirchenbuch mit 60 MB laedt niemand fuer eine Kachel. Dann bleibt
 * es beim Kuerzel.
 */
@Composable
actual fun PdfThumbnail(url: String, modifier: Modifier, content: @Composable BoxScope.(ImageBitmap?) -> Unit) {
    val app = LocalContext.current.applicationContext as WtApp
    val cacheDir = LocalContext.current.cacheDir
    val bitmap by produceState<Bitmap?>(null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                PdfDocument.open(app, url, cacheDir, maxBytes = PDF_THUMB_MAX_BYTES).use { it.render(0, PDF_THUMB_WIDTH) }
            }.getOrNull()
        }
    }
    Box(modifier) { content(bitmap?.asImageBitmap()) }
}

private const val PDF_THUMB_MAX_BYTES = 3L * 1024 * 1024
private const val PDF_THUMB_WIDTH = 240

/** Ein geoeffnetes PDF: Datei im Cache, Renderer, und das Schloss, das die Seiten nacheinander zeichnen laesst. */
class PdfDocument private constructor(private val fd: ParcelFileDescriptor, private val renderer: PdfRenderer) {
    private val lock = Mutex()
    private val ratios = HashMap<Int, Float>()

    val pageCount: Int get() = renderer.pageCount

    /** Breite zu Hoehe der Seite - fuer den Platzhalter, bevor die Seite gezeichnet ist. */
    fun ratio(index: Int): Float = ratios[index] ?: (1f / 1.414f)

    suspend fun render(index: Int, widthPx: Int): Bitmap = lock.withLock {
        renderer.openPage(index).use { page ->
            val ratio = page.width.toFloat() / page.height
            ratios[index] = ratio
            val bitmap = Bitmap.createBitmap(widthPx, (widthPx / ratio).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { fd.close() }
    }

    inline fun <T> use(block: (PdfDocument) -> T): T = try { block(this) } finally { close() }

    companion object {
        /**
         * Laedt die Datei ueber die Sitzung der App in den Cache (einmal je Adresse) und oeffnet sie.
         * maxBytes: groessere Dateien werden nicht geladen (Vorschau in Listen); 0 = ohne Grenze.
         */
        fun open(app: WtApp, url: String, cacheDir: File, maxBytes: Long = 0): PdfDocument {
            val folder = File(cacheDir, "pdf").apply { mkdirs() }
            val name = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
            val file = File(folder, "$name.pdf")

            if (!file.exists() || file.length() == 0L) {
                app.client.http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                    val body = response.body ?: throw IllegalStateException("empty")
                    if (response.header("Content-Type").orEmpty().startsWith("text/html")) throw IllegalStateException("html")
                    if (maxBytes > 0 && body.contentLength() > maxBytes) throw IllegalStateException("too large")
                    val temp = File(folder, "$name.part")
                    temp.outputStream().use { out -> body.byteStream().copyTo(out) }
                    temp.renameTo(file)
                }
            }

            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return PdfDocument(fd, PdfRenderer(fd))
        }
    }
}
