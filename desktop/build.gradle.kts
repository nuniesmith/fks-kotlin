import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    kotlin("plugin.compose")
}

kotlin {
    jvm {
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)

                // Koin Dependency Injection
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "xyz.fkstrading.clients.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "FKS Trading"
            packageVersion = "1.0.0"
            description = "FKS Trading Desktop Client"
            copyright = "© 2024 FKS Trading"
            vendor = "FKS Trading"

            linux {
                // iconFile.set(project.file("src/main/resources/icon.png"))
                debMaintainer = "support@fkstrading.xyz"
                menuGroup = "Finance"
                appCategory = "Finance"
            }

            macOS {
                // iconFile.set(project.file("src/main/resources/icon.icns"))
                bundleID = "xyz.fkstrading.clients"
            }

            windows {
                // iconFile.set(project.file("src/main/resources/icon.ico"))
                menuGroup = "FKS Trading"
                upgradeUuid = "8f4e6d2a-3c1b-4a5e-9f8d-7e6c5b4a3d2e"
            }
        }
    }
}
