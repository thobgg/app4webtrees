package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.api.FactJson
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Lebensstationen auf der Karte (OpenStreetMap ueber osmdroid - keine Google-Dienste, passend zu webtrees):
 * ein Marker je Ereignis mit Koordinaten, in zeitlicher Reihenfolge durch eine Linie verbunden.
 */
@Composable
fun LifeMap(facts: List<FactJson>) {
    val stations = facts.filter { it.place?.lat != null && it.place.lng != null }

    if (stations.isEmpty()) {
        Text(stringResource(Res.string.map_none), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    val context = LocalContext.current
    val lineColor = MaterialTheme.colorScheme.primary.hashCode()
    val map = remember { MapView(context) }

    DisposableEffect(map) {
        map.onResume()
        onDispose { map.onPause(); map.onDetach() }
    }

    // clipToBounds: die Kartenansicht zeichnet sonst ueber ihre Flaeche hinaus und verdeckt die Reiter darueber.
    Box(Modifier.fillMaxSize().clipToBounds()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                map.apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                }
            },
            update = { view ->
                view.overlays.clear()

                val points = stations.map { GeoPoint(it.place!!.lat!!, it.place.lng!!) }

                if (points.distinct().size > 1) {
                    view.overlays += Polyline(view).apply {
                        setPoints(points)
                        outlinePaint.color = lineColor
                        outlinePaint.strokeWidth = 6f
                    }
                }

                // Mehrere Ereignisse am selben Ort: ein Marker, alle Zeilen im Text
                stations.groupBy { it.place!!.lat to it.place.lng }.forEach { (_, here) ->
                    view.overlays += Marker(view).apply {
                        position = GeoPoint(here.first().place!!.lat!!, here.first().place!!.lng!!)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = here.first().place!!.short
                        snippet = here.joinToString("<br>") { listOfNotNull(it.date?.year?.takeIf { y -> y != 0 }?.toString(), it.label).joinToString(" · ") }
                    }
                }

                view.post {
                    if (points.distinct().size == 1) {
                        view.controller.setZoom(11.0)
                        view.controller.setCenter(points.first())
                    } else {
                        view.zoomToBoundingBox(BoundingBox.fromGeoPoints(points).increaseByScale(1.4f), false, 80)
                    }
                }
                view.invalidate()
            },
        )
    }
}
