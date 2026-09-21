plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tomex.cobaltandroid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tomex.cobaltandroid"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-poc"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*"
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.1")

    // First test the published, unmodified Cobalt artifact.
    implementation("com.github.auties00:cobalt-lib:0.1.0")
}
