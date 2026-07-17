package xyz.fkstrading.client.config

/**
 * Application Configuration
 *
 * Central configuration for the FKS Trading client application.
 * Contains URLs, timeouts, and other app-wide settings.
 */
object AppConfig {
    /**
     * Default endpoints: the real janus backend over Tailscale.
     *
     * These are tailnet-internal defaults — the backend host (oryx) is only reachable
     * from devices on the single-user tailnet, so trust is device-level; HTTPS is
     * terminated by `tailscale serve` on the host, and proper user/auth management is
     * deliberately deferred. Overridable at runtime in Settings
     * (SystemSettingsViewModel: `api_base_url` / `ws_url`).
     *
     * Port map on oryx:
     *  - `:443`  -> host 7001 -> janus forward REST (`/api/v1/signals/generate` + the
     *    `/api/v1/risk` routes)
     *  - `:8443` -> host 7000 -> janus_api (REST + WebSocket `/ws/signals`, `/ws/stream`
     *    on the same router — janus services/data/src/api/mod.rs)
     */
    object Development {
        // janus_api serves its WebSocket routes on the same port as its REST API.
        const val WS_BASE_URL = "wss://oryx.tailfef10.ts.net:8443"
        // janus_api (brain REST)
        const val HTTP_BASE_URL = "https://oryx.tailfef10.ts.net:8443"
        // janus forward REST (signals/risk, positions/account)
        const val FORWARD_BASE_URL = "https://oryx.tailfef10.ts.net"
    }

    /**
     * Production environment — same single-host oryx deployment as [Development]
     * (the old api.fkstrading.xyz host is dead; there is no separate prod stack).
     */
    object Production {
        const val WS_BASE_URL = "wss://oryx.tailfef10.ts.net:8443"
        const val HTTP_BASE_URL = "https://oryx.tailfef10.ts.net:8443"
        const val FORWARD_BASE_URL = "https://oryx.tailfef10.ts.net"
    }

    /**
     * Current environment (change for production builds)
     */
    private val currentEnv = Development

    /**
     * WebSocket channel URLs
     *
     * janus_api only exposes `/ws/signals` and `/ws/stream` server-side; the
     * orders/positions/market channels below are legacy and unwired — kept for
     * source compatibility, do not expect them to connect.
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
