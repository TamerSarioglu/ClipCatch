package com.tamersarioglu.clipcatch.util

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

sealed class NetworkException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    
    class NoInternetException(message: String = "No internet connection available") : NetworkException(message)
    
    class TimeoutException(message: String = "Request timeout", cause: Throwable? = null) : NetworkException(message, cause)
    
    class ServerException(val code: Int, message: String = "Server error: $code") : NetworkException(message)
    
    class ClientException(val code: Int, message: String = "Client error: $code") : NetworkException(message)
    
    class UnknownHostException(message: String = "Unable to resolve host", cause: Throwable? = null) : NetworkException(message, cause)
    
    class SSLException(message: String = "SSL/TLS error", cause: Throwable? = null) : NetworkException(message, cause)
    
    class GenericNetworkException(message: String = "Network error", cause: Throwable? = null) : NetworkException(message, cause)
}

fun Throwable.toNetworkException(): NetworkException {
    return when (this) {
        is NetworkException -> this
        is SocketTimeoutException -> NetworkException.TimeoutException(cause = this)
        is UnknownHostException -> NetworkException.UnknownHostException(cause = this)
        is SSLException -> NetworkException.SSLException(cause = this)
        else -> NetworkException.GenericNetworkException(message = this.message ?: "Unknown network error", cause = this)
    }
}