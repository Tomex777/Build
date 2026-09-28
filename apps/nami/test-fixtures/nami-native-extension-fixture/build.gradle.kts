plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "app.nami.fixture.nativeextension"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.nami.fixture.nativeextension"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    compileOnly(project(":core:domain"))
    compileOnly(project(":core:source-api"))
}
