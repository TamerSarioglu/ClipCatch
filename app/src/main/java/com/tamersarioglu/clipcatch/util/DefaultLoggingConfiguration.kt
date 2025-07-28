package com.tamersarioglu.clipcatch.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of LoggingConfiguration that persists settings using SharedPreferences.
 * This implementation is thread-safe and provides fallback behavior for invalid configurations.
 */
@Singleton
class DefaultLoggingConfiguration @Inject constructor(
    private val context: Context
) : LoggingConfiguration {
    
    companion object {
        private const val PREFS_NAME = "logging_configuration"
        private const val KEY_GLOBAL_LEVEL = "global_level"
        private const val KEY_EXTRACTION_SUMMARY_ONLY = "extraction_summary_only"
        private const val KEY_COMPONENT_PREFIX = "component_"
    }
    
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    @Volatile
    private var cachedSettings: LoggingSettings? = null

    override fun getGlobalLogLevel(): LogLevel {
        return getCurrentSettings().globalLevel
    }

    override fun setGlobalLogLevel(level: LogLevel) {
        val currentSettings = getCurrentSettings()
        val updatedSettings = currentSettings.withGlobalLevel(level)
        updateSettings(updatedSettings)
    }

    override fun getComponentLogLevel(component: String): LogLevel {
        return getCurrentSettings().getEffectiveLevel(component)
    }

    override fun setComponentLogLevel(component: String, level: LogLevel) {
        if (component.isBlank()) return
        
        val currentSettings = getCurrentSettings()
        val updatedSettings = currentSettings.withComponentLevel(component, level)
        updateSettings(updatedSettings)
    }

    override fun removeComponentLogLevel(component: String) {
        val currentSettings = getCurrentSettings()
        val updatedSettings = currentSettings.withoutComponentLevel(component)
        updateSettings(updatedSettings)
    }

    override fun isLoggingEnabled(level: LogLevel, component: String?): Boolean {
        val effectiveLevel = if (component != null) {
            getComponentLogLevel(component)
        } else {
            getGlobalLogLevel()
        }
        return level.shouldLog(effectiveLevel)
    }

    override fun getCurrentSettings(): LoggingSettings {
        // Return cached settings if available
        cachedSettings?.let { return it }
        
        // Load settings from SharedPreferences
        val settings = loadSettingsFromPreferences()
        cachedSettings = settings
        return settings
    }

    override fun updateSettings(settings: LoggingSettings) {
        val validatedSettings = settings.validate()
        saveSettingsToPreferences(validatedSettings)
        cachedSettings = validatedSettings
    }

    override fun resetToDefaults() {
        val defaultSettings = LoggingSettings()
        updateSettings(defaultSettings)
    }
    
    /**
     * Loads logging settings from SharedPreferences with fallback to defaults.
     */
    private fun loadSettingsFromPreferences(): LoggingSettings {
        return try {
            val globalLevelString = sharedPreferences.getString(KEY_GLOBAL_LEVEL, LogLevel.INFO.name)
            val globalLevel = LogLevel.fromString(globalLevelString ?: LogLevel.INFO.name)
            
            val extractionSummaryOnly = sharedPreferences.getBoolean(KEY_EXTRACTION_SUMMARY_ONLY, true)
            
            val componentLevels = mutableMapOf<String, LogLevel>()
            
            // Load component-specific levels
            val allKeys = sharedPreferences.all.keys
            for (key in allKeys) {
                if (key.startsWith(KEY_COMPONENT_PREFIX)) {
                    val component = key.removePrefix(KEY_COMPONENT_PREFIX)
                    val levelString = sharedPreferences.getString(key, null)
                    if (levelString != null) {
                        val level = LogLevel.fromString(levelString)
                        componentLevels[component] = level
                    }
                }
            }
            
            LoggingSettings(
                globalLevel = globalLevel,
                componentLevels = componentLevels,
                extractionSummaryOnly = extractionSummaryOnly
            ).validate()
            
        } catch (e: Exception) {
            // If loading fails, return default settings
            Log.e("ClipCatch:DefaultLoggingConfiguration", "Failed to load logging settings, using defaults", e)
            LoggingSettings()
        }
    }
    
    /**
     * Saves logging settings to SharedPreferences.
     */
    private fun saveSettingsToPreferences(settings: LoggingSettings) {
        try {
            val editor = sharedPreferences.edit()
            
            // Save global level
            editor.putString(KEY_GLOBAL_LEVEL, settings.globalLevel.name)
            editor.putBoolean(KEY_EXTRACTION_SUMMARY_ONLY, settings.extractionSummaryOnly)
            
            // Clear existing component levels
            val allKeys = sharedPreferences.all.keys.toList()
            for (key in allKeys) {
                if (key.startsWith(KEY_COMPONENT_PREFIX)) {
                    editor.remove(key)
                }
            }
            
            // Save component-specific levels
            for ((component, level) in settings.componentLevels) {
                editor.putString(KEY_COMPONENT_PREFIX + component, level.name)
            }
            
            editor.apply()
            
        } catch (e: Exception) {
            Log.e("ClipCatch:DefaultLoggingConfiguration", "Failed to save logging settings", e)
        }
    }
}