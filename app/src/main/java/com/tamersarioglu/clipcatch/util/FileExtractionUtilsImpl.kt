package com.tamersarioglu.clipcatch.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of FileExtractionUtils that handles file extraction from APK and ZIP archives.
 * Extracted from the original ClipCatchApplication to provide reusable file extraction functionality.
 */
@Singleton
class FileExtractionUtilsImpl @Inject constructor(
    private val context: Context,
    private val logger: Logger
) : FileExtractionUtils {

    companion object {
        private const val TAG = "FileExtractionUtils"
        private const val BUFFER_SIZE = 8192
    }

    override suspend fun extractFromAPK(
        sourcePattern: String,
        targetDirectory: File,
        filter: (ZipEntry) -> Boolean
    ): ExtractionResult = withContext(Dispatchers.IO) {
        logger.enter(TAG, "extractFromAPK", sourcePattern, targetDirectory.absolutePath)
        
        try {
            val apkFile = File(context.applicationInfo.sourceDir)
            
            if (!apkFile.exists()) {
                val error = "APK file does not exist: ${apkFile.absolutePath}"
                logger.e(TAG, error)
                return@withContext ExtractionResult.Failure(error)
            }

            if (!targetDirectory.exists()) {
                val created = targetDirectory.mkdirs()
                logger.d(TAG, "Created target directory: $created - ${targetDirectory.absolutePath}")
            }

            logger.d(TAG, "Starting APK extraction from: ${apkFile.absolutePath}")
            logger.d(TAG, "APK size: ${apkFile.length()} bytes")

            val zipFile = ZipFile(apkFile)
            val entries = zipFile.entries()
            
            val extractedFiles = mutableListOf<String>()
            val errors = mutableListOf<String>()
            var extractedCount = 0

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                
                // Check if entry matches the source pattern and passes the filter
                if (entry.name.startsWith(sourcePattern) && filter(entry)) {
                    try {
                        val targetFile = File(targetDirectory, entry.name.substringAfterLast("/"))
                        
                        val shouldExtract = !targetFile.exists()
                        
                        if (shouldExtract) {
                            extractZipEntry(zipFile, entry, targetFile)
                            extractedCount++
                            extractedFiles.add(entry.name)
                            logger.d(TAG, "Extracted: ${entry.name} -> ${targetFile.name} (${entry.size} bytes)")
                        } else {
                            logger.d(TAG, "File already exists, skipping extraction: ${targetFile.name}")
                        }
                        
                        // Always try to extract ZIP archive contents, even if the archive already exists
                        if (targetFile.name.endsWith(".zip.so")) {
                            logger.d(TAG, "Detected ZIP archive, extracting contents: ${targetFile.name}")
                            when (val zipResult = extractZipArchive(targetFile, targetDirectory)) {
                                is ExtractionResult.Success -> {
                                    logger.d(TAG, "Successfully extracted ${zipResult.extractedCount} files from ZIP archive")
                                }
                                is ExtractionResult.Failure -> {
                                    logger.w(TAG, "Failed to extract ZIP archive contents: ${zipResult.error}")
                                    errors.add("ZIP extraction failed for ${targetFile.name}: ${zipResult.error}")
                                }
                                is ExtractionResult.PartialSuccess -> {
                                    logger.w(TAG, "Partially extracted ZIP archive: ${zipResult.extractedCount} success, ${zipResult.failedCount} failed")
                                    errors.addAll(zipResult.errors)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        val errorMsg = "Failed to extract ${entry.name}: ${e.message}"
                        logger.w(TAG, errorMsg, e)
                        errors.add(errorMsg)
                    }
                }
            }
            
            zipFile.close()
            logger.d(TAG, "APK extraction completed: $extractedCount files extracted")
            
            val result = when {
                errors.isEmpty() -> ExtractionResult.Success(extractedCount, extractedFiles)
                extractedCount > 0 -> ExtractionResult.PartialSuccess(extractedCount, errors.size, extractedFiles, errors)
                else -> ExtractionResult.Failure("No files extracted. Errors: ${errors.joinToString("; ")}")
            }
            
            logger.exit(TAG, "extractFromAPK", result)
            result
            
        } catch (e: Exception) {
            val error = "Failed to extract files from APK: ${e.message}"
            logger.e(TAG, error, e)
            ExtractionResult.Failure(error, e)
        }
    }

    override suspend fun extractZipArchive(
        zipFile: File,
        targetDirectory: File
    ): ExtractionResult = withContext(Dispatchers.IO) {
        logger.enter(TAG, "extractZipArchive", zipFile.name, targetDirectory.absolutePath)
        
        try {
            if (!zipFile.exists()) {
                val error = "ZIP file does not exist: ${zipFile.absolutePath}"
                logger.e(TAG, error)
                return@withContext ExtractionResult.Failure(error)
            }

            if (!targetDirectory.exists()) {
                val created = targetDirectory.mkdirs()
                logger.d(TAG, "Created target directory: $created - ${targetDirectory.absolutePath}")
            }

            logger.d(TAG, "Extracting ZIP archive: ${zipFile.name} (${zipFile.length()} bytes)")
            
            val zipInputStream = ZipInputStream(zipFile.inputStream())
            val extractedFiles = mutableListOf<String>()
            val errors = mutableListOf<String>()
            var entry: ZipEntry?
            var extractedCount = 0

            while (zipInputStream.nextEntry.also { entry = it } != null) {
                val currentEntry = entry!!
                val entryName = currentEntry.name
                
                try {
                    val targetFile = File(targetDirectory, entryName)
                    
                    // Create parent directories if needed
                    targetFile.parentFile?.let { parentDir ->
                        if (!parentDir.exists()) {
                            val created = parentDir.mkdirs()
                            logger.d(TAG, "Created parent directory: $created - ${parentDir.absolutePath}")
                        }
                    }
                    
                    if (!currentEntry.isDirectory) {
                        extractZipEntryFromStream(zipInputStream, targetFile)
                        extractedCount++
                        extractedFiles.add(entryName)
                        logger.d(TAG, "Extracted from ZIP: $entryName (${currentEntry.size} bytes)")
                    } else {
                        logger.d(TAG, "Skipping directory entry: $entryName")
                    }
                } catch (e: Exception) {
                    val errorMsg = "Failed to extract ZIP entry $entryName: ${e.message}"
                    logger.w(TAG, errorMsg, e)
                    errors.add(errorMsg)
                } finally {
                    zipInputStream.closeEntry()
                }
            }
            
            zipInputStream.close()
            logger.d(TAG, "ZIP extraction completed: $extractedCount files extracted")
            
            val result = when {
                errors.isEmpty() -> ExtractionResult.Success(extractedCount, extractedFiles)
                extractedCount > 0 -> ExtractionResult.PartialSuccess(extractedCount, errors.size, extractedFiles, errors)
                else -> ExtractionResult.Failure("No files extracted from ZIP. Errors: ${errors.joinToString("; ")}")
            }
            
            logger.exit(TAG, "extractZipArchive", result)
            result
            
        } catch (e: Exception) {
            val error = "Failed to extract ZIP archive: ${e.message}"
            logger.e(TAG, error, e)
            ExtractionResult.Failure(error, e)
        }
    }

    /**
     * Extracts a single ZIP entry to a target file.
     * Extracted from the original ClipCatchApplication.extractZipEntry method.
     */
    private fun extractZipEntry(zipFile: ZipFile, entry: ZipEntry, targetFile: File) {
        logger.d(TAG, "Extracting ZIP entry: ${entry.name} -> ${targetFile.name}")
        
        var inputStream: java.io.InputStream? = null
        var outputStream: FileOutputStream? = null
        
        try {
            inputStream = zipFile.getInputStream(entry)
            outputStream = FileOutputStream(targetFile)
            
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalBytes = 0L
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
            }
            
            logger.d(TAG, "Successfully extracted ${entry.name}: $totalBytes bytes written")
            
        } finally {
            try {
                inputStream?.close()
            } catch (e: Exception) {
                logger.w(TAG, "Error closing input stream for ${entry.name}", e)
            }
            try {
                outputStream?.close()
            } catch (e: Exception) {
                logger.w(TAG, "Error closing output stream for ${targetFile.name}", e)
            }
        }
    }

    /**
     * Extracts a ZIP entry from an input stream to a target file.
     * Used when extracting from a ZipInputStream.
     */
    private fun extractZipEntryFromStream(zipInputStream: ZipInputStream, targetFile: File) {
        logger.d(TAG, "Extracting ZIP entry from stream -> ${targetFile.name}")
        
        var outputStream: FileOutputStream? = null
        
        try {
            outputStream = FileOutputStream(targetFile)
            
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalBytes = 0L
            
            while (zipInputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
            }
            
            logger.d(TAG, "Successfully extracted from stream -> ${targetFile.name}: $totalBytes bytes written")
            
        } finally {
            try {
                outputStream?.close()
            } catch (e: Exception) {
                logger.w(TAG, "Error closing output stream for ${targetFile.name}", e)
            }
        }
    }
}