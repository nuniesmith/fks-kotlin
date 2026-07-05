# fks-kotlin — TODO

> **Repo:** `github.com/nuniesmith/fks-kotlin`
> **Last synced from master todo:** 2026-04-03

---

## Status

Auth gate and core domain logic are complete. UI screens exist. Remaining work is visual polish, SSE client, push notifications, and live integration testing.

---

## P1 — Core Remaining Work

### KT-A: Build verification
- [ ] Verify Kotlin/Gradle build still works: `./gradlew build` passes clean
- [ ] Run shared module tests: `./gradlew :shared:desktopTest`

### KT-C: Match WebUI look & feel
- [ ] Port terminal theme (dark background, cyan/green accent, monospace) to Compose `Theme.kt` — currently generic Material 3
- [ ] Implement common components matching WebUI: status bar strip, workspace tab bar, strip cells with live values
- [ ] Test on each platform target (Android, Desktop, iOS)

### KT-D: Core functionality
- [~] Connect to the **janus** backend over Tailscale HTTPS (the READ surface
      was repointed off the old fks_ruby/FastAPI service — `FksApiClient.kt`).
      The janus read DTOs (health, dashboard-overview, signals-latest, risk
      portfolio) are verified against the live wire (`JanusWireDeserTest`, incl.
      a real captured signal fixture). Remaining: confirm `HttpClientFactory`
      works end-to-end with the self-signed cert + Tailscale FQDN against janus.
- [ ] Implement SSE client in KMP (Ktor `HttpStatement` streaming) — wire to `/sse/strip`, `/sse/dashboard`
- [ ] Core views: dashboard (scores/signals), positions, signals, charts (embed or native LW), alerts
- [ ] Push notifications for trade signals (Android: FCM stub; iOS: APNs stub; Desktop: system tray)

---

## P2 — Platform Polish

- [ ] Android: test on physical device (Pixel/Galaxy) + verify Tailscale connectivity
- [ ] iOS: test on simulator + real device via Xcode
- [ ] Desktop: test on Linux + macOS (JVM window + tray icon)
- [ ] Web (Wasm): verify `wasmJsMain` target builds and loads in browser

---

## P3 — Future

- [ ] Offline-first sync: verify `SyncEngine` bidirectional sync works with `StrategyConfigRepositoryImpl` on reconnect
- [ ] Kotlin app in App Store / Play Store (post live trading validation)
