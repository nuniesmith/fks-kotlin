package xyz.fkstrading.shared.data.repository

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.fkstrading.shared.data.db.DatabaseWrapper
import xyz.fkstrading.shared.domain.models.Position

/**
 * Offline-first implementation of PositionRepository
 *
 * Strategy:
 * - All reads come from local database
 * - All writes go to local database immediately
 * - Sync with remote happens in background
 * - Conflict resolution favors server data
 */
class PositionRepositoryImpl(
    private val database: DatabaseWrapper,
    private val remoteDataSource: PositionRemoteDataSource? = null,
) : PositionRepository {
    private val syncMutex = Mutex()
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    // ========================================
    // OBSERVE OPERATIONS
    // ========================================

    override fun observePosition(positionId: String): Flow<Position?> {
        return database.getPositionById(positionId)
    }

    override fun observeAllPositions(): Flow<List<Position>> {
        return database.getAllPositions()
    }

    override fun observeOpenPositions(): Flow<List<Position>> {
        return database.getOpenPositions()
    }

    override fun observeClosedPositions(): Flow<List<Position>> {
        return database.getClosedPositions()
    }

    override fun observePositionsBySymbol(symbol: String): Flow<List<Position>> {
        return database.getPositionsBySymbol(symbol)
    }

    // ========================================
    // GET OPERATIONS
    // ========================================

    override suspend fun getPositionById(positionId: String): Position? {
        return database.getPositionById(positionId).first()
    }

    override suspend fun getAllPositions(): List<Position> {
        return database.getAllPositions().first()
    }

    override suspend fun getOpenPositions(): List<Position> {
        return database.getOpenPositions().first()
    }

    override suspend fun getClosedPositions(): List<Position> {
        return database.getClosedPositions().first()
    }

    override suspend fun getPositionsBySymbol(symbol: String): List<Position> {
        return database.getPositionsBySymbol(symbol).first()
    }

    // ========================================
    // WRITE OPERATIONS
    // ========================================

    override suspend fun savePosition(position: Position) {
        // Save to local database immediately
        database.insertOrReplacePosition(position, isSynced = false)

        // Try to sync to remote in background if available
        if (remoteDataSource != null) {
            try {
                remoteDataSource.savePosition(position)
                database.markPositionAsSynced(position.positionId)
            } catch (e: Exception) {
                // Failed to sync, will be retried during next sync operation
                println("Failed to sync position ${position.positionId} to remote: ${e.message}")
            }
        }
    }

    override suspend fun savePositions(positions: List<Position>) {
        positions.forEach { position ->
            database.insertOrReplacePosition(position, isSynced = false)
        }

        // Try to sync to remote in background if available
        if (remoteDataSource != null) {
            try {
                positions.forEach { position ->
                    remoteDataSource.savePosition(position)
                    database.markPositionAsSynced(position.positionId)
                }
            } catch (e: Exception) {
                println("Failed to sync positions to remote: ${e.message}")
            }
        }
    }

    override suspend fun updatePositionPrice(
        positionId: String,
        currentPrice: Double,
        unrealizedPnL: Double,
    ) {
        database.updatePositionPriceAndPnL(positionId, currentPrice, unrealizedPnL)

        // Try to sync to remote if available
        if (remoteDataSource != null) {
            try {
                remoteDataSource.updatePositionPrice(positionId, currentPrice, unrealizedPnL)
            } catch (e: Exception) {
                println("Failed to sync position price update to remote: ${e.message}")
            }
        }
    }

    override suspend fun closePosition(
        positionId: String,
        realizedPnL: Double,
    ) {
        database.closePosition(positionId, realizedPnL)

        // Try to sync to remote if available
        if (remoteDataSource != null) {
            try {
                remoteDataSource.closePosition(positionId, realizedPnL)
            } catch (e: Exception) {
                println("Failed to sync position close to remote: ${e.message}")
            }
        }
    }

    override suspend fun deletePosition(positionId: String) {
        database.deletePosition(positionId)

        // Try to delete from remote if available
        if (remoteDataSource != null) {
            try {
                remoteDataSource.deletePosition(positionId)
            } catch (e: Exception) {
                println("Failed to delete position $positionId from remote: ${e.message}")
            }
        }
    }

    override suspend fun deleteOldPositions(olderThanMillis: Long) {
        // Note: This is a local-only operation
        // We don't delete from remote to preserve history
        val allPositions = database.getAllPositions().first()
        allPositions.forEach { position ->
            if (position.openedAt.toEpochMilliseconds() < olderThanMillis) {
                database.deletePosition(position.positionId)
            }
        }
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
                val unsyncedPositions = database.getUnsyncedPositions().first()
                unsyncedPositions.forEach { position ->
                    try {
                        remoteDataSource.savePosition(position)
                        database.markPositionAsSynced(position.positionId)
                    } catch (e: Exception) {
                        println("Failed to sync position ${position.positionId}: ${e.message}")
                    }
                }

                // Step 2: Pull latest positions from remote
                val remotePositions = remoteDataSource.getRecentPositions(limit = 100)
                remotePositions.forEach { position ->
                    database.insertOrReplacePosition(position, isSynced = true)
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

            // Fetch latest positions from remote
            val remotePositions = remoteDataSource.getRecentPositions(limit = 100)
            remotePositions.forEach { position ->
                database.insertOrReplacePosition(position, isSynced = true)
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
        database.getAllPositions().first().forEach { position ->
            database.deletePosition(position.positionId)
        }
    }
}

/**
 * Interface for remote data source operations
 * This allows injecting different implementations (REST API, GraphQL, etc.)
 */
interface PositionRemoteDataSource {
    suspend fun getPositionById(positionId: String): Position?

    suspend fun getRecentPositions(limit: Int): List<Position>

    suspend fun getPositionsBySymbol(symbol: String): List<Position>

    suspend fun getOpenPositions(): List<Position>

    suspend fun getClosedPositions(): List<Position>

    suspend fun savePosition(position: Position)

    suspend fun updatePositionPrice(
        positionId: String,
        currentPrice: Double,
        unrealizedPnL: Double,
    )

    suspend fun closePosition(
        positionId: String,
        realizedPnL: Double,
    )

    suspend fun deletePosition(positionId: String)
}
