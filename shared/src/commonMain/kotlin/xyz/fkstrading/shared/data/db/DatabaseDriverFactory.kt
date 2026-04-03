package xyz.fkstrading.shared.data.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Factory interface for creating platform-specific SQL drivers
 */
interface DatabaseDriverFactory {
    /**
     * Creates a platform-specific SQL driver for the FKS database
     */
    fun createDriver(): SqlDriver
}
