package com.tamersarioglu.clipcatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.tamersarioglu.clipcatch.ui.screen.DownloadScreen
import com.tamersarioglu.clipcatch.ui.theme.ClipCatchTheme
import com.tamersarioglu.clipcatch.ui.viewmodel.DownloadViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    
    // Handle back navigation
    BackHandler(enabled = uiState.isDownloading) {
        // If download is in progress, show confirmation or cancel download
        viewModel.cancelDownload()
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        DownloadScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}