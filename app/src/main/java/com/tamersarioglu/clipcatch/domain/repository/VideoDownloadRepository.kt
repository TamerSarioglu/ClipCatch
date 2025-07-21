package com.tamersarioglu.clipcatch.domain.repository

import com.tamersarioglu.clipcatch.domain.model.DownloadProgress
import com.tamersarioglu.clipcatch.domain.model.VideoInfo
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for YouTube video download operations.
 * Defines the contract for extracting video information, downloading videos,
 * and validating YouTube URLs.
 */
interface VideoDownloadRepository {
    
    /**
     * Extracts video information from a YouTube URL.
     * 
     * @param url The YouTube URL to extract information from
     * @return Result containing VideoInfo on success, or an exception on failure
     */
    suspend fun extractVideoInfo(url: String): Result<VideoInfo>
    
    /**
     * Downloads a video from the provided URL and tracks progress.
     * 
     * @param url The YouTube URL of the video to download
     * @return Flow emitting DownloadProgress updates throughout the download process
     */
    suspend fun downloadVideo(url: String): Flow<DownloadProgress>
    
    /**
     * Validates if the provided URL is a valid YouTube URL.
     * Supports various YouTube URL formats including:
     * - youtube.com/watch?v=
     * - youtube.com/shorts/
     * - youtu.be/
     * - m.youtube.com
     * 
     * @param url The URL to validate
     * @return true if the URL is a valid YouTube URL, false otherwise
     */
    fun validateYouTubeUrl(url: String): Boolean
}