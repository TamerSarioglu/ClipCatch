package com.tamersarioglu.clipcatch.data.service

import com.tamersarioglu.clipcatch.domain.model.InitializationResult
import com.tamersarioglu.clipcatch.domain.model.InitializationStatus

/**
 * Interface for orchestrating the complete application initialization process.
 * Coordinates all initialization services and manages dependencies between initialization steps.
 */
interface InitializationOrchestrator {
    
    /**
     * Performs the complete application initialization process.
     * Coordinates native library extraction, Python environment setup, and YouTube-DL initialization
     * in the correct order with proper dependency management.
     * 
     * @return InitializationResult indicating the overall success of the initialization process
     */
    suspend fun initialize(): InitializationResult
    
    /**
     * Gets the current overall initialization status.
     * 
     * @return InitializationStatus representing the current state of the initialization process
     */
    fun getInitializationStatus(): InitializationStatus
    
    /**
     * Retries the initialization process after a previous failure.
     * Uses recovery strategies and alternative methods based on the previous failure type.
     * 
     * @return InitializationResult indicating the retry outcome
     */
    suspend fun retryInitialization(): InitializationResult
    
    /**
     * Performs a rollback of the initialization process.
     * Cleans up any partially completed initialization steps and resets to a clean state.
     * 
     * @return InitializationResult indicating the rollback outcome
     */
    suspend fun rollbackInitialization(): InitializationResult
    
    /**
     * Checks if the complete initialization process has been successfully completed.
     * 
     * @return true if all initialization steps are complete and successful, false otherwise
     */
    fun isInitializationComplete(): Boolean
    
    /**
     * Gets a detailed status message describing the current initialization state.
     * 
     * @return String with detailed information about the initialization progress
     */
    fun getDetailedStatusMessage(): String
    
    /**
     * Forces a complete re-initialization by resetting all services and starting over.
     * This clears all cached state and performs initialization from scratch.
     * 
     * @return InitializationResult indicating the re-initialization outcome
     */
    suspend fun forceReinitialization(): InitializationResult
}