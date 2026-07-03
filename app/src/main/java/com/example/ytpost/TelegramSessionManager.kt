package com.example.ytpost

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TelegramSessionManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "telegram_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveTelegramCredentials(apiId: String, apiHash: String, phoneNumber: String) {
        sharedPreferences.edit().apply {
            putString("api_id", apiId)
            putString("api_hash", apiHash)
            putString("phone_number", phoneNumber)
            apply()
        }
    }

    fun getApiId(): String? = sharedPreferences.getString("api_id", null)
    fun getApiHash(): String? = sharedPreferences.getString("api_hash", null)
    fun getPhoneNumber(): String? = sharedPreferences.getString("phone_number", null)

    fun saveSessionString(session: String) {
        sharedPreferences.edit().putString("session_string", session).apply()
    }

    fun getSessionString(): String? = sharedPreferences.getString("session_string", null)

    fun isConfigured(): Boolean {
        return getApiId() != null && getApiHash() != null && getPhoneNumber() != null
    }
}
