package com.tamersarioglu.clipcatch.util

/**
 * Interface for managing logging configuration settings.
 * Provides methods to get and set log levels globally and per component.
 */
interface LoggingConfiguration {
    
    /**
     * Gets the current global log level.
     * 
     * @return The current global LogLevel
     */
    fun getGlobalLogLevel(): LogLevel
    
    /**
     * Sets the global log level for all components.
     * 
     * @param level The new global log level
     */
    fun setGlobalLogLevel(level: LogLevel)
    
    /**
     * Gets the log level for a specific component.
     * Returns component-specific level if set, otherwise returns global level.
     * 
     * @param component The component name
     * @return The effective log level for the component
     */
    fun getComponentLogLevel(component: String): LogLevel
    
    /**
     * Sets the log level for a specific component.
     * 
     * @param component The component name
     * @param level The new log level for the component
     */
    fun setComponentLogLevel(component: String, level: LogLevel)
    
    /**
     * Removes component-specific log level override.
     * Component will use global level after removal.
     * 
     * @param component The component name
     */
    fun removeComponentLogLevel(component: String)
    
    /**
     * Checks if logging is enabled for a specific level and component.
     * 
     * @param level The log level to check
     * @param component The component name (optional, uses global level if null)
     * @return true if logging is enabled for the given level and component
     */
    fun isLoggingEnabled(level: LogLevel, component: String? = null): Boolean
    
    /**
     * Gets the current logging settings.
     * 
     * @return Current LoggingSettings instance
     */
    fun getCurrentSettings(): LoggingSettings
    
    /**
     * Updates the logging settings.
     * 
     * @param settings The new logging settings
     */
    fun updateSettings(settings: LoggingSettings)
    
    /**
     * Resets logging configuration to default values.
     */
    fun resetToDefaults()
}