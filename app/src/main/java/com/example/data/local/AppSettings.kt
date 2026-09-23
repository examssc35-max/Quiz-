package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("quiz_explore_settings", Context.MODE_PRIVATE)

    private val _soundEnabled = MutableStateFlow(prefs.getBoolean(KEY_SOUND, true))
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _voiceEnabled = MutableStateFlow(prefs.getBoolean(KEY_VOICE, true))
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled.asStateFlow()

    private val _vibrationEnabled = MutableStateFlow(prefs.getBoolean(KEY_VIBRATION, true))
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    private val _showExplanations = MutableStateFlow(prefs.getBoolean(KEY_EXPLANATIONS, true))
    val showExplanations: StateFlow<Boolean> = _showExplanations.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getString(KEY_THEME, "System") ?: "System")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _blurIntensity = MutableStateFlow(prefs.getString(KEY_BLUR, "Medium") ?: "Medium")
    val blurIntensity: StateFlow<String> = _blurIntensity.asStateFlow()

    private val _appLanguage = MutableStateFlow(prefs.getString(KEY_LANGUAGE, "English") ?: "English")
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply()
        _soundEnabled.value = enabled
    }

    fun setVoiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE, enabled).apply()
        _voiceEnabled.value = enabled
    }

    fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION, enabled).apply()
        _vibrationEnabled.value = enabled
    }

    fun setShowExplanations(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EXPLANATIONS, enabled).apply()
        _showExplanations.value = enabled
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME, mode).apply()
        _themeMode.value = mode
    }

    fun setBlurIntensity(intensity: String) {
        prefs.edit().putString(KEY_BLUR, intensity).apply()
        _blurIntensity.value = intensity
    }

    fun setAppLanguage(language: String) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
        _appLanguage.value = language
    }

    companion object {
        private const val KEY_SOUND = "key_sound"
        private const val KEY_VOICE = "key_voice"
        private const val KEY_VIBRATION = "key_vibration"
        private const val KEY_EXPLANATIONS = "key_explanations"
        private const val KEY_THEME = "key_theme"
        private const val KEY_BLUR = "key_blur"
        private const val KEY_LANGUAGE = "key_language"

        @Volatile
        private var INSTANCE: AppSettings? = null

        fun getInstance(context: Context): AppSettings {
            return INSTANCE ?: synchronized(this) {
                val instance = AppSettings(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
