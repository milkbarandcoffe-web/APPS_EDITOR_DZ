package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.SettingsScreen
import com.example.ui.components.VerticalWebView
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.WebviewViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Fullscreen reale: il contenuto non lascia spazio per le barre di sistema
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        setContent { MyApplicationTheme { MainAppContainer() } }
    }

    /**
     * Nasconde status bar e navigation bar (immersive). L'utente puo' farle
     * riapparire temporaneamente con uno swipe dal bordo (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE),
     * dopodiche' si nascondono di nuovo da sole.
     */
    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Se il sistema le ha temporaneamente mostrate (es. dopo uno swipe o un dialog),
        // le nascondiamo di nuovo quando la finestra riprende il focus.
        if (hasFocus) hideSystemBars()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainAppContainer() {
    val viewModel: WebviewViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Sostituisce il vecchio swipe-edge gesture (rimosso perche' in conflitto
    // col pinch-zoom/pan a due dita nativo della WebView): il tasto/gesture
    // back di sistema torna indietro nella history della WebView; se non
    // c'e' history, manda l'app in background (comportamento standard Android).
    BackHandler(enabled = webViewRef != null) {
        val wv = webViewRef
        if (wv?.canGoBack() == true) {
            wv.goBack()
        } else {
            (context as? ComponentActivity)?.moveTaskToBack(true)
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    LaunchedEffect(Unit) {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context,
                "Vai in Impostazioni > Permessi > Mostra sopra altre app",
                Toast.LENGTH_LONG).show()
        }
        // Chiede esenzione da Doze/battery optimization — come fa MacroDroid
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            try {
                context.startActivity(Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                ))
            } catch (_: Exception) {}
        }
    }

    val activeUrl = viewModel.viewerUrl().ifBlank { "about:blank" }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars
    ) { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) {

            if (!uiState.showSettings) {
                VerticalWebView(
                    url = activeUrl,
                    extraAllowedHost = uiState.settings.verificationUrl,
                    modifier = Modifier.fillMaxSize(),
                    onWebViewCreated = { webViewRef = it }
                )
            } else {
                SettingsScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                    onLogoutGoogle = {
                        val wv = webViewRef
                        if (wv == null) {
                            Toast.makeText(context, "WebView non pronta", Toast.LENGTH_SHORT).show()
                        } else {
                            // Naviga sull'endpoint di logout reale di Google: scatena gli
                            // Set-Cookie che invalidano la sessione (non basta cancellare
                            // i cookie in locale, serve il round-trip col server Google).
                            wv.loadUrl("https://accounts.google.com/Logout")
                            Toast.makeText(context, "Disconnesso da Google", Toast.LENGTH_SHORT).show()
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                android.webkit.CookieManager.getInstance().flush()
                                wv.loadUrl(activeUrl)
                            }, 2000)
                        }
                    }
                )
            }

            // X: sempre visibile quando isXVisible, anche dentro Settings
            if (uiState.isXVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp)
                        .size(35.dp)
                        .clip(CircleShape)
                        .border(
                            2.dp,
                            if (uiState.isBubbleServiceRunning)
                                (runCatching { Color(android.graphics.Color.parseColor(uiState.settings.bubbleColorHex)) }
                                    .getOrDefault(Color(0xFF34a853)))
                                    .copy(alpha = uiState.settings.xOpacity.coerceAtLeast(0.4f))
                            else
                                Color.White.copy(alpha = uiState.settings.xOpacity.coerceAtLeast(0.3f)),
                            CircleShape
                        )
                        .combinedClickable(
                            onClick = {
                                viewModel.toggleSettings(context)
                            },
                            onDoubleClick = {
                                if (!Settings.canDrawOverlays(context)) {
                                    Toast.makeText(context,
                                        "Permesso overlay non concesso. Vai in Settings.",
                                        Toast.LENGTH_LONG).show()
                                    context.startActivity(Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                                } else {
                                    viewModel.toggleBubbleService(context)
                                }
                            },
                            onLongClick = {
                                viewModel.hideXFor10Seconds()
                            }
                        )
                )
            }


        }
    }
}
