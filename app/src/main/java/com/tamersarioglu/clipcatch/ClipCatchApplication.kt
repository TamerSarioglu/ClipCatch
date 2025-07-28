package com.tamersarioglu.clipcatch

import android.app.Application
import com.tamersarioglu.clipcatch.data.service.InitializationOrchestrator

import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import javax.inject.Inject

@HiltAndroidApp
class ClipCatchApplication : Application() {

    @Inject
    lateinit var initializationOrchestrator: InitializationOrchestrator

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            initializationOrchestrator.initialize()
        }
    }
}