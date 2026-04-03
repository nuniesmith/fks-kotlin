package xyz.fkstrading.client.features.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.datetime.Instant
import org.koin.compose.koinInject
import xyz.fkstrading.shared.domain.models.*
import kotlin.math.abs

/**
 * Voyager Screen for Orders
 *
 * Displays:
 * - List of orders (active/completed)
 * - Order creation form
 * - Order details
 * - Filtering and sorting options
 */
class OrdersScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: OrdersViewModel = koinInject()
        val navigator = LocalNavigator.currentOrThrow

        OrdersScreenContent(
            viewModel = viewModel,
            onNavigateBack = { navigator.pop() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreenContent(
    viewModel: OrdersViewModel,
    onNavigateBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    val metrics by viewModel.orderMetrics.collectAsState()
    val creationState by viewModel.orderCreationState.collectAsState()

    var showFilters by remember { mutableStateOf(false) }
    var showOrderForm by remember { mutableStateOf(false) }
    var selectedOrder by remember { mutableStateOf<Order?>(null) }

    // Handle order creation success
    LaunchedEffect(creationState) {
        if (creationState is OrderCreationState.Success) {
            showOrderForm = false
            viewModel.resetOrderCreationState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Orders") },
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showOrderForm = true },
                icon = { Icon(Icons.Default.Add, "Create order") },
                text = { Text("New Order") },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            // Order Metrics Summary
            OrderMetricsCard(metrics)

            // Filters (if shown)
            if (showFilters) {
                OrderFilterSection(
                    filterState = filterState,
                    onUpdateFilter = { viewModel.updateFilter(it) },
                    onClearFilters = { viewModel.clearFilters() },
                )
            }

            // Orders List
            when (val state = uiState) {
                is OrdersUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is OrdersUiState.Empty -> {
                    EmptyOrdersView()
                }
                is OrdersUiState.Success -> {
                    OrdersList(
                        orders = state.orders,
                        onOrderClick = { selectedOrder = it },
                        onCancelOrder = { viewModel.cancelOrder(it.orderId) },
                    )
                }
                is OrdersUiState.Error -> {
                    ErrorView(
                        message = state.message,
                        onRetry = { viewModel.refresh() },
                    )
                }
            }
        }
    }

    // Order Creation Form
    if (showOrderForm) {
        OrderCreationDialog(
            creationState = creationState,
            onDismiss = {
                showOrderForm = false
                viewModel.resetOrderCreationState()
            },
            onSubmit = { request ->
                viewModel.createOrder(request)
            },
        )
    }

    // Order Details Dialog
    selectedOrder?.let { order ->
        OrderDetailsDialog(
            order = order,
            onDismiss = { selectedOrder = null },
            onCancel = {
                viewModel.cancelOrder(order.orderId)
                selectedOrder = null
            },
            onDelete = {
                viewModel.deleteOrder(order.orderId)
                selectedOrder = null
            },
        )
    }
}

@Composable
fun OrderMetricsCard(metrics: OrderMetrics) {
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
            Text(
                text = "Order Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MetricItem(
                    label = "Active",
                    value = metrics.activeOrders.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricItem(
                    label = "Filled",
                    value = metrics.filledOrders.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricItem(
                    label = "Fill Rate",
                    value = "${String.format("%.1f", metrics.fillRate)}%",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MetricItem(
                    label = "Cancelled",
                    value = metrics.cancelledOrders.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricItem(
                    label = "Total Volume",
                    value = formatCurrency(metrics.totalVolume),
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
fun OrderFilterSection(
    filterState: OrderFilter,
    onUpdateFilter: (OrderFilter) -> Unit,
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

            // Active Orders Filter
            FilterChip(
                selected = filterState.activeOnly,
                onClick = {
                    onUpdateFilter(filterState.copy(activeOnly = !filterState.activeOnly))
                },
                label = { Text("Active Only") },
            )

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
                OrderSortOption.values().take(3).forEach { option ->
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
fun OrdersList(
    orders: List<Order>,
    onOrderClick: (Order) -> Unit,
    onCancelOrder: (Order) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = orders,
            key = { it.orderId },
        ) { order ->
            OrderCard(
                order = order,
                onClick = { onOrderClick(order) },
                onCancel = { onCancelOrder(order) },
            )
        }
    }
}

@Composable
fun OrderCard(
    order: Order,
    onClick: () -> Unit,
    onCancel: () -> Unit,
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
            // Header: Symbol, Side, and Status
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
                        text = order.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = order.side.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (order.side == OrderSide.BUY) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier =
                            Modifier
                                .background(
                                    color =
                                        if (order.side == OrderSide.BUY) {
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
                OrderStatusBadge(order.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Order Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = order.orderType.name.replace('_', ' '),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Qty: ${formatNumber(order.quantity)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    order.price?.let {
                        Text(
                            text = "Price: ${formatCurrency(it)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } ?: Text(
                        text = "Market Order",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    )
                    if (order.filledQuantity > 0) {
                        Text(
                            text = "Filled: ${formatNumber(order.filledQuantity)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Fill Progress (for partially filled orders)
            if (order.status == OrderStatus.PARTIALLY_FILLED) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = order.fillPercentage().toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${String.format("%.1f", order.fillPercentage() * 100)}% filled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Cancel Button (for active orders)
            if (order.isActive()) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(Icons.Default.Cancel, "Cancel order", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel Order")
                }
            }
        }
    }
}

@Composable
fun OrderStatusBadge(status: OrderStatus) {
    val (color, text) =
        when (status) {
            OrderStatus.PENDING -> MaterialTheme.colorScheme.secondary to "PENDING"
            OrderStatus.SUBMITTED -> MaterialTheme.colorScheme.primary to "SUBMITTED"
            OrderStatus.ACCEPTED -> MaterialTheme.colorScheme.primary to "ACCEPTED"
            OrderStatus.PARTIALLY_FILLED -> MaterialTheme.colorScheme.tertiary to "PARTIAL"
            OrderStatus.FILLED -> Color(0xFF4CAF50) to "FILLED"
            OrderStatus.CANCELLED -> MaterialTheme.colorScheme.outline to "CANCELLED"
            OrderStatus.REJECTED -> MaterialTheme.colorScheme.error to "REJECTED"
            OrderStatus.EXPIRED -> MaterialTheme.colorScheme.outline to "EXPIRED"
        }

    AssistChip(
        onClick = {},
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor = color.copy(alpha = 0.2f),
                labelColor = color,
            ),
    )
}

@Composable
fun EmptyOrdersView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Default.ShoppingCart,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = "No orders",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Create your first order to get started",
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
                text = "Error loading orders",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderCreationDialog(
    creationState: OrderCreationState,
    onDismiss: () -> Unit,
    onSubmit: (OrderRequest) -> Unit,
) {
    var symbol by remember { mutableStateOf("") }
    var side by remember { mutableStateOf(OrderSide.BUY) }
    var orderType by remember { mutableStateOf(OrderType.LIMIT) }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var stopPrice by remember { mutableStateOf("") }
    var timeInForce by remember { mutableStateOf(TimeInForce.GTC) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Order") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 500.dp),
            ) {
                item {
                    // Symbol
                    OutlinedTextField(
                        value = symbol,
                        onValueChange = { symbol = it.uppercase() },
                        label = { Text("Symbol") },
                        placeholder = { Text("BTC/USD") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                item {
                    // Side Selection
                    Text("Order Side", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = side == OrderSide.BUY,
                            onClick = { side = OrderSide.BUY },
                            label = { Text("BUY") },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF4CAF50).copy(alpha = 0.2f),
                                ),
                        )
                        FilterChip(
                            selected = side == OrderSide.SELL,
                            onClick = { side = OrderSide.SELL },
                            label = { Text("SELL") },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFF44336).copy(alpha = 0.2f),
                                ),
                        )
                    }
                }

                item {
                    // Order Type Selection
                    Text("Order Type", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = orderType == OrderType.MARKET,
                            onClick = { orderType = OrderType.MARKET },
                            label = { Text("MARKET") },
                        )
                        FilterChip(
                            selected = orderType == OrderType.LIMIT,
                            onClick = { orderType = OrderType.LIMIT },
                            label = { Text("LIMIT") },
                        )
                        FilterChip(
                            selected = orderType == OrderType.STOP,
                            onClick = { orderType = OrderType.STOP },
                            label = { Text("STOP") },
                        )
                    }
                }

                item {
                    // Quantity
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Quantity") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }

                if (orderType != OrderType.MARKET) {
                    item {
                        // Price (for limit orders)
                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = it },
                            label = { Text("Price") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                        )
                    }
                }

                if (orderType == OrderType.STOP || orderType == OrderType.STOP_LIMIT) {
                    item {
                        // Stop Price
                        OutlinedTextField(
                            value = stopPrice,
                            onValueChange = { stopPrice = it },
                            label = { Text("Stop Price") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                        )
                    }
                }

                // Loading or Error State
                when (creationState) {
                    is OrderCreationState.Creating -> {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Creating order...")
                            }
                        }
                    }
                    is OrderCreationState.Error -> {
                        item {
                            Text(
                                text = creationState.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val request =
                        OrderRequest(
                            symbol = symbol,
                            side = side,
                            orderType = orderType,
                            quantity = quantity.toDoubleOrNull() ?: 0.0,
                            price = price.toDoubleOrNull(),
                            stopPrice = stopPrice.toDoubleOrNull(),
                            timeInForce = timeInForce,
                        )
                    if (request.isValid()) {
                        onSubmit(request)
                    }
                },
                enabled = creationState !is OrderCreationState.Creating,
            ) {
                Text("Create Order")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = creationState !is OrderCreationState.Creating,
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun OrderDetailsDialog(
    order: Order,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${order.symbol} - ${order.side.name}")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailRow("Order ID", order.orderId)
                DetailRow("Type", order.orderType.name.replace('_', ' '))
                DetailRow("Quantity", formatNumber(order.quantity))

                order.price?.let {
                    DetailRow("Price", formatCurrency(it))
                }
                order.stopPrice?.let {
                    DetailRow("Stop Price", formatCurrency(it))
                }

                DetailRow("Status", order.status.name)
                DetailRow("Time in Force", order.timeInForce.name)

                if (order.filledQuantity > 0) {
                    DetailRow("Filled Quantity", formatNumber(order.filledQuantity))
                    order.averageFillPrice?.let {
                        DetailRow("Avg Fill Price", formatCurrency(it))
                    }
                }

                DetailRow("Created", formatTimestamp(order.timestamp))

                if ((order.fees ?: 0.0) > 0 || (order.commission ?: 0.0) > 0) {
                    DetailRow("Fees", formatCurrency(order.fees ?: 0.0))
                    DetailRow("Commission", formatCurrency(order.commission ?: 0.0))
                }

                order.errorMessage?.let {
                    Text(
                        text = "Error: $it",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (order.isActive()) {
                Button(onClick = onCancel) {
                    Text("Cancel Order")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (order.isComplete() && order.status != OrderStatus.FILLED) {
                    TextButton(onClick = onDelete) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
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
    return "$${"%.2f".format(abs(value))}"
}

private fun formatNumber(value: Double): String {
    return "%.4f".format(value)
}

private fun formatTimestamp(instant: Instant): String {
    return instant.toString().take(19).replace('T', ' ')
}

private fun OrderSortOption.displayName(): String {
    return when (this) {
        OrderSortOption.DATE_DESC -> "Newest"
        OrderSortOption.DATE_ASC -> "Oldest"
        OrderSortOption.SYMBOL_ASC -> "Symbol A-Z"
        OrderSortOption.SYMBOL_DESC -> "Symbol Z-A"
        OrderSortOption.QUANTITY_DESC -> "Qty ↓"
        OrderSortOption.QUANTITY_ASC -> "Qty ↑"
    }
}
