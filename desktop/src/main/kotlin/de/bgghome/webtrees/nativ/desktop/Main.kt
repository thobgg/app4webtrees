package de.bgghome.webtrees.nativ.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import coil3.SingletonImageLoader
import de.bgghome.webtrees.nativ.Desktop
import de.bgghome.webtrees.nativ.DesktopPlattform
import de.bgghome.webtrees.nativ.data.DesktopAblage
import de.bgghome.webtrees.nativ.desk.DeskRoot
import de.bgghome.webtrees.nativ.desk.DeskTheme
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.LocalAppName
import de.bgghome.webtrees.nativ.ui.wtImageLoader
import javax.swing.UIManager

/**
 * wtWin / wtTux: derselbe Kern wie wtAnd, am Schreibtisch in eigenen Aufbauten (desk/). Menueleiste und
 * Dateidialog im Aussehen des Systems, Fenstergroesse und -lage bleiben ueber einen Neustart erhalten.
 */
fun main() {
    // Die Menueleiste ist Swing: ohne diese Zeile erscheint sie im Java-eigenen Stil statt wie unter Windows.
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }

    val plattform = DesktopPlattform().also { Desktop.plattform = it }
    SingletonImageLoader.setSafe { context -> wtImageLoader(context, plattform.client) }
    val fenster = FensterAblage()

    application {
        val viewModel = remember { AppViewModel(plattform) }
        val state = remember { fenster.laden() }
        Window(
            onCloseRequest = { fenster.sichern(state); exitApplication() },
            title = plattform.appName,
            state = state,
        ) {
            CompositionLocalProvider(LocalAppName provides plattform.appName) {
                DeskTheme { DeskRoot(viewModel, onQuit = { fenster.sichern(state); exitApplication() }) }
            }
        }
    }
}

/** Groesse, Lage und Maximierung des Hauptfensters in den Desktop-Einstellungen. */
private class FensterAblage {
    private val prefs = DesktopAblage("desk")
    private fun zahl(key: String) = prefs.getString(key, null)?.toFloatOrNull()

    fun laden(): WindowState {
        val w = zahl("win_w"); val h = zahl("win_h"); val x = zahl("win_x"); val y = zahl("win_y")
        // Bildschirm in dp (Windows-Skalierung beruecksichtigt): nie groesser oeffnen, als Platz ist.
        val schirm = runCatching {
            val gc = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
            val skala = gc.defaultTransform.scaleX.toFloat()
            val b = gc.bounds; val rand = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc)
            DpSize(((b.width - rand.left - rand.right) / skala).dp, ((b.height - rand.top - rand.bottom) / skala).dp)
        }.getOrNull()
        val erstesMal = w == null || h == null
        val breite = minOf(w ?: 1280f, schirm?.width?.value ?: 1280f)
        val hoehe = minOf(h ?: 820f, (schirm?.height?.value ?: 820f) - 40f)
        return WindowState(
            // Erster Start: maximiert - ein Arbeitsprogramm nutzt den ganzen Bildschirm (Rueckmeldung Thomas, 23.09.2026).
            placement = if (erstesMal || prefs.getBoolean("win_max", false)) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = if (x != null && y != null) WindowPosition(x.dp, y.dp) else WindowPosition.PlatformDefault,
            size = DpSize(breite.dp, hoehe.dp),
        )
    }

    fun sichern(state: WindowState) {
        prefs.putBoolean("win_max", state.placement == WindowPlacement.Maximized)
        if (state.placement == WindowPlacement.Floating) {
            prefs.putString("win_w", state.size.width.value.toString())
            prefs.putString("win_h", state.size.height.value.toString())
            (state.position as? WindowPosition.Absolute)?.let {
                prefs.putString("win_x", it.x.value.toString())
                prefs.putString("win_y", it.y.value.toString())
            }
        }
    }
}
