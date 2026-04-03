package xyz.fkstrading.shared.data.repository

import kotlinx.coroutines.flow.Flow
import xyz.fkstrading.shared.domain.models.Position

/**
 * Repository interface for managing trading positions
 * Provides offline-first data access with automatic sync
 */
interface PositionRepository {
    /**
     * Observes a position by ID
     * Returns a Flow that emits the position whenever it changes
     */
    fun observePosition(positionId: String): Flow<Position?>

    /**
     * Observes all positions
     * Returns a Flow that emits the list of positions whenever it changes
     */
    fun observeAllPositions(): Flow<List<Position>>

    /**
     * Observes open positions only
     */
    fun observeOpenPositions(): Flow<List<Position>>

    /**
     * Observes closed positions only
     */
    fun observeClosedPositions(): Flow<List<Position>>

    /**
     * Observes positions for a specific symbol
     */
    fun observePositionsBySymbol(symbol: String): Flow<List<Position>>

    /**
     * Gets a position by ID (one-time fetch)
     */
    suspend fun getPositionById(positionId: String): Position?

    /**
     * Gets all positions (one-time fetch)
     */
    suspend fun getAllPositions(): List<Position>

    /**
     * Gets open positions (one-time fetch)
     */
    suspend fun getOpenPositions(): List<Position>

    /**
     * Gets closed positions (one-time fetch)
     */
    suspend fun getClosedPositions(): List<Position>

    /**
     * Gets positions for a specific symbol (one-time fetch)
     */
    suspend fun getPositionsBySymbol(symbol: String): List<Position>

    /**
     * Saves a position to local database
     * If offline, marks for sync when connection is restored
     */
    suspend fun savePosition(position: Position)

    /**
     * Saves multiple positions in batch
     */
    suspend fun savePositions(positions: List<Position>)

    /**
     * Updates position price and unrealized P&L
     */
    suspend fun updatePositionPrice(
        positionId: String,
        currentPrice: Double,
        unrealizedPnL: Double,
    )

    /**
     * Closes a position with realized P&L
     */
    suspend fun closePosition(
        positionId: String,
        realizedPnL: Double,
    )

    /**
     * Deletes a position by ID
     */
    suspend fun deletePosition(positionId: String)

    /**
     * Deletes old positions older than the specified timestamp
     */
    suspend fun deleteOldPositions(olderThanMillis: Long)

    /**
     * Syncs local changes with remote server
     * Returns true if sync was successful
     */
    suspend fun sync(): Boolean

    /**
     * Forces a refresh from remote server
     */
    suspend fun refresh()

    /**
     * Clears all local positions
     */
    suspend fun clearAll()
}
