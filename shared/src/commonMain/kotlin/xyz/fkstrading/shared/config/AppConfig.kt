package xyz.fkstrading.shared.config

import kotlinx.serialization.Serializable

/**
 * Application configuration
 *
 * Manages environment-specific settings including:
 * - API endpoints
 * - Development mode flags
 * - Feature toggles
 * - Timeouts and limits
 */
@Serializable
data class AppConfig(
    val environment: Environment = Environment.DEVELOPMENT,
    val apiBaseUrl: String = getDefaultApiUrl(environment),
    val wsBaseUrl: String = getDefaultWsUrl(environment),
    val useMockData: Boolean = false,
    val enableLogging: Boolean = true,
    val enableAnalytics: Boolean = false,
    val requestTimeoutMs: Long = 30_000L,
    val connectTimeoutMs: Long = 10_000L,
    val maxRetries: Int = 3,
    val authToken: String? = null,
) {
    companion object {
        /**
         * Development configuration with localhost endpoints
         */
        fun development(useMockData: Boolean = false): AppConfig {
            return AppConfig(
                environment = Environment.DEVELOPMENT,
                apiBaseUrl = "http://localhost:8000",
                wsBaseUrl = "ws://localhost:8000",
                useMockData = useMockData,
                enableLogging = true,
                enableAnalytics = false,
            )
        }

        /**
         * Production configuration for fkstrading.xyz
         */
        fun production(authToken: String? = null): AppConfig {
            return AppConfig(
                environment = Environment.PRODUCTION,
                apiBaseUrl = "https://api.fkstrading.xyz",
                wsBaseUrl = "wss://api.fkstrading.xyz",
                useMockData = false,
                enableLogging = false,
                enableAnalytics = true,
                authToken = authToken,
            )
        }

        /**
         * Staging configuration
         */
        fun staging(authToken: String? = null): AppConfig {
            return AppConfig(
                environment = Environment.STAGING,
                apiBaseUrl = "https://staging-api.fkstrading.xyz",
                wsBaseUrl = "wss://staging-api.fkstrading.xyz",
                useMockData = false,
                enableLogging = true,
                enableAnalytics = false,
                authToken = authToken,
            )
        }

        /**
         * Mock data configuration for UI testing without backend
         */
        fun mockOnly(): AppConfig {
            return AppConfig(
                environment = Environment.DEVELOPMENT,
                apiBaseUrl = "http://localhost:8000",
                wsBaseUrl = "ws://localhost:8000",
                useMockData = true,
                enableLogging = true,
                enableAnalytics = false,
            )
        }

        private fun getDefaultApiUrl(env: Environment): String {
            return when (env) {
                Environment.DEVELOPMENT -> "http://localhost:8000"
                Environment.STAGING -> "https://staging-api.fkstrading.xyz"
                Environment.PRODUCTION -> "https://api.fkstrading.xyz"
            }
        }

        private fun getDefaultWsUrl(env: Environment): String {
            return when (env) {
                Environment.DEVELOPMENT -> "ws://localhost:8000"
                Environment.STAGING -> "wss://staging-api.fkstrading.xyz"
                Environment.PRODUCTION -> "wss://api.fkstrading.xyz"
            }
        }
    }

    /**
     * Returns true if running in development mode
     */
    fun isDevelopment(): Boolean = environment == Environment.DEVELOPMENT

    /**
     * Returns true if running in production mode
     */
    fun isProduction(): Boolean = environment == Environment.PRODUCTION

    /**
     * Returns true if running in staging mode
     */
    fun isStaging(): Boolean = environment == Environment.STAGING

    /**
     * Returns true if mock data should be used
     */
    fun shouldUseMockData(): Boolean = useMockData

    /**
     * Returns the full WebSocket URL for a specific endpoint
     */
    fun getWsUrl(endpoint: String): String {
        val cleanEndpoint = endpoint.trimStart('/')
        return "$wsBaseUrl/$cleanEndpoint"
    }

    /**
     * Returns the full API URL for a specific endpoint
     */
    fun getApiUrl(endpoint: String): String {
        val cleanEndpoint = endpoint.trimStart('/')
        return "$apiBaseUrl/$cleanEndpoint"
    }

    /**
     * Validates the configuration
     */
    fun validate(): Result<Unit> {
        return try {
            require(apiBaseUrl.isNotBlank()) { "API base URL cannot be blank" }
            require(wsBaseUrl.isNotBlank()) { "WebSocket base URL cannot be blank" }
            require(requestTimeoutMs > 0) { "Request timeout must be positive" }
            require(connectTimeoutMs > 0) { "Connect timeout must be positive" }
            require(maxRetries >= 0) { "Max retries must be non-negative" }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Application environment
 */
@Serializable
enum class Environment {
    DEVELOPMENT,
    STAGING,
    PRODUCTION,
}

/**
 * Feature flags for toggling features on/off
 */
@Serializable
data class FeatureFlags(
    val enableRealTimeUpdates: Boolean = true,
    val enableCharts: Boolean = false,
    val enableTradeHistory: Boolean = false,
    val enableBacktesting: Boolean = false,
    val enableRiskAnalytics: Boolean = true,
    val enableNotifications: Boolean = false,
    val enableExport: Boolean = false,
    val enableAdvancedOrders: Boolean = true,
) {
    companion object {
        /**
         * All features enabled (for development)
         */
        fun all(): FeatureFlags {
            return FeatureFlags(
                enableRealTimeUpdates = true,
                enableCharts = true,
                enableTradeHistory = true,
                enableBacktesting = true,
                enableRiskAnalytics = true,
                enableNotifications = true,
                enableExport = true,
                enableAdvancedOrders = true,
            )
        }

        /**
         * Production feature set (stable features only)
         */
        fun production(): FeatureFlags {
            return FeatureFlags(
                enableRealTimeUpdates = true,
                enableCharts = false,
                enableTradeHistory = false,
                enableBacktesting = false,
                enableRiskAnalytics = true,
                enableNotifications = false,
                enableExport = false,
                enableAdvancedOrders = true,
            )
        }

        /**
         * Minimal feature set
         */
        fun minimal(): FeatureFlags {
            return FeatureFlags(
                enableRealTimeUpdates = false,
                enableCharts = false,
                enableTradeHistory = false,
                enableBacktesting = false,
                enableRiskAnalytics = false,
                enableNotifications = false,
                enableExport = false,
                enableAdvancedOrders = false,
            )
        }
    }
}
