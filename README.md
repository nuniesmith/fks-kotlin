# fks-kotlin

**Kotlin Multiplatform cross-platform client apps — source code only.**

This repo contains the FKS KMP application: shared business logic + Compose Multiplatform UI targeting iOS, Android, desktop (JVM), and web (Wasm). Infrastructure (Docker, compose, CI/CD) lives in [fks](https://github.com/nuniesmith/fks).

---

## What's here

```
shared/                          # KMP shared module
├── src/commonMain/kotlin/
│   ├── auth/                    # Tailscale auth (identity, state, repository)
│   ├── data/
│   │   ├── api/                 # FksApiClient, HTTP client factory, token manager
│   │   ├── repository/          # Signal, position, order, strategy config repos
│   │   ├── websocket/           # WebSocket client + reconnect strategy + subscription manager
│   │   ├── bridge/              # WebSocketRepositoryBridge
│   │   ├── sync/                # SyncEngine
│   │   ├── mock/                # Mock data sources for tests
│   │   └── db/                  # SQLDelight database wrapper + driver factories
│   ├── domain/
│   │   ├── strategy/            # StrategyExecutor, OrderBuilder, ExecutionValidator, PositionSizer
│   │   └── usecases/            # Use cases
│   ├── di/                      # Koin modules (App, Auth, Network, Database, WebSocket)
│   └── config/                  # AppConfig
├── src/commonTest/kotlin/       # Cross-platform tests (strategy, websocket, integration)
└── src/sqldelight/              # Signal, Position, Order, StrategyConfig, SyncMetadata schemas

composeApp/                      # Compose Multiplatform UI (iOS, Android, desktop, web)
├── src/commonMain/kotlin/
│   ├── client/
│   │   ├── App.kt               # Root composable — auth gate → MainScaffold
│   │   ├── features/
│   │   │   ├── auth/            # AuthScreen + AuthViewModel (Tailscale gate)
│   │   │   ├── dashboard/       # DashboardScreen + ViewModel
│   │   │   ├── trading/         # Orders, positions
│   │   │   ├── realtime/        # Live signals (WebSocket)
│   │   │   ├── portfolio/       # Kraken spot portfolio
│   │   │   └── settings/        # System settings, strategy config, presets
│   │   ├── ui/components/       # FksAppBar, FksBottomNav
│   │   └── theme/               # Typography, Theme (terminal dark)
│   └── di/                      # App-level Koin module
├── src/androidMain/             # Android entry (FksApplication, MainActivity)
├── src/iosMain/                 # iOS entry (MainViewController)
├── src/desktopMain/             # Desktop entry (Main.kt — JVM window)
└── src/wasmJsMain/              # Web entry (index.html + Main.kt)

ios/                             # Native iOS wrapper (Xcode project)
android/                         # Android app module
desktop/                         # Desktop JVM module
```

## Auth model

All access requires Tailscale. On startup the app calls `GET /api/tailscale/identity` on the FKS backend to resolve who's connecting. `AuthScreen` blocks entry until Tailscale identity is confirmed. `NotConnected` state shows setup instructions and a retry button.

```kotlin
// TailscaleAuthState sealed class
Loading | Connected(identity) | NotConnected(reason, isNetworkError) | Error(message)
```

## Building

```bash
# Desktop JVM (fastest iteration)
./gradlew :desktop:run

# Android
./gradlew :android:assembleDebug

# iOS — open ios/FKSTrading.xcodeproj in Xcode

# Run shared module tests (desktop JVM)
./gradlew :shared:desktopTest

# Run all common tests
./gradlew :shared:commonTest
```

Requires JDK 17+, Gradle 8.x. iOS requires Xcode 15+.

## Dependency injection

Koin. Modules are platform-specific entrypoints that call `initKoin(appModule)` with the platform's `PlatformModule`. Shared DI graph: `NetworkModule → DatabaseModule → AuthModule → WebSocketModule → domain repositories`.

## Data persistence

SQLDelight for offline-first local storage. Tables: `Signal`, `Position`, `Order`, `StrategyConfig`, `SyncMetadata`. `SyncEngine` handles bidirectional sync with the FKS backend.

## Deployment

The KMP app connects to the FKS backend over Tailscale HTTPS. No server-side deployment needed for the apps themselves. The read surface (health, dashboard, signals, risk portfolio) targets **janus** — the old fks_ruby/FastAPI read path was retired; `FksApiClient.kt` holds the janus read DTOs, verified against the live wire in `JanusWireDeserTest`.

## Status

- Auth gate ✅ (Tailscale identity)
- Shared domain logic ✅ (strategy executor, order builder, position sizer, execution validator)
- WebSocket client ✅ (reconnect strategy, subscription manager)
- Dashboard, orders, positions, signals, settings screens ✅
- KMP theme: terminal dark ✅
- Gradle build verified ✅
- Native iOS wrapper ✅

Remaining: full visual QA, SSE client in KMP, push notifications.
