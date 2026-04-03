package xyz.fkstrading.client.features.portfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.datetime.Instant
import org.koin.compose.koinInject
import xyz.fkstrading.shared.domain.models.Position
import xyz.fkstrading.shared.domain.models.PositionStatus
import xyz.fkstrading.shared.domain.models.isClosed
import xyz.fkstrading.shared.domain.models.isLong
import xyz.fkstrading.shared.domain.models.isOpen
import xyz.fkstrading.shared.domain.models.pnlPercentage
import kotlin.math.abs

/**
 * Voyager Screen for Portfolio/Positions
 *
 * Displays:
 * - Portfolio metrics summary
 * - List of positions (open/closed)
 * - Position details
 * - Filtering and sorting options
 */
class PositionsScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: PortfolioViewModel = koinInject()
        val navigator = LocalNavigator.currentOrThrow

        PositionsScreenContent(
            viewModel = viewModel,
            onNavigateBack = { navigator.pop() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PositionsScreenContent(
    viewModel: PortfolioViewModel,
    onNavigateBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    val metrics by viewModel.portfolioMetrics.collectAsState()

    var showFilters by remember { mutableStateOf(false) }
    var selectedPosition by remember { mutableStateOf<Position?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Portfolio") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            if (showFilters) Icons.Default.FilterListOff else Icons.Default.FilterList,
                            "Toggle filters",
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            // Portfolio Metrics Summary
            PortfolioMetricsCard(metrics)

            // Filters (if shown)
            if (showFilters) {
                FilterSection(
                    filterState = filterState,
                    onUpdateFilter = { viewModel.updateFilter(it) },
                    onClearFilters = { viewModel.clearFilters() },
                )
            }

            // Positions List
            when (val state = uiState) {
                is PortfolioUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is PortfolioUiState.Empty -> {
                    EmptyPositionsView()
                }
                is PortfolioUiState.Success -> {
                    PositionsList(
                        positions = state.positions,
                        onPositionClick = { selectedPosition = it },
                        onClosePosition = { viewModel.closePosition(it.positionId) },
                    )
                }
                is PortfolioUiState.Error -> {
                    ErrorView(
                        message = state.message,
                        onRetry = { viewModel.refresh() },
                    )
                }
            }
        }
    }

    // Position Details Dialog
    selectedPosition?.let { position ->
        PositionDetailsDialog(
            position = position,
            onDismiss = { selectedPosition = null },
            onClose = {
                viewModel.closePosition(position.positionId)
                selectedPosition = null
            },
            onDelete = {
                viewModel.deletePosition(position.positionId)
                selectedPosition = null
            },
        )
    }
}

@Composable
fun PortfolioMetricsCard(metrics: PortfolioMetrics) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Total P&L
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Total P&L",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = formatCurrency(metrics.totalPnL),
                    style = MaterialTheme.typography.headlineSmall,
                    color =
                        if (metrics.isProfitable()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MetricItem(
                    label = "Open",
                    value = metrics.openPositions.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricItem(
                    label = "Closed",
                    value = metrics.closedPositions.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricItem(
                    label = "Win Rate",
                    value = "${String.format("%.1f", metrics.winRate)}%",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MetricItem(
                    label = "Unrealized",
                    value = formatCurrency(metrics.totalUnrealizedPnL),
                    modifier = Modifier.weight(1f),
                )
                MetricItem(
                    label = "Realized",
                    value = formatCurrency(metrics.totalRealizedPnL),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun MetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun FilterSection(
    filterState: PositionFilter,
    onUpdateFilter: (PositionFilter) -> Unit,
    onClearFilters: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Filters", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onClearFilters) {
                    Text("Clear")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status Filters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filterState.showOpenOnly,
                    onClick = {
                        onUpdateFilter(
                            filterState.copy(
                                showOpenOnly = !filterState.showOpenOnly,
                                showClosedOnly = false,
                            ),
                        )
                    },
                    label = { Text("Open Only") },
                )
                FilterChip(
                    selected = filterState.showClosedOnly,
                    onClick = {
                        onUpdateFilter(
                            filterState.copy(
                                showClosedOnly = !filterState.showClosedOnly,
                                showOpenOnly = false,
                            ),
                        )
                    },
                    label = { Text("Closed Only") },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sort Options
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SortOption.values().take(3).forEach { option ->
                    FilterChip(
                        selected = filterState.sortBy == option,
                        onClick = { onUpdateFilter(filterState.copy(sortBy = option)) },
                        label = { Text(option.displayName()) },
                    )
                }
            }
        }
    }
}

@Composable
fun PositionsList(
    positions: List<Position>,
    onPositionClick: (Position) -> Unit,
    onClosePosition: (Position) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = positions,
            key = { it.positionId },
        ) { position ->
            PositionCard(
                position = position,
                onClick = { onPositionClick(position) },
                onClose = { onClosePosition(position) },
            )
        }
    }
}

@Composable
fun PositionCard(
    position: Position,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Header: Symbol and Status
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
                        text = position.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = position.side.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (position.isLong()) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier =
                            Modifier
                                .background(
                                    color =
                                        if (position.isLong()) {
                                            Color(0xFF4CAF50).copy(alpha = 0.1f)
                                        } else {
                                            Color(0xFFF44336).copy(alpha = 0.1f)
                                        },
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                // Status Badge
                if (position.status == PositionStatus.OPEN) {
                    AssistChip(
                        onClick = {},
                        label = { Text("OPEN", style = MaterialTheme.typography.labelSmall) },
                        colors =
                            AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Position Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Qty: ${formatNumber(position.quantity)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Entry: ${formatCurrency(position.entryPrice)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Current: ${formatCurrency(position.currentPrice)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (position.isOpen()) {
                        val pnl = position.unrealizedPnL
                        val pnlPercent = position.pnlPercentage()
                        Text(
                            text = "${formatCurrency(pnl)} (${formatPercent(pnlPercent)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pnl >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        val pnl = position.realizedPnL
                        Text(
                            text = "Realized: ${formatCurrency(pnl)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pnl >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            // Stop Loss / Take Profit
            if (position.stopLoss != null || position.takeProfit != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    position.stopLoss?.let {
                        Text(
                            text = "SL: ${formatCurrency(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    position.takeProfit?.let {
                        Text(
                            text = "TP: ${formatCurrency(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Close Button (for open positions)
            if (position.isOpen()) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(Icons.Default.Close, "Close position", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Close Position")
                }
            }
        }
    }
}

@Composable
fun EmptyPositionsView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Default.AccountBalance,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = "No positions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Your positions will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "Error loading positions",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun PositionDetailsDialog(
    position: Position,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${position.symbol} - ${position.side.name}")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailRow("Position ID", position.positionId)
                DetailRow("Quantity", formatNumber(position.quantity))
                DetailRow("Entry Price", formatCurrency(position.entryPrice))
                DetailRow("Current Price", formatCurrency(position.currentPrice))

                position.stopLoss?.let {
                    DetailRow("Stop Loss", formatCurrency(it))
                }
                position.takeProfit?.let {
                    DetailRow("Take Profit", formatCurrency(it))
                }

                DetailRow("Status", position.status.name)
                DetailRow("Opened", formatTimestamp(position.openedAt))

                if (position.isOpen()) {
                    DetailRow(
                        "Unrealized P&L",
                        "${formatCurrency(position.unrealizedPnL)} (${formatPercent(position.pnlPercentage())})",
                    )
                } else {
                    position.closedAt?.let {
                        DetailRow("Closed", formatTimestamp(it))
                    }
                    DetailRow("Realized P&L", formatCurrency(position.realizedPnL))
                }

                if ((position.fees ?: 0.0) > 0 || (position.commission ?: 0.0) > 0) {
                    DetailRow("Fees", formatCurrency(position.fees ?: 0.0))
                    DetailRow("Commission", formatCurrency(position.commission ?: 0.0))
                }
            }
        },
        confirmButton = {
            if (position.isOpen()) {
                Button(onClick = onClose) {
                    Text("Close Position")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (position.isClosed()) {
                    TextButton(onClick = onDelete) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// Utility functions
private fun formatCurrency(value: Double): String {
    val sign = if (value >= 0) "+" else ""
    return "$sign$${"%.2f".format(abs(value))}"
}

private fun formatNumber(value: Double): String {
    return "%.4f".format(value)
}

private fun formatPercent(value: Double): String {
    val sign = if (value >= 0) "+" else ""
    return "$sign${"%.2f".format(abs(value))}%"
}

private fun formatTimestamp(instant: Instant): String {
    // Simple formatting - in production you'd use proper date formatting
    return instant.toString().take(19).replace('T', ' ')
}

private fun SortOption.displayName(): String {
    return when (this) {
        SortOption.DATE_DESC -> "Newest"
        SortOption.DATE_ASC -> "Oldest"
        SortOption.PNL_DESC -> "P&L ↓"
        SortOption.PNL_ASC -> "P&L ↑"
        SortOption.SYMBOL_ASC -> "Symbol A-Z"
        SortOption.SYMBOL_DESC -> "Symbol Z-A"
    }
}
