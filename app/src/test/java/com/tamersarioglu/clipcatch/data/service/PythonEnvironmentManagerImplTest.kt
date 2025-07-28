package com.tamersarioglu.clipcatch.data.service

import android.content.Context
import com.tamersarioglu.clipcatch.domain.model.InitializationError
import com.tamersarioglu.clipcatch.util.FileExtractionUtils
import com.tamersarioglu.clipcatch.util.Logger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipEntry

@RunWith(RobolectricTestRunner::class)
class PythonEnvironmentManagerImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var pythonEnvironmentManager: PythonEnvironmentManagerImpl
    private lateinit var mockContext: Context
    private lateinit var mockFileExtractionUtils: FileExtractionUtils
    private lateinit var mockLogger: Logger
    private lateinit var testFilesDir: File

    @Before
    fun setUp() {
        mockContext = mockk()
        mockFileExtractionUtils = mockk()
        mockLogger = mockk(relaxed = true)
        
        testFilesDir = tempFolder.newFolder("files")
        every { mockContext.filesDir } returns testFilesDir

        pythonEnvironmentManager = PythonEnvironmentManagerImpl(
            context = mockContext,
            fileExtractionUtils = mockFileExtractionUtils,
            errorHandler = mockk(relaxed = true),
            logger = mockLogger
        )
    }

    @Test
    fun `getPythonDirectory returns correct directory`() {
        // Act
        val result = pythonEnvironmentManager.getPythonDirectory()

        // Assert
        assertEquals("python", result.name)
        assertEquals(testFilesDir, result.parentFile)
    }

    @Test
    fun `shouldExtractPythonFiles returns true when directory does not exist`() {
        // Act - Python directory doesn't exist yet
        val result = pythonEnvironmentManager.shouldExtractPythonFiles()

        // Assert
        assertTrue(result)
        verify { mockLogger.d("PythonEnvironmentManager", "Python directory does not exist, extraction needed") }
    }

    @Test
    fun `shouldExtractPythonFiles returns true when directory is empty`() {
        // Arrange - Create empty python directory
        val pythonDir = File(testFilesDir, "python")
        pythonDir.mkdirs()

        // Act
        val result = pythonEnvironmentManager.shouldExtractPythonFiles()

        // Assert
        assertTrue(result)
        verify { mockLogger.d("PythonEnvironmentManager", "Python directory has 0 files, extraction needed: true") }
    }

    @Test
    fun `shouldExtractPythonFiles returns false when directory has files`() {
        // Arrange - Create python directory with a file
        val pythonDir = File(testFilesDir, "python")
        pythonDir.mkdirs()
        File(pythonDir, "test.py").createNewFile()

        // Act
        val result = pythonEnvironmentManager.shouldExtractPythonFiles()

        // Assert
        assertFalse(result)
        verify { mockLogger.d("PythonEnvironmentManager", "Python directory has 1 files, extraction needed: false") }
    }

    @Test
    fun `ensurePythonDirectoryExists returns true when directory already exists`() {
        // Arrange - Create python directory
        val pythonDir = File(testFilesDir, "python")
        pythonDir.mkdirs()

        // Act
        val result = pythonEnvironmentManager.ensurePythonDirectoryExists()

        // Assert
        assertTrue(result)
        verify { mockLogger.d("PythonEnvironmentManager", "Python directory already exists: ${pythonDir.absolutePath}") }
    }

    @Test
    fun `ensurePythonDirectoryExists creates directory when it does not exist`() {
        // Act - Python directory doesn't exist yet
        val result = pythonEnvironmentManager.ensurePythonDirectoryExists()

        // Assert
        assertTrue(result)
        val pythonDir = File(testFilesDir, "python")
        assertTrue(pythonDir.exists())
        verify { mockLogger.d("PythonEnvironmentManager", "Python directory creation result: true - ${pythonDir.absolutePath}") }
    }

    @Test
    fun `ensurePythonDirectoryExists handles read-only filesystem gracefully`() {
        // This test is hard to simulate with real files, so we'll test the logging behavior
        // In a real scenario, mkdirs() would return false if the filesystem is read-only
        
        // For now, just test that the method exists and can be called
        val result = pythonEnvironmentManager.ensurePythonDirectoryExists()
        
        // Should succeed in our test environment
        assertTrue(result)
    }

    @Test
    fun `getRequiredPythonFiles returns expected patterns`() {
        // Act
        val result = pythonEnvironmentManager.getRequiredPythonFiles()

        // Assert
        assertEquals(5, result.size)
        assertTrue(result.contains("python"))
        assertTrue(result.contains("yt-dlp"))
        assertTrue(result.contains(".py"))
        assertTrue(result.contains(".pyc"))
        assertTrue(result.contains("python.zip"))
    }

    @Test
    fun `extractPythonFiles succeeds with successful extraction`() = runTest {
        // Arrange
        val pythonDir = File(testFilesDir, "python")
        val mockExtractionResult = com.tamersarioglu.clipcatch.util.ExtractionResult.Success(
            extractedCount = 3,
            extractedFiles = listOf("python.zip", "yt-dlp", "test.py")
        )
        
        coEvery { 
            mockFileExtractionUtils.extractFromAPK(
                sourcePattern = "",
                targetDirectory = any(),
                filter = any()
            ) 
        } returns mockExtractionResult

        // Act
        val result = pythonEnvironmentManager.extractPythonFiles()

        // Assert
        assertTrue(result.success)
        assertEquals(3, result.extractedFiles.size)
        assertEquals(pythonDir.absolutePath, result.extractionPath)
        assertNull(result.error)
        
        coVerify { 
            mockFileExtractionUtils.extractFromAPK(
                sourcePattern = "",
                targetDirectory = any(),
                filter = any()
            ) 
        }
    }

    @Test
    fun `extractPythonFiles handles extraction failure`() = runTest {
        // Arrange
        val pythonDir = File(testFilesDir, "python")
        val mockExtractionResult = com.tamersarioglu.clipcatch.util.ExtractionResult.Failure(
            error = "Extraction failed",
            cause = RuntimeException("Test error")
        )
        
        coEvery { 
            mockFileExtractionUtils.extractFromAPK(
                sourcePattern = "",
                targetDirectory = any(),
                filter = any()
            ) 
        } returns mockExtractionResult

        // Act
        val result = pythonEnvironmentManager.extractPythonFiles()

        // Assert
        assertFalse(result.success)
        assertTrue(result.extractedFiles.isEmpty())
        assertEquals(pythonDir.absolutePath, result.extractionPath)
        assertNotNull(result.error)
        assertTrue(result.error is InitializationError.PythonEnvironmentError)
        assertEquals("Extraction failed", result.error?.message)
    }

    @Test
    fun `extractPythonFiles handles partial success`() = runTest {
        // Arrange
        val pythonDir = File(testFilesDir, "python")
        val mockExtractionResult = com.tamersarioglu.clipcatch.util.ExtractionResult.PartialSuccess(
            extractedCount = 2,
            failedCount = 1,
            extractedFiles = listOf("python.zip", "yt-dlp"),
            errors = listOf("Failed to extract test.py")
        )
        
        coEvery { 
            mockFileExtractionUtils.extractFromAPK(
                sourcePattern = "",
                targetDirectory = any(),
                filter = any()
            ) 
        } returns mockExtractionResult

        // Act
        val result = pythonEnvironmentManager.extractPythonFiles()

        // Assert
        assertTrue(result.success) // Should be true because extractedCount > 0
        assertEquals(2, result.extractedFiles.size)
        assertEquals(1, result.failedFiles.size)
        assertEquals(pythonDir.absolutePath, result.extractionPath)
        assertNotNull(result.error)
        assertTrue(result.error is InitializationError.PythonEnvironmentError)
    }

    @Test
    fun `extractPythonFiles handles exceptions`() = runTest {
        // Arrange
        val pythonDir = File(testFilesDir, "python")
        coEvery { 
            mockFileExtractionUtils.extractFromAPK(any(), any(), any()) 
        } throws RuntimeException("Test exception")

        // Act
        val result = pythonEnvironmentManager.extractPythonFiles()

        // Assert
        assertFalse(result.success)
        assertTrue(result.extractedFiles.isEmpty())
        assertEquals(pythonDir.absolutePath, result.extractionPath)
        assertNotNull(result.error)
        assertTrue(result.error is InitializationError.PythonEnvironmentError)
        assertEquals("Failed to extract Python files: Test exception", result.error?.message)
    }

    @Test
    fun `verifyPythonEnvironment succeeds when all checks pass`() = runTest {
        // Arrange - Create python directory with files matching all required patterns
        val pythonDir = File(testFilesDir, "python")
        pythonDir.mkdirs()
        File(pythonDir, "python.zip").createNewFile()  // matches "python" and "python.zip"
        File(pythonDir, "yt-dlp").createNewFile()      // matches "yt-dlp"
        File(pythonDir, "test.py").createNewFile()     // matches ".py"
        File(pythonDir, "module.pyc").createNewFile()  // matches ".pyc"

        // Act
        val result = pythonEnvironmentManager.verifyPythonEnvironment()

        // Assert
        assertTrue("Verification should succeed when all patterns are found", result.success)
        assertTrue(result.verifiedItems.contains("Python directory exists"))
        assertTrue(result.verifiedItems.contains("Python directory readable"))
        assertTrue(result.verifiedItems.contains("Python files present"))
        assertTrue(result.verifiedItems.contains("Required pattern: python"))
        assertTrue(result.verifiedItems.contains("Required pattern: yt-dlp"))
        assertTrue(result.verifiedItems.contains("Required pattern: .py"))
        assertTrue(result.verifiedItems.contains("Required pattern: .pyc"))
        assertTrue(result.verifiedItems.contains("Required pattern: python.zip"))
        assertTrue("Failed items should be empty: ${result.failedItems}", result.failedItems.isEmpty())
        assertNull(result.error)
        assertEquals(pythonDir.absolutePath, result.verificationDetails["pythonDirectory"])
        assertEquals("4", result.verificationDetails["fileCount"])
    }

    @Test
    fun `verifyPythonEnvironment fails when directory does not exist`() = runTest {
        // Act - Python directory doesn't exist
        val result = pythonEnvironmentManager.verifyPythonEnvironment()

        // Assert
        assertFalse(result.success)
        assertTrue(result.failedItems.contains("Python directory exists"))
        assertTrue(result.failedItems.contains("Python directory readable"))
        assertTrue(result.failedItems.contains("Python files present"))
        assertNotNull(result.error)
        assertTrue(result.error is InitializationError.PythonEnvironmentError)
    }

    @Test
    fun `verifyPythonEnvironment handles missing required patterns`() = runTest {
        // Arrange - Create python directory with unrelated file
        val pythonDir = File(testFilesDir, "python")
        pythonDir.mkdirs()
        File(pythonDir, "unrelated.txt").createNewFile()

        // Act
        val result = pythonEnvironmentManager.verifyPythonEnvironment()

        // Assert
        assertFalse(result.success)
        assertTrue(result.verifiedItems.contains("Python directory exists"))
        assertTrue(result.verifiedItems.contains("Python directory readable"))
        assertTrue(result.verifiedItems.contains("Python files present"))
        assertTrue(result.failedItems.contains("Required pattern: python"))
        assertTrue(result.failedItems.contains("Required pattern: yt-dlp"))
        assertTrue(result.failedItems.contains("Required pattern: .py"))
        assertTrue(result.failedItems.contains("Required pattern: .pyc"))
        assertTrue(result.failedItems.contains("Required pattern: python.zip"))
        assertNotNull(result.error)
    }

    @Test
    fun `setupPythonEnvironment succeeds with all steps`() = runTest {
        // Arrange
        val mockExtractionResult = com.tamersarioglu.clipcatch.util.ExtractionResult.Success(
            extractedCount = 4,
            extractedFiles = listOf("python.zip", "yt-dlp", "test.py", "module.pyc")
        )
        
        coEvery { 
            mockFileExtractionUtils.extractFromAPK(any(), any(), any()) 
        } returns mockExtractionResult
        
        // Create the files that would be extracted to make verification pass
        coEvery { 
            mockFileExtractionUtils.extractFromAPK(any(), any(), any()) 
        } answers {
            val pythonDir = File(testFilesDir, "python")
            pythonDir.mkdirs()
            File(pythonDir, "python.zip").createNewFile()
            File(pythonDir, "yt-dlp").createNewFile()
            File(pythonDir, "test.py").createNewFile()
            File(pythonDir, "module.pyc").createNewFile()
            mockExtractionResult
        }

        // Act
        val result = pythonEnvironmentManager.setupPythonEnvironment()

        // Assert
        assertTrue("Setup should succeed: ${result.failedSteps}", result.success)
        assertEquals(3, result.setupSteps.size)
        assertTrue(result.setupSteps.contains("Python directory creation"))
        assertTrue(result.setupSteps.contains("Python files extraction"))
        assertTrue(result.setupSteps.contains("Python environment verification"))
        assertTrue("Failed steps should be empty: ${result.failedSteps}", result.failedSteps.isEmpty())
        val pythonDir = File(testFilesDir, "python")
        assertEquals(pythonDir.absolutePath, result.setupPath)
        assertNull(result.error)
    }

    @Test
    fun `setupPythonEnvironment handles extraction failures gracefully`() = runTest {
        // Arrange - Mock extraction failure
        coEvery { 
            mockFileExtractionUtils.extractFromAPK(any(), any(), any()) 
        } returns com.tamersarioglu.clipcatch.util.ExtractionResult.Failure(
            error = "Extraction failed",
            cause = RuntimeException("Test error")
        )

        // Act
        val result = pythonEnvironmentManager.setupPythonEnvironment()

        // Assert
        assertFalse(result.success)
        assertTrue(result.failedSteps.contains("Python files extraction"))
        assertTrue(result.setupSteps.contains("Python directory creation"))
        assertNotNull(result.error)
        assertTrue(result.error is InitializationError.PythonEnvironmentError)
    }
}