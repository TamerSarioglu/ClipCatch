package com.tamersarioglu.clipcatch.data.util

import android.util.Log
import com.tamersarioglu.clipcatch.domain.model.DownloadError
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

@Singleton
class ErrorHandler @Inject constructor() {
    
    companion object {
        private const val TAG = "ErrorHandler"
    }
    
    fun mapExceptionToDownloadError(exception: Throwable): DownloadError {
        return when (exception) {
            is com.tamersarioglu.clipcatch.data.service.YouTubeExtractionException -> exception.error
            is NetworkException -> mapNetworkExceptionToDownloadError(exception)
            is SecurityException -> DownloadError.PERMISSION_DENIED
            is UnknownHostException -> DownloadError.NETWORK_ERROR
            is SocketTimeoutException -> DownloadError.NETWORK_ERROR
            is SSLException -> DownloadError.NETWORK_ERROR
            is IOException -> {
                when {
                    exception.message?.contains("space", ignoreCase = true) == true -> 
                        DownloadError.INSUFFICIENT_STORAGE
                    exception.message?.contains("permission", ignoreCase = true) == true -> 
                        DownloadError.PERMISSION_DENIED
                    else -> DownloadError.STORAGE_ERROR
                }
            }
            is IllegalArgumentException -> {
                when {
                    exception.message?.contains("url", ignoreCase = true) == true -> 
                        DownloadError.INVALID_URL
                    else -> DownloadError.UNKNOWN_ERROR
                }
            }
            else -> DownloadError.UNKNOWN_ERROR
        }
    }
    
    private fun mapNetworkExceptionToDownloadError(exception: NetworkException): DownloadError {
        return when (exception) {
            is NetworkException.NoInternetException -> DownloadError.NETWORK_ERROR
            is NetworkException.TimeoutException -> DownloadError.NETWORK_ERROR
            is NetworkException.ServerException -> {
                when (exception.code) {
                    403 -> DownloadError.VIDEO_UNAVAILABLE
                    404 -> DownloadError.VIDEO_UNAVAILABLE
                    429 -> DownloadError.NETWORK_ERROR
                    in 500..599 -> DownloadError.NETWORK_ERROR
                    else -> DownloadError.UNKNOWN_ERROR
                }
            }
            is NetworkException.ClientException -> {
                when (exception.code) {
                    400 -> DownloadError.INVALID_URL
                    401 -> DownloadError.VIDEO_UNAVAILABLE
                    403 -> DownloadError.VIDEO_UNAVAILABLE
                    404 -> DownloadError.VIDEO_UNAVAILABLE
                    else -> DownloadError.UNKNOWN_ERROR
                }
            }
            is NetworkException.UnknownHostException -> DownloadError.NETWORK_ERROR
            is NetworkException.SSLException -> DownloadError.NETWORK_ERROR
            is NetworkException.GenericNetworkException -> DownloadError.NETWORK_ERROR
        }
    }
    
    fun getErrorMessage(error: DownloadError): String {
        return when (error) {
            DownloadError.INVALID_URL ->
                "The provided URL is not valid. Please check the YouTube URL and try again."
            DownloadError.NETWORK_ERROR ->
                "Network connection error. Please check your internet connection and try again."
            DownloadError.STORAGE_ERROR ->
                "Unable to save the file. Please check storage permissions and available space."
            DownloadError.PERMISSION_DENIED ->
                "Storage permission is required to download videos. Please grant permission and try again."
            DownloadError.VIDEO_UNAVAILABLE ->
                "This video cannot be downloaded. The YouTube extraction service is currently unavailable."
            DownloadError.INSUFFICIENT_STORAGE ->
                "Not enough storage space available. Please free up some space and try again."
            DownloadError.AGE_RESTRICTED ->
                "This video is age-restricted and cannot be downloaded."
            DownloadError.GEO_BLOCKED ->
                "This video is not available in your region."
            DownloadError.UNKNOWN_ERROR ->
                "An unexpected error occurred. Please try again."
        }
    }
    
    fun getDetailedErrorMessage(error: DownloadError, exception: Throwable?): String {
        val baseMessage = getErrorMessage(error)
        val exceptionMessage = exception?.message
        
        return if (exceptionMessage != null && exceptionMessage.isNotBlank()) {
            "$baseMessage\nDetails: $exceptionMessage"
        } else {
            baseMessage
        }
    }
    
    fun isRecoverableError(error: DownloadError): Boolean {
        return when (error) {
            DownloadError.NETWORK_ERROR -> true
            DownloadError.UNKNOWN_ERROR -> true
            DownloadError.STORAGE_ERROR -> false
            DownloadError.PERMISSION_DENIED -> false
            DownloadError.INVALID_URL -> false
            DownloadError.VIDEO_UNAVAILABLE -> false
            DownloadError.INSUFFICIENT_STORAGE -> false
            DownloadError.AGE_RESTRICTED -> false
            DownloadError.GEO_BLOCKED -> false
        }
    }
    
    fun getRecoveryAction(error: DownloadError): ErrorRecoveryAction {
        return when (error) {
            DownloadError.NETWORK_ERROR -> ErrorRecoveryAction.RETRY
            DownloadError.STORAGE_ERROR -> ErrorRecoveryAction.CHECK_STORAGE
            DownloadError.PERMISSION_DENIED -> ErrorRecoveryAction.REQUEST_PERMISSION
            DownloadError.INVALID_URL -> ErrorRecoveryAction.CORRECT_URL
            DownloadError.VIDEO_UNAVAILABLE -> ErrorRecoveryAction.TRY_DIFFERENT_VIDEO
            DownloadError.INSUFFICIENT_STORAGE -> ErrorRecoveryAction.FREE_STORAGE
            DownloadError.AGE_RESTRICTED -> ErrorRecoveryAction.TRY_DIFFERENT_VIDEO
            DownloadError.GEO_BLOCKED -> ErrorRecoveryAction.TRY_DIFFERENT_VIDEO
            DownloadError.UNKNOWN_ERROR -> ErrorRecoveryAction.RETRY
        }
    }
    
    fun logError(
        tag: String,
        message: String,
        exception: Throwable? = null,
        error: DownloadError? = null
    ) {
        val logMessage = buildString {
            append(message)
            error?.let { append(" [Error: ${it.name}]") }
            exception?.let { append(" [Exception: ${it.javaClass.simpleName}]") }
        }
        
        when (error) {
            DownloadError.NETWORK_ERROR,
            DownloadError.UNKNOWN_ERROR -> {
                Log.w(tag, logMessage, exception)
            }
            DownloadError.PERMISSION_DENIED,
            DownloadError.STORAGE_ERROR,
            DownloadError.INSUFFICIENT_STORAGE -> {
                Log.e(tag, logMessage, exception)
            }
            else -> {
                Log.i(tag, logMessage, exception)
            }
        }
    }
    
    fun <T> createErrorResult(
        error: DownloadError,
        exception: Throwable? = null,
        tag: String = TAG
    ): Result<T> {
        val message = getDetailedErrorMessage(error, exception)
        logError(tag, "Operation failed", exception, error)
        return Result.failure(ErrorException(error, message, exception))
    }
    
    suspend fun <T> handleSuspendOperation(
        tag: String = TAG,
        operation: suspend () -> T
    ): Result<T> {
        return try {
            Result.success(operation())
        } catch (e: Exception) {
            val error = mapExceptionToDownloadError(e)
            createErrorResult(error, e, tag)
        }
    }
    
    fun <T> handleOperation(
        tag: String = TAG,
        operation: () -> T
    ): Result<T> {
        return try {
            Result.success(operation())
        } catch (e: Exception) {
            val error = mapExceptionToDownloadError(e)
            createErrorResult(error, e, tag)
        }
    }
}

enum class ErrorRecoveryAction {
    RETRY,
    REQUEST_PERMISSION,
    CHECK_STORAGE,
    FREE_STORAGE,
    CORRECT_URL,
    TRY_DIFFERENT_VIDEO,
    CONTACT_SUPPORT
}

class ErrorException(
    val error: DownloadError,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)