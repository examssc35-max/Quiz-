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

    // Custom Theme Settings (User can choose custom theme presets or customize colors)
    private val _themePresetId = MutableStateFlow(prefs.getString(KEY_THEME_PRESET, "deep_midnight") ?: "deep_midnight")
    val themePresetId: StateFlow<String> = _themePresetId.asStateFlow()

    private val _customPrimaryHex = MutableStateFlow(prefs.getString(KEY_CUSTOM_PRIMARY, "#3B82F6") ?: "#3B82F6")
    val customPrimaryHex: StateFlow<String> = _customPrimaryHex.asStateFlow()

    private val _customSecondaryHex = MutableStateFlow(prefs.getString(KEY_CUSTOM_SECONDARY, "#06B6D4") ?: "#06B6D4")
    val customSecondaryHex: StateFlow<String> = _customSecondaryHex.asStateFlow()

    private val _customBgHex = MutableStateFlow(prefs.getString(KEY_CUSTOM_BG, "#070E1E") ?: "#070E1E")
    val customBgHex: StateFlow<String> = _customBgHex.asStateFlow()

    // AI Voice Congratulation Settings (Unlimited words, custom words, and Bengali/English support)
    private val _voiceCongratulationMode = MutableStateFlow(prefs.getString(KEY_VOICE_CONG_MODE, "unlimited_ai") ?: "unlimited_ai")
    val voiceCongratulationMode: StateFlow<String> = _voiceCongratulationMode.asStateFlow()

    private val _voiceLanguage = MutableStateFlow(prefs.getString(KEY_VOICE_LANGUAGE, "auto") ?: "auto")
    val voiceLanguage: StateFlow<String> = _voiceLanguage.asStateFlow()

    private val _customCongratulationWords = MutableStateFlow(
        prefs.getString(KEY_CUSTOM_CONG_WORDS, "সাবাশ, অসাধারণ, মারহাবা, দারুণ, চমৎকার, Bravo, Outstanding, Fantastic, Mind blowing") ?: "সাবাশ, অসাধারণ, মারহাবা, দারুণ, চমৎকার, Bravo, Outstanding, Fantastic, Mind blowing"
    )
    val customCongratulationWords: StateFlow<String> = _customCongratulationWords.asStateFlow()

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

    fun setThemePresetId(presetId: String) {
        prefs.edit().putString(KEY_THEME_PRESET, presetId).apply()
        _themePresetId.value = presetId
    }

    fun setCustomColors(primaryHex: String, secondaryHex: String, bgHex: String? = null) {
        val editor = prefs.edit()
            .putString(KEY_CUSTOM_PRIMARY, primaryHex)
            .putString(KEY_CUSTOM_SECONDARY, secondaryHex)
        if (bgHex != null) {
            editor.putString(KEY_CUSTOM_BG, bgHex)
            _customBgHex.value = bgHex
        }
        editor.apply()
        _customPrimaryHex.value = primaryHex
        _customSecondaryHex.value = secondaryHex
    }

    fun setCustomBgColor(bgHex: String) {
        prefs.edit().putString(KEY_CUSTOM_BG, bgHex).apply()
        _customBgHex.value = bgHex
    }

    fun setVoiceCongratulationMode(mode: String) {
        prefs.edit().putString(KEY_VOICE_CONG_MODE, mode).apply()
        _voiceCongratulationMode.value = mode
    }

    fun setVoiceLanguage(language: String) {
        prefs.edit().putString(KEY_VOICE_LANGUAGE, language).apply()
        _voiceLanguage.value = language
    }

    fun setCustomCongratulationWords(words: String) {
        prefs.edit().putString(KEY_CUSTOM_CONG_WORDS, words).apply()
        _customCongratulationWords.value = words
    }

    companion object {
        private const val KEY_SOUND = "key_sound"
        private const val KEY_VOICE = "key_voice"
        private const val KEY_VIBRATION = "key_vibration"
        private const val KEY_EXPLANATIONS = "key_explanations"
        private const val KEY_THEME = "key_theme"
        private const val KEY_BLUR = "key_blur"
        private const val KEY_LANGUAGE = "key_language"
        private const val KEY_THEME_PRESET = "key_theme_preset"
        private const val KEY_CUSTOM_PRIMARY = "key_custom_primary"
        private const val KEY_CUSTOM_SECONDARY = "key_custom_secondary"
        private const val KEY_CUSTOM_BG = "key_custom_bg"
        private const val KEY_VOICE_CONG_MODE = "key_voice_cong_mode"
        private const val KEY_VOICE_LANGUAGE = "key_voice_language"
        private const val KEY_CUSTOM_CONG_WORDS = "key_custom_cong_words"

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
