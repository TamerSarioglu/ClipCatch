package com.tamersarioglu.clipcatch.domain.model

/**
 * Represents the result of a file extraction operation.
 * @param success Whether the extraction operation was successful
 * @param extractedFiles List of files that were successfully extracted
 * @param failedFiles List of files that failed to extract
 * @param error Error information if the extraction failed
 * @param extractionPath The path where files were extracted to
 */
data class ExtractionResult(
    val success: Boolean,
    val extractedFiles: List<String> = emptyList(),
    val failedFiles: List<String> = emptyList(),
    val error: InitializationError? = null,
    val extractionPath: String? = null
) {
    /**
     * Convenience property to check if extraction was partially successful.
     */
    val isPartialSuccess: Boolean
        get() = extractedFiles.isNotEmpty() && failedFiles.isNotEmpty()
    
    /**
     * Total number of files processed during extraction.
     */
    val totalFilesProcessed: Int
        get() = extractedFiles.size + failedFiles.size
}