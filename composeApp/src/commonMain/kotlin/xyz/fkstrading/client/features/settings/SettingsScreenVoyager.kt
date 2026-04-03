package xyz.fkstrading.client.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.koin.compose.koinInject

/**
 * Settings Screen using Voyager navigation
 *
 * Provides tabbed interface for:
 * - Strategy Configuration
 * - System Settings (Discord, API, etc.)
 */
class SettingsScreenVoyager : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val strategyViewModel: StrategyConfigViewModel = koinInject()
        val systemViewModel: SystemSettingsViewModel = koinInject()

        SettingsScreenWithTabs(
            strategyViewModel = strategyViewModel,
            systemViewModel = systemViewModel,
            onNavigateBack = { navigator.pop() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenWithTabs(
    strategyViewModel: StrategyConfigViewModel,
    systemViewModel: SystemSettingsViewModel,
    onNavigateBack: () -> Unit,
) {
    var selectedTabIndex by remember { mutableStateOf(0) }

    val tabs =
        listOf(
            SettingsTab.System,
            SettingsTab.Strategy,
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(tab.title) },
                        icon = tab.icon?.let { { Icon(it, contentDescription = tab.title) } },
                    )
                }
            }

            // Tab Content
            when (tabs[selectedTabIndex]) {
                SettingsTab.System -> {
                    SystemSettingsScreen(
                        viewModel = systemViewModel,
                        onNavigateBack = {}, // Don't navigate back, handled by top bar
                    )
                }
                SettingsTab.Strategy -> {
                    SettingsScreen(
                        viewModel = strategyViewModel,
                        onNavigateBack = {}, // Don't navigate back, handled by top bar
                    )
                }
            }
        }
    }
}

/**
 * Settings tab enum
 */
private sealed class SettingsTab(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    object System : SettingsTab("System", Icons.Default.Settings)

    object Strategy : SettingsTab("Strategies", Icons.Default.Refresh)
}
