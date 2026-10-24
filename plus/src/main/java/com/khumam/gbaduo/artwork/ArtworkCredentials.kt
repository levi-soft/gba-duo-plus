package com.khumam.gbaduo.artwork

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

/** Device-local encrypted credentials. Keystore alias and preferences are separate from RA. */
class ArtworkCredentials(context: Context) {
    private val preferences = context.getSharedPreferences("plus-artwork-credentials", Context.MODE_PRIVATE)
    private val alias = "gbaduo-plus-artwork-sources"

    @Synchronized fun steamGridDbKey(): String = read().optString("steamKey")
    @Synchronized fun saveSteamGridDbKey(value: String) {
        require(value.length <= 1024 && value.none { it == '\r' || it == '\n' }) { "Invalid API key." }
        write(JSONObject().put("steamKey", value.trim()))
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun read(): JSONObject {
        val encoded = preferences.getString("sources", null) ?: return JSONObject()
        return runCatching {
            val data = Base64.decode(encoded, Base64.NO_WRAP)
            require(data.size in 29..16384)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12))) }
            val clear = cipher.doFinal(data, 12, data.size - 12)
            try { JSONObject(clear.toString(Charsets.UTF_8)) } finally { clear.fill(0) }
        }.getOrElse { JSONObject() }
    }

    private fun write(value: JSONObject) {
        val clear = value.toString().toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val encrypted = Base64.encodeToString(cipher.iv + cipher.doFinal(clear), Base64.NO_WRAP)
            if (!preferences.edit().putString("sources", encrypted).commit()) throw IllegalStateException("Could not save artwork credentials.")
        } catch (_: Exception) { throw IllegalStateException("Could not save artwork credentials securely.") }
        finally { clear.fill(0) }
    }
}
