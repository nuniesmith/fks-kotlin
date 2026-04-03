package xyz.fkstrading.shared.di

/**
 * Initializes Koin for Desktop/JVM platform
 *
 * Call this from your main() function before starting the application.
 *
 * @param enableLogging Whether to enable Koin logging (default: true)
 *
 * @example
 * ```kotlin
 * fun main() {
 *     initKoin(enableLogging = true)
 *     application {
 *         // Your Compose Desktop app
 *     }
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
