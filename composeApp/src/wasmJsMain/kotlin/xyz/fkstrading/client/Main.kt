package xyz.fkstrading.client

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        platformInit()
        App()
    }
}

actual fun isCompactScreen(): Boolean {
    return kotlinx.browser.window.innerWidth < 600
}

actual fun platformInit() {
    kotlinx.browser.document.title = "FKS Trading Terminal"
    println("🌐 Running on Web (WebAssembly)")
}
