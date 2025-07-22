package com.tamersarioglu.clipcatch.data.service

import com.tamersarioglu.clipcatch.data.dto.DownloadProgressDto
import com.tamersarioglu.clipcatch.domain.model.DownloadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

/**
 * Implementation of DownloadManagerService that handles video downloads with progress tracking,
 * file streaming, and retry mechanism with exponential backoff.
 */
@Singleton
class DownloadManagerServiceImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val fileManagerService: FileManagerService
) : DownloadManagerService {

    // Track active downloads by URL
    private val activeDownloads = ConcurrentHashMap<String, Boolean>()
    
    // Track cancellation requests by URL
    private val cancellationRequests = ConcurrentHashMap<String, Boolean>()
    
    // Default buffer size for streaming (8KB)
    private val DEFAULT_BUFFER_SIZE = 8 * 1024
    
    // Maximum number of retry attempts
    private val MAX_RETRY_ATTEMPTS = 3
    
    // Base delay for exponential backoff (in milliseconds)
    private val BASE_RETRY_DELAY = 1000L

    override suspend fun downloadVideo(
        url: String, 
        fileName: String,
        destinationPath: String?
    ): Flow<DownloadProgressDto> = flow {
        // Validate inputs
        if (url.isBlank()) {
            emit(DownloadProgressDto.Error(
                errorType = DownloadError.INVALID_URL.name,
                message = "Download URL cannot be empty"
            ))
            return@flow
        }
        
        if (fileName.isBlank()) {
            emit(DownloadProgressDto.Error(
                errorType = DownloadError.STORAGE_ERROR.name,
                message = "File name cannot be empty"
            ))
            return@flow
        }
        
        // Check if download is already in progress
        if (isDownloadInProgress(url)) {
            emit(DownloadProgressDto.Error(
                errorType = DownloadError.UNKNOWN_ERROR.name,
                message = "Download already in progress for this URL"
            ))
            return@flow
        }
        
        // Mark download as active
        activeDownloads[url] = true
        
        // Clear any previous cancellation request
        cancellationRequests.remove(url)
        
        var currentAttempt = 0
        var lastError: Exception? = null
        
        // Retry loop with exponential backoff
        while (currentAttempt <= MAX_RETRY_ATTEMPTS) {
            if (cancellationRequests[url] == true) {
                emit(DownloadProgressDto.Error(
                    errorType = DownloadError.UNKNOWN_ERROR.name,
                    message = "Download was canceled"
                ))
                break
            }
            
            try {
                // If this is a retry, emit progress update
                if (currentAttempt > 0) {
                    emit(DownloadProgressDto.Progress(0))
                }
                
                // Perform the actual download
                val result = performDownload(url, fileName, destinationPath) { progress ->
                    // Check for cancellation during download
                    if (cancellationRequests[url] == true) {
                        throw CancellationException("Download canceled")
                    }
                    emit(DownloadProgressDto.Progress(progress))
                }
                
                // Download completed successfully
                emit(DownloadProgressDto.Success(result))
                break
                
            } catch (e: CancellationException) {
                emit(DownloadProgressDto.Error(
                    errorType = DownloadError.UNKNOWN_ERROR.name,
                    message = "Download was canceled"
                ))
                break
                
            } catch (e: UnknownHostException) {
                lastError = e
                if (currentAttempt >= MAX_RETRY_ATTEMPTS) {
                    emit(DownloadProgressDto.Error(
                        errorType = DownloadError.NETWORK_ERROR.name,
                        message = "No internet connection available"
                    ))
                }
                
            } catch (e: SocketTimeoutException) {
                lastError = e
                if (currentAttempt >= MAX_RETRY_ATTEMPTS) {
                    emit(DownloadProgressDto.Error(
                        errorType = DownloadError.NETWORK_ERROR.name,
                        message = "Connection timeout during download"
                    ))
                }
                
            } catch (e: IOException) {
                lastError = e
                if (currentAttempt >= MAX_RETRY_ATTEMPTS) {
                    emit(DownloadProgressDto.Error(
                        errorType = DownloadError.STORAGE_ERROR.name,
                        message = "Storage error: ${e.message}"
                    ))
                }
                
            } catch (e: SecurityException) {
                lastError = e
                // Don't retry permission errors
                emit(DownloadProgressDto.Error(
                    errorType = DownloadError.PERMISSION_DENIED.name,
                    message = "Storage permission denied"
                ))
                break
                
            } catch (e: Exception) {
                lastError = e
                if (currentAttempt >= MAX_RETRY_ATTEMPTS) {
                    emit(DownloadProgressDto.Error(
                        errorType = DownloadError.UNKNOWN_ERROR.name,
                        message = "Download failed: ${e.message}"
                    ))
                }
            }
            
            // If we reach here and haven't hit max retries, implement exponential backoff
            if (currentAttempt < MAX_RETRY_ATTEMPTS) {
                val backoffDelay = calculateBackoffDelay(currentAttempt)
                delay(backoffDelay)
                currentAttempt++
            } else {
                break
            }
        }
        
        // Clean up
        activeDownloads.remove(url)
        cancellationRequests.remove(url)
    }

    override suspend fun cancelDownload(url: String): Boolean {
        if (!isDownloadInProgress(url)) {
            return false
        }
        
        // Mark for cancellation
        cancellationRequests[url] = true
        return true
    }

    override fun isDownloadInProgress(url: String): Boolean {
        return activeDownloads[url] == true
    }
    
    /**
     * Performs the actual download operation with progress tracking
     * 
     * @param url The URL to download from
     * @param fileName The name to save the file as
     * @param destinationPath Optional custom destination path
     * @param progressCallback Callback function to report download progress
     * @return The path to the downloaded file
     */
    private suspend fun performDownload(
        url: String,
        fileName: String,
        destinationPath: String?,
        progressCallback: suspend (Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        
        try {
            // Create the request
            val request = Request.Builder()
                .url(url)
                .build()
            
            // Execute the request
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("Unexpected response code: ${response.code}")
            }
            
            // Get content length for progress calculation
            val contentLength = response.body.contentLength()
            
            // Check if we have enough storage space
            if (contentLength > 0 && !fileManagerService.hasEnoughStorageSpace(contentLength)) {
                throw IOException("Insufficient storage space")
            }
            
            // Create the output file
            val outputFile = fileManagerService.createDownloadFile(fileName, destinationPath)
            
            // Get input stream from response
            inputStream = response.body.byteStream()
            
            // Get output stream for file
            outputStream = fileManagerService.openFileOutputStream(outputFile)
            
            // Stream the data with progress updates
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead: Int
            var totalBytesRead = 0L
            var lastProgressUpdate = 0
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
                // Calculate progress percentage
                val progress = if (contentLength > 0) {
                    (totalBytesRead * 100 / contentLength).toInt()
                } else {
                    // If content length is unknown, use indeterminate progress
                    min(99, ((totalBytesRead / DEFAULT_BUFFER_SIZE) % 100).toInt())
                }
                
                // Only emit progress updates when there's a change to avoid flooding
                if (progress > lastProgressUpdate) {
                    progressCallback(progress)
                    lastProgressUpdate = progress
                }
            }
            
            // Ensure 100% progress is reported
            if (lastProgressUpdate < 100) {
                progressCallback(100)
            }
            
            // Return the path to the downloaded file
            outputFile.absolutePath
            
        } finally {
            // Clean up resources
            inputStream?.close()
            fileManagerService.closeOutputStream(outputStream)
        }
    }
    
    /**
     * Calculates the delay for exponential backoff retry strategy
     * 
     * @param attempt The current attempt number (0-based)
     * @return Delay time in milliseconds
     */
    private fun calculateBackoffDelay(attempt: Int): Long {
        // Exponential backoff with jitter: base * 2^attempt + random jitter
        val exponentialDelay = BASE_RETRY_DELAY * 2.0.pow(attempt.toDouble()).toLong()
        val jitter = (exponentialDelay * 0.2 * Math.random()).toLong()
        return exponentialDelay + jitter
    }
}