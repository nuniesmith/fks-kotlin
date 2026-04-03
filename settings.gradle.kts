// FKS Kotlin Multiplatform — Root Project Settings
// Generated: 2025 — required for Gradle multi-project build

rootProject.name = "fks-clients"

// =========================================
// PLUGIN MANAGEMENT
// =========================================
pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

// =========================================
// DEPENDENCY RESOLUTION MANAGEMENT
// =========================================
dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

// =========================================
// VERSION CATALOG
// =========================================
// Reads gradle/libs.versions.toml automatically (default location)

// =========================================
// SUBPROJECTS
// =========================================

// Shared KMP data layer — Ktor, SQLDelight, coroutines, serialization
// Referenced as project(":shared") by all app targets
include(":shared")

// Modern KMP app — targets Android, Desktop (JVM), iOS
// Primary development target going forward
include(":composeApp")

// Legacy standalone Android app (xyz.fkstrading.clients)
// Pre-dates the composeApp KMP restructure; kept for reference
include(":android")

// Legacy standalone Desktop app (xyz.fkstrading.clients)
// Pre-dates the composeApp KMP restructure; kept for reference
include(":desktop")
