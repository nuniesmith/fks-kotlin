package xyz.fkstrading.shared.data.repository

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.fkstrading.shared.data.db.DatabaseWrapper
import xyz.fkstrading.shared.domain.models.Signal

/**
 * Offline-first implementation of SignalRepository
 *
 * Strategy:
 * - All reads come from local database
 * - All writes go to local database immediately
 * - Sync with remote happens in background
 * - Conflict resolution favors server data
 */
class SignalRepositoryImpl(
    private val database: DatabaseWrapper,
    private val remoteDataSource: SignalRemoteDataSource? = null,
) : SignalRepository {
    private val syncMutex = Mutex()
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    // ========================================
    // OBSERVE OPERATIONS
    // ========================================

    override fun observeSignal(signalId: String): Flow<Signal?> {
        return database.getSignalById(signalId)
    }

    override fun observeAllSignals(): Flow<List<Signal>> {
        return database.getAllSignals()
    }

    override fun observeSignalsBySymbol(symbol: String): Flow<List<Signal>> {
        return database.getSignalsBySymbol(symbol)
    }

    override fun observeRecentSignals(limit: Int): Flow<List<Signal>> {
        return database.getRecentSignals(limit.toLong())
    }

    // ========================================
    // GET OPERATIONS
    // ========================================

    override suspend fun getSignalById(signalId: String): Signal? {
        return database.getSignalById(signalId).first()
    }

    override suspend fun getAllSignals(): List<Signal> {
        return database.getAllSignals().first()
    }

    override suspend fun getSignalsBySymbol(symbol: String): List<Signal> {
        return database.getSignalsBySymbol(symbol).first()
    }

    // ========================================
    // WRITE OPERATIONS
    // ========================================

    override suspend fun saveSignal(signal: Signal) {
        // Save to local database immediately
        database.insertOrReplaceSignal(signal, isSynced = false)

        // Try to sync to remote in background if available
        if (remoteDataSource != null) {
            try {
                remoteDataSource.saveSignal(signal)
                database.markSignalAsSynced(signal.signalId)
            } catch (e: Exception) {
                // Failed to sync, will be retried during next sync operation
                println("Failed to sync signal ${signal.signalId} to remote: ${e.message}")
            }
        }
    }

    override suspend fun saveSignals(signals: List<Signal>) {
        signals.forEach { signal ->
            database.insertOrReplaceSignal(signal, isSynced = false)
        }

        // Try to sync to remote in background if available
        if (remoteDataSource != null) {
            try {
                signals.forEach { signal ->
                    remoteDataSource.saveSignal(signal)
                    database.markSignalAsSynced(signal.signalId)
                }
            } catch (e: Exception) {
                println("Failed to sync signals to remote: ${e.message}")
            }
        }
    }

    override suspend fun deleteSignal(signalId: String) {
        database.deleteSignal(signalId)

        // Try to delete from remote if available
        if (remoteDataSource != null) {
            try {
                remoteDataSource.deleteSignal(signalId)
            } catch (e: Exception) {
                println("Failed to delete signal $signalId from remote: ${e.message}")
            }
        }
    }

    override suspend fun deleteOldSignals(olderThanMillis: Long) {
        database.deleteOldSignals(olderThanMillis)
    }

    // ========================================
    // SYNC OPERATIONS
    // ========================================

    override suspend fun sync(): Boolean =
        syncMutex.withLock {
            if (remoteDataSource == null) {
                return false
            }

            return try {
                _syncStatus.value = SyncStatus.Syncing

                // Step 1: Push unsynced local changes to remote
                val unsyncedSignals = database.getUnsyncedSignals().first()
                unsyncedSignals.forEach { signal ->
                    try {
                        remoteDataSource.saveSignal(signal)
                        database.markSignalAsSynced(signal.signalId)
                    } catch (e: Exception) {
                        println("Failed to sync signal ${signal.signalId}: ${e.message}")
                    }
                }

                // Step 2: Pull latest signals from remote
                val remoteSignals = remoteDataSource.getRecentSignals(limit = 100)
                remoteSignals.forEach { signal ->
                    database.insertOrReplaceSignal(signal, isSynced = true)
                }

                _syncStatus.value = SyncStatus.Success
                true
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error(e.message ?: "Unknown error")
                println("Sync failed: ${e.message}")
                false
            } finally {
                if (_syncStatus.value is SyncStatus.Syncing) {
                    _syncStatus.value = SyncStatus.Idle
                }
            }
        }

    override suspend fun refresh() {
        if (remoteDataSource == null) {
            return
        }

        try {
            _syncStatus.value = SyncStatus.Syncing

            // Fetch latest signals from remote
            val remoteSignals = remoteDataSource.getRecentSignals(limit = 100)
            remoteSignals.forEach { signal ->
                database.insertOrReplaceSignal(signal, isSynced = true)
            }

            _syncStatus.value = SyncStatus.Success
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "Unknown error")
            println("Refresh failed: ${e.message}")
        } finally {
            if (_syncStatus.value is SyncStatus.Syncing) {
                _syncStatus.value = SyncStatus.Idle
            }
        }
    }

    // ========================================
    // CLEANUP OPERATIONS
    // ========================================

    override suspend fun clearAll() {
        // Note: This only clears local data, not remote
        database.getAllSignals().first().forEach { signal ->
            database.deleteSignal(signal.signalId)
        }
    }
}

/**
 * Interface for remote data source operations
 * This allows injecting different implementations (REST API, GraphQL, etc.)
 */
interface SignalRemoteDataSource {
    suspend fun getSignalById(signalId: String): Signal?

    suspend fun getRecentSignals(limit: Int): List<Signal>

    suspend fun getSignalsBySymbol(symbol: String): List<Signal>

    suspend fun saveSignal(signal: Signal)

    suspend fun deleteSignal(signalId: String)
}

/**
 * Sync status for the repository
 */
sealed class SyncStatus {
    object Idle : SyncStatus()

    object Syncing : SyncStatus()

    object Success : SyncStatus()

    data class Error(val message: String) : SyncStatus()
}
