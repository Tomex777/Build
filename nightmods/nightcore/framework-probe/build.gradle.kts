plugins {
    id("com.android.application")
}

android {
    namespace = "dev.nightmods.frameworkprobe"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.nightmods.frameworkprobe"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
