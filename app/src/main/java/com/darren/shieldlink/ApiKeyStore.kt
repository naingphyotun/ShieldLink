package com.darren.shieldlink

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the user's own VirusTotal API key on the device.
 *
 * The key is encrypted with an AES-GCM key that lives in the Android Keystore,
 * so the stored text is useless without this app on this phone.
 * The key is never logged and never sent anywhere except VirusTotal.
 */
object ApiKeyStore {

    private const val PREFS = "shieldlink_secure"
    private const val PREF_BLOB = "vt_key_blob"
    private const val KEY_ALIAS = "shieldlink_vt_key_wrap"
    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** True if a key has been saved. */
    fun hasKey(context: Context): Boolean = get(context) != null

    /** The saved key, or null if none is saved or it cannot be decrypted. */
    fun get(context: Context): String? {
        val blob = prefs(context).getString(PREF_BLOB, null) ?: return null
        return try {
            val parts = blob.split(":")
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val data = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            null   // e.g. restored to a new phone: the Keystore key is gone, ask for the key again
        }
    }

    /** Saves the key (trimmed). */
    fun save(context: Context, key: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val data = Base64.encodeToString(cipher.doFinal(key.trim().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        prefs(context).edit().putString(PREF_BLOB, "$iv:$data").apply()
    }

    /** Removes the saved key. */
    fun clear(context: Context) {
        prefs(context).edit().remove(PREF_BLOB).apply()
    }

    /** Shows only the first and last 4 characters, e.g. "a1b2••••••••c3d4". */
    fun mask(key: String): String =
        if (key.length <= 8) "••••••••" else key.take(4) + "••••••••" + key.takeLast(4)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}