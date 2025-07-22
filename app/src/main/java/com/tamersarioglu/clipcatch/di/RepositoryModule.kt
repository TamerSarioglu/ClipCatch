package com.tamersarioglu.clipcatch.di

import com.tamersarioglu.clipcatch.data.datasource.DownloadDataSource
import com.tamersarioglu.clipcatch.data.datasource.DownloadDataSourceImpl
import com.tamersarioglu.clipcatch.data.datasource.YouTubeDataSource
import com.tamersarioglu.clipcatch.data.datasource.YouTubeDataSourceImpl
import com.tamersarioglu.clipcatch.data.repository.VideoDownloadRepositoryImpl
import com.tamersarioglu.clipcatch.domain.repository.VideoDownloadRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindVideoDownloadRepository(
        videoDownloadRepositoryImpl: VideoDownloadRepositoryImpl
    ): VideoDownloadRepository
    
    @Binds
    @Singleton
    abstract fun bindYouTubeDataSource(
        youTubeDataSourceImpl: YouTubeDataSourceImpl
    ): YouTubeDataSource
    
    @Binds
    @Singleton
    abstract fun bindDownloadDataSource(
        downloadDataSourceImpl: DownloadDataSourceImpl
    ): DownloadDataSource
}