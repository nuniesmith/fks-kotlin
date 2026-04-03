package xyz.fkstrading.shared.data.repository

import kotlinx.coroutines.flow.Flow
import xyz.fkstrading.shared.domain.models.StrategyConfig

/**
 * Repository interface for managing strategy configurations.
 * Provides offline-first data access with automatic sync.
 */
interface StrategyConfigRepository {
    /**
     * Observes a strategy config by ID.
     * Returns a Flow that emits the config whenever it changes.
     */
    fun observeConfig(configId: String): Flow<StrategyConfig?>

    /**
     * Observes all strategy configs.
     * Returns a Flow that emits the list of configs whenever it changes.
     */
    fun observeAllConfigs(): Flow<List<StrategyConfig>>

    /**
     * Observes active strategy configs.
     * Returns a Flow that emits the list of active configs whenever it changes.
     */
    fun observeActiveConfigs(): Flow<List<StrategyConfig>>

    /**
     * Observes the default strategy config.
     * Returns a Flow that emits the default config whenever it changes.
     */
    fun observeDefaultConfig(): Flow<StrategyConfig?>

    /**
     * Gets a strategy config by ID (one-time fetch).
     */
    suspend fun getConfigById(configId: String): StrategyConfig?

    /**
     * Gets all strategy configs (one-time fetch).
     */
    suspend fun getAllConfigs(): List<StrategyConfig>

    /**
     * Gets active strategy configs (one-time fetch).
     */
    suspend fun getActiveConfigs(): List<StrategyConfig>

    /**
     * Gets the default strategy config (one-time fetch).
     */
    suspend fun getDefaultConfig(): StrategyConfig?

    /**
     * Gets a strategy config by name (one-time fetch).
     */
    suspend fun getConfigByName(name: String): StrategyConfig?

    /**
     * Saves a strategy config to local database.
     * If offline, marks for sync when connection is restored.
     */
    suspend fun saveConfig(config: StrategyConfig): Result<StrategyConfig>

    /**
     * Saves multiple strategy configs in a batch operation.
     */
    suspend fun saveConfigs(configs: List<StrategyConfig>): Result<List<StrategyConfig>>

    /**
     * Sets a strategy config as the default.
     * Clears the default flag from all other configs.
     */
    suspend fun setAsDefault(configId: String): Result<StrategyConfig>

    /**
     * Updates the active status of a strategy config.
     */
    suspend fun updateActiveStatus(
        configId: String,
        isActive: Boolean,
    ): Result<StrategyConfig>

    /**
     * Deletes a strategy config by ID.
     */
    suspend fun deleteConfig(configId: String): Result<Unit>

    /**
     * Deletes all strategy configs.
     * WARNING: This operation cannot be undone.
     */
    suspend fun deleteAllConfigs(): Result<Unit>

    /**
     * Searches for strategy configs by name pattern.
     */
    suspend fun searchConfigsByName(pattern: String): List<StrategyConfig>

    /**
     * Synchronizes local strategy configs with remote server.
     * Uploads unsynced configs and downloads remote changes.
     */
    suspend fun syncConfigs(): Result<Unit>

    /**
     * Gets the count of all strategy configs.
     */
    suspend fun getConfigCount(): Long

    /**
     * Gets the count of active strategy configs.
     */
    suspend fun getActiveConfigCount(): Long

    /**
     * Gets the count of unsynced strategy configs.
     */
    suspend fun getUnsyncedConfigCount(): Long

    /**
     * Ensures a default config exists. If none exists, creates one.
     * This is useful for initialization to guarantee at least one config is available.
     *
     * @return The default config (existing or newly created)
     */
    suspend fun ensureDefaultConfigExists(): StrategyConfig
}
