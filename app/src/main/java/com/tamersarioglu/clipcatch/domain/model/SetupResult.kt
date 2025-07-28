package com.tamersarioglu.clipcatch.domain.model

/**
 * Represents the result of an environment setup operation.
 * @param success Whether the setup operation was successful
 * @param setupSteps List of setup steps that were completed successfully
 * @param failedSteps List of setup steps that failed
 * @param error Error information if the setup failed
 * @param setupPath The path where the environment was set up
 * @param configurationDetails Additional configuration details from the setup
 */
data class SetupResult(
    val success: Boolean,
    val setupSteps: List<String> = emptyList(),
    val failedSteps: List<String> = emptyList(),
    val error: InitializationError? = null,
    val setupPath: String? = null,
    val configurationDetails: Map<String, String> = emptyMap()
) {
    /**
     * Convenience property to check if setup was partially successful.
     */
    val isPartialSuccess: Boolean
        get() = setupSteps.isNotEmpty() && failedSteps.isNotEmpty()
    
    /**
     * Total number of setup steps processed.
     */
    val totalStepsProcessed: Int
        get() = setupSteps.size + failedSteps.size
}