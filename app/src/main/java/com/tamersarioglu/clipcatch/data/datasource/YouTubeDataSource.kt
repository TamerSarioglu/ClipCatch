package com.tamersarioglu.clipcatch.data.datasource

import com.tamersarioglu.clipcatch.data.dto.VideoInfoDto
import com.tamersarioglu.clipcatch.domain.model.DownloadError
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface for YouTube data operations
 */
interface YouTubeDataSource {
    
    /**
     * Extracts video information from a YouTube URL
     * @param url The YouTube URL to extract information from
     * @return VideoInfoDto containing the video information
     * @throws Exception if the URL is invalid or video information cannot be extracted
     */
    suspend fun getVideoInfo(url: String): VideoInfoDto
    
    /**
     * Validates if the provided URL is a valid YouTube URL
     * @param url The URL to validate
     * @return true if the URL is a valid YouTube URL, false otherwise
     */
    fun validateYouTubeUrl(url: String): Boolean
}

/**
 * Implementation of YouTubeDataSource
 * This is a placeholder implementation that will be replaced with actual YouTube extraction logic
 */
@Singleton
class YouTubeDataSourceImpl @Inject constructor() : YouTubeDataSource {
    
    companion object {
        // YouTube URL patterns for validation
        private val YOUTUBE_URL_PATTERNS = listOf(
            Regex("^https?://(www\\.)?youtube\\.com/watch\\?v=([a-zA-Z0-9_-]+).*$"),
            Regex("^https?://(www\\.)?youtu\\.be/([a-zA-Z0-9_-]+).*$"),
            Regex("^https?://(www\\.)?youtube\\.com/shorts/([a-zA-Z0-9_-]+).*$"),
            Regex("^https?://(m\\.)?youtube\\.com/watch\\?v=([a-zA-Z0-9_-]+).*$")
        )
    }
    
    override suspend fun getVideoInfo(url: String): VideoInfoDto {
        try {
            if (!validateYouTubeUrl(url)) {
                throw YouTubeDataException(DownloadError.INVALID_URL, "Invalid YouTube URL: $url")
            }
            
            // Extract video ID from URL
            val videoId = extractVideoId(url)
                ?: throw YouTubeDataException(DownloadError.INVALID_URL, "Could not extract video ID from URL: $url")
            
            // TODO: Replace with actual YouTube extraction service
            // For now, return mock data for testing purposes
            return VideoInfoDto(
                id = videoId,
                title = "Sample Video Title",
                downloadUrl = "https://example.com/video.mp4",
                thumbnailUrl = "https://example.com/thumbnail.jpg",
                duration = 300L,
                fileSize = 1024L * 1024L, // 1MB
                format = "mp4"
            )
            
        } catch (e: YouTubeDataException) {
            throw e
        } catch (e: UnknownHostException) {
            throw YouTubeDataException(DownloadError.NETWORK_ERROR, "No internet connection available")
        } catch (e: SocketTimeoutException) {
            throw YouTubeDataException(DownloadError.NETWORK_ERROR, "Connection timeout")
        } catch (e: IOException) {
            throw YouTubeDataException(DownloadError.NETWORK_ERROR, "Network error: ${e.message}")
        } catch (e: SecurityException) {
            throw YouTubeDataException(DownloadError.PERMISSION_DENIED, "Permission denied: ${e.message}")
        } catch (e: Exception) {
            throw YouTubeDataException(DownloadError.UNKNOWN_ERROR, "Failed to extract video information: ${e.message}")
        }
    }
    
    override fun validateYouTubeUrl(url: String): Boolean {
        if (url.isBlank()) return false
        
        return YOUTUBE_URL_PATTERNS.any { pattern ->
            pattern.matches(url)
        }
    }
    
    /**
     * Extracts video ID from YouTube URL
     */
    private fun extractVideoId(url: String): String? {
        YOUTUBE_URL_PATTERNS.forEach { pattern ->
            val matchResult = pattern.find(url)
            if (matchResult != null && matchResult.groupValues.size > 2) {
                return matchResult.groupValues[2]
            }
        }
        return null
    }
}

/**
 * Custom exception for YouTube data source operations
 */
class YouTubeDataException(
    val error: DownloadError,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)