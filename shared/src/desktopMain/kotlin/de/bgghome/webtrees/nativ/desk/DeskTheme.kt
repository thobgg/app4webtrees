package de.bgghome.webtrees.nativ.desk

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.platform.SystemFont
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.ui.LocalDeskMode
import de.bgghome.webtrees.nativ.ui.WtTheme

/**
 * Das Aussehen am Schreibtisch: dieselben Farben wie wtAnd, aber dichter und eckiger - Schrift eine Stufe kleiner,
 * kaum Rundungen, keine 48-dp-Tippflaechen. So wirkt das Fenster wie ein Arbeitsprogramm, nicht wie ein Handy.
 */
/** Hell, dunkel oder wie das System - gilt fuer alle Fenster; Voreinstellung hell wie bei Arbeitsprogrammen ueblich. */
object DeskErscheinung {
    enum class Wahl { Hell, Dunkel, System }
    private val prefs = de.bgghome.webtrees.nativ.data.DesktopAblage("desk")
    val wahl = mutableStateOf(Wahl.entries.firstOrNull { it.name == prefs.getString("erscheinung", null) } ?: Wahl.Hell)
    fun setzen(w: Wahl) { wahl.value = w; prefs.putString("erscheinung", w.name) }
    val dunkel: Boolean? get() = when (wahl.value) { Wahl.Hell -> false; Wahl.Dunkel -> true; Wahl.System -> null }
}

@Composable
fun DeskTheme(content: @Composable () -> Unit) {
    WtTheme(dark = DeskErscheinung.dunkel) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            typography = MaterialTheme.typography.dichter(),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(3.dp), medium = RoundedCornerShape(4.dp),
                large = RoundedCornerShape(6.dp), extraLarge = RoundedCornerShape(8.dp),
            ),
        ) {
            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides 0.dp,
                LocalDeskMode provides true,
                LocalScrollbarStyle provides ScrollbarStyle(
                    minimalHeight = 24.dp, thickness = 10.dp, shape = RoundedCornerShape(2.dp), hoverDurationMillis = 200,
                    unhoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                    hoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                ),
                content = content,
            )
        }
    }
}

/** Unter Windows die Schrift des Systems (Segoe UI), sonst die Vorgabe der Plattform. */
@OptIn(ExperimentalTextApi::class)
private val systemSchrift: FontFamily? =
    if (System.getProperty("os.name").orEmpty().startsWith("Windows")) FontFamily(
        SystemFont("Segoe UI", FontWeight.Normal), SystemFont("Segoe UI Semibold", FontWeight.SemiBold),
        SystemFont("Segoe UI", FontWeight.Bold), SystemFont("Segoe UI Semibold", FontWeight.Medium),
    ) else null

private fun TextStyle.kleiner(): TextStyle =
    copy(fontSize = fontSize * 0.9f, lineHeight = lineHeight * 0.9f, fontFamily = systemSchrift ?: fontFamily)

private fun Typography.dichter() = Typography(
    displayLarge = displayLarge.kleiner(), displayMedium = displayMedium.kleiner(), displaySmall = displaySmall.kleiner(),
    headlineLarge = headlineLarge.kleiner(), headlineMedium = headlineMedium.kleiner(), headlineSmall = headlineSmall.kleiner(),
    titleLarge = titleLarge.kleiner(), titleMedium = titleMedium.kleiner(), titleSmall = titleSmall.kleiner(),
    bodyLarge = bodyLarge.kleiner(), bodyMedium = bodyMedium.kleiner(), bodySmall = bodySmall.kleiner(),
    labelLarge = labelLarge.kleiner(), labelMedium = labelMedium.kleiner(), labelSmall = labelSmall.kleiner(),
)
