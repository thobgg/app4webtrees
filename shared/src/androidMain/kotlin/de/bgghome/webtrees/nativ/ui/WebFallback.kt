package de.bgghome.webtrees.nativ.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.backhandler.BackHandler
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
 *
 * Die Zurueck-Taste geht innerhalb von webtrees eine Seite zurueck; erst auf der ersten Seite schliesst sie die Ansicht.
 * Links auf fremde Seiten (etwa eine URL in einer Quelle) oeffnet der Browser des Geraets - so bleibt der Weg durch
 * webtrees erhalten und Zurueck fuehrt wieder hierher (Issue 25).
 *
 * Ein Dateifeld auf der Seite (Medienobjekt anlegen) oeffnet nur dann die Dateiwahl des Geraets, wenn die App sie
 * ueber onShowFileChooser selbst startet - ohne das tat der Knopf "Datei auswaehlen" nichts (Issue 40).
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun WebFallbackScreen(url: String, onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as WtApp
    val host = url.toHttpUrlOrNull()?.host
    var webView by remember { mutableStateOf<WebView?>(null) }
    var kannZurueck by remember { mutableStateOf(false) }
    // Wartet auf das Ergebnis der Dateiwahl; null, wenn gerade keine laeuft.
    var dateiRueckgabe by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val dateiWahl = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { ergebnis ->
        dateiRueckgabe?.onReceiveValue(gewaehlteDateien(ergebnis.resultCode, ergebnis.data))
        dateiRueckgabe = null
    }

    BackHandler(enabled = kannZurueck) { webView?.goBack() }

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
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                            // Eine noch offene Wahl abschliessen, sonst wartet das WebView ewig auf sie.
                            dateiRueckgabe?.onReceiveValue(null)
                            dateiRueckgabe = callback
                            return try {
                                dateiWahl.launch(params.createIntent())
                                true
                            } catch (e: Exception) {
                                dateiRueckgabe = null
                                false
                            }
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val ziel = request.url
                            val fremd = ziel.scheme !in setOf("http", "https") || (host != null && ziel.host != host)
                            if (!fremd) return false
                            runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ziel.toString())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            return true
                        }

                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                            kannZurueck = view.canGoBack()
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            view.evaluateJavascript(MENUE_HOEHE, null)
                        }
                    }
                    // Erst laden, wenn das WebView seine Groesse hat: sonst rechnet es "vh" mit der Hoehe 0 und die
                    // aufgeklappten webtrees-Menues (max-height: 90vh) bleiben ein schmaler Streifen (Issue 28).
                    addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                        override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                            if (b - t <= 0) return
                            v.removeOnLayoutChangeListener(this)
                            loadUrl(url)
                        }
                    })
                    webView = this
                }
            },
        )
    }
}

/** Die gewaehlten Dateien aus dem Ergebnis der Dateiwahl - auch mehrere (ClipData), abgebrochen = null. */
private fun gewaehlteDateien(resultCode: Int, data: Intent?): Array<Uri>? {
    if (resultCode != android.app.Activity.RESULT_OK || data == null) return null
    val clip = data.clipData
    if (clip != null && clip.itemCount > 0) return Array(clip.itemCount) { clip.getItemAt(it).uri }
    return data.data?.let { arrayOf(it) }
}

/** Menues hoechstens 90 % der wirklichen Fensterhoehe in Pixeln statt 90vh, auch nach Drehen des Geraets. */
private const val MENUE_HOEHE = """(function(){var s=document.getElementById('wtand-menue');if(!s){s=document.createElement('style');s.id='wtand-menue';
document.head.appendChild(s);addEventListener('resize',function(){s.textContent='.dropdown-menu{max-height:'+Math.round(innerHeight*0.9)+'px!important}';});}
s.textContent='.dropdown-menu{max-height:'+Math.round(innerHeight*0.9)+'px!important}';})()"""
