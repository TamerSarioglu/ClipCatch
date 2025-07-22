package com.tamersarioglu.clipcatch.data.service

import android.content.Context
import com.tamersarioglu.clipcatch.data.dto.VideoInfoDto
import com.tamersarioglu.clipcatch.domain.model.DownloadError
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Internal data class for parsing yt-dlp JSON response
 */
@Serializable
private data class YoutubeDLVideoInfo(
    @SerialName("id")
    val id: String? = null,
    
    @SerialName("title")
    val title: String? = null,
    
    @SerialName("url")
    val url: String? = null,
    
    @SerialName("thumbnail")
    val thumbnail: String? = null,
    
    @SerialName("duration")
    val duration: Long? = null,
    
    @SerialName("filesize")
    val filesize: Long? = null,
    
    @SerialName("filesize_approx")
    val filesizeApprox: Long? = null,
    
    @SerialName("ext")
    val ext: String? = null,
    
    @SerialName("format_id")
    val formatId: String? = null,
    
    @SerialName("webpage_url")
    val webpageUrl: String? = null,
    
    @SerialName("uploader")
    val uploader: String? = null
)

/**
 * Service interface for YouTube video information extraction
 */
interface YouTubeExtractorService {
    
    /**
     * Extracts video information from a YouTube URL using yt-dlp
     * @param url The YouTube URL to extract information from
     * @return VideoInfoDto containing the video information
     * @throws YouTubeExtractionException if extraction fails
     */
    suspend fun extractVideoInfo(url: String): VideoInfoDto
    
    /**
     * Validates if the provided URL is a supported YouTube URL format
     * @param url The URL to validate
     * @return true if the URL is supported, false otherwise
     */
    fun isValidYouTubeUrl(url: String): Boolean
}

/**
 * Implementation of YouTubeExtractorService using yt-dlp library
 */
@Singleton
class YouTubeExtractorServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : YouTubeExtractorService {
    
    companion object {
        // Comprehensive YouTube URL patterns
        private val YOUTUBE_URL_PATTERNS = listOf(
            // Standard YouTube URLs
            Regex("^https?://(www\\.)?youtube\\.com/watch\\?v=([a-zA-Z0-9_-]+).*$"),
            // Shortened YouTube URLs
            Regex("^https?://(www\\.)?youtu\\.be/([a-zA-Z0-9_-]+).*$"),
            // YouTube Shorts URLs
            Regex("^https?://(www\\.)?youtube\\.com/shorts/([a-zA-Z0-9_-]+).*$"),
            // Mobile YouTube URLs
            Regex("^https?://(m\\.)?youtube\\.com/watch\\?v=([a-zA-Z0-9_-]+).*$"),
            // YouTube embed URLs
            Regex("^https?://(www\\.)?youtube\\.com/embed/([a-zA-Z0-9_-]+).*$"),
            // YouTube live URLs
            Regex("^https?://(www\\.)?youtube\\.com/live/([a-zA-Z0-9_-]+).*$")
        )
        
        // yt-dlp options for video information extraction
        private const val FORMAT_SELECTOR = "best[ext=mp4]/best"
        private const val MAX_DURATION = 3600 // 1 hour limit
        
        // Flag to track initialization status
        private var isInitialized = false
    }
    
    /**
     * Initialize the YouTube-DL library if not already initialized
     * This method is called automatically before any extraction operation
     */
    private suspend fun initializeYoutubeDL() = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            try {
                YoutubeDL.getInstance().init(context)
                // Update the library to get the latest version
                YoutubeDL.getInstance().updateYoutubeDL(context)
                isInitialized = true
            } catch (e: Exception) {
                throw YouTubeExtractionException(
                    DownloadError.UNKNOWN_ERROR,
                    "Failed to initialize YouTube-DL library: ${e.message}",
                    e
                )
            }
        }
    }
    
    override suspend fun extractVideoInfo(url: String): VideoInfoDto = withContext(Dispatchers.IO) {
        try {
            if (!isValidYouTubeUrl(url)) {
                throw YouTubeExtractionException(
                    DownloadError.INVALID_URL,
                    "Invalid or unsupported YouTube URL format: $url"
                )
            }
            
            // Initialize YouTube-DL library if needed
            initializeYoutubeDL()
            
            // Create yt-dlp request for video information extraction
            val request = YoutubeDLRequest(url).apply {
                // Extract video information without downloading
                addOption("--dump-json")
                addOption("--no-download")
                addOption("--format", FORMAT_SELECTOR)
                addOption("--no-playlist")
                addOption("--extract-flat", "false")
                // Add user agent to avoid blocking
                addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                // Set timeout
                addOption("--socket-timeout", "30")
                // Retry mechanism for transient errors
                addOption("--retries", "3")
                // Skip unavailable fragments
                addOption("--skip-unavailable-fragments")
                // Abort on download errors
                addOption("--abort-on-error")
            }
            
            // Execute yt-dlp request
            val response: YoutubeDLResponse = YoutubeDL.getInstance().execute(request)
            
            if (response.exitCode != 0) {
                handleYoutubeDLError(response.err, url)
            }
            
            // Parse the JSON response
            val videoInfo = parseVideoInfoFromResponse(response.out, url)
            
            // Validate video duration (optional limit)
            if (videoInfo.duration > MAX_DURATION) {
                throw YouTubeExtractionException(
                    DownloadError.VIDEO_UNAVAILABLE,
                    "Video duration exceeds maximum allowed length (${MAX_DURATION}s)"
                )
            }
            
            return@withContext videoInfo
            
        } catch (e: YouTubeExtractionException) {
            throw e
        } catch (e: SecurityException) {
            throw YouTubeExtractionException(
                DownloadError.PERMISSION_DENIED,
                "Permission denied while extracting video information: ${e.message}"
            )
        } catch (e: java.net.SocketTimeoutException) {
            throw YouTubeExtractionException(
                DownloadError.NETWORK_ERROR,
                "Network timeout while extracting video information for URL: $url",
                e
            )
        } catch (e: java.io.IOException) {
            throw YouTubeExtractionException(
                DownloadError.NETWORK_ERROR,
                "Network error while extracting video information: ${e.message}",
                e
            )
        } catch (e: Exception) {
            throw YouTubeExtractionException(
                DownloadError.UNKNOWN_ERROR,
                "Failed to extract video information: ${e.message}",
                e
            )
        }
    }
    
    override fun isValidYouTubeUrl(url: String): Boolean {
        // Check for blank or excessively long URLs
        if (url.isBlank() || url.length > 2048) return false
        
        // Normalize the URL by trimming whitespace
        val normalizedUrl = url.trim()
        
        // Check if the URL matches any of the supported YouTube URL patterns
        return YOUTUBE_URL_PATTERNS.any { pattern ->
            pattern.matches(normalizedUrl)
        }
    }
    
    /**
     * Parses video information from yt-dlp JSON response
     */
    private fun parseVideoInfoFromResponse(jsonOutput: String, originalUrl: String): VideoInfoDto {
        try {
            // Create JSON parser with lenient settings
            val json = Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }
            
            // Find the JSON object in the output (yt-dlp may output multiple lines)
            val jsonLines = jsonOutput.split("\n").filter { it.trim().startsWith("{") && it.trim().endsWith("}") }
            
            if (jsonLines.isEmpty()) {
                throw YouTubeExtractionException(
                    DownloadError.VIDEO_UNAVAILABLE,
                    "No valid JSON found in yt-dlp output"
                )
            }
            
            // Parse the first valid JSON line (should contain video info)
            val videoInfo = json.decodeFromString<YoutubeDLVideoInfo>(jsonLines.first())
            
            // Extract and validate required fields
            val videoId = videoInfo.id ?: extractVideoIdFromUrl(originalUrl) ?: "unknown"
            val title = videoInfo.title?.takeIf { it.isNotBlank() } ?: "YouTube Video $videoId"
            val downloadUrl = videoInfo.url?.takeIf { it.isNotBlank() }
                ?: throw YouTubeExtractionException(
                    DownloadError.VIDEO_UNAVAILABLE,
                    "Could not extract download URL for video"
                )
            
            // Use filesize or filesize_approx, whichever is available
            val fileSize = videoInfo.filesize ?: videoInfo.filesizeApprox
            
            return VideoInfoDto(
                id = videoId,
                title = title,
                downloadUrl = downloadUrl,
                thumbnailUrl = videoInfo.thumbnail,
                duration = videoInfo.duration ?: 0L,
                fileSize = fileSize,
                format = videoInfo.ext ?: "mp4"
            )
            
        } catch (e: YouTubeExtractionException) {
            throw e
        } catch (e: Exception) {
            throw YouTubeExtractionException(
                DownloadError.UNKNOWN_ERROR,
                "Failed to parse video information: ${e.message}",
                e
            )
        }
    }
    
    /**
     * Extracts video ID from YouTube URL
     */
    private fun extractVideoIdFromUrl(url: String): String? {
        YOUTUBE_URL_PATTERNS.forEach { pattern ->
            val matchResult = pattern.find(url)
            if (matchResult != null && matchResult.groupValues.size > 2) {
                return matchResult.groupValues[2]
            }
        }
        return null
    }
    

    
    /**
     * Handles yt-dlp error messages and maps them to appropriate DownloadError types
     */
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

/**
 * Custom exception for YouTube extraction operations
 */
class YouTubeExtractionException(
    val error: DownloadError,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)