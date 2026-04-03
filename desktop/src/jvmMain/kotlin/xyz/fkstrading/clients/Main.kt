package xyz.fkstrading.clients

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.koin.compose.koinInject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import xyz.fkstrading.clients.api.FksApiClient
import xyz.fkstrading.clients.di.appModule
import xyz.fkstrading.clients.domain.viewmodel.AuthViewModel
import xyz.fkstrading.clients.ui.screens.*
import xyz.fkstrading.clients.ui.theme.FksDarkColorScheme

fun main() =
    application {
        // Initialize Koin Dependency Injection
        // Stop existing Koin instance if any (useful for testing/reloading)
        stopKoin()

        startKoin {
            modules(appModule)
        }

        // Configure API URLs from environment variables with localhost fallback
        //
        // For Desktop apps (outside Docker):
        // - Production: Set environment variables (FKS_API_URL, FKS_AUTH_URL, etc.)
        // - Local Development: Falls back to localhost URLs (acceptable for desktop apps)
        //
        // Note: This is different from Android (which uses BuildConfig) and different from
        // services running in Docker (which use service discovery). Desktop apps typically
        // connect to services running on localhost or remote servers via environment config.
        FksApiClient.configure(
            apiUrl = System.getenv("FKS_API_URL") ?: "http://localhost:8001",
            authUrl = System.getenv("FKS_AUTH_URL") ?: "http://localhost:8009",
            dataUrl = System.getenv("FKS_DATA_URL") ?: "http://localhost:8003",
            portfolioUrl = System.getenv("FKS_PORTFOLIO_URL") ?: "http://localhost:8012",
        )

        Window(
            onCloseRequest = ::exitApplication,
            title = "FKS Trading",
            state = rememberWindowState(width = 1200.dp, height = 800.dp),
        ) {
            FksDesktopApp()
        }
    }

@Composable
fun FksDesktopApp() {
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
                MainContent(authViewModel)
            } else {
                LoginScreen(
                    viewModel = authViewModel,
                    onLoginSuccess = { /* State handles navigation */ },
                )
            }
        }
    }
}

@Composable
fun MainContent(authViewModel: AuthViewModel) {
    var currentScreen by remember { mutableStateOf("signals") }

    Row(modifier = Modifier.fillMaxSize()) {
        // Sidebar
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
        ) {
            NavigationRailItem(
                selected = currentScreen == "signals",
                onClick = { currentScreen = "signals" },
                icon = { Text("📊") },
                label = { Text("Signals") },
            )
            NavigationRailItem(
                selected = currentScreen == "portfolio",
                onClick = { currentScreen = "portfolio" },
                icon = { Text("💼") },
                label = { Text("Portfolio") },
            )
            NavigationRailItem(
                selected = currentScreen == "evaluation",
                onClick = { currentScreen = "evaluation" },
                icon = { Text("📈") },
                label = { Text("Evaluation") },
            )

            Spacer(modifier = Modifier.weight(1f))

            NavigationRailItem(
                selected = false,
                onClick = { authViewModel.logout() },
                icon = { Text("🚪") },
                label = { Text("Logout") },
            )
        }

        // Main content
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
                "signals" -> SignalMatrixScreen()
                "portfolio" -> PortfolioDashboardScreen()
                "evaluation" -> EvaluationMatrixScreen()
            }
        }
    }
}
