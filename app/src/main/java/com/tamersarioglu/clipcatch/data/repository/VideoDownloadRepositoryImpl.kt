package com.tamersarioglu.clipcatch.data.repository

import com.tamersarioglu.clipcatch.data.datasource.DownloadDataSource
import com.tamersarioglu.clipcatch.data.datasource.YouTubeDataSource
import com.tamersarioglu.clipcatch.data.datasource.YouTubeDataException
import com.tamersarioglu.clipcatch.data.mapper.DownloadProgressMapper
import com.tamersarioglu.clipcatch.data.mapper.VideoInfoMapper
import com.tamersarioglu.clipcatch.domain.model.DownloadError
import com.tamersarioglu.clipcatch.domain.model.DownloadProgress
import com.tamersarioglu.clipcatch.domain.model.VideoInfo
import com.tamersarioglu.clipcatch.domain.repository.VideoDownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of VideoDownloadRepository that coordinates between data sources
 * and handles the business logic for video download operations.
 */
@Singleton
class VideoDownloadRepositoryImpl @Inject constructor(
    private val youTubeDataSource: YouTubeDataSource,
    private val downloadDataSource: DownloadDataSource,
    private val videoInfoMapper: VideoInfoMapper,
    private val downloadProgressMapper: DownloadProgressMapper
) : VideoDownloadRepository {

    companion object {
        // Comprehensive YouTube URL patterns
        private val YOUTUBE_URL_PATTERNS = listOf(
            // Standard YouTube URLs
            Pattern.compile("^https?://(www\\.)?youtube\\.com/watch\\?v=([a-zA-Z0-9_-]{11}).*$"),
            // YouTube Shorts URLs
            Pattern.compile("^https?://(www\\.)?youtube\\.com/shorts/([a-zA-Z0-9_-]{11}).*$"),
            // Shortened YouTube URLs
            Pattern.compile("^https?://youtu\\.be/([a-zA-Z0-9_-]{11}).*$"),
            // Mobile YouTube URLs
            Pattern.compile("^https?://m\\.youtube\\.com/watch\\?v=([a-zA-Z0-9_-]{11}).*$"),
            // YouTube embed URLs
            Pattern.compile("^https?://(www\\.)?youtube\\.com/embed/([a-zA-Z0-9_-]{11}).*$"),
            // YouTube live URLs
            Pattern.compile("^https?://(www\\.)?youtube\\.com/live/([a-zA-Z0-9_-]{11}).*$")
        )
    }

    override suspend fun extractVideoInfo(url: String): Result<VideoInfo> {
        return try {
            // Validate URL format first
            if (!validateYouTubeUrl(url)) {
                return Result.failure(
                    IllegalArgumentException("Invalid YouTube URL format: $url")
                )
            }

            // Extract video information using data source
            val videoInfoDto = youTubeDataSource.getVideoInfo(url)
            
            // Map DTO to domain model
            val videoInfo = videoInfoMapper.mapToDomain(videoInfoDto)
            
            // Validate extracted information
            if (videoInfo.id.isBlank() || videoInfo.title.isBlank() || videoInfo.downloadUrl.isBlank()) {
                return Result.failure(
                    IllegalStateException("Incomplete video information extracted from URL: $url")
                )
            }
            
            Result.success(videoInfo)
            
        } catch (e: YouTubeDataException) {
            // Handle specific YouTube data exceptions
            Result.failure(e)
        } catch (e: SecurityException) {
            Result.failure(
                SecurityException("Permission denied while extracting video info: ${e.message}", e)
            )
        } catch (e: Exception) {
            // Handle any other unexpected exceptions
            Result.failure(
                RuntimeException("Failed to extract video information: ${e.message}", e)
            )
        }
    }

    override suspend fun downloadVideo(url: String): Flow<DownloadProgress> {
        return try {
            // First extract video information to get download URL and file name
            val videoInfoResult = extractVideoInfo(url)
            
            if (videoInfoResult.isFailure) {
                // Return error flow if video info extraction failed
                return kotlinx.coroutines.flow.flowOf(
                    DownloadProgress.Error(DownloadError.INVALID_URL)
                )
            }
            
            val videoInfo = videoInfoResult.getOrThrow()
            
            // Generate appropriate file name
            val fileName = generateFileName(videoInfo)
            
            // Start download using the extracted download URL
            downloadDataSource.downloadVideo(videoInfo.downloadUrl, fileName)
                .map { progressDto ->
                    // Map DTO to domain model
                    downloadProgressMapper.mapToDomain(progressDto)
                }
                
        } catch (e: SecurityException) {
            kotlinx.coroutines.flow.flowOf(
                DownloadProgress.Error(DownloadError.PERMISSION_DENIED)
            )
        } catch (e: Exception) {
            kotlinx.coroutines.flow.flowOf(
                DownloadProgress.Error(DownloadError.UNKNOWN_ERROR)
            )
        }
    }

    override fun validateYouTubeUrl(url: String): Boolean {
        // Check for null or empty URL
        if (url.isBlank()) {
            return false
        }
        
        // Trim whitespace and convert to lowercase for consistent checking
        val trimmedUrl = url.trim()
        
        // Check against all YouTube URL patterns
        return YOUTUBE_URL_PATTERNS.any { pattern ->
            pattern.matcher(trimmedUrl).matches()
        } || youTubeDataSource.validateYouTubeUrl(trimmedUrl)
    }

    /**
     * Generates a safe file name from video information
     */
    private fun generateFileName(videoInfo: VideoInfo): String {
        // Sanitize title for file system
        val sanitizedTitle = videoInfo.title
            .replace(Regex("[^a-zA-Z0-9\\s\\-_.]"), "") // Remove special characters
            .replace(Regex("\\s+"), "_") // Replace spaces with underscores
            .take(100) // Limit length to avoid file system issues
        
        // Add video ID to ensure uniqueness
        val videoId = videoInfo.id.take(11) // YouTube video IDs are 11 characters
        
        // Determine file extension based on format
        val extension = when (videoInfo.format.name.lowercase()) {
            "webm" -> "webm"
            "mkv" -> "mkv"
            else -> "mp4" // Default to mp4
        }
        
        return "${sanitizedTitle}_${videoId}.${extension}"
    }

    /**
     * Extracts video ID from YouTube URL for validation purposes
     */
    private fun extractVideoId(url: String): String? {
        YOUTUBE_URL_PATTERNS.forEach { pattern ->
            val matcher = pattern.matcher(url)
            if (matcher.matches() && matcher.groupCount() >= 2) {
                return matcher.group(2) // Video ID is typically in group 2
            }
        }
        return null
    }
}