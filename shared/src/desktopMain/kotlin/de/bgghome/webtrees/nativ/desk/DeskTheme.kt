package de.bgghome.webtrees.nativ.desk

import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.ui.LocalDeskMode
import de.bgghome.webtrees.nativ.ui.WtTheme

/**
 * Das Aussehen am Schreibtisch: dieselben Farben wie wtAnd, aber dichter und eckiger - Schrift eine Stufe kleiner,
 * kaum Rundungen, keine 48-dp-Tippflaechen. So wirkt das Fenster wie ein Arbeitsprogramm, nicht wie ein Handy.
 */
@Composable
fun DeskTheme(content: @Composable () -> Unit) {
    WtTheme {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            typography = MaterialTheme.typography.dichter(),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(3.dp), medium = RoundedCornerShape(4.dp),
                large = RoundedCornerShape(6.dp), extraLarge = RoundedCornerShape(8.dp),
            ),
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp, LocalDeskMode provides true, content = content)
        }
    }
}

private fun TextStyle.kleiner(): TextStyle = copy(fontSize = fontSize * 0.9f, lineHeight = lineHeight * 0.9f)

private fun Typography.dichter() = Typography(
    displayLarge = displayLarge.kleiner(), displayMedium = displayMedium.kleiner(), displaySmall = displaySmall.kleiner(),
    headlineLarge = headlineLarge.kleiner(), headlineMedium = headlineMedium.kleiner(), headlineSmall = headlineSmall.kleiner(),
    titleLarge = titleLarge.kleiner(), titleMedium = titleMedium.kleiner(), titleSmall = titleSmall.kleiner(),
    bodyLarge = bodyLarge.kleiner(), bodyMedium = bodyMedium.kleiner(), bodySmall = bodySmall.kleiner(),
    labelLarge = labelLarge.kleiner(), labelMedium = labelMedium.kleiner(), labelSmall = labelSmall.kleiner(),
)
