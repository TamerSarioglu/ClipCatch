package com.tamersarioglu.clipcatch.data.service

import com.tamersarioglu.clipcatch.domain.model.InitializationError

/**
 * Interface for handling initialization errors with categorization and recovery suggestions.
 * Provides centralized error handling and recovery logic for initialization failures.
 */
interface InitializationErrorHandler {

    /**
     * Handles an initialization error by processing, categorizing, and logging it.
     *
     * @param error The initialization error to handle
     * @return ErrorHandlingResult containing the processing outcome
     */
    fun handleError(error: InitializationError): ErrorHandlingResult

    /**
     * Suggests a recovery action for the given initialization error.
     *
     * @param error The initialization error to analyze
     * @return RecoveryAction if recovery is possible, null otherwise
     */
    fun suggestRecoveryAction(error: InitializationError): RecoveryAction?

    /**
     * Logs an initialization error with appropriate context and severity.
     *
     * @param error The initialization error to log
     */
    fun logError(error: InitializationError)

    /**
     * Categorizes an error based on its type and characteristics.
     *
     * @param error The initialization error to categorize
     * @return ErrorCategory representing the error classification
     */
    fun categorizeError(error: InitializationError): ErrorCategory

    /**
     * Determines if an error is recoverable based on its type and context.
     *
     * @param error The initialization error to analyze
     * @return true if the error can potentially be recovered from
     */
    fun isRecoverable(error: InitializationError): Boolean
}

/** Result of error handling processing. */
sealed class ErrorHandlingResult {
    /**
     * Error was successfully processed and handled.
     * @param category The category assigned to the error
     * @param recoveryAction Suggested recovery action, if any
     */
    data class Handled(val category: ErrorCategory, val recoveryAction: RecoveryAction?) :
            ErrorHandlingResult()

    /**
     * Error processing failed.
     * @param reason The reason why error handling failed
     */
    data class Failed(val reason: String) : ErrorHandlingResult()
}

/** Categories for different types of initialization errors. */
enum class ErrorCategory {
    /** Errors that can be automatically recovered from. */
    RECOVERABLE,

    /** Errors that require user intervention or configuration changes. */
    USER_INTERVENTION_REQUIRED,

    /** Critical errors that cannot be recovered from. */
    CRITICAL,

    /** Temporary errors that may resolve themselves. */
    TRANSIENT,

    /** Errors related to system configuration or environment. */
    CONFIGURATION,

    /** Unknown or unclassified errors. */
    UNKNOWN
}

/** Suggested recovery actions for initialization errors. */
sealed class RecoveryAction {
    /**
     * Retry the failed operation.
     * @param maxAttempts Maximum number of retry attempts
     * @param delayMs Delay between retry attempts in milliseconds
     */
    data class Retry(val maxAttempts: Int = 3, val delayMs: Long = 1000L) : RecoveryAction()

    /**
     * Re-extract files from the APK.
     * @param targetPattern Pattern of files to re-extract
     */
    data class ReExtractFiles(val targetPattern: String) : RecoveryAction()

    /**
     * Clear and recreate directories.
     * @param directories List of directory paths to recreate
     */
    data class RecreateDirectories(val directories: List<String>) : RecoveryAction()

    /**
     * Use alternative initialization method.
     * @param alternativeMethod Description of the alternative method
     */
    data class UseAlternativeMethod(val alternativeMethod: String) : RecoveryAction()

    /** Reset the initialization state and start over. */
    object ResetAndRestart : RecoveryAction()

    /**
     * No recovery action available.
     * @param reason Explanation of why no recovery is possible
     */
    data class NoRecovery(val reason: String) : RecoveryAction()
}
