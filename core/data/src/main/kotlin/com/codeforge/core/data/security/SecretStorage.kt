// Modul: :core:data
package com.codeforge.core.data.security

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Minimaler Key-Value-Speicher (SharedPreferences in Produktion, Map in Tests). */
internal interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

internal class SharedPreferencesStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()
}

internal interface SecretBox {
    fun encrypt(plain: String): String

    /** `null`, wenn nicht entschlüsselbar (z. B. Schlüssel nach Gerätewechsel/Reset ungültig). */
    fun decrypt(encoded: String): String?
}

/** AES-256-GCM mit nicht-exportierbarem Schlüssel im Android Keystore. Format: Base64(IV[12] + Ciphertext). */
internal class KeystoreSecretBox(private val alias: String = "codeforge_git_secret_key") : SecretBox {

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    override fun decrypt(encoded: String): String? = runCatching {
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, IV_SIZE)))
        String(cipher.doFinal(raw, IV_SIZE, raw.size - IV_SIZE), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
