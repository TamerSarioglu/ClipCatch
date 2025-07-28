package com.tamersarioglu.clipcatch.data.service

import com.tamersarioglu.clipcatch.domain.model.InitializationError
import com.tamersarioglu.clipcatch.domain.model.InitializationResult
import com.tamersarioglu.clipcatch.domain.model.InitializationStatus
import com.tamersarioglu.clipcatch.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of InitializationOrchestrator that coordinates all initialization services.
 * Manages dependencies between initialization steps and provides retry and rollback mechanisms.
 */
@Singleton
class InitializationOrchestratorImpl @Inject constructor(
    private val nativeLibraryManager: NativeLibraryManager,
    private val pythonEnvironmentManager: PythonEnvironmentManager,
    private val youtubeDLService: YouTubeDLInitializationService,
    private val errorHandler: InitializationErrorHandler,
    private val logger: Logger
) : InitializationOrchestrator {
    
    private val initializationMutex = Mutex()
    
    @Volatile
    private var currentStatus: InitializationStatus = InitializationStatus.NotStarted
    
    @Volatile
    private var lastError: InitializationError? = null
    
    @Volatile
    private var retryCount = 0
    
    @Volatile
    private var errorHistory = mutableListOf<InitializationError>()
    
    @Volatile
    private var recoveryAttempts = mutableMapOf<String, Int>()
    
    private companion object {
        const val MAX_RETRY_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 100L
        const val MAX_RECOVERY_ATTEMPTS = 2
        const val TAG = "InitializationOrchestrator"
    }
    
    override suspend fun initialize(): InitializationResult = initializationMutex.withLock {
        if (currentStatus == InitializationStatus.InProgress) {
            logger.d(TAG, "Initialization already in progress, skipping duplicate request")
            return InitializationResult.Success
        }
        
        if (currentStatus == InitializationStatus.Completed) {
            logger.d(TAG, "Initialization already completed")
            return InitializationResult.Success
        }
        
        logger.i(TAG, "Starting application initialization process")
        currentStatus = InitializationStatus.InProgress
        
        try {
            // Step 1: Extract and load native libraries
            logger.d(TAG, "Step 1: Initializing native libraries")
            val nativeLibResult = initializeNativeLibraries()
            if (nativeLibResult is InitializationResult.Failure) {
                return handleInitializationFailure(nativeLibResult.error, "Native library initialization failed")
            }
            
            // Step 2: Setup Python environment
            logger.d(TAG, "Step 2: Setting up Python environment")
            val pythonResult = initializePythonEnvironment()
            if (pythonResult is InitializationResult.Failure) {
                return handleInitializationFailure(pythonResult.error, "Python environment setup failed")
            }
            
            // Step 3: Initialize YouTube-DL
            logger.d(TAG, "Step 3: Initializing YouTube-DL service")
            val youtubeDLResult = youtubeDLService.initialize()
            if (youtubeDLResult is InitializationResult.Failure) {
                return handleInitializationFailure(youtubeDLResult.error, "YouTube-DL initialization failed")
            }
            
            // All steps completed successfully
            currentStatus = InitializationStatus.Completed
            retryCount = 0
            lastError = null
            errorHistory.clear()
            recoveryAttempts.clear()
            logger.i(TAG, "Application initialization completed successfully")
            
            return InitializationResult.Success
            
        } catch (exception: Exception) {
            val error = InitializationError.GenericError(
                "Unexpected error during initialization: ${exception.message}",
                exception,
                true
            )
            return handleInitializationFailure(error, "Unexpected initialization error")
        }
    }
    
    override fun getInitializationStatus(): InitializationStatus = currentStatus
    
    override suspend fun retryInitialization(): InitializationResult = initializationMutex.withLock {
        if (retryCount >= MAX_RETRY_ATTEMPTS) {
            logger.w(TAG, "Maximum retry attempts ($MAX_RETRY_ATTEMPTS) exceeded")
            return InitializationResult.Failure(
                InitializationError.GenericError(
                    "Maximum retry attempts exceeded",
                    null,
                    false
                )
            )
        }
        
        retryCount++
        logger.i(TAG, "Retrying initialization (attempt $retryCount/$MAX_RETRY_ATTEMPTS)")
        
        // Apply recovery actions if available
        lastError?.let { error ->
            val recoveryAction = errorHandler.suggestRecoveryAction(error)
            if (recoveryAction != null && shouldAttemptRecovery(recoveryAction)) {
                logger.d(TAG, "Applying recovery action: $recoveryAction")
                incrementRecoveryAttempt(recoveryAction)
                applyRecoveryAction(recoveryAction)
            } else {
                logger.d(TAG, "Skipping recovery action - max attempts reached or no recovery available")
            }
        }
        
        // Add delay before retry
        delay(RETRY_DELAY_MS)
        
        // Reset status and retry
        currentStatus = InitializationStatus.NotStarted
        return initialize()
    }
    
    override suspend fun rollbackInitialization(): InitializationResult = initializationMutex.withLock {
        logger.i(TAG, "Rolling back initialization")
        
        try {
            // Reset YouTube-DL service
            logger.d(TAG, "Resetting YouTube-DL service")
            youtubeDLService.resetAndReinitialize()
            
            // Note: We don't rollback native libraries and Python environment
            // as they are file-based and rolling back would require re-extraction
            // which is handled by the individual services when needed
            
            currentStatus = InitializationStatus.NotStarted
            retryCount = 0
            lastError = null
            
            logger.i(TAG, "Initialization rollback completed")
            return InitializationResult.Success
            
        } catch (exception: Exception) {
            logger.e(TAG, "Error during rollback: ${exception.message}", exception)
            return InitializationResult.Failure(
                InitializationError.GenericError(
                    "Rollback failed: ${exception.message}",
                    exception,
                    false
                )
            )
        }
    }
    
    override fun isInitializationComplete(): Boolean {
        val status = currentStatus
        return status == InitializationStatus.Completed && youtubeDLService.isInitialized()
    }
    
    override fun getDetailedStatusMessage(): String {
        val status = currentStatus
        return when (status) {
            is InitializationStatus.NotStarted -> "Initialization not started"
            is InitializationStatus.InProgress -> "Initialization in progress..."
            is InitializationStatus.Completed -> {
                if (youtubeDLService.isInitialized()) {
                    "Initialization completed successfully"
                } else {
                    "Initialization completed but YouTube-DL verification failed"
                }
            }
            is InitializationStatus.Failed -> {
                val errorMsg = status.error.message
                val retryInfo = if (retryCount > 0) " (Retry $retryCount/$MAX_RETRY_ATTEMPTS)" else ""
                val errorHistoryInfo = if (errorHistory.size > 1) " (${errorHistory.size} errors total)" else ""
                "Initialization failed: $errorMsg$retryInfo$errorHistoryInfo"
            }
        }
    }
    
    /**
     * Gets a comprehensive error report including error history and recovery attempts.
     */
    fun getErrorReport(): Map<String, Any?> {
        return mapOf(
            "currentStatus" to currentStatus::class.simpleName,
            "retryCount" to retryCount,
            "maxRetryAttempts" to MAX_RETRY_ATTEMPTS,
            "errorHistoryCount" to errorHistory.size,
            "lastError" to (lastError?.let { 
                mapOf(
                    "type" to it::class.simpleName,
                    "message" to it.message,
                    "recoverable" to it.recoverable,
                    "category" to errorHandler.categorizeError(it).name,
                    "handlerRecoverable" to errorHandler.isRecoverable(it)
                )
            } ?: "None"),
            "recoveryAttempts" to recoveryAttempts.toMap(),
            "errorHistory" to errorHistory.map { error ->
                mapOf(
                    "type" to error::class.simpleName,
                    "message" to error.message,
                    "recoverable" to error.recoverable
                )
            },
            "serviceStates" to mapOf(
                "nativeLibraryManager" to "Available",
                "pythonEnvironmentManager" to "Available", 
                "youtubeDLService" to mapOf(
                    "initialized" to youtubeDLService.isInitialized(),
                    "status" to youtubeDLService.getStatusMessage()
                )
            )
        )
    }
    
    override suspend fun forceReinitialization(): InitializationResult = initializationMutex.withLock {
        logger.i(TAG, "Forcing complete re-initialization")
        
        // Reset all state
        currentStatus = InitializationStatus.NotStarted
        retryCount = 0
        lastError = null
        errorHistory.clear()
        recoveryAttempts.clear()
        errorHistory.clear()
        recoveryAttempts.clear()
        
        // Perform rollback first
        val rollbackResult = rollbackInitialization()
        if (rollbackResult is InitializationResult.Failure) {
            logger.w(TAG, "Rollback failed during force re-initialization, continuing anyway")
        }
        
        // Perform fresh initialization
        return initialize()
    }
    
    /**
     * Initializes native libraries by extracting and loading them.
     */
    private suspend fun initializeNativeLibraries(): InitializationResult {
        try {
            // Check if extraction is needed
            if (nativeLibraryManager.shouldExtractLibraries()) {
                logger.d(TAG, "Extracting native libraries")
                val extractionResult = nativeLibraryManager.extractNativeLibraries()
                if (!extractionResult.success) {
                    return InitializationResult.Failure(
                        extractionResult.error ?: InitializationError.NativeLibraryError(
                            "Failed to extract native libraries",
                            null
                        )
                    )
                }
            }
            
            // Load native libraries
            logger.d(TAG, "Loading native libraries")
            val loadResult = nativeLibraryManager.loadNativeLibraries()
            if (!loadResult.success) {
                return InitializationResult.Failure(
                    loadResult.error ?: InitializationError.NativeLibraryError(
                        "Failed to load native libraries",
                        null
                    )
                )
            }
            
            // Verify native libraries
            logger.d(TAG, "Verifying native libraries")
            val verificationResult = nativeLibraryManager.verifyNativeLibraries()
            if (!verificationResult.success) {
                return InitializationResult.Failure(
                    verificationResult.error ?: InitializationError.NativeLibraryError(
                        "Native library verification failed",
                        null
                    )
                )
            }
            
            logger.d(TAG, "Native library initialization completed successfully")
            return InitializationResult.Success
            
        } catch (exception: Exception) {
            return InitializationResult.Failure(
                InitializationError.NativeLibraryError(
                    "Unexpected error during native library initialization: ${exception.message}",
                    exception
                )
            )
        }
    }
    
    /**
     * Initializes Python environment by setting up directories and extracting files.
     */
    private suspend fun initializePythonEnvironment(): InitializationResult {
        try {
            logger.d(TAG, "Setting up Python environment")
            val setupResult = pythonEnvironmentManager.setupPythonEnvironment()
            if (!setupResult.success) {
                return InitializationResult.Failure(
                    setupResult.error ?: InitializationError.PythonEnvironmentError(
                        "Failed to setup Python environment",
                        null
                    )
                )
            }
            
            logger.d(TAG, "Python environment initialization completed successfully")
            return InitializationResult.Success
            
        } catch (exception: Exception) {
            return InitializationResult.Failure(
                InitializationError.PythonEnvironmentError(
                    "Unexpected error during Python environment initialization: ${exception.message}",
                    exception
                )
            )
        }
    }
    
    /**
     * Handles initialization failure by updating status and logging error.
     */
    private suspend fun handleInitializationFailure(
        error: InitializationError,
        context: String
    ): InitializationResult {
        logger.e(TAG, "$context: ${error.message}", error.cause)
        
        currentStatus = InitializationStatus.Failed(error)
        lastError = error
        
        // Add to error history for tracking
        errorHistory.add(error)
        
        // Handle the error through the error handler
        val handlingResult = errorHandler.handleError(error)
        logger.d(TAG, "Error handling result: $handlingResult")
        
        // Log error state information
        logErrorState(error, context)
        
        return InitializationResult.Failure(error)
    }
    
    /**
     * Logs comprehensive error state information for debugging and monitoring.
     */
    private fun logErrorState(error: InitializationError, context: String) {
        logger.d(TAG, "=== Error State Report ===")
        logger.d(TAG, "Context: $context")
        logger.d(TAG, "Error Type: ${error::class.simpleName}")
        logger.d(TAG, "Error Message: ${error.message}")
        logger.d(TAG, "Recoverable: ${error.recoverable}")
        logger.d(TAG, "Retry Count: $retryCount/$MAX_RETRY_ATTEMPTS")
        logger.d(TAG, "Error History Count: ${errorHistory.size}")
        logger.d(TAG, "Recovery Attempts: $recoveryAttempts")
        
        // Log error categorization
        val category = errorHandler.categorizeError(error)
        val isRecoverable = errorHandler.isRecoverable(error)
        logger.d(TAG, "Error Category: $category")
        logger.d(TAG, "Handler Recoverable: $isRecoverable")
        
        // Log suggested recovery action
        val recoveryAction = errorHandler.suggestRecoveryAction(error)
        logger.d(TAG, "Suggested Recovery: ${recoveryAction?.javaClass?.simpleName ?: "None"}")
        
        logger.d(TAG, "=== End Error State Report ===")
    }
    
    /**
     * Determines if a recovery action should be attempted based on previous attempts.
     */
    private fun shouldAttemptRecovery(recoveryAction: RecoveryAction): Boolean {
        val actionKey = recoveryAction.javaClass.simpleName
        val currentAttempts = recoveryAttempts[actionKey] ?: 0
        
        return when (recoveryAction) {
            is RecoveryAction.NoRecovery -> false
            is RecoveryAction.Retry -> currentAttempts < recoveryAction.maxAttempts
            else -> currentAttempts < MAX_RECOVERY_ATTEMPTS
        }
    }
    
    /**
     * Increments the recovery attempt count for a specific recovery action.
     */
    private fun incrementRecoveryAttempt(recoveryAction: RecoveryAction) {
        val actionKey = recoveryAction.javaClass.simpleName
        val currentAttempts = recoveryAttempts[actionKey] ?: 0
        recoveryAttempts[actionKey] = currentAttempts + 1
        
        logger.d(TAG, "Recovery attempt incremented for $actionKey: ${currentAttempts + 1}")
    }
    
    /**
     * Applies a recovery action based on the error handler's suggestion.
     */
    private suspend fun applyRecoveryAction(recoveryAction: RecoveryAction) {
        when (recoveryAction) {
            is RecoveryAction.Retry -> {
                logger.d(TAG, "Recovery action: Retry with delay ${recoveryAction.delayMs}ms")
                delay(recoveryAction.delayMs)
            }
            is RecoveryAction.ReExtractFiles -> {
                logger.d(TAG, "Recovery action: Re-extract files matching ${recoveryAction.targetPattern}")
                try {
                    when {
                        recoveryAction.targetPattern.contains("lib/") -> {
                            logger.d(TAG, "Re-extracting native libraries")
                            nativeLibraryManager.extractNativeLibraries()
                        }
                        recoveryAction.targetPattern.contains("python") -> {
                            logger.d(TAG, "Re-extracting Python files")
                            pythonEnvironmentManager.extractPythonFiles()
                        }
                        else -> {
                            logger.d(TAG, "Generic file re-extraction for pattern: ${recoveryAction.targetPattern}")
                        }
                    }
                } catch (e: Exception) {
                    logger.w(TAG, "Failed to apply re-extraction recovery action", e)
                }
            }
            is RecoveryAction.RecreateDirectories -> {
                logger.d(TAG, "Recovery action: Recreate directories ${recoveryAction.directories}")
                try {
                    recoveryAction.directories.forEach { dirName ->
                        when (dirName) {
                            "native_libs" -> {
                                logger.d(TAG, "Recreating native libraries directory")
                                val nativeDir = java.io.File(nativeLibraryManager.getNativeLibraryDirectory())
                                if (nativeDir.exists()) {
                                    nativeDir.deleteRecursively()
                                }
                                nativeDir.mkdirs()
                            }
                            "python" -> {
                                logger.d(TAG, "Recreating Python directory")
                                val pythonDir = pythonEnvironmentManager.getPythonDirectory()
                                if (pythonDir.exists()) {
                                    pythonDir.deleteRecursively()
                                }
                                pythonDir.mkdirs()
                            }
                            else -> {
                                logger.d(TAG, "Unknown directory for recreation: $dirName")
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.w(TAG, "Failed to apply directory recreation recovery action", e)
                }
            }
            is RecoveryAction.UseAlternativeMethod -> {
                logger.d(TAG, "Recovery action: Use alternative method - ${recoveryAction.alternativeMethod}")
                // Alternative methods are handled by the individual services during initialization
                // This is mainly informational for the orchestrator
            }
            is RecoveryAction.ResetAndRestart -> {
                logger.d(TAG, "Recovery action: Reset and restart")
                try {
                    rollbackInitialization()
                } catch (e: Exception) {
                    logger.w(TAG, "Failed to apply reset and restart recovery action", e)
                }
            }
            is RecoveryAction.NoRecovery -> {
                logger.d(TAG, "Recovery action: No recovery available - ${recoveryAction.reason}")
            }
        }
    }
}