package de.bgghome.webtrees.nativ.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.bgghome.webtrees.nativ.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Etappe 1 (23.09.2026): Das Desktop-Ziel baut und oeffnet ein Fenster mit den
 * geteilten Texten. Anmeldung, Baum und Profil folgen, sobald ihre Bildschirme
 * ohne Android auskommen (androidMain -> commonMain, siehe shared/build.gradle.kts).
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
        state = rememberWindowState(width = 1100.dp, height = 760.dp),
    ) {
        MaterialTheme {
            Scaffold(Modifier.fillMaxSize()) { inner ->
                Column(
                    Modifier.fillMaxSize().padding(inner).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(Res.string.setup_subtitle), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
