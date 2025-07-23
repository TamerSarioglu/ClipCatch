# ClipCatch

A modern Android YouTube video downloader app built with Jetpack Compose and Clean Architecture.

## Features

- Download YouTube videos directly to your device
- Support for all YouTube URL formats (youtube.com, youtu.be, shorts, mobile, embed, live)
- Real-time URL validation and video information extraction
- Progress tracking with visual indicators
- Modern Material 3 UI with responsive design
- Comprehensive error handling and recovery
- Scoped storage support for Android 10+

## Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: Clean Architecture (Domain, Data, Presentation layers)
- **Dependency Injection**: Hilt
- **Async Programming**: Coroutines & Flow
- **Video Extraction**: YouTube-DL Android library
- **Networking**: Retrofit + OkHttp
- **Serialization**: Kotlinx Serialization

## YouTube-DL Integration

This app uses the [youtubedl-android](https://github.com/yausername/youtubedl-android) library for video extraction and downloading. The library is automatically initialized on app startup and updated in the background.
