package com.example.data.config

import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object ConfigCrypto {
    private const val PASSPHRASE = "Dz-BollaViewer-2025-cfg-key!"

    private fun keyBytes(): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(PASSPHRASE.toByteArray(Charsets.UTF_8))

    /**
     * Config cifrato: exec (bolla + viewer guest), dev (viewer owner),
     * token (bridge), email, key (guest key, vuota se owner), owner (flag).
     */
    fun encrypt(exec: String, dev: String = "", token: String,
                email: String = "", key: String = "", owner: Boolean = false): String {
        val json = JSONObject().apply {
            put("exec", exec); put("dev", dev); put("token", token)
            put("email", email); put("key", key); put("owner", owner)
        }.toString()
        val iv = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes(), "AES"), IvParameterSpec(iv))
        return "DZCFG1:" + Base64.encodeToString(iv + cipher.doFinal(json.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    data class Cfg(val exec: String, val dev: String, val token: String,
                   val email: String, val key: String, val owner: Boolean)

    fun decryptFull(payload: String): Cfg? = try {
        val clean = payload.trim().removePrefix("DZCFG1:").trim()
        val combined = Base64.decode(clean, Base64.NO_WRAP)
        if (combined.size < 17) null else {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes(), "AES"),
                IvParameterSpec(combined.copyOfRange(0, 16)))
            val o = JSONObject(String(cipher.doFinal(combined.copyOfRange(16, combined.size)), Charsets.UTF_8))
            val exec = o.optString("exec", "")
            if (exec.isBlank()) null
            else Cfg(exec, o.optString("dev",""), o.optString("token",""),
                     o.optString("email",""), o.optString("key",""), o.optBoolean("owner",false))
        }
    } catch (e: Exception) { null }
}
