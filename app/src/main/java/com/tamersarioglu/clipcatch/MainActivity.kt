package com.tamersarioglu.clipcatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.tamersarioglu.clipcatch.ui.screen.DownloadScreen
import com.tamersarioglu.clipcatch.ui.theme.ClipCatchTheme
import dagger.hilt.android.AndroidEntryPoint
import android.util.Log

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClipCatchTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainContent()
                }
            }
        }
    }
}

@Composable
fun MainContent() {
    val context = LocalContext.current
    var youtubeDLStatus by remember { mutableStateOf("Checking...") }
    
    LaunchedEffect(Unit) {
        try {
            val app = context.applicationContext as ClipCatchApplication
            app.ensureYoutubeDLInitialized()
            
            // Check status periodically until initialization is complete
            while (true) {
                val status = app.getYoutubeDLStatus()
                youtubeDLStatus = status
                Log.d("MainActivity", "YouTube-DL Status: $youtubeDLStatus")
                
                if (status == "YouTube-DL Ready" || status == "YouTube-DL Failed - Check logs") {
                    break
                }
                
                // Wait a bit before checking again
                kotlinx.coroutines.delay(500)
            }
        } catch (e: Exception) {
            youtubeDLStatus = "Error: ${e.message}"
            Log.e("MainActivity", "YouTube-DL initialization error", e)
        }
    }
    
    DownloadScreen(
        viewModel = hiltViewModel(),
        youtubeDLStatus = youtubeDLStatus
    )
}