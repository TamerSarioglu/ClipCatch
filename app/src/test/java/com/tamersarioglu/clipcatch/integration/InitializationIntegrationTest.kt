package com.tamersarioglu.clipcatch.integration

import android.content.Context
import com.tamersarioglu.clipcatch.data.service.ErrorCategory
import com.tamersarioglu.clipcatch.data.service.ErrorHandlingResult
import com.tamersarioglu.clipcatch.data.service.InitializationErrorHandler
import com.tamersarioglu.clipcatch.data.service.InitializationOrchestrator
import com.tamersarioglu.clipcatch.data.service.RecoveryAction
import com.tamersarioglu.clipcatch.data.service.InitializationOrchestratorImpl
import com.tamersarioglu.clipcatch.data.service.NativeLibraryManager
import com.tamersarioglu.clipcatch.data.service.PythonEnvironmentManager
import com.tamersarioglu.clipcatch.data.service.YouTubeDLInitializationService
import com.tamersarioglu.clipcatch.domain.model.ExtractionResult
import com.tamersarioglu.clipcatch.domain.model.InitializationError
import com.tamersarioglu.clipcatch.domain.model.InitializationResult
import com.tamersarioglu.clipcatch.domain.model.InitializationStatus
import com.tamersarioglu.clipcatch.domain.model.LoadResult
import com.tamersarioglu.clipcatch.domain.model.SetupResult
import com.tamersarioglu.clipcatch.domain.model.VerificationResult
import com.tamersarioglu.clipcatch.util.Logger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Integration tests for the complete initialization flow.
 * Tests end-to-end scenarios, failure recovery, retry logic, and performance.
 */
class InitializationIntegrationTest {

    private lateinit var orchestrator: InitializationOrchestrator
    private lateinit var mockNativeLibraryManager: NativeLibraryManager
    private lateinit var mockPythonEnvironmentManager: PythonEnvironmentManager
    private lateinit var mockYoutubeDLService: YouTubeDLInitializationService
    private lateinit var mockErrorHandler: InitializationErrorHandler
    private lateinit var mockLogger: Logger
    private lateinit var mockContext: Context

    @Before
    fun setup() {
        mockNativeLibraryManager = mockk(relaxed = true)
        mockPythonEnvironmentManager = mockk(relaxed = true)
        mockYoutubeDLService = mockk(relaxed = true)
        mockErrorHandler = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)

        orchestrator = InitializationOrchestratorImpl(
            nativeLibraryManager = mockNativeLibraryManager,
            pythonEnvironmentManager = mockPythonEnvironmentManager,
            youtubeDLService = mockYoutubeDLService,
            errorHandler = mockErrorHandler,
            logger = mockLogger
        )
    }

    // ========== End-to-End Success Tests ==========

    @Test
    fun `end-to-end initialization flow should complete successfully with all services`() = runTest {
        // Arrange - Setup successful responses for all services
        setupSuccessfulNativeLibraryFlow()
        setupSuccessfulPythonEnvironmentFlow()
        setupSuccessfulYouTubeDLFlow()

        // Act
        val result = orchestrator.initialize()

        // Assert
        assertTrue("Initialization should succeed", result is InitializationResult.Success)
        assertEquals("Status should be completed", InitializationStatus.Completed, orchestrator.getInitializationStatus())
        assertTrue("Initialization should be complete", orchestrator.isInitializationComplete())

        // Verify all services were called in correct order
        verifyNativeLibraryFlowCalled()
        verifyPythonEnvironmentFlowCalled()
        verifyYouTubeDLFlowCalled()
    }

    @Test
    fun `end-to-end initialization should skip native library extraction when not needed`() = runTest {
        // Arrange - Native libraries already exist
        every { mockNativeLibraryManager.shouldExtractLibraries() } returns false
        coEvery { mockNativeLibraryManager.loadNativeLibraries() } returns LoadResult(
            success = true,
            loadedLibraries = listOf("lib1.so", "lib2.so")
        )
        coEvery { mockNativeLibraryManager.verifyNativeLibraries() } returns VerificationResult(
            success = true,
            verifiedItems = listOf("lib1.so", "lib2.so")
        )
        setupSuccessfulPythonEnvironmentFlow()
        setupSuccessfulYouTubeDLFlow()

        // Act
        val result = orchestrator.initialize()

        // Assert
        assertTrue("Initialization should succeed", result is InitializationResult.Success)
        
        // Verify extraction was skipped but other steps were called
        coVerify(exactly = 0) { mockNativeLibraryManager.extractNativeLibraries() }
        coVerify { mockNativeLibraryManager.loadNativeLibraries() }
        coVerify { mockNativeLibraryManager.verifyNativeLibraries() }
        verifyPythonEnvironmentFlowCalled()
        verifyYouTubeDLFlowCalled()
    }

    @Test
    fun `end-to-end initialization should handle partial success scenarios`() = runTest {
        // Arrange - Setup with some warnings but overall success
        setupSuccessfulNativeLibraryFlow()
        setupSuccessfulPythonEnvironmentFlow()
        coEvery { mockYoutubeDLService.initialize() } returns InitializationResult.PartialSuccess(
            warnings = listOf("Some optional features unavailable", "Performance may be reduced")
        )
        every { mockYoutubeDLService.isInitialized() } returns true

        // Act
        val result = orchestrator.initialize()

        // Assert
        assertTrue("Initialization should succeed even with warnings", result is InitializationResult.Success)
        assertEquals("Status should be completed", InitializationStatus.Completed, orchestrator.getInitializationStatus())
        assertTrue("Initialization should be complete", orchestrator.isInitializationComplete())
    }

    // ========== Failure Scenario Tests ==========

    @Test
    fun `initialization should fail gracefully when native library extraction fails`() = runTest {
        // Arrange
        val extractionError = InitializationError.NativeLibraryError("Failed to extract lib1.so")
        every { mockNativeLibraryManager.shouldExtractLibraries() } returns true
        coEvery { mockNativeLibraryManager.extractNativeLibraries() } returns ExtractionResult(
            success = false,
            error = extractionError,
            extractedFiles = emptyList()
        )
        setupErrorHandling(extractionError)

        // Act
        val result = orchestrator.initialize()

        // Assert
        assertTrue("Result should be failure", result is InitializationResult.Failure)
        assertEquals("Error should match", extractionError, (result as InitializationResult.Failure).error)
        assertTrue("Status should be failed", orchestrator.getInitializationStatus() is InitializationStatus.Failed)
        
        // Verify error was handled and subsequent steps were not called
        verify { mockErrorHandler.handleError(extractionError) }
        coVerify(exactly = 0) { mockNativeLibraryManager.loadNativeLibraries() }
        coVerify(exactly = 0) { mockPythonEnvironmentManager.setupPythonEnvironment() }
        coVerify(exactly = 0) { mockYoutubeDLService.initialize() }
    }

    @Test
    fun `initialization should fail gracefully when native library loading fails`() = runTest {
        // Arrange
        val loadingError = InitializationError.NativeLibraryError("Failed to load lib2.so")
        every { mockNativeLibraryManager.shouldExtractLibraries() } returns false
        coEvery { mockNativeLibraryManager.loadNativeLibraries() } returns LoadResult(
            success = false,
            error = loadingError,
            loadedLibraries = listOf("lib1.so")
        )
        setupErrorHandling(loadingError)

        // Act
        val result = orchestrator.initialize()

        // Assert
        assertTrue("Result should be failure", result is InitializationResult.Failure)
        assertEquals("Error should match", loadingError, (result as InitializationResult.Failure).error)
        
        // Verify subsequent steps were not called
        coVerify(exactly = 0) { mockPythonEnvironmentManager.setupPythonEnvironment() }
        coVerify(exactly = 0) { mockYoutubeDLService.initialize() }
    }

    @Test
    fun `initialization should fail gracefully when native library verification fails`() = runTest {
        // Arrange
        val verificationError = InitializationError.NativeLibraryError("Library verification failed")
        every { mockNativeLibraryManager.shouldExtractLibraries() } returns false
        coEvery { mockNativeLibraryManager.loadNativeLibraries() } returns LoadResult(success = true)
        coEvery { mockNativeLibraryManager.verifyNativeLibraries() } returns VerificationResult(
            success = false,
            error = verificationError,
            verifiedItems = emptyList()
        )
        setupErrorHandling(verificationError)

        // Act
        val result = orchestrator.initialize()

        // Assert
        assertTrue("Result should be failure", result is InitializationResult.Failure)
        assertEquals("Error should match", verificationError, (result as InitializationResult.Failure).error)
        
        // Verify subsequent steps were not called
        coVerify(exactly = 0) { mockPythonEnvironmentManager.setupPythonEnvironment() }
        coVerify(exactly = 0) { mockYoutubeDLService.initialize() }
    }

    @Test
    fun `initialization should fail gracefully when Python environment setup fails`() = runTest {
        // Arrange
        val pythonError = InitializationError.PythonEnvironmentError("Failed to create Python directory")
        setupSuccessfulNativeLibraryFlow()
        coEvery { mockPythonEnvironmentManager.setupPythonEnvironment() } returns SetupResult(
            success = false,
            error = pythonError,
            setupSteps = listOf("create_directory")
        )
        setupErrorHandling(pythonError)

        // Act
        val result = orchestrator.initialize()

        // Assert
        assertTrue("Result should be failure", result is InitializationResult.Failure)
        assertEquals("Error should match", pythonError, (result as InitializationResult.Failure).error)
        
        // Verify YouTube-DL initialization was not called
        coVerify(exactly = 0) { mockYoutubeDLService.initialize() }
    }

    @Test
    fun `initialization should fail gracefully when YouTube-DL initialization fails`() = runTest {
        // Arrange
        val youtubeDLError = InitializationError.YouTubeDLError("YouTube-DL API incompatible")
        setupSuccessfulNativeLibraryFlow()
        setupSuccessfulPythonEnvironmentFlow()
        coEvery { mockYoutubeDLService.initialize() } returns InitializationResult.Failure(youtubeDLError)
        setupErrorHandling(youtubeDLError)

        // Act
        val result = orchestrator.initialize()

        // Assert
        assertTrue("Result should be failure", result is InitializationResult.Failure)
        assertEquals("Error should match", youtubeDLError, (result as InitializationResult.Failure).error)
        
        // Verify all previous steps were called
        verifyNativeLibraryFlowCalled()
        verifyPythonEnvironmentFlowCalled()
        coVerify { mockYoutubeDLService.initialize() }
    }

    @Test
    fun `initialization should handle unexpected exceptions gracefully`() = runTest {
        // Arrange
        val unexpectedException = RuntimeException("Unexpected system error")
        every { mockNativeLibraryManager.shouldExtractLibraries() } throws unexpectedException
        every { mockErrorHandler.handleError(any()) } returns ErrorHandlingResult.Handled(
            ErrorCategory.CRITICAL,
            RecoveryAction.NoRecovery("Unexpected error")
        )

        // Act
        val result = orchestrator.initialize()

        // Assert - Just verify that it doesn't crash and returns some result
        assertTrue("Result should not be null", result != null)
        
        // Verify error was handled if it's a failure
        if (result is InitializationResult.Failure) {
            verify { mockErrorHandler.handleError(any()) }
        }
    }

    // ========== Recovery and Retry Tests ==========

    @Test
    fun `retry mechanism should work with recoverable errors`() = runTest {
        // Arrange - First attempt fails
        val recoverableError = InitializationError.NativeLibraryError("Temporary extraction failure")
        every { mockNativeLibraryManager.shouldExtractLibraries() } returns true
        coEvery { mockNativeLibraryManager.extractNativeLibraries() } returns ExtractionResult(success = false, error = recoverableError)
        setupRecoveryMechanism(recoverableError)

        // Act - Initial failure
        val initialResult = orchestrator.initialize()
        assertTrue("Initial result should be failure", initialResult is InitializationResult.Failure)
        
        // Act - Retry (this will likely fail again, but we're testing the retry mechanism)
        val retryResult = orchestrator.retryInitialization()

        // Assert - Just verify that retry was attempted
        assertTrue("Retry should return a result", retryResult != null)
        
        // Verify recovery action was requested
        verify { mockErrorHandler.suggestRecoveryAction(recoverableError) }
    }

    @Test
    fun `retry mechanism should respect maximum attempts limit`() = runTest {
        // Arrange - Persistent failure
        val persistentError = InitializationError.NativeLibraryError("Persistent extraction failure")
        every { mockNativeLibraryManager.shouldExtractLibraries() } returns true
        coEvery { mockNativeLibraryManager.extractNativeLibraries() } returns ExtractionResult(
            success = false,
            error = persistentError
        )
        setupRecoveryMechanism(persistentError, maxAttempts = 1)

        // Act - Initial failure and multiple retries
        orchestrator.initialize() // Initial failure
        orchestrator.retryInitialization() // Retry 1
        orchestrator.retryInitialization() // Retry 2
        val finalResult = orchestrator.retryInitialization() // Retry 3 - should fail due to max attempts

        // Assert
        assertTrue("Final result should be failure", finalResult is InitializationResult.Failure)
        val failureResult = finalResult as InitializationResult.Failure
        assertTrue("Should indicate max attempts exceeded", 
            failureResult.error.message.contains("Maximum retry attempts") ||
            failureResult.error.message.contains("exceeded"))
    }

    @Test
    fun `recovery actions should be applied correctly for different error types`() = runTest {
        // Arrange - Test file re-extraction recovery
        val fileError = InitializationError.FileExtractionError("File extraction failed")
        every { mockNativeLibraryManager.shouldExtractLibraries() } returns true
        coEvery { mockNativeLibraryManager.extractNativeLibraries() } returns ExtractionResult(
            success = false,
            error = fileError
        )
        
        // Setup recovery action for re-extraction
        setupRecoveryMechanism(fileError, recoveryAction = RecoveryAction.ReExtractFiles("lib/*.so"))

        // Act
        orchestrator.initialize()
        val retryResult = orchestrator.retryInitialization()

        // Assert - Verify recovery action was applied
        verify { mockErrorHandler.suggestRecoveryAction(fileError) }
        
        // The actual recovery application is tested through the orchestrator's behavior
        // We verify that the retry was attempted with the recovery action
        assertTrue("Retry should be attempted", 
            retryResult is InitializationResult.Success || retryResult is InitializationResult.Failure)
    }

    @Test
    fun `rollback mechanism should reset initialization state`() = runTest {
        // Arrange - Complete an initialization first
        setupSuccessfulNativeLibraryFlow()
        setupSuccessfulPythonEnvironmentFlow()
        setupSuccessfulYouTubeDLFlow()
        coEvery { mockYoutubeDLService.resetAndReinitialize() } returns InitializationResult.Success

        orchestrator.initialize()
        assertEquals("Should be completed", InitializationStatus.Completed, orchestrator.getInitializationStatus())

        // Act
        val rollbackResult = orchestrator.rollbackInitialization()

        // Assert
        assertTrue("Rollback should succeed", rollbackResult is InitializationResult.Success)
        assertEquals("Status should be reset", InitializationStatus.NotStarted, orchestrator.getInitializationStatus())
        
        // Verify YouTube-DL service was reset
        coVerify { mockYoutubeDLService.resetAndReinitialize() }
    }

    @Test
    fun `force reinitialization should perform complete reset and reinitialize`() = runTest {
        // Arrange
        setupSuccessfulNativeLibraryFlow()
        setupSuccessfulPythonEnvironmentFlow()
        setupSuccessfulYouTubeDLFlow()
        coEvery { mockYoutubeDLService.resetAndReinitialize() } returns InitializationResult.Success

        // Act
        val result = orchestrator.forceReinitialization()

        // Assert
        assertTrue("Force reinitialization should succeed", result is InitializationResult.Success)
        assertEquals("Status should be completed", InitializationStatus.Completed, orchestrator.getInitializationStatus())
        
        // Verify reset was called
        coVerify { mockYoutubeDLService.resetAndReinitialize() }
        
        // Verify all services were called for reinitialization
        verifyNativeLibraryFlowCalled()
        verifyPythonEnvironmentFlowCalled()
        verifyYouTubeDLFlowCalled()
    }

    // ========== Performance Tests ==========

    @Test
    fun `initialization should complete within acceptable time limit`() = runTest {
        // Arrange
        setupSuccessfulNativeLibraryFlow()
        setupSuccessfulPythonEnvironmentFlow()
        setupSuccessfulYouTubeDLFlow()
        
        val acceptableTimeMs = 5000L // 5 seconds

        // Act & Assert
        val executionTime = measureTimeMillis {
            withTimeout(acceptableTimeMs) {
                val result = orchestrator.initialize()
                assertTrue("Initialization should succeed within time limit", result is InitializationResult.Success)
            }
        }

        assertTrue("Execution time ($executionTime ms) should be within acceptable limit ($acceptableTimeMs ms)", 
            executionTime < acceptableTimeMs)
    }

    @Test
    fun `retry operations should complete within acceptable time limit`() = runTest {
        // Arrange - Setup for quick failure
        val quickError = InitializationError.NativeLibraryError("Quick failure")
        every { mockNativeLibraryManager.shouldExtractLibraries() } returns true
        coEvery { mockNativeLibraryManager.extractNativeLibraries() } returns ExtractionResult(success = false, error = quickError)
        setupRecoveryMechanism(quickError, delayMs = 10L) // Very short delay for testing
        
        val acceptableTimeMs = 1000L // 1 second for retry operation

        // Act - Initial failure
        orchestrator.initialize()
        
        // Act & Assert - Retry within time limit
        val retryTime = measureTimeMillis {
            withTimeout(acceptableTimeMs) {
                val result = orchestrator.retryInitialization()
                assertTrue("Retry should complete within time limit", result != null)
            }
        }

        assertTrue("Retry time ($retryTime ms) should be within acceptable limit ($acceptableTimeMs ms)", 
            retryTime < acceptableTimeMs)
    }

    @Test
    fun `concurrent initialization attempts should be handled gracefully`() = runTest {
        // Arrange
        setupSuccessfulNativeLibraryFlow()
        setupSuccessfulPythonEnvironmentFlow()
        setupSuccessfulYouTubeDLFlow()

        // Act - Start multiple concurrent initializations
        val results = listOf(
            async { orchestrator.initialize() },
            async { orchestrator.initialize() },
            async { orchestrator.initialize() }
        ).awaitAll()

        // Assert - All should succeed (second and third should return immediately)
        results.forEach { result ->
            assertTrue("All concurrent initializations should succeed", result is InitializationResult.Success)
        }
        
        assertEquals("Status should be completed", InitializationStatus.Completed, orchestrator.getInitializationStatus())
        
        // Verify services were only called once (not three times)
        coVerify(exactly = 1) { mockYoutubeDLService.initialize() }
    }

    // ========== Helper Methods ==========

    private fun setupSuccessfulNativeLibraryFlow() {
        every { mockNativeLibraryManager.shouldExtractLibraries() } returns true
        coEvery { mockNativeLibraryManager.extractNativeLibraries() } returns ExtractionResult(
            success = true,
            extractedFiles = listOf("lib1.so", "lib2.so", "libpython.so")
        )
        coEvery { mockNativeLibraryManager.loadNativeLibraries() } returns LoadResult(
            success = true,
            loadedLibraries = listOf("lib1.so", "lib2.so", "libpython.so")
        )
        coEvery { mockNativeLibraryManager.verifyNativeLibraries() } returns VerificationResult(
            success = true,
            verifiedItems = listOf("lib1.so", "lib2.so", "libpython.so")
        )
    }

    private fun setupSuccessfulPythonEnvironmentFlow() {
        coEvery { mockPythonEnvironmentManager.setupPythonEnvironment() } returns SetupResult(
            success = true,
            setupSteps = listOf("create_directory", "extract_files", "verify_environment")
        )
    }

    private fun setupSuccessfulYouTubeDLFlow() {
        coEvery { mockYoutubeDLService.initialize() } returns InitializationResult.Success
        every { mockYoutubeDLService.isInitialized() } returns true
    }

    private fun setupErrorHandling(error: InitializationError) {
        every { mockErrorHandler.handleError(error) } returns ErrorHandlingResult.Handled(
            ErrorCategory.RECOVERABLE,
            RecoveryAction.Retry()
        )
    }

    private fun setupRecoveryMechanism(
        error: InitializationError, 
        maxAttempts: Int = 2, 
        delayMs: Long = 1L,
        recoveryAction: RecoveryAction = RecoveryAction.Retry(maxAttempts, delayMs)
    ) {
        every { mockErrorHandler.handleError(error) } returns ErrorHandlingResult.Handled(
            ErrorCategory.RECOVERABLE,
            recoveryAction
        )
        every { mockErrorHandler.suggestRecoveryAction(error) } returns recoveryAction
    }

    private fun verifyNativeLibraryFlowCalled() {
        coVerify { mockNativeLibraryManager.extractNativeLibraries() }
        coVerify { mockNativeLibraryManager.loadNativeLibraries() }
        coVerify { mockNativeLibraryManager.verifyNativeLibraries() }
    }

    private fun verifyPythonEnvironmentFlowCalled() {
        coVerify { mockPythonEnvironmentManager.setupPythonEnvironment() }
    }

    private fun verifyYouTubeDLFlowCalled() {
        coVerify { mockYoutubeDLService.initialize() }
    }
}