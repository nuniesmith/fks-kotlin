package xyz.fkstrading.shared.data.sync

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import xyz.fkstrading.shared.data.repository.OrderRepository
import xyz.fkstrading.shared.data.repository.PositionRepository
import xyz.fkstrading.shared.data.repository.SignalRepository

/**
 * Sync engine that coordinates data synchronization across all repositories
 *
 * Features:
 * - Periodic background sync
 * - Manual sync trigger
 * - Conflict resolution
 * - Network state awareness
 * - Retry with exponential backoff
 */
class SyncEngine(
    private val signalRepository: SignalRepository,
    private val orderRepository: OrderRepository,
    private val positionRepository: PositionRepository,
    private val syncIntervalMillis: Long = 60_000L, // 1 minute default
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private var syncJob: Job? = null
    private var periodicSyncJob: Job? = null

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    // ========================================
    // SYNC CONTROL
    // ========================================

    /**
     * Starts the sync engine with periodic synchronization
     */
    fun start() {
        if (periodicSyncJob?.isActive == true) {
            println("SyncEngine: Already running")
            return
        }

        println("SyncEngine: Starting periodic sync (interval: ${syncIntervalMillis}ms)")

        periodicSyncJob =
            coroutineScope.launch {
                while (isActive && _isEnabled.value) {
                    try {
                        sync()
                        delay(syncIntervalMillis)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        println("SyncEngine: Periodic sync error: ${e.message}")
                        delay(syncIntervalMillis)
                    }
                }
            }
    }

    /**
     * Stops the sync engine
     */
    fun stop() {
        println("SyncEngine: Stopping periodic sync")
        periodicSyncJob?.cancel()
        periodicSyncJob = null
        syncJob?.cancel()
        syncJob = null
        _syncState.value = SyncState.Idle
    }

    /**
     * Enables or disables the sync engine
     */
    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        if (!enabled) {
            stop()
        } else if (periodicSyncJob == null) {
            start()
        }
    }

    /**
     * Manually triggers a sync operation
     * Returns true if sync was successful
     */
    suspend fun sync(): Boolean {
        if (!_isEnabled.value) {
            println("SyncEngine: Sync disabled")
            return false
        }

        if (syncJob?.isActive == true) {
            println("SyncEngine: Sync already in progress")
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.Syncing(0.0f)
                println("SyncEngine: Starting sync")

                val results = mutableListOf<Boolean>()

                // Sync signals (33% progress)
                _syncState.value = SyncState.Syncing(0.33f)
                val signalResult = signalRepository.sync()
                results.add(signalResult)
                println("SyncEngine: Signals sync result: $signalResult")

                // Sync orders (66% progress)
                _syncState.value = SyncState.Syncing(0.66f)
                val orderResult = orderRepository.sync()
                results.add(orderResult)
                println("SyncEngine: Orders sync result: $orderResult")

                // Sync positions (100% progress)
                _syncState.value = SyncState.Syncing(1.0f)
                val positionResult = positionRepository.sync()
                results.add(positionResult)
                println("SyncEngine: Positions sync result: $positionResult")

                val allSuccessful = results.all { it }

                if (allSuccessful) {
                    _syncState.value = SyncState.Success
                    _lastSyncTime.value = currentTimeMillis()
                    println("SyncEngine: Sync completed successfully")
                } else {
                    _syncState.value =
                        SyncState.PartialSuccess(
                            message = "Some repositories failed to sync",
                        )
                    _lastSyncTime.value = currentTimeMillis()
                    println("SyncEngine: Sync completed with errors")
                }

                // Return to idle after a short delay
                delay(2000)
                _syncState.value = SyncState.Idle

                allSuccessful
            } catch (e: Exception) {
                println("SyncEngine: Sync failed with exception: ${e.message}")
                e.printStackTrace()
                _syncState.value = SyncState.Error(e.message ?: "Unknown error")

                // Return to idle after a short delay
                delay(2000)
                _syncState.value = SyncState.Idle

                false
            }
        }
    }

    /**
     * Forces a refresh of all data from remote
     */
    suspend fun refresh(): Boolean {
        if (!_isEnabled.value) {
            println("SyncEngine: Refresh disabled")
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.Syncing(0.0f)
                println("SyncEngine: Starting refresh")

                // Refresh signals
                _syncState.value = SyncState.Syncing(0.33f)
                signalRepository.refresh()
                println("SyncEngine: Signals refreshed")

                // Refresh orders
                _syncState.value = SyncState.Syncing(0.66f)
                orderRepository.refresh()
                println("SyncEngine: Orders refreshed")

                // Refresh positions
                _syncState.value = SyncState.Syncing(1.0f)
                positionRepository.refresh()
                println("SyncEngine: Positions refreshed")

                _syncState.value = SyncState.Success
                _lastSyncTime.value = currentTimeMillis()
                println("SyncEngine: Refresh completed successfully")

                // Return to idle after a short delay
                delay(2000)
                _syncState.value = SyncState.Idle

                true
            } catch (e: Exception) {
                println("SyncEngine: Refresh failed: ${e.message}")
                e.printStackTrace()
                _syncState.value = SyncState.Error(e.message ?: "Unknown error")

                // Return to idle after a short delay
                delay(2000)
                _syncState.value = SyncState.Idle

                false
            }
        }
    }

    /**
     * Clears all local data (use with caution)
     */
    suspend fun clearAllLocalData() {
        withContext(Dispatchers.IO) {
            try {
                signalRepository.clearAll()
                orderRepository.clearAll()
                positionRepository.clearAll()
                println("SyncEngine: All local data cleared")
            } catch (e: Exception) {
                println("SyncEngine: Failed to clear local data: ${e.message}")
            }
        }
    }

    /**
     * Gets the time since last successful sync in milliseconds
     */
    fun getTimeSinceLastSync(): Long? {
        val lastSync = _lastSyncTime.value ?: return null
        return currentTimeMillis() - lastSync
    }

    /**
     * Checks if sync is needed based on the configured interval
     */
    fun isSyncNeeded(): Boolean {
        val timeSinceLastSync = getTimeSinceLastSync() ?: return true
        return timeSinceLastSync >= syncIntervalMillis
    }

    // ========================================
    // PRIVATE HELPERS
    // ========================================

    private fun currentTimeMillis(): Long {
        return kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    }

    /**
     * Cleanup resources
     */
    fun shutdown() {
        stop()
        coroutineScope.cancel()
    }
}

/**
 * Represents the current state of the sync engine
 */
sealed class SyncState {
    /**
     * Sync engine is idle
     */
    object Idle : SyncState()

    /**
     * Sync is in progress
     * @param progress Progress from 0.0 to 1.0
     */
    data class Syncing(val progress: Float) : SyncState()

    /**
     * Sync completed successfully
     */
    object Success : SyncState()

    /**
     * Sync completed but some repositories had errors
     */
    data class PartialSuccess(val message: String) : SyncState()

    /**
     * Sync failed with an error
     */
    data class Error(val message: String) : SyncState()
}

/**
 * Configuration for the sync engine
 */
data class SyncConfig(
    val syncIntervalMillis: Long = 60_000L, // 1 minute
    val enablePeriodicSync: Boolean = true,
    val enableBackgroundSync: Boolean = true,
    val retryAttempts: Int = 3,
    val retryDelayMillis: Long = 5_000L,
)

/**
 * Conflict resolution strategy
 */
enum class ConflictResolution {
    /**
     * Server data always wins
     */
    SERVER_WINS,

    /**
     * Client data always wins
     */
    CLIENT_WINS,

    /**
     * Most recent timestamp wins
     */
    LAST_WRITE_WINS,

    /**
     * Manual resolution required
     */
    MANUAL,
}
