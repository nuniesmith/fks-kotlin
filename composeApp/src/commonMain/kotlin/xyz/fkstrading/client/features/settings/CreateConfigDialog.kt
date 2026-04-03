package xyz.fkstrading.client.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.fkstrading.shared.domain.strategy.models.*

/**
 * Dialog for creating or editing a custom strategy configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateConfigDialog(
    initialConfig: ExecutionConfig? = null,
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, ExecutionConfig) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var positionSizingMethod by remember {
        mutableStateOf(
            initialConfig?.positionSizingMethod ?: PositionSizingMethod.RISK_BASED
        )
    }
    var riskPerTrade by remember { mutableStateOf((initialConfig?.riskPerTrade ?: 0.02) * 100) }
    var stopLossPercent by remember { mutableStateOf((initialConfig?.stopLossPercentage ?: 0.02) * 100) }
    var takeProfitPercent by remember { mutableStateOf((initialConfig?.takeProfitPercentage ?: 0.04) * 100) }
    var maxPositions by remember { mutableStateOf(initialConfig?.maxPositions ?: 10) }
    var minConfidence by remember { mutableStateOf((initialConfig?.minSignalConfidence ?: 0.6) * 100) }
    var requireConfirmation by remember { mutableStateOf(initialConfig?.requireConfirmation ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 600.dp),
        title = { Text(if (initialConfig == null) "Create Configuration" else "Edit Configuration") },
        text = {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Configuration Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                HorizontalDivider()

                // Execution Mode Info
                Text("Execution Mode", style = MaterialTheme.typography.titleSmall)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("AUTO", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Fully automated execution based on configuration parameters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Require Confirmation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text("Require Manual Confirmation")
                    Switch(
                        checked = requireConfirmation,
                        onCheckedChange = { requireConfirmation = it },
                    )
                }

                HorizontalDivider()

                // Position Sizing
                Text("Position Sizing Method", style = MaterialTheme.typography.titleSmall)
                PositionSizingMethod.entries.forEach { method ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(
                            selected = positionSizingMethod == method,
                            onClick = { positionSizingMethod = method },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(method.name.replace("_", " "), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Risk per Trade
                Text("Risk Per Trade: ${riskPerTrade.toInt()}%")
                Slider(
                    value = riskPerTrade.toFloat(),
                    onValueChange = { riskPerTrade = it.toDouble() },
                    valueRange = 0.5f..10f,
                    steps = 19,
                )

                HorizontalDivider()

                // Stop Loss
                Text("Stop Loss: ${stopLossPercent.toInt()}%")
                Slider(
                    value = stopLossPercent.toFloat(),
                    onValueChange = { stopLossPercent = it.toDouble() },
                    valueRange = 0.5f..20f,
                    steps = 39,
                )

                // Take Profit
                Text("Take Profit: ${takeProfitPercent.toInt()}%")
                Slider(
                    value = takeProfitPercent.toFloat(),
                    onValueChange = { takeProfitPercent = it.toDouble() },
                    valueRange = 1f..30f,
                    steps = 29,
                )

                HorizontalDivider()

                // Max Positions
                Text("Max Concurrent Positions: $maxPositions")
                Slider(
                    value = maxPositions.toFloat(),
                    onValueChange = { maxPositions = it.toInt() },
                    valueRange = 1f..20f,
                    steps = 19,
                )

                // Min Confidence
                Text("Minimum Signal Confidence: ${minConfidence.toInt()}%")
                Slider(
                    value = minConfidence.toFloat(),
                    onValueChange = { minConfidence = it.toDouble() },
                    valueRange = 50f..95f,
                    steps = 9,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val config =
                        ExecutionConfig(
                            positionSizingMethod = positionSizingMethod,
                            riskPerTrade = riskPerTrade / 100.0,
                            stopLossPercentage = stopLossPercent / 100.0,
                            takeProfitPercentage = takeProfitPercent / 100.0,
                            maxPositions = maxPositions,
                            minSignalConfidence = minConfidence / 100.0,
                            requireConfirmation = requireConfirmation,
                        )
                    onConfirm(name, config)
                },
                enabled = name.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
