package xyz.fkstrading.shared.data.repository

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import xyz.fkstrading.shared.data.db.DatabaseWrapper
import xyz.fkstrading.shared.domain.models.StrategyConfig
import xyz.fkstrading.shared.domain.models.default
import xyz.fkstrading.shared.domain.models.validate
import xyz.fkstrading.shared.domain.models.withActiveStatus
import xyz.fkstrading.shared.domain.models.withDefaultStatus
import xyz.fkstrading.shared.domain.models.withUpdatedTimestamp

/**
 * Offline-first implementation of StrategyConfigRepository.
 *
 * Strategy:
 * - All reads come from local database
 * - All writes go to local database immediately
 * - Sync with remote happens in background
 * - Conflict resolution favors server data
 */
class StrategyConfigRepositoryImpl(
    private val database: DatabaseWrapper,
) : StrategyConfigRepository {
    private val syncMutex = Mutex()
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    // ========================================
    // OBSERVE OPERATIONS
    // ========================================

    override fun observeConfig(configId: String): Flow<StrategyConfig?> {
        return database.getStrategyConfigById(configId)
    }

    override fun observeAllConfigs(): Flow<List<StrategyConfig>> {
        return database.getAllStrategyConfigs()
    }

    override fun observeActiveConfigs(): Flow<List<StrategyConfig>> {
        return database.getActiveStrategyConfigs()
    }

    override fun observeDefaultConfig(): Flow<StrategyConfig?> {
        return database.getDefaultStrategyConfig()
    }

    // ========================================
    // GET OPERATIONS
    // ========================================

    override suspend fun getConfigById(configId: String): StrategyConfig? {
        return database.getStrategyConfigById(configId).first()
    }

    override suspend fun getAllConfigs(): List<StrategyConfig> {
        return database.getAllStrategyConfigs().first()
    }

    override suspend fun getActiveConfigs(): List<StrategyConfig> {
        return database.getActiveStrategyConfigs().first()
    }

    override suspend fun getDefaultConfig(): StrategyConfig? {
        return database.getDefaultStrategyConfig().first()
    }

    override suspend fun getConfigByName(name: String): StrategyConfig? {
        return database.getStrategyConfigByName(name).first()
    }

    // ========================================
    // WRITE OPERATIONS
    // ========================================

    override suspend fun saveConfig(config: StrategyConfig): Result<StrategyConfig> {
        return try {
            // Validate config before saving
            val validationErrors = config.validate()
            if (validationErrors.isNotEmpty()) {
                return Result.failure(
                    IllegalArgumentException("Invalid configuration: ${validationErrors.joinToString(", ")}"),
                )
            }

            // Update timestamp
            val updatedConfig = config.withUpdatedTimestamp(Clock.System.now())

            // Save to local database immediately
            database.insertOrReplaceStrategyConfig(updatedConfig, isSynced = false)

            Result.success(updatedConfig)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveConfigs(configs: List<StrategyConfig>): Result<List<StrategyConfig>> {
        return try {
            // Validate all configs
            val validationErrors =
                configs.flatMap { config ->
                    config.validate().map { "${config.name}: $it" }
                }
            if (validationErrors.isNotEmpty()) {
                return Result.failure(
                    IllegalArgumentException("Invalid configurations: ${validationErrors.joinToString(", ")}"),
                )
            }

            val timestamp = Clock.System.now()
            val updatedConfigs = configs.map { it.withUpdatedTimestamp(timestamp) }

            // Save all configs to database
            updatedConfigs.forEach { config ->
                database.insertOrReplaceStrategyConfig(config, isSynced = false)
            }

            Result.success(updatedConfigs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setAsDefault(configId: String): Result<StrategyConfig> {
        return try {
            val config =
                getConfigById(configId)
                    ?: return Result.failure(IllegalArgumentException("Config not found: $configId"))

            val timestamp = Clock.System.now()

            // Clear all default flags
            database.clearStrategyConfigDefaultFlags(timestamp.toEpochMilliseconds())

            // Set this config as default
            database.setStrategyConfigAsDefault(configId, timestamp.toEpochMilliseconds())

            // Return updated config
            val updatedConfig = config.withDefaultStatus(true).withUpdatedTimestamp(timestamp)
            Result.success(updatedConfig)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateActiveStatus(
        configId: String,
        isActive: Boolean,
    ): Result<StrategyConfig> {
        return try {
            val config =
                getConfigById(configId)
                    ?: return Result.failure(IllegalArgumentException("Config not found: $configId"))

            val timestamp = Clock.System.now()

            // Update active status in database
            database.updateStrategyConfigActiveStatus(
                configId = configId,
                isActive = isActive,
                timestamp = timestamp.toEpochMilliseconds(),
            )

            // Return updated config
            val updatedConfig = config.withActiveStatus(isActive).withUpdatedTimestamp(timestamp)
            Result.success(updatedConfig)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteConfig(configId: String): Result<Unit> {
        return try {
            database.deleteStrategyConfigById(configId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAllConfigs(): Result<Unit> {
        return try {
            database.deleteAllStrategyConfigs()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========================================
    // SEARCH OPERATIONS
    // ========================================

    override suspend fun searchConfigsByName(pattern: String): List<StrategyConfig> {
        return database.searchStrategyConfigsByName(pattern).first()
    }

    // ========================================
    // COUNT OPERATIONS
    // ========================================

    override suspend fun getConfigCount(): Long {
        return database.countAllStrategyConfigs().first()
    }

    override suspend fun getActiveConfigCount(): Long {
        return database.countActiveStrategyConfigs().first()
    }

    override suspend fun getUnsyncedConfigCount(): Long {
        return database.countUnsyncedStrategyConfigs().first()
    }

    // ========================================
    // SYNC OPERATIONS
    // ========================================

    override suspend fun syncConfigs(): Result<Unit> =
        syncMutex.withLock {
            return try {
                _syncStatus.value = SyncStatus.Syncing

                // Note: Remote sync not yet implemented
                // This would upload unsynced configs and download remote changes

                _syncStatus.value = SyncStatus.Success
                Result.success(Unit)
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error(e.message ?: "Sync failed")
                Result.failure(e)
            }
        }

    // ========================================
    // HELPER METHODS
    // ========================================

    /**
     * Ensures a default config exists. If none exists, creates one.
     */
    override suspend fun ensureDefaultConfigExists(): StrategyConfig {
        val existing = getDefaultConfig()
        if (existing != null) {
            return existing
        }

        // Create a default config with isDefault = true
        val defaultConfig =
            StrategyConfig.default(
                configId = "default-${Clock.System.now().toEpochMilliseconds()}",
                timestamp = Clock.System.now(),
            ).copy(isDefault = true)

        saveConfig(defaultConfig)
        return defaultConfig
    }
}
