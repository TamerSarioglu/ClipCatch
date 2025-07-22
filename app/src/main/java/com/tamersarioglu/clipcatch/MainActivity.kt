package com.tamersarioglu.clipcatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.tamersarioglu.clipcatch.ui.screen.DownloadScreen
import com.tamersarioglu.clipcatch.ui.theme.ClipCatchTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClipCatchTheme {
                DownloadScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}