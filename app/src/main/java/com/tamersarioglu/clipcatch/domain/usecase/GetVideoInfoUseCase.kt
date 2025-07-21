package com.tamersarioglu.clipcatch.domain.usecase

import com.tamersarioglu.clipcatch.domain.model.VideoInfo
import com.tamersarioglu.clipcatch.domain.repository.VideoDownloadRepository
import javax.inject.Inject

/**
 * Use case for extracting video information from YouTube URLs.
 * Handles the business logic for retrieving video metadata before download.
 */
class GetVideoInfoUseCase @Inject constructor(
    private val repository: VideoDownloadRepository
) {
    
    /**
     * Extracts video information from a YouTube URL.
     * 
     * @param url The YouTube URL to extract information from
     * @return Result containing VideoInfo on success, or an exception on failure
     * @throws IllegalArgumentException if the URL is invalid or empty
     */
    suspend operator fun invoke(url: String): Result<VideoInfo> {
        // Validate input
        if (url.isBlank()) {
            return Result.failure(IllegalArgumentException("URL cannot be blank"))
        }
        
        val trimmedUrl = url.trim()
        
        // Validate URL format before attempting extraction
        if (!repository.validateYouTubeUrl(trimmedUrl)) {
            return Result.failure(IllegalArgumentException("Invalid YouTube URL format"))
        }
        
        return try {
            repository.extractVideoInfo(trimmedUrl)
        } catch (e: Exception) {
            // Wrap any repository exceptions with more context
            Result.failure(
                VideoInfoExtractionException(
                    "Failed to extract video information from URL: $trimmedUrl",
                    e
                )
            )
        }
    }
    
    /**
     * Custom exception for video information extraction failures
     */
    class VideoInfoExtractionException(
        message: String,
        cause: Throwable? = null
    ) : Exception(message, cause)
}