package xyz.fkstrading.client

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import kotlinx.coroutines.launch
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import xyz.fkstrading.client.features.auth.AuthScreen
import xyz.fkstrading.client.features.dashboard.DashboardScreen
import xyz.fkstrading.client.theme.FksTheme
import xyz.fkstrading.client.ui.components.FksAppBar
import xyz.fkstrading.client.ui.components.FksBottomNav
import xyz.fkstrading.shared.auth.TailscaleAuthRepository
import xyz.fkstrading.shared.auth.TailscaleAuthState

/**
 * Main Application Entry Point
 *
 * This is the root composable that runs on ALL platforms:
 * - Android (Phone/Tablet)
 * - iOS (iPhone/iPad)
 * - Desktop (Linux, macOS, Windows)
 *
 * Architecture:
 * - Voyager for navigation
 * - Koin for dependency injection
 * - Material3 for design system
 * - Tailscale auth gate before the main scaffold
 *
 * Auth gate logic
 * ---------------
 * | Auth state                            | Behaviour                              |
 * |---------------------------------------|----------------------------------------|
 * | Loading                               | AuthScreen (spinner)                   |
 * | Connected                             | Main scaffold (auto, no button needed) |
 * | NotConnected  (isNetworkError = true) | Main scaffold + dev-mode warning strip |
 * | NotConnected  (isNetworkError = false)| AuthScreen (access blocked)            |
 * | Error                                 | AuthScreen (error card + retry)        |
 *
 * The repository is injected directly here (no ViewModel) to avoid the
 * lifecycle-viewmodel dependency that is not in the version catalog.
 * A [rememberCoroutineScope] is used for the onRetry callback so it is
 * scoped to the composition lifetime.
 */
@Composable
fun App() {
    FksTheme {
        KoinContext {
            val repo = koinInject<TailscaleAuthRepository>()
            val authState by repo.authState.collectAsState()
            val scope = rememberCoroutineScope()

            // Trigger the initial identity fetch exactly once per composition.
            // The repository already exposes a cached Connected state if a prior
            // session was persisted, so the UI is never blank while this runs.
            LaunchedEffect(Unit) {
                repo.fetchIdentity()
            }

            // Compute convenience flags outside the when() so they can be reused
            // in multiple slots of the Scaffold.
            val isNetworkError =
                (authState as? TailscaleAuthState.NotConnected)?.isNetworkError == true

            val showMainScaffold =
                authState is TailscaleAuthState.Connected || isNetworkError

            if (showMainScaffold) {
                // ── Main application scaffold ─────────────────────────────────
                MainScaffold(
                    showDevWarning = isNetworkError,
                )
            } else {
                // ── Auth gate ─────────────────────────────────────────────────
                // Covers: Loading, NotConnected(!isNetworkError), Error
                AuthScreen(
                    authState = authState,
                    onRetry = {
                        scope.launch { repo.fetchIdentity() }
                    },
                    // onEnterApp is a no-op here: when authState becomes Connected
                    // the when() above automatically switches to MainScaffold via
                    // recomposition — no manual navigation needed.
                    onEnterApp = {},
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main scaffold
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The full application scaffold (AppBar + Navigator + optional BottomNav).
 *
 * @param showDevWarning When `true` a yellow/error warning strip is rendered
 *                       above the bottom navigation bar to indicate that the
 *                       Tailscale backend could not be reached and the app is
 *                       running in dev mode without auth enforcement.
 */
@Composable
private fun MainScaffold(showDevWarning: Boolean) {
    Navigator(DashboardScreen()) { navigator ->
        Scaffold(
            topBar = {
                FksAppBar(
                    navigator = navigator,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            content = { innerPadding ->
                SlideTransition(
                    navigator = navigator,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            },
            bottomBar = {
                Column {
                    // ── Dev-mode warning strip ────────────────────────────────
                    // Shown when the backend was unreachable (network error).
                    // This allows development to proceed without a running backend
                    // or an active Tailscale connection, while still surfacing a
                    // visible reminder that auth was skipped.
                    if (showDevWarning) {
                        DevWarningStrip()
                    }

                    // ── Bottom navigation (mobile / compact screens only) ─────
                    if (isCompactScreen()) {
                        FksBottomNav(navigator = navigator)
                    }
                }
            },
        )
    }
}

/**
 * A compact warning banner shown at the bottom of the screen when the
 * Tailscale backend is unreachable (dev / offline mode).
 *
 * Intentionally unobtrusive — it sits above the bottom nav rather than
 * blocking any content — while still being clearly visible.
 */
@Composable
private fun DevWarningStrip() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = "⚠  Backend unreachable — Tailscale auth skipped (dev mode)",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Platform expect declarations
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Platform-specific screen size detection.
 *
 * Returns `true` for mobile / compact screens (< 600 dp width).
 * Returns `false` for tablets and desktop.
 */
@Composable
expect fun isCompactScreen(): Boolean

/**
 * Platform-specific initialisation.
 * Called before [App] is rendered.
 */
expect fun platformInit()
