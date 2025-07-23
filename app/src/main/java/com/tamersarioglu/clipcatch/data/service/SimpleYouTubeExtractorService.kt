package com.tamersarioglu.clipcatch.data.service

import android.content.Context
import android.util.Log
import com.tamersarioglu.clipcatch.data.dto.VideoInfoDto
import com.tamersarioglu.clipcatch.domain.model.DownloadError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple YouTube extractor that uses HTTP requests to get basic video information
 * This is a fallback when the full YouTube-DL library fails to initialize
 */
@Singleton
class SimpleYouTubeExtractorService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) {
    
    companion object {
        private const val TAG = "SimpleYouTubeExtractor"
        
        // YouTube URL patterns
        private val YOUTUBE_URL_PATTERNS = listOf(
            Pattern.compile("^https?://(www\\.)?youtube\\.com/watch\\?v=([a-zA-Z0-9_-]+).*$"),
            Pattern.compile("^https?://(www\\.)?youtu\\.be/([a-zA-Z0-9_-]+).*$"),
            Pattern.compile("^https?://(www\\.)?youtube\\.com/shorts/([a-zA-Z0-9_-]+).*$"),
            Pattern.compile("^https?://(m\\.)?youtube\\.com/watch\\?v=([a-zA-Z0-9_-]+).*$")
        )
        
        // Regex patterns for extracting video information from HTML
        private val TITLE_PATTERN = Pattern.compile("<title>([^<]+)</title>", Pattern.CASE_INSENSITIVE)
        private val VIDEO_ID_PATTERN = Pattern.compile("\"videoId\":\"([^\"]+)\"")
        private val DURATION_PATTERN = Pattern.compile("\"lengthSeconds\":\"([^\"]+)\"")
        private val THUMBNAIL_PATTERN = Pattern.compile("\"thumbnail\":\\{\"thumbnails\":\\[\\{\"url\":\"([^\"]+)\"")
    }
    
    /**
     * Extracts basic video information using HTTP requests
     * This is a simpler approach that doesn't require the full YouTube-DL library
     */
    suspend fun extractVideoInfo(url: String): VideoInfoDto = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Extracting video info using simple HTTP approach for: $url")
            
            if (!isValidYouTubeUrl(url)) {
                throw SimpleYouTubeExtractionException(
                    DownloadError.INVALID_URL,
                    "Invalid YouTube URL format: $url"
                )
            }
            
            val videoId = extractVideoId(url)
                ?: throw SimpleYouTubeExtractionException(
                    DownloadError.INVALID_URL,
                    "Could not extract video ID from URL: $url"
                )
            
            Log.d(TAG, "Extracted video ID: $videoId")
            
            // Fetch the YouTube page HTML
            val htmlContent = fetchYouTubePageContent(url)
            
            // Extract video information from HTML
            val title = extractTitle(htmlContent) ?: "YouTube Video $videoId"
            val duration = extractDuration(htmlContent) ?: 0L
            val thumbnailUrl = extractThumbnail(htmlContent, videoId)
            
            Log.d(TAG, "Extracted video info - Title: $title, Duration: $duration")
            
            // Mark this as a simple extraction that can't provide actual download URLs
            // The download URL will be a placeholder to indicate this limitation
            val downloadUrl = "SIMPLE_EXTRACTOR_PLACEHOLDER:$videoId"
            
            return@withContext VideoInfoDto(
                id = videoId,
                title = title,
                downloadUrl = downloadUrl,
                thumbnailUrl = thumbnailUrl,
                duration = duration,
                fileSize = null, // We can't determine file size without stream info
                format = "mp4"
            )
            
        } catch (e: SimpleYouTubeExtractionException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract video info using simple approach", e)
            throw SimpleYouTubeExtractionException(
                DownloadError.NETWORK_ERROR,
                "Failed to extract video information: ${e.message}",
                e
            )
        }
    }
    
    private fun isValidYouTubeUrl(url: String): Boolean {
        if (url.isBlank() || url.length > 2048) return false
        val normalizedUrl = url.trim()
        return YOUTUBE_URL_PATTERNS.any { pattern ->
            pattern.matcher(normalizedUrl).matches()
        }
    }
    
    private fun extractVideoId(url: String): String? {
        YOUTUBE_URL_PATTERNS.forEach { pattern ->
            val matcher = pattern.matcher(url)
            if (matcher.matches() && matcher.groupCount() >= 2) {
                return matcher.group(2)
            }
        }
        return null
    }
    
    private suspend fun fetchYouTubePageContent(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .build()
        
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw SimpleYouTubeExtractionException(
                DownloadError.NETWORK_ERROR,
                "HTTP request failed with code: ${response.code}"
            )
        }
        
        return response.body?.string() ?: throw SimpleYouTubeExtractionException(
            DownloadError.NETWORK_ERROR,
            "Empty response body"
        )
    }
    
    private fun extractTitle(htmlContent: String): String? {
        val matcher = TITLE_PATTERN.matcher(htmlContent)
        if (matcher.find()) {
            val title = matcher.group(1)?.trim()
            // Clean up the title by removing " - YouTube" suffix
            return title?.replace(" - YouTube", "")?.trim()
        }
        return null
    }
    
    private fun extractDuration(htmlContent: String): Long? {
        val matcher = DURATION_PATTERN.matcher(htmlContent)
        if (matcher.find()) {
            val durationStr = matcher.group(1)
            return try {
                durationStr?.toLongOrNull()
            } catch (e: NumberFormatException) {
                null
            }
        }
        return null
    }
    
    private fun extractThumbnail(htmlContent: String, videoId: String): String? {
        val matcher = THUMBNAIL_PATTERN.matcher(htmlContent)
        if (matcher.find()) {
            return matcher.group(1)?.replace("\\u0026", "&")
        }
        // Fallback to standard YouTube thumbnail URL
        return "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
    }
}

/**
 * Exception for simple YouTube extraction operations
 */
class SimpleYouTubeExtractionException(
    val error: DownloadError,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)