plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val requestedAbi = providers.gradleProperty("mirrorChessAbi").orNull
val ciInstallableRelease = providers.gradleProperty("mirrorChessCiInstallableRelease").orNull == "true"

android {
    namespace = "com.night.mirrorchess"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.night.mirrorchess"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.2.0"

        if (!requestedAbi.isNullOrBlank()) {
            ndk {
                abiFilters += requestedAbi
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (ciInstallableRelease) {
                // CI-only installable release candidate. Production distribution
                // signing remains external; no keystore credentials live in-repo.
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.02.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.29.0")

    testImplementation("junit:junit:4.13.2")
}
