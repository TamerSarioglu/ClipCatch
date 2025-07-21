package com.tamersarioglu.clipcatch.domain.usecase

import com.tamersarioglu.clipcatch.domain.model.DownloadProgress
import com.tamersarioglu.clipcatch.domain.repository.VideoDownloadRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for downloading YouTube videos.
 * Handles the business logic for initiating and tracking video downloads.
 */
class DownloadVideoUseCase @Inject constructor(
    private val repository: VideoDownloadRepository
) {
    
    /**
     * Downloads a video from the provided YouTube URL.
     * 
     * @param url The YouTube URL of the video to download
     * @return Flow emitting DownloadProgress updates throughout the download process
     * @throws IllegalArgumentException if the URL is invalid or empty
     */
    suspend operator fun invoke(url: String): Flow<DownloadProgress> {
        require(url.isNotBlank()) { "URL cannot be blank" }
        
        // Validate URL format before attempting download
        if (!repository.validateYouTubeUrl(url)) {
            throw IllegalArgumentException("Invalid YouTube URL format")
        }
        
        return repository.downloadVideo(url)
    }
}