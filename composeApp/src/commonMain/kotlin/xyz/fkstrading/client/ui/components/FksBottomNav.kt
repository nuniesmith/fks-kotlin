package xyz.fkstrading.client.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import xyz.fkstrading.client.features.dashboard.DashboardScreen
import xyz.fkstrading.client.features.orders.OrdersScreen
import xyz.fkstrading.client.features.portfolio.PositionsScreen
import xyz.fkstrading.client.features.realtime.RealTimeSignalsScreen

@Composable
fun FksBottomNav(navigator: Navigator) {
    val currentScreen = navigator.lastItem

    NavigationBar {
        NavigationBarItem(
            selected = currentScreen is DashboardScreen,
            onClick = { navigator.push(DashboardScreen()) },
            icon = { Icon(Icons.Default.Dashboard, "Dashboard") },
            label = { Text("Dashboard") },
        )
        NavigationBarItem(
            selected = currentScreen is PositionsScreen,
            onClick = { navigator.push(PositionsScreen()) },
            icon = { Icon(Icons.Default.AccountBalance, "Portfolio") },
            label = { Text("Portfolio") },
        )
        NavigationBarItem(
            selected = currentScreen is OrdersScreen,
            onClick = { navigator.push(OrdersScreen()) },
            icon = { Icon(Icons.Default.ShoppingCart, "Orders") },
            label = { Text("Orders") },
        )
        NavigationBarItem(
            selected = currentScreen is RealTimeSignalsScreen,
            onClick = { navigator.push(RealTimeSignalsScreen()) },
            icon = { Icon(Icons.Default.TrendingUp, "Signals") },
            label = { Text("Signals") },
        )
    }
}
