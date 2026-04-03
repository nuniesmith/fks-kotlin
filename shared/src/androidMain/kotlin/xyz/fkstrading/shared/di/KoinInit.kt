package xyz.fkstrading.shared.di

import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.logger.Level

/**
 * Initializes Koin for Android platform
 *
 * Call this from your Application class's onCreate() method.
 *
 * @param context Android application context
 * @param enableLogging Whether to enable Koin logging (default: true for debug builds)
 *
 * @example
 * ```kotlin
 * class FksApplication : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         initKoin(this, BuildConfig.DEBUG)
 *     }
 * }
 * ```
 */
fun initKoin(
    context: Context,
    enableLogging: Boolean = true,
) {
    xyz.fkstrading.shared.di.initKoin {
        androidContext(context)
        if (enableLogging) {
            androidLogger(Level.INFO)
        }
    }
}
