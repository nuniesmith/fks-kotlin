package xyz.fkstrading.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * Main Activity for Android
 *
 * Entry point for the Android application.
 * Koin is initialized in FksApplication.onCreate()
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        platformInit()

        setContent {
            App()
        }
    }
}
