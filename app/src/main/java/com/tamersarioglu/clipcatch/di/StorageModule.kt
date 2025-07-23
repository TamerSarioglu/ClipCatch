package com.tamersarioglu.clipcatch.di

import android.content.Context
import com.tamersarioglu.clipcatch.data.service.DownloadManagerService
import com.tamersarioglu.clipcatch.data.service.DownloadManagerServiceImpl
import com.tamersarioglu.clipcatch.data.service.FileManagerService
import com.tamersarioglu.clipcatch.data.service.FileManagerServiceImpl
import com.tamersarioglu.clipcatch.data.service.YouTubeExtractorService
import com.tamersarioglu.clipcatch.data.service.YouTubeExtractorServiceImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    
    @Provides
    @Singleton
    fun provideFileManagerService(
        @ApplicationContext context: Context,
        logger: com.tamersarioglu.clipcatch.data.util.Logger
    ): FileManagerService = FileManagerServiceImpl(context, logger)
    
    @Provides
    @Singleton
    fun provideDownloadManagerService(
        okHttpClient: OkHttpClient,
        fileManager: FileManagerService,
        errorHandler: com.tamersarioglu.clipcatch.data.util.ErrorHandler,
        logger: com.tamersarioglu.clipcatch.data.util.Logger,
        networkUtils: com.tamersarioglu.clipcatch.data.util.NetworkUtils,
        retryUtils: com.tamersarioglu.clipcatch.data.util.RetryUtils
    ): DownloadManagerService = DownloadManagerServiceImpl(okHttpClient, fileManager, errorHandler, logger, networkUtils, retryUtils)
    
    @Provides
    @Singleton
    fun provideYouTubeExtractorService(
        @ApplicationContext context: Context
    ): YouTubeExtractorService = YouTubeExtractorServiceImpl(context)
}