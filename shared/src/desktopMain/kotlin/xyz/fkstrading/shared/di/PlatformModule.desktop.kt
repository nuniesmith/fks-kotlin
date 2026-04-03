package xyz.fkstrading.shared.di

import org.koin.dsl.module
import xyz.fkstrading.shared.data.db.DatabaseDriverFactory
import xyz.fkstrading.shared.data.db.DesktopDatabaseDriverFactory

/**
 * Desktop-specific platform module
 * Provides Desktop implementations of platform-dependent dependencies
 */
val desktopPlatformModule =
    module {
        single<DatabaseDriverFactory> {
            DesktopDatabaseDriverFactory()
        }
    }
