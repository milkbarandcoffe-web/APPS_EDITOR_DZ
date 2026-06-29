package com.example.data.repository

import android.content.Context
import com.example.data.database.AppDao
import com.example.data.database.AppSettingEntity
import com.example.data.database.UserEntity
import com.example.data.network.NetworkPoster
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

data class PostResult(val ok: Boolean, val message: String)

class AppRepository(private val appDao: AppDao) {

    val allUsers: Flow<List<UserEntity>> = appDao.getAllUsers()
    val settings: Flow<AppSettingEntity?> = appDao.getSettingsFlow()

    suspend fun checkAndInitializeDefaults() {
        if (appDao.getSettingsDirect() == null) appDao.saveSettings(AppSettingEntity())
    }

    suspend fun saveSettings(s: AppSettingEntity) = appDao.saveSettings(s)
    suspend fun insertUser(u: UserEntity) = appDao.insertUser(u)
    suspend fun deleteUser(u: UserEntity) = appDao.deleteUser(u)

    /**
     * URL da aprire nel WebViewer (solo /exec; /dev non si usa piu':
     * in WebView anonima chiederebbe login Google).
     * - guest → exec pulito (doGet senza token serve la pagina guest)
     * - owner → exec + "?token=..." (doGet col token serve la pagina owner)
     * Il token viene aggiunto qui, NON salvato dentro bridgeExec, cosi' la
     * bolla (sendPost) continua ad accodare il token su un exec pulito.
     */
    fun viewerUrl(settings: AppSettingEntity): String {
        val exec = settings.bridgeExec.trim()
        if (exec.isBlank() || !settings.isOwner) return exec
        if (exec.contains("okey=")) return exec
        val okey = settings.guestKey.trim()
        if (okey.isBlank()) return exec
        val sep = if (exec.contains("?")) "&" else "?"
        return exec + sep + "okey=" + enc(okey)
    }

    /**
     * Bolla POST: sempre su exec (richiede deployment pubblicato, stabile).
     */
    fun sendPost(context: Context, clipboardText: String, settings: AppSettingEntity): PostResult {
        if (clipboardText.isBlank()) return PostResult(false, "Clipboard vuota")
        val exec = settings.bridgeExec.trim()
        val token = settings.bridgeToken.trim()
        if (exec.isBlank()) return PostResult(false, "Config mancante: incolla il config ricevuto")

        val sb = StringBuilder(exec)
        sb.append("?token=").append(enc(token))
        if (settings.guestEmail.isNotBlank()) sb.append("&who=").append(enc(settings.guestEmail))
        if (settings.guestKey.isNotBlank())   sb.append("&gkey=").append(enc(settings.guestKey))

        val result = NetworkPoster.post(context, sb.toString(), clipboardText)
        val body = result.body
        return when {
            body.contains("\"ok\":true")       -> PostResult(true, "OK")
            else -> {
                val err = extractJsonString(body, "error")
                PostResult(false, err.ifBlank { result.error.ifBlank { "HTTP ${result.httpCode}" } })
            }
        }
    }

    private fun extractJsonString(json: String, key: String): String {
        val prefix = "\"$key\":\""
        val i = json.indexOf(prefix); if (i < 0) return ""
        val start = i + prefix.length
        val end = json.indexOf("\"", start)
        return if (end > start) json.substring(start, end) else ""
    }

    private fun enc(s: String): String =
        try { java.net.URLEncoder.encode(s, "UTF-8") } catch (e: Exception) { s }
}
