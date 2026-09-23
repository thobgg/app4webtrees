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
import de.bgghome.webtrees.nativ.ui.AppRoot
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.LocalAppName
import de.bgghome.webtrees.nativ.ui.WtTheme
import de.bgghome.webtrees.nativ.ui.wtImageLoader

/**
 * Etappe 2 (23.09.2026): dieselbe Oberflaeche wie am Handy im Fenster - Anmeldung, Baum, Profil, Suche, Fotos.
 * Ab 840 dp Breite zeigt sie schon heute Leiste und Profil neben dem Baum; das Fenster-Layout (Menueleiste,
 * drei Spalten) folgt in der naechsten Etappe.
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
                WtTheme { AppRoot(viewModel) }
            }
        }
    }
}
