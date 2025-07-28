package com.tamersarioglu.clipcatch.data.service

import com.tamersarioglu.clipcatch.domain.model.ExtractionResult
import com.tamersarioglu.clipcatch.domain.model.SetupResult
import com.tamersarioglu.clipcatch.domain.model.VerificationResult
import java.io.File

/**
 * Interface for managing Python environment setup and file extraction.
 * Handles the creation of Python directories, extraction of Python files from the APK,
 * and verification that the Python environment is properly configured for YouTube-DL.
 */
interface PythonEnvironmentManager {
    
    /**
     * Sets up the complete Python environment including directory creation and file extraction.
     * This is the main entry point that orchestrates all Python environment setup tasks.
     * 
     * @return SetupResult indicating the overall success of the Python environment setup
     */
    suspend fun setupPythonEnvironment(): SetupResult
    
    /**
     * Extracts Python files from the APK to the Python directory.
     * This includes Python scripts, compiled Python files (.pyc), Python archives (.zip),
     * and yt-dlp related files required by YouTube-DL.
     * 
     * @return ExtractionResult indicating success, failure, or partial success with details
     */
    suspend fun extractPythonFiles(): ExtractionResult
    
    /**
     * Verifies that the Python environment has been properly set up.
     * Checks directory existence, required files presence, and basic integrity.
     * 
     * @return VerificationResult indicating which components passed verification
     */
    suspend fun verifyPythonEnvironment(): VerificationResult
    
    /**
     * Gets the Python directory where Python files are stored.
     * Creates the directory if it doesn't exist.
     * 
     * @return File object representing the Python directory
     */
    fun getPythonDirectory(): File
    
    /**
     * Determines whether Python files need to be extracted from the APK.
     * Checks if the Python directory exists and contains the required files.
     * 
     * @return true if extraction is needed, false if Python files are already present
     */
    fun shouldExtractPythonFiles(): Boolean
    
    /**
     * Ensures that the Python directory exists and is properly configured.
     * Creates the directory structure if it doesn't exist.
     * 
     * @return true if the directory exists or was successfully created, false otherwise
     */
    fun ensurePythonDirectoryExists(): Boolean
    
    /**
     * Gets the list of required Python files that should be present in the environment.
     * Used for verification and extraction filtering.
     * 
     * @return List of required Python file patterns or names
     */
    fun getRequiredPythonFiles(): List<String>
}