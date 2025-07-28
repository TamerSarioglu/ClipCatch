package com.tamersarioglu.clipcatch.domain.model

/**
 * Represents the current status of the application initialization process.
 */
sealed class InitializationStatus {
    /**
     * Initialization has not been started yet.
     */
    object NotStarted : InitializationStatus()
    
    /**
     * Initialization is currently in progress.
     */
    object InProgress : InitializationStatus()
    
    /**
     * Initialization has completed successfully.
     */
    object Completed : InitializationStatus()
    
    /**
     * Initialization has failed with an error.
     * @param error The error that caused the initialization to fail
     */
    data class Failed(val error: InitializationError) : InitializationStatus()
}