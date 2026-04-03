package xyz.fkstrading.client

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
actual fun isCompactScreen(): Boolean {
    return LocalConfiguration.current.screenWidthDp < 600
}

actual fun platformInit() {
    // Koin is initialized in FksApplication.onCreate()
    println("📱 Android platform ready")
}
