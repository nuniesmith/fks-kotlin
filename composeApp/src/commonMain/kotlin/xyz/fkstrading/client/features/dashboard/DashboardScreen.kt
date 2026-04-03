package xyz.fkstrading.client.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import org.koin.compose.koinInject
import xyz.fkstrading.client.features.orders.OrdersScreen
import xyz.fkstrading.client.features.portfolio.PortfolioViewModel
import xyz.fkstrading.client.features.portfolio.PositionsScreen
import xyz.fkstrading.client.features.realtime.RealTimeSignalsScreen
import xyz.fkstrading.shared.domain.models.Position
import xyz.fkstrading.shared.domain.models.isLong
import xyz.fkstrading.shared.domain.models.isOpen
import xyz.fkstrading.shared.domain.models.pnlPercentage
import kotlin.math.abs

class DashboardScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        DashboardContent(navigator = navigator)
    }
}

@Composable
fun DashboardContent(navigator: cafe.adriel.voyager.navigator.Navigator) {
    val portfolioViewModel: PortfolioViewModel = koinInject()
    val portfolioMetrics by portfolioViewModel.portfolioMetrics.collectAsState()
    val portfolioState by portfolioViewModel.uiState.collectAsState()

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        item {
            Column {
                Text(
                    text = "FKS Trading Terminal",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Real-time Trading Dashboard",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Quick Actions
        item {
            QuickActionsCard(navigator)
        }

        // Portfolio Summary
        item {
            PortfolioSummaryCard(
                metrics = portfolioMetrics,
                onViewAll = { navigator.push(PositionsScreen()) },
            )
        }

        // Recent Positions
        item {
            when (val state = portfolioState) {
                is xyz.fkstrading.client.features.portfolio.PortfolioUiState.Success -> {
                    val recentPositions = state.positions.take(3)
                    if (recentPositions.isNotEmpty()) {
                        RecentPositionsCard(
                            positions = recentPositions,
                            onViewAll = { navigator.push(PositionsScreen()) },
                        )
                    }
                }
                else -> {
                    // Loading or empty state
                }
            }
        }

        // Market Status
        item {
            MarketStatusCard()
        }

        // Quick Stats
        item {
            QuickStatsRow(metrics = portfolioMetrics)
        }
    }
}

@Composable
fun QuickActionsCard(navigator: cafe.adriel.voyager.navigator.Navigator) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickActionButton(
                    icon = Icons.Default.AccountBalance,
                    label = "Portfolio",
                    onClick = { navigator.push(PositionsScreen()) },
                    modifier = Modifier.weight(1f),
                )
                QuickActionButton(
                    icon = Icons.Default.ShoppingCart,
                    label = "Orders",
                    onClick = { navigator.push(OrdersScreen()) },
                    modifier = Modifier.weight(1f),
                )
                QuickActionButton(
                    icon = Icons.Default.TrendingUp,
                    label = "Signals",
                    onClick = { navigator.push(RealTimeSignalsScreen()) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedButton(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
fun PortfolioSummaryCard(
    metrics: xyz.fkstrading.client.features.portfolio.PortfolioMetrics,
    onViewAll: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (metrics.isProfitable()) {
                        Color(0xFF4CAF50).copy(alpha = 0.1f)
                    } else if (metrics.totalPnL < 0) {
                        Color(0xFFF44336).copy(alpha = 0.1f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
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
                Text(
                    text = "Portfolio Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onViewAll) {
                    Text("View All")
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Total P&L - Large Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Total P&L",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = formatCurrency(metrics.totalPnL),
                            style = MaterialTheme.typography.headlineMedium,
                            color =
                                if (metrics.isProfitable()) {
                                    Color(0xFF4CAF50)
                                } else if (metrics.totalPnL < 0) {
                                    Color(0xFFF44336)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            fontWeight = FontWeight.Bold,
                        )
                        if (metrics.totalPnL != 0.0) {
                            Icon(
                                imageVector = if (metrics.isProfitable()) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = if (metrics.isProfitable()) Color(0xFF4CAF50) else Color(0xFFF44336),
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    if (metrics.totalInvested > 0) {
                        Text(
                            text = formatPercent(metrics.getPnLPercentage()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (metrics.isProfitable()) Color(0xFF4CAF50) else Color(0xFFF44336),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MetricColumn(
                    label = "Open Positions",
                    value = metrics.openPositions.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricColumn(
                    label = "Closed",
                    value = metrics.closedPositions.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricColumn(
                    label = "Win Rate",
                    value = "${String.format("%.1f", metrics.winRate)}%",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MetricColumn(
                    label = "Unrealized",
                    value = formatCurrency(metrics.totalUnrealizedPnL),
                    valueColor = if (metrics.totalUnrealizedPnL >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                    modifier = Modifier.weight(1f),
                )
                MetricColumn(
                    label = "Realized",
                    value = formatCurrency(metrics.totalRealizedPnL),
                    valueColor = if (metrics.totalRealizedPnL >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun MetricColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
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
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
    }
}

@Composable
fun RecentPositionsCard(
    positions: List<Position>,
    onViewAll: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recent Positions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onViewAll) {
                    Text("View All")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            positions.forEach { position ->
                PositionItem(position)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun PositionItem(position: Position) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { /* Navigate to position details */ },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = position.symbol,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = position.side.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (position.isLong()) Color(0xFF4CAF50) else Color(0xFFF44336),
                )
            }
            Text(
                text = "Qty: ${formatNumber(position.quantity)} @ ${formatCurrency(position.entryPrice)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            val pnl = if (position.isOpen()) position.unrealizedPnL else position.realizedPnL
            Text(
                text = formatCurrency(pnl),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (pnl >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
            )
            Text(
                text = formatPercent(position.pnlPercentage()),
                style = MaterialTheme.typography.bodySmall,
                color = if (pnl >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
            )
        }
    }
}

@Composable
fun MarketStatusCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Market Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))

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
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Markets Open",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                AssistChip(
                    onClick = {},
                    label = { Text("Live", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Box(
                            modifier =
                                Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF4CAF50), shape = androidx.compose.foundation.shape.CircleShape),
                        )
                    },
                    colors =
                        AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
                        ),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Trading is active. All systems operational.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun QuickStatsRow(metrics: xyz.fkstrading.client.features.portfolio.PortfolioMetrics) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard(
            label = "Winning",
            value = metrics.winningTrades.toString(),
            icon = Icons.Default.TrendingUp,
            color = Color(0xFF4CAF50),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "Losing",
            value = metrics.losingTrades.toString(),
            icon = Icons.Default.TrendingDown,
            color = Color(0xFFF44336),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = color.copy(alpha = 0.1f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
