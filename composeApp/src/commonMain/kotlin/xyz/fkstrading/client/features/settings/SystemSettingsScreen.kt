package xyz.fkstrading.client.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * System Settings Screen for Discord webhook and general configuration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemSettingsScreen(
    viewModel: SystemSettingsViewModel,
    onNavigateBack: () -> Unit = {},
) {
    val discordWebhookUrl by viewModel.discordWebhookUrl.collectAsState()
    val discordEnabled by viewModel.discordEnabled.collectAsState()
    val notifyOnSignal by viewModel.notifyOnSignal.collectAsState()
    val notifyOnFill by viewModel.notifyOnFill.collectAsState()
    val notifyOnError by viewModel.notifyOnError.collectAsState()
    val executionMode by viewModel.executionMode.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val testResult by viewModel.testResult.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.Share, "Export Settings")
                    }
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Default.Refresh, "Reset to Defaults")
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState =
                    remember { SnackbarHostState() }.apply {
                        LaunchedEffect(uiState) {
                            when (val state = uiState) {
                                is SystemSettingsUiState.Success -> {
                                    showSnackbar(state.message)
                                }
                                is SystemSettingsUiState.Error -> {
                                    showSnackbar(
                                        message = state.message,
                                        withDismissAction = true,
                                    )
                                }
                                else -> {}
                            }
                        }
                    },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Discord Notifications Section
            DiscordSettingsSection(
                webhookUrl = discordWebhookUrl,
                enabled = discordEnabled,
                notifyOnSignal = notifyOnSignal,
                notifyOnFill = notifyOnFill,
                notifyOnError = notifyOnError,
                testResult = testResult,
                onWebhookUrlChange = { viewModel.updateDiscordWebhookUrl(it) },
                onEnabledChange = { viewModel.toggleDiscordEnabled(it) },
                onNotifyOnSignalChange = { viewModel.toggleNotifyOnSignal(it) },
                onNotifyOnFillChange = { viewModel.toggleNotifyOnFill(it) },
                onNotifyOnErrorChange = { viewModel.toggleNotifyOnError(it) },
                onTestWebhook = { viewModel.testDiscordWebhook() },
                onClearTestResult = { viewModel.clearTestResult() },
            )

            Divider()

            // Execution Mode Section
            ExecutionModeSection(
                executionMode = executionMode,
                onExecutionModeChange = { viewModel.updateExecutionMode(it) },
            )

            Divider()

            // Help Section
            HelpSection()
        }
    }

    // Export Dialog
    if (showExportDialog) {
        ExportSettingsDialog(
            envVars = viewModel.exportAsEnvVars(),
            onDismiss = { showExportDialog = false },
        )
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        ResetConfirmationDialog(
            onDismiss = { showResetDialog = false },
            onConfirm = {
                viewModel.resetToDefaults()
                showResetDialog = false
            },
        )
    }
}

@Composable
private fun DiscordSettingsSection(
    webhookUrl: String,
    enabled: Boolean,
    notifyOnSignal: Boolean,
    notifyOnFill: Boolean,
    notifyOnError: Boolean,
    testResult: TestResult?,
    onWebhookUrlChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onNotifyOnSignalChange: (Boolean) -> Unit,
    onNotifyOnFillChange: (Boolean) -> Unit,
    onNotifyOnErrorChange: (Boolean) -> Unit,
    onTestWebhook: () -> Unit,
    onClearTestResult: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Discord Notifications",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }

            Text(
                "Get real-time notifications about trading signals, order fills, and errors in your Discord channel.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Webhook URL Input
            OutlinedTextField(
                value = webhookUrl,
                onValueChange = onWebhookUrlChange,
                label = { Text("Discord Webhook URL") },
                placeholder = { Text("https://discord.com/api/webhooks/...") },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                leadingIcon = {
                    Icon(Icons.Default.Link, contentDescription = null)
                },
                trailingIcon = {
                    if (webhookUrl.isNotBlank()) {
                        IconButton(onClick = { onWebhookUrlChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                supportingText = {
                    Text("Get webhook URL from Discord: Server Settings → Integrations → Webhooks")
                },
                singleLine = true,
            )

            // Test Webhook Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onTestWebhook,
                    enabled = enabled && webhookUrl.isNotBlank() && testResult !is TestResult.Testing,
                    modifier = Modifier.weight(1f),
                ) {
                    when (testResult) {
                        is TestResult.Testing -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Testing...")
                        }
                        else -> {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Test Webhook")
                        }
                    }
                }
            }

            // Test Result
            testResult?.let { result ->
                when (result) {
                    is TestResult.Success -> {
                        Card(
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        result.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                                IconButton(onClick = onClearTestResult) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss")
                                }
                            }
                        }
                    }
                    is TestResult.Error -> {
                        Card(
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                ),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                    Text(
                                        result.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                                IconButton(onClick = onClearTestResult) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss")
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }

            // Notification Preferences
            if (enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Notification Preferences",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )

                    NotificationToggle(
                        icon = Icons.Default.Phone,
                        label = "Signal Received",
                        description = "Notify when trading signals are generated",
                        checked = notifyOnSignal,
                        onCheckedChange = onNotifyOnSignalChange,
                    )

                    NotificationToggle(
                        icon = Icons.Default.Check,
                        label = "Order Filled",
                        description = "Notify when orders are executed",
                        checked = notifyOnFill,
                        onCheckedChange = onNotifyOnFillChange,
                    )

                    NotificationToggle(
                        icon = Icons.Default.Warning,
                        label = "Errors",
                        description = "Notify when errors occur",
                        checked = notifyOnError,
                        onCheckedChange = onNotifyOnErrorChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ExecutionModeSection(
    executionMode: ExecutionMode,
    onExecutionModeChange: (ExecutionMode) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Execution Mode",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                "Choose how trading signals are executed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExecutionModeOption(
                    mode = ExecutionMode.SIMULATED,
                    title = "Simulated",
                    description = "Test with simulated execution (no real money)",
                    icon = "🧪",
                    selected = executionMode == ExecutionMode.SIMULATED,
                    onClick = { onExecutionModeChange(ExecutionMode.SIMULATED) },
                )

                ExecutionModeOption(
                    mode = ExecutionMode.PAPER,
                    title = "Paper Trading",
                    description = "Real exchange API with testnet account",
                    icon = "📝",
                    selected = executionMode == ExecutionMode.PAPER,
                    onClick = { onExecutionModeChange(ExecutionMode.PAPER) },
                )

                ExecutionModeOption(
                    mode = ExecutionMode.LIVE,
                    title = "Live Trading",
                    description = "⚠️ CAUTION: Real money, real trades",
                    icon = "💰",
                    selected = executionMode == ExecutionMode.LIVE,
                    onClick = { onExecutionModeChange(ExecutionMode.LIVE) },
                    warning = true,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExecutionModeOption(
    mode: ExecutionMode,
    title: String,
    description: String,
    icon: String,
    selected: Boolean,
    onClick: () -> Unit,
    warning: Boolean = false,
) {
    Card(
        onClick = onClick,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        if (warning) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ),
        border =
            if (selected) {
                CardDefaults.outlinedCardBorder()
            } else {
                null
            },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                icon,
                style = MaterialTheme.typography.headlineMedium,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint =
                        if (warning) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                )
            }
        }
    }
}

@Composable
private fun HelpSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Help & Documentation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            HelpItem(
                title = "How to get Discord Webhook URL",
                steps =
                    listOf(
                        "1. Go to your Discord server",
                        "2. Click Server Settings → Integrations",
                        "3. Click Webhooks → New Webhook",
                        "4. Configure name and channel",
                        "5. Copy Webhook URL and paste above",
                    ),
            )

            HelpItem(
                title = "Recommended Testing Workflow",
                steps =
                    listOf(
                        "1. Start with SIMULATED mode",
                        "2. Test for 1+ week with simulated trades",
                        "3. Move to PAPER mode (testnet)",
                        "4. Validate for 2+ weeks",
                        "5. Only then consider LIVE (with caution!)",
                    ),
            )
        }
    }
}

@Composable
private fun HelpItem(
    title: String,
    steps: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        steps.forEach { step ->
            Text(
                step,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun ExportSettingsDialog(
    envVars: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Copy these environment variables to your backend .env file:")
                OutlinedTextField(
                    value = envVars,
                    onValueChange = {},
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                    readOnly = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun ResetConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
        title = { Text("Reset to Defaults?") },
        text = { Text("This will reset all system settings to their default values. Your Discord webhook URL will be cleared.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text("Reset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
