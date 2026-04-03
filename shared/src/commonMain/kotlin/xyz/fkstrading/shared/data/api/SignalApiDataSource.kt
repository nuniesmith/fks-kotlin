package xyz.fkstrading.shared.data.api

import xyz.fkstrading.shared.data.repository.SignalRemoteDataSource
import xyz.fkstrading.shared.domain.models.Signal

/**
 * REST API implementation of SignalRemoteDataSource
 *
 * Provides signal data operations using HTTP endpoints.
 * This implementation uses FksApiClient for network communication.
 */
class SignalApiDataSource(
    private val apiClient: FksApiClient,
) : SignalRemoteDataSource {
    override suspend fun getSignalById(signalId: String): Signal? {
        return apiClient.getSignalById(signalId)
            .getOrNull()
    }

    override suspend fun getRecentSignals(limit: Int): List<Signal> {
        return apiClient.getRecentSignals(limit)
            .getOrElse { emptyList() }
    }

    override suspend fun getSignalsBySymbol(symbol: String): List<Signal> {
        return apiClient.getSignalsBySymbol(symbol)
            .getOrElse { emptyList() }
    }

    override suspend fun saveSignal(signal: Signal) {
        val result = apiClient.saveSignal(signal)
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: Exception("Failed to save signal")
        }
    }

    override suspend fun deleteSignal(signalId: String) {
        val result = apiClient.deleteSignal(signalId)
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: Exception("Failed to delete signal")
        }
    }
}
