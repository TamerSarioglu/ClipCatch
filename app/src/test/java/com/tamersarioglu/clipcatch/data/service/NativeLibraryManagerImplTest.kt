package com.tamersarioglu.clipcatch.data.service

import android.content.Context
import com.tamersarioglu.clipcatch.domain.model.ExtractionResult
import com.tamersarioglu.clipcatch.domain.model.InitializationError
import com.tamersarioglu.clipcatch.domain.model.LoadResult
import com.tamersarioglu.clipcatch.domain.model.VerificationResult
import com.tamersarioglu.clipcatch.util.FileExtractionUtils
import com.tamersarioglu.clipcatch.util.Logger
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Unit tests for NativeLibraryManagerImpl.
 * Tests the core functionality of native library management including extraction, loading, and verification.
 */
class NativeLibraryManagerImplTest {

    private lateinit var context: Context
    private lateinit var fileExtractionUtils: FileExtractionUtils
    private lateinit var errorHandler: InitializationErrorHandler
    private lateinit var logger: Logger
    private lateinit var nativeLibraryManager: NativeLibraryManagerImpl

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        fileExtractionUtils = mockk(relaxed = true)
        errorHandler = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        
        val mockFilesDir = mockk<File>(relaxed = true)
        every { context.filesDir } returns mockFilesDir
        every { mockFilesDir.absolutePath } returns "/data/data/com.test/files"
        
        every { errorHandler.handleError(any()) } returns ErrorHandlingResult.Handled(
            ErrorCategory.RECOVERABLE, 
            RecoveryAction.Retry()
        )
        
        nativeLibraryManager = NativeLibraryManagerImpl(context, fileExtractionUtils, errorHandler, logger)
    }

    @Test
    fun `extractNativeLibraries should successfully extract libraries`() = runTest {
        // Given
        val extractedFiles = listOf("libpython.zip.so", "libffmpeg.zip.so")
        val successResult = com.tamersarioglu.clipcatch.util.ExtractionResult.Success(
            extractedCount = 2,
            extractedFiles = extractedFiles
        )
        
        val zipExtractedFiles = listOf("libpython.so", "libffmpeg.so")
        val zipSuccessResult = com.tamersarioglu.clipcatch.util.ExtractionResult.Success(
            extractedCount = 2,
            extractedFiles = zipExtractedFiles
        )
        
        coEvery { 
            fileExtractionUtils.extractFromAPK(
                sourcePattern = "lib/",
                targetDirectory = any(),
                filter = any()
            )
        } returns successResult
        
        coEvery {
            fileExtractionUtils.extractZipArchive(any(), any())
        } returns zipSuccessResult

        // When
        val result = nativeLibraryManager.extractNativeLibraries()

        // Then
        assertTrue(result.success)
        // Should contain both the original .zip.so files and the extracted .so files
        assertTrue(result.extractedFiles.containsAll(extractedFiles))
        assertTrue(result.extractedFiles.containsAll(zipExtractedFiles))
        assertTrue(result.extractionPath?.contains("native_libs") == true)
        assertNull(result.error)
        
        verify { logger.i(any(), "Successfully extracted 2 native libraries") }
    }

    @Test
    fun `extractNativeLibraries should handle partial success`() = runTest {
        // Given
        val extractedFiles = listOf("libpython.zip.so")
        val errors = listOf("Failed to extract libssl.zip.so")
        val partialResult = com.tamersarioglu.clipcatch.util.ExtractionResult.PartialSuccess(
            extractedCount = 1,
            failedCount = 1,
            extractedFiles = extractedFiles,
            errors = errors
        )
        
        coEvery { 
            fileExtractionUtils.extractFromAPK(any(), any(), any())
        } returns partialResult

        // When
        val result = nativeLibraryManager.extractNativeLibraries()

        // Then
        assertTrue(result.success) // Should be true since extractedCount > 0
        assertEquals(extractedFiles, result.extractedFiles)
        assertEquals(errors, result.failedFiles)
        assertTrue(result.extractionPath?.contains("native_libs") == true)
        
        verify { logger.w(any(), "Partially extracted native libraries: 1 success, 1 failed") }
    }

    @Test
    fun `extractNativeLibraries should handle extraction failure`() = runTest {
        // Given
        val failureResult = com.tamersarioglu.clipcatch.util.ExtractionResult.Failure(
            error = "APK not found",
            cause = RuntimeException("Test exception")
        )
        
        coEvery { 
            fileExtractionUtils.extractFromAPK(any(), any(), any())
        } returns failureResult

        // When
        val result = nativeLibraryManager.extractNativeLibraries()

        // Then
        assertFalse(result.success)
        assertTrue(result.extractedFiles.isEmpty())
        assertNotNull(result.error)
        assertTrue(result.error is InitializationError.NativeLibraryError)
        assertTrue(result.error?.message?.contains("Failed to extract native libraries") == true)
        
        verify { logger.e(any(), "Failed to extract native libraries: APK not found") }
    }

    @Test
    fun `extractNativeLibraries should handle unexpected exception`() = runTest {
        // Given
        coEvery { 
            fileExtractionUtils.extractFromAPK(any(), any(), any())
        } throws RuntimeException("Unexpected error")

        // When
        val result = nativeLibraryManager.extractNativeLibraries()

        // Then
        assertFalse(result.success)
        assertNotNull(result.error)
        assertTrue(result.error is InitializationError.NativeLibraryError)
        assertTrue(result.error?.message?.contains("Unexpected error") == true)
        
        verify { logger.e(any(), any<String>(), any<Exception>()) }
    }

    @Test
    fun `getNativeLibraryDirectory should return path containing native_libs`() {
        // When
        val result = nativeLibraryManager.getNativeLibraryDirectory()

        // Then
        assertTrue(result.contains("native_libs"))
    }

    @Test
    fun `setNativeLibraryPath should log the operation`() {
        // Given
        val testPath = "/test/path"

        // When
        nativeLibraryManager.setNativeLibraryPath(testPath)

        // Then
        verify { logger.d(any(), "Setting native library path: $testPath") }
    }
}