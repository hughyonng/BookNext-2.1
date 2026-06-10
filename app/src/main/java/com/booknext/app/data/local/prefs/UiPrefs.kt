package com.booknext.app.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UiPrefs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val THEME_ID = stringPreferencesKey("theme_id")
        private val DARK_MODE = booleanPreferencesKey("dark_mode")
        private val UI_FONT_SCALE = floatPreferencesKey("ui_font_scale")
        private val UI_FONT_FAMILY = stringPreferencesKey("ui_font_family")
        private val UI_LINE_SPACING = floatPreferencesKey("ui_line_spacing")
        private val BLUE_LIGHT = booleanPreferencesKey("blue_light")
        private val BLUE_LIGHT_AMOUNT = floatPreferencesKey("blue_light_amount")
        private val EMPTY_FOLDERS = stringSetPreferencesKey("empty_folders")
    }

    val themeId: Flow<String> = context.dataStore.data.map { it[THEME_ID] ?: "blue" }
    val darkMode: Flow<Boolean> = context.dataStore.data.map { it[DARK_MODE] ?: false }
    val uiFontScale: Flow<Float> = context.dataStore.data.map { it[UI_FONT_SCALE] ?: 1.0f }
    val uiFontFamily: Flow<String> = context.dataStore.data.map { it[UI_FONT_FAMILY] ?: "sans-serif" }
    val uiLineSpacing: Flow<Float> = context.dataStore.data.map { it[UI_LINE_SPACING] ?: 1.5f }
    val blueLight: Flow<Boolean> = context.dataStore.data.map { it[BLUE_LIGHT] ?: false }
    val blueLightAmount: Flow<Float> = context.dataStore.data.map { it[BLUE_LIGHT_AMOUNT] ?: 0.3f }
    val emptyFolders: Flow<Set<String>> = context.dataStore.data.map { it[EMPTY_FOLDERS] ?: emptySet() }

    suspend fun saveThemeId(id: String) { context.dataStore.edit { it[THEME_ID] = id } }
    suspend fun saveDarkMode(enabled: Boolean) { context.dataStore.edit { it[DARK_MODE] = enabled } }
    suspend fun saveUiFontScale(v: Float) { context.dataStore.edit { it[UI_FONT_SCALE] = v } }
    suspend fun saveUiFontFamily(v: String) { context.dataStore.edit { it[UI_FONT_FAMILY] = v } }
    suspend fun saveUiLineSpacing(v: Float) { context.dataStore.edit { it[UI_LINE_SPACING] = v } }
    suspend fun saveBlueLight(v: Boolean) { context.dataStore.edit { it[BLUE_LIGHT] = v } }
    suspend fun saveBlueLightAmount(v: Float) { context.dataStore.edit { it[BLUE_LIGHT_AMOUNT] = v } }

    suspend fun addEmptyFolder(name: String) {
        context.dataStore.edit { prefs ->
            val current: Set<String> = prefs[EMPTY_FOLDERS] ?: emptySet()
            prefs[EMPTY_FOLDERS] = current + name
        }
    }

    suspend fun removeEmptyFolder(name: String) {
        context.dataStore.edit { prefs ->
            val current: Set<String> = prefs[EMPTY_FOLDERS] ?: emptySet()
            prefs[EMPTY_FOLDERS] = current - name
        }
    }

    fun observeBookmarks(bookId: String): Flow<List<Int>> = context.dataStore.data.map { prefs ->
        prefs[stringPreferencesKey("bookmarks_$bookId")]?.split(",")
            ?.mapNotNull { it.toIntOrNull() } ?: emptyList()
    }

    suspend fun saveBookmarks(bookId: String, pages: List<Int>) {
        context.dataStore.edit {
            if (pages.isEmpty()) it.remove(stringPreferencesKey("bookmarks_$bookId"))
            else it[stringPreferencesKey("bookmarks_$bookId")] = pages.joinToString(",")
        }
    }
}
