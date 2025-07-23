package com.tamersarioglu.clipcatch

import android.content.res.Configuration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tamersarioglu.clipcatch.ui.theme.ClipCatchTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    
    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Before
    fun setUp() {
        hiltRule.inject()
    }
    
    @Test
    fun mainActivity_displaysAppTitle() {
        composeTestRule.setContent {
            ClipCatchTheme {
            }
        }
        composeTestRule.onNodeWithText("ClipCatch").assertExists()
        composeTestRule.onNodeWithText("Download YouTube videos directly to your device").assertExists()
    }
    
    @Test
    fun mainActivity_displaysUrlInputField() {
        composeTestRule.setContent {
            ClipCatchTheme {
            }
        }
        composeTestRule.onNodeWithContentDescription("YouTube URL input field").assertExists()
    }
    
    @Test
    fun mainActivity_displaysDownloadButton() {
        composeTestRule.setContent {
            ClipCatchTheme {
            }
        }
        composeTestRule.onNodeWithContentDescription("Download video button").assertExists()
    }
}