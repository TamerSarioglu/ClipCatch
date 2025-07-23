package com.tamersarioglu.clipcatch

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.tamersarioglu.clipcatch.ui.screen.DownloadScreen
import com.tamersarioglu.clipcatch.ui.theme.ClipCatchTheme
import com.tamersarioglu.clipcatch.ui.viewmodel.DownloadViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        enableEdgeToEdge()
        
        // Configure window for better immersive experience
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            ClipCatchTheme {
                MainContent()
            }
        }
    }
}

@Composable
private fun MainContent() {
    val viewModel: DownloadViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    
    // Handle back navigation
    BackHandler(enabled = uiState.isDownloading) {
        // If download is in progress, cancel download
        viewModel.cancelDownload()
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            // Responsive layout based on screen size
            when {
                // Tablet landscape or large screens
                configuration.screenWidthDp >= 840 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        DownloadScreen(
                            modifier = Modifier
                                .widthIn(max = 600.dp)
                                .fillMaxSize()
                        )
                    }
                }
                // Phone landscape
                configuration.orientation == Configuration.ORIENTATION_LANDSCAPE -> {
                    DownloadScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 32.dp)
                    )
                }
                // Phone portrait (default)
                else -> {
                    DownloadScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
            }
        }
    }
}