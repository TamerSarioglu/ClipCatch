package com.tamersarioglu.clipcatch.data.service

import android.content.Context
import android.util.Log
import com.tamersarioglu.clipcatch.data.dto.VideoInfoDto
import com.tamersarioglu.clipcatch.domain.model.DownloadError
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
    @ApplicationContext private val context: Context
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
            val app = context.applicationContext as com.tamersarioglu.clipcatch.ClipCatchApplication
            if (!app.ensureYoutubeDLInitialized()) {
                Log.e("YouTubeExtractor", "YouTube-DL not available - native libraries missing")
                throw YouTubeExtractionException(
                    DownloadError.UNKNOWN_ERROR,
                    "YouTube-DL initialization failed. Please reinstall the app or contact support."
                )
            }
            
            Log.d("YouTubeExtractor", "Using YouTube-DL for video info extraction...")
            
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-json")
                addOption("--no-playlist")
                addOption("--format", "best[ext=mp4]/best")
                addOption("--no-check-certificate")
                addOption("--prefer-free-formats")
                addOption("--add-header", "referer:youtube.com")
                addOption("--add-header", "user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }
            
            val response = YoutubeDL.getInstance().execute(request)
            
            if (response.exitCode != 0) {
                val errorMessage = response.err ?: "Unknown YouTube-DL error"
                Log.e("YouTubeExtractor", "YouTube-DL failed with exit code ${response.exitCode}: $errorMessage")
                handleYoutubeDLError(errorMessage, url)
            }
            
            val jsonOutput = response.out
            if (jsonOutput.isNullOrBlank()) {
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
            
            Log.d("YouTubeExtractor", "Successfully extracted video info: $title")
            
            return@withContext VideoInfoDto(
                id = videoId,
                title = title,
                downloadUrl = downloadUrl,
                thumbnailUrl = thumbnailUrl.takeIf { it.isNotBlank() },
                duration = duration,
                fileSize = fileSize,
                format = format
            )
            
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
            // Ensure initialization is attempted
            val app = context.applicationContext as com.tamersarioglu.clipcatch.ClipCatchApplication
            if (!app.ensureYoutubeDLInitialized()) {
                Log.w("YouTubeExtractor", "YouTube-DL initialization failed")
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
            
            else -> DownloadError.UNKNOWN_ERROR
        }
        
        throw YouTubeExtractionException(error, "yt-dlp error for URL '$url': $errorMessage")
    }
}

class YouTubeExtractionException(
    val error: DownloadError,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)