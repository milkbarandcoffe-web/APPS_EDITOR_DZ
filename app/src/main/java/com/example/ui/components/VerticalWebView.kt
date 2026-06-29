package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VerticalWebView(
    url: String,
    extraAllowedHost: String = "",
    modifier: Modifier = Modifier,
    onWebViewCreated: (WebView) -> Unit = {}
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Nessun pointerInput/gesture-detector qui sopra: il Box NON deve intercettare
    // il touch prima della WebView, altrimenti il pinch-zoom e il pan a due dita
    // nativi della WebView vengono "mangiati" dal layer Compose e non funzionano.
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val requestUrl = request?.url?.toString() ?: return false
                            return if (!isSameWebapp(url, requestUrl) &&
                                       !isExtraAllowedHost(extraAllowedHost, requestUrl) &&
                                       !isGoogleAuthHost(requestUrl)) {
                                // Host diverso dalla webapp attiva: link veramente esterno -> Chrome
                                try {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(requestUrl)).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                    )
                                } catch (_: Exception) {
                                    // Nessuna app in grado di gestire il link: ignora silenziosamente
                                }
                                true
                            } else {
                                // Stesso host (o stessa famiglia script.google.com/googleusercontent.com):
                                // resta nella webapp, carica nel WebView
                                false
                            }
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                            loadError = null
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            // Il banner GAS vive nel documento host script.google.com (non
                            // dentro l'iframe della webapp). Lo individuiamo dal SUO testo,
                            // risaliamo al contenitore-banner più piccolo che lo racchiude
                            // e nascondiamo SOLO quello (display:none). Non rimuoviamo nodi
                            // generici (era la causa dello schermo bianco) e NON tocchiamo
                            // l'iframe 'sandboxFrame'/'userCodeAppPanel' della webapp.
                            view?.evaluateJavascript("""
                                (function() {
                                    function hideBanner() {
                                        // Trova il nodo testuale del banner
                                        var marker = null;
                                        var nodes = document.querySelectorAll('div,header,nav,section,span,a');
                                        for (var i = 0; i < nodes.length; i++) {
                                            var el = nodes[i];
                                            var txt = (el.textContent || '');
                                            if (txt.length < 250 &&
                                                txt.indexOf('creata da un utente di Google Apps Script') !== -1) {
                                                marker = el;
                                                break;
                                            }
                                        }
                                        if (!marker) return false;
                                        // Risali al contenitore "barra" (quello largo quanto la pagina
                                        // e alto poche decine di px, ancorato in cima)
                                        var node = marker;
                                        for (var up = 0; up < 6 && node && node.parentElement; up++) {
                                            var r = node.getBoundingClientRect();
                                            // se è una barra in cima a tutta larghezza -> è il banner
                                            if (r.top < 80 && r.height > 0 && r.height < 120 &&
                                                r.width > window.innerWidth * 0.7) {
                                                node.style.setProperty('display','none','important');
                                                document.body.style.setProperty('margin-top','0','important');
                                                document.body.style.setProperty('padding-top','0','important');
                                                return true;
                                            }
                                            node = node.parentElement;
                                        }
                                        // fallback: nascondi solo il marker stesso
                                        marker.style.setProperty('display','none','important');
                                        return true;
                                    }
                                    hideBanner();
                                    setTimeout(hideBanner, 700);
                                    setTimeout(hideBanner, 2000);
                                })();
                            """.trimIndent(), null)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            isLoading = false
                            loadError = description ?: "Errore di caricamento sconosciuto"
                        }
                    }

                    // Zoom nativo: pinch a due dita per zoomare/spostare il contenuto.
                    // displayZoomControls=false toglie i pulsanti +/- a schermo, ma il
                    // pinch gesture e il pan rimangono attivi (gestiti nativamente da WebView).
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportZoom(true)

                        val databasePath = ctx.cacheDir.absolutePath + "/webview_db"
                        databasePath.let { setDatabasePath(it) }

                        cacheMode = if (isNetworkAvailable(ctx)) {
                            WebSettings.LOAD_DEFAULT
                        } else {
                            WebSettings.LOAD_CACHE_ELSE_NETWORK
                        }
                    }

                    onWebViewCreated(this)
                    loadUrl(url)
                }
            },
            update = { webView ->
                if (webView.url != url) {
                    webView.settings.cacheMode = if (isNetworkAvailable(context)) {
                        WebSettings.LOAD_DEFAULT
                    } else {
                        WebSettings.LOAD_CACHE_ELSE_NETWORK
                    }
                    webView.loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                color = Color(0xFFE91E63)
            )
        }

        if (loadError != null && !isNetworkAvailable(context)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color(0xFF222222))
                    .padding(12.dp)
            ) {
                Text(
                    text = "Sei offline. Caricamento della cache locale...",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

private fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/**
 * Confronta l'host dell'URL richiesto con l'host della webapp attiva (initialUrl).
 * Restituisce true se vanno considerati "la stessa webapp" (quindi da NON aprire
 * fuori in Chrome), false se e' un link veramente esterno.
 */
/**
 * Domini Google di login/verifica/account, sempre consentiti DENTRO la WebView,
 * indipendentemente dalle impostazioni dell'utente (login, 2-step verification,
 * gestione account). Non serve configurarli a mano in Settings.
 */
private val GOOGLE_AUTH_HOSTS = listOf(
    "accounts.google.com",
    "accounts.youtube.com",
    "myaccount.google.com",
    "oauthaccountmanager.googleapis.com",
    "content.googleapis.com",
    "apis.google.com"
)

private fun isGoogleAuthHost(requestUrl: String): Boolean {
    val host = try { Uri.parse(requestUrl).host ?: return false } catch (_: Exception) { return false }
    return GOOGLE_AUTH_HOSTS.any { allowed ->
        host.equals(allowed, ignoreCase = true) || host.endsWith(".$allowed", ignoreCase = true)
    }
}

/** Vero se l'host della richiesta corrisponde a una QUALSIASI delle webapp
 *  salvate in Settings (non solo quella attualmente attiva). */
private fun isAnyKnownWebapp(knownUrls: List<String>, requestUrl: String): Boolean =
    knownUrls.any { isSameWebapp(it, requestUrl) }

/**
 * Controlla se l'host della richiesta corrisponde al dominio extra configurato
 * in Settings (es. "accounts.google.com" per la verifica/login Google). Accetta
 * sia un URL completo che un semplice dominio, e ammette anche i sottodomini
 * (utile perche' il login Google puo' toccare piu' host della stessa famiglia).
 */
private fun isExtraAllowedHost(extraAllowed: String, requestUrl: String): Boolean {
    if (extraAllowed.isBlank()) return false
    return try {
        val requestHost = Uri.parse(requestUrl).host ?: return false
        val allowedHost = if (extraAllowed.contains("://")) {
            Uri.parse(extraAllowed).host ?: extraAllowed.trim()
        } else {
            extraAllowed.trim().trimEnd('/')
        }
        if (allowedHost.isBlank()) return false
        requestHost.equals(allowedHost, ignoreCase = true) ||
            requestHost.endsWith(".$allowedHost", ignoreCase = true)
    } catch (_: Exception) {
        false
    }
}

private fun isSameWebapp(initialUrl: String, requestUrl: String): Boolean {
    return try {
        val initial = Uri.parse(initialUrl)
        val request = Uri.parse(requestUrl)
        val initialHost = initial.host ?: return false
        val requestHost = request.host ?: return false

        // Il rendering della webapp vive in un iframe *.googleusercontent.com: sempre dentro.
        if (requestHost.endsWith(".googleusercontent.com", ignoreCase = true)) return true

        // Solo la webapp madre (stesso deploy id) resta dentro.
        // Altre webapp script.google.com ed editor /d/.../edit -> esterno (Chrome).
        if (requestHost.equals("script.google.com", ignoreCase = true) &&
            initialHost.equals("script.google.com", ignoreCase = true)) {
            val dep = deployId(initial.path)
            return dep != null && dep == deployId(request.path)
        }

        // Host non-GAS identico -> dentro.
        requestHost.equals(initialHost, ignoreCase = true)
    } catch (_: Exception) {
        false
    }
}

private fun deployId(path: String?): String? {
    if (path == null) return null
    return Regex("/macros/s/([^/]+)").find(path)?.groupValues?.get(1)
}
