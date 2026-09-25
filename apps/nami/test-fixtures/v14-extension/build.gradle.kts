plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "app.nami.fixture.v14"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.nami.fixture.v14"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "14.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    compileOnly(project(":extensions:aniyomi-compat"))
}
