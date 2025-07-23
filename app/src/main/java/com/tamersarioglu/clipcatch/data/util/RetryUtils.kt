package com.tamersarioglu.clipcatch.data.util

import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

@Singleton
class RetryUtils @Inject constructor(
    private val logger: Logger
) {
    
    companion object {
        private const val TAG = "RetryUtils"
        private const val DEFAULT_MAX_ATTEMPTS = 3
        private const val DEFAULT_BASE_DELAY_MS = 1000L
        private const val DEFAULT_MAX_DELAY_MS = 30000L
        private const val DEFAULT_BACKOFF_MULTIPLIER = 2.0
        private const val DEFAULT_JITTER_FACTOR = 0.1
    }
    
    suspend fun <T> executeWithRetry(
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        baseDelayMs: Long = DEFAULT_BASE_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        backoffMultiplier: Double = DEFAULT_BACKOFF_MULTIPLIER,
        jitterFactor: Double = DEFAULT_JITTER_FACTOR,
        retryCondition: (Throwable) -> Boolean = ::isRetryableException,
        operation: suspend (attempt: Int) -> T
    ): T {
        var lastException: Throwable? = null
        
        repeat(maxAttempts) { attempt ->
            try {
                logger.d(TAG, "Executing operation, attempt ${attempt + 1}/$maxAttempts")
                return operation(attempt + 1)
            } catch (e: Exception) {
                lastException = e
                logger.w(TAG, "Operation failed on attempt ${attempt + 1}/$maxAttempts", e)
                
                // Check if we should retry this exception
                if (!retryCondition(e)) {
                    logger.i(TAG, "Exception is not retryable, failing immediately")
                    throw e
                }
                
                // If this is the last attempt, don't delay
                if (attempt == maxAttempts - 1) {
                    logger.w(TAG, "Max attempts reached, failing")
                    throw e
                }
                
                // Calculate delay with exponential backoff and jitter
                val delay = calculateDelay(
                    attempt = attempt,
                    baseDelayMs = baseDelayMs,
                    maxDelayMs = maxDelayMs,
                    backoffMultiplier = backoffMultiplier,
                    jitterFactor = jitterFactor
                )
                
                logger.d(TAG, "Waiting ${delay}ms before retry")
                delay(delay)
            }
        }
        
        // If we get here, all attempts failed
        throw lastException ?: RuntimeException("All retry attempts failed")
    }
    
    suspend fun <T> executeWithLinearRetry(
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        delayMs: Long = DEFAULT_BASE_DELAY_MS,
        retryCondition: (Throwable) -> Boolean = ::isRetryableException,
        operation: suspend (attempt: Int) -> T
    ): T {
        return executeWithRetry(
            maxAttempts = maxAttempts,
            baseDelayMs = delayMs,
            maxDelayMs = delayMs,
            backoffMultiplier = 1.0,
            jitterFactor = 0.0,
            retryCondition = retryCondition,
            operation = operation
        )
    }
    
    suspend fun <T> executeWithImmediateRetry(
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        retryCondition: (Throwable) -> Boolean = ::isRetryableException,
        operation: suspend (attempt: Int) -> T
    ): T {
        return executeWithRetry(
            maxAttempts = maxAttempts,
            baseDelayMs = 0L,
            maxDelayMs = 0L,
            backoffMultiplier = 1.0,
            jitterFactor = 0.0,
            retryCondition = retryCondition,
            operation = operation
        )
    }
    
    private fun calculateDelay(
        attempt: Int,
        baseDelayMs: Long,
        maxDelayMs: Long,
        backoffMultiplier: Double,
        jitterFactor: Double
    ): Long {
        val exponentialDelay = baseDelayMs * backoffMultiplier.pow(attempt.toDouble())
        val cappedDelay = min(exponentialDelay, maxDelayMs.toDouble())
        val jitter = cappedDelay * jitterFactor * (Random.nextDouble() - 0.5) * 2
        return (cappedDelay + jitter).toLong().coerceAtLeast(0L)
    }
    
    private fun isRetryableException(exception: Throwable): Boolean {
        return when (exception) {
            is java.net.SocketTimeoutException -> true
            is java.net.ConnectException -> true
            is java.net.UnknownHostException -> true
            is java.io.IOException -> {
                val message = exception.message?.lowercase() ?: ""
                when {
                    message.contains("timeout") -> true
                    message.contains("connection") -> true
                    message.contains("network") -> true
                    message.contains("host") -> true
                    message.contains("permission") -> false
                    message.contains("space") -> false
                    else -> true
                }
            }
            is NetworkException.TimeoutException -> true
            is NetworkException.ServerException -> {
                exception.code >= 500
            }
            is NetworkException.GenericNetworkException -> true
            is NetworkException.NoInternetException -> true
            is SecurityException -> false
            is IllegalArgumentException -> false
            else -> false
        }
    }
    
    fun createRetryCondition(vararg retryableExceptions: Class<out Throwable>): (Throwable) -> Boolean {
        return { exception ->
            retryableExceptions.any { it.isInstance(exception) }
        }
    }
    
    fun createMessageBasedRetryCondition(vararg retryableMessages: String): (Throwable) -> Boolean {
        return { exception ->
            val message = exception.message?.lowercase() ?: ""
            retryableMessages.any { retryableMessage ->
                message.contains(retryableMessage.lowercase())
            }
        }
    }
    
    fun combineRetryConditions(
        vararg conditions: (Throwable) -> Boolean
    ): (Throwable) -> Boolean {
        return { exception ->
            conditions.any { condition -> condition(exception) }
        }
    }
}

data class RetryConfig(
    val maxAttempts: Int = 3,
    val baseDelayMs: Long = 1000L,
    val maxDelayMs: Long = 30000L,
    val backoffMultiplier: Double = 2.0,
    val jitterFactor: Double = 0.1,
    val retryCondition: (Throwable) -> Boolean = { true }
)

suspend fun <T> retryOperation(
    config: RetryConfig = RetryConfig(),
    operation: suspend (attempt: Int) -> T
): T {
    return RetryUtils(Logger()).executeWithRetry(
        maxAttempts = config.maxAttempts,
        baseDelayMs = config.baseDelayMs,
        maxDelayMs = config.maxDelayMs,
        backoffMultiplier = config.backoffMultiplier,
        jitterFactor = config.jitterFactor,
        retryCondition = config.retryCondition,
        operation = operation
    )
}