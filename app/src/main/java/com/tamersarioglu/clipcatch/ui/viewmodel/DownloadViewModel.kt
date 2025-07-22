package com.tamersarioglu.clipcatch.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tamersarioglu.clipcatch.domain.model.DownloadError
import com.tamersarioglu.clipcatch.domain.model.DownloadProgress
import com.tamersarioglu.clipcatch.domain.usecase.DownloadVideoUseCase
import com.tamersarioglu.clipcatch.domain.usecase.GetVideoInfoUseCase
import com.tamersarioglu.clipcatch.domain.usecase.ValidateUrlUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/** ViewModel for managing download screen state and operations */
@HiltViewModel
class DownloadViewModel
@Inject
constructor(
        private val downloadVideoUseCase: DownloadVideoUseCase,
        private val validateUrlUseCase: ValidateUrlUseCase,
        private val getVideoInfoUseCase: GetVideoInfoUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null
    private var validationJob: Job? = null

    /** Updates the URL input and triggers real-time validation */
    fun onUrlChanged(url: String) {
        _uiState.value =
                _uiState.value.copy(
                        url = url,
                        urlErrorMessage = null,
                        error = null,
                        errorMessage = null,
                        isDownloadComplete = false,
                        downloadedFilePath = null
                )

        // Cancel previous validation job
        validationJob?.cancel()

        // Start new validation with debouncing
        validationJob =
                viewModelScope.launch {
                    kotlinx.coroutines.delay(300) // Debounce for 300ms
                    validateUrl(url)
                }
    }

    /** Validates the current URL and updates the UI state */
    private suspend fun validateUrl(url: String) {
        if (url.isBlank()) {
            _uiState.value =
                    _uiState.value.copy(
                            isValidatingUrl = false,
                            isUrlValid = false,
                            urlErrorMessage = null,
                            canStartDownload = false,
                            videoInfo = null
                    )
            return
        }

        _uiState.value = _uiState.value.copy(isValidatingUrl = true)

        try {
            val validationResult = validateUrlUseCase(url)

            _uiState.value =
                    _uiState.value.copy(
                            isValidatingUrl = false,
                            isUrlValid = validationResult.isValid,
                            urlErrorMessage = validationResult.errorMessage,
                            canStartDownload =
                                    validationResult.isValid && !_uiState.value.isDownloading
                    )

            // If URL is valid, extract video info
            if (validationResult.isValid && validationResult.sanitizedUrl != null) {
                extractVideoInfo(validationResult.sanitizedUrl)
            } else {
                _uiState.value = _uiState.value.copy(videoInfo = null)
            }
        } catch (e: Exception) {
            _uiState.value =
                    _uiState.value.copy(
                            isValidatingUrl = false,
                            isUrlValid = false,
                            urlErrorMessage = "Error validating URL: ${e.message}",
                            canStartDownload = false,
                            videoInfo = null
                    )
        }
    }

    /** Extracts video information for the given URL */
    private suspend fun extractVideoInfo(url: String) {
        _uiState.value = _uiState.value.copy(isLoadingVideoInfo = true)

        try {
            val result = getVideoInfoUseCase(url)

            result.fold(
                    onSuccess = { videoInfo ->
                        _uiState.value =
                                _uiState.value.copy(
                                        isLoadingVideoInfo = false,
                                        videoInfo = videoInfo
                                )
                    },
                    onFailure = { exception ->
                        _uiState.value =
                                _uiState.value.copy(
                                        isLoadingVideoInfo = false,
                                        videoInfo = null,
                                        urlErrorMessage =
                                                "Failed to load video info: ${exception.message}"
                                )
                    }
            )
        } catch (e: Exception) {
            _uiState.value =
                    _uiState.value.copy(
                            isLoadingVideoInfo = false,
                            videoInfo = null,
                            urlErrorMessage = "Error loading video info: ${e.message}"
                    )
        }
    }

    /** Starts the video download process */
    fun downloadVideo() {
        val currentState = _uiState.value

        if (!currentState.canStartDownload || currentState.url.isBlank()) {
            return
        }

        // Cancel any existing download
        downloadJob?.cancel()

        // Reset state for new download
        _uiState.value =
                currentState.copy(
                        isDownloading = true,
                        downloadProgress = 0,
                        isDownloadComplete = false,
                        downloadedFilePath = null,
                        error = null,
                        errorMessage = null,
                        canStartDownload = false
                )

        downloadJob =
                viewModelScope.launch {
                    try {
                        downloadVideoUseCase(currentState.url)
                                .catch { exception -> handleDownloadError(exception) }
                                .collect { progress -> handleDownloadProgress(progress) }
                    } catch (e: Exception) {
                        handleDownloadError(e)
                    }
                }
    }

    /** Handles download progress updates */
    private fun handleDownloadProgress(progress: DownloadProgress) {
        when (progress) {
            is DownloadProgress.Progress -> {
                _uiState.value =
                        _uiState.value.copy(downloadProgress = progress.percentage.coerceIn(0, 100))
            }
            is DownloadProgress.Success -> {
                _uiState.value =
                        _uiState.value.copy(
                                isDownloading = false,
                                isDownloadComplete = true,
                                downloadedFilePath = progress.filePath,
                                downloadProgress = 100,
                                canStartDownload = true
                        )
            }
            is DownloadProgress.Error -> {
                _uiState.value =
                        _uiState.value.copy(
                                isDownloading = false,
                                error = progress.error,
                                errorMessage = getErrorMessage(progress.error),
                                canStartDownload = true
                        )
            }
        }
    }

    /** Handles download errors */
    private fun handleDownloadError(exception: Throwable) {
        val message =
                when (exception) {
                    is IllegalArgumentException -> exception.message ?: "Invalid input"
                    else -> "Download failed: ${exception.message}"
                }

        _uiState.value =
                _uiState.value.copy(
                        isDownloading = false,
                        error = DownloadError.UNKNOWN_ERROR,
                        errorMessage = message,
                        canStartDownload = true
                )
    }

    /** Converts DownloadError enum to user-friendly error messages */
    private fun getErrorMessage(error: DownloadError): String {
        return when (error) {
            DownloadError.INVALID_URL ->
                    "The provided URL is not valid. Please check and try again."
            DownloadError.NETWORK_ERROR ->
                    "Network connection error. Please check your internet connection and try again."
            DownloadError.STORAGE_ERROR ->
                    "Unable to save the file. Please check storage permissions and available space."
            DownloadError.PERMISSION_DENIED ->
                    "Storage permission is required to download videos. Please grant permission and try again."
            DownloadError.VIDEO_UNAVAILABLE ->
                    "This video is not available for download. It may be private or deleted."
            DownloadError.INSUFFICIENT_STORAGE ->
                    "Not enough storage space available. Please free up some space and try again."
            DownloadError.AGE_RESTRICTED -> "This video is age-restricted and cannot be downloaded."
            DownloadError.GEO_BLOCKED -> "This video is not available in your region."
            DownloadError.UNKNOWN_ERROR -> "An unexpected error occurred. Please try again."
        }
    }

    /** Cancels the current download operation */
    fun cancelDownload() {
        downloadJob?.cancel()
        _uiState.value =
                _uiState.value.copy(
                        isDownloading = false,
                        downloadProgress = 0,
                        canStartDownload = _uiState.value.isUrlValid
                )
    }

    /** Clears the current error state */
    fun clearError() {
        _uiState.value =
                _uiState.value.copy(error = null, errorMessage = null, urlErrorMessage = null)
    }

    /** Resets the download state (useful for starting a new download) */
    fun resetDownloadState() {
        downloadJob?.cancel()
        _uiState.value =
                _uiState.value.copy(
                        isDownloading = false,
                        downloadProgress = 0,
                        isDownloadComplete = false,
                        downloadedFilePath = null,
                        error = null,
                        errorMessage = null,
                        canStartDownload = _uiState.value.isUrlValid
                )
    }

    override fun onCleared() {
        super.onCleared()
        downloadJob?.cancel()
        validationJob?.cancel()
    }
}
