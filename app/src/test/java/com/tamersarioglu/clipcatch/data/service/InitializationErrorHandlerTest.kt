package com.tamersarioglu.clipcatch.data.service

import com.tamersarioglu.clipcatch.domain.model.InitializationError
import com.tamersarioglu.clipcatch.util.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InitializationErrorHandlerTest {

    private lateinit var logger: Logger
    private lateinit var errorHandler: InitializationErrorHandlerImpl

    @Before
    fun setUp() {
        logger = mockk(relaxed = true)
        errorHandler = InitializationErrorHandlerImpl(logger)
    }

    @Test
    fun `handleError should successfully process native library error`() {
        // Given
        val error = InitializationError.NativeLibraryError("Failed to extract native libraries")

        // When
        val result = errorHandler.handleError(error)

        // Then
        assertTrue("Result should be handled", result is ErrorHandlingResult.Handled)
        val handledResult = result as ErrorHandlingResult.Handled
        assertEquals("Should be categorized as recoverable", ErrorCategory.RECOVERABLE, handledResult.category)
        assertTrue("Should suggest recovery action", handledResult.recoveryAction is RecoveryAction.ReExtractFiles)

        // Verify logging
        verify { logger.enter(any(), "handleError", "NativeLibraryError") }
        verify { logger.exit(any(), "handleError", "Handled") }
    }

    @Test
    fun `handleError should handle processing failure gracefully`() {
        // Given
        val error = InitializationError.GenericError("Test error")
        every { logger.logError(any(), any(), any(), any()) } throws RuntimeException("Logging failed")

        // When
        val result = errorHandler.handleError(error)

        // Then
        assertTrue("Result should be failed", result is ErrorHandlingResult.Failed)
        val failedResult = result as ErrorHandlingResult.Failed
        assertTrue("Should contain error message", failedResult.reason.contains("Failed to handle initialization error"))
    }

    @Test
    fun `suggestRecoveryAction should suggest file re-extraction for native library extraction error`() {
        // Given
        val error = InitializationError.NativeLibraryError("Failed to extract native libraries from APK")

        // When
        val recoveryAction = errorHandler.suggestRecoveryAction(error)

        // Then
        assertTrue("Should suggest file re-extraction", recoveryAction is RecoveryAction.ReExtractFiles)
        val reExtractAction = recoveryAction as RecoveryAction.ReExtractFiles
        assertEquals("Should target lib/ pattern", "lib/", reExtractAction.targetPattern)
    }

    @Test
    fun `suggestRecoveryAction should suggest retry for native library loading error`() {
        // Given
        val error = InitializationError.NativeLibraryError("Failed to load native library")

        // When
        val recoveryAction = errorHandler.suggestRecoveryAction(error)

        // Then
        assertTrue("Should suggest retry", recoveryAction is RecoveryAction.Retry)
        val retryAction = recoveryAction as RecoveryAction.Retry
        assertEquals("Should have 2 max attempts", 2, retryAction.maxAttempts)
        assertEquals("Should have 2000ms delay", 2000L, retryAction.delayMs)
    }

    @Test
    fun `suggestRecoveryAction should suggest directory recreation for Python environment error`() {
        // Given
        val error = InitializationError.PythonEnvironmentError("Python directory does not exist")

        // When
        val recoveryAction = errorHandler.suggestRecoveryAction(error)

        // Then
        assertTrue("Should suggest directory recreation", recoveryAction is RecoveryAction.RecreateDirectories)
        val recreateAction = recoveryAction as RecoveryAction.RecreateDirectories
        assertTrue("Should include python directory", recreateAction.directories.contains("python"))
    }

    @Test
    fun `suggestRecoveryAction should suggest alternative method for YouTube-DL initialization error`() {
        // Given
        val error = InitializationError.YouTubeDLError("YouTube-DL initialization failed")

        // When
        val recoveryAction = errorHandler.suggestRecoveryAction(error)

        // Then
        assertTrue("Should suggest alternative method", recoveryAction is RecoveryAction.UseAlternativeMethod)
        val alternativeAction = recoveryAction as RecoveryAction.UseAlternativeMethod
        assertEquals("Should suggest alternative YouTube-DL initialization", 
            "Alternative YouTube-DL initialization", alternativeAction.alternativeMethod)
    }

    @Test
    fun `suggestRecoveryAction should suggest no recovery for file permission error`() {
        // Given
        val error = InitializationError.FileExtractionError("Permission denied when extracting files")

        // When
        val recoveryAction = errorHandler.suggestRecoveryAction(error)

        // Then
        assertTrue("Should suggest no recovery", recoveryAction is RecoveryAction.NoRecovery)
        val noRecoveryAction = recoveryAction as RecoveryAction.NoRecovery
        assertTrue("Should mention manual intervention", 
            noRecoveryAction.reason.contains("manual intervention"))
    }

    @Test
    fun `suggestRecoveryAction should suggest retry for recoverable generic error`() {
        // Given
        val error = InitializationError.GenericError("Generic recoverable error", recoverable = true)

        // When
        val recoveryAction = errorHandler.suggestRecoveryAction(error)

        // Then
        assertTrue("Should suggest retry", recoveryAction is RecoveryAction.Retry)
    }

    @Test
    fun `suggestRecoveryAction should suggest no recovery for non-recoverable generic error`() {
        // Given
        val error = InitializationError.GenericError("Generic non-recoverable error", recoverable = false)

        // When
        val recoveryAction = errorHandler.suggestRecoveryAction(error)

        // Then
        assertTrue("Should suggest no recovery", recoveryAction is RecoveryAction.NoRecovery)
    }

    @Test
    fun `categorizeError should categorize native library errors correctly`() {
        // Test permission error
        val permissionError = InitializationError.NativeLibraryError("Permission denied")
        assertEquals("Permission error should require user intervention", 
            ErrorCategory.USER_INTERVENTION_REQUIRED, errorHandler.categorizeError(permissionError))

        // Test missing library error
        val missingError = InitializationError.NativeLibraryError("Missing native library")
        assertEquals("Missing library should be recoverable", 
            ErrorCategory.RECOVERABLE, errorHandler.categorizeError(missingError))

        // Test extraction error
        val extractionError = InitializationError.NativeLibraryError("Failed to extract")
        assertEquals("Extraction error should be recoverable", 
            ErrorCategory.RECOVERABLE, errorHandler.categorizeError(extractionError))

        // Test loading error
        val loadingError = InitializationError.NativeLibraryError("Failed to load library")
        assertEquals("Loading error should be transient", 
            ErrorCategory.TRANSIENT, errorHandler.categorizeError(loadingError))
    }

    @Test
    fun `categorizeError should categorize Python environment errors correctly`() {
        // Test directory error
        val directoryError = InitializationError.PythonEnvironmentError("Python directory missing")
        assertEquals("Directory error should be recoverable", 
            ErrorCategory.RECOVERABLE, errorHandler.categorizeError(directoryError))

        // Test permission error
        val permissionError = InitializationError.PythonEnvironmentError("Permission denied")
        assertEquals("Permission error should require user intervention", 
            ErrorCategory.USER_INTERVENTION_REQUIRED, errorHandler.categorizeError(permissionError))
    }

    @Test
    fun `categorizeError should categorize file extraction errors correctly`() {
        // Test APK error
        val apkError = InitializationError.FileExtractionError("APK file not found")
        assertEquals("APK error should be critical", 
            ErrorCategory.CRITICAL, errorHandler.categorizeError(apkError))

        // Test permission error
        val permissionError = InitializationError.FileExtractionError("Permission denied")
        assertEquals("Permission error should require user intervention", 
            ErrorCategory.USER_INTERVENTION_REQUIRED, errorHandler.categorizeError(permissionError))

        // Test space error
        val spaceError = InitializationError.FileExtractionError("Not enough space")
        assertEquals("Space error should require user intervention", 
            ErrorCategory.USER_INTERVENTION_REQUIRED, errorHandler.categorizeError(spaceError))
    }

    @Test
    fun `categorizeError should categorize YouTube-DL errors correctly`() {
        // Test incompatible error
        val incompatibleError = InitializationError.YouTubeDLError("Incompatible version")
        assertEquals("Incompatible error should be critical", 
            ErrorCategory.CRITICAL, errorHandler.categorizeError(incompatibleError))

        // Test initialization error
        val initError = InitializationError.YouTubeDLError("Initialization failed")
        assertEquals("Initialization error should be transient", 
            ErrorCategory.TRANSIENT, errorHandler.categorizeError(initError))
    }

    @Test
    fun `categorizeError should categorize generic errors correctly`() {
        // Test recoverable generic error
        val recoverableError = InitializationError.GenericError("Recoverable error", recoverable = true)
        assertEquals("Recoverable generic error should be recoverable", 
            ErrorCategory.RECOVERABLE, errorHandler.categorizeError(recoverableError))

        // Test non-recoverable generic error
        val nonRecoverableError = InitializationError.GenericError("Non-recoverable error", recoverable = false)
        assertEquals("Non-recoverable generic error should be unknown", 
            ErrorCategory.UNKNOWN, errorHandler.categorizeError(nonRecoverableError))
    }

    @Test
    fun `isRecoverable should return correct recoverability based on error and category`() {
        // Test recoverable native library error
        val recoverableError = InitializationError.NativeLibraryError("Missing library")
        assertTrue("Recoverable native library error should be recoverable", 
            errorHandler.isRecoverable(recoverableError))

        // Test critical YouTube-DL error
        val criticalError = InitializationError.YouTubeDLError("Incompatible version")
        assertFalse("Critical YouTube-DL error should not be recoverable", 
            errorHandler.isRecoverable(criticalError))

        // Test permission error
        val permissionError = InitializationError.FileExtractionError("Permission denied")
        assertFalse("Permission error should not be recoverable", 
            errorHandler.isRecoverable(permissionError))
    }

    @Test
    fun `logError should log error with appropriate severity and context`() {
        // Given
        val nativeLibraryError = InitializationError.NativeLibraryError("Test native library error")
        val youtubeDLError = InitializationError.YouTubeDLError("Test YouTube-DL error")

        // When
        errorHandler.logError(nativeLibraryError)
        errorHandler.logError(youtubeDLError)

        // Then
        verify { logger.logError(any(), eq("Initialization Error"), any(), any()) }
        verify { logger.w(any(), "Native Library Error: Test native library error") }
        verify { logger.e(any(), "YouTube-DL Error (Critical): Test YouTube-DL error") }
    }

    @Test
    fun `logError should include underlying cause when present`() {
        // Given
        val underlyingException = RuntimeException("Underlying cause")
        val error = InitializationError.NativeLibraryError("Test error", underlyingException)

        // When
        errorHandler.logError(error)

        // Then
        verify { logger.w(any(), "Underlying cause: Underlying cause", underlyingException) }
    }

    @Test
    fun `logError should handle different error types with appropriate logging levels`() {
        // Given
        val recoverableGenericError = InitializationError.GenericError("Recoverable", recoverable = true)
        val criticalGenericError = InitializationError.GenericError("Critical", recoverable = false)

        // When
        errorHandler.logError(recoverableGenericError)
        errorHandler.logError(criticalGenericError)

        // Then
        verify { logger.w(any(), "Generic Error (Recoverable): Recoverable") }
        verify { logger.e(any(), "Generic Error (Critical): Critical") }
    }
}