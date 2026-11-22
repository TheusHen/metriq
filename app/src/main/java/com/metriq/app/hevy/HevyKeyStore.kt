package com.metriq.app.hevy

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the Hevy API key in EncryptedSharedPreferences backed by a Keystore MasterKey.
 * The key is never logged and blank values are treated as absent.
 */
object HevyKeyStore {
    private const val PREFS_NAME = "hevy_secure_prefs"
    private const val KEY_API = "hevy_api_key"

    // Building a MasterKey is expensive; reuse the opened preferences.
    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences? {
        cachedPrefs?.let { return it }
        return synchronized(this) {
            cachedPrefs ?: runCatching {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrNull().also { cachedPrefs = it }
        }
    }

    /** Returns false when the keystore is unavailable, so the caller can tell the user. */
    fun save(context: Context, apiKey: String): Boolean {
        val value = apiKey.trim()
        if (value.isEmpty()) {
            return clear(context)
        }
        return runCatching {
            prefs(context)?.edit()?.putString(KEY_API, value)?.commit() ?: false
        }.getOrDefault(false)
    }

    fun read(context: Context): String? {
        val value = runCatching {
            prefs(context)?.getString(KEY_API, null)
        }.getOrNull()
        return value?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun clear(context: Context): Boolean = runCatching {
        prefs(context)?.edit()?.remove(KEY_API)?.commit() ?: false
    }.getOrDefault(false)
}
