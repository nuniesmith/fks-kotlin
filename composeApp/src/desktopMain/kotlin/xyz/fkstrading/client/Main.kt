package xyz.fkstrading.client

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import xyz.fkstrading.client.di.appModule
import xyz.fkstrading.shared.di.allModules

fun main() =
    application {
        // Initialize Koin before creating window
        platformInit()

        val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "FKS Trading Pro",
        ) {
            App()
        }
    }

@Composable
actual fun isCompactScreen() = false

actual fun platformInit() {
    // Initialize Koin only once
    if (GlobalContext.getOrNull() == null) {
        startKoin {
            printLogger(Level.INFO)
            modules(allModules + appModule)
        }
        println("🖥️ Desktop platform initialized with Koin")
    }
}
