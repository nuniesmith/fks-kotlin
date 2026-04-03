package xyz.fkstrading.client.features.settings

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import xyz.fkstrading.shared.config.AppConfig
import xyz.fkstrading.shared.data.storage.SettingsStorage

/**
 * ViewModel for system-wide settings including Discord notifications,
 * API configuration, and general preferences.
 *
 * Settings are persisted across app restarts using the platform-specific
 * [SettingsStorage] implementation (SharedPreferences on Android,
 * java.util.prefs on Desktop, NSUserDefaults on iOS, localStorage on WASM).
 */
class SystemSettingsViewModel(
    private val appConfig: AppConfig = AppConfig.development(),
    private val storage: SettingsStorage = SettingsStorage(),
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Discord settings
    private val _discordWebhookUrl = MutableStateFlow("")
    val discordWebhookUrl: StateFlow<String> = _discordWebhookUrl.asStateFlow()

    private val _discordEnabled = MutableStateFlow(false)
    val discordEnabled: StateFlow<Boolean> = _discordEnabled.asStateFlow()

    private val _notifyOnSignal = MutableStateFlow(true)
    val notifyOnSignal: StateFlow<Boolean> = _notifyOnSignal.asStateFlow()

    private val _notifyOnFill = MutableStateFlow(true)
    val notifyOnFill: StateFlow<Boolean> = _notifyOnFill.asStateFlow()

    private val _notifyOnError = MutableStateFlow(true)
    val notifyOnError: StateFlow<Boolean> = _notifyOnError.asStateFlow()

    // API settings
    private val _apiBaseUrl = MutableStateFlow(appConfig.apiBaseUrl)
    val apiBaseUrl: StateFlow<String> = _apiBaseUrl.asStateFlow()

    private val _wsUrl = MutableStateFlow(appConfig.wsBaseUrl)
    val wsUrl: StateFlow<String> = _wsUrl.asStateFlow()

    // Execution mode
    private val _executionMode = MutableStateFlow(ExecutionMode.SIMULATED)
    val executionMode: StateFlow<ExecutionMode> = _executionMode.asStateFlow()

    // UI state
    private val _uiState = MutableStateFlow<SystemSettingsUiState>(SystemSettingsUiState.Idle)
    val uiState: StateFlow<SystemSettingsUiState> = _uiState.asStateFlow()

    // Test result state
    private val _testResult = MutableStateFlow<TestResult?>(null)
    val testResult: StateFlow<TestResult?> = _testResult.asStateFlow()

    companion object {
        // Storage keys
        private const val KEY_DISCORD_WEBHOOK_GENERAL = "DISCORD_WEBHOOK_GENERAL"
        private const val KEY_DISCORD_ENABLED = "discord_enabled"
        private const val KEY_NOTIFY_SIGNAL = "notify_signal"
        private const val KEY_NOTIFY_FILL = "notify_fill"
        private const val KEY_NOTIFY_ERROR = "notify_error"
        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_WS_URL = "ws_url"
        private const val KEY_EXECUTION_MODE = "execution_mode"
    }

    init {
        loadSettings()
    }

    /**
     * Load settings from persistent storage
     */
    private fun loadSettings() {
        viewModelScope.launch {
            try {
                _discordWebhookUrl.value = storage.getString(KEY_DISCORD_WEBHOOK_GENERAL, "")
                _discordEnabled.value = storage.getBoolean(KEY_DISCORD_ENABLED, false)
                _notifyOnSignal.value = storage.getBoolean(KEY_NOTIFY_SIGNAL, true)
                _notifyOnFill.value = storage.getBoolean(KEY_NOTIFY_FILL, true)
                _notifyOnError.value = storage.getBoolean(KEY_NOTIFY_ERROR, true)
                _apiBaseUrl.value = storage.getString(KEY_API_BASE_URL, appConfig.apiBaseUrl)
                _wsUrl.value = storage.getString(KEY_WS_URL, appConfig.wsBaseUrl)
                _executionMode.value = try {
                    ExecutionMode.valueOf(
                        storage.getString(KEY_EXECUTION_MODE, ExecutionMode.SIMULATED.name)
                    )
                } catch (e: IllegalArgumentException) {
                    ExecutionMode.SIMULATED
                }
            } catch (e: Exception) {
                _uiState.value = SystemSettingsUiState.Error("Failed to load settings: ${e.message}")
            }
        }
    }

    /**
     * Persist all current settings to storage
     */
    private fun saveSettings() {
        viewModelScope.launch {
            try {
                storage.putString(KEY_DISCORD_WEBHOOK_GENERAL, _discordWebhookUrl.value)
                storage.putBoolean(KEY_DISCORD_ENABLED, _discordEnabled.value)
                storage.putBoolean(KEY_NOTIFY_SIGNAL, _notifyOnSignal.value)
                storage.putBoolean(KEY_NOTIFY_FILL, _notifyOnFill.value)
                storage.putBoolean(KEY_NOTIFY_ERROR, _notifyOnError.value)
                storage.putString(KEY_API_BASE_URL, _apiBaseUrl.value)
                storage.putString(KEY_WS_URL, _wsUrl.value)
                storage.putString(KEY_EXECUTION_MODE, _executionMode.value.name)

                _uiState.value = SystemSettingsUiState.Success("Settings saved")
                delay(2000)
                _uiState.value = SystemSettingsUiState.Idle
            } catch (e: Exception) {
                _uiState.value = SystemSettingsUiState.Error("Failed to save settings: ${e.message}")
            }
        }
    }

    /**
     * Update Discord webhook URL
     */
    fun updateDiscordWebhookUrl(url: String) {
        _discordWebhookUrl.value = url
        saveSettings()
    }

    /**
     * Toggle Discord notifications on/off
     */
    fun toggleDiscordEnabled(enabled: Boolean) {
        _discordEnabled.value = enabled
        saveSettings()
    }

    /**
     * Toggle notification for signals
     */
    fun toggleNotifyOnSignal(enabled: Boolean) {
        _notifyOnSignal.value = enabled
        saveSettings()
    }

    /**
     * Toggle notification for fills
     */
    fun toggleNotifyOnFill(enabled: Boolean) {
        _notifyOnFill.value = enabled
        saveSettings()
    }

    /**
     * Toggle notification for errors
     */
    fun toggleNotifyOnError(enabled: Boolean) {
        _notifyOnError.value = enabled
        saveSettings()
    }

    /**
     * Update API base URL
     */
    fun updateApiBaseUrl(url: String) {
        _apiBaseUrl.value = url
        saveSettings()
    }

    /**
     * Update WebSocket URL
     */
    fun updateWsUrl(url: String) {
        _wsUrl.value = url
        saveSettings()
    }

    /**
     * Update execution mode
     */
    fun updateExecutionMode(mode: ExecutionMode) {
        _executionMode.value = mode
        saveSettings()
    }

    /**
     * Test Discord webhook connection by actually POSTing a test message.
     *
     * Validates URL format first, then sends a real HTTP POST to the
     * Discord webhook endpoint. Reports success/failure back via [testResult].
     */
    fun testDiscordWebhook() {
        if (_discordWebhookUrl.value.isBlank()) {
            _testResult.value = TestResult.Error("Please enter a webhook URL")
            return
        }

        viewModelScope.launch {
            _uiState.value = SystemSettingsUiState.Testing
            _testResult.value = TestResult.Testing

            try {
                val url = _discordWebhookUrl.value.trim()
                val isValidFormat =
                    url.startsWith("https://discord.com/api/webhooks/") ||
                            url.startsWith("https://discordapp.com/api/webhooks/")

                if (!isValidFormat) {
                    _testResult.value = TestResult.Error("Invalid Discord webhook URL format")
                    _uiState.value = SystemSettingsUiState.Idle
                    return@launch
                }

                // Actually POST a test message to Discord
                val httpClient = HttpClient()
                try {
                    val response = httpClient.post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(
                            """{"content": "✅ **FKS Trading Platform** — webhook test successful!", "username": "FKS Trading Bot"}"""
                        )
                    }

                    when {
                        response.status.value in 200..299 -> {
                            _testResult.value = TestResult.Success("Webhook is working! Check your Discord channel.")
                        }

                        response.status.value == 204 -> {
                            // Discord returns 204 No Content on success
                            _testResult.value = TestResult.Success("Webhook is working! Check your Discord channel.")
                        }

                        response.status.value == 401 || response.status.value == 403 -> {
                            _testResult.value =
                                TestResult.Error("Webhook unauthorized — the URL may be invalid or revoked")
                        }

                        response.status.value == 404 -> {
                            _testResult.value = TestResult.Error("Webhook not found — it may have been deleted")
                        }

                        response.status.value == 429 -> {
                            _testResult.value =
                                TestResult.Error("Rate limited by Discord — please wait a moment and try again")
                        }

                        else -> {
                            _testResult.value = TestResult.Error(
                                "Discord returned HTTP ${response.status.value}: ${response.status.description}"
                            )
                        }
                    }
                } finally {
                    httpClient.close()
                }

                _uiState.value = SystemSettingsUiState.Idle
            } catch (e: Exception) {
                _testResult.value = TestResult.Error("Connection failed: ${e.message}")
                _uiState.value = SystemSettingsUiState.Idle
            }
        }
    }

    /**
     * Clear test result
     */
    fun clearTestResult() {
        _testResult.value = null
    }

    /**
     * Export settings as environment variables format
     */
    fun exportAsEnvVars(): String {
        return buildString {
            appendLine("# Discord Configuration")
            appendLine("DISCORD_WEBHOOK_GENERAL=${_discordWebhookUrl.value}")
            appendLine("DISCORD_ENABLE_NOTIFICATIONS=${_discordEnabled.value}")
            appendLine("DISCORD_NOTIFY_ON_SIGNAL=${_notifyOnSignal.value}")
            appendLine("DISCORD_NOTIFY_ON_FILL=${_notifyOnFill.value}")
            appendLine("DISCORD_NOTIFY_ON_ERROR=${_notifyOnError.value}")
            appendLine()
            appendLine("# API Configuration")
            appendLine("API_BASE_URL=${_apiBaseUrl.value}")
            appendLine("WS_URL=${_wsUrl.value}")
            appendLine()
            appendLine("# Execution Mode")
            appendLine("EXECUTION_MODE=${_executionMode.value.name.lowercase()}")
        }
    }

    /**
     * Reset all settings to defaults
     */
    fun resetToDefaults() {
        viewModelScope.launch {
            _discordWebhookUrl.value = ""
            _discordEnabled.value = false
            _notifyOnSignal.value = true
            _notifyOnFill.value = true
            _notifyOnError.value = true
            _apiBaseUrl.value = appConfig.apiBaseUrl
            _wsUrl.value = appConfig.wsBaseUrl
            _executionMode.value = ExecutionMode.SIMULATED
            saveSettings()
            _uiState.value = SystemSettingsUiState.Success("Settings reset to defaults")
        }
    }

    /**
     * Get Discord configuration for backend
     */
    fun getDiscordConfig(): DiscordConfig {
        return DiscordConfig(
            webhookUrl = _discordWebhookUrl.value,
            enabled = _discordEnabled.value,
            notifyOnSignal = _notifyOnSignal.value,
            notifyOnFill = _notifyOnFill.value,
            notifyOnError = _notifyOnError.value,
        )
    }

    /**
     * Cleanup when ViewModel is cleared
     */
    fun onCleared() {
        viewModelScope.cancel()
    }
}

/**
 * UI state for system settings screen
 */
sealed class SystemSettingsUiState {
    object Idle : SystemSettingsUiState()

    object Testing : SystemSettingsUiState()

    data class Success(val message: String) : SystemSettingsUiState()

    data class Error(val message: String) : SystemSettingsUiState()
}

/**
 * Test result states
 */
sealed class TestResult {
    object Testing : TestResult()

    data class Success(val message: String) : TestResult()

    data class Error(val message: String) : TestResult()
}

/**
 * Execution mode enum
 */
enum class ExecutionMode {
    SIMULATED,
    PAPER,
    LIVE,
}

/**
 * Discord configuration data class
 */
data class DiscordConfig(
    val webhookUrl: String,
    val enabled: Boolean,
    val notifyOnSignal: Boolean,
    val notifyOnFill: Boolean,
    val notifyOnError: Boolean,
)
