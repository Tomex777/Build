plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "app.nami.source.kayoanime"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.nami.source.kayoanime"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "17.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    compileOnly(project(":extensions:aniyomi-compat"))
    compileOnly("org.jsoup:jsoup:1.22.2")
}
