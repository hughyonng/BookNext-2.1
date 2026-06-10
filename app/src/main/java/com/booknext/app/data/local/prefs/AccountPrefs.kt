package com.booknext.app.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
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
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { it[SERVER_URL] ?: "" }
    val apiKey: Flow<String> = context.dataStore.data.map { it[API_KEY] ?: "" }
    val hasSeenWelcome: Flow<Boolean> = context.dataStore.data.map { it[HAS_SEEN_WELCOME_KEY] ?: false }
    val googleBooksApiKey: Flow<String> = context.dataStore.data.map { it[GOOGLE_BOOKS_API_KEY] ?: "" }

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
}
