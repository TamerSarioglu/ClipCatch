package com.tamersarioglu.clipcatch.data.service

import com.tamersarioglu.clipcatch.domain.model.InitializationResult
import com.tamersarioglu.clipcatch.domain.model.InitializationStatus

/**
 * Interface for managing YouTube-DL initialization.
 * Handles all YouTube-DL specific initialization logic including library setup,
 * status tracking, and retry mechanisms.
 */
interface YouTubeDLInitializationService {
    
    /**
     * Initializes YouTube-DL with all required dependencies.
     * This includes native library setup, Python environment configuration,
     * and YouTube-DL instance initialization with proper error handling.
     * 
     * @return InitializationResult indicating success, failure, or partial success
     */
    suspend fun initialize(): InitializationResult
    
    /**
     * Checks if YouTube-DL has been successfully initialized.
     * 
     * @return true if YouTube-DL is ready for use, false otherwise
     */
    fun isInitialized(): Boolean
    
    /**
     * Gets the current initialization status with detailed information.
     * 
     * @return InitializationStatus representing the current state
     */
    fun getInitializationStatus(): InitializationStatus
    
    /**
     * Gets a human-readable status message for display purposes.
     * 
     * @return String describing the current YouTube-DL status
     */
    fun getStatusMessage(): String
    
    /**
     * Ensures YouTube-DL is initialized, performing initialization if needed.
     * This is a convenience method that checks the current state and initializes
     * if necessary, with built-in retry logic.
     * 
     * @return true if YouTube-DL is ready after this call, false if initialization failed
     */
    suspend fun ensureInitialized(): Boolean
    
    /**
     * Retries YouTube-DL initialization after a previous failure.
     * Uses alternative initialization methods and recovery strategies.
     * 
     * @return InitializationResult indicating the retry outcome
     */
    suspend fun retryInitialization(): InitializationResult
    
    /**
     * Resets the initialization state and performs a fresh initialization.
     * This clears any cached state and attempts initialization from scratch.
     * 
     * @return InitializationResult indicating the reset and initialization outcome
     */
    suspend fun resetAndReinitialize(): InitializationResult
    
    /**
     * Verifies that YouTube-DL is working correctly by testing basic functionality.
     * This includes version checks and basic API calls to ensure the library is operational.
     * 
     * @return true if verification passes, false if YouTube-DL is not working properly
     */
    suspend fun verifyInitialization(): Boolean
    
    /**
     * Gets the YouTube-DL version information if available.
     * 
     * @return Version string if available, null if not initialized or version unavailable
     */
    suspend fun getVersion(): String?
}