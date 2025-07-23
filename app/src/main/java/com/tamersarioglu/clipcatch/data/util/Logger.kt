package com.tamersarioglu.clipcatch.data.util

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Logger @Inject constructor() {
    
    companion object {
        private const val APP_TAG = "ClipCatch"
        private const val MAX_TAG_LENGTH = 23
        private const val DEBUG_ENABLED = true
    }
    
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (DEBUG_ENABLED) {
            val formattedTag = formatTag(tag)
            if (throwable != null) {
                Log.d(formattedTag, message, throwable)
            } else {
                Log.d(formattedTag, message)
            }
        }
    }
    
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        val formattedTag = formatTag(tag)
        if (throwable != null) {
            Log.i(formattedTag, message, throwable)
        } else {
            Log.i(formattedTag, message)
        }
    }
    
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val formattedTag = formatTag(tag)
        if (throwable != null) {
            Log.w(formattedTag, message, throwable)
        } else {
            Log.w(formattedTag, message)
        }
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val formattedTag = formatTag(tag)
        if (throwable != null) {
            Log.e(formattedTag, message, throwable)
        } else {
            Log.e(formattedTag, message)
        }
    }
    
    fun v(tag: String, message: String, throwable: Throwable? = null) {
        if (DEBUG_ENABLED) {
            val formattedTag = formatTag(tag)
            if (throwable != null) {
                Log.v(formattedTag, message, throwable)
            } else {
                Log.v(formattedTag, message)
            }
        }
    }
    
    fun enter(tag: String, methodName: String, vararg params: Any?) {
        if (DEBUG_ENABLED) {
            val paramString = if (params.isNotEmpty()) {
                params.joinToString(", ") { it?.toString() ?: "null" }
            } else {
                ""
            }
            d(tag, "→ $methodName($paramString)")
        }
    }
    
    fun exit(tag: String, methodName: String, result: Any? = null) {
        if (DEBUG_ENABLED) {
            val resultString = result?.toString() ?: "void"
            d(tag, "← $methodName returns: $resultString")
        }
    }
    
    fun logNetworkRequest(tag: String, method: String, url: String, headers: Map<String, String>? = null) {
        if (DEBUG_ENABLED) {
            d(tag, "🌐 $method $url")
            headers?.forEach { (key, value) ->
                d(tag, "   Header: $key = $value")
            }
        }
    }
    
    fun logNetworkResponse(tag: String, code: Int, url: String, responseTime: Long? = null) {
        if (DEBUG_ENABLED) {
            val timeString = responseTime?.let { " (${it}ms)" } ?: ""
            d(tag, "🌐 Response: $code for $url$timeString")
        }
    }
    
    fun logDownloadProgress(tag: String, url: String, progress: Int, bytesDownloaded: Long? = null) {
        if (DEBUG_ENABLED) {
            val bytesString = bytesDownloaded?.let { " (${formatBytes(it)})" } ?: ""
            d(tag, "⬇️ Download progress: $progress%$bytesString - $url")
        }
    }
    
    fun logFileOperation(tag: String, operation: String, filePath: String, success: Boolean) {
        val emoji = if (success) "✅" else "❌"
        val status = if (success) "SUCCESS" else "FAILED"
        i(tag, "$emoji File $operation $status: $filePath")
    }
    
    fun logPermissionRequest(tag: String, permission: String, granted: Boolean) {
        val emoji = if (granted) "✅" else "❌"
        val status = if (granted) "GRANTED" else "DENIED"
        i(tag, "$emoji Permission $status: $permission")
    }
    
    fun logUserAction(tag: String, action: String, details: String? = null) {
        val message = if (details != null) {
            "👤 User action: $action - $details"
        } else {
            "👤 User action: $action"
        }
        i(tag, message)
    }
    
    fun logError(tag: String, context: String, error: Throwable, additionalInfo: Map<String, Any>? = null) {
        val message = buildString {
            append("❌ Error in $context: ${error.message}")
            additionalInfo?.forEach { (key, value) ->
                append("\n   $key: $value")
            }
        }
        e(tag, message, error)
    }
    
    fun logPerformance(tag: String, operation: String, durationMs: Long, additionalMetrics: Map<String, Any>? = null) {
        if (DEBUG_ENABLED) {
            val message = buildString {
                append("⏱️ Performance: $operation took ${durationMs}ms")
                additionalMetrics?.forEach { (key, value) ->
                    append("\n   $key: $value")
                }
            }
            d(tag, message)
        }
    }
    
    private fun formatTag(tag: String): String {
        val fullTag = "$APP_TAG:$tag"
        return if (fullTag.length > MAX_TAG_LENGTH) {
            fullTag.take(MAX_TAG_LENGTH)
        } else {
            fullTag
        }
    }
    
    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return "%.1f %s".format(size, units[unitIndex])
    }
}

inline fun <T> T.logDebug(tag: String, message: String, throwable: Throwable? = null): T {
    Logger().d(tag, message, throwable)
    return this
}

inline fun <T> T.logInfo(tag: String, message: String, throwable: Throwable? = null): T {
    Logger().i(tag, message, throwable)
    return this
}

inline fun <T> T.logWarning(tag: String, message: String, throwable: Throwable? = null): T {
    Logger().w(tag, message, throwable)
    return this
}

inline fun <T> T.logError(tag: String, message: String, throwable: Throwable? = null): T {
    Logger().e(tag, message, throwable)
    return this
}