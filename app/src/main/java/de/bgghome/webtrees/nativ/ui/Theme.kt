package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Festes Farbschema (Petrol) statt der Geraetefarben: Die App soll auf jedem Geraet gleich aussehen.
 * Formensprache wie in gaengigen Stammbaum-Apps: weisse Flaechen
 * auf hellgrauem Grund, Haarlinien, kaum Schatten - aber eigene Farben, kein fremdes Branding.
 */
private val Light = lightColorScheme(
    primary = Color(0xFF1F6F78), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE9EC), onPrimaryContainer = Color(0xFF05343A),
    secondary = Color(0xFF4A6366), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEBEC), onSecondaryContainer = Color(0xFF0F2C2F),
    tertiary = Color(0xFFB5562F), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCD), onTertiaryContainer = Color(0xFF3A1406),
    background = Color(0xFFF1F3F3), onBackground = Color(0xFF24302F),
    surface = Color.White, onSurface = Color(0xFF24302F),
    surfaceVariant = Color(0xFFE6EBEB), onSurfaceVariant = Color(0xFF5B6665),
    surfaceContainerLowest = Color(0xFFF1F3F3), surfaceContainerLow = Color(0xFFF7F8F8),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFFFFFFF), surfaceContainerHighest = Color(0xFFEDF0F0),
    outline = Color(0xFF9AA5A4), outlineVariant = Color(0xFFE1E5E4),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD0DA), onPrimary = Color(0xFF00363C),
    primaryContainer = Color(0xFF0F4F57), onPrimaryContainer = Color(0xFFCDE9EC),
    secondary = Color(0xFFB1CBCE), onSecondary = Color(0xFF1C3437),
    secondaryContainer = Color(0xFF2C4346), onSecondaryContainer = Color(0xFFDCEBEC),
    tertiary = Color(0xFFFFB597), onTertiary = Color(0xFF571F09),
    tertiaryContainer = Color(0xFF75331B), onTertiaryContainer = Color(0xFFFFDBCD),
    background = Color(0xFF101414), onBackground = Color(0xFFDFE3E3),
    surface = Color(0xFF181D1D), onSurface = Color(0xFFDFE3E3),
    surfaceVariant = Color(0xFF2A3131), onSurfaceVariant = Color(0xFFAEB8B7),
    surfaceContainerLowest = Color(0xFF101414), surfaceContainerLow = Color(0xFF151A1A),
    surfaceContainer = Color(0xFF181D1D), surfaceContainerHigh = Color(0xFF1F2525), surfaceContainerHighest = Color(0xFF2A3131),
    outline = Color(0xFF7D8887), outlineVariant = Color(0xFF333B3B),
)

/** Farben, die Material nicht kennt: Geschlecht (duenner Kartenrahmen), Verbindungslinien, Silhouetten. */
data class TreeColors(val male: Color, val female: Color, val unknown: Color, val connector: Color, val silhouetteBackground: Color, val silhouette: Color)

private val LightTree = TreeColors(Color(0xFF4FA9C4), Color(0xFFE58A86), Color(0xFFA3ACAB), Color(0xFFA0A8A7), Color(0xFFE3E8E8), Color(0xFFB4BDBC))
private val DarkTree = TreeColors(Color(0xFF5FB6CF), Color(0xFFE39892), Color(0xFF7D8887), Color(0xFF6A7473), Color(0xFF2A3131), Color(0xFF4A5453))

val treeColors: TreeColors
    @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) DarkTree else LightTree

fun TreeColors.forSex(sex: String): Color = when (sex) {
    "M" -> male
    "F" -> female
    else -> unknown
}

@Composable
fun WtTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
