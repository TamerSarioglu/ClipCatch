package com.tamersarioglu.clipcatch.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class DownloadProgressDto {

    @Serializable
    @SerialName("progress")
    data class Progress(
        @SerialName("percentage")
        val percentage: Int
    ) : DownloadProgressDto()

    @Serializable
    @SerialName("success")
    data class Success(
        @SerialName("file_path")
        val filePath: String
    ) : DownloadProgressDto()

    @Serializable
    @SerialName("error")
    data class Error(
        @SerialName("error_type")
        val errorType: String,

        @SerialName("message")
        val message: String
    ) : DownloadProgressDto()
}