plugins {
    id("com.android.application")
}

android {
    namespace = "com.night.sora.youtubemusic"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.night.sora.ext.youtube.music"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":extension-api"))
    implementation(project(":youtube-innertube"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
}
