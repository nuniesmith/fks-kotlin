package xyz.fkstrading.clients

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.compose.koinInject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import xyz.fkstrading.clients.api.FksApiClient
import xyz.fkstrading.clients.di.appModule
import xyz.fkstrading.clients.domain.viewmodel.AuthViewModel
import xyz.fkstrading.clients.navigation.Screen
import xyz.fkstrading.clients.ui.screens.LoginScreen
import xyz.fkstrading.clients.ui.screens.MonitoringScreen
import xyz.fkstrading.clients.ui.screens.SignalMatrixScreen
import xyz.fkstrading.clients.ui.theme.FksDarkColorScheme

/**
 * Main Activity for FKS Trading Android App
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Koin Dependency Injection
        // Stop existing Koin instance if any (useful for testing)
        stopKoin()

        startKoin {
            androidLogger()
            androidContext(this@MainActivity)
            modules(appModule)
        }

        // Configure API client (use environment or BuildConfig)
        FksApiClient.configure(
            apiUrl = BuildConfig.FKS_API_URL,
            authUrl = BuildConfig.FKS_AUTH_URL,
            dataUrl = BuildConfig.FKS_DATA_URL,
            portfolioUrl = BuildConfig.FKS_PORTFOLIO_URL,
        )

        setContent {
            FksApp()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up Koin if needed (typically not necessary for Activity lifecycle)
        // but useful if we want to reset state
    }
}

@Composable
fun FksApp() {
    // Use Koin to inject the same AuthViewModel instance everywhere
    // koinInject() gets the ViewModel from Koin, ensuring singleton behavior
    val authViewModel: AuthViewModel = koinInject()
    val isAuthenticated by authViewModel.isAuthenticated.collectAsState()

    MaterialTheme(colorScheme = FksDarkColorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (isAuthenticated) {
                // Main app content
                MainNavigation(authViewModel = authViewModel)
            } else {
                // Login screen
                LoginScreen(
                    viewModel = authViewModel,
                    onLoginSuccess = {
                        // Navigation handled by state change
                    },
                )
            }
        }
    }
}

@Composable
fun MainNavigation(authViewModel: AuthViewModel) {
    // Use type-safe Screen sealed class instead of hardcoded strings
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Signals) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.bottomNavScreens.forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        icon = {
                            Text(
                                when (screen) {
                                    is Screen.Signals -> "📊"
                                    is Screen.Portfolio -> "💼"
                                    is Screen.Monitoring -> "📈"
                                    is Screen.Settings -> "⚙️"
                                },
                            )
                        },
                        label = { Text(screen.title) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentScreen) {
                is Screen.Signals -> SignalMatrixScreen()
                is Screen.Portfolio -> xyz.fkstrading.clients.ui.screens.PortfolioDashboardScreen()
                is Screen.Monitoring -> MonitoringScreen()
                is Screen.Settings -> SettingsScreen(authViewModel = authViewModel)
            }
        }
    }
}

@Composable
fun SettingsScreen(authViewModel: AuthViewModel) {
    // FIXED: Use the same AuthViewModel instance passed from parent
    // instead of creating a new instance with remember { AuthViewModel() }
    // This ensures state synchronization - logout() will work correctly

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { authViewModel.logout() },
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
        ) {
            Text("Logout")
        }
    }
}
