plugins {
    id("com.android.application")
}

android {
    namespace = "com.night.spotui.ext.musicdex"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.night.spotui.ext.musicdex"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":spotui-source-api"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
}
