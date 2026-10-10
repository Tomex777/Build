plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.velvet.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.velvet.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.5.1-voice-and-quoting-alpha"
    }
    // Only the release APK uses the permanent Velvet key. Never commit the private
    // key or password to the repository; CI injects them from GitHub Actions secrets.
    signingConfigs {
        val keyPath = System.getenv("VELVET_KEYSTORE_PATH")
        val password = System.getenv("VELVET_KEYSTORE_PASSWORD")
        if (!keyPath.isNullOrBlank() && !password.isNullOrBlank()) {
            create("velvetUpdate") {
                storeFile = file(keyPath)
                storePassword = password
                keyAlias = "velvet-update"
                keyPassword = password
                storeType = "PKCS12"
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfigs.findByName("velvetUpdate")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
