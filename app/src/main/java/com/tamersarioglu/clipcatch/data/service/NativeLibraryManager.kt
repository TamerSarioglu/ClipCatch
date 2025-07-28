package com.tamersarioglu.clipcatch.data.service

import com.tamersarioglu.clipcatch.domain.model.ExtractionResult
import com.tamersarioglu.clipcatch.domain.model.LoadResult
import com.tamersarioglu.clipcatch.domain.model.VerificationResult

/**
 * Interface for managing native library extraction, loading, and verification.
 * Handles the extraction of native libraries from the APK, loading them into memory,
 * and verifying that they are properly accessible.
 */
interface NativeLibraryManager {
    
    /**
     * Extracts native libraries from the APK to the private library directory.
     * This includes Python libraries, FFmpeg libraries, SSL/crypto libraries, and other
     * native dependencies required by YouTube-DL.
     * 
     * @return ExtractionResult indicating success, failure, or partial success with details
     */
    suspend fun extractNativeLibraries(): ExtractionResult
    
    /**
     * Loads the extracted native libraries into memory using System.load().
     * This ensures that the libraries are available for use by the YouTube-DL library.
     * 
     * @return LoadResult indicating which libraries were successfully loaded and which failed
     */
    suspend fun loadNativeLibraries(): LoadResult
    
    /**
     * Verifies that native libraries have been properly extracted and are accessible.
     * Checks file existence, sizes, and basic integrity of the extracted libraries.
     * 
     * @return VerificationResult indicating which libraries passed verification
     */
    suspend fun verifyNativeLibraries(): VerificationResult
    
    /**
     * Determines whether native libraries need to be extracted from the APK.
     * Checks if the private library directory exists and contains the required libraries.
     * 
     * @return true if extraction is needed, false if libraries are already present
     */
    fun shouldExtractLibraries(): Boolean
    
    /**
     * Gets the path to the private native library directory where libraries are extracted.
     * 
     * @return File path to the native library directory
     */
    fun getNativeLibraryDirectory(): String
    
    /**
     * Sets the native library path for the system to use when loading libraries.
     * This updates the java.library.path system property and reloads the library path.
     * 
     * @param path The path to set as the native library directory
     */
    fun setNativeLibraryPath(path: String)
}