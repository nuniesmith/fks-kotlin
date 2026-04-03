// Root build configuration for fks-clients (Kotlin Multiplatform)
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}

// Node.js configuration is handled via gradle.properties:
// kotlin.js.nodejs.download=false (uses system Node.js)
