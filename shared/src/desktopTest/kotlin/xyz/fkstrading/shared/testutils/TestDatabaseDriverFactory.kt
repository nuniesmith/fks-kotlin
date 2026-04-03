package xyz.fkstrading.shared.testutils

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import xyz.fkstrading.shared.data.db.DatabaseDriverFactory
import xyz.fkstrading.shared.data.db.FksDatabase

/**
 * Test-only database driver factory for creating in-memory databases.
 * This is JVM-only and should only be used in desktop/JVM tests.
 *
 * For platform-specific tests on iOS/Android, use platform-specific test helpers.
 */
object TestDatabaseDriverFactory {
    /**
     * Creates an in-memory SQLite database driver for testing.
     * The database schema is automatically created.
     *
     * @return An in-memory SqlDriver with the FKS schema initialized
     */
    fun createInMemoryDriver(): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FksDatabase.Schema.create(driver)
        return driver
    }

    /**
     * Creates a DatabaseDriverFactory that returns an in-memory driver.
     * Useful for injecting into components that expect a DatabaseDriverFactory.
     *
     * @return A DatabaseDriverFactory that creates in-memory drivers
     */
    fun createInMemoryDriverFactory(): DatabaseDriverFactory {
        return object : DatabaseDriverFactory {
            override fun createDriver(): SqlDriver = createInMemoryDriver()
        }
    }
}
