package xyz.fkstrading.shared.di

/**
 * Initializes Koin for iOS platform
 *
 * Call this from your iOS app's initialization code (e.g., AppDelegate or App struct).
 *
 * @param enableLogging Whether to enable Koin logging (default: true)
 *
 * @example Swift usage:
 * ```swift
 * import Shared
 *
 * @main
 * struct FksApp: App {
 *     init() {
 *         KoinInitKt.initKoin(enableLogging: true)
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
