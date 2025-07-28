package com.tamersarioglu.clipcatch.util

/**
 * Data class representing logging configuration settings that can be persisted.
 * 
 * @param globalLevel The default log level for all components
 * @param componentLevels Map of component-specific log level overrides
 * @param extractionSummaryOnly Whether file extraction should use summary logging only
 */
data class LoggingSettings(
    val globalLevel: LogLevel = LogLevel.INFO,
    val componentLevels: Map<String, LogLevel> = emptyMap(),
    val extractionSummaryOnly: Boolean = true
) {
    
    /**
     * Gets the effective log level for a specific component.
     * Returns component-specific level if set, otherwise returns global level.
     * 
     * @param component The component name
     * @return The effective log level for the component
     */
    fun getEffectiveLevel(component: String): LogLevel {
        return componentLevels[component] ?: globalLevel
    }
    
    /**
     * Creates a new LoggingSettings with updated global level.
     * 
     * @param level The new global log level
     * @return New LoggingSettings instance with updated global level
     */
    fun withGlobalLevel(level: LogLevel): LoggingSettings {
        return copy(globalLevel = level)
    }
    
    /**
     * Creates a new LoggingSettings with updated component level.
     * 
     * @param component The component name
     * @param level The new log level for the component
     * @return New LoggingSettings instance with updated component level
     */
    fun withComponentLevel(component: String, level: LogLevel): LoggingSettings {
        val updatedLevels = componentLevels.toMutableMap()
        updatedLevels[component] = level
        return copy(componentLevels = updatedLevels)
    }
    
    /**
     * Creates a new LoggingSettings with a component level removed.
     * 
     * @param component The component name to remove
     * @return New LoggingSettings instance with component level removed
     */
    fun withoutComponentLevel(component: String): LoggingSettings {
        val updatedLevels = componentLevels.toMutableMap()
        updatedLevels.remove(component)
        return copy(componentLevels = updatedLevels)
    }
    
    /**
     * Validates the settings and returns a corrected version if needed.
     * This ensures all component names are valid and levels are supported.
     * 
     * @return Validated LoggingSettings instance
     */
    fun validate(): LoggingSettings {
        val validComponents = setOf(
            LoggingComponents.FILE_EXTRACTION,
            LoggingComponents.NETWORK,
            LoggingComponents.YOUTUBE_EXTRACTOR,
            LoggingComponents.MAIN_ACTIVITY,
            LoggingComponents.DOWNLOAD_MANAGER,
            LoggingComponents.PYTHON_ENVIRONMENT,
            LoggingComponents.NATIVE_LIBRARY,
            LoggingComponents.INITIALIZATION,
            LoggingComponents.STORAGE,
            LoggingComponents.PERMISSION
        )
        
        val validatedComponentLevels = componentLevels.filterKeys { component ->
            validComponents.contains(component)
        }
        
        return copy(componentLevels = validatedComponentLevels)
    }
}