package com.tamersarioglu.clipcatch.domain.model

/**
 * Base sealed class for all initialization errors.
 * @param message Human-readable error message
 * @param cause The underlying exception that caused this error, if any
 * @param recoverable Whether this error can potentially be recovered from
 */
sealed class InitializationError(
    val message: String,
    val cause: Throwable? = null,
    val recoverable: Boolean = false
) {
    /**
     * Error related to native library extraction or loading.
     */
    class NativeLibraryError(
        message: String, 
        cause: Throwable? = null
    ) : InitializationError(message, cause, true)
    
    /**
     * Error related to Python environment setup.
     */
    class PythonEnvironmentError(
        message: String, 
        cause: Throwable? = null
    ) : InitializationError(message, cause, true)
    
    /**
     * Error related to YouTube-DL initialization.
     */
    class YouTubeDLError(
        message: String, 
        cause: Throwable? = null
    ) : InitializationError(message, cause, false)
    
    /**
     * Error related to file extraction operations.
     */
    class FileExtractionError(
        message: String, 
        cause: Throwable? = null
    ) : InitializationError(message, cause, true)
    
    /**
     * Generic initialization error for cases not covered by specific error types.
     */
    class GenericError(
        message: String, 
        cause: Throwable? = null, 
        recoverable: Boolean = false
    ) : InitializationError(message, cause, recoverable)
}