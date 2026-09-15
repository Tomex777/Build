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
        versionCode = 2
        versionName = "0.2.0"
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
    // Compile-only stubs. These classes are supplied by LSPosed at runtime and are NOT packaged.
    compileOnly(project(":xposed-stubs"))
}
