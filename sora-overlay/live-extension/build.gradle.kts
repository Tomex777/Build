plugins {
    id("com.android.application")
}

android {
    namespace = "com.night.sora.liveextension"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.night.sora.ext.live"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":extension-api"))
}
