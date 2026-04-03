package xyz.fkstrading.shared.di

import org.koin.dsl.module
import xyz.fkstrading.shared.data.db.DatabaseDriverFactory
import xyz.fkstrading.shared.data.db.IosDatabaseDriverFactory

/**
 * iOS-specific platform module
 * Provides iOS implementations of platform-dependent dependencies
 */
val iosPlatformModule =
    module {
        single<DatabaseDriverFactory> {
            IosDatabaseDriverFactory()
        }
    }
