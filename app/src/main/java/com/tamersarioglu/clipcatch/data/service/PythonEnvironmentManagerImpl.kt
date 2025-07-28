package com.tamersarioglu.clipcatch.data.service

import android.content.Context
import com.tamersarioglu.clipcatch.domain.model.ExtractionResult
import com.tamersarioglu.clipcatch.domain.model.InitializationError
import com.tamersarioglu.clipcatch.domain.model.SetupResult
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
 * Implementation of PythonEnvironmentManager that handles Python environment setup and file extraction.
 * Extracted from the original ClipCatchApplication to provide reusable Python environment management.
 */
@Singleton
class PythonEnvironmentManagerImpl @Inject constructor(
    private val context: Context,
    private val fileExtractionUtils: FileExtractionUtils,
    private val errorHandler: InitializationErrorHandler,
    private val logger: Logger
) : PythonEnvironmentManager {

    companion object {
        private const val TAG = "PythonEnvironmentManager"
        private const val PYTHON_DIR_NAME = "python"
        
        // Required Python file patterns for verification
        private val REQUIRED_PYTHON_PATTERNS = listOf(
            "python"
        )
        
        // Python file extensions and patterns to extract
        private val PYTHON_FILE_PATTERNS = listOf(
            "python",
            "libpython"
        )
    }

    override suspend fun setupPythonEnvironment(): SetupResult = withContext(Dispatchers.IO) {
        logger.enter(TAG, "setupPythonEnvironment")
        
        val setupSteps = mutableListOf<String>()
        val failedSteps = mutableListOf<String>()
        var setupPath: String? = null
        var error: InitializationError? = null
        
        try {
            // Step 1: Ensure Python directory exists
            logger.d(TAG, "Step 1: Ensuring Python directory exists")
            if (ensurePythonDirectoryExists()) {
                setupSteps.add("Python directory creation")
                setupPath = getPythonDirectory().absolutePath
                logger.d(TAG, "Python directory ensured at: $setupPath")
            } else {
                failedSteps.add("Python directory creation")
                logger.e(TAG, "Failed to ensure Python directory exists")
            }
            
            // Step 2: Extract Python files if needed
            logger.d(TAG, "Step 2: Extracting Python files")
            if (shouldExtractPythonFiles()) {
                logger.d(TAG, "Python files extraction needed")
                val extractionResult = extractPythonFiles()
                
                if (extractionResult.success) {
                    setupSteps.add("Python files extraction")
                    logger.d(TAG, "Python files extracted successfully: ${extractionResult.extractedFiles.size} files")
                } else {
                    failedSteps.add("Python files extraction")
                    error = extractionResult.error
                    logger.e(TAG, "Python files extraction failed", extractionResult.error?.cause)
                }
            } else {
                setupSteps.add("Python files verification (already present)")
                logger.d(TAG, "Python files already present, skipping extraction")
            }
            
            // Step 3: Verify Python environment
            logger.d(TAG, "Step 3: Verifying Python environment")
            val verificationResult = verifyPythonEnvironment()
            
            if (verificationResult.success) {
                setupSteps.add("Python environment verification")
                logger.d(TAG, "Python environment verification successful")
            } else {
                failedSteps.add("Python environment verification")
                if (error == null) {
                    error = verificationResult.error
                }
                logger.e(TAG, "Python environment verification failed", verificationResult.error?.cause)
            }
            
            val success = failedSteps.isEmpty()
            val configurationDetails = mapOf(
                "pythonDirectory" to (setupPath ?: ""),
                "extractedFiles" to setupSteps.size.toString(),
                "failedSteps" to failedSteps.size.toString()
            )
            
            val result = SetupResult(
                success = success,
                setupSteps = setupSteps,
                failedSteps = failedSteps,
                error = error,
                setupPath = setupPath,
                configurationDetails = configurationDetails
            )
            
            logger.exit(TAG, "setupPythonEnvironment", result)
            result
            
        } catch (e: Exception) {
            val initError = InitializationError.PythonEnvironmentError(
                "Python environment setup failed: ${e.message}",
                e
            )
            
            // Process error through error handler
            val handlingResult = errorHandler.handleError(initError)
            logger.d(TAG, "Error handling result: $handlingResult")
            
            val result = SetupResult(
                success = false,
                setupSteps = setupSteps,
                failedSteps = failedSteps + "Python environment setup",
                error = initError,
                setupPath = setupPath
            )
            
            logger.e(TAG, "Python environment setup failed", e)
            logger.exit(TAG, "setupPythonEnvironment", result)
            result
        }
    }

    override suspend fun extractPythonFiles(): ExtractionResult = withContext(Dispatchers.IO) {
        logger.enter(TAG, "extractPythonFiles")
        
        try {
            val pythonDir = getPythonDirectory()
            logger.d(TAG, "Extracting Python files to: ${pythonDir.absolutePath}")
            
            // Use FileExtractionUtils to extract Python files from APK
            val utilsResult = fileExtractionUtils.extractFromAPK(
                sourcePattern = "",  // Search entire APK
                targetDirectory = pythonDir,
                filter = { entry -> isPythonFile(entry) }
            )
            
            // Convert FileExtractionUtils.ExtractionResult to domain ExtractionResult
            val result = convertExtractionResult(utilsResult, pythonDir.absolutePath)
            
            logger.d(TAG, "Python files extraction completed: ${result.extractedFiles.size} files extracted")
            logger.exit(TAG, "extractPythonFiles", result)
            result
            
        } catch (e: Exception) {
            val error = InitializationError.PythonEnvironmentError(
                "Failed to extract Python files: ${e.message}",
                e
            )
            
            // Process error through error handler
            val handlingResult = errorHandler.handleError(error)
            logger.d(TAG, "Error handling result: $handlingResult")
            
            val result = ExtractionResult(
                success = false,
                error = error,
                extractionPath = getPythonDirectory().absolutePath
            )
            
            logger.e(TAG, "Python files extraction failed", e)
            logger.exit(TAG, "extractPythonFiles", result)
            result
        }
    }

    override suspend fun verifyPythonEnvironment(): VerificationResult = withContext(Dispatchers.IO) {
        logger.enter(TAG, "verifyPythonEnvironment")
        
        val verifiedItems = mutableListOf<String>()
        val failedItems = mutableListOf<String>()
        val verificationDetails = mutableMapOf<String, String>()
        var error: InitializationError? = null
        
        try {
            val pythonDir = getPythonDirectory()
            
            // Verify Python directory exists
            if (pythonDir.exists() && pythonDir.isDirectory) {
                verifiedItems.add("Python directory exists")
                verificationDetails["pythonDirectory"] = pythonDir.absolutePath
                logger.d(TAG, "Python directory verified: ${pythonDir.absolutePath}")
            } else {
                failedItems.add("Python directory exists")
                logger.e(TAG, "Python directory does not exist or is not a directory")
            }
            
            // Verify Python directory is readable
            if (pythonDir.canRead()) {
                verifiedItems.add("Python directory readable")
                logger.d(TAG, "Python directory is readable")
            } else {
                failedItems.add("Python directory readable")
                logger.e(TAG, "Python directory is not readable")
            }
            
            // Check Python files
            val pythonFiles = pythonDir.listFiles() ?: emptyArray()
            verificationDetails["fileCount"] = pythonFiles.size.toString()
            logger.d(TAG, "Python directory contains ${pythonFiles.size} files")
            
            if (pythonFiles.isNotEmpty()) {
                verifiedItems.add("Python files present")
                
                // Verify specific required files/patterns
                val requiredFiles = getRequiredPythonFiles()
                var requiredFilesFound = 0
                
                for (pattern in requiredFiles) {
                    val matchingFiles = pythonFiles.filter { file ->
                        file.name.contains(pattern, ignoreCase = true)
                    }
                    
                    if (matchingFiles.isNotEmpty()) {
                        verifiedItems.add("Required pattern: $pattern")
                        verificationDetails[pattern] = matchingFiles.joinToString(", ") { it.name }
                        logger.d(TAG, "Found files matching pattern '$pattern': ${matchingFiles.map { it.name }}")
                        requiredFilesFound++
                    } else {
                        logger.w(TAG, "No files found matching required pattern: $pattern")
                    }
                }
                
                // Also check for libpython.zip.so as it's a valid Python library
                val hasLibPython = pythonFiles.any { file ->
                    file.name.contains("libpython", ignoreCase = true)
                }
                
                if (hasLibPython) {
                    verifiedItems.add("Python library")
                    verificationDetails["pythonLibrary"] = pythonFiles.filter { it.name.contains("libpython", ignoreCase = true) }.joinToString(", ") { it.name }
                    logger.d(TAG, "Found Python library files")
                    requiredFilesFound++
                }
                
                // Consider verification successful if we have at least one required file or Python library
                if (requiredFilesFound == 0) {
                    failedItems.add("Required Python files")
                    logger.w(TAG, "No required Python files found")
                } else {
                    verifiedItems.add("Required Python files")
                    logger.d(TAG, "Found $requiredFilesFound required Python files")
                }
                
                // Log all Python files for debugging
                pythonFiles.forEach { file ->
                    logger.d(TAG, "Python file: ${file.name} (${file.length()} bytes)")
                }
                
            } else {
                failedItems.add("Python files present")
                logger.w(TAG, "Python directory is empty")
            }
            
            val success = failedItems.isEmpty()
            
            if (!success && error == null) {
                error = InitializationError.PythonEnvironmentError(
                    "Python environment verification failed: ${failedItems.joinToString(", ")}",
                    null
                )
                
                // Process error through error handler
                val handlingResult = errorHandler.handleError(error)
                logger.d(TAG, "Error handling result: $handlingResult")
            }
            
            val result = VerificationResult(
                success = success,
                verifiedItems = verifiedItems,
                failedItems = failedItems,
                error = error,
                verificationDetails = verificationDetails
            )
            
            logger.exit(TAG, "verifyPythonEnvironment", result)
            result
            
        } catch (e: Exception) {
            val initError = InitializationError.PythonEnvironmentError(
                "Python environment verification failed: ${e.message}",
                e
            )
            
            // Process error through error handler
            val handlingResult = errorHandler.handleError(initError)
            logger.d(TAG, "Error handling result: $handlingResult")
            
            val result = VerificationResult(
                success = false,
                verifiedItems = verifiedItems,
                failedItems = failedItems + "Python environment verification",
                error = initError,
                verificationDetails = verificationDetails
            )
            
            logger.e(TAG, "Python environment verification failed", e)
            logger.exit(TAG, "verifyPythonEnvironment", result)
            result
        }
    }

    override fun getPythonDirectory(): File {
        return File(context.filesDir, PYTHON_DIR_NAME)
    }

    override fun shouldExtractPythonFiles(): Boolean {
        logger.enter(TAG, "shouldExtractPythonFiles")
        
        val pythonDir = getPythonDirectory()
        
        if (!pythonDir.exists()) {
            logger.d(TAG, "Python directory does not exist, extraction needed")
            logger.exit(TAG, "shouldExtractPythonFiles", true)
            return true
        }
        
        val pythonFiles = pythonDir.listFiles()
        
        // Check if we have any Python-related files
        val hasPythonFiles = pythonFiles?.any { file ->
            file.name.contains("python", ignoreCase = true) || file.name.contains("libpython", ignoreCase = true)
        } ?: false
        
        val shouldExtract = pythonFiles.isNullOrEmpty() || !hasPythonFiles
        
        logger.d(TAG, "Python directory has ${pythonFiles?.size ?: 0} files, has Python files: $hasPythonFiles, extraction needed: $shouldExtract")
        logger.exit(TAG, "shouldExtractPythonFiles", shouldExtract)
        
        return shouldExtract
    }

    override fun ensurePythonDirectoryExists(): Boolean {
        logger.enter(TAG, "ensurePythonDirectoryExists")
        
        try {
            val pythonDir = getPythonDirectory()
            
            if (pythonDir.exists()) {
                logger.d(TAG, "Python directory already exists: ${pythonDir.absolutePath}")
                logger.exit(TAG, "ensurePythonDirectoryExists", true)
                return true
            }
            
            val created = pythonDir.mkdirs()
            logger.d(TAG, "Python directory creation result: $created - ${pythonDir.absolutePath}")
            
            logger.exit(TAG, "ensurePythonDirectoryExists", created)
            return created
            
        } catch (e: Exception) {
            logger.e(TAG, "Error ensuring Python directory exists", e)
            logger.exit(TAG, "ensurePythonDirectoryExists", false)
            return false
        }
    }

    override fun getRequiredPythonFiles(): List<String> {
        return REQUIRED_PYTHON_PATTERNS
    }

    /**
     * Determines if a ZIP entry represents a Python file that should be extracted.
     * Based on the original logic from ClipCatchApplication.extractPythonFilesFromAPK.
     */
    private fun isPythonFile(entry: ZipEntry): Boolean {
        val name = entry.name
        
        // Must contain "python" or "libpython" in the path
        if (!name.contains("python", ignoreCase = true) && !name.contains("libpython", ignoreCase = true)) {
            return false
        }
        
        // Check for specific Python file patterns
        return PYTHON_FILE_PATTERNS.any { pattern ->
            name.contains(pattern, ignoreCase = true)
        }
    }

    /**
     * Converts FileExtractionUtils.ExtractionResult to domain model ExtractionResult.
     */
    private fun convertExtractionResult(
        utilsResult: com.tamersarioglu.clipcatch.util.ExtractionResult,
        extractionPath: String
    ): ExtractionResult {
        return when (utilsResult) {
            is com.tamersarioglu.clipcatch.util.ExtractionResult.Success -> {
                ExtractionResult(
                    success = true,
                    extractedFiles = utilsResult.extractedFiles,
                    extractionPath = extractionPath
                )
            }
            is com.tamersarioglu.clipcatch.util.ExtractionResult.Failure -> {
                val error = InitializationError.PythonEnvironmentError(
                    utilsResult.error,
                    utilsResult.cause
                )
                
                // Process error through error handler
                val handlingResult = errorHandler.handleError(error)
                logger.d(TAG, "Error handling result: $handlingResult")
                
                ExtractionResult(
                    success = false,
                    error = error,
                    extractionPath = extractionPath
                )
            }
            is com.tamersarioglu.clipcatch.util.ExtractionResult.PartialSuccess -> {
                val error = if (utilsResult.errors.isNotEmpty()) {
                    val initError = InitializationError.PythonEnvironmentError(
                        "Partial extraction failure: ${utilsResult.errors.joinToString("; ")}",
                        null
                    )
                    
                    // Process error through error handler
                    val handlingResult = errorHandler.handleError(initError)
                    logger.d(TAG, "Error handling result: $handlingResult")
                    
                    initError
                } else null
                
                ExtractionResult(
                    success = utilsResult.extractedCount > 0,
                    extractedFiles = utilsResult.extractedFiles,
                    failedFiles = utilsResult.errors,
                    error = error,
                    extractionPath = extractionPath
                )
            }
        }
    }
}