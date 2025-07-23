package com.tamersarioglu.clipcatch.data.datasource

import com.tamersarioglu.clipcatch.data.dto.DownloadProgressDto
import com.tamersarioglu.clipcatch.domain.model.DownloadError
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton


interface DownloadDataSource {
    suspend fun downloadVideo(url: String, fileName: String): Flow<DownloadProgressDto>
}

@Singleton
class DownloadDataSourceImpl @Inject constructor() : DownloadDataSource {
    
    override suspend fun downloadVideo(url: String, fileName: String): Flow<DownloadProgressDto> {
        return kotlinx.coroutines.flow.flow {
            try {
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
                
                // Check if URL is accessible
                if (!isUrlAccessible(url)) {
                    emit(DownloadProgressDto.Error(
                        errorType = DownloadError.VIDEO_UNAVAILABLE.name,
                        message = "Video is not accessible or has been removed"
                    ))
                    return@flow
                }
                
                // TODO: Replace with actual download implementation
                // For now, simulate download progress
                
                // Emit progress updates
                for (progress in 0..100 step 10) {
                    emit(DownloadProgressDto.Progress(progress))
                    kotlinx.coroutines.delay(100) // Simulate download time
                }
                
                // Simulate successful completion
                val filePath = "/storage/emulated/0/Download/$fileName"
                emit(DownloadProgressDto.Success(filePath))
                
            } catch (e: UnknownHostException) {
                emit(DownloadProgressDto.Error(
                    errorType = DownloadError.NETWORK_ERROR.name,
                    message = "No internet connection available"
                ))
            } catch (e: SocketTimeoutException) {
                emit(DownloadProgressDto.Error(
                    errorType = DownloadError.NETWORK_ERROR.name,
                    message = "Connection timeout during download"
                ))
            } catch (e: IOException) {
                emit(DownloadProgressDto.Error(
                    errorType = DownloadError.STORAGE_ERROR.name,
                    message = "Storage error: ${e.message}"
                ))
            } catch (e: SecurityException) {
                emit(DownloadProgressDto.Error(
                    errorType = DownloadError.PERMISSION_DENIED.name,
                    message = "Storage permission denied"
                ))
            } catch (e: Exception) {
                emit(DownloadProgressDto.Error(
                    errorType = DownloadError.UNKNOWN_ERROR.name,
                    message = "Download failed: ${e.message}"
                ))
            }
        }
    }

    private fun isUrlAccessible(url: String): Boolean {

        return url.isNotBlank()
    }
}

class DownloadDataException(
    val error: DownloadError,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)