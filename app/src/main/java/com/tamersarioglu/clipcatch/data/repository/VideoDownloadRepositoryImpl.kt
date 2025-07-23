package com.tamersarioglu.clipcatch.data.repository

import com.tamersarioglu.clipcatch.data.datasource.DownloadDataSource
import com.tamersarioglu.clipcatch.data.datasource.YouTubeDataSource
import com.tamersarioglu.clipcatch.data.datasource.YouTubeDataException
import com.tamersarioglu.clipcatch.data.mapper.DownloadProgressMapper
import com.tamersarioglu.clipcatch.data.mapper.VideoInfoMapper
import com.tamersarioglu.clipcatch.data.util.ErrorHandler
import com.tamersarioglu.clipcatch.data.util.Logger
import com.tamersarioglu.clipcatch.data.util.NetworkException
import com.tamersarioglu.clipcatch.data.util.NetworkSuitability
import com.tamersarioglu.clipcatch.data.util.NetworkUtils
import com.tamersarioglu.clipcatch.data.util.RetryUtils
import com.tamersarioglu.clipcatch.domain.model.DownloadError
import com.tamersarioglu.clipcatch.domain.model.DownloadProgress
import com.tamersarioglu.clipcatch.domain.model.VideoInfo
import com.tamersarioglu.clipcatch.domain.repository.VideoDownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoDownloadRepositoryImpl @Inject constructor(
    private val youTubeDataSource: YouTubeDataSource,
    private val downloadDataSource: DownloadDataSource,
    private val videoInfoMapper: VideoInfoMapper,
    private val downloadProgressMapper: DownloadProgressMapper,
    private val errorHandler: ErrorHandler,
    private val logger: Logger,
    private val networkUtils: NetworkUtils,
    private val retryUtils: RetryUtils
) : VideoDownloadRepository {
    
    companion object {
        private const val TAG = "VideoDownloadRepository"
        
        private val YOUTUBE_URL_PATTERNS = listOf(
            Pattern.compile("^https?://(www\\.)?youtube\\.com/watch\\?v=([a-zA-Z0-9_-]{11}).*$"),
            Pattern.compile("^https?://(www\\.)?youtube\\.com/shorts/([a-zA-Z0-9_-]{11}).*$"),
            Pattern.compile("^https?://youtu\\.be/([a-zA-Z0-9_-]{11}).*$"),
            Pattern.compile("^https?://m\\.youtube\\.com/watch\\?v=([a-zA-Z0-9_-]{11}).*$"),
            Pattern.compile("^https?://(www\\.)?youtube\\.com/embed/([a-zA-Z0-9_-]{11}).*$"),
            Pattern.compile("^https?://(www\\.)?youtube\\.com/live/([a-zA-Z0-9_-]{11}).*$")
        )
    }

    override suspend fun extractVideoInfo(url: String): Result<VideoInfo> {
        logger.enter(TAG, "extractVideoInfo", url)
        
        return errorHandler.handleSuspendOperation(TAG) {
            val networkInfo = networkUtils.getNetworkInfo()
            if (!networkInfo.isAvailable) {
                logger.w(TAG, "No network connection available for video info extraction")
                throw NetworkException.NoInternetException("No internet connection available")
            }
            
            if (!validateYouTubeUrl(url)) {
                logger.w(TAG, "Invalid YouTube URL format: $url")
                throw IllegalArgumentException("Invalid YouTube URL format: $url")
            }
            
            logger.i(TAG, "Extracting video info for URL: $url")
            
            val videoInfoDto = retryUtils.executeWithRetry(
                maxAttempts = 3,
                baseDelayMs = 1000L,
                retryCondition = { exception ->
                    val isRetryable = when (exception) {
                        is YouTubeDataException -> exception.error == DownloadError.NETWORK_ERROR
                        else -> errorHandler.isRecoverableError(errorHandler.mapExceptionToDownloadError(exception))
                    }
                    logger.d(TAG, "Exception is retryable: $isRetryable for ${exception.javaClass.simpleName}")
                    isRetryable
                }
            ) { attempt ->
                logger.d(TAG, "Attempting to extract video info, attempt $attempt")
                youTubeDataSource.getVideoInfo(url)
            }
            
            val videoInfo = videoInfoMapper.mapToDomain(videoInfoDto)
            
            if (videoInfo.id.isBlank() || videoInfo.title.isBlank() || videoInfo.downloadUrl.isBlank()) {
                logger.e(TAG, "Incomplete video information extracted from URL: $url")
                throw IllegalStateException("Incomplete video information extracted from URL: $url")
            }
            
            logger.i(TAG, "Successfully extracted video info: ${videoInfo.title}")
            videoInfo
        }.also { result ->
            logger.exit(TAG, "extractVideoInfo", result.isSuccess)
        }
    }

    override suspend fun downloadVideo(url: String): Flow<DownloadProgress> {
        logger.enter(TAG, "downloadVideo", url)
        
        return kotlinx.coroutines.flow.flow {
            try {
                val networkSuitability = networkUtils.isNetworkSuitableForDownload()
                when (networkSuitability) {
                    NetworkSuitability.NotAvailable -> {
                        logger.w(TAG, "No network connection available for download")
                        emit(DownloadProgress.Error(DownloadError.NETWORK_ERROR))
                        return@flow
                    }
                    NetworkSuitability.Limited -> {
                        logger.w(TAG, "Network connection is metered, download may be slow or expensive")
                    }
                    else -> {
                        logger.d(TAG, "Network is suitable for download: $networkSuitability")
                    }
                }
                
                logger.i(TAG, "Extracting video information before download")
                val videoInfoResult = extractVideoInfo(url)
                
                if (videoInfoResult.isFailure) {
                    val exception = videoInfoResult.exceptionOrNull()
                    val error = if (exception != null) {
                        errorHandler.mapExceptionToDownloadError(exception)
                    } else {
                        DownloadError.INVALID_URL
                    }
                    logger.e(TAG, "Failed to extract video info for download", exception)
                    emit(DownloadProgress.Error(error))
                    return@flow
                }
                
                val videoInfo = videoInfoResult.getOrThrow()
                logger.i(TAG, "Starting download for video: ${videoInfo.title}")
                
                // Check if this is from the simple extractor (which can't provide real download URLs)
                if (videoInfo.downloadUrl.startsWith("SIMPLE_EXTRACTOR_PLACEHOLDER:")) {
                    logger.w(TAG, "Cannot download video - simple extractor was used (YouTube-DL library failed)")
                    emit(DownloadProgress.Error(DownloadError.VIDEO_UNAVAILABLE))
                    return@flow
                }
                
                val fileName = generateFileName(videoInfo)
                logger.d(TAG, "Generated file name: $fileName")
                
                downloadDataSource.downloadVideo(videoInfo.downloadUrl, fileName)
                    .map { progressDto ->
                        downloadProgressMapper.mapToDomain(progressDto)
                    }
                    .collect { progress ->
                        when (progress) {
                            is DownloadProgress.Progress -> {
                                logger.logDownloadProgress(TAG, url, progress.percentage)
                            }
                            is DownloadProgress.Success -> {
                                logger.i(TAG, "Download completed successfully: ${progress.filePath}")
                            }
                            is DownloadProgress.Error -> {
                                logger.e(TAG, "Download failed with error: ${progress.error}")
                            }
                        }
                        emit(progress)
                    }
                    
            } catch (e: Exception) {
                val error = errorHandler.mapExceptionToDownloadError(e)
                logger.e(TAG, "Download operation failed", e)
                emit(DownloadProgress.Error(error))
            }
        }
        .onStart {
            logger.d(TAG, "Starting download flow for URL: $url")
        }
        .catch { exception ->
            val error = errorHandler.mapExceptionToDownloadError(exception)
            logger.e(TAG, "Download flow error", exception)
            emit(DownloadProgress.Error(error))
        }
    }

    override fun validateYouTubeUrl(url: String): Boolean {
        logger.enter(TAG, "validateYouTubeUrl", url)
        
        return try {
            if (url.isBlank()) {
                logger.d(TAG, "URL is blank")
                return false
            }
            
            val trimmedUrl = url.trim()
            logger.d(TAG, "Validating trimmed URL: $trimmedUrl")
            
            val isValidPattern = YOUTUBE_URL_PATTERNS.any { pattern ->
                val matches = pattern.matcher(trimmedUrl).matches()
                if (matches) {
                    logger.d(TAG, "URL matches pattern: ${pattern.pattern()}")
                }
                matches
            }
            
            val isValid = isValidPattern || youTubeDataSource.validateYouTubeUrl(trimmedUrl)
            logger.d(TAG, "URL validation result: $isValid")
            
            isValid
            
        } catch (e: Exception) {
            logger.w(TAG, "Error during URL validation", e)
            false
        }.also { result ->
            logger.exit(TAG, "validateYouTubeUrl", result)
        }
    }

    private fun generateFileName(videoInfo: VideoInfo): String {
        val sanitizedTitle = videoInfo.title
            .replace(Regex("[^a-zA-Z0-9\\s\\-_.]"), "")
            .replace(Regex("\\s+"), "_")
            .take(100)
        
        val videoId = videoInfo.id.take(11)
        
        val extension = when (videoInfo.format.name.lowercase()) {
            "webm" -> "webm"
            "mkv" -> "mkv"
            else -> "mp4"
        }
        
        return "${sanitizedTitle}_${videoId}.${extension}"
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
}