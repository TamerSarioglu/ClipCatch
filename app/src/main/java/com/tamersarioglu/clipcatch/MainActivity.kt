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
import androidx.hilt.navigation.compose.hiltViewModel
import com.tamersarioglu.clipcatch.data.service.InitializationOrchestrator
import com.tamersarioglu.clipcatch.domain.model.InitializationStatus
import com.tamersarioglu.clipcatch.ui.screen.DownloadScreen
import com.tamersarioglu.clipcatch.ui.theme.ClipCatchTheme
import dagger.hilt.android.AndroidEntryPoint
import android.util.Log
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var initializationOrchestrator: InitializationOrchestrator
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClipCatchTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainContent(initializationOrchestrator)
                }
            }
        }
    }
}

@Composable
fun MainContent(initializationOrchestrator: InitializationOrchestrator) {
    var youtubeDLStatus by remember { mutableStateOf("Checking...") }
    
    LaunchedEffect(Unit) {
        try {
            // Ensure initialization is started
            initializationOrchestrator.initialize()
            
            // Check status periodically until initialization is complete
            while (true) {
                val status = initializationOrchestrator.getInitializationStatus()
                val statusMessage = when (status) {
                    is InitializationStatus.NotStarted -> "YouTube-DL Not Started"
                    is InitializationStatus.InProgress -> "YouTube-DL Initializing..."
                    is InitializationStatus.Completed -> "YouTube-DL Ready"
                    is InitializationStatus.Failed -> "YouTube-DL Failed - Check logs"
                }
                
                youtubeDLStatus = statusMessage
                Log.d("MainActivity", "YouTube-DL Status: $youtubeDLStatus")
                
                if (status is InitializationStatus.Completed || status is InitializationStatus.Failed) {
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