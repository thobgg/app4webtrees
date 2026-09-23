package de.bgghome.webtrees.nativ

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.bgghome.webtrees.nativ.ui.AppRoot
import de.bgghome.webtrees.nativ.ui.AppViewModel
import de.bgghome.webtrees.nativ.ui.connect
import de.bgghome.webtrees.nativ.ui.WtTheme

class MainActivity : ComponentActivity() {

    // Das ViewModel bekommt die Plattform der App (SharedPreferences, WorkManager) - es kennt Android selbst nicht mehr.
    private val viewModel: AppViewModel by viewModels {
        viewModelFactory { initializer { AppViewModel((application as WtApp).plattform) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleConnectLink(intent)
        setContent {
            WtTheme {
                AppRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleConnectLink(intent)
    }

    /** "Verbinden" aus webtrees: Adresse, Baum und Einmal-Code kommen im Link - nichts muss getippt werden. Die App fragt vor dem Koppeln nach. */
    private fun handleConnectLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "webtreesand" || uri.host != "connect") return

        viewModel.connect(
            url = uri.getQueryParameter("url").orEmpty(),
            tree = uri.getQueryParameter("tree").orEmpty(),
            code = uri.getQueryParameter("code").orEmpty(),
            user = uri.getQueryParameter("user").orEmpty(),
        )
        // Den Code nicht im Intent liegen lassen (er waere nach einer Drehung des Bildschirms ohnehin verbraucht).
        intent.data = null
    }
}
