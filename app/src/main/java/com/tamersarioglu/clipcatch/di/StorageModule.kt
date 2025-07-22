package com.tamersarioglu.clipcatch.di

import android.content.Context
import com.tamersarioglu.clipcatch.data.service.DownloadManagerService
import com.tamersarioglu.clipcatch.data.service.DownloadManagerServiceImpl
import com.tamersarioglu.clipcatch.data.service.FileManagerService
import com.tamersarioglu.clipcatch.data.service.FileManagerServiceImpl
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
        @ApplicationContext context: Context
    ): FileManagerService = FileManagerServiceImpl(context)
    
    @Provides
    @Singleton
    fun provideDownloadManagerService(
        okHttpClient: OkHttpClient,
        fileManager: FileManagerService
    ): DownloadManagerService = DownloadManagerServiceImpl(okHttpClient, fileManager)
}