package xyz.fkstrading.shared.data.api

import xyz.fkstrading.shared.data.repository.PositionRemoteDataSource
import xyz.fkstrading.shared.domain.models.Position

/**
 * REST API implementation of PositionRemoteDataSource
 *
 * Provides position data operations using HTTP endpoints.
 * This implementation uses FksApiClient for network communication.
 */
class PositionApiDataSource(
    private val apiClient: FksApiClient,
) : PositionRemoteDataSource {
    override suspend fun getPositionById(positionId: String): Position? {
        return apiClient.getPositionById(positionId)
            .getOrNull()
    }

    override suspend fun getRecentPositions(limit: Int): List<Position> {
        return apiClient.getRecentPositions(limit)
            .getOrElse { emptyList() }
    }

    override suspend fun getPositionsBySymbol(symbol: String): List<Position> {
        return apiClient.getPositionsBySymbol(symbol)
            .getOrElse { emptyList() }
    }

    override suspend fun getOpenPositions(): List<Position> {
        return apiClient.getOpenPositions()
            .getOrElse { emptyList() }
    }

    override suspend fun getClosedPositions(): List<Position> {
        return apiClient.getClosedPositions()
            .getOrElse { emptyList() }
    }

    override suspend fun savePosition(position: Position) {
        val result = apiClient.savePosition(position)
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: Exception("Failed to save position")
        }
    }

    override suspend fun updatePositionPrice(
        positionId: String,
        currentPrice: Double,
        unrealizedPnL: Double,
    ) {
        // Get the position first
        val position =
            getPositionById(positionId)
                ?: throw Exception("Position not found: $positionId")

        // Update the position with new price and P&L
        val updatedPosition =
            position.copy(
                currentPrice = currentPrice,
                unrealizedPnL = unrealizedPnL,
                updatedAt = kotlinx.datetime.Clock.System.now(),
            )

        // Save the updated position
        savePosition(updatedPosition)
    }

    override suspend fun closePosition(
        positionId: String,
        realizedPnL: Double,
    ) {
        val result = apiClient.closePosition(positionId)
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: Exception("Failed to close position")
        }
    }

    override suspend fun deletePosition(positionId: String) {
        val result = apiClient.deletePosition(positionId)
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: Exception("Failed to delete position")
        }
    }
}
