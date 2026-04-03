package xyz.fkstrading.shared.di

/**
 * Initializes Koin for WebAssembly/JS platform
 *
 * Call this from your web application's initialization code before using any dependencies.
 *
 * @param enableLogging Whether to enable Koin logging (default: true)
 *
 * @example
 * ```kotlin
 * fun main() {
 *     initKoin(enableLogging = true)
 *     // Start your Compose for Web app
 * }
 * ```
 */
fun initKoin(enableLogging: Boolean = true) {
    xyz.fkstrading.shared.di.initKoin {
        if (enableLogging) {
            printLogger(org.koin.core.logger.Level.INFO)
        }
    }
}
