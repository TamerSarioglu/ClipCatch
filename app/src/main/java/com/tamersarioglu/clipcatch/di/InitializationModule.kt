package com.tamersarioglu.clipcatch.di

import com.tamersarioglu.clipcatch.data.service.InitializationOrchestrator
import com.tamersarioglu.clipcatch.data.service.InitializationOrchestratorImpl
import com.tamersarioglu.clipcatch.data.service.NativeLibraryManager
import com.tamersarioglu.clipcatch.data.service.NativeLibraryManagerImpl
import com.tamersarioglu.clipcatch.data.service.PythonEnvironmentManager
import com.tamersarioglu.clipcatch.data.service.PythonEnvironmentManagerImpl
import com.tamersarioglu.clipcatch.data.service.YouTubeDLInitializationService
import com.tamersarioglu.clipcatch.data.service.YouTubeDLInitializationServiceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing initialization-related services.
 * Binds all service interfaces to their implementations with proper singleton scoping.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class InitializationModule {

    /**
     * Binds InitializationOrchestrator interface to its implementation.
     * The orchestrator coordinates all initialization services.
     */
    @Binds
    @Singleton
    abstract fun bindInitializationOrchestrator(
        impl: InitializationOrchestratorImpl
    ): InitializationOrchestrator

    /**
     * Binds NativeLibraryManager interface to its implementation.
     * Manages native library extraction, loading, and verification.
     */
    @Binds
    @Singleton
    abstract fun bindNativeLibraryManager(
        impl: NativeLibraryManagerImpl
    ): NativeLibraryManager

    /**
     * Binds PythonEnvironmentManager interface to its implementation.
     * Manages Python environment setup and file extraction.
     */
    @Binds
    @Singleton
    abstract fun bindPythonEnvironmentManager(
        impl: PythonEnvironmentManagerImpl
    ): PythonEnvironmentManager

    /**
     * Binds YouTubeDLInitializationService interface to its implementation.
     * Handles all YouTube-DL specific initialization logic.
     */
    @Binds
    @Singleton
    abstract fun bindYouTubeDLInitializationService(
        impl: YouTubeDLInitializationServiceImpl
    ): YouTubeDLInitializationService
}