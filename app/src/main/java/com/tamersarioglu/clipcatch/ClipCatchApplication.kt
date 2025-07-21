package com.tamersarioglu.clipcatch

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ClipCatchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}