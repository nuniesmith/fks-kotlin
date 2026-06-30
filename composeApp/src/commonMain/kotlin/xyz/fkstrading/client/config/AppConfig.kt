package xyz.fkstrading.client.config

/**
 * Application Configuration
 *
 * Central configuration for the FKS Trading client application.
 * Contains URLs, timeouts, and other app-wide settings.
 */
object AppConfig {
    /**
     * Development environment (local janus backend)
     *
     * HTTP points at the janus Brain API; [FORWARD_BASE_URL] is the janus Forward REST
     * service (positions/account). Confirm the real ports before smoke-testing.
     */
    object Development {
        const val WS_BASE_URL = "ws://localhost:8000"
        const val HTTP_BASE_URL = "http://localhost:8080"
        const val FORWARD_BASE_URL = "http://localhost:8081"
    }

    /**
     * Production environment
     */
    object Production {
        const val WS_BASE_URL = "wss://api.fkstrading.xyz"
        const val HTTP_BASE_URL = "https://api.fkstrading.xyz"
        const val FORWARD_BASE_URL = "https://api.fkstrading.xyz"
    }

    /**
     * Current environment (change for production builds)
     */
    private val currentEnv = Development

    /**
     * WebSocket channel URLs
     */
    object WebSocket {
        val SIGNALS_URL = "${currentEnv.WS_BASE_URL}/ws/signals"
        val ORDERS_URL = "${currentEnv.WS_BASE_URL}/ws/orders"
        val POSITIONS_URL = "${currentEnv.WS_BASE_URL}/ws/positions"
        val MARKET_DATA_URL = "${currentEnv.WS_BASE_URL}/ws/market"

        // Default URL (multi-channel endpoint)
        val DEFAULT_URL = SIGNALS_URL
    }

    /**
     * HTTP API endpoints
     */
    object Api {
        val BASE_URL = currentEnv.HTTP_BASE_URL
    }

    /**
     * WebSocket configuration
     */
    object WebSocketConfig {
        const val PING_INTERVAL_MS = 30_000L
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val MAX_RECONNECT_ATTEMPTS = 5
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
    }

    /**
     * UI configuration
     */
    object UI {
        const val MAX_SIGNALS_DISPLAYED = 100
        const val AUTO_SCROLL_ENABLED = true
        const val SUBSCRIPTION_POLL_INTERVAL_MS = 1_000L
    }
}
