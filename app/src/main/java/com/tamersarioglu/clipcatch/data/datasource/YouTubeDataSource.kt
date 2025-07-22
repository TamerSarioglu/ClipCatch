package com.tamersarioglu.clipcatch.data.datasource

import com.tamersarioglu.clipcatch.data.dto.VideoInfoDto
import com.tamersarioglu.clipcatch.data.service.YouTubeExtractorService
import com.tamersarioglu.clipcatch.data.service.YouTubeExtractionException
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
 * Implementation of YouTubeDataSource using YouTubeExtractorService
 */
@Singleton
class YouTubeDataSourceImpl @Inject constructor(
    private val youTubeExtractorService: YouTubeExtractorService
) : YouTubeDataSource {
    

    
    override suspend fun getVideoInfo(url: String): VideoInfoDto {
        try {
            return youTubeExtractorService.extractVideoInfo(url)
            
        } catch (e: YouTubeExtractionException) {
            throw YouTubeDataException(e.error, e.message ?: "YouTube extraction failed", e)
        } catch (e: UnknownHostException) {
            throw YouTubeDataException(DownloadError.NETWORK_ERROR, "No internet connection available", e)
        } catch (e: SocketTimeoutException) {
            throw YouTubeDataException(DownloadError.NETWORK_ERROR, "Connection timeout", e)
        } catch (e: IOException) {
            throw YouTubeDataException(DownloadError.NETWORK_ERROR, "Network error: ${e.message}", e)
        } catch (e: SecurityException) {
            throw YouTubeDataException(DownloadError.PERMISSION_DENIED, "Permission denied: ${e.message}", e)
        } catch (e: Exception) {
            throw YouTubeDataException(DownloadError.UNKNOWN_ERROR, "Failed to extract video information: ${e.message}", e)
        }
    }
    
    override fun validateYouTubeUrl(url: String): Boolean {
        return youTubeExtractorService.isValidYouTubeUrl(url)
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