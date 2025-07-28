package com.tamersarioglu.clipcatch.data.service

import android.content.Context
import android.util.Log
import com.tamersarioglu.clipcatch.data.dto.VideoInfoDto
import com.tamersarioglu.clipcatch.domain.model.DownloadError
import com.tamersarioglu.clipcatch.domain.model.InitializationStatus
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

interface YouTubeExtractorService {

    suspend fun extractVideoInfo(url: String): VideoInfoDto
    fun isValidYouTubeUrl(url: String): Boolean
    fun isYoutubeDLAvailable(): Boolean
}

@Singleton
class YouTubeExtractorServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val initializationOrchestrator: InitializationOrchestrator
) : YouTubeExtractorService {
    
    companion object {
        private val YOUTUBE_URL_PATTERNS = listOf(
            Regex("^https?://(www\\.)?youtube\\.com/watch\\?v=([a-zA-Z0-9_-]+).*$"),
            Regex("^https?://(www\\.)?youtu\\.be/([a-zA-Z0-9_-]+).*$"),
            Regex("^https?://(www\\.)?youtube\\.com/shorts/([a-zA-Z0-9_-]+).*$"),
            Regex("^https?://(m\\.)?youtube\\.com/watch\\?v=([a-zA-Z0-9_-]+).*$"),
            Regex("^https?://(www\\.)?youtube\\.com/embed/([a-zA-Z0-9_-]+).*$"),
            Regex("^https?://(www\\.)?youtube\\.com/live/([a-zA-Z0-9_-]+).*$")
        )
    }

    override suspend fun extractVideoInfo(url: String): VideoInfoDto = withContext(Dispatchers.IO) {
        try {
            Log.d("YouTubeExtractor", "Starting video info extraction for URL: $url")
            
            if (!isValidYouTubeUrl(url)) {
                throw YouTubeExtractionException(
                    DownloadError.INVALID_URL,
                    "Invalid or unsupported YouTube URL format: $url"
                )
            }
            
            // Ensure YouTube-DL is initialized before proceeding
            initializationOrchestrator.initialize()
            if (!initializationOrchestrator.isInitializationComplete()) {
                Log.e("YouTubeExtractor", "YouTube-DL not available - initialization incomplete")
                throw YouTubeExtractionException(
                    DownloadError.UNKNOWN_ERROR,
                    "YouTube-DL initialization failed. Please reinstall the app or contact support."
                )
            }
            
            // Use retry mechanism for format fallback
            return@withContext extractWithRetry(url)
            
        } catch (e: YouTubeExtractionException) {
            Log.e("YouTubeExtractor", "YouTube extraction exception: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            Log.e("YouTubeExtractor", "Unexpected exception during extraction", e)
            throw YouTubeExtractionException(
                DownloadError.UNKNOWN_ERROR,
                "Failed to extract video information: ${e.message}",
                e
            )
        }
    }
    

    
    override fun isValidYouTubeUrl(url: String): Boolean {
        if (url.isBlank() || url.length > 2048) return false
        
        val normalizedUrl = url.trim()
        
        return YOUTUBE_URL_PATTERNS.any { pattern ->
            pattern.matches(normalizedUrl)
        }
    }

    override fun isYoutubeDLAvailable(): Boolean {
        return try {
            // Check if initialization is complete
            val status = initializationOrchestrator.getInitializationStatus()
            if (status !is InitializationStatus.Completed) {
                Log.w("YouTubeExtractor", "YouTube-DL initialization not complete: $status")
                return false
            }
            
            // Try to get version as a test of functionality
            val youtubeDLInstance = YoutubeDL.getInstance()
            val version = youtubeDLInstance.version(context)
            Log.d("YouTubeExtractor", "YouTube-DL is available, version: $version")
            
            version != null
        } catch (e: Exception) {
            Log.e("YouTubeExtractor", "YouTube-DL availability check failed", e)
            false
        }
    }

    private suspend fun extractWithRetry(url: String, maxAttempts: Int = 3): VideoInfoDto {
        var lastException: Exception? = null
        
        for (attempt in 1..maxAttempts) {
            try {
                Log.d("YouTubeExtractor", "Extraction attempt $attempt for URL: $url")
                return performExtraction(url, attempt)
            } catch (e: YouTubeExtractionException) {
                if (isFormatRelatedError(e) && attempt < maxAttempts) {
                    Log.w("YouTubeExtractor", "Format error on attempt $attempt, retrying with different strategy...")
                    lastException = e
                    continue
                }
                throw e
            } catch (e: Exception) {
                Log.e("YouTubeExtractor", "Unexpected error on attempt $attempt", e)
                throw YouTubeExtractionException(
                    DownloadError.UNKNOWN_ERROR,
                    "Failed to extract video information: ${e.message}",
                    e
                )
            }
        }
        
        throw lastException ?: YouTubeExtractionException(
            DownloadError.UNKNOWN_ERROR, 
            "All format selection attempts failed for URL: $url"
        )
    }

    private suspend fun performExtraction(url: String, attempt: Int): VideoInfoDto {
        Log.d("YouTubeExtractor", "Using YouTube-DL for video info extraction (attempt $attempt)...")
        
        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-json")
            addOption("--no-playlist")
            addOption("--no-check-certificate")
            addOption("--prefer-free-formats")
            addOption("--add-header", "referer:youtube.com")
            addOption("--add-header", "user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            addOption("--no-warnings")
            addOption("--quiet")
            addOption("--ignore-errors")
            
            // Configure format selection based on attempt number
            configureFormatSelection(this, attempt)
        }
        
        val response = YoutubeDL.getInstance().execute(request)
        
        if (response.exitCode != 0) {
            val errorMessage = response.err
            Log.e("YouTubeExtractor", "YouTube-DL failed with exit code ${response.exitCode}: $errorMessage")
            
            // Check if this is just a Python deprecation warning but extraction still worked
            val jsonOutput = response.out
            if (jsonOutput.isNotBlank() && errorMessage.contains("python version", ignoreCase = true) && errorMessage.contains("deprecated", ignoreCase = true)) {
                Log.w("YouTubeExtractor", "Python deprecation warning detected but extraction succeeded, continuing...")
            } else {
                handleYoutubeDLError(errorMessage, url)
            }
        }
        
        val jsonOutput = response.out
        if (jsonOutput.isBlank()) {
            throw YouTubeExtractionException(
                DownloadError.UNKNOWN_ERROR,
                "YouTube-DL returned empty output for URL: $url"
            )
        }
        
        Log.d("YouTubeExtractor", "Parsing YouTube-DL JSON response...")
        val videoJson = JSONObject(jsonOutput)
        
        val videoId = videoJson.optString("id", "")
        val title = videoJson.optString("title", "Unknown Title")
        val duration = videoJson.optLong("duration", 0L)
        val thumbnailUrl = videoJson.optString("thumbnail")
        val fileSize = videoJson.optLong("filesize", 0L).takeIf { it > 0 }
        val format = videoJson.optString("ext", "mp4")
        
        // Get the actual download URL
        val downloadUrl = videoJson.optString("url", "")
        if (downloadUrl.isBlank()) {
            throw YouTubeExtractionException(
                DownloadError.VIDEO_UNAVAILABLE,
                "Could not extract download URL from video info"
            )
        }
        
        Log.d("YouTubeExtractor", "Successfully extracted video info: $title (attempt $attempt)")
        
        return VideoInfoDto(
            id = videoId,
            title = title,
            downloadUrl = downloadUrl,
            thumbnailUrl = thumbnailUrl.takeIf { it.isNotBlank() },
            duration = duration,
            fileSize = fileSize,
            format = format
        )
    }

    private fun isFormatRelatedError(exception: YouTubeExtractionException): Boolean {
        val message = exception.message?.lowercase() ?: ""
        return message.contains("requested format is not available") ||
               message.contains("no video formats found") ||
               message.contains("format not available") ||
               message.contains("no suitable formats") ||
               message.contains("no formats found") ||
               message.contains("format selection failed") ||
               message.contains("unsupported format") ||
               message.contains("format unavailable") ||
               message.contains("no compatible formats") ||
               message.contains("format error")
    }

    private fun configureFormatSelection(request: YoutubeDLRequest, attempt: Int = 1) {
        when (attempt) {
            1 -> {
                // Strategy 1: Automatic format selection
                // No format option added - YouTube-DL will automatically select the best available format
                Log.d("YouTubeExtractor", "Using automatic format selection (attempt $attempt)")
            }
            2 -> {
                // Strategy 2: Quality-based format selection
                request.addOption("--format", "best")
                Log.d("YouTubeExtractor", "Using quality-based format selection: 'best' (attempt $attempt)")
            }
            3 -> {
                // Strategy 3: Compatibility-based format selection
                request.addOption("--format", "worst")
                Log.d("YouTubeExtractor", "Using compatibility-based format selection: 'worst' (attempt $attempt)")
            }
            else -> {
                // Fallback: use automatic selection for any attempt beyond 3
                Log.w("YouTubeExtractor", "Attempt $attempt beyond expected range, using automatic format selection")
            }
        }
    }

    private fun extractVideoIdFromUrl(url: String): String? {
        YOUTUBE_URL_PATTERNS.forEach { pattern ->
            val matchResult = pattern.find(url)
            if (matchResult != null && matchResult.groupValues.size > 2) {
                return matchResult.groupValues[2]
            }
        }
        return null
    }

    private fun handleYoutubeDLError(errorMessage: String, url: String): Nothing {
        val lowerError = errorMessage.lowercase()
        
        val error = when {
            lowerError.contains("python version") && 
            (lowerError.contains("deprecated") || lowerError.contains("not supported")) -> {
                Log.e("YouTubeExtractor", "Python version compatibility issue detected")
                DownloadError.UNKNOWN_ERROR
            }
            
            lowerError.contains("private video") || 
            lowerError.contains("video unavailable") ||
            lowerError.contains("video has been removed") ||
            lowerError.contains("this video is not available") -> DownloadError.VIDEO_UNAVAILABLE
            
            lowerError.contains("sign in to confirm your age") ||
            lowerError.contains("age-restricted") ||
            lowerError.contains("inappropriate") -> DownloadError.AGE_RESTRICTED
            
            lowerError.contains("not available in your country") ||
            lowerError.contains("geo-blocked") ||
            lowerError.contains("region") -> DownloadError.GEO_BLOCKED
            
            lowerError.contains("network") ||
            lowerError.contains("connection") ||
            lowerError.contains("timeout") ||
            lowerError.contains("unreachable") -> DownloadError.NETWORK_ERROR
            
            lowerError.contains("invalid") ||
            lowerError.contains("malformed") ||
            lowerError.contains("unsupported url") -> DownloadError.INVALID_URL
            
            lowerError.contains("requested format is not available") ||
            lowerError.contains("no video formats found") ||
            lowerError.contains("format not available") ||
            lowerError.contains("no suitable formats") ||
            lowerError.contains("no formats found") ||
            lowerError.contains("format selection failed") ||
            lowerError.contains("unsupported format") ||
            lowerError.contains("format unavailable") ||
            lowerError.contains("no compatible formats") ||
            lowerError.contains("format error") -> DownloadError.VIDEO_UNAVAILABLE
            
            else -> DownloadError.UNKNOWN_ERROR
        }
        
        val userFriendlyMessage = when {
            error == DownloadError.UNKNOWN_ERROR && lowerError.contains("python version") -> 
                "Video extraction failed due to compatibility issues. Please try again or contact support."
            error == DownloadError.VIDEO_UNAVAILABLE && (
                lowerError.contains("requested format is not available") ||
                lowerError.contains("no video formats found") ||
                lowerError.contains("format not available") ||
                lowerError.contains("no suitable formats") ||
                lowerError.contains("no formats found") ||
                lowerError.contains("format selection failed") ||
                lowerError.contains("unsupported format") ||
                lowerError.contains("format unavailable") ||
                lowerError.contains("no compatible formats") ||
                lowerError.contains("format error")
            ) -> "The video cannot be processed because no compatible formats are available. This may be due to regional restrictions or video settings."
            else -> "yt-dlp error for URL '$url': $errorMessage"
        }
        
        throw YouTubeExtractionException(error, userFriendlyMessage)
    }
}

class YouTubeExtractionException(
    val error: DownloadError,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)