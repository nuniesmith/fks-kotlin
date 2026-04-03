package xyz.fkstrading.client.features.realtime

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import kotlinx.datetime.Instant
import org.koin.compose.koinInject
import xyz.fkstrading.shared.data.websocket.ConnectionState
import xyz.fkstrading.shared.domain.models.Direction
import xyz.fkstrading.shared.domain.models.Signal

/**
 * Real-Time Signals Screen
 *
 * Displays live WebSocket connection status and real-time trading signals.
 * Users can connect/disconnect and subscribe to different channels.
 */
class RealTimeSignalsScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: RealTimeSignalsViewModel = koinInject()
        RealTimeSignalsContent(viewModel)
    }
}

@Composable
fun RealTimeSignalsContent(viewModel: RealTimeSignalsViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val signals by viewModel.signals.collectAsState()
    val activeSubscriptions by viewModel.activeSubscriptions.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val statistics by viewModel.statistics.collectAsState()

    val listState = rememberLazyListState()

    // Auto-scroll to top when new signals arrive
    LaunchedEffect(signals.size) {
        if (uiState.autoScroll && signals.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            ConnectionStatusBar(
                connectionState = connectionState,
                statistics = statistics,
            )
        },
        floatingActionButton = {
            ConnectionFab(
                connectionState = connectionState,
                onConnect = { viewModel.connect() },
                onDisconnect = { viewModel.disconnect() },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            // Error banner
            uiState.error?.let { error ->
                ErrorBanner(
                    message = error,
                    onDismiss = { viewModel.dismissError() },
                )
            }

            // Channel subscription controls
            ChannelSubscriptionPanel(
                activeSubscriptions = activeSubscriptions,
                onToggleSubscription = { channel -> viewModel.toggleSubscription(channel) },
                enabled = connectionState is ConnectionState.Connected,
            )

            Divider()

            // Signals header with clear button
            SignalsHeader(
                signalCount = signals.size,
                onClear = { viewModel.clearSignals() },
            )

            // Signals list
            if (signals.isEmpty()) {
                EmptySignalsState(connectionState)
            } else {
                SignalsList(
                    signals = signals,
                    listState = listState,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionStatusBar(
    connectionState: ConnectionState,
    statistics: ConnectionStatistics,
) {
    val (statusColor, statusText) =
        when (connectionState) {
            is ConnectionState.Connected -> Color(0xFF4CAF50) to "Connected"
            is ConnectionState.Connecting -> Color(0xFFFFC107) to "Connecting..."
            is ConnectionState.Disconnected -> Color(0xFFF44336) to "Disconnected"
            is ConnectionState.Error -> Color(0xFFF44336) to "Error: ${connectionState.error?.message ?: "Unknown"}"
        }

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Status indicator
                Box(
                    modifier =
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                )

                Column {
                    Text(
                        text = "Real-Time Signals",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        actions = {
            if (connectionState is ConnectionState.Connected) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(
                        text = "${statistics.messagesReceived} msgs",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = formatUptime(statistics.uptimeSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    )
}

@Composable
fun ConnectionFab(
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val (emoji, onClick, containerColor) =
        when (connectionState) {
            is ConnectionState.Connected -> {
                Triple("✕", onDisconnect, Color(0xFFF44336))
            }
            is ConnectionState.Disconnected -> {
                Triple("▶", onConnect, Color(0xFF4CAF50))
            }
            else -> {
                Triple("↻", {}, Color.Gray)
            }
        }

    FloatingActionButton(
        onClick = onClick,
        containerColor = containerColor,
        contentColor = Color.White,
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
        )
    }
}

@Composable
fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "⚠️",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(onClick = onDismiss) {
                Text("✕", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun ChannelSubscriptionPanel(
    activeSubscriptions: Set<String>,
    onToggleSubscription: (String) -> Unit,
    enabled: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Channels",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Channels.ALL.forEach { channel ->
                    ChannelChip(
                        channel = channel,
                        isSubscribed = channel in activeSubscriptions,
                        onClick = { onToggleSubscription(channel) },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelChip(
    channel: String,
    isSubscribed: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    FilterChip(
        selected = isSubscribed,
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = channel.replace("_", " ").uppercase(),
                style = MaterialTheme.typography.labelSmall,
            )
        },
        leadingIcon =
            if (isSubscribed) {
                {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            } else {
                null
            },
    )
}

@Composable
fun SignalsHeader(
    signalCount: Int,
    onClear: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Signals ($signalCount)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (signalCount > 0) {
            TextButton(onClick = onClear) {
                Text("🗑️", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear")
            }
        }
    }
}

@Composable
fun EmptySignalsState(connectionState: ConnectionState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text =
                    when (connectionState) {
                        is ConnectionState.Connected -> "🔍"
                        is ConnectionState.Disconnected -> "⚠️"
                        is ConnectionState.Error -> "❌"
                        else -> "↻"
                    },
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                text =
                    when (connectionState) {
                        is ConnectionState.Connected -> "No signals yet"
                        is ConnectionState.Disconnected -> "Disconnected"
                        is ConnectionState.Connecting -> "Connecting..."
                        is ConnectionState.Error -> "Error"
                    },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    when (connectionState) {
                        is ConnectionState.Connected -> "Waiting for signals from subscribed channels"
                        is ConnectionState.Disconnected -> "Connect to start receiving signals"
                        is ConnectionState.Connecting -> "Establishing connection..."
                        is ConnectionState.Error -> connectionState.error?.message ?: "Unknown error"
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SignalsList(
    signals: List<Signal>,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = signals,
            key = { it.signalId },
        ) { signal ->
            SignalCard(signal)
        }
    }
}

@Composable
fun SignalCard(signal: Signal) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Header: Symbol and Type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = signal.direction.emoji(),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = signal.symbol,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                DirectionBadge(signal.direction)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Price levels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                PriceInfo("Entry", String.format("%.4f", signal.entryPrice), MaterialTheme.colorScheme.primary)
                PriceInfo("Stop Loss", String.format("%.4f", signal.stopLoss), MaterialTheme.colorScheme.error)
                PriceInfo("Take Profit", String.format("%.4f", signal.takeProfit), MaterialTheme.colorScheme.tertiary)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Confidence and risk/reward
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Confidence: ",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "${(signal.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = confidenceColor(signal.confidence),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = getConfidenceStars(signal.confidence),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                signal.riskRewardRatio?.let { rr ->
                    Text(
                        text = "R:R ${String.format("%.1f", rr)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Timestamp
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatTimestamp(signal.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun DirectionBadge(direction: Direction) {
    val (color, text) =
        when (direction) {
            Direction.LONG -> Color(0xFF4CAF50) to "LONG"
            Direction.SHORT -> Color(0xFFF44336) to "SHORT"
        }

    Surface(
        color = color,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
fun PriceInfo(
    label: String,
    value: String,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

// Helper functions
private fun Direction.emoji(): String =
    when (this) {
        Direction.LONG -> "📈"
        Direction.SHORT -> "📉"
    }

private fun getConfidenceStars(confidence: Double): String {
    val stars = (confidence * 5).toInt()
    return "⭐".repeat(stars)
}

private fun confidenceColor(confidence: Double): Color =
    when {
        confidence >= 0.8 -> Color(0xFF4CAF50)
        confidence >= 0.6 -> Color(0xFFFFC107)
        else -> Color(0xFFFF9800)
    }

private fun formatUptime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}

private fun formatTimestamp(timestamp: Instant): String {
    // Format Instant as time string (HH:mm:ss)
    val timeString = timestamp.toString()
    return timeString.substringAfter("T").substringBefore(".")
}
