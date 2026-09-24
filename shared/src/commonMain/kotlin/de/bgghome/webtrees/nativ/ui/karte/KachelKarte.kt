package de.bgghome.webtrees.nativ.ui.karte

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import java.io.File
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/*
 * Eigene Kachelkarte in Compose, uebernommen aus mpd-app (dort seit 22.09.2026, hier
 * 24.09.2026 fuer wtWin/wtTux): OpenStreetMap-Kacheln in Web-Mercator, ganze Zoomstufen,
 * Pins und Linien auf einem Canvas darueber. Kacheln laedt Coil ueber einen eigenen
 * OkHttp-Client mit ehrlichem User-Agent (OSM-Regel) und eigenem Plattencache - nie ueber
 * den API-Client, der traegt das Sitzungs-Cookie. Auf Android bleibt vorerst osmdroid
 * (LifeMap.kt); wenn diese Karte taugt, kann sie es abloesen, dann ist die Karte gemeinsam.
 */

data class GeoPunkt(val lat: Double, val lon: Double)

/** Pin auf der Karte; [tag] reicht der Aufrufer durch (z.B. Foto-Index). */
data class KartenPin(
    val lat: Double,
    val lon: Double,
    val text: String? = null,
    val farbe: Color = Color(0xFF2E75B6),
    val radiusDp: Float = 13f,
    val tag: Any? = null,
    /** Mini-Foto statt Kreis (Google-Maps-Stil, wie photoPin auf Android):
     *  quadratisch, runde Ecken, weisser Rand. Braucht den ImageLoader der Karte. */
    val bildUrl: String? = null,
)

data class KartenLinie(val punkte: List<GeoPunkt>, val farbe: Color, val breiteDp: Float)

enum class KachelEbene(val maxZoom: Int, val quelle: String) {
    STANDARD(19, "© OpenStreetMap") {
        override fun url(z: Int, x: Int, y: Int) = "https://tile.openstreetmap.org/$z/$x/$y.png"
    },
    TOPO(17, "© OpenTopoMap") {
        override fun url(z: Int, x: Int, y: Int): String {
            val s = "abc"[(x + y) % 3]
            return "https://$s.tile.opentopomap.org/$z/$x/$y.png"
        }
    };

    abstract fun url(z: Int, x: Int, y: Int): String
}

object WebMercator {
    private const val MAX_LAT = 85.0511
    fun xTile(lon: Double, z: Int): Double = (lon + 180.0) / 360.0 * (1 shl z)
    fun yTile(lat: Double, z: Int): Double {
        val r = Math.toRadians(lat.coerceIn(-MAX_LAT, MAX_LAT))
        return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * (1 shl z)
    }
    fun lon(x: Double, z: Int): Double = x / (1 shl z) * 360.0 - 180.0
    fun lat(y: Double, z: Int): Double = Math.toDegrees(atan(sinh(PI - 2.0 * PI * y / (1 shl z))))
}

/** Mittelpunkt und Zoomstufe; Compose-Zustand, damit die Karte mitzieht. */
class KartenZustand(lat: Double = 48.0, lon: Double = 10.0, zoom: Int = 5) {
    var lat by mutableStateOf(lat)
    var lon by mutableStateOf(lon)
    var zoom by mutableStateOf(zoom)

    fun setze(lat: Double, lon: Double, zoom: Int? = null) {
        this.lat = lat; this.lon = lon
        zoom?.let { this.zoom = it }
    }

    /** Alle Punkte ins Bild, mit Rand; ein einzelner Punkt bekommt [einzelZoom]. */
    fun passeEin(punkte: List<GeoPunkt>, breitePx: Int, hoehePx: Int, randPx: Int = 32, einzelZoom: Int = 12, maxZoom: Int = 17) {
        if (punkte.isEmpty() || breitePx <= 0 || hoehePx <= 0) return
        val minLat = punkte.minOf { it.lat }; val maxLat = punkte.maxOf { it.lat }
        val minLon = punkte.minOf { it.lon }; val maxLon = punkte.maxOf { it.lon }
        val mitteLat = (minLat + maxLat) / 2; val mitteLon = (minLon + maxLon) / 2
        if (punkte.size == 1 || (maxLat - minLat < 1e-6 && maxLon - minLon < 1e-6)) {
            setze(mitteLat, mitteLon, einzelZoom); return
        }
        for (z in maxZoom downTo 1) {
            val w = (WebMercator.xTile(maxLon, z) - WebMercator.xTile(minLon, z)) * 256
            val h = (WebMercator.yTile(minLat, z) - WebMercator.yTile(maxLat, z)) * 256
            if (w <= breitePx - 2 * randPx && h <= hoehePx - 2 * randPx) { setze(mitteLat, mitteLon, z); return }
        }
        setze(mitteLat, mitteLon, 1)
    }
}

/** Kachel-Cache je Plattform (Android: cacheDir/tiles, Desktop: ~/.cache/app4webtrees/tiles). */
expect fun kachelCacheOrdner(context: PlatformContext): File

/** User-Agent fuer die Kachelserver (OSM verlangt einen ehrlichen); die Plattform setzt ihn beim Start. */
var kachelUserAgent: String = "app4webtrees (https://github.com/thobgg/app4webtrees)"

private val kachelClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", kachelUserAgent)
                    .build(),
            )
        }
        .build()
}

@Composable
fun rememberKachelLoader(): ImageLoader {
    val ctx = LocalPlatformContext.current
    return remember {
        ImageLoader.Builder(ctx)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { kachelClient })) }
            .diskCache {
                DiskCache.Builder()
                    .directory(kachelCacheOrdner(ctx).apply { mkdirs() }.toOkioPath())
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}

/**
 * Die Karte. [interaktiv] = Ziehen, Mausrad/Doppelklick zoomen; sonst reine
 * Anzeige (Panel scrollt drueber). [onTap] bekommt den Ort unter dem
 * Zeiger, [onPinTap] den getroffenen Pin (hat Vorrang).
 */
@Composable
fun KachelKarte(
    zustand: KartenZustand,
    ebene: KachelEbene,
    modifier: Modifier = Modifier,
    interaktiv: Boolean = true,
    pins: List<KartenPin> = emptyList(),
    linien: List<KartenLinie> = emptyList(),
    onTap: ((GeoPunkt) -> Unit)? = null,
    onPinTap: ((KartenPin) -> Unit)? = null,
    /** Fuer Pins mit [KartenPin.bildUrl]; der ImageLoader der App (Sitzung). */
    imageLoader: ImageLoader? = null,
    /** Eigene Zeichnung ueber Linien und Pins (z.B. Heatmap); bekommt die Projektion. */
    zeichne: (DrawScope.(zumSchirm: (GeoPunkt) -> Offset) -> Unit)? = null,
) {
    val loader = rememberKachelLoader()
    val density = LocalDensity.current
    val tileDp = with(density) { 256.toDp() }
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier.clipToBounds().background(Color(0xFF2A2A2A))) {
        val wPx = constraints.maxWidth
        val hPx = constraints.maxHeight
        val z = zustand.zoom.coerceIn(1, ebene.maxZoom)
        val n = 1 shl z
        val cxPx = WebMercator.xTile(zustand.lon, z) * 256
        val cyPx = WebMercator.yTile(zustand.lat, z) * 256
        val left = cxPx - wPx / 2.0
        val top = cyPx - hPx / 2.0

        fun zumSchirm(p: GeoPunkt) = Offset(
            (WebMercator.xTile(p.lon, z) * 256 - left).toFloat(),
            (WebMercator.yTile(p.lat, z) * 256 - top).toFloat(),
        )
        fun zumOrt(o: Offset) = GeoPunkt(
            WebMercator.lat((top + o.y) / 256, z),
            WebMercator.lon((left + o.x) / 256, z),
        )
        fun verschiebe(dx: Float, dy: Float) {
            zustand.setze(
                WebMercator.lat((cyPx - dy) / 256, z),
                WebMercator.lon((cxPx - dx) / 256, z),
            )
        }
        fun zoomBei(o: Offset, delta: Int) {
            val neu = (z + delta).coerceIn(1, ebene.maxZoom)
            if (neu == z) return
            val ort = zumOrt(o)
            // Der Ort unter dem Zeiger bleibt unter dem Zeiger.
            val ox = WebMercator.xTile(ort.lon, neu) * 256 - (o.x - wPx / 2.0)
            val oy = WebMercator.yTile(ort.lat, neu) * 256 - (o.y - hPx / 2.0)
            zustand.setze(WebMercator.lat(oy / 256, neu), WebMercator.lon(ox / 256, neu), neu)
        }
        fun pinBei(o: Offset): KartenPin? = pins.lastOrNull { p ->
            val s = zumSchirm(GeoPunkt(p.lat, p.lon))
            val r = with(density) { (p.radiusDp + 4).dp.toPx() }
            (s - o).getDistance() <= r
        }

        val gesten = if (!interaktiv) Modifier else Modifier
            // Mausrad: als Ereignisschleife, weil Modifier.onPointerEvent nur
            // auf dem Desktop existiert und diese Karte auch auf Android laeuft.
            .pointerInput(z, wPx, hPx) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.type != PointerEventType.Scroll) continue
                        val change = ev.changes.firstOrNull() ?: continue
                        val dy = change.scrollDelta.y
                        if (dy != 0f) { zoomBei(change.position, if (dy < 0) 1 else -1); change.consume() }
                    }
                }
            }
            .pointerInput(z, wPx, hPx) {
                detectDragGestures { change, drag -> change.consume(); verschiebe(drag.x, drag.y) }
            }
            .pointerInput(z, wPx, hPx, pins) {
                detectTapGestures(
                    onTap = { o -> pinBei(o)?.let { onPinTap?.invoke(it) } ?: onTap?.invoke(zumOrt(o)) },
                    onDoubleTap = { o -> zoomBei(o, 1) },
                )
            }

        Box(Modifier.fillMaxSize().then(gesten)) {
            val tx0 = floor(left / 256).toInt()
            val tx1 = floor((left + wPx) / 256).toInt()
            val ty0 = max(0, floor(top / 256).toInt())
            val ty1 = min(n - 1, floor((top + hPx) / 256).toInt())
            for (ty in ty0..ty1) for (tx in tx0..tx1) {
                val wx = ((tx % n) + n) % n
                AsyncImage(
                    model = ebene.url(z, wx, ty),
                    imageLoader = loader,
                    contentDescription = null,
                    modifier = Modifier
                        .offset { IntOffset((tx * 256 - left).roundToInt(), (ty * 256 - top).roundToInt()) }
                        .size(tileDp),
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                for (l in linien) {
                    if (l.punkte.size < 2) continue
                    val path = Path()
                    l.punkte.forEachIndexed { i, p ->
                        val s = zumSchirm(p)
                        if (i == 0) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y)
                    }
                    drawPath(path, l.farbe, style = Stroke(width = l.breiteDp.dp.toPx(), cap = StrokeCap.Round))
                }
                for (p in pins) {
                    if (p.bildUrl != null) continue // als Composable darueber
                    val s = zumSchirm(GeoPunkt(p.lat, p.lon))
                    val r = p.radiusDp.dp.toPx()
                    drawCircle(Color.White, r + 2.dp.toPx(), s)
                    drawCircle(p.farbe, r, s)
                    p.text?.let { t ->
                        val layout = textMeasurer.measure(
                            t, TextStyle(color = Color.White, fontSize = (p.radiusDp * 0.85f).sp, fontWeight = FontWeight.Bold),
                        )
                        drawText(layout, topLeft = Offset(s.x - layout.size.width / 2f, s.y - layout.size.height / 2f))
                    }
                }
                zeichne?.invoke(this) { p -> zumSchirm(p) }
            }
            // Foto-Marker: 52 dp, runde Ecken, weisser Rand mit Schatten -
            // dieselbe Optik wie photoPin auf Android; Klick wie ein Pin.
            if (imageLoader != null) for (p in pins) {
                val url = p.bildUrl ?: continue
                val s = zumSchirm(GeoPunkt(p.lat, p.lon))
                val halb = with(density) { 26.dp.roundToPx() }
                AsyncImage(
                    model = url, imageLoader = imageLoader, contentDescription = p.text,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset { IntOffset(s.x.roundToInt() - halb, s.y.roundToInt() - halb) }
                        .size(52.dp)
                        .shadow(3.dp, RoundedCornerShape(8.dp))
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(2.5.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .then(if (onPinTap != null) Modifier.clickable { onPinTap(p) } else Modifier),
                )
            }
            Text(
                ebene.quelle, color = Color(0xCC000000), fontSize = 9.sp,
                modifier = Modifier.align(Alignment.BottomEnd).background(Color(0x99FFFFFF)).padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}
