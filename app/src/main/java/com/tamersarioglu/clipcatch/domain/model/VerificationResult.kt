package com.tamersarioglu.clipcatch.domain.model

/**
 * Represents the result of a verification operation.
 * @param success Whether the verification was successful
 * @param verifiedItems List of items that passed verification
 * @param failedItems List of items that failed verification
 * @param error Error information if the verification failed
 * @param verificationDetails Additional details about the verification process
 */
data class VerificationResult(
    val success: Boolean,
    val verifiedItems: List<String> = emptyList(),
    val failedItems: List<String> = emptyList(),
    val error: InitializationError? = null,
    val verificationDetails: Map<String, String> = emptyMap()
) {
    /**
     * Convenience property to check if verification was partially successful.
     */
    val isPartialSuccess: Boolean
        get() = verifiedItems.isNotEmpty() && failedItems.isNotEmpty()
    
    /**
     * Total number of items processed during verification.
     */
    val totalItemsProcessed: Int
        get() = verifiedItems.size + failedItems.size
}