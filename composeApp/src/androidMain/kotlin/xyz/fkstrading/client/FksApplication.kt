package xyz.fkstrading.client

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import xyz.fkstrading.client.di.appModule
import xyz.fkstrading.shared.di.allModules

/**
 * FKS Trading Application
 *
 * Main application class for Android.
 * Initializes Koin dependency injection with shared and app modules.
 */
class FksApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Koin DI
        startKoin {
            androidLogger(Level.INFO)
            androidContext(this@FksApplication)
            modules(allModules + appModule)
        }

        println("📱 Android platform initialized with Koin")
    }
}
