package xyz.fkstrading.shared.di

import org.koin.dsl.module
import xyz.fkstrading.shared.data.api.HttpClientFactory

/**
 * Koin module for network dependencies
 *
 * Provides HTTP client and API client instances for dependency injection.
 */
val networkModule =
    module {

        /**
         * HTTP Client - Singleton instance
         *
         * Can be configured via environment variables or build configuration
         * to use different base URLs for dev/staging/production.
         */
        single {
            // Default to the janus brain API. NOTE: this HttpClientFactory client is not the
            // lever for FksApiClient (which builds absolute URLs from its own ctor baseUrl).
            val baseUrl = getProperty("API_BASE_URL", "http://localhost:8080")
            val enableLogging = getProperty("ENABLE_HTTP_LOGGING", "true").toBoolean()

            HttpClientFactory.create(
                baseUrl = baseUrl,
                enableLogging = enableLogging,
            )
        }

        /**
         * WebSocket Client - Singleton instance
         *
         * Used for real-time data streams (Week 2 implementation)
         */
        single(qualifier = org.koin.core.qualifier.named("websocket")) {
            val wsBaseUrl = getProperty("WS_BASE_URL", "ws://localhost:8000")
            HttpClientFactory.createWebSocketClient(baseUrl = wsBaseUrl)
        }

        // API Client moved to DatabaseModule (FksApiClient)
    }

/**
 * Helper function to get property with fallback
 */
private inline fun <reified T> org.koin.core.scope.Scope.getProperty(
    key: String,
    defaultValue: T,
): T {
    return try {
        getProperty(key) ?: defaultValue
    } catch (e: Exception) {
        defaultValue
    }
}
