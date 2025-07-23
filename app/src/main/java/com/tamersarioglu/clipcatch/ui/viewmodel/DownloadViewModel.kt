package com.tamersarioglu.clipcatch.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tamersarioglu.clipcatch.data.util.ErrorHandler
import com.tamersarioglu.clipcatch.data.util.ErrorRecoveryAction
import com.tamersarioglu.clipcatch.data.util.Logger
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

@HiltViewModel
class DownloadViewModel
@Inject
constructor(
        private val downloadVideoUseCase: DownloadVideoUseCase,
        private val validateUrlUseCase: ValidateUrlUseCase,
        private val getVideoInfoUseCase: GetVideoInfoUseCase,
        private val errorHandler: ErrorHandler,
        private val logger: Logger
) : ViewModel() {
    
    companion object {
        private const val TAG = "DownloadViewModel"
    }

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null
    private var validationJob: Job? = null

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

        validationJob?.cancel()

        validationJob =
                viewModelScope.launch {
                    kotlinx.coroutines.delay(300)
                    validateUrl(url)
                }
    }

    private suspend fun validateUrl(url: String) {
        logger.enter(TAG, "validateUrl", url)
        
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
            logger.d(TAG, "URL validation result: ${validationResult.isValid}")

            _uiState.value =
                    _uiState.value.copy(
                            isValidatingUrl = false,
                            isUrlValid = validationResult.isValid,
                            urlErrorMessage = validationResult.errorMessage,
                            canStartDownload = validationResult.isValid && !_uiState.value.isDownloading
                    )

            // If URL is valid, extract video info
            if (validationResult.isValid && validationResult.sanitizedUrl != null) {
                extractVideoInfo(validationResult.sanitizedUrl)
            } else {
                _uiState.value = _uiState.value.copy(videoInfo = null)
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error during URL validation", e)
            val error = errorHandler.mapExceptionToDownloadError(e)
            val message = errorHandler.getErrorMessage(error)
            
            _uiState.value =
                    _uiState.value.copy(
                            isValidatingUrl = false,
                            isUrlValid = false,
                            urlErrorMessage = message,
                            canStartDownload = false,
                            videoInfo = null
                    )
        }
    }

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

    fun downloadVideo() {
        val currentState = _uiState.value

        if (!currentState.canStartDownload || currentState.url.isBlank()) {
            return
        }

        downloadJob?.cancel()

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

    private fun handleDownloadError(exception: Throwable) {
        logger.e(TAG, "Download error occurred", exception)
        
        val error = errorHandler.mapExceptionToDownloadError(exception)
        val message = errorHandler.getErrorMessage(error)
        val recoveryAction = errorHandler.getRecoveryAction(error)
        
        logger.d(TAG, "Mapped error: $error, Recovery action: $recoveryAction")

        _uiState.value =
                _uiState.value.copy(
                        isDownloading = false,
                        error = error,
                        errorMessage = message,
                        canStartDownload = true,
                        recoveryAction = recoveryAction
                )
    }

    private fun getErrorMessage(error: DownloadError): String {
        return errorHandler.getErrorMessage(error)
    }
    
    fun attemptErrorRecovery() {
        val currentState = _uiState.value
        val error = currentState.error ?: return
        val recoveryAction = currentState.recoveryAction ?: return
        
        logger.i(TAG, "Attempting error recovery for: $error with action: $recoveryAction")
        
        when (recoveryAction) {
            ErrorRecoveryAction.RETRY -> {
                if (errorHandler.isRecoverableError(error)) {
                    logger.d(TAG, "Retrying download operation")
                    downloadVideo()
                } else {
                    logger.w(TAG, "Error is not recoverable, cannot retry")
                }
            }
            ErrorRecoveryAction.REQUEST_PERMISSION -> {
                logger.d(TAG, "Permission request needed - handled by UI")
            }
            ErrorRecoveryAction.CHECK_STORAGE -> {
                logger.d(TAG, "Storage check needed - handled by UI")
            }
            ErrorRecoveryAction.FREE_STORAGE -> {
                logger.d(TAG, "Storage cleanup needed - handled by UI")
            }
            ErrorRecoveryAction.CORRECT_URL -> {
                logger.d(TAG, "URL correction needed - clearing current URL")
                onUrlChanged("")
            }
            ErrorRecoveryAction.TRY_DIFFERENT_VIDEO -> {
                logger.d(TAG, "Different video needed - clearing current URL")
                onUrlChanged("")
            }
            ErrorRecoveryAction.CONTACT_SUPPORT -> {
                logger.d(TAG, "Support contact needed - handled by UI")
            }
        }
    }
    
    fun getDetailedErrorInfo(): String? {
        val currentState = _uiState.value
        val error = currentState.error ?: return null
        
        return buildString {
            append("Error Type: ${error.name}\n")
            append("Message: ${currentState.errorMessage}\n")
            append("Recovery Action: ${currentState.recoveryAction?.name}\n")
            append("Is Recoverable: ${errorHandler.isRecoverableError(error)}\n")
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _uiState.value =
                _uiState.value.copy(
                        isDownloading = false,
                        downloadProgress = 0,
                        canStartDownload = _uiState.value.isUrlValid
                )
    }

    fun clearError() {
        _uiState.value =
                _uiState.value.copy(error = null, errorMessage = null, urlErrorMessage = null)
    }

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
