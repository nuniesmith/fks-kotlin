package xyz.fkstrading.shared.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

/**
 * Desktop implementation of DatabaseDriverFactory using JDBC SQLite driver
 */
class DesktopDatabaseDriverFactory(
    private val databasePath: String = getDefaultDatabasePath(),
) : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver {
        // Ensure the database directory exists
        val dbFile = File(databasePath)
        dbFile.parentFile?.mkdirs()

        // Create the JDBC SQLite driver
        val driver = JdbcSqliteDriver("jdbc:sqlite:$databasePath")

        // Create the database schema if it doesn't exist
        FksDatabase.Schema.create(driver)

        return driver
    }

    companion object {
        /**
         * Gets the default database path for desktop platforms
         * Uses the user's home directory under .fks/data
         */
        private fun getDefaultDatabasePath(): String {
            val userHome = System.getProperty("user.home")
            val appDir = File(userHome, ".fks/data")
            appDir.mkdirs()
            return File(appDir, "fks.db").absolutePath
        }

        /**
         * Creates an in-memory database driver for testing
         */
        fun createInMemoryDriver(): SqlDriver {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            FksDatabase.Schema.create(driver)
            return driver
        }
    }
}
