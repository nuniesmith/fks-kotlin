package xyz.fkstrading.clients.navigation

/**
 * Type-safe navigation screens for FKS Trading Android App
 *
 * This sealed class replaces hardcoded string literals with type-safe navigation.
 * Each screen is represented by a sealed class instance, preventing typos and
 * enabling compile-time safety.
 */
sealed class Screen(val route: String, val title: String) {
    data object Signals : Screen("signals", "Signals")

    data object Portfolio : Screen("portfolio", "Portfolio")

    data object Monitoring : Screen("monitoring", "Monitoring")

    data object Settings : Screen("settings", "Settings")

    companion object {
        /**
         * Get screen by route string (for deep linking)
         */
        fun fromRoute(route: String): Screen {
            return when (route) {
                Signals.route -> Signals
                Portfolio.route -> Portfolio
                Monitoring.route -> Monitoring
                Settings.route -> Settings
                else -> Signals // Default fallback
            }
        }

        /**
         * Get all bottom navigation screens
         */
        val bottomNavScreens = listOf(Signals, Portfolio, Monitoring, Settings)
    }
}
