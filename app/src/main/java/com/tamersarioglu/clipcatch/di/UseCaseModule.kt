package com.tamersarioglu.clipcatch.di

import com.tamersarioglu.clipcatch.domain.repository.VideoDownloadRepository
import com.tamersarioglu.clipcatch.domain.usecase.DownloadVideoUseCase
import com.tamersarioglu.clipcatch.domain.usecase.GetVideoInfoUseCase
import com.tamersarioglu.clipcatch.domain.usecase.ValidateUrlUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object UseCaseModule {
    
    @Provides
    @ViewModelScoped
    fun provideDownloadVideoUseCase(
        repository: VideoDownloadRepository
    ): DownloadVideoUseCase = DownloadVideoUseCase(repository)
    
    @Provides
    @ViewModelScoped
    fun provideValidateUrlUseCase(
        repository: VideoDownloadRepository
    ): ValidateUrlUseCase = ValidateUrlUseCase(repository)
    
    @Provides
    @ViewModelScoped
    fun provideGetVideoInfoUseCase(
        repository: VideoDownloadRepository
    ): GetVideoInfoUseCase = GetVideoInfoUseCase(repository)
}