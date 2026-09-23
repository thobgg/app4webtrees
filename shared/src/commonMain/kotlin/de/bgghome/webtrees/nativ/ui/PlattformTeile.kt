package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import de.bgghome.webtrees.nativ.api.FactJson

/*
 * Was jede Plattform selbst zeichnet oder selbst holt (Aufteilung 23.09.2026). Android behaelt, was es schon
 * hatte (osmdroid, PdfRenderer, WebView, Photo Picker und Kamera); der Desktop setzt eigene Stuecke ein.
 */

/**
 * Name des Programms auf dieser Plattform: wtAnd (Android), wtWin (Windows), wtTux (Linux) - Entscheidung Thomas,
 * 23.09.2026. Das Gesamtprojekt heisst app4webtrees; die Namen folgen dem Muster, das der webtrees-Autor akzeptiert
 * (kein "webtrees" vorn, das nach offiziellem Produkt klingt).
 */
val LocalAppName = staticCompositionLocalOf { "wtAnd" }

/**
 * Ein Foto, das hochgeladen werden soll - unabhaengig davon, woher es kommt (Android: content://-Adresse aus
 * Galerie oder Kamera, Desktop: Datei). [preview] ist das Modell fuer die Vorschau (Coil kennt beide Arten).
 */
class PhotoFile(
    val name: String,
    val mime: String,
    val preview: Any,
    /** Verkleinert, gedreht und als JPEG, hoechstens [maxBytes] gross - oder null, wenn es sich nicht als Bild lesen laesst. */
    val prepareJpeg: suspend (maxBytes: Long) -> ByteArray?,
    /** Die Datei unveraendert. */
    val readBytes: suspend () -> ByteArray,
)

/** Die Wege zu einem Foto: aus der Galerie (oder vom Datentraeger) waehlen, mit der Kamera aufnehmen. */
class PhotoSources(val pick: () -> Unit, val take: () -> Unit, val canTake: Boolean = true)

@Composable
expect fun rememberPhotoSources(onPhoto: (PhotoFile) -> Unit): PhotoSources

/** Liefert eine Funktion, die - wo noetig - die Erlaubnis fuer Benachrichtigungen erfragt und dann [onGranted] ruft. */
@Composable
expect fun rememberNotificationPermission(onGranted: () -> Unit): () -> Unit

/** Lebensstationen auf der Karte. */
@Composable
expect fun LifeMap(facts: List<FactJson>)

/** PDF-Betrachter fuer Urkunden und Briefe aus dem Archiv. */
@Composable
expect fun PdfViewer(target: PdfTarget, onClose: () -> Unit, onOpenWeb: (String) -> Unit)

/** Vorschau der ersten Seite eines PDFs fuer Listen; null, solange (oder wenn) sie nicht da ist. */
@Composable
expect fun PdfThumbnail(url: String, modifier: Modifier = Modifier, content: @Composable BoxScope.(ImageBitmap?) -> Unit)

/** Rueckfall fuer alles, was (noch) nicht nativ ist: die webtrees-Seite selbst, in derselben Sitzung. */
@Composable
expect fun WebFallbackScreen(url: String, onClose: () -> Unit)
