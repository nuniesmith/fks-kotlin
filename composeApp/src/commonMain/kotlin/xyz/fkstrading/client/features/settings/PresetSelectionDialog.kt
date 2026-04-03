package xyz.fkstrading.client.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Dialog for selecting a configuration preset.
 */
@Composable
fun PresetSelectionDialog(
    onDismiss: () -> Unit,
    onSelect: (ConfigPreset) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a Preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Select a pre-configured strategy to get started quickly",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ConfigPreset.values().forEach { preset ->
                    PresetCard(
                        preset = preset,
                        onClick = { onSelect(preset) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetCard(
    preset: ConfigPreset,
    onClick: () -> Unit,
) {
    val (icon, title, description, details) =
        when (preset) {
            ConfigPreset.CONSERVATIVE ->
                PresetInfo(
                    icon = Icons.Default.Shield,
                    title = "Conservative",
                    description = "Low risk, requires confirmation",
                    details = listOf("1% risk per trade", "Confirmation required", "High confidence threshold"),
                )

            ConfigPreset.BALANCED ->
                PresetInfo(
                    icon = Icons.Default.Balance,
                    title = "Balanced",
                    description = "Moderate risk, automated",
                    details = listOf("2% risk per trade", "Auto execution", "Medium confidence threshold"),
                )

            ConfigPreset.AGGRESSIVE ->
                PresetInfo(
                    icon = Icons.Default.TrendingUp,
                    title = "Aggressive",
                    description = "Higher risk, automated execution",
                    details = listOf("3% risk per trade", "Auto execution", "Lower confidence threshold"),
                )
        }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
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
                Spacer(Modifier.height(4.dp))
                details.forEach { detail ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Select",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class PresetInfo(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val description: String,
    val details: List<String>,
)
