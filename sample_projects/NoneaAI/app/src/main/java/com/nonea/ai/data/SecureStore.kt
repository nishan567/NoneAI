package com.nonea.ai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.nonea.ai.ai.Redact
import com.nonea.ai.ai.SafeLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Private storage for secrets (Gemini key, proxy token). Values are AES-256-GCM encrypted with a key
 * that lives in the Android Keystore and never leaves it; the ciphertext sits in app-private
 * SharedPreferences, which is excluded from backups. If the Keystore is unusable the secret is kept
 * in memory for the session only; it is never written to disk in plain text.
 */
class SecureStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _geminiKey = MutableStateFlow(load(K_GEMINI))
    private val _proxyToken = MutableStateFlow(load(K_PROXY))
    val geminiKey: StateFlow<String> = _geminiKey.asStateFlow()
    val proxyToken: StateFlow<String> = _proxyToken.asStateFlow()

    @Volatile var persistent = true
        private set

    fun setGeminiKey(v: String) { _geminiKey.value = v; Redact.register(v); store(K_GEMINI, v) }
    fun setProxyToken(v: String) { _proxyToken.value = v; Redact.register(v); store(K_PROXY, v) }

    private fun load(name: String): String {
        val enc = prefs.getString(name, null) ?: return ""
        return try {
            decrypt(enc).also { Redact.register(it) }
        } catch (e: Exception) {
            // Keystore key lost (restore to new phone, lock-screen reset on some OEMs): drop the unreadable blob.
            SafeLog.w(TAG, "could not decrypt stored secret '$name'", e)
            prefs.edit().remove(name).apply()
            ""
        }
    }

    private fun store(name: String, value: String) {
        if (value.isEmpty()) { prefs.edit().remove(name).apply(); return }
        try {
            prefs.edit().putString(name, encrypt(value)).apply()
        } catch (e: Exception) {
            persistent = false
            SafeLog.w(TAG, "could not persist secret '$name'; keeping it in memory only", e)
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val all = Base64.decode(encoded, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        return String(c.doFinal(all.copyOfRange(12, all.size)), Charsets.UTF_8)
    }

    companion object {
        private const val TAG = "SecureStore"
        private const val FILE = "nonea_secure"          // keep in sync with res/xml backup rules
        private const val ALIAS = "nonea_secrets_v1"
        private const val K_GEMINI = "gemini_key"
        private const val K_PROXY = "proxy_token"
        @Volatile private var inst: SecureStore? = null
        fun get(ctx: Context): SecureStore = inst ?: synchronized(this) { inst ?: SecureStore(ctx).also { inst = it } }
    }
}
