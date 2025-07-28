package com.tamersarioglu.clipcatch.domain.model

/**
 * Represents the result of a library loading operation.
 * @param success Whether the loading operation was successful
 * @param loadedLibraries List of libraries that were successfully loaded
 * @param failedLibraries List of libraries that failed to load
 * @param error Error information if the loading failed
 * @param libraryPath The path where libraries were loaded from
 */
data class LoadResult(
    val success: Boolean,
    val loadedLibraries: List<String> = emptyList(),
    val failedLibraries: List<String> = emptyList(),
    val error: InitializationError? = null,
    val libraryPath: String? = null
) {
    /**
     * Convenience property to check if loading was partially successful.
     */
    val isPartialSuccess: Boolean
        get() = loadedLibraries.isNotEmpty() && failedLibraries.isNotEmpty()
    
    /**
     * Total number of libraries processed during loading.
     */
    val totalLibrariesProcessed: Int
        get() = loadedLibraries.size + failedLibraries.size
}