package xyz.fkstrading.shared.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

/**
 * iOS implementation of DatabaseDriverFactory
 */
class IosDatabaseDriverFactory : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver {
        return NativeSqliteDriver(
            schema = FksDatabase.Schema,
            name = "fks.db",
        )
    }
}

/**
 * Factory function for creating database driver on iOS
 */
fun createDatabaseDriver(): SqlDriver {
    return IosDatabaseDriverFactory().createDriver()
}
