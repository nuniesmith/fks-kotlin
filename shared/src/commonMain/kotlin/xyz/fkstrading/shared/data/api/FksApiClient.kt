package xyz.fkstrading.shared.data.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import xyz.fkstrading.shared.domain.models.*

/**
 * REST API client for FKS backend
 *
 * Provides HTTP endpoints for CRUD operations on signals, orders, and positions.
 * Complements WebSocket real-time data with traditional REST API.
 *
 * Usage:
 * ```kotlin
 * val apiClient = FksApiClient(
 *     baseUrl = "http://localhost:8000",
 *     httpClient = httpClient
 * )
 *
 * // Fetch signals
 * val signals = apiClient.getRecentSignals(limit = 50)
 *
 * // Create order
 * val order = apiClient.createOrder(orderRequest)
 * ```
 */
class FksApiClient(
    private val baseUrl: String = "http://localhost:8000",
    private val httpClient: HttpClient = createDefaultHttpClient(),
    private val authToken: String? = null,
) {
    companion object {
        /**
         * Creates a default HTTP client with JSON serialization and logging
         */
        fun createDefaultHttpClient(): HttpClient {
            return HttpClient {
                install(ContentNegotiation) {
                    json(
                        Json {
                            prettyPrint = true
                            isLenient = true
                            ignoreUnknownKeys = true
                            encodeDefaults = true
                        },
                    )
                }

                install(Logging) {
                    logger = Logger.DEFAULT
                    level = LogLevel.INFO
                }

                install(HttpTimeout) {
                    requestTimeoutMillis = 30_000
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 30_000
                }

                // Retry on network failures
                install(HttpRequestRetry) {
                    retryOnServerErrors(maxRetries = 3)
                    exponentialDelay()
                }

                defaultRequest {
                    contentType(ContentType.Application.Json)
                }
            }
        }
    }

    // ========================================
    // SIGNAL ENDPOINTS
    // ========================================

    /**
     * Get recent signals
     */
    suspend fun getRecentSignals(limit: Int = 100): Result<List<Signal>> {
        return executeRequest {
            httpClient.get("$baseUrl/api/signals") {
                parameter("limit", limit)
                setAuthHeader()
            }.body()
        }
    }

    /**
     * Get signal by ID
     */
    suspend fun getSignalById(signalId: String): Result<Signal> {
        return executeRequest {
            httpClient.get("$baseUrl/api/signals/$signalId") {
                setAuthHeader()
            }.body()
        }
    }

    /**
     * Get signals by symbol
     */
    suspend fun getSignalsBySymbol(
        symbol: String,
        limit: Int = 100,
    ): Result<List<Signal>> {
        return executeRequest {
            httpClient.get("$baseUrl/api/signals/symbol/$symbol") {
                parameter("limit", limit)
                setAuthHeader()
            }.body()
        }
    }

    /**
     * Get signals by type
     */
    suspend fun getSignalsByType(
        type: SignalType,
        limit: Int = 100,
    ): Result<List<Signal>> {
        return executeRequest {
            httpClient.get("$baseUrl/api/signals/type/${type.name}") {
                parameter("limit", limit)
                setAuthHeader()
            }.body()
        }
    }

    /**
     * Create or update a signal
     */
    suspend fun saveSignal(signal: Signal): Result<Signal> {
        return executeRequest {
            httpClient.post("$baseUrl/api/signals") {
                setAuthHeader()
                setBody(signal)
            }.body()
        }
    }

    /**
     * Delete a signal
     */
    suspend fun deleteSignal(signalId: String): Result<Unit> {
        return executeRequest {
            httpClient.delete("$baseUrl/api/signals/$signalId") {
                setAuthHeader()
            }
        }
    }

    // ========================================
    // ORDER ENDPOINTS
    // ========================================

    /**
     * Get recent orders
     */
    suspend fun getRecentOrders(limit: Int = 100): Result<List<Order>> {
        return executeRequest {
            httpClient.get("$baseUrl/api/orders") {
                parameter("limit", limit)
                setAuthHeader()
            }.body()
        }
    }

    /**
     * Get order by ID
     */
    suspend fun getOrderById(orderId: String): Result<Order> {
        return executeRequest {
            httpClient.get("$baseUrl/api/orders/$orderId") {
                setAuthHeader()
            }.body()
        }
    }

    /**
     * Get orders by symbol
     */
    suspend fun getOrdersBySymbol(symbol: String): Result<List<Order>> {
        return executeRequest {
            httpClient.get("$baseUrl/api/orders/symbol/$symbol") {
                setAuthHeader()
            }.body()
        }
    }

    /**
     * Get active orders
     */
    suspend fun getActiveOrders(): Result<List<Order>> {
        return executeRequest {
            httpClient.get("$baseUrl/api/orders/active") {
                setAuthHeader()
            }.body()
        }
    }

    /**
     * Create a new order
     */
    suspend fun createOrder(order: Order): Result<Order> {
        return executeRequest {
            httpClient.post("$baseUrl/api/orders") {
                setAuthHeader()
                setBody(order)
            }.body()
        }
    }

    /**
     * Update an existing order
     */
    suspend fun updateOrder(order: Order): Result<Order> {
        return executeRequest {
            httpClient.put("$baseUrl/api/orders/${order.orderId}") {
                setAuthHeader()
                setBody(order)
            }.body()
        }
    }

    /**
     * Cancel an order
     */
    suspend fun cancelOrder(orderId: String): Result<Order> {
        return executeRequest {
            httpClient.post("$baseUrl/api/orders/$orderId/cancel") {
                setAuthHeader()
            }.body()
        }
    }

    /**
     * Delete an order
     */
    suspend fun deleteOrder(orderId: String): Result<Unit> {
        return executeRequest {
            httpClient.delete("$baseUrl/api/orders/$orderId") {
                setAuthHeader()
            }
        }
    }

    // ========================================
    // POSITION ENDPOINTS
    // ========================================

    /**
     * Get recent positions
     */
    suspend fun getRecentPositions(limit: Int = 100): Result<List<Position>> {
        return executeRequest {
            httpClient.get("$baseUrl/api/positions") {
                parameter("limit", limit)
                setAuthHeader()
            }.body()
        }
    }

    /**
     * Get position by ID
     */
    suspend fun getPositionById(positionId: String): Result<Position> {
        return executeRequest {
            httpClient.get("$baseUrl/api/positions/$positionId") {
                setAuthHeader()
            }.body()
        }
    }

    /**
     * Get open positions
     */
    suspend fun getOpenPositions(): Result<List<Position>> {
        return executeRequest {
            httpClient.get("$baseUrl/api/positions/open") {
                setAuthHeader()
            }.body()
        }
    }

    /**
     * Get closed positions
     */
    suspend fun getClosedPositions(): Result<List<Position>> {
        return executeRequest {
            httpClient.get("$baseUrl/api/positions/closed") {
                setAuthHeader()
            }.body()
        }
    }

    /**
     * Get positions by symbol
     */
    suspend fun getPositionsBySymbol(symbol: String): Result<List<Position>> {
        return executeRequest {
            httpClient.get("$baseUrl/api/positions/symbol/$symbol") {
                setAuthHeader()
            }.body()
        }
    }

    /**
     * Create or update a position
     */
    suspend fun savePosition(position: Position): Result<Position> {
        return executeRequest {
            httpClient.post("$baseUrl/api/positions") {
                setAuthHeader()
                setBody(position)
            }.body()
        }
    }

    /**
     * Close a position
     */
    suspend fun closePosition(positionId: String): Result<Position> {
        return executeRequest {
            httpClient.post("$baseUrl/api/positions/$positionId/close") {
                setAuthHeader()
            }.body()
        }
    }

    /**
     * Delete a position
     */
    suspend fun deletePosition(positionId: String): Result<Unit> {
        return executeRequest {
            httpClient.delete("$baseUrl/api/positions/$positionId") {
                setAuthHeader()
            }
        }
    }

    // ========================================
    // HEALTH & STATUS
    // ========================================

    /**
     * Check API health
     */
    suspend fun getHealth(): Result<HealthResponse> {
        return executeRequest {
            httpClient.get("$baseUrl/health").body()
        }
    }

    /**
     * Get API status
     */
    suspend fun getStatus(): Result<StatusResponse> {
        return executeRequest {
            httpClient.get("$baseUrl/api/status") {
                setAuthHeader()
            }.body()
        }
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    /**
     * Executes a request and wraps the result in a Result type
     */
    private suspend fun <T> executeRequest(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: ClientRequestException) {
            // 4xx errors
            println("FksApiClient: Client error (${e.response.status}): ${e.message}")
            Result.failure(ApiException.ClientError(e.response.status.value, e.message ?: "Unknown error"))
        } catch (e: ServerResponseException) {
            // 5xx errors
            println("FksApiClient: Server error (${e.response.status}): ${e.message}")
            Result.failure(ApiException.ServerError(e.response.status.value, e.message ?: "Unknown error"))
        } catch (e: HttpRequestTimeoutException) {
            println("FksApiClient: Request timeout: ${e.message}")
            Result.failure(ApiException.Timeout(e.message ?: "Request timeout"))
        } catch (e: Exception) {
            println("FksApiClient: Network error: ${e.message}")
            Result.failure(ApiException.NetworkError(e.message ?: "Network error"))
        }
    }

    /**
     * Sets the authorization header if auth token is available
     */
    private fun HttpRequestBuilder.setAuthHeader() {
        authToken?.let {
            header(HttpHeaders.Authorization, "Bearer $it")
        }
    }

    /**
     * Cleanup resources
     */
    fun close() {
        httpClient.close()
    }
}

/**
 * Health response from the API
 */
@kotlinx.serialization.Serializable
data class HealthResponse(
    val status: String,
    val timestamp: Long,
    val services: Map<String, String> = emptyMap(),
)

/**
 * Status response from the API
 */
@kotlinx.serialization.Serializable
data class StatusResponse(
    val version: String,
    val uptime: Long,
    val activeConnections: Int = 0,
    val totalRequests: Long = 0,
)

/**
 * API exceptions with specific error types
 */
sealed class ApiException(message: String) : Exception(message) {
    data class ClientError(val code: Int, val msg: String) : ApiException("Client error ($code): $msg")

    data class ServerError(val code: Int, val msg: String) : ApiException("Server error ($code): $msg")

    data class Timeout(val msg: String) : ApiException("Timeout: $msg")

    data class NetworkError(val msg: String) : ApiException("Network error: $msg")
}
