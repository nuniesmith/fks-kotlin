# Strategy Settings Feature

## Overview

This feature provides a comprehensive UI for managing trading strategy configurations in the FKS application.

## Components

### ViewModels
- **StrategyConfigViewModel**: Main ViewModel for settings management
  - Manages configuration CRUD operations
  - Provides reactive StateFlows for UI
  - Handles preset creation
  - Coordinates with StrategyConfigRepository

### UI Screens
- **SettingsScreen**: Main settings interface
  - Configuration list display
  - Search and filter (future)
  - Empty state handling
  - Snackbar notifications

### Dialogs
- **PresetSelectionDialog**: Choose from predefined presets
  - Conservative, Balanced, Aggressive templates
  - Visual preset cards with descriptions
  
- **CreateConfigDialog**: Create/edit custom configurations
  - Comprehensive parameter editor
  - Real-time validation
  - Slider-based inputs for ease of use
  
- **DeleteConfirmationDialog**: Confirm destructive actions

## Architecture

```
UI Layer (Compose)
    ↓
ViewModel Layer (StrategyConfigViewModel)
    ↓
Repository Layer (StrategyConfigRepository)
    ↓
Data Layer (SQLDelight + DatabaseWrapper)
```

## State Management

### UI State
```kotlin
sealed class SettingsUiState {
    object Idle
    object Loading
    data class Success(val message: String)
    data class Error(val message: String)
}
```

### Reactive Streams
- `configs: StateFlow<List<StrategyConfig>>`
- `activeConfigs: StateFlow<List<StrategyConfig>>`
- `defaultConfig: StateFlow<StrategyConfig?>`
- `uiState: StateFlow<SettingsUiState>`

## Usage

### Basic Setup
```kotlin
// In your DI module
val settingsModule = module {
    viewModel { StrategyConfigViewModel(get()) }
}

// In your composable
val viewModel: StrategyConfigViewModel = koinViewModel()

SettingsScreen(
    viewModel = viewModel,
    onNavigateBack = { navController.popBackStack() }
)
```

### Navigation Integration
```kotlin
// Add to your navigation graph
composable("settings") {
    val viewModel: StrategyConfigViewModel = koinViewModel()
    SettingsScreen(
        viewModel = viewModel,
        onNavigateBack = { navController.navigateUp() }
    )
}
```

## Features

### ✅ Implemented
- Create custom configurations
- Use preset templates (Conservative, Balanced, Aggressive)
- Edit existing configurations
- Set default configuration
- Toggle active/inactive status
- Duplicate configurations
- Delete configurations
- Reactive UI updates
- Persistent storage

### 🚧 Future Enhancements
- Import/Export configurations (JSON)
- Remote sync
- Performance analytics per config
- Configuration templates gallery
- Batch operations
- Search and filter
- Version history

## Configuration Parameters

All parameters are adjustable via the UI:

### Execution Settings
- **Mode**: MANUAL, SEMI_AUTO, AUTO
- **Require Confirmation**: Boolean

### Position Sizing
- **Method**: FIXED, PERCENTAGE, RISK_BASED, KELLY_CRITERION
- **Risk Per Trade**: 0.5% - 10%

### Risk Management
- **Stop Loss**: 0.5% - 20%
- **Take Profit**: 1% - 30%

### Limits
- **Max Positions**: 1 - 20
- **Min Confidence**: 50% - 95%

## Integration

### With Execution Engine
```kotlin
// Execute with default config
executor.executeWithDefaultConfig(signal, accountBalance, currentPrice)

// Execute with specific config
executor.executeWithConfig(signal, configId, accountBalance, currentPrice)
```

### With Repository
```kotlin
// Observe all configs
repository.observeAllConfigs().collect { configs ->
    // Update UI
}

// Set default
repository.setAsDefault(configId)

// Save config
repository.saveConfig(config)
```

## Testing

Run tests:
```bash
cd src/clients
./gradlew :composeApp:testDebugUnitTest
```

Test files:
- `StrategyConfigViewModelTest.kt` (to be created)
- Integration tests with repository
- UI tests for composables

## Documentation

- [UI User Guide](../../../../docs/ui/strategy-settings-ui.md)
- [Integration Guide](../../../../docs/integration/strategy-executor-config-integration.md)
- [Test Summary](../../../../docs/testing/strategy-execution-test-summary.md)

## Dependencies

```kotlin
// In shared module
implementation("app.cash.sqldelight:*")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:*")
implementation("org.jetbrains.kotlinx:kotlinx-datetime:*")

// In composeApp module
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:*")
implementation("androidx.compose.material3:material3:*")
implementation("io.insert-koin:koin-compose:*")
```

## Contributing

When adding new features:
1. Update ViewModel with new operations
2. Add UI components as needed
3. Update documentation
4. Add tests
5. Update this README

## License

See project root LICENSE file.
