package xyz.fkstrading.client.features.settings

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import xyz.fkstrading.shared.data.repository.StrategyConfigRepository
import xyz.fkstrading.shared.domain.models.StrategyConfig
import xyz.fkstrading.shared.domain.models.aggressive
import xyz.fkstrading.shared.domain.models.conservative
import xyz.fkstrading.shared.domain.models.default
import xyz.fkstrading.shared.domain.models.withUpdatedTimestamp
import xyz.fkstrading.shared.domain.strategy.models.ExecutionConfig

/**
 * ViewModel for managing strategy configurations in the Settings UI.
 */
class StrategyConfigViewModel(
    private val repository: StrategyConfigRepository,
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Observe all configs
    val configs: StateFlow<List<StrategyConfig>> =
        repository
            .observeAllConfigs()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Observe active configs only
    val activeConfigs: StateFlow<List<StrategyConfig>> =
        repository
            .observeActiveConfigs()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Current default config
    val defaultConfig: StateFlow<StrategyConfig?> =
        repository
            .observeDefaultConfig()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // UI state
    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Idle)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Selected config for editing
    private val _selectedConfig = MutableStateFlow<StrategyConfig?>(null)
    val selectedConfig: StateFlow<StrategyConfig?> = _selectedConfig.asStateFlow()

    init {
        // Ensure at least one config exists
        viewModelScope.launch {
            repository.ensureDefaultConfigExists()
        }
    }

    /**
     * Creates a new strategy configuration.
     */
    fun createConfig(
        name: String,
        executionConfig: ExecutionConfig,
    ) {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading
            val config =
                StrategyConfig(
                    configId = "config-${Clock.System.now().toEpochMilliseconds()}",
                    name = name,
                    executionConfig = executionConfig,
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now(),
                )

            repository.saveConfig(config)
                .onSuccess {
                    _uiState.value = SettingsUiState.Success("Configuration created successfully")
                }
                .onFailure { error ->
                    _uiState.value = SettingsUiState.Error(error.message ?: "Failed to create configuration")
                }
        }
    }

    /**
     * Updates an existing configuration.
     */
    fun updateConfig(config: StrategyConfig) {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading
            val updated = config.withUpdatedTimestamp(Clock.System.now())

            repository.saveConfig(updated)
                .onSuccess {
                    _uiState.value = SettingsUiState.Success("Configuration updated successfully")
                    _selectedConfig.value = null
                }
                .onFailure { error ->
                    _uiState.value = SettingsUiState.Error(error.message ?: "Failed to update configuration")
                }
        }
    }

    /**
     * Sets a configuration as the default.
     */
    fun setAsDefault(configId: String) {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading
            repository.setAsDefault(configId)
                .onSuccess {
                    _uiState.value = SettingsUiState.Success("Default configuration updated")
                }
                .onFailure { error ->
                    _uiState.value = SettingsUiState.Error(error.message ?: "Failed to set default")
                }
        }
    }

    /**
     * Toggles the active status of a configuration.
     */
    fun toggleActive(
        configId: String,
        isActive: Boolean,
    ) {
        viewModelScope.launch {
            repository.updateActiveStatus(configId, isActive)
                .onFailure { error ->
                    _uiState.value = SettingsUiState.Error(error.message ?: "Failed to update status")
                }
        }
    }

    /**
     * Deletes a configuration.
     */
    fun deleteConfig(configId: String) {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading
            repository.deleteConfig(configId)
                .onSuccess {
                    _uiState.value = SettingsUiState.Success("Configuration deleted")
                }
                .onFailure { error ->
                    _uiState.value = SettingsUiState.Error(error.message ?: "Failed to delete configuration")
                }
        }
    }

    /**
     * Selects a configuration for editing.
     */
    fun selectConfig(config: StrategyConfig) {
        _selectedConfig.value = config
    }

    /**
     * Clears the selected configuration.
     */
    fun clearSelection() {
        _selectedConfig.value = null
    }

    /**
     * Resets UI state to idle.
     */
    fun resetUiState() {
        _uiState.value = SettingsUiState.Idle
    }

    /**
     * Creates a preset configuration (Conservative, Aggressive, etc.)
     */
    fun createPreset(preset: ConfigPreset) {
        val timestamp = Clock.System.now()
        val config =
            when (preset) {
                ConfigPreset.CONSERVATIVE ->
                    StrategyConfig.conservative(
                        configId = "preset-conservative-${timestamp.toEpochMilliseconds()}",
                        timestamp = timestamp,
                    )
                ConfigPreset.AGGRESSIVE ->
                    StrategyConfig.aggressive(
                        configId = "preset-aggressive-${timestamp.toEpochMilliseconds()}",
                        timestamp = timestamp,
                    )
                ConfigPreset.BALANCED ->
                    StrategyConfig.default(
                        configId = "preset-balanced-${timestamp.toEpochMilliseconds()}",
                        timestamp = timestamp,
                        name = "Balanced Strategy",
                    )
            }

        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading
            repository.saveConfig(config)
                .onSuccess {
                    _uiState.value = SettingsUiState.Success("${preset.displayName} preset created")
                }
                .onFailure { error ->
                    _uiState.value = SettingsUiState.Error(error.message ?: "Failed to create preset")
                }
        }
    }

    /**
     * Duplicates an existing configuration.
     */
    fun duplicateConfig(configId: String) {
        viewModelScope.launch {
            val original = repository.getConfigById(configId)
            if (original != null) {
                val timestamp = Clock.System.now()
                val duplicate =
                    original.copy(
                        configId = "config-${timestamp.toEpochMilliseconds()}",
                        name = "${original.name} (Copy)",
                        isDefault = false,
                        createdAt = timestamp,
                        updatedAt = timestamp,
                    )

                _uiState.value = SettingsUiState.Loading
                repository.saveConfig(duplicate)
                    .onSuccess {
                        _uiState.value = SettingsUiState.Success("Configuration duplicated")
                    }
                    .onFailure { error ->
                        _uiState.value = SettingsUiState.Error(error.message ?: "Failed to duplicate")
                    }
            } else {
                _uiState.value = SettingsUiState.Error("Configuration not found")
            }
        }
    }

    /**
     * Cleanup when ViewModel is cleared
     */
    fun onCleared() {
        viewModelScope.cancel()
    }
}

/**
 * UI state for settings screen.
 */
sealed class SettingsUiState {
    object Idle : SettingsUiState()

    object Loading : SettingsUiState()

    data class Success(val message: String) : SettingsUiState()

    data class Error(val message: String) : SettingsUiState()
}

/**
 * Configuration presets.
 */
enum class ConfigPreset(val displayName: String) {
    CONSERVATIVE("Conservative"),
    BALANCED("Balanced"),
    AGGRESSIVE("Aggressive"),
}
