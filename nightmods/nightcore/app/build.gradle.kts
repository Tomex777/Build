plugins {
    id("com.android.application")
}

android {
    namespace = "dev.nightmods.core"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.nightmods.core"
        minSdk = 36
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // API 100 is supplied by LSPosed ET inside hooked processes.
    compileOnly("io.github.libxposed:api:100")
    // The module app packages the API-100 framework service client/provider.
    implementation("io.github.libxposed:service:100-1.0.0")
    compileOnly("androidx.annotation:annotation:1.7.1")
}
