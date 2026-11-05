package com.hostytalk.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hostytalk.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "hostytalk")

class Prefs(private val context: Context) {
    private val tokenKey = stringPreferencesKey("token")
    private val serverKey = stringPreferencesKey("server_url")
    private val merchantLangKey = stringPreferencesKey("merchant_lang")

    val token: Flow<String?> = context.dataStore.data.map { it[tokenKey] }
    val serverUrl: Flow<String> = context.dataStore.data.map { it[serverKey] ?: BuildConfig.API_BASE_URL }
    val merchantLang: Flow<String> = context.dataStore.data.map { it[merchantLangKey] ?: "tr" }

    suspend fun currentToken(): String? = token.first()
    suspend fun currentServerUrl(): String = serverUrl.first()

    suspend fun setToken(value: String?) = context.dataStore.edit {
        if (value == null) it.remove(tokenKey) else it[tokenKey] = value
    }

    suspend fun setServerUrl(value: String) = context.dataStore.edit { it[serverKey] = value.trim().trimEnd('/') }

    suspend fun setMerchantLang(value: String) = context.dataStore.edit { it[merchantLangKey] = value }
}
