package xyz.fkstrading.client

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    platformInit()
    return ComposeUIViewController { App() }
}

@Composable
actual fun isCompactScreen() = true // Always compact on iPhone

actual fun platformInit() {
    println("📱 Running on iOS")
}
