package com.booknext.app.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountPrefs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val API_KEY = stringPreferencesKey("api_key")
        private val HAS_SEEN_WELCOME_KEY = booleanPreferencesKey("has_seen_welcome")
        private val GOOGLE_BOOKS_API_KEY = stringPreferencesKey("google_books_api_key")
        private val DIRECT_UPLOAD_URL = stringPreferencesKey("direct_upload_url")
        private val LOGIN_HISTORY = stringPreferencesKey("login_history")
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { it[SERVER_URL] ?: "" }
    val apiKey: Flow<String> = context.dataStore.data.map { it[API_KEY] ?: "" }
    val hasSeenWelcome: Flow<Boolean> = context.dataStore.data.map { it[HAS_SEEN_WELCOME_KEY] ?: false }
    val googleBooksApiKey: Flow<String> = context.dataStore.data.map { it[GOOGLE_BOOKS_API_KEY] ?: "" }
    val directUploadUrl: Flow<String> = context.dataStore.data.map { it[DIRECT_UPLOAD_URL] ?: "" }

    /**
     * 登录历史记录（最近使用的服务器地址列表），最多 10 条
     */
    val loginHistory: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val json = prefs[LOGIN_HISTORY] ?: return@map emptyList()
        try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(json, type)
        } catch (_: Exception) { emptyList() }
    }

    suspend fun addLoginUrl(url: String) {
        val current = context.dataStore.data.first()[LOGIN_HISTORY] ?: "[]"
        val type = object : TypeToken<List<String>>() {}.type
        val list = try { Gson().fromJson<List<String>>(current, type).toMutableList() } catch (_: Exception) { mutableListOf() }
        list.remove(url)
        list.add(0, url)
        val trimmed = list.take(10)
        context.dataStore.edit { it[LOGIN_HISTORY] = Gson().toJson(trimmed) }
    }

    suspend fun saveCredentials(url: String, key: String) {
        context.dataStore.edit {
            it[SERVER_URL] = url.trimEnd('/')
            it[API_KEY] = key
        }
    }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { it[API_KEY] = key }
    }

    suspend fun setHasSeenWelcome(value: Boolean) {
        context.dataStore.edit { it[HAS_SEEN_WELCOME_KEY] = value }
    }

    suspend fun saveGoogleBooksApiKey(v: String) {
        context.dataStore.edit { it[GOOGLE_BOOKS_API_KEY] = v }
    }

    suspend fun saveDirectUploadUrl(url: String) {
        context.dataStore.edit { it[DIRECT_UPLOAD_URL] = url.trimEnd('/') }
    }
}
