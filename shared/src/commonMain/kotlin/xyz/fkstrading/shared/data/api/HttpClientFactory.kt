package xyz.fkstrading.shared.data.api

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Factory for creating configured HTTP clients
 *
 * Provides a centralized configuration for all HTTP clients used
 * throughout the application with proper error handling, timeouts,
 * and serialization settings.
 */
object HttpClientFactory {
    /**
     * Creates a configured HTTP client for API communication
     *
     * @param baseUrl Base URL for the API (e.g., "http://localhost:8000")
     * @param enableLogging Whether to enable HTTP request/response logging
     * @param timeoutMillis Request timeout in milliseconds
     * @return Configured HttpClient instance
     */
    fun create(
        baseUrl: String = "http://localhost:8000",
        enableLogging: Boolean = true,
        timeoutMillis: Long = 30_000,
    ): HttpClient {
        return HttpClient {
            // Content negotiation with JSON
            install(ContentNegotiation) {
                json(
                    Json {
                        // Ignore unknown fields from API
                        ignoreUnknownKeys = true
                        // Be lenient with malformed JSON
                        isLenient = true
                        // Pretty print for debugging
                        prettyPrint = true
                        // Allow null values
                        explicitNulls = false
                        // Use defaults for missing fields
                        coerceInputValues = true
                    },
                )
            }

            // HTTP logging for debugging
            if (enableLogging) {
                install(Logging) {
                    logger = Logger.DEFAULT
                    level = LogLevel.INFO
                    // Filter out sensitive headers
                    sanitizeHeader { header -> header == HttpHeaders.Authorization }
                }
            }

            // Timeout configuration
            install(HttpTimeout) {
                requestTimeoutMillis = timeoutMillis
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = timeoutMillis
            }

            // Default request configuration
            defaultRequest {
                url(baseUrl)

                // Set default headers
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json)
                header(HttpHeaders.UserAgent, "FKS-Trading-Client/1.0")

                // Add authentication header if token is available
                val token = TokenManager.getAccessToken()
                if (token != null) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }

            // HTTP redirect handling
            followRedirects = true

            // Retry configuration for failed requests
            // HTTP response validation (handled by caller)
        }
    }

    /**
     * Creates an HTTP client for WebSocket connections
     *
     * @param baseUrl Base URL for WebSocket connections
     * @return Configured HttpClient with WebSocket support
     */
    fun createWebSocketClient(baseUrl: String = "ws://localhost:8000"): HttpClient {
        return HttpClient {
            install(io.ktor.client.plugins.websocket.WebSockets) {
                pingIntervalMillis = 20_000
                maxFrameSize = Long.MAX_VALUE
            }

            defaultRequest {
                url(baseUrl)
            }
        }
    }
}
