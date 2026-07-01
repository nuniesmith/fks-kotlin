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
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import xyz.fkstrading.shared.domain.models.*

/**
 * REST API client for the janus backend (READ surface).
 *
 * This client targets **janus**, not the retired `fks_ruby` backend. janus exposes a
 * read-only operator surface split across two services:
 *
 *  - **Brain API** ([baseUrl], `JANUS_HTTP_PORT`): `/health`, `/api/signals/...`,
 *    `/api/dashboard/...`.
 *  - **Forward REST** ([forwardBaseUrl]): `/api/v1/risk/portfolio`, `/api/v1/account`,
 *    `/api/v1/health`.
 *
 * Only the read endpoints that have a real janus equivalent are wired through to the
 * network: latest signals, portfolio positions, `/health`, and the dashboard overview.
 * Every other read method (per-id / per-type / orders / closed positions) has **no janus
 * equivalent** and returns `Result.failure(ApiException.ClientError(501, …))` — a
 * deliberate fail-fast so the offline-first repositories surface the gap in logs instead
 * of silently swallowing a 404 forever. Write/order methods are unchanged and remain a
 * gated follow-up (janus terminates order flow at human confirmation).
 *
 * NOTE: janus serializes `snake_case`; the typed read models below carry `@SerialName`
 * and a mapping layer. Compilation confirms types only — the wire mapping (and the exact
 * brain/forward ports) must be confirmed by a live smoke-test before relying on the data.
 *
 * Usage:
 * ```kotlin
 * val apiClient = FksApiClient(
 *     baseUrl = "http://localhost:8080",         // janus brain
 *     forwardBaseUrl = "http://localhost:8081",  // janus forward REST
 * )
 * val portfolio = apiClient.getRecentPositions()
 * val health = apiClient.getHealth()
 * ```
 */
class FksApiClient(
    private val baseUrl: String = "http://localhost:8080",
    private val forwardBaseUrl: String = baseUrl,
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
     * Get the latest signals as raw janus payloads.
     *
     * Maps to janus Brain `GET /api/signals/latest`, which returns
     * `{ signals: [<raw Redis JSON>], count }`. The elements are **unschematized**
     * (raw Redis values), so they cannot be deserialized as the typed [Signal]; callers
     * get [JsonElement]s and must extract fields defensively. Capture a real body via the
     * smoke-test before mapping these into typed UI models.
     */
    suspend fun getRecentSignalsRaw(): Result<SignalsLatestResponse> {
        return executeRequest {
            httpClient.get("$baseUrl/api/signals/latest") {
                setAuthHeader()
            }.body()
        }
    }

    /**
     * Get recent signals (typed).
     *
     * No janus equivalent — janus only serves opaque raw signals at `/api/signals/latest`.
     * Fails fast; use [getRecentSignalsRaw] for the raw payloads.
     */
    suspend fun getRecentSignals(limit: Int = 100): Result<List<Signal>> =
        Result.failure(
            ApiException.ClientError(501, "typed /api/signals not served by janus; use getRecentSignalsRaw()"),
        )

    /**
     * Get signal by ID — no janus equivalent (fails fast).
     */
    suspend fun getSignalById(signalId: String): Result<Signal> =
        Result.failure(ApiException.ClientError(501, "signal-by-id not served by janus"))

    /**
     * Get signals by symbol — no janus equivalent on the brain read surface (fails fast).
     */
    suspend fun getSignalsBySymbol(
        symbol: String,
        limit: Int = 100,
    ): Result<List<Signal>> =
        Result.failure(ApiException.ClientError(501, "signals-by-symbol not served by janus"))

    /**
     * Get signals by type — no janus equivalent (fails fast).
     */
    suspend fun getSignalsByType(
        type: SignalType,
        limit: Int = 100,
    ): Result<List<Signal>> =
        Result.failure(ApiException.ClientError(501, "signals-by-type not served by janus"))

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

    // Order reads have no janus equivalent on the read surface. The only live order/balance
    // view is the gated Forward `GET /api/v1/account` (conditional 404 unless the live feed is
    // wired) — a deliberate follow-up, not part of this read pass. All four fail fast.

    /**
     * Get recent orders — gated janus follow-up (`/api/v1/account`); fails fast.
     */
    suspend fun getRecentOrders(limit: Int = 100): Result<List<Order>> =
        Result.failure(ApiException.ClientError(501, "orders read is a gated janus follow-up (/api/v1/account)"))

    /**
     * Get order by ID — no janus equivalent (fails fast).
     */
    suspend fun getOrderById(orderId: String): Result<Order> =
        Result.failure(ApiException.ClientError(501, "order-by-id not served by janus"))

    /**
     * Get orders by symbol — no janus equivalent (fails fast).
     */
    suspend fun getOrdersBySymbol(symbol: String): Result<List<Order>> =
        Result.failure(ApiException.ClientError(501, "orders-by-symbol not served by janus"))

    /**
     * Get active orders — gated janus follow-up (`/api/v1/account`); fails fast.
     */
    suspend fun getActiveOrders(): Result<List<Order>> =
        Result.failure(ApiException.ClientError(501, "active orders is a gated janus follow-up (/api/v1/account)"))

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
     * Get recent (live) positions.
     *
     * Maps to janus Forward `GET /api/v1/risk/portfolio` ([PortfolioStateDto]). The portfolio
     * holds only live/open positions keyed by symbol; each is mapped to a [Position] via
     * [PortfolioPositionDto.toPosition] (the map key becomes the id; `openedAt`/`currentPrice`
     * are placeholders — janus provides neither here). [limit] is ignored (janus returns the
     * full portfolio).
     */
    suspend fun getRecentPositions(limit: Int = 100): Result<List<Position>> {
        return executeRequest {
            val dto: PortfolioStateDto =
                httpClient.get("$forwardBaseUrl/api/v1/risk/portfolio") {
                    setAuthHeader()
                }.body()
            dto.positions.map { (key, p) -> p.toPosition(key) }
        }
    }

    /**
     * Get position by ID — janus has no by-id endpoint (ids are synthetic map keys); fails fast.
     */
    suspend fun getPositionById(positionId: String): Result<Position> =
        Result.failure(ApiException.ClientError(501, "position-by-id not served by janus"))

    /**
     * Get open positions. The janus portfolio holds only live positions, so this is the
     * whole portfolio.
     */
    suspend fun getOpenPositions(): Result<List<Position>> = getRecentPositions()

    /**
     * Get closed positions. janus does not expose closed history → empty (not an error).
     */
    suspend fun getClosedPositions(): Result<List<Position>> = Result.success(emptyList())

    /**
     * Get positions by symbol — janus has no by-symbol endpoint; filter the portfolio client-side.
     */
    suspend fun getPositionsBySymbol(symbol: String): Result<List<Position>> =
        getRecentPositions().map { list -> list.filter { it.symbol == symbol } }

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
     * Check janus Brain health.
     *
     * Maps to janus Brain `GET /health` ([JanusHealthResponse]) — same path as before, but a
     * far richer shape than the old fks_ruby `{status,timestamp,services}`.
     */
    suspend fun getHealth(): Result<JanusHealthResponse> {
        return executeRequest {
            httpClient.get("$baseUrl/health").body()
        }
    }

    /**
     * Get the dashboard overview.
     *
     * Repoints the old `/api/status` to janus Brain `GET /api/dashboard/overview`
     * ([DashboardOverviewResponse]). Several fields are janus placeholders (0) until the
     * backing stores are wired — the shape is stable, the values are not.
     */
    suspend fun getStatus(): Result<DashboardOverviewResponse> {
        return executeRequest {
            httpClient.get("$baseUrl/api/dashboard/overview") {
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

// =============================================================================
// janus READ DTOs (snake_case wire shapes) + mapping layer
//
// janus serializes snake_case; every field that differs from the Kotlin camelCase name
// carries an explicit @SerialName. Missing fields rely on Kotlin-side defaults (the client
// JSON config has ignoreUnknownKeys = true, so janus-only extra fields are dropped).
// =============================================================================

/**
 * janus Brain `GET /api/signals/latest`.
 *
 * `signals` are raw, unschematized Redis JSON values — kept as [JsonElement], NOT typed
 * [Signal]s. Extract fields defensively after confirming the real shape via smoke-test.
 */
@kotlinx.serialization.Serializable
data class SignalsLatestResponse(
    val signals: List<JsonElement> = emptyList(),
    val count: Int = 0,
)

/**
 * One position inside janus Forward `GET /api/v1/risk/portfolio` (`PositionDto`).
 * `side` is the janus string `"Long"`/`"Short"` (mapped to [OrderSide] in [toPosition]).
 */
@kotlinx.serialization.Serializable
data class PortfolioPositionDto(
    val symbol: String,
    @SerialName("entry_price") val entryPrice: Double,
    val quantity: Double,
    val side: String,
    @SerialName("stop_loss") val stopLoss: Double? = null,
    @SerialName("take_profit") val takeProfit: Double? = null,
    @SerialName("position_value") val positionValue: Double = 0.0,
    @SerialName("risk_amount") val riskAmount: Double? = null,
)

/**
 * janus Forward `GET /api/v1/risk/portfolio` (`PortfolioStateDto`). `positions` is keyed
 * by symbol; the live portfolio holds only open positions.
 */
@kotlinx.serialization.Serializable
data class PortfolioStateDto(
    val positions: Map<String, PortfolioPositionDto> = emptyMap(),
    @SerialName("daily_pnl") val dailyPnl: Double = 0.0,
    @SerialName("total_value") val totalValue: Double = 0.0,
    @SerialName("total_exposure") val totalExposure: Double = 0.0,
    @SerialName("position_count") val positionCount: Int = 0,
    @SerialName("exposure_percentage") val exposurePercentage: Double = 0.0,
)

/**
 * Maps a janus portfolio position into the app's [Position].
 *
 * Fills the gaps janus doesn't provide: [Position.positionId] from the portfolio map [key];
 * [Position.currentPrice] defaults to the entry price (janus has no mark price here);
 * [Position.status] is always OPEN (portfolio = live positions); [Position.openedAt] is a
 * placeholder ([Instant.DISTANT_PAST]) — janus carries no open timestamp.
 */
fun PortfolioPositionDto.toPosition(key: String): Position =
    Position(
        positionId = key,
        symbol = symbol,
        side = if (side.equals("Short", ignoreCase = true)) OrderSide.SELL else OrderSide.BUY,
        quantity = quantity,
        entryPrice = entryPrice,
        currentPrice = entryPrice,
        status = PositionStatus.OPEN,
        openedAt = Instant.DISTANT_PAST,
        stopLoss = stopLoss,
        takeProfit = takeProfit,
        value = positionValue,
    )

/** One module entry in janus health/dashboard payloads (`ModuleHealthSummary`). */
@kotlinx.serialization.Serializable
data class JanusModuleHealth(
    val name: String,
    val healthy: Boolean,
    val message: String? = null,
)

/**
 * janus Brain `GET /health` (`HealthStatus`). `service_state` is the snake_case string
 * `"standby"`/`"running"`/`"stopped"` (kept as [String], not a Kotlin enum).
 */
@kotlinx.serialization.Serializable
data class JanusHealthResponse(
    val status: String,
    @SerialName("uptime_seconds") val uptimeSeconds: Long = 0,
    @SerialName("signals_generated") val signalsGenerated: Long = 0,
    @SerialName("signals_persisted") val signalsPersisted: Long = 0,
    val modules: List<JanusModuleHealth> = emptyList(),
    @SerialName("shutdown_requested") val shutdownRequested: Boolean = false,
    @SerialName("service_state") val serviceState: String = "",
)

/** `performance` block inside the janus dashboard overview. All fields are placeholders (0.0) until wired. */
@kotlinx.serialization.Serializable
data class JanusPerformance(
    @SerialName("signal_generation_rate") val signalGenerationRate: Double = 0.0,
    @SerialName("persistence_rate") val persistenceRate: Double = 0.0,
    @SerialName("avg_latency_ms") val avgLatencyMs: Double = 0.0,
    @SerialName("error_rate") val errorRate: Double = 0.0,
)

/**
 * janus Brain `GET /api/dashboard/overview`. `recent_signals` are raw, unschematized Redis
 * JSON (kept as [JsonElement]); `total_persisted` etc. may be placeholder values until wired.
 */
@kotlinx.serialization.Serializable
data class DashboardOverviewResponse(
    @SerialName("total_signals") val totalSignals: Long = 0,
    @SerialName("total_persisted") val totalPersisted: Long = 0,
    @SerialName("uptime_seconds") val uptimeSeconds: Long = 0,
    @SerialName("active_modules") val activeModules: Int = 0,
    @SerialName("healthy_modules") val healthyModules: Int = 0,
    @SerialName("recent_signals") val recentSignals: List<JsonElement> = emptyList(),
    val performance: JanusPerformance = JanusPerformance(),
    @SerialName("module_status") val moduleStatus: List<JanusModuleHealth> = emptyList(),
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
