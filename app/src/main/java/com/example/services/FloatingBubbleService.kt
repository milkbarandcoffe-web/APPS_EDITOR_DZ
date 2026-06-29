package com.example.services

import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.widget.TextView
import android.widget.Toast
import com.example.MainActivity
import com.example.data.database.AppDatabase
import com.example.data.database.AppSettingEntity
import com.example.data.repository.AppRepository
import kotlinx.coroutines.*

class FloatingBubbleService : Service() {

    companion object {
        const val ACTION_HIDE = "com.example.bubble.HIDE"
        const val ACTION_SHOW = "com.example.bubble.SHOW"
    }

    private var windowManager: WindowManager? = null
    private var bubbleView: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    // Stato "modalita' errore": se true, la bolla mostra il testo dell'errore
    // allargata e resta cosi' finche' l'utente non tocca di nuovo (vedi onTap).
    private var errorMode = false
    private var errorText = ""

    // Ultime impostazioni note, usate per ripristinare l'aspetto normale
    // della bolla dopo l'errore senza dover aspettare una nuova query DB.
    private var lastSettings: AppSettingEntity = AppSettingEntity()

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var repository: AppRepository

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        repository = AppRepository(AppDatabase.getDatabase(this).appDao())
        ioScope.launch {
            val settings = getSettings()
            lastSettings = settings
            handler.post { showBubble(settings) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> handler.post { bubbleView?.visibility = View.GONE }
            ACTION_SHOW -> handler.post { bubbleView?.visibility = View.VISIBLE }
        }
        return START_STICKY
    }

    private suspend fun getSettings(): AppSettingEntity =
        AppDatabase.getDatabase(this@FloatingBubbleService).appDao()
            .getSettingsDirect() ?: AppSettingEntity()

    private fun showBubble(settings: AppSettingEntity) {
        val tv = TextView(this)
        applyStyle(tv, settings)

        val size = dp(settings.bubbleSize)
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80; y = 400
        }

        tv.setOnTouchListener(BubbleTouchListener(
            params = params,
            onDrag = { windowManager?.updateViewLayout(tv, params) },
            onClick = { onTap(tv) }
        ))

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager?.addView(tv, params)
        bubbleView = tv
        bubbleParams = params
    }

    /** Stile normale (quadrato/rotondo, dimensione e colore da Settings). */
    private fun applyStyle(tv: TextView, s: AppSettingEntity) {
        tv.setPadding(0, 0, 0, 0)
        tv.maxWidth = Int.MAX_VALUE
        tv.text = s.bubbleText
        tv.setTextColor(Color.WHITE)
        tv.textSize = s.bubbleSize * 0.28f
        tv.gravity = Gravity.CENTER
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.alpha = s.bubbleOpacity
        val bg = GradientDrawable().apply { shape = GradientDrawable.OVAL }
        bg.setColor(try { Color.parseColor(s.bubbleColorHex) }
                    catch (e: Exception) { Color.parseColor("#FF6200EE") })
        tv.background = bg
    }

    /**
     * Su Android 10+ (API 29) la clipboard e' leggibile SOLO se l'app ha il
     * focus della finestra. Un foreground service NON ha focus -> di default
     * legge una clipboard vuota in background.
     *
     * Soluzione (come i clipboard manager): rendere la finestra overlay
     * temporaneamente FOCUSABLE. Quando l'overlay prende il focus, l'app ha
     * focus e puo' leggere la clipboard. Subito dopo si rimuove il focus.
     */
    private fun onTap(tv: TextView) {
        // Se siamo in modalita' errore: il tap copia l'errore in clipboard
        // e ripristina la bolla allo stato normale. Non fa l'invio.
        if (errorMode) {
            copyToClipboard(errorText)
            toast("Errore copiato negli appunti")
            resetBubbleFromError(tv)
            return
        }

        val wm = windowManager ?: return
        val params = bubbleParams ?: return

        // 1. Rendi la finestra focusable (rimuove FLAG_NOT_FOCUSABLE)
        params.flags = params.flags and
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        try { wm.updateViewLayout(tv, params) } catch (_: Exception) {}

        // 2. Dopo che il focus si e' assestato, leggi clipboard e procedi
        handler.postDelayed({
            val text = readClipboard()

            // 3. Ripristina non-focusable (l'overlay non deve rubare input)
            params.flags = params.flags or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            try { wm.updateViewLayout(tv, params) } catch (_: Exception) {}

            if (text.isEmpty()) {
                toast("Clipboard vuota")
                return@postDelayed
            }
            doSend(tv, text)
        }, 250)
    }

    private fun readClipboard(): String {
        return try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(this).toString().trim()
            } else ""
        } catch (e: Exception) { "" }
    }

    private fun copyToClipboard(text: String) {
        try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("errore_bolla", text))
        } catch (_: Exception) {}
    }

    private fun doSend(tv: TextView, text: String) {
        ioScope.launch {
            val settings = getSettings()
            lastSettings = settings
            val origText = settings.bubbleText

            handler.post { tv.text = "..." }

            val result = repository.sendPost(applicationContext, text, settings)

            if (result.ok) {
                handler.post {
                    tv.text = "OK"
                    (tv.background as? GradientDrawable)?.setColor(Color.parseColor("#FF34a853"))
                }
                toast("Inviato")

                delay(3000)
                val freshSettings = getSettings()
                lastSettings = freshSettings
                handler.post {
                    tv.text = origText
                    applyStyle(tv, freshSettings)
                }
            } else {
                val msg = result.message.ifEmpty { "Errore sconosciuto" }
                handler.post { showErrorBubble(tv, msg) }
                toast("Err: $msg")
            }
        }
    }

    /**
     * Allarga la bolla (width WRAP_CONTENT, forma rounded) e mostra il testo
     * dell'errore. Resta cosi' finche' l'utente non tocca di nuovo (vedi onTap).
     */
    private fun showErrorBubble(tv: TextView, message: String) {
        val wm = windowManager ?: return
        val params = bubbleParams ?: return

        errorMode = true
        errorText = message

        tv.maxWidth = dp(240)
        tv.setPadding(dp(14), dp(10), dp(14), dp(10))
        tv.text = message
        tv.textSize = 12f
        tv.gravity = Gravity.CENTER
        tv.setTextColor(Color.WHITE)
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.alpha = 1f
        val bg = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(Color.parseColor("#FFea4335"))
        }
        tv.background = bg

        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        try { wm.updateViewLayout(tv, params) } catch (_: Exception) {}
    }

    /** Torna allo stato normale (attivo) dopo che l'errore e' stato copiato. */
    private fun resetBubbleFromError(tv: TextView) {
        val wm = windowManager ?: return
        val params = bubbleParams ?: return

        errorMode = false
        errorText = ""

        val size = dp(lastSettings.bubbleSize)
        params.width = size
        params.height = size
        try { wm.updateViewLayout(tv, params) } catch (_: Exception) {}

        applyStyle(tv, lastSettings)
    }

    private fun startForegroundNotification() {
        val chId = "bolla_ch"
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(
                NotificationChannel(chId, "Bolla", NotificationManager.IMPORTANCE_LOW)
            )
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        startForeground(1337,
            Notification.Builder(this, chId)
                .setContentTitle("Bolla attiva")
                .setContentText("Tap per inviare clipboard")
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentIntent(pi)
                .setOngoing(true)
                .build()
        )
    }

    private fun toast(msg: String) =
        handler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
        bubbleView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
    }
}

class BubbleTouchListener(
    private val params: WindowManager.LayoutParams,
    private val onDrag: () -> Unit,
    private val onClick: () -> Unit
) : View.OnTouchListener {
    private var sx = 0; private var sy = 0
    private var rx = 0f; private var ry = 0f
    private var moved = false; private var t0 = 0L

    override fun onTouch(v: View, e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                sx = params.x; sy = params.y
                rx = e.rawX; ry = e.rawY
                moved = false; t0 = System.currentTimeMillis()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.rawX - rx).toInt()
                val dy = (e.rawY - ry).toInt()
                if (Math.abs(dx) > 8 || Math.abs(dy) > 8) moved = true
                if (moved) { params.x = sx + dx; params.y = sy + dy; onDrag() }
            }
            MotionEvent.ACTION_UP -> {
                if (!moved && System.currentTimeMillis() - t0 < 400) onClick()
            }
        }
        return true
    }
}
