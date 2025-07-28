package com.tamersarioglu.clipcatch.util

import android.content.Context
import java.io.File
import java.util.zip.ZipEntry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility interface for file extraction operations from APK and ZIP archives.
 * Provides methods to extract files from the application APK and handle ZIP archive extraction.
 */
interface FileExtractionUtils {
    /**
     * Extracts files from the application APK based on a pattern and filter.
     *
     * @param sourcePattern Pattern to match files in the APK (e.g., "lib/", "assets/")
     * @param targetDirectory Directory where extracted files will be placed
     * @param filter Optional filter function to determine which entries to extract
     * @return ExtractionResult indicating success or failure with details
     */
    suspend fun extractFromAPK(
        sourcePattern: String,
        targetDirectory: File,
        filter: (ZipEntry) -> Boolean = { true }
    ): ExtractionResult

    /**
     * Extracts contents of a ZIP archive to a target directory.
     *
     * @param zipFile The ZIP file to extract
     * @param targetDirectory Directory where extracted files will be placed
     * @return ExtractionResult indicating success or failure with details
     */
    suspend fun extractZipArchive(
        zipFile: File,
        targetDirectory: File
    ): ExtractionResult
}

/**
 * Result of a file extraction operation.
 */
sealed class ExtractionResult {
    data class Success(
        val extractedCount: Int,
        val extractedFiles: List<String> = emptyList()
    ) : ExtractionResult()

    data class Failure(
        val error: String,
        val cause: Throwable? = null
    ) : ExtractionResult()

    data class PartialSuccess(
        val extractedCount: Int,
        val failedCount: Int,
        val extractedFiles: List<String> = emptyList(),
        val errors: List<String> = emptyList()
    ) : ExtractionResult()
}