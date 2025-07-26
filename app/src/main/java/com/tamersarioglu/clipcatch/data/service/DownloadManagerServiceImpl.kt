package com.tamersarioglu.clipcatch.data.service

import com.tamersarioglu.clipcatch.data.dto.DownloadProgressDto
import com.tamersarioglu.clipcatch.util.ErrorHandler
import com.tamersarioglu.clipcatch.util.Logger
import com.tamersarioglu.clipcatch.util.NetworkUtils
import com.tamersarioglu.clipcatch.util.RetryUtils
import com.tamersarioglu.clipcatch.domain.model.DownloadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

@Singleton
class DownloadManagerServiceImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val fileManagerService: FileManagerService,
    private val errorHandler: ErrorHandler,
    private val logger: Logger,
    private val networkUtils: NetworkUtils,
    private val retryUtils: RetryUtils
) : DownloadManagerService {

    companion object {
        private const val TAG = "DownloadManagerService"
        private const val DEFAULT_BUFFER_SIZE = 8 * 1024
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val BASE_RETRY_DELAY = 1000L
    }

    private val activeDownloads = ConcurrentHashMap<String, Boolean>()
    private val cancellationRequests = ConcurrentHashMap<String, Boolean>()
    private val cleanupTasks = ConcurrentHashMap<String, () -> Unit>()

    override suspend fun downloadVideo(
        url: String, 
        fileName: String,
        destinationPath: String?
    ): Flow<DownloadProgressDto> = flow {
        logger.enter(TAG, "downloadVideo", url, fileName, destinationPath)
        
        try {
            if (url.isBlank()) {
                logger.w(TAG, "Download URL is empty")
                emit(DownloadProgressDto.Error(
                    errorType = DownloadError.INVALID_URL.name,
                    message = "Download URL cannot be empty"
                ))
                return@flow
            }
            
            if (fileName.isBlank()) {
                logger.w(TAG, "File name is empty")
                emit(DownloadProgressDto.Error(
                    errorType = DownloadError.STORAGE_ERROR.name,
                    message = "File name cannot be empty"
                ))
                return@flow
            }
            
            val networkInfo = networkUtils.getNetworkInfo()
            if (!networkInfo.isAvailable) {
                logger.w(TAG, "No network connection available")
                emit(DownloadProgressDto.Error(
                    errorType = DownloadError.NETWORK_ERROR.name,
                    message = "No internet connection available"
                ))
                return@flow
            }
            
            if (isDownloadInProgress(url)) {
                logger.w(TAG, "Download already in progress for URL: $url")
                emit(DownloadProgressDto.Error(
                    errorType = DownloadError.UNKNOWN_ERROR.name,
                    message = "Download already in progress for this URL"
                ))
                return@flow
            }
            
            activeDownloads[url] = true
            logger.i(TAG, "Starting download for: $fileName")
            
            cancellationRequests.remove(url)
            
            val result = retryUtils.executeWithRetry(
                maxAttempts = MAX_RETRY_ATTEMPTS,
                baseDelayMs = BASE_RETRY_DELAY,
                retryCondition = { exception ->
                    val isRetryable = when (exception) {
                        is CancellationException -> false
                        is SecurityException -> false
                        else -> errorHandler.isRecoverableError(errorHandler.mapExceptionToDownloadError(exception))
                    }
                    logger.d(TAG, "Exception is retryable: $isRetryable for ${exception.javaClass.simpleName}")
                    isRetryable
                }
            ) { attempt ->
                logger.d(TAG, "Download attempt $attempt for: $fileName")
                
                if (cancellationRequests[url] == true) {
                    throw CancellationException("Download canceled")
                }
                
                if (attempt > 1) {
                    emit(DownloadProgressDto.Progress(0))
                }
                
                performDownload(url, fileName, destinationPath) { progress ->
                    if (cancellationRequests[url] == true) {
                        throw CancellationException("Download canceled")
                    }
                    emit(DownloadProgressDto.Progress(progress))
                }
            }
            
            logger.i(TAG, "Download completed successfully: $result")
            emit(DownloadProgressDto.Success(result))
            
        } catch (e: CancellationException) {
            logger.i(TAG, "Download was canceled: $fileName")
            emit(DownloadProgressDto.Error(
                errorType = DownloadError.UNKNOWN_ERROR.name,
                message = "Download was canceled"
            ))
            
        } catch (e: Exception) {
            val error = errorHandler.mapExceptionToDownloadError(e)
            val message = errorHandler.getErrorMessage(error)
            logger.e(TAG, "Download failed for: $fileName", e)
            
            emit(DownloadProgressDto.Error(
                errorType = error.name,
                message = message
            ))
            
        } finally {
            performCleanup(url)
        }
    }

    override suspend fun cancelDownload(url: String): Boolean {
        if (!isDownloadInProgress(url)) {
            return false
        }
        
        cancellationRequests[url] = true
        return true
    }

    override fun isDownloadInProgress(url: String): Boolean {
        return activeDownloads[url] == true
    }
    
    private suspend fun performDownload(
        url: String,
        fileName: String,
        destinationPath: String?,
        progressCallback: suspend (Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        
        try {
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("Unexpected response code: ${response.code}")
            }
            
            val contentLength = response.body.contentLength()
            
            if (contentLength > 0 && !fileManagerService.hasEnoughStorageSpace(contentLength)) {
                throw IOException("Insufficient storage space")
            }
            
            val outputFile = fileManagerService.createDownloadFile(fileName, destinationPath)
            
            inputStream = response.body.byteStream()
            
            outputStream = fileManagerService.openFileOutputStream(outputFile)
            
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead: Int
            var totalBytesRead = 0L
            var lastProgressUpdate = 0
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
                val progress = if (contentLength > 0) {
                    (totalBytesRead * 100 / contentLength).toInt()
                } else {
                    min(99, ((totalBytesRead / DEFAULT_BUFFER_SIZE) % 100).toInt())
                }
                
                if (progress > lastProgressUpdate) {
                    progressCallback(progress)
                    lastProgressUpdate = progress
                }
            }
            
            if (lastProgressUpdate < 100) {
                progressCallback(100)
            }
            
            outputFile.absolutePath
            
        } finally {
            inputStream?.close()
            fileManagerService.closeOutputStream(outputStream)
        }
    }
    
    private fun calculateBackoffDelay(attempt: Int): Long {
        val exponentialDelay = BASE_RETRY_DELAY * 2.0.pow(attempt.toDouble()).toLong()
        val jitter = (exponentialDelay * 0.2 * Math.random()).toLong()
        return exponentialDelay + jitter
    }
    
    private fun performCleanup(url: String) {
        logger.d(TAG, "Performing cleanup for URL: $url")
        
        activeDownloads.remove(url)
        
        cancellationRequests.remove(url)
        
        cleanupTasks.remove(url)?.invoke()
        
        logger.d(TAG, "Cleanup completed for URL: $url")
    }
}