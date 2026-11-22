package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "puente_lsa_prefs")

data class UserSettings(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val isOfflineModeForced: Boolean = false,
    val activeSignLanguageCode: String = "LSA",
    val hapticAlertsEnabled: Boolean = true,
    val isAiExtraEnabled: Boolean = false,
    val isVoiceExtraEnabled: Boolean = false,
    val autoSpeakEnabled: Boolean = false,
    val speechRate: Float = 1.0f,
    val wearableSyncEnabled: Boolean = true,
    val wearableMirrorHaptics: Boolean = true,
    val connectedWearableName: String = "Sin reloj vinculado"
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val OFFLINE_MODE = booleanPreferencesKey("offline_mode_forced")
        val SIGN_LANG = stringPreferencesKey("active_sign_language")
        val HAPTIC_ALERTS = booleanPreferencesKey("haptic_alerts_enabled")
        val AI_EXTRA_ENABLED = booleanPreferencesKey("ai_extra_enabled")
        val VOICE_EXTRA_ENABLED = booleanPreferencesKey("voice_extra_enabled")
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak_enabled")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val WEARABLE_SYNC = booleanPreferencesKey("wearable_sync_enabled")
        val WEARABLE_HAPTICS = booleanPreferencesKey("wearable_mirror_haptics")
        val WEARABLE_NAME = stringPreferencesKey("connected_wearable_name")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val themeName = prefs[Keys.THEME_MODE] ?: ThemePreference.SYSTEM.name
        val theme = ThemePreference.entries.firstOrNull { it.name == themeName } ?: ThemePreference.SYSTEM
        UserSettings(
            themePreference = theme,
            isOfflineModeForced = prefs[Keys.OFFLINE_MODE] ?: false,
            activeSignLanguageCode = prefs[Keys.SIGN_LANG] ?: "LSA",
            hapticAlertsEnabled = prefs[Keys.HAPTIC_ALERTS] ?: true,
            isAiExtraEnabled = prefs[Keys.AI_EXTRA_ENABLED] ?: false,
            isVoiceExtraEnabled = prefs[Keys.VOICE_EXTRA_ENABLED] ?: false,
            autoSpeakEnabled = prefs[Keys.AUTO_SPEAK] ?: false,
            speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
            wearableSyncEnabled = prefs[Keys.WEARABLE_SYNC] ?: true,
            wearableMirrorHaptics = prefs[Keys.WEARABLE_HAPTICS] ?: true,
            connectedWearableName = prefs[Keys.WEARABLE_NAME] ?: "Sin reloj vinculado"
        )
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME_MODE] = theme.name }
    }

    suspend fun setOfflineModeForced(forced: Boolean) {
        context.dataStore.edit { it[Keys.OFFLINE_MODE] = forced }
    }

    suspend fun setActiveSignLanguage(code: String) {
        context.dataStore.edit { it[Keys.SIGN_LANG] = code }
    }

    suspend fun setHapticAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC_ALERTS] = enabled }
    }

    suspend fun setAiExtraEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AI_EXTRA_ENABLED] = enabled }
    }

    suspend fun setVoiceExtraEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[Keys.VOICE_EXTRA_ENABLED] = enabled
            if (!enabled) {
                it[Keys.AUTO_SPEAK] = false
            }
        }
    }

    suspend fun setAutoSpeakEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SPEAK] = enabled }
    }

    suspend fun setSpeechRate(rate: Float) {
        context.dataStore.edit { it[Keys.SPEECH_RATE] = rate }
    }

    suspend fun setWearableSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WEARABLE_SYNC] = enabled }
    }

    suspend fun setWearableMirrorHaptics(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WEARABLE_HAPTICS] = enabled }
    }

    suspend fun setConnectedWearableName(name: String) {
        context.dataStore.edit { it[Keys.WEARABLE_NAME] = name }
    }
}
