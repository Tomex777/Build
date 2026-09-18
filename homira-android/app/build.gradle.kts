fun buildConfigString(value: String): String =
    "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"") + "\""

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.night.homira"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.night.homira"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-call-ui"

        buildConfigField(
            "String",
            "FIREBASE_PROJECT_ID",
            buildConfigString(
                providers.gradleProperty(
                    "HOMIRA_FIREBASE_PROJECT_ID"
                ).orNull.orEmpty()
            )
        )
        buildConfigField(
            "String",
            "FIREBASE_APP_ID",
            buildConfigString(
                providers.gradleProperty(
                    "HOMIRA_FIREBASE_APP_ID"
                ).orNull.orEmpty()
            )
        )
        buildConfigField(
            "String",
            "FIREBASE_API_KEY",
            buildConfigString(
                providers.gradleProperty(
                    "HOMIRA_FIREBASE_API_KEY"
                ).orNull.orEmpty()
            )
        )
        buildConfigField(
            "String",
            "FIREBASE_SENDER_ID",
            buildConfigString(
                providers.gradleProperty(
                    "HOMIRA_FIREBASE_SENDER_ID"
                ).orNull.orEmpty()
            )
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    // Compose 1.12+ requires compileSdk 37. The June 2026 stable BOM
    // stays on the Compose 1.11 line, which is compatible with API 36.
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.core:core-telecom:1.1.0-beta01")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation(platform("io.github.jan-tennert.supabase:bom:3.8.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")
    implementation("io.ktor:ktor-client-android:3.5.2")

    implementation("io.github.webrtc-sdk:android:150.7871.01")

    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-messaging")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
