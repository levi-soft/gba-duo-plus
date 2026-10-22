package com.khumam.gbaduo.achievements

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores only the successful login token, encrypted by a device-local Android Keystore key. */
class AchievementCredentials(context: Context) {
    private val prefs = context.getSharedPreferences("retroachievements", Context.MODE_PRIVATE)
    private val alias = "gbaduo-retroachievements-session"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(username: String, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val secret = JSONObject().put("username", username).put("token", token).toString().toByteArray(Charsets.UTF_8)
        val encrypted = cipher.doFinal(secret)
        secret.fill(0)
        prefs.edit().putString("session", Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)).apply()
    }
    fun load(): Pair<String, String>? {
        val encoded = prefs.getString("session", null) ?: return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
            val clear = cipher.doFinal(bytes, 12, bytes.size - 12)
            try { JSONObject(clear.toString(Charsets.UTF_8)).let { it.getString("username") to it.getString("token") } }
            finally { clear.fill(0) }
        }.getOrElse { clear(); null }
    }
    fun clear() { prefs.edit().clear().apply() }
}
