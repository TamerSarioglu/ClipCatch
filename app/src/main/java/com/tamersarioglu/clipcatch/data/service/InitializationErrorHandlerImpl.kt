package com.tamersarioglu.clipcatch.data.service

import com.tamersarioglu.clipcatch.domain.model.InitializationError
import com.tamersarioglu.clipcatch.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of InitializationErrorHandler that provides centralized error handling
 * and recovery suggestions for initialization failures.
 */
@Singleton
class InitializationErrorHandlerImpl @Inject constructor(
    private val logger: Logger
) : InitializationErrorHandler {

    companion object {
        private const val TAG = "InitializationErrorHandler"
        private const val DEFAULT_RETRY_ATTEMPTS = 3
        private const val DEFAULT_RETRY_DELAY_MS = 1000L
    }

    override fun handleError(error: InitializationError): ErrorHandlingResult {
        logger.enter(TAG, "handleError", error::class.simpleName)
        
        try {
            // Log the error first
            logError(error)
            
            // Categorize the error
            val category = categorizeError(error)
            logger.d(TAG, "Error categorized as: $category")
            
            // Suggest recovery action
            val recoveryAction = suggestRecoveryAction(error)
            logger.d(TAG, "Recovery action suggested: ${recoveryAction?.javaClass?.simpleName ?: "None"}")
            
            val result = ErrorHandlingResult.Handled(category, recoveryAction)
            logger.exit(TAG, "handleError", "Handled")
            return result
            
        } catch (e: Exception) {
            val errorMsg = "Failed to handle initialization error: ${e.message}"
            logger.e(TAG, errorMsg, e)
            return ErrorHandlingResult.Failed(errorMsg)
        }
    }

    override fun suggestRecoveryAction(error: InitializationError): RecoveryAction? {
        logger.d(TAG, "Suggesting recovery action for: ${error::class.simpleName}")
        
        return when (error) {
            is InitializationError.NativeLibraryError -> {
                when {
                    error.message.contains("extract", ignoreCase = true) -> {
                        logger.d(TAG, "Suggesting file re-extraction for native library error")
                        RecoveryAction.ReExtractFiles("lib/")
                    }
                    error.message.contains("load", ignoreCase = true) -> {
                        logger.d(TAG, "Suggesting retry for native library loading error")
                        RecoveryAction.Retry(maxAttempts = 2, delayMs = 2000L)
                    }
                    error.message.contains("missing", ignoreCase = true) -> {
                        logger.d(TAG, "Suggesting file re-extraction for missing native libraries")
                        RecoveryAction.ReExtractFiles("lib/")
                    }
                    else -> {
                        logger.d(TAG, "Suggesting directory recreation for native library error")
                        RecoveryAction.RecreateDirectories(listOf("native_libs"))
                    }
                }
            }
            
            is InitializationError.PythonEnvironmentError -> {
                when {
                    error.message.contains("directory", ignoreCase = true) -> {
                        logger.d(TAG, "Suggesting directory recreation for Python environment error")
                        RecoveryAction.RecreateDirectories(listOf("python"))
                    }
                    error.message.contains("extraction", ignoreCase = true) -> {
                        logger.d(TAG, "Suggesting Python file re-extraction")
                        RecoveryAction.ReExtractFiles("python")
                    }
                    error.message.contains("empty", ignoreCase = true) -> {
                        logger.d(TAG, "Suggesting Python file re-extraction for empty directory")
                        RecoveryAction.ReExtractFiles("python")
                    }
                    else -> {
                        logger.d(TAG, "Suggesting reset and restart for Python environment error")
                        RecoveryAction.ResetAndRestart
                    }
                }
            }
            
            is InitializationError.FileExtractionError -> {
                when {
                    error.message.contains("APK", ignoreCase = true) -> {
                        logger.d(TAG, "Suggesting retry for APK extraction error")
                        RecoveryAction.Retry(maxAttempts = 2, delayMs = 1500L)
                    }
                    error.message.contains("ZIP", ignoreCase = true) -> {
                        logger.d(TAG, "Suggesting alternative method for ZIP extraction error")
                        RecoveryAction.UseAlternativeMethod("Manual ZIP extraction")
                    }
                    error.message.contains("permission", ignoreCase = true) -> {
                        logger.d(TAG, "No recovery available for permission error")
                        RecoveryAction.NoRecovery("File permission issues require manual intervention")
                    }
                    else -> {
                        logger.d(TAG, "Suggesting retry for general file extraction error")
                        RecoveryAction.Retry()
                    }
                }
            }
            
            is InitializationError.YouTubeDLError -> {
                when {
                    error.message.contains("initialization", ignoreCase = true) -> {
                        logger.d(TAG, "Suggesting alternative method for YouTube-DL initialization error")
                        RecoveryAction.UseAlternativeMethod("Alternative YouTube-DL initialization")
                    }
                    error.message.contains("library", ignoreCase = true) -> {
                        logger.d(TAG, "Suggesting reset and restart for YouTube-DL library error")
                        RecoveryAction.ResetAndRestart
                    }
                    error.message.contains("python", ignoreCase = true) -> {
                        logger.d(TAG, "Suggesting Python environment recreation for YouTube-DL Python error")
                        RecoveryAction.RecreateDirectories(listOf("python", "native_libs"))
                    }
                    else -> {
                        logger.d(TAG, "No recovery available for YouTube-DL error")
                        RecoveryAction.NoRecovery("YouTube-DL errors typically require app restart")
                    }
                }
            }
            
            is InitializationError.GenericError -> {
                if (error.recoverable) {
                    logger.d(TAG, "Suggesting retry for recoverable generic error")
                    RecoveryAction.Retry()
                } else {
                    logger.d(TAG, "No recovery available for non-recoverable generic error")
                    RecoveryAction.NoRecovery("Generic non-recoverable error")
                }
            }
        }
    }

    override fun logError(error: InitializationError) {
        val errorType = error::class.simpleName
        val isRecoverable = error.recoverable
        val hasUnderlying = error.cause != null
        
        val contextInfo = mapOf<String, Any>(
            "errorType" to (errorType ?: "Unknown"),
            "recoverable" to isRecoverable,
            "hasUnderlyingCause" to hasUnderlying,
            "category" to categorizeError(error).name
        )
        
        logger.logError(
            tag = TAG,
            context = "Initialization Error",
            error = error.cause ?: RuntimeException(error.message),
            additionalInfo = contextInfo
        )
        
        // Log specific details based on error type
        when (error) {
            is InitializationError.NativeLibraryError -> {
                logger.w(TAG, "Native Library Error: ${error.message}")
                if (error.cause != null) {
                    logger.w(TAG, "Underlying cause: ${error.cause.message}", error.cause)
                }
            }
            
            is InitializationError.PythonEnvironmentError -> {
                logger.w(TAG, "Python Environment Error: ${error.message}")
                if (error.cause != null) {
                    logger.w(TAG, "Underlying cause: ${error.cause.message}", error.cause)
                }
            }
            
            is InitializationError.FileExtractionError -> {
                logger.w(TAG, "File Extraction Error: ${error.message}")
                if (error.cause != null) {
                    logger.w(TAG, "Underlying cause: ${error.cause.message}", error.cause)
                }
            }
            
            is InitializationError.YouTubeDLError -> {
                logger.e(TAG, "YouTube-DL Error (Critical): ${error.message}")
                if (error.cause != null) {
                    logger.e(TAG, "Underlying cause: ${error.cause.message}", error.cause)
                }
            }
            
            is InitializationError.GenericError -> {
                if (error.recoverable) {
                    logger.w(TAG, "Generic Error (Recoverable): ${error.message}")
                } else {
                    logger.e(TAG, "Generic Error (Critical): ${error.message}")
                }
                if (error.cause != null) {
                    logger.w(TAG, "Underlying cause: ${error.cause.message}", error.cause)
                }
            }
        }
    }

    override fun categorizeError(error: InitializationError): ErrorCategory {
        return when (error) {
            is InitializationError.NativeLibraryError -> {
                when {
                    error.message.contains("permission", ignoreCase = true) -> ErrorCategory.USER_INTERVENTION_REQUIRED
                    error.message.contains("missing", ignoreCase = true) -> ErrorCategory.RECOVERABLE
                    error.message.contains("extract", ignoreCase = true) -> ErrorCategory.RECOVERABLE
                    error.message.contains("load", ignoreCase = true) -> ErrorCategory.TRANSIENT
                    else -> ErrorCategory.RECOVERABLE // Default to recoverable for native library errors
                }
            }
            
            is InitializationError.PythonEnvironmentError -> {
                when {
                    error.message.contains("directory", ignoreCase = true) -> ErrorCategory.RECOVERABLE
                    error.message.contains("extraction", ignoreCase = true) -> ErrorCategory.RECOVERABLE
                    error.message.contains("permission", ignoreCase = true) -> ErrorCategory.USER_INTERVENTION_REQUIRED
                    else -> ErrorCategory.CONFIGURATION
                }
            }
            
            is InitializationError.FileExtractionError -> {
                when {
                    error.message.contains("permission", ignoreCase = true) -> ErrorCategory.USER_INTERVENTION_REQUIRED
                    error.message.contains("space", ignoreCase = true) -> ErrorCategory.USER_INTERVENTION_REQUIRED
                    error.message.contains("APK", ignoreCase = true) -> ErrorCategory.CRITICAL
                    else -> ErrorCategory.RECOVERABLE
                }
            }
            
            is InitializationError.YouTubeDLError -> {
                when {
                    error.message.contains("incompatible", ignoreCase = true) -> ErrorCategory.CRITICAL
                    error.message.contains("missing", ignoreCase = true) -> ErrorCategory.CRITICAL
                    error.message.contains("initialization", ignoreCase = true) -> ErrorCategory.TRANSIENT
                    else -> ErrorCategory.CRITICAL
                }
            }
            
            is InitializationError.GenericError -> {
                if (error.recoverable) {
                    ErrorCategory.RECOVERABLE
                } else {
                    ErrorCategory.UNKNOWN
                }
            }
        }
    }

    override fun isRecoverable(error: InitializationError): Boolean {
        val category = categorizeError(error)
        val baseRecoverable = error.recoverable
        
        // Override based on category
        val categoryRecoverable = when (category) {
            ErrorCategory.RECOVERABLE, ErrorCategory.TRANSIENT -> true
            ErrorCategory.CRITICAL -> false
            ErrorCategory.USER_INTERVENTION_REQUIRED -> false
            ErrorCategory.CONFIGURATION -> true
            ErrorCategory.UNKNOWN -> baseRecoverable
        }
        
        logger.d(TAG, "Error recoverable: base=$baseRecoverable, category=$categoryRecoverable, final=${baseRecoverable && categoryRecoverable}")
        
        return baseRecoverable && categoryRecoverable
    }
}