package com.tamersarioglu.clipcatch.domain.model

/**
 * Represents the result of an initialization operation.
 */
sealed class InitializationResult {
    /**
     * The initialization operation completed successfully.
     */
    object Success : InitializationResult()
    
    /**
     * The initialization operation failed with an error.
     * @param error The error that caused the failure
     */
    data class Failure(val error: InitializationError) : InitializationResult()
    
    /**
     * The initialization operation completed with some warnings.
     * @param warnings List of warning messages encountered during initialization
     */
    data class PartialSuccess(val warnings: List<String>) : InitializationResult()
}