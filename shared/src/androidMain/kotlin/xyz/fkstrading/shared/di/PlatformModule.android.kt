package xyz.fkstrading.shared.di

import android.content.Context
import org.koin.dsl.module
import xyz.fkstrading.shared.data.db.AndroidDatabaseDriverFactory
import xyz.fkstrading.shared.data.db.DatabaseDriverFactory

/**
 * Android-specific platform module
 * Provides Android implementations of platform-dependent dependencies
 */
fun androidPlatformModule(context: Context) =
    module {
        single<DatabaseDriverFactory> {
            AndroidDatabaseDriverFactory(context)
        }
    }
