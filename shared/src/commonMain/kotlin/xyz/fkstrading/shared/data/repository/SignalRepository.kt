package xyz.fkstrading.shared.data.repository

import kotlinx.coroutines.flow.Flow
import xyz.fkstrading.shared.domain.models.Signal

/**
 * Repository interface for managing trading signals
 * Provides offline-first data access with automatic sync
 */
interface SignalRepository {
    /**
     * Observes a signal by ID
     * Returns a Flow that emits the signal whenever it changes
     */
    fun observeSignal(signalId: String): Flow<Signal?>

    /**
     * Observes all signals
     * Returns a Flow that emits the list of signals whenever it changes
     */
    fun observeAllSignals(): Flow<List<Signal>>

    /**
     * Observes signals for a specific symbol
     */
    fun observeSignalsBySymbol(symbol: String): Flow<List<Signal>>

    /**
     * Observes recent signals (limited number)
     */
    fun observeRecentSignals(limit: Int = 50): Flow<List<Signal>>

    /**
     * Gets a signal by ID (one-time fetch)
     */
    suspend fun getSignalById(signalId: String): Signal?

    /**
     * Gets all signals (one-time fetch)
     */
    suspend fun getAllSignals(): List<Signal>

    /**
     * Gets signals for a specific symbol (one-time fetch)
     */
    suspend fun getSignalsBySymbol(symbol: String): List<Signal>

    /**
     * Saves a signal to local database
     * If offline, marks for sync when connection is restored
     */
    suspend fun saveSignal(signal: Signal)

    /**
     * Saves multiple signals in batch
     */
    suspend fun saveSignals(signals: List<Signal>)

    /**
     * Deletes a signal by ID
     */
    suspend fun deleteSignal(signalId: String)

    /**
     * Deletes old signals older than the specified timestamp
     */
    suspend fun deleteOldSignals(olderThanMillis: Long)

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
     * Clears all local signals
     */
    suspend fun clearAll()
}
