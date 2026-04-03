plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

android {
    namespace = "xyz.fkstrading.clients"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "xyz.fkstrading.clients"
        minSdk = libs.versions.android.min.sdk.get().toInt()
        targetSdk = libs.versions.android.target.sdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Read API URLs from gradle.properties with secure defaults
        // To override, add these properties to gradle.properties or local.properties:
        //   fks.api.url=https://api.fkstrading.xyz
        //   fks.auth.url=https://auth.fkstrading.xyz
        //   fks.data.url=https://data.fkstrading.xyz
        //   fks.portfolio.url=https://portfolio.fkstrading.xyz
        //
        // SECURITY: Always use HTTPS in production. Never hardcode URLs in source code.
        val apiUrl =
            project.findProperty("fks.api.url") as String?
                ?: "https://api.fkstrading.xyz"
        val authUrl =
            project.findProperty("fks.auth.url") as String?
                ?: "https://auth.fkstrading.xyz"
        val dataUrl =
            project.findProperty("fks.data.url") as String?
                ?: "https://data.fkstrading.xyz"
        val portfolioUrl =
            project.findProperty("fks.portfolio.url") as String?
                ?: "https://portfolio.fkstrading.xyz"

        buildConfigField("String", "FKS_API_URL", "\"$apiUrl\"")
        buildConfigField("String", "FKS_AUTH_URL", "\"$authUrl\"")
        buildConfigField("String", "FKS_DATA_URL", "\"$dataUrl\"")
        buildConfigField("String", "FKS_PORTFOLIO_URL", "\"$portfolioUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Shared module
    implementation(project(":shared"))

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")

    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")

    // Koin
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation("androidx.compose.ui:ui-tooling")
}
