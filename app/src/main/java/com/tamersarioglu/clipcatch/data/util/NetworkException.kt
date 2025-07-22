package com.tamersarioglu.clipcatch.data.util

import java.io.IOException

/**
 * Custom exception class for network-related errors
 */
sealed class NetworkException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    
    /**
     * No internet connection available
     */
    class NoInternetException(message: String = "No internet connection available") : NetworkException(message)
    
    /**
     * Request timeout
     */
    class TimeoutException(message: String = "Request timeout", cause: Throwable? = null) : NetworkException(message, cause)
    
    /**
     * Server error (5xx)
     */
    class ServerException(val code: Int, message: String = "Server error: $code") : NetworkException(message)
    
    /**
     * Client error (4xx)
     */
    class ClientException(val code: Int, message: String = "Client error: $code") : NetworkException(message)
    
    /**
     * Unknown host error
     */
    class UnknownHostException(message: String = "Unable to resolve host", cause: Throwable? = null) : NetworkException(message, cause)
    
    /**
     * SSL/TLS error
     */
    class SSLException(message: String = "SSL/TLS error", cause: Throwable? = null) : NetworkException(message, cause)
    
    /**
     * Generic network error
     */
    class GenericNetworkException(message: String = "Network error", cause: Throwable? = null) : NetworkException(message, cause)
}

/**
 * Extension function to convert generic exceptions to NetworkException
 */
fun Throwable.toNetworkException(): NetworkException {
    return when (this) {
        is NetworkException -> this
        is java.net.SocketTimeoutException -> NetworkException.TimeoutException(cause = this)
        is java.net.UnknownHostException -> NetworkException.UnknownHostException(cause = this)
        is javax.net.ssl.SSLException -> NetworkException.SSLException(cause = this)
        else -> NetworkException.GenericNetworkException(message = this.message ?: "Unknown network error", cause = this)
    }
}