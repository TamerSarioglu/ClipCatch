package com.tamersarioglu.clipcatch.util

import android.content.Context
import android.content.pm.ApplicationInfo
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class FileExtractionUtilsTest {

    private lateinit var context: Context
    private lateinit var logger: Logger
    private lateinit var fileExtractionUtils: FileExtractionUtilsImpl
    private lateinit var tempDir: File
    private lateinit var testApkFile: File

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        fileExtractionUtils = FileExtractionUtilsImpl(context, logger)
        
        // Create temporary directory for tests
        tempDir = createTempDir("file_extraction_test")
        
        // Create a test APK file
        testApkFile = File(tempDir, "test.apk")
        createTestApkFile(testApkFile)
        
        // Mock ApplicationInfo
        val applicationInfo = mockk<ApplicationInfo>(relaxed = true)
        applicationInfo.sourceDir = testApkFile.absolutePath
        every { context.applicationInfo } returns applicationInfo
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `extractFromAPK should successfully extract matching files`() = runTest {
        // Given
        val targetDir = File(tempDir, "extracted")
        val sourcePattern = "lib/"
        
        // When
        val result = fileExtractionUtils.extractFromAPK(sourcePattern, targetDir)
        
        // Then
        assertTrue("Extraction should succeed", result is ExtractionResult.Success)
        val successResult = result as ExtractionResult.Success
        assertEquals("Should extract 2 files", 2, successResult.extractedCount)
        assertTrue("Should contain libtest1.so", successResult.extractedFiles.contains("lib/arm64-v8a/libtest1.so"))
        assertTrue("Should contain libtest2.so", successResult.extractedFiles.contains("lib/arm64-v8a/libtest2.so"))
        
        // Verify files were actually created
        assertTrue("libtest1.so should exist", File(targetDir, "libtest1.so").exists())
        assertTrue("libtest2.so should exist", File(targetDir, "libtest2.so").exists())
        
        // Verify logging
        verify { logger.enter(any(), "extractFromAPK", sourcePattern, targetDir.absolutePath) }
        verify { logger.exit(any(), "extractFromAPK", any()) }
    }

    @Test
    fun `extractFromAPK should handle filter correctly`() = runTest {
        // Given
        val targetDir = File(tempDir, "extracted_filtered")
        val sourcePattern = "lib/"
        val filter: (ZipEntry) -> Boolean = { entry -> entry.name.contains("libtest1") }
        
        // When
        val result = fileExtractionUtils.extractFromAPK(sourcePattern, targetDir, filter)
        
        // Then
        assertTrue("Extraction should succeed", result is ExtractionResult.Success)
        val successResult = result as ExtractionResult.Success
        assertEquals("Should extract 1 file", 1, successResult.extractedCount)
        assertTrue("Should contain only libtest1.so", successResult.extractedFiles.contains("lib/arm64-v8a/libtest1.so"))
        assertFalse("Should not contain libtest2.so", successResult.extractedFiles.contains("lib/arm64-v8a/libtest2.so"))
    }

    @Test
    fun `extractFromAPK should handle non-existent APK file`() = runTest {
        // Given
        val applicationInfo = mockk<ApplicationInfo>(relaxed = true)
        applicationInfo.sourceDir = "/non/existent/path.apk"
        every { context.applicationInfo } returns applicationInfo
        
        val targetDir = File(tempDir, "extracted_fail")
        
        // When
        val result = fileExtractionUtils.extractFromAPK("lib/", targetDir)
        
        // Then
        assertTrue("Extraction should fail", result is ExtractionResult.Failure)
        val failureResult = result as ExtractionResult.Failure
        assertTrue("Error should mention APK file", failureResult.error.contains("APK file does not exist"))
    }

    @Test
    fun `extractFromAPK should create target directory if it doesn't exist`() = runTest {
        // Given
        val targetDir = File(tempDir, "new_directory")
        assertFalse("Target directory should not exist initially", targetDir.exists())
        
        // When
        val result = fileExtractionUtils.extractFromAPK("lib/", targetDir)
        
        // Then
        assertTrue("Target directory should be created", targetDir.exists())
        assertTrue("Extraction should succeed", result is ExtractionResult.Success)
    }

    @Test
    fun `extractZipArchive should successfully extract ZIP contents`() = runTest {
        // Given
        val zipFile = File(tempDir, "test.zip")
        createTestZipFile(zipFile)
        val targetDir = File(tempDir, "zip_extracted")
        
        // When
        val result = fileExtractionUtils.extractZipArchive(zipFile, targetDir)
        
        // Then
        assertTrue("ZIP extraction should succeed", result is ExtractionResult.Success)
        val successResult = result as ExtractionResult.Success
        assertEquals("Should extract 2 files", 2, successResult.extractedCount)
        
        // Verify files were created
        assertTrue("file1.txt should exist", File(targetDir, "file1.txt").exists())
        assertTrue("file2.txt should exist", File(targetDir, "file2.txt").exists())
        
        // Verify content
        assertEquals("Content should match", "Content of file 1", File(targetDir, "file1.txt").readText())
        assertEquals("Content should match", "Content of file 2", File(targetDir, "file2.txt").readText())
        
        // Verify logging
        verify { logger.enter(any(), "extractZipArchive", zipFile.name, targetDir.absolutePath) }
        verify { logger.exit(any(), "extractZipArchive", any()) }
    }

    @Test
    fun `extractZipArchive should handle non-existent ZIP file`() = runTest {
        // Given
        val nonExistentZip = File(tempDir, "non_existent.zip")
        val targetDir = File(tempDir, "zip_fail")
        
        // When
        val result = fileExtractionUtils.extractZipArchive(nonExistentZip, targetDir)
        
        // Then
        assertTrue("ZIP extraction should fail", result is ExtractionResult.Failure)
        val failureResult = result as ExtractionResult.Failure
        assertTrue("Error should mention ZIP file", failureResult.error.contains("ZIP file does not exist"))
    }

    @Test
    fun `extractZipArchive should create target directory if it doesn't exist`() = runTest {
        // Given
        val zipFile = File(tempDir, "test.zip")
        createTestZipFile(zipFile)
        val targetDir = File(tempDir, "new_zip_directory")
        assertFalse("Target directory should not exist initially", targetDir.exists())
        
        // When
        val result = fileExtractionUtils.extractZipArchive(zipFile, targetDir)
        
        // Then
        assertTrue("Target directory should be created", targetDir.exists())
        assertTrue("ZIP extraction should succeed", result is ExtractionResult.Success)
    }

    @Test
    fun `extractZipArchive should handle directories in ZIP`() = runTest {
        // Given
        val zipFile = File(tempDir, "test_with_dirs.zip")
        createTestZipFileWithDirectories(zipFile)
        val targetDir = File(tempDir, "zip_with_dirs")
        
        // When
        val result = fileExtractionUtils.extractZipArchive(zipFile, targetDir)
        
        // Then
        assertTrue("ZIP extraction should succeed", result is ExtractionResult.Success)
        val successResult = result as ExtractionResult.Success
        assertEquals("Should extract 2 files (directories are skipped)", 2, successResult.extractedCount)
        
        // Verify directory structure
        assertTrue("Subdirectory should exist", File(targetDir, "subdir").exists())
        assertTrue("File in subdirectory should exist", File(targetDir, "subdir/nested.txt").exists())
    }

    @Test
    fun `extractFromAPK should handle ZIP archive extraction`() = runTest {
        // Given
        val testApkWithZip = File(tempDir, "test_with_zip.apk")
        createTestApkFileWithZipArchive(testApkWithZip)
        
        val applicationInfo = mockk<ApplicationInfo>(relaxed = true)
        applicationInfo.sourceDir = testApkWithZip.absolutePath
        every { context.applicationInfo } returns applicationInfo
        
        val targetDir = File(tempDir, "extracted_with_zip")
        
        // When
        val result = fileExtractionUtils.extractFromAPK("lib/", targetDir)
        
        // Then
        assertTrue("Extraction should succeed", result is ExtractionResult.Success)
        val successResult = result as ExtractionResult.Success
        assertTrue("Should extract at least the ZIP archive", successResult.extractedCount >= 1)
        
        // The ZIP archive should be extracted and its contents should be available
        assertTrue("ZIP archive should be extracted", File(targetDir, "libpython.zip.so").exists())
    }

    private fun createTestApkFile(apkFile: File) {
        ZipOutputStream(FileOutputStream(apkFile)).use { zipOut ->
            // Add some test native libraries
            addZipEntry(zipOut, "lib/arm64-v8a/libtest1.so", "Native library 1 content")
            addZipEntry(zipOut, "lib/arm64-v8a/libtest2.so", "Native library 2 content")
            addZipEntry(zipOut, "assets/test.txt", "Asset file content")
            addZipEntry(zipOut, "META-INF/MANIFEST.MF", "Manifest content")
        }
    }

    private fun createTestApkFileWithZipArchive(apkFile: File) {
        ZipOutputStream(FileOutputStream(apkFile)).use { zipOut ->
            // Create a ZIP archive as a byte array
            val zipArchiveBytes = createZipArchiveBytes()
            
            // Add the ZIP archive as a .zip.so file
            val entry = ZipEntry("lib/arm64-v8a/libpython.zip.so")
            entry.size = zipArchiveBytes.size.toLong()
            zipOut.putNextEntry(entry)
            zipOut.write(zipArchiveBytes)
            zipOut.closeEntry()
            
            // Add other files
            addZipEntry(zipOut, "lib/arm64-v8a/libtest.so", "Native library content")
        }
    }

    private fun createZipArchiveBytes(): ByteArray {
        val tempZip = File(tempDir, "temp_archive.zip")
        ZipOutputStream(FileOutputStream(tempZip)).use { zipOut ->
            addZipEntry(zipOut, "python/test.py", "print('Hello from Python')")
            addZipEntry(zipOut, "python/module.py", "def test(): pass")
        }
        val bytes = tempZip.readBytes()
        tempZip.delete()
        return bytes
    }

    private fun createTestZipFile(zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            addZipEntry(zipOut, "file1.txt", "Content of file 1")
            addZipEntry(zipOut, "file2.txt", "Content of file 2")
        }
    }

    private fun createTestZipFileWithDirectories(zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            // Add directory entry
            val dirEntry = ZipEntry("subdir/")
            zipOut.putNextEntry(dirEntry)
            zipOut.closeEntry()
            
            // Add files
            addZipEntry(zipOut, "root.txt", "Root file content")
            addZipEntry(zipOut, "subdir/nested.txt", "Nested file content")
        }
    }

    private fun addZipEntry(zipOut: ZipOutputStream, name: String, content: String) {
        val entry = ZipEntry(name)
        entry.size = content.toByteArray().size.toLong()
        zipOut.putNextEntry(entry)
        zipOut.write(content.toByteArray())
        zipOut.closeEntry()
    }
}