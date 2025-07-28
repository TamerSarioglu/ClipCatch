package com.tamersarioglu.clipcatch.data.service

import android.content.Context
import com.tamersarioglu.clipcatch.domain.model.ExtractionResult
import com.tamersarioglu.clipcatch.domain.model.InitializationError
import com.tamersarioglu.clipcatch.domain.model.InitializationResult
import com.tamersarioglu.clipcatch.domain.model.InitializationStatus
import com.tamersarioglu.clipcatch.domain.model.SetupResult
import com.tamersarioglu.clipcatch.util.Logger
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class YouTubeDLInitializationServiceImplTest {

    private lateinit var context: Context
    private lateinit var nativeLibraryManager: NativeLibraryManager
    private lateinit var pythonEnvironmentManager: PythonEnvironmentManager
    private lateinit var errorHandler: InitializationErrorHandler
    private lateinit var logger: Logger
    private lateinit var youtubeDLService: YouTubeDLInitializationServiceImpl
    
    private lateinit var mockYoutubeDL: YoutubeDL

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        nativeLibraryManager = mockk(relaxed = true)
        pythonEnvironmentManager = mockk(relaxed = true)
        errorHandler = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        mockYoutubeDL = mockk(relaxed = true)
        
        // Mock static YoutubeDL.getInstance()
        mockkStatic(YoutubeDL::class)
        every { YoutubeDL.getInstance() } returns mockYoutubeDL
        
        youtubeDLService = YouTubeDLInitializationServiceImpl(
            context = context,
            nativeLibraryManager = nativeLibraryManager,
            pythonEnvironmentManager = pythonEnvironmentManager,
            errorHandler = errorHandler,
            logger = logger
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `initialize should return success when all dependencies are ready and YouTube-DL initializes successfully`() = runTest {
        // Arrange
        val successfulExtractionResult = ExtractionResult(
            success = true,
            extractedFiles = listOf("libpython.so", "libssl.so"),
            extractionPath = "/data/data/app/files/native_libs"
        )
        val successfulSetupResult = SetupResult(
            success = true,
            setupSteps = listOf("Create Python directory", "Extract Python files"),
            setupPath = "/data/data/app/files/python"
        )
        
        coEvery { nativeLibraryManager.extractNativeLibraries() } returns successfulExtractionResult
        coEvery { pythonEnvironmentManager.setupPythonEnvironment() } returns successfulSetupResult
        every { pythonEnvironmentManager.getPythonDirectory() } returns File("/data/data/app/files/python")
        every { mockYoutubeDL.init(context) } returns Unit
        every { mockYoutubeDL.version(context) } returns "2023.12.30"
        every { errorHandler.handleError(any()) } returns ErrorHandlingResult.Handled(
            ErrorCategory.RECOVERABLE, 
            RecoveryAction.Retry()
        )

        // Act
        val result = youtubeDLService.initialize()

        // Assert
        assertTrue("Expected InitializationResult.Success but got $result", result is InitializationResult.Success)
        assertTrue("Expected service to be initialized", youtubeDLService.isInitialized())
        assertEquals("Expected status to be Completed", InitializationStatus.Completed, youtubeDLService.getInitializationStatus())
        assertEquals("Expected status message to be 'YouTube-DL Ready'", "YouTube-DL Ready", youtubeDLService.getStatusMessage())
        
        coVerify { nativeLibraryManager.extractNativeLibraries() }
        coVerify { pythonEnvironmentManager.setupPythonEnvironment() }
        verify { mockYoutubeDL.init(context) }
        verify { mockYoutubeDL.version(context) }
    }

    @Test
    fun `initialize should return failure when native library extraction fails`() = runTest {
        // Arrange
        val failedExtractionResult = ExtractionResult(
            success = false,
            failedFiles = listOf("libpython.so"),
            error = InitializationError.NativeLibraryError("Extraction failed")
        )
        
        coEvery { nativeLibraryManager.extractNativeLibraries() } returns failedExtractionResult
        every { errorHandler.handleError(any()) } returns ErrorHandlingResult.Handled(
            ErrorCategory.RECOVERABLE, 
            RecoveryAction.ReExtractFiles("lib/*.so")
        )

        // Act
        val result = youtubeDLService.initialize()

        // Assert
        assertTrue(result is InitializationResult.Failure)
        assertFalse(youtubeDLService.isInitialized())
        assertTrue(youtubeDLService.getInitializationStatus() is InitializationStatus.Failed)
        assertTrue(youtubeDLService.getStatusMessage().contains("Failed"))
        
        coVerify { nativeLibraryManager.extractNativeLibraries() }
        coVerify(exactly = 0) { pythonEnvironmentManager.setupPythonEnvironment() }
        verify(exactly = 0) { mockYoutubeDL.init(any()) }
    }

    @Test
    fun `initialize should return failure when Python environment setup fails`() = runTest {
        // Arrange
        val successfulExtractionResult = ExtractionResult(success = true)
        val failedSetupResult = SetupResult(
            success = false,
            failedSteps = listOf("Extract Python files"),
            error = InitializationError.PythonEnvironmentError("Python setup failed")
        )
        
        coEvery { nativeLibraryManager.extractNativeLibraries() } returns successfulExtractionResult
        coEvery { pythonEnvironmentManager.setupPythonEnvironment() } returns failedSetupResult
        every { errorHandler.handleError(any()) } returns ErrorHandlingResult.Handled(
            ErrorCategory.RECOVERABLE, 
            RecoveryAction.RecreateDirectories(listOf("/data/data/app/files/python"))
        )

        // Act
        val result = youtubeDLService.initialize()

        // Assert
        assertTrue(result is InitializationResult.Failure)
        assertFalse(youtubeDLService.isInitialized())
        assertTrue(youtubeDLService.getInitializationStatus() is InitializationStatus.Failed)
        
        coVerify { nativeLibraryManager.extractNativeLibraries() }
        coVerify { pythonEnvironmentManager.setupPythonEnvironment() }
        verify(exactly = 0) { mockYoutubeDL.init(any()) }
    }

    @Test
    fun `initialize should return failure when YouTube-DL init throws YoutubeDLException`() = runTest {
        // Arrange
        val successfulExtractionResult = ExtractionResult(success = true)
        val successfulSetupResult = SetupResult(success = true)
        val youtubeDLException = YoutubeDLException("YouTube-DL initialization failed")
        
        coEvery { nativeLibraryManager.extractNativeLibraries() } returns successfulExtractionResult
        coEvery { pythonEnvironmentManager.setupPythonEnvironment() } returns successfulSetupResult
        every { mockYoutubeDL.init(context) } throws youtubeDLException
        every { errorHandler.handleError(any()) } returns ErrorHandlingResult.Handled(
            ErrorCategory.CRITICAL, 
            RecoveryAction.NoRecovery("YouTube-DL library incompatible")
        )

        // Act
        val result = youtubeDLService.initialize()

        // Assert
        assertTrue(result is InitializationResult.Failure)
        assertFalse(youtubeDLService.isInitialized())
        assertTrue(youtubeDLService.getInitializationStatus() is InitializationStatus.Failed)
        
        val failureResult = result as InitializationResult.Failure
        assertTrue(failureResult.error is InitializationError.YouTubeDLError)
        assertEquals(youtubeDLException, failureResult.error.cause)
    }

    @Test
    fun `initialize should handle standard init failure gracefully`() = runTest {
        // Arrange
        val successfulExtractionResult = ExtractionResult(success = true)
        val successfulSetupResult = SetupResult(success = true)
        
        coEvery { nativeLibraryManager.extractNativeLibraries() } returns successfulExtractionResult
        coEvery { pythonEnvironmentManager.setupPythonEnvironment() } returns successfulSetupResult
        every { pythonEnvironmentManager.getPythonDirectory() } returns File("/data/data/app/files/python")
        every { mockYoutubeDL.init(context) } throws RuntimeException("Standard init failed")
        every { errorHandler.handleError(any()) } returns ErrorHandlingResult.Handled(
            ErrorCategory.RECOVERABLE, 
            RecoveryAction.UseAlternativeMethod("init_ytdlp")
        )

        // Act
        val result = youtubeDLService.initialize()

        // Assert
        assertTrue("Expected InitializationResult.Failure but got $result", result is InitializationResult.Failure)
        assertFalse("Expected service to not be initialized", youtubeDLService.isInitialized())
        
        verify { mockYoutubeDL.init(context) }
        verify { errorHandler.handleError(any()) }
    }

    @Test
    fun `initialize should handle UnsatisfiedLinkError for missing native libraries`() = runTest {
        // Arrange
        val successfulExtractionResult = ExtractionResult(success = true)
        val successfulSetupResult = SetupResult(success = true)
        val linkError = UnsatisfiedLinkError("libpython.so not found")
        
        coEvery { nativeLibraryManager.extractNativeLibraries() } returns successfulExtractionResult
        coEvery { pythonEnvironmentManager.setupPythonEnvironment() } returns successfulSetupResult
        every { mockYoutubeDL.init(context) } throws linkError
        every { errorHandler.handleError(any()) } returns ErrorHandlingResult.Handled(
            ErrorCategory.RECOVERABLE, 
            RecoveryAction.ReExtractFiles("lib/*.so")
        )

        // Act
        val result = youtubeDLService.initialize()

        // Assert
        assertTrue(result is InitializationResult.Failure)
        assertFalse(youtubeDLService.isInitialized())
        
        val failureResult = result as InitializationResult.Failure
        assertTrue(failureResult.error is InitializationError.NativeLibraryError)
        assertTrue(failureResult.error.message.contains("Python library loading failed"))
        assertEquals(linkError, failureResult.error.cause)
    }

    @Test
    fun `ensureInitialized should return true when already initialized`() = runTest {
        // Arrange - simulate already initialized state
        val successfulExtractionResult = ExtractionResult(success = true)
        val successfulSetupResult = SetupResult(success = true)
        
        coEvery { nativeLibraryManager.extractNativeLibraries() } returns successfulExtractionResult
        coEvery { pythonEnvironmentManager.setupPythonEnvironment() } returns successfulSetupResult
        every { mockYoutubeDL.init(context) } returns Unit
        every { mockYoutubeDL.version(context) } returns "2023.12.30"
        
        // Initialize first
        youtubeDLService.initialize()

        // Act
        val result = youtubeDLService.ensureInitialized()

        // Assert
        assertTrue(result)
        assertTrue(youtubeDLService.isInitialized())
        
        // Verify initialization was only called once
        coVerify(exactly = 1) { nativeLibraryManager.extractNativeLibraries() }
    }

    @Test
    fun `ensureInitialized should initialize when not attempted before`() = runTest {
        // Arrange
        val successfulExtractionResult = ExtractionResult(success = true)
        val successfulSetupResult = SetupResult(success = true)
        
        coEvery { nativeLibraryManager.extractNativeLibraries() } returns successfulExtractionResult
        coEvery { pythonEnvironmentManager.setupPythonEnvironment() } returns successfulSetupResult
        every { mockYoutubeDL.init(context) } returns Unit
        every { mockYoutubeDL.version(context) } returns "2023.12.30"

        // Act
        val result = youtubeDLService.ensureInitialized()

        // Assert
        assertTrue(result)
        assertTrue(youtubeDLService.isInitialized())
        
        coVerify { nativeLibraryManager.extractNativeLibraries() }
        coVerify { pythonEnvironmentManager.setupPythonEnvironment() }
    }

    @Test
    fun `retryInitialization should reset state and try again`() = runTest {
        // Arrange - simulate failed initialization first
        val failedExtractionResult = ExtractionResult(
            success = false,
            error = InitializationError.NativeLibraryError("First attempt failed")
        )
        val successfulExtractionResult = ExtractionResult(success = true)
        val successfulSetupResult = SetupResult(success = true)
        
        coEvery { nativeLibraryManager.extractNativeLibraries() } returnsMany listOf(
            failedExtractionResult,
            successfulExtractionResult
        )
        coEvery { pythonEnvironmentManager.setupPythonEnvironment() } returns successfulSetupResult
        every { mockYoutubeDL.init(context) } returns Unit
        every { mockYoutubeDL.version(context) } returns "2023.12.30"
        every { errorHandler.handleError(any()) } returns ErrorHandlingResult.Handled(
            ErrorCategory.RECOVERABLE, 
            RecoveryAction.Retry()
        )

        // First attempt should fail
        val firstResult = youtubeDLService.initialize()
        assertTrue(firstResult is InitializationResult.Failure)

        // Act - retry
        val retryResult = youtubeDLService.retryInitialization()

        // Assert
        assertTrue(retryResult is InitializationResult.Success)
        assertTrue(youtubeDLService.isInitialized())
        
        coVerify(exactly = 2) { nativeLibraryManager.extractNativeLibraries() }
    }

    @Test
    fun `retryInitialization should fail after maximum attempts`() = runTest {
        // Arrange - simulate repeated failures
        val failedExtractionResult = ExtractionResult(
            success = false,
            error = InitializationError.NativeLibraryError("Persistent failure")
        )
        
        coEvery { nativeLibraryManager.extractNativeLibraries() } returns failedExtractionResult
        every { errorHandler.handleError(any()) } returns ErrorHandlingResult.Handled(
            ErrorCategory.RECOVERABLE, 
            RecoveryAction.Retry()
        )

        // Act - retry multiple times
        var lastResult: InitializationResult = InitializationResult.Success
        for (i in 1..4) { // One more than MAX_RETRY_ATTEMPTS
            lastResult = youtubeDLService.retryInitialization()
        }

        // Assert
        assertTrue(lastResult is InitializationResult.Failure)
        assertFalse(youtubeDLService.isInitialized())
        
        val failureResult = lastResult as InitializationResult.Failure
        assertTrue(failureResult.error.message.contains("Maximum retry attempts"))
    }

    @Test
    fun `resetAndReinitialize should clear all state and start fresh`() = runTest {
        // Arrange - simulate failed state
        val failedExtractionResult = ExtractionResult(
            success = false,
            error = InitializationError.NativeLibraryError("Initial failure")
        )
        val successfulExtractionResult = ExtractionResult(success = true)
        val successfulSetupResult = SetupResult(success = true)
        
        coEvery { nativeLibraryManager.extractNativeLibraries() } returnsMany listOf(
            failedExtractionResult,
            successfulExtractionResult
        )
        coEvery { pythonEnvironmentManager.setupPythonEnvironment() } returns successfulSetupResult
        every { mockYoutubeDL.init(context) } returns Unit
        every { mockYoutubeDL.version(context) } returns "2023.12.30"
        every { errorHandler.handleError(any()) } returns ErrorHandlingResult.Handled(
            ErrorCategory.RECOVERABLE, 
            RecoveryAction.Retry()
        )

        // First attempt should fail
        val firstResult = youtubeDLService.initialize()
        assertTrue(firstResult is InitializationResult.Failure)
        assertTrue(youtubeDLService.getInitializationStatus() is InitializationStatus.Failed)

        // Act - reset and reinitialize
        val resetResult = youtubeDLService.resetAndReinitialize()

        // Assert
        assertTrue(resetResult is InitializationResult.Success)
        assertTrue(youtubeDLService.isInitialized())
        assertEquals(InitializationStatus.Completed, youtubeDLService.getInitializationStatus())
        
        coVerify(exactly = 2) { nativeLibraryManager.extractNativeLibraries() }
    }

    @Test
    fun `verifyInitialization should return true when version is available`() = runTest {
        // Arrange
        every { mockYoutubeDL.version(context) } returns "2023.12.30"
        
        // Simulate initialized state
        val successfulExtractionResult = ExtractionResult(success = true)
        val successfulSetupResult = SetupResult(success = true)
        coEvery { nativeLibraryManager.extractNativeLibraries() } returns successfulExtractionResult
        coEvery { pythonEnvironmentManager.setupPythonEnvironment() } returns successfulSetupResult
        every { mockYoutubeDL.init(context) } returns Unit
        youtubeDLService.initialize()

        // Act
        val result = youtubeDLService.verifyInitialization()

        // Assert
        assertTrue(result)
        verify { mockYoutubeDL.version(context) }
    }

    @Test
    fun `verifyInitialization should return false when version throws exception`() = runTest {
        // Arrange
        every { mockYoutubeDL.version(context) } throws RuntimeException("Version check failed")
        
        // Simulate initialized state
        val successfulExtractionResult = ExtractionResult(success = true)
        val successfulSetupResult = SetupResult(success = true)
        coEvery { nativeLibraryManager.extractNativeLibraries() } returns successfulExtractionResult
        coEvery { pythonEnvironmentManager.setupPythonEnvironment() } returns successfulSetupResult
        every { mockYoutubeDL.init(context) } returns Unit
        youtubeDLService.initialize()

        // Act
        val result = youtubeDLService.verifyInitialization()

        // Assert
        assertFalse(result)
    }

    @Test
    fun `getVersion should return version when initialized`() = runTest {
        // Arrange
        val expectedVersion = "2023.12.30"
        every { mockYoutubeDL.version(context) } returns expectedVersion
        
        // Simulate initialized state
        val successfulExtractionResult = ExtractionResult(success = true)
        val successfulSetupResult = SetupResult(success = true)
        coEvery { nativeLibraryManager.extractNativeLibraries() } returns successfulExtractionResult
        coEvery { pythonEnvironmentManager.setupPythonEnvironment() } returns successfulSetupResult
        every { mockYoutubeDL.init(context) } returns Unit
        youtubeDLService.initialize()

        // Act
        val version = youtubeDLService.getVersion()

        // Assert
        assertEquals(expectedVersion, version)
    }

    @Test
    fun `getVersion should return null when not initialized`() = runTest {
        // Act
        val version = youtubeDLService.getVersion()

        // Assert
        assertNull(version)
        verify(exactly = 0) { mockYoutubeDL.version(any()) }
    }

    @Test
    fun `getVersion should return null when version call throws exception`() = runTest {
        // Arrange
        every { mockYoutubeDL.version(context) } throws RuntimeException("Version unavailable")
        
        // Simulate initialized state
        val successfulExtractionResult = ExtractionResult(success = true)
        val successfulSetupResult = SetupResult(success = true)
        coEvery { nativeLibraryManager.extractNativeLibraries() } returns successfulExtractionResult
        coEvery { pythonEnvironmentManager.setupPythonEnvironment() } returns successfulSetupResult
        every { mockYoutubeDL.init(context) } returns Unit
        youtubeDLService.initialize()

        // Act
        val version = youtubeDLService.getVersion()

        // Assert
        assertNull(version)
    }

    @Test
    fun `initialize should return success when called multiple times after successful initialization`() = runTest {
        // Arrange
        val successfulExtractionResult = ExtractionResult(success = true)
        val successfulSetupResult = SetupResult(success = true)
        
        coEvery { nativeLibraryManager.extractNativeLibraries() } returns successfulExtractionResult
        coEvery { pythonEnvironmentManager.setupPythonEnvironment() } returns successfulSetupResult
        every { mockYoutubeDL.init(context) } returns Unit
        every { mockYoutubeDL.version(context) } returns "2023.12.30"

        // Act - initialize twice
        val firstResult = youtubeDLService.initialize()
        val secondResult = youtubeDLService.initialize()

        // Assert
        assertTrue("First initialization should succeed", firstResult is InitializationResult.Success)
        assertTrue("Second initialization should also succeed", secondResult is InitializationResult.Success)
        assertTrue("Service should be initialized", youtubeDLService.isInitialized())
    }

    @Test
    fun `error handler should be called for all initialization failures`() = runTest {
        // Arrange
        val nativeLibError = InitializationError.NativeLibraryError("Native lib failed")
        val failedExtractionResult = ExtractionResult(
            success = false,
            error = nativeLibError
        )
        
        coEvery { nativeLibraryManager.extractNativeLibraries() } returns failedExtractionResult
        
        val errorSlot = slot<InitializationError>()
        every { errorHandler.handleError(capture(errorSlot)) } returns ErrorHandlingResult.Handled(
            ErrorCategory.RECOVERABLE, 
            RecoveryAction.ReExtractFiles("lib/*.so")
        )

        // Act
        val result = youtubeDLService.initialize()

        // Assert
        assertTrue(result is InitializationResult.Failure)
        verify { errorHandler.handleError(any()) }
        
        // Verify the captured error is of the correct type
        assertTrue(errorSlot.captured is InitializationError.NativeLibraryError)
        assertTrue(errorSlot.captured.message.contains("Failed to extract native libraries"))
    }
}