plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    // =========================================
    // ANDROID TARGET
    // =========================================
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "21"
            }
        }
    }

    // =========================================
    // JVM/DESKTOP TARGET
    // =========================================
    jvm("desktop") {
        compilations.all {
            kotlinOptions {
                jvmTarget = "21"
            }
        }
    }

    // =========================================
    // iOS TARGETS
    // =========================================
    val iosX64 = iosX64()
    val iosArm64 = iosArm64()
    val iosSimulatorArm64 = iosSimulatorArm64()

    listOf(
        iosX64,
        iosArm64,
        iosSimulatorArm64,
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    // =========================================
    // WEB/WASM TARGET (Disabled - SQLDelight incompatibility)
    // =========================================
    // @OptIn(ExperimentalWasmDsl::class)
    // wasmJs {
    //     browser()
    // }

    // =========================================
    // SOURCE SETS
    // =========================================
    sourceSets {
        // ===== COMMON MAIN =====
        commonMain.dependencies {
            // Networking (Ktor Client)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.logging)

            // Serialization
            implementation(libs.kotlinx.serialization.json)

            // Coroutines
            implementation(libs.kotlinx.coroutines.core)

            // DateTime
            implementation(libs.kotlinx.datetime)

            // Dependency Injection (Koin)
            implementation(libs.koin.core)

            // Database (SQLDelight)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
        }

        // ===== COMMON TEST =====
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        // ===== ANDROID MAIN =====
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
            implementation(libs.sqldelight.android.driver)
        }

        // ===== DESKTOP MAIN =====
        val desktopMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(libs.sqldelight.sqlite.driver)
            }
        }

        // ===== DESKTOP TEST =====
        val desktopTest by getting {
            dependsOn(commonTest.get())
        }

        // ===== iOS MAIN =====
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain.get())
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)

            dependencies {
                implementation(libs.ktor.client.darwin)
                implementation(libs.sqldelight.native.driver)
            }
        }

        // ===== WASM/JS MAIN (Disabled - SQLDelight incompatibility) =====
        // val wasmJsMain by getting {
        //     dependencies {
        //         implementation(libs.ktor.client.js)
        //     }
        // }
    }
}

// =========================================
// ANDROID CONFIGURATION
// =========================================
android {
    namespace = "xyz.fkstrading.shared"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.min.sdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

// =========================================
// TEST CONFIGURATION
// =========================================
tasks.withType<AbstractTestTask>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

// =========================================
// SQLDELIGHT CONFIGURATION
// =========================================
sqldelight {
    databases {
        create("FksDatabase") {
            packageName.set("xyz.fkstrading.shared.data.db")
            srcDirs.setFrom("src/commonMain/sqldelight")
        }
    }
}
