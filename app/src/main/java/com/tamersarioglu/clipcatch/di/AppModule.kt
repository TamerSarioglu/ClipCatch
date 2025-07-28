package com.tamersarioglu.clipcatch.di

import android.content.Context
import com.tamersarioglu.clipcatch.data.service.InitializationErrorHandler
import com.tamersarioglu.clipcatch.data.service.InitializationErrorHandlerImpl
import com.tamersarioglu.clipcatch.util.FileExtractionUtils
import com.tamersarioglu.clipcatch.util.FileExtractionUtilsImpl
import com.tamersarioglu.clipcatch.util.Logger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideFileExtractionUtils(
        @ApplicationContext context: Context,
        logger: Logger
    ): FileExtractionUtils = FileExtractionUtilsImpl(context, logger)

    @Provides
    @Singleton
    fun provideInitializationErrorHandler(
        logger: Logger
    ): InitializationErrorHandler = InitializationErrorHandlerImpl(logger)
}