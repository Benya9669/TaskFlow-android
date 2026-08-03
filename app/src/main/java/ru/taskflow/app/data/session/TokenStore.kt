package ru.taskflow.app.data.session

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class SessionTokens(val accessToken: String, val refreshToken: String)

class TokenStore(context: Context) {
    private val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "taskflow_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun read(): SessionTokens? {
        val access = preferences.getString(ACCESS_TOKEN, null)
        val refresh = preferences.getString(REFRESH_TOKEN, null)
        return if (access.isNullOrBlank() || refresh.isNullOrBlank()) null else SessionTokens(access, refresh)
    }

    fun save(tokens: SessionTokens) = preferences.edit().putString(ACCESS_TOKEN, tokens.accessToken).putString(REFRESH_TOKEN, tokens.refreshToken).apply()
    fun serverUrl(): String? = preferences.getString(SERVER_URL, null)
    fun saveServerUrl(url: String) = preferences.edit().putString(SERVER_URL, url.trimEnd('/') + "/").apply()
    fun clear() = preferences.edit().clear().apply()

    private companion object {
        const val ACCESS_TOKEN = "access_token"
        const val REFRESH_TOKEN = "refresh_token"
        const val SERVER_URL = "server_url"
    }
}
