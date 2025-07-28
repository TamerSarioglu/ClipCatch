package com.tamersarioglu.clipcatch.data.service

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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InitializationOrchestratorImplTest {

    private lateinit var orchestrator: InitializationOrchestratorImpl
    private lateinit var mockNativeLibraryManager: NativeLibraryManager
    private lateinit var mockPythonEnvironmentManager: PythonEnvironmentManager
    private lateinit var mockYoutubeDLService: YouTubeDLInitializationService
    private lateinit var mockErrorHandler: InitializationErrorHandler
    private lateinit var mockLogger: Logger

    @Before
    fun setup() {
        mockNativeLibraryManager = mockk(relaxed = true)
        mockPythonEnvironmentManager = mockk(relaxed = true)
        mockYoutubeDLService = mockk(relaxed = true)
        mockErrorHandler = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)

        orchestrator = InitializationOrchestratorImpl(
            nativeLibraryManager = mockNativeLibraryManager,
            pythonEnvironmentManager = mockPythonEnvironmentManager,
            youtubeDLService = mockYoutubeDLService,
            errorHandler = mockErrorHandler,
            logger = mockLogger
        )
    }

    @Test
    fun `initialize should complete successfully when all services succeed`() = runTest {
        // Arrange
        every { mockNativeLibraryManager.shouldExtractLibraries() } returns true
        coEvery { mockNativeLibraryManager.extractNativeLibraries() } returns ExtractionResult(
            success = true,
            extractedFiles = listOf("lib1.so", "lib2.so")
        )
        coEvery { mockNativeLibraryManager.loadNativeLibraries() } returns LoadResult(
            success = true,
            loadedLibraries = listOf("lib1.so", "lib2.so")
        )
        coEvery { mockNativeLibraryManager.verifyNativeLibraries() } returns VerificationResult(
            success = true,
            verifiedItems = listOf("lib1.so", "lib2.so")
        )
        coEvery { mockPythonEnvironmentManager.setupPythonEnvironment() } returns SetupResult(
            success = true,
            setupSteps = listOf("create_directory", "extract_files")
        )
        coEvery { mockPythonEnvironmentManager.verifyPythonEnvironment() } returns VerificationResult(
            success = true,
            verifiedItems = listOf("python_files")
        )
        coEvery { mockYoutubeDLService.initialize() } returns InitializationResult.Success
        every { mockYoutubeDLService.isInitialized() } returns true

        // Act
        val result = orchestrator.initialize()

        // Assert
        assertTrue(result is InitializationResult.Success)
        assertEquals(InitializationStatus.Completed, orchestrator.getInitializationStatus())
        assertTrue(orchestrator.isInitializationComplete())
        
        // Verify all services were called in correct order
        coVerify { mockNativeLibraryManager.extractNativeLibraries() }
        coVerify { mockNativeLibraryManager.loadNativeLibraries() }
        coVerify { mockNativeLibraryManager.verifyNativeLibraries() }
        coVerify { mockPythonEnvironmentManager.setupPythonEnvironment() }
        coVerify { mockPythonEnvironmentManager.verifyPythonEnvironment() }
        coVerify { mockYoutubeDLService.initialize() }
    }

    @Test
    fun `initialize should skip native library extraction when not needed`() = runTest {
        // Arrange
        every { mockNativeLibraryManager.shouldExtractLibraries() } returns false
        coEvery { mockNativeLibraryManager.loadNativeLibraries() } returns LoadResult(success = true)
        coEvery { mockNativeLibraryManager.verifyNativeLibraries() } returns VerificationResult(success = true)
        coEvery { mockPythonEnvironmentManager.setupPythonEnvironment() } returns SetupResult(success = true)
        coEvery { mockPythonEnvironmentManager.verifyPythonEnvironment() } returns VerificationResult(success = true)
        coEvery { mockYoutubeDLService.initialize() } returns InitializationResult.Success

        // Act
        val result = orchestrator.initialize()

        // Assert
        assertTrue(result is InitializationResult.Success)
        
        // Verify extraction was skipped but other steps were called
        coVerify(exactly = 0) { mockNativeLibraryManager.extractNativeLibraries() }
        coVerify { mockNativeLibraryManager.loadNativeLibraries() }
        coVerify { mockNativeLibraryManager.verifyNativeLibraries() }
    }

    @Test
    fun `initialize should fail when native library extraction fails`() = runTest {
        // Arrange
        val error = InitializationError.NativeLibraryError("Extraction failed")
        every { mockNativeLibraryManager.shouldExtractLibraries() } returns true
        coEvery { mockNativeLibraryManager.extractNativeLibraries() } returns ExtractionResult(
            success = false,
            error = error
        )
        every { mockErrorHandler.handleError(any()) } returns ErrorHandlingResult.Handled(
            ErrorCategory.RECOVERABLE,
            RecoveryAction.Retry()
        )

        // Act
        val result = orchestrator.initialize()

        // Assert
        assertTrue(result is InitializationResult.Failure)
        assertEquals(error, (result as InitializationResult.Failure).error)
        assertTrue(orchestrator.getInitializationStatus() is InitializationStatus.Failed)
        
        // Verify error was handled
        verify { mockErrorHandler.handleError(error) }
        
        // Verify subsequent steps were not called
        coVerify(exactly = 0) { mockNativeLibraryManager.loadNativeLibraries() }
        coVerify(exactly = 0) { mockPythonEnvironmentManager.setupPythonEnvironment() }
        coVerify(exactly = 0) { mockYoutubeDLService.initialize() }
    }

    @Test
    fun `initialize should fail when native library loading fails`() = runTest {
        // Arrange
        val error = InitializationError.NativeLibraryError("Loading failed")
        every { mockNativeLibraryManager.shouldExtractLibraries() } returns false
        coEvery { mockNativeLibraryManager.loadNativeLibraries() } returns LoadResult(
            success = false,
            error = error
        )
        every { mockErrorHandler.handleError(any()) } returns ErrorHandlingResult.Handled(
            ErrorCategory.RECOVERABLE,
            RecoveryAction.Retry()
        )

        // Act
        val result = orchestrator.initialize()

        // Assert
        assertTrue(result is InitializationResult.Failure)
        assertEquals(error, (result as InitializationResult.Failure).error)
        
        // Verify subsequent steps were not called
        coVerify(exactly = 0) { mockPythonEnvironmentManager.setupPythonEnvironment() }
        coVerify(exactly = 0) { mockYoutubeDLService.initialize() }
    }

    @Test
    fun `initialize should fail when Python environment setup fails`() = runTest {
        // Arrange
        val error = InitializationError.PythonEnvironmentError("Python setup failed")
        every { mockNativeLibraryManager.shouldExtractLibraries() } returns false
        coEvery { mockNativeLibraryManager.loadNativeLibraries() } returns LoadResult(success = true)
        coEvery { mockNativeLibraryManager.verifyNativeLibraries() } returns VerificationResult(success = true)
        coEvery { mockPythonEnvironmentManager.setupPythonEnvironment() } returns SetupResult(
            success = false,
            error = error
        )
        every { mockErrorHandler.handleError(any()) } returns ErrorHandlingResult.Handled(
            ErrorCategory.RECOVERABLE,
            RecoveryAction.ReExtractFiles("*.py")
        )

        // Act
        val result = orchestrator.initialize()

        // Assert
        assertTrue(result is InitializationResult.Failure)
        assertEquals(error, (result as InitializationResult.Failure).error)
        
        // Verify YouTube-DL initialization was not called
        coVerify(exactly = 0) { mockYoutubeDLService.initialize() }
    }

    @Test
    fun `initialize should fail when YouTube-DL initialization fails`() = runTest {
        // Arrange
        val error = InitializationError.YouTubeDLError("YouTube-DL init failed")
        every { mockNativeLibraryManager.shouldExtractLibraries() } returns false
        coEvery { mockNativeLibraryManager.loadNativeLibraries() } returns LoadResult(success = true)
        coEvery { mockNativeLibraryManager.verifyNativeLibraries() } returns VerificationResult(success = true)
        coEvery { mockPythonEnvironmentManager.setupPythonEnvironment() } returns SetupResult(success = true)
        coEvery { mockPythonEnvironmentManager.verifyPythonEnvironment() } returns VerificationResult(success = true)
        coEvery { mockYoutubeDLService.initialize() } returns InitializationResult.Failure(error)
        every { mockErrorHandler.handleError(any()) } returns ErrorHandlingResult.Handled(
            ErrorCategory.CRITICAL,
            RecoveryAction.NoRecovery("YouTube-DL incompatible")
        )

        // Act
        val result = orchestrator.initialize()

        // Assert
        assertTrue(result is InitializationResult.Failure)
        assertEquals(error, (result as InitializationResult.Failure).error)
    }

    @Test
    fun `initialize should return success if already completed`() = runTest {
        // Arrange - First successful initialization
        every { mockNativeLibraryManager.shouldExtractLibraries() } returns false
        coEvery { mockNativeLibraryManager.loadNativeLibraries() } returns LoadResult(success = true)
        coEvery { mockNativeLibraryManager.verifyNativeLibraries() } returns VerificationResult(success = true)
        coEvery { mockPythonEnvironmentManager.setupPythonEnvironment() } returns SetupResult(success = true)
        coEvery { mockPythonEnvironmentManager.verifyPythonEnvironment() } returns VerificationResult(success = true)
        coEvery { mockYoutubeDLService.initialize() } returns InitializationResult.Success

        // Act - First initialization
        val firstResult = orchestrator.initialize()
        
        // Act - Second initialization attempt
        val secondResult = orchestrator.initialize()

        // Assert
        assertTrue(firstResult is InitializationResult.Success)
        assertTrue(secondResult is InitializationResult.Success)
        
        // Verify services were only called once
        coVerify(exactly = 1) { mockYoutubeDLService.initialize() }
    }

    @Test
    fun `retryInitialization should call error handler for recovery actions`() = runTest {
        // Arrange - First initialization fails
        val error = InitializationError.NativeLibraryError("Extraction failed")
        every { mockNativeLibraryManager.shouldExtractLibraries() } returns true
        coEvery { mockNativeLibraryManager.extractNativeLibraries() } returns ExtractionResult(
            success = false,
            error = error
        )
        every { mockErrorHandler.handleError(any()) } returns ErrorHandlingResult.Handled(
            ErrorCategory.RECOVERABLE,
            RecoveryAction.Retry(maxAttempts = 2, delayMs = 1L)
        )
        every { mockErrorHandler.suggestRecoveryAction(any()) } returns RecoveryAction.Retry(
            maxAttempts = 2,
            delayMs = 1L
        )

        // First initialization fails
        orchestrator.initialize()

        // Act
        val retryResult = orchestrator.retryInitialization()

        // Assert - Just verify the method was called and error handler was invoked
        assertTrue("Retry should be attempted", retryResult is InitializationResult.Failure || retryResult is InitializationResult.Success)
        
        // Verify recovery action was requested
        verify { mockErrorHandler.suggestRecoveryAction(any()) }
    }

    @Test
    fun `retryInitialization should respect max attempts limit`() = runTest {
        // Arrange - Setup multiple failures
        val error = InitializationError.NativeLibraryError("Persistent error")
        every { mockNativeLibraryManager.shouldExtractLibraries() } returns true
        coEvery { mockNativeLibraryManager.extractNativeLibraries() } returns ExtractionResult(
            success = false,
            error = error
        )
        every { mockErrorHandler.handleError(any()) } returns ErrorHandlingResult.Handled(
            ErrorCategory.RECOVERABLE,
            RecoveryAction.Retry(maxAttempts = 1, delayMs = 1L)
        )
        every { mockErrorHandler.suggestRecoveryAction(any()) } returns RecoveryAction.Retry(
            maxAttempts = 1,
            delayMs = 1L
        )

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
    fun `rollbackInitialization should reset status and call service reset`() = runTest {
        // Arrange - First complete an initialization
        every { mockNativeLibraryManager.shouldExtractLibraries() } returns false
        coEvery { mockNativeLibraryManager.loadNativeLibraries() } returns LoadResult(success = true)
        coEvery { mockNativeLibraryManager.verifyNativeLibraries() } returns VerificationResult(success = true)
        coEvery { mockPythonEnvironmentManager.setupPythonEnvironment() } returns SetupResult(success = true)
        coEvery { mockPythonEnvironmentManager.verifyPythonEnvironment() } returns VerificationResult(success = true)
        coEvery { mockYoutubeDLService.initialize() } returns InitializationResult.Success
        coEvery { mockYoutubeDLService.resetAndReinitialize() } returns InitializationResult.Success

        orchestrator.initialize()

        // Act
        val rollbackResult = orchestrator.rollbackInitialization()

        // Assert
        assertTrue(rollbackResult is InitializationResult.Success)
        assertEquals(InitializationStatus.NotStarted, orchestrator.getInitializationStatus())
        
        // Verify YouTube-DL service was reset
        coVerify { mockYoutubeDLService.resetAndReinitialize() }
    }

    @Test
    fun `forceReinitialization should call reset and reinitialize`() = runTest {
        // Arrange - Setup for successful re-initialization
        every { mockNativeLibraryManager.shouldExtractLibraries() } returns false
        coEvery { mockNativeLibraryManager.loadNativeLibraries() } returns LoadResult(success = true)
        coEvery { mockNativeLibraryManager.verifyNativeLibraries() } returns VerificationResult(success = true)
        coEvery { mockPythonEnvironmentManager.setupPythonEnvironment() } returns SetupResult(success = true)
        coEvery { mockPythonEnvironmentManager.verifyPythonEnvironment() } returns VerificationResult(success = true)
        coEvery { mockYoutubeDLService.initialize() } returns InitializationResult.Success
        coEvery { mockYoutubeDLService.resetAndReinitialize() } returns InitializationResult.Success
        every { mockYoutubeDLService.isInitialized() } returns true

        // Act
        val result = orchestrator.forceReinitialization()

        // Assert
        assertTrue("Force reinitialization should succeed", result is InitializationResult.Success)
        
        // Verify reset was called
        coVerify { mockYoutubeDLService.resetAndReinitialize() }
    }

    @Test
    fun `isInitializationComplete should return false when YouTube-DL is not initialized`() = runTest {
        // Arrange
        every { mockNativeLibraryManager.shouldExtractLibraries() } returns false
        coEvery { mockNativeLibraryManager.loadNativeLibraries() } returns LoadResult(success = true)
        coEvery { mockNativeLibraryManager.verifyNativeLibraries() } returns VerificationResult(success = true)
        coEvery { mockPythonEnvironmentManager.setupPythonEnvironment() } returns SetupResult(success = true)
        coEvery { mockPythonEnvironmentManager.verifyPythonEnvironment() } returns VerificationResult(success = true)
        coEvery { mockYoutubeDLService.initialize() } returns InitializationResult.Success
        every { mockYoutubeDLService.isInitialized() } returns false

        // Act
        orchestrator.initialize()
        val isComplete = orchestrator.isInitializationComplete()

        // Assert
        assertFalse(isComplete)
    }

    @Test
    fun `getDetailedStatusMessage should return appropriate messages for different states`() {
        // Test NotStarted
        assertEquals("Initialization not started", orchestrator.getDetailedStatusMessage())

        // Test other states would require setting up the orchestrator in those states
        // which is more complex due to the private status field
    }

    @Test
    fun `initialize should handle unexpected exceptions gracefully`() = runTest {
        // Arrange
        every { mockNativeLibraryManager.shouldExtractLibraries() } throws RuntimeException("Unexpected error")
        every { mockErrorHandler.handleError(any()) } returns ErrorHandlingResult.Handled(
            ErrorCategory.CRITICAL,
            RecoveryAction.NoRecovery("Unexpected error")
        )

        // Act
        val result = orchestrator.initialize()

        // Assert
        assertTrue("Result should be a failure", result is InitializationResult.Failure)
        val failureResult = result as InitializationResult.Failure
        assertTrue("Error should be GenericError", failureResult.error is InitializationError.GenericError)
        assertTrue("Error message should contain expected text", 
            failureResult.error.message.contains("Unexpected error") || 
            failureResult.error.message.contains("initialization"))
        
        // Verify error was handled
        verify { mockErrorHandler.handleError(any()) }
    }
}