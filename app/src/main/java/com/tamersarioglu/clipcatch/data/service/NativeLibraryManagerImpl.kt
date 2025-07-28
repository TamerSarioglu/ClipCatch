package com.tamersarioglu.clipcatch.data.service

import android.content.Context
import com.tamersarioglu.clipcatch.domain.model.ExtractionResult
import com.tamersarioglu.clipcatch.domain.model.InitializationError
import com.tamersarioglu.clipcatch.domain.model.LoadResult
import com.tamersarioglu.clipcatch.domain.model.VerificationResult
import com.tamersarioglu.clipcatch.util.FileExtractionUtils
import com.tamersarioglu.clipcatch.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of NativeLibraryManager that handles native library extraction, loading, and verification.
 * Extracted from the original ClipCatchApplication to provide dedicated native library management.
 */
@Singleton
class NativeLibraryManagerImpl @Inject constructor(
    private val context: Context,
    private val fileExtractionUtils: FileExtractionUtils,
    private val errorHandler: InitializationErrorHandler,
    private val logger: Logger
) : NativeLibraryManager {

    companion object {
        private const val TAG = "NativeLibraryManager"
        private const val NATIVE_LIBS_DIR = "native_libs"
        
        // Native library patterns to look for in the APK
        private val NATIVE_LIBRARY_PATTERNS = listOf(
            "python",
            "ffmpeg", 
            "ffprobe",
            "aria2c",
            "ssl",
            "crypto"
        )
    }

    private val privateLibDir: File by lazy {
        File(context.filesDir, NATIVE_LIBS_DIR)
    }

    override suspend fun extractNativeLibraries(): ExtractionResult = withContext(Dispatchers.IO) {
        logger.enter(TAG, "extractNativeLibraries")
        
        try {
            // Determine the device architecture
            val architecture = getDeviceArchitecture()
            logger.d(TAG, "Device architecture: $architecture")
            
            logger.d(TAG, "Starting native library extraction from APK")
            logger.d(TAG, "Target directory: ${privateLibDir.absolutePath}")
            logger.d(TAG, "Looking for libraries matching patterns: ${NATIVE_LIBRARY_PATTERNS.joinToString(", ")}")
            logger.d(TAG, "Target architecture: $architecture")
            
            if (!privateLibDir.exists()) {
                val created = privateLibDir.mkdirs()
                logger.d(TAG, "Created private native library directory: $created")
            }
            
            // Filter for native libraries that need extraction
            val nativeLibraryFilter: (ZipEntry) -> Boolean = { entry ->
                entry.name.startsWith("lib/$architecture/") && 
                NATIVE_LIBRARY_PATTERNS.any { pattern -> entry.name.contains(pattern) } &&
                (entry.name.endsWith(".so") || entry.name.endsWith(".zip.so"))
            }
            
            // Extract native libraries from APK
            val extractionResult = fileExtractionUtils.extractFromAPK(
                sourcePattern = "lib/",
                targetDirectory = privateLibDir,
                filter = nativeLibraryFilter
            )
            
            logger.d(TAG, "Extraction result type: ${extractionResult::class.simpleName}")
            val result = when (extractionResult) {
                is com.tamersarioglu.clipcatch.util.ExtractionResult.Success -> {
                    logger.i(TAG, "Successfully extracted ${extractionResult.extractedCount} native libraries")
                    logger.d(TAG, "Extracted files: ${extractionResult.extractedFiles.joinToString(", ")}")
                    
                    // FileExtractionUtils already handles ZIP extraction, so we just use the results
                    val allExtractedFiles = extractionResult.extractedFiles.toMutableList()
                    
                    extractionResult.extractedFiles.forEach { fileName ->
                        logger.d(TAG, "Extracted file: $fileName")
                    }
                    
                    // Copy dependencies from usr/lib to the root directory for easier loading
                    copyDependenciesToRoot()
                    
                    setNativeLibraryPath(privateLibDir.absolutePath)
                    
                    ExtractionResult(
                        success = true,
                        extractedFiles = allExtractedFiles,
                        extractionPath = privateLibDir.absolutePath
                    )
                }
                is com.tamersarioglu.clipcatch.util.ExtractionResult.PartialSuccess -> {
                    logger.w(TAG, "Partially extracted native libraries: ${extractionResult.extractedCount} success, ${extractionResult.failedCount} failed")
                    logger.d(TAG, "Extracted files: ${extractionResult.extractedFiles.joinToString(", ")}")
                    logger.d(TAG, "Failed files: ${extractionResult.errors.joinToString(", ")}")
                    setNativeLibraryPath(privateLibDir.absolutePath)
                    
                    ExtractionResult(
                        success = extractionResult.extractedCount > 0,
                        extractedFiles = extractionResult.extractedFiles,
                        failedFiles = extractionResult.errors,
                        extractionPath = privateLibDir.absolutePath
                    )
                }
                is com.tamersarioglu.clipcatch.util.ExtractionResult.Failure -> {
                    logger.e(TAG, "Failed to extract native libraries: ${extractionResult.error}")
                    logger.e(TAG, "Failure cause: ${extractionResult.cause}")
                    
                    val error = InitializationError.NativeLibraryError(
                        "Failed to extract native libraries: ${extractionResult.error}",
                        extractionResult.cause
                    )
                    
                    // Process error through error handler
                    val handlingResult = errorHandler.handleError(error)
                    logger.d(TAG, "Error handling result: $handlingResult")
                    
                    ExtractionResult(
                        success = false,
                        error = error
                    )
                }
            }
            
            logger.exit(TAG, "extractNativeLibraries", result)
            result
            
        } catch (e: Exception) {
            val errorMsg = "Unexpected error during native library extraction: ${e.message}"
            logger.e(TAG, errorMsg, e)
            
            val error = InitializationError.NativeLibraryError(errorMsg, e)
            
            // Process error through error handler
            val handlingResult = errorHandler.handleError(error)
            logger.d(TAG, "Error handling result: $handlingResult")
            
            ExtractionResult(
                success = false,
                error = error
            )
        }
    }

    override suspend fun loadNativeLibraries(): LoadResult = withContext(Dispatchers.IO) {
        logger.enter(TAG, "loadNativeLibraries")
        
        try {
            if (!privateLibDir.exists()) {
                val errorMsg = "Private native library directory does not exist: ${privateLibDir.absolutePath}"
                logger.e(TAG, errorMsg)
                
                val error = InitializationError.NativeLibraryError(errorMsg)
                
                // Process error through error handler
                val handlingResult = errorHandler.handleError(error)
                logger.d(TAG, "Error handling result: $handlingResult")
                
                return@withContext LoadResult(
                    success = false,
                    error = error
                )
            }
            
            val files = privateLibDir.listFiles() ?: emptyArray()
            logger.d(TAG, "Found ${files.size} files in private native library directory")
            
            val loadedLibraries = mutableListOf<String>()
            val failedLibraries = mutableListOf<String>()
            
            // Define dependency libraries that should be loaded first
            val dependencyLibraries = listOf("libz.so.1", "libc++_shared.so", "libandroid-support.so")
            
            // First, load dependency libraries
            val dependencyFiles = files.filter { file ->
                file.name.endsWith(".so") && !file.name.endsWith(".zip.so") && 
                dependencyLibraries.any { dep -> file.name.contains(dep) }
            }
            
            logger.d(TAG, "Loading ${dependencyFiles.size} dependency libraries first")
            dependencyFiles.forEach { file ->
                logger.d(TAG, "Loading dependency: ${file.name}")
                try {
                    System.load(file.absolutePath)
                    loadedLibraries.add(file.name)
                    logger.d(TAG, "Successfully loaded dependency: ${file.name}")
                } catch (e: UnsatisfiedLinkError) {
                    failedLibraries.add(file.name)
                    logger.w(TAG, "Could not load dependency ${file.name}: ${e.message}")
                } catch (e: Exception) {
                    failedLibraries.add(file.name)
                    logger.w(TAG, "Unexpected error loading dependency ${file.name}", e)
                }
            }
            
            // Then, load the main libraries
            val mainLibraries = files.filter { file ->
                file.name.endsWith(".so") && !file.name.endsWith(".zip.so") && 
                !dependencyLibraries.any { dep -> file.name.contains(dep) }
            }
            
            logger.d(TAG, "Loading ${mainLibraries.size} main libraries")
            mainLibraries.forEach { file ->
                logger.d(TAG, "Loading main library: ${file.name}")
                try {
                    System.load(file.absolutePath)
                    loadedLibraries.add(file.name)
                    logger.d(TAG, "Successfully loaded main library: ${file.name}")
                } catch (e: UnsatisfiedLinkError) {
                    failedLibraries.add(file.name)
                    logger.w(TAG, "Could not load main library ${file.name}: ${e.message}")
                } catch (e: Exception) {
                    failedLibraries.add(file.name)
                    logger.w(TAG, "Unexpected error loading main library ${file.name}", e)
                }
            }
            
            // Skip ZIP archives
            files.filter { it.name.endsWith(".zip.so") }.forEach { file ->
                logger.d(TAG, "Skipping .zip.so archive (should be extracted first): ${file.name}")
            }
            
            val success = loadedLibraries.isNotEmpty()
            val error = if (!success && failedLibraries.isNotEmpty()) {
                val initError = InitializationError.NativeLibraryError(
                    "Failed to load any native libraries. Failed libraries: ${failedLibraries.joinToString(", ")}"
                )
                
                // Process error through error handler
                val handlingResult = errorHandler.handleError(initError)
                logger.d(TAG, "Error handling result: $handlingResult")
                
                initError
            } else null
            
            val result = LoadResult(
                success = success,
                loadedLibraries = loadedLibraries,
                failedLibraries = failedLibraries,
                libraryPath = privateLibDir.absolutePath,
                error = error
            )
            
            logger.i(TAG, "Library loading completed: ${loadedLibraries.size} loaded, ${failedLibraries.size} failed")
            logger.exit(TAG, "loadNativeLibraries", result)
            result
            
        } catch (e: Exception) {
            val errorMsg = "Unexpected error during native library loading: ${e.message}"
            logger.e(TAG, errorMsg, e)
            
            val error = InitializationError.NativeLibraryError(errorMsg, e)
            
            // Process error through error handler
            val handlingResult = errorHandler.handleError(error)
            logger.d(TAG, "Error handling result: $handlingResult")
            
            LoadResult(
                success = false,
                error = error
            )
        }
    }

    override suspend fun verifyNativeLibraries(): VerificationResult = withContext(Dispatchers.IO) {
        logger.enter(TAG, "verifyNativeLibraries")
        
        try {
            logger.d(TAG, "Verifying native library extraction and accessibility")
            
            if (!privateLibDir.exists()) {
                val errorMsg = "Private native library directory does not exist after extraction"
                logger.w(TAG, errorMsg)
                
                val error = InitializationError.NativeLibraryError(errorMsg)
                
                // Process error through error handler
                val handlingResult = errorHandler.handleError(error)
                logger.d(TAG, "Error handling result: $handlingResult")
                
                return@withContext VerificationResult(
                    success = false,
                    error = error
                )
            }
            
            val files = privateLibDir.listFiles() ?: emptyArray()
            logger.d(TAG, "Files in private native library directory: ${files.size}")
            
            val verifiedItems = mutableListOf<String>()
            val failedItems = mutableListOf<String>()
            val verificationDetails = mutableMapOf<String, String>()
            
            // Log all files for debugging
            files.forEach { file ->
                logger.d(TAG, "File: ${file.name} (${file.length()} bytes)")
                verificationDetails[file.name] = "${file.length()} bytes"
            }
            
            // Check for Python-related libraries
            val pythonLibs = files.filter { file ->
                NATIVE_LIBRARY_PATTERNS.any { pattern -> file.name.contains(pattern) }
            }
            
            if (pythonLibs.isEmpty()) {
                logger.w(TAG, "No Python-related libraries found after extraction")
                failedItems.add("Python-related libraries")
            } else {
                logger.d(TAG, "Found ${pythonLibs.size} Python-related libraries after extraction")
                pythonLibs.forEach { file ->
                    val fileType = when {
                        file.name.endsWith(".zip.so") -> "ZIP archive (not a shared library)"
                        file.name.endsWith(".so") -> "Shared library"
                        else -> "Unknown"
                    }
                    logger.d(TAG, "Python library: ${file.name} (${file.length()} bytes) - Type: $fileType")
                    verifiedItems.add(file.name)
                    verificationDetails["${file.name}_type"] = fileType
                }
            }
            
            // Verify file integrity (basic checks)
            files.forEach { file ->
                if (file.length() == 0L) {
                    logger.w(TAG, "Empty file detected: ${file.name}")
                    failedItems.add("${file.name} (empty)")
                } else if (file.canRead()) {
                    if (!verifiedItems.contains(file.name)) {
                        verifiedItems.add(file.name)
                    }
                } else {
                    logger.w(TAG, "File not readable: ${file.name}")
                    failedItems.add("${file.name} (not readable)")
                }
            }
            
            val success = verifiedItems.isNotEmpty() && pythonLibs.isNotEmpty()
            
            val error = if (!success) {
                val initError = InitializationError.NativeLibraryError(
                    "Native library verification failed. Verified: ${verifiedItems.size}, Failed: ${failedItems.size}"
                )
                
                // Process error through error handler
                val handlingResult = errorHandler.handleError(initError)
                logger.d(TAG, "Error handling result: $handlingResult")
                
                initError
            } else null
            
            val result = VerificationResult(
                success = success,
                verifiedItems = verifiedItems,
                failedItems = failedItems,
                verificationDetails = verificationDetails,
                error = error
            )
            
            logger.i(TAG, "Native library verification completed: success=$success, verified=${verifiedItems.size}, failed=${failedItems.size}")
            logger.exit(TAG, "verifyNativeLibraries", result)
            result
            
        } catch (e: Exception) {
            val errorMsg = "Error verifying native library extraction: ${e.message}"
            logger.e(TAG, errorMsg, e)
            
            val error = InitializationError.NativeLibraryError(errorMsg, e)
            
            // Process error through error handler
            val handlingResult = errorHandler.handleError(error)
            logger.d(TAG, "Error handling result: $handlingResult")
            
            VerificationResult(
                success = false,
                error = error
            )
        }
    }

    override fun shouldExtractLibraries(): Boolean {
        logger.enter(TAG, "shouldExtractLibraries")
        
        if (!privateLibDir.exists()) {
            logger.d(TAG, "Private native library directory does not exist")
            return true
        }
        
        val files = privateLibDir.listFiles()
        val pythonLibs = files?.filter { file ->
            NATIVE_LIBRARY_PATTERNS.any { pattern -> file.name.contains(pattern) }
        }
        
        // Check if we have .zip.so files that need extraction
        val zipSoFiles = files?.filter { file ->
            file.name.endsWith(".zip.so")
        }
        
        // Check if we have actual .so files (not .zip.so)
        val actualSoFiles = files?.filter { file ->
            file.name.endsWith(".so") && !file.name.endsWith(".zip.so")
        }
        
        // Check if we have the correct architecture libraries
        val architecture = getDeviceArchitecture()
        val correctArchitectureFiles = files?.filter { file ->
            file.name.endsWith(".so") && !file.name.endsWith(".zip.so") && 
            file.length() > 1000 // Basic check to ensure it's a real library, not a placeholder
        }
        
        logger.d(TAG, "Private native library directory has ${files?.size ?: 0} files, ${pythonLibs?.size ?: 0} Python-related")
        logger.d(TAG, "Found ${zipSoFiles?.size ?: 0} .zip.so files, ${actualSoFiles?.size ?: 0} actual .so files")
        logger.d(TAG, "Target architecture: $architecture")
        
        // Extract if we have .zip.so files but no actual .so files, or if no Python-related libraries found
        // Also extract if we don't have the correct architecture libraries
        val shouldExtract = (zipSoFiles?.isNotEmpty() == true && actualSoFiles.isNullOrEmpty()) || 
                           pythonLibs.isNullOrEmpty() ||
                           correctArchitectureFiles.isNullOrEmpty()
        logger.exit(TAG, "shouldExtractLibraries", shouldExtract)
        return shouldExtract
    }

    override fun getNativeLibraryDirectory(): String {
        return privateLibDir.absolutePath
    }

    override fun setNativeLibraryPath(path: String) {
        logger.enter(TAG, "setNativeLibraryPath", path)
        
        try {
            // Include both the main library path and the usr/lib subdirectory
            val usrLibPath = "$path/usr/lib"
            val combinedPath = "$path:$usrLibPath"
            
            logger.d(TAG, "Setting native library path: $combinedPath")
            System.setProperty("java.library.path", combinedPath)
            
            // Try to reload the library path
            val sysPathField = ClassLoader::class.java.getDeclaredField("sys_paths")
            sysPathField.isAccessible = true
            sysPathField.set(null, null)
            
            logger.d(TAG, "Successfully set native library path")
            
        } catch (e: Exception) {
            logger.w(TAG, "Could not set native library path", e)
        }
        
        logger.exit(TAG, "setNativeLibraryPath")
    }

    /**
     * Determines the device architecture for native library extraction.
     */
    private fun getDeviceArchitecture(): String {
        return try {
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            when {
                abi.contains("arm64") -> "arm64-v8a"
                abi.contains("arm") -> "armeabi-v7a"
                abi.contains("x86_64") -> "x86_64"
                abi.contains("x86") -> "x86"
                else -> "arm64-v8a" // Default fallback
            }
        } catch (e: Exception) {
            logger.w(TAG, "Could not determine device architecture, using default", e)
            "arm64-v8a" // Default fallback
        }
    }

    /**
     * Copies dependency libraries from usr/lib to the root directory for easier loading.
     */
    private fun copyDependenciesToRoot() {
        logger.d(TAG, "Copying dependencies from usr/lib to root directory")
        
        try {
            val usrLibDir = File(privateLibDir, "usr/lib")
            if (!usrLibDir.exists()) {
                logger.w(TAG, "usr/lib directory does not exist")
                return
            }
            
            val dependencyFiles = usrLibDir.listFiles { file ->
                file.name.endsWith(".so") && file.length() > 1000 // Skip placeholder files
            } ?: emptyArray()
            
            logger.d(TAG, "Found ${dependencyFiles.size} dependency files in usr/lib")
            
            dependencyFiles.forEach { sourceFile ->
                val targetFile = File(privateLibDir, sourceFile.name)
                if (!targetFile.exists()) {
                    try {
                        sourceFile.copyTo(targetFile, overwrite = false)
                        logger.d(TAG, "Copied dependency: ${sourceFile.name}")
                    } catch (e: Exception) {
                        logger.w(TAG, "Failed to copy dependency ${sourceFile.name}", e)
                    }
                } else {
                    logger.d(TAG, "Dependency already exists in root: ${sourceFile.name}")
                }
            }
            
        } catch (e: Exception) {
            logger.w(TAG, "Error copying dependencies", e)
        }
    }


}