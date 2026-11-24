package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "studio_prefs")

enum class LanguagePolicy {
    AUTO_DETECT,
    ENGLISH,
    BENGALI
}

enum class AiBackendPreference {
    GPU,
    CPU
}

data class StudioPreferences(
    val activeProjectId: String? = null,
    val selectedModelPath: String? = null,
    val backendPreference: AiBackendPreference = AiBackendPreference.GPU,
    val languagePolicy: LanguagePolicy = LanguagePolicy.AUTO_DETECT,
    val themeMode: ThemeMode = ThemeMode.DARK
)

class PreferencesManager(private val context: Context) {

    private object Keys {
        val ACTIVE_PROJECT_ID = stringPreferencesKey("active_project_id")
        val SELECTED_MODEL_PATH = stringPreferencesKey("selected_model_path")
        val BACKEND_PREF = stringPreferencesKey("backend_pref")
        val LANGUAGE_POLICY = stringPreferencesKey("language_policy")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val preferencesFlow: Flow<StudioPreferences> = context.dataStore.data.map { prefs ->
        StudioPreferences(
            activeProjectId = prefs[Keys.ACTIVE_PROJECT_ID],
            selectedModelPath = prefs[Keys.SELECTED_MODEL_PATH],
            backendPreference = prefs[Keys.BACKEND_PREF]?.let {
                runCatching { AiBackendPreference.valueOf(it) }.getOrNull()
            } ?: AiBackendPreference.GPU,
            languagePolicy = prefs[Keys.LANGUAGE_POLICY]?.let {
                runCatching { LanguagePolicy.valueOf(it) }.getOrNull()
            } ?: LanguagePolicy.AUTO_DETECT,
            themeMode = prefs[Keys.THEME_MODE]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: ThemeMode.DARK
        )
    }

    suspend fun setActiveProjectId(projectId: String?) {
        context.dataStore.edit { prefs ->
            if (projectId == null) prefs.remove(Keys.ACTIVE_PROJECT_ID)
            else prefs[Keys.ACTIVE_PROJECT_ID] = projectId
        }
    }

    suspend fun setSelectedModelPath(path: String?) {
        context.dataStore.edit { prefs ->
            if (path == null) prefs.remove(Keys.SELECTED_MODEL_PATH)
            else prefs[Keys.SELECTED_MODEL_PATH] = path
        }
    }

    suspend fun setBackendPreference(backend: AiBackendPreference) {
        context.dataStore.edit { it[Keys.BACKEND_PREF] = backend.name }
    }

    suspend fun setLanguagePolicy(policy: LanguagePolicy) {
        context.dataStore.edit { it[Keys.LANGUAGE_POLICY] = policy.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }
}
