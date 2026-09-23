package de.bgghome.webtrees.nativ.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import de.bgghome.webtrees.nativ.WtApp
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jetbrains.compose.resources.stringResource
import de.bgghome.webtrees.nativ.res.*

/**
 * Rueckfall fuer alles, was (noch) nicht nativ ist: die webtrees-Seite selbst, in derselben
 * Sitzung - das Sitzungs-Cookie der App wird an das WebView uebergeben.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebFallbackScreen(url: String, onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as WtApp

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("webtrees") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_close)) } },
            )
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier.padding(padding).fillMaxSize(),
            factory = { context ->
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)

                url.toHttpUrlOrNull()?.let { httpUrl ->
                    app.client.cookieJar.cookiesFor(httpUrl).forEach { cookie ->
                        cookieManager.setCookie(httpUrl.toString(), cookie.toString())
                    }
                }

                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webViewClient = WebViewClient()
                    loadUrl(url)
                }
            },
        )
    }
}
