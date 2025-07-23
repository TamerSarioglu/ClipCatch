package com.tamersarioglu.clipcatch.data.service

import android.content.Context
import android.util.Log
import com.tamersarioglu.clipcatch.data.dto.VideoInfoDto
import com.tamersarioglu.clipcatch.domain.model.DownloadError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton



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
    @ApplicationContext private val context: Context,
    private val simpleExtractor: SimpleYouTubeExtractorService
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
            
            // Use simple HTTP-based extraction (YouTube-DL library has initialization issues)
            Log.d("YouTubeExtractor", "Using simple HTTP-based extraction...")
            return@withContext simpleExtractor.extractVideoInfo(url)
            
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