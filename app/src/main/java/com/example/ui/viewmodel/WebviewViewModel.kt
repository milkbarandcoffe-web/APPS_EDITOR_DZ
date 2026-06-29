package com.example.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.config.ConfigCrypto
import com.example.data.database.AppDatabase
import com.example.data.database.AppSettingEntity
import com.example.data.database.UserEntity
import com.example.data.repository.AppRepository
import com.example.services.FloatingBubbleService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UiState(
    val settings: AppSettingEntity = AppSettingEntity(),
    val users: List<UserEntity> = emptyList(),
    val showSettings: Boolean = false,
    val isXVisible: Boolean = true,
    val isBubbleServiceRunning: Boolean = false,
    val toastMessage: String? = null
)

class WebviewViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private var xHideJob: Job? = null

    init {
        val dao = AppDatabase.getDatabase(application).appDao()
        repository = AppRepository(dao)
        viewModelScope.launch {
            repository.checkAndInitializeDefaults()
            repository.settings.filterNotNull().collect { s ->
                _uiState.update { it.copy(settings = s) }
            }
        }
        viewModelScope.launch {
            repository.allUsers.collect { list ->
                _uiState.update { it.copy(users = list) }
            }
        }
    }

    /** URL da passare al WebViewer: dev per owner, exec per guest. */
    fun viewerUrl(): String = repository.viewerUrl(_uiState.value.settings)

    /** Tap X: apre/chiude Settings. */
    fun toggleSettings(context: Context) {
        val opening = !_uiState.value.showSettings
        _uiState.update { it.copy(showSettings = opening) }
        if (_uiState.value.isBubbleServiceRunning) {
            val action = if (opening) FloatingBubbleService.ACTION_HIDE
                         else FloatingBubbleService.ACTION_SHOW
            context.startService(Intent(context, FloatingBubbleService::class.java)
                .apply { this.action = action })
        }
    }

    /** Doppio tap X: avvia/ferma la bolla. */
    fun toggleBubbleService(context: Context) {
        val running = _uiState.value.isBubbleServiceRunning
        if (running) {
            context.stopService(Intent(context, FloatingBubbleService::class.java))
            _uiState.update { it.copy(isBubbleServiceRunning = false) }
            showToast("Bolla disattivata")
        } else {
            context.startForegroundService(Intent(context, FloatingBubbleService::class.java))
            _uiState.update { it.copy(isBubbleServiceRunning = true) }
            showToast("Bolla attivata")
        }
    }

    fun hideXFor10Seconds() {
        xHideJob?.cancel()
        xHideJob = viewModelScope.launch {
            _uiState.update { it.copy(isXVisible = false) }
            delay(10_000)
            _uiState.update { it.copy(isXVisible = true) }
        }
    }

    fun updateSettings(updated: AppSettingEntity) {
        viewModelScope.launch { repository.saveSettings(updated) }
    }

    /**
     * Importa un config DZCFG1: decifra e salva tutto (exec, dev, token,
     * email, key, flag owner). Il viewer si adatta automaticamente.
     */
    fun importConfig(payload: String) {
        if (payload.isBlank()) { showToast("Incolla il config"); return }
        val cfg = ConfigCrypto.decryptFull(payload.trim())
        if (cfg == null) { showToast("Config non valido"); return }
        viewModelScope.launch {
            val cur = repository.settings.firstOrNull() ?: AppSettingEntity()
            repository.saveSettings(cur.copy(
                bridgeExec  = cfg.exec,
                bridgeDev   = cfg.dev,
                bridgeToken = cfg.token,
                guestEmail  = cfg.email,
                guestKey    = cfg.key,
                isOwner     = cfg.owner,
                bubbleText  = "SEND"
            ))
            showToast("Config importata ✓")
        }
    }

    /**
     * Recupero owner manuale (pannello 4-tap in Settings): imposta solo l'exec
     * e il flag owner. Tiene il token gia' salvato (costante tra le pubblicazioni);
     * se assente usa quello noto del progetto. Salva exec PULITO (senza query):
     * il token viene aggiunto al volo da viewerUrl/sendPost.
     */
    fun setOwnerManual(ownerLink: String) {
        val raw = ownerLink.trim()
        val base = raw.substringBefore("?").trimEnd('/')
        if (base.isBlank()) { showToast("Inserisci il link owner"); return }
        val q = if (raw.contains("?")) raw.substringAfter("?") else ""
        fun param(k: String): String {
            for (p in q.split("&")) {
                val i = p.indexOf("=")
                if (i > 0 && p.substring(0, i) == k)
                    return try { java.net.URLDecoder.decode(p.substring(i + 1), "UTF-8") } catch (e: Exception) { p.substring(i + 1) }
            }
            return ""
        }
        val okey = param("gkey")
        val who  = param("who")
        if (okey.isBlank()) { showToast("Link senza gkey (okey)"); return }
        viewModelScope.launch {
            val cur = repository.settings.firstOrNull() ?: AppSettingEntity()
            val tok = param("token").ifBlank { cur.bridgeToken.ifBlank { "356186c3-95bb-4ed9-9790-847a68f0305c" } }
            repository.saveSettings(cur.copy(
                bridgeExec  = base,
                bridgeDev   = "",
                bridgeToken = tok,
                guestEmail  = who,
                guestKey    = okey,
                isOwner     = true,
                bubbleText  = "SEND"
            ))
            showToast("Owner impostato ✓")
        }
    }

    fun copyToClipboard(text: String) {
        val cm = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("cfg", text))
    }

    fun clearToast() = _uiState.update { it.copy(toastMessage = null) }
    private fun showToast(msg: String) = _uiState.update { it.copy(toastMessage = msg) }
}
