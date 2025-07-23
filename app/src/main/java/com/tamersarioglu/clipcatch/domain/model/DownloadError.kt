package com.tamersarioglu.clipcatch.domain.model

enum class DownloadError {
    INVALID_URL,
    NETWORK_ERROR,
    STORAGE_ERROR,
    PERMISSION_DENIED,
    VIDEO_UNAVAILABLE,
    INSUFFICIENT_STORAGE,
    AGE_RESTRICTED,
    GEO_BLOCKED,
    UNKNOWN_ERROR
}