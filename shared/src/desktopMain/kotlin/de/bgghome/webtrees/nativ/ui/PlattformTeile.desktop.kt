package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.Desktop
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.res.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.jetbrains.compose.resources.stringResource
import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.security.MessageDigest
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

// ── Fotos: Dateidialog statt Galerie, keine Kamera ──────────────────

@Composable
actual fun rememberPhotoSources(onPhoto: (PhotoFile) -> Unit): PhotoSources = remember {
    PhotoSources(
        pick = {
            val dialog = FileDialog(null as Frame?, "", FileDialog.LOAD).apply {
                setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in BILDENDUNGEN }
                isVisible = true
            }
            val name = dialog.file ?: return@PhotoSources
            onPhoto(photoFile(File(dialog.directory, name)))
        },
        take = {},
        canTake = false,
    )
}

private val BILDENDUNGEN = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "tif", "tiff")

private fun photoFile(file: File): PhotoFile {
    val mime = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "gif" -> "image/gif"; "bmp" -> "image/bmp"
        "webp" -> "image/webp"; "tif", "tiff" -> "image/tiff"; else -> ""
    }
    return PhotoFile(
        name = file.name, mime = mime, preview = file,
        prepareJpeg = { limit -> toUploadJpeg(file, limit) },
        readBytes = { file.readBytes() },
    )
}

/**
 * Wie ImagePrep auf Android: hoechstens 2560 Pixel Kantenlaenge, als JPEG, so lange staerker komprimiert und notfalls
 * verkleinert, bis es unter dem Limit des Servers liegt. Die EXIF-Drehung beachtet der Desktop noch nicht.
 */
private fun toUploadJpeg(file: File, maxBytes: Long): ByteArray? {
    val original = ImageIO.read(file) ?: return null
    fun scaled(source: BufferedImage, side: Int): BufferedImage {
        val scale = side.toDouble() / maxOf(source.width, source.height)
        if (scale >= 1.0 && source.type == BufferedImage.TYPE_INT_RGB) return source
        val w = (source.width * minOf(scale, 1.0)).toInt().coerceAtLeast(1)
        val h = (source.height * minOf(scale, 1.0)).toInt().coerceAtLeast(1)
        return BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).also { out ->
            out.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                drawImage(source, 0, 0, w, h, java.awt.Color.WHITE, null)
                dispose()
            }
        }
    }
    fun encode(image: BufferedImage, quality: Float): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            val param = writer.defaultWriteParam.apply { compressionMode = ImageWriteParam.MODE_EXPLICIT; compressionQuality = quality }
            writer.write(null, IIOImage(image, null, null), param)
        }
        writer.dispose()
        return out.toByteArray()
    }
    var side = 2560
    while (true) {
        val image = scaled(original, side)
        for (quality in listOf(0.88f, 0.80f, 0.72f, 0.64f)) {
            val bytes = encode(image, quality)
            if (bytes.size <= maxBytes) return bytes
        }
        if (side <= 800) return encode(image, 0.6f)
        side = (side * 0.8).toInt()
    }
}

// Der Desktop erinnert nicht im Hintergrund; das Menue bietet es gar nicht erst an.
@Composable
actual fun rememberNotificationPermission(onGranted: () -> Unit): () -> Unit = onGranted

// ── Karte: vorerst die Stationen als Liste, jede oeffnet OpenStreetMap im Browser ──

@Composable
actual fun LifeMap(facts: List<FactJson>) {
    val stations = facts.filter { it.place?.lat != null && it.place.lng != null }
    if (stations.isEmpty()) {
        Text(stringResource(Res.string.map_none), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        stations.forEach { fact ->
            val place = fact.place!!
            val lat = place.lat!!; val lng = place.lng!!
            val year = fact.date?.year?.takeIf { it != 0 }?.toString()
            Text(
                listOfNotNull(year, fact.label, place.short).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
                    .clickable { openBrowser("https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=12/$lat/$lng") }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

// ── PDF: PDFBox zeichnet die Seiten, die Datei kommt ueber die Sitzung der App ──

private class PdfDatei(file: File) : AutoCloseable {
    private val doc: PDDocument = Loader.loadPDF(file)
    private val renderer = PDFRenderer(doc)
    private val lock = Mutex()
    val pageCount: Int get() = doc.numberOfPages

    suspend fun render(index: Int, widthPx: Int): ImageBitmap = lock.withLock {
        withContext(Dispatchers.IO) {
            val box = doc.getPage(index).mediaBox
            val scale = widthPx / box.width
            renderer.renderImage(index, scale).toComposeImageBitmap()
        }
    }

    override fun close() = doc.close()

    companion object {
        fun open(url: String, maxBytes: Long = 0): PdfDatei {
            val folder = File(Desktop.plattform.cacheOrdner, "pdf").apply { mkdirs() }
            val name = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
            val file = File(folder, "$name.pdf")
            if (!file.exists() || file.length() == 0L) {
                Desktop.plattform.client.http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                    val body = response.body ?: throw IllegalStateException("empty")
                    if (response.header("Content-Type").orEmpty().startsWith("text/html")) throw IllegalStateException("html")
                    if (maxBytes > 0 && body.contentLength() > maxBytes) throw IllegalStateException("too large")
                    val temp = File(folder, "$name.part")
                    temp.outputStream().use { out -> body.byteStream().copyTo(out) }
                    temp.renameTo(file)
                }
            }
            return PdfDatei(file)
        }
    }
}

@Composable
actual fun PdfViewer(target: PdfTarget, onClose: () -> Unit, onOpenWeb: (String) -> Unit) {
    var document by remember { mutableStateOf<PdfDatei?>(null) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(target.url) {
        document = withContext(Dispatchers.IO) { runCatching { PdfDatei.open(target.url) }.getOrNull() }
        error = document == null
    }
    DisposableEffect(document) {
        val geoeffnet = document
        onDispose { geoeffnet?.close() }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_close)) }
            Column(Modifier.weight(1f)) {
                Text(target.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                document?.let { Text(stringResource(Res.string.pdf_pages, it.pageCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            target.webUrl?.let { url ->
                IconButton(onClick = { onOpenWeb(url) }) { Icon(Icons.Default.ExitToApp, contentDescription = stringResource(Res.string.viewer_open_web)) }
            }
        }
        val doc = document
        when {
            error -> Text(stringResource(Res.string.pdf_error), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            doc == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                // Am Bildschirm hoechstens 1000 dp breit - eine Urkunde in voller Fensterbreite liest sich schlecht.
                val widthDp = minOf(maxWidth, 1000.dp)
                val widthPx = with(LocalDensity.current) { widthDp.roundToPx() }
                LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    items((0 until doc.pageCount).toList()) { index ->
                        val page by produceState<ImageBitmap?>(null, doc, index, widthPx) { value = runCatching { doc.render(index, widthPx) }.getOrNull() }
                        Box(Modifier.padding(vertical = 4.dp)) {
                            page?.let { Image(it, contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth()) }
                                ?: Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
actual fun PdfThumbnail(url: String, modifier: Modifier, content: @Composable BoxScope.(ImageBitmap?) -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching { PdfDatei.open(url, maxBytes = 3L * 1024 * 1024).use { it.render(0, 240) } }.getOrNull()
        }
    }
    Box(modifier) { content(bitmap) }
}

// ── Web-Rueckfall: der Systembrowser (dort ist man ggf. noch einmal anzumelden) ──

@Composable
actual fun WebFallbackScreen(url: String, onClose: () -> Unit) {
    LaunchedEffect(url) {
        openBrowser(url)
        onClose()
    }
}

internal fun openBrowser(url: String) {
    runCatching { java.awt.Desktop.getDesktop().browse(URI(url)) }
        .recoverCatching { ProcessBuilder("xdg-open", url).start() }
}
