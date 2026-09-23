package de.bgghome.webtrees.nativ.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.SingletonImageLoader
import de.bgghome.webtrees.nativ.Desktop
import de.bgghome.webtrees.nativ.DesktopPlattform
import de.bgghome.webtrees.nativ.desk.DeskRoot
import de.bgghome.webtrees.nativ.desk.DeskTheme
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.LocalAppName
import de.bgghome.webtrees.nativ.ui.wtImageLoader

/**
 * wtWin / wtTux: derselbe Kern wie wtAnd, am Schreibtisch im Aufbau "Baum im Mittelpunkt" (desk/DeskRoot.kt) -
 * Menueleiste, Arbeitsbereiche, Personenliste, Baum, Personentafel, Statuszeile (Etappe 3, 23.09.2026).
 */
fun main() {
    val plattform = DesktopPlattform().also { Desktop.plattform = it }
    SingletonImageLoader.setSafe { context -> wtImageLoader(context, plattform.client) }

    application {
        val viewModel = remember { AppViewModel(plattform) }
        Window(
            onCloseRequest = ::exitApplication,
            title = plattform.appName,
            state = rememberWindowState(width = 1280.dp, height = 820.dp),
        ) {
            CompositionLocalProvider(LocalAppName provides plattform.appName) {
                DeskTheme { DeskRoot(viewModel, onQuit = ::exitApplication) }
            }
        }
    }
}
