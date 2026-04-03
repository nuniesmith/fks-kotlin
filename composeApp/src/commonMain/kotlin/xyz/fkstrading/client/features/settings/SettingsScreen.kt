package xyz.fkstrading.client.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.fkstrading.shared.domain.models.StrategyConfig

/**
 * Main settings screen for managing strategy configurations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: StrategyConfigViewModel,
    onNavigateBack: () -> Unit = {},
) {
    val configs by viewModel.configs.collectAsState()
    val defaultConfig by viewModel.defaultConfig.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showPresetDialog by remember { mutableStateOf(false) }
    var configToDelete by remember { mutableStateOf<StrategyConfig?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Strategy Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showPresetDialog = true }) {
                        Icon(Icons.Default.Add, "Add Preset")
                    }
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Settings, "Create Custom")
                    }
                },
            )
        },
        snackbarHost = {
            // Show snackbar for success/error messages
            when (uiState) {
                is SettingsUiState.Success -> {
                    LaunchedEffect(uiState) {
                        // Snackbar would show here
                        viewModel.resetUiState()
                    }
                }
                is SettingsUiState.Error -> {
                    LaunchedEffect(uiState) {
                        // Error snackbar would show here
                        viewModel.resetUiState()
                    }
                }
                else -> {}
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState is SettingsUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                configs.isEmpty() -> {
                    EmptyConfigsView(
                        onCreatePreset = { showPresetDialog = true },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    ConfigurationList(
                        configs = configs,
                        defaultConfigId = defaultConfig?.configId,
                        onSetDefault = { viewModel.setAsDefault(it) },
                        onToggleActive = { id, active -> viewModel.toggleActive(id, active) },
                        onEdit = { viewModel.selectConfig(it) },
                        onDuplicate = { viewModel.duplicateConfig(it.configId) },
                        onDelete = { configToDelete = it },
                    )
                }
            }
        }
    }

    // Dialogs
    if (showCreateDialog) {
        CreateConfigDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, config ->
                viewModel.createConfig(name, config)
                showCreateDialog = false
            },
        )
    }

    if (showPresetDialog) {
        PresetSelectionDialog(
            onDismiss = { showPresetDialog = false },
            onSelect = { preset ->
                viewModel.createPreset(preset)
                showPresetDialog = false
            },
        )
    }

    configToDelete?.let { config ->
        DeleteConfirmationDialog(
            configName = config.name,
            onDismiss = { configToDelete = null },
            onConfirm = {
                viewModel.deleteConfig(config.configId)
                configToDelete = null
            },
        )
    }
}

@Composable
private fun EmptyConfigsView(
    onCreatePreset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            "No Strategy Configurations",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "Create your first strategy configuration to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onCreatePreset) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Create Preset")
        }
    }
}

@Composable
private fun ConfigurationList(
    configs: List<StrategyConfig>,
    defaultConfigId: String?,
    onSetDefault: (String) -> Unit,
    onToggleActive: (String, Boolean) -> Unit,
    onEdit: (StrategyConfig) -> Unit,
    onDuplicate: (StrategyConfig) -> Unit,
    onDelete: (StrategyConfig) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(configs, key = { it.configId }) { config ->
            ConfigCard(
                config = config,
                isDefault = config.configId == defaultConfigId,
                onSetDefault = { onSetDefault(config.configId) },
                onToggleActive = { onToggleActive(config.configId, !config.isActive) },
                onEdit = { onEdit(config) },
                onDuplicate = { onDuplicate(config) },
                onDelete = { onDelete(config) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigCard(
    config: StrategyConfig,
    isDefault: Boolean,
    onSetDefault: () -> Unit,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            config.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (isDefault) {
                            Spacer(Modifier.width(8.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text("Default", style = MaterialTheme.typography.labelSmall) },
                                colors =
                                    AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    ),
                            )
                        }
                    }
                    config.description?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Switch(
                    checked = config.isActive,
                    onCheckedChange = { onToggleActive() },
                )
            }

            // Config details (when expanded)
            if (expanded) {
                Divider(modifier = Modifier.padding(vertical = 12.dp))

                ConfigDetails(config)

                Spacer(Modifier.height(12.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!isDefault) {
                        OutlinedButton(
                            onClick = onSetDefault,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Set Default")
                        }
                    }

                    OutlinedButton(
                        onClick = onDuplicate,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Duplicate")
                    }

                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit")
                    }

                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigDetails(config: StrategyConfig) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailRow("Execution Mode", config.executionConfig.mode.name)
        DetailRow("Position Sizing", config.executionConfig.positionSizingMethod.name)
        DetailRow("Risk Per Trade", "${(config.executionConfig.riskPerTrade * 100).toInt()}%")
        DetailRow("Stop Loss", config.executionConfig.stopLossPercentage?.let { "${(it * 100).toInt()}%" } ?: "ATR-based")
        DetailRow("Take Profit", config.executionConfig.takeProfitPercentage?.let { "${(it * 100).toInt()}%" } ?: "RR ratio")
        DetailRow("Max Positions", config.executionConfig.maxPositions.toString())
        DetailRow("Min Confidence", "${(config.executionConfig.minSignalConfidence * 100).toInt()}%")
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DeleteConfirmationDialog(
    configName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Configuration?") },
        text = { Text("Are you sure you want to delete '$configName'? This action cannot be undone.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
