package com.tamersarioglu.clipcatch.util

/**
 * Enum representing different logging levels with priority-based ordering.
 * Lower priority values indicate more verbose logging.
 */
enum class LogLevel(val priority: Int) {
    VERBOSE(0),
    DEBUG(1),
    INFO(2),
    WARN(3),
    ERROR(4);

    /**
     * Checks if this log level should be logged based on the configured minimum level.
     * @param minimumLevel The minimum level required for logging
     * @return true if this level should be logged, false otherwise
     */
    fun shouldLog(minimumLevel: LogLevel): Boolean {
        return this.priority >= minimumLevel.priority
    }

    companion object {
        /**
         * Gets the LogLevel from a string value, defaulting to INFO if invalid.
         * @param value The string representation of the log level
         * @return The corresponding LogLevel or INFO if not found
         */
        fun fromString(value: String): LogLevel {
            return try {
                valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                INFO
            }
        }
    }
}