plugins {
    id("com.android.application")
}

android {
    namespace = "com.night.sora.youtubemusic"
    compileSdk = 36
    val arm64Only = providers.gradleProperty("SPOTUI_ARM64_ONLY").orNull == "true"

    defaultConfig {
        applicationId = "com.night.spotui.ext.youtube.music"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (arm64Only) {
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":spotui-source-api"))
    implementation(project(":youtube-innertube"))
    implementation(files(
        "libs/youtube-engine-api-release.aar",
        "libs/youtube-engine-core-release.aar",
    ))
    implementation("io.github.dokar3:quickjs-kt-android:1.0.15")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
}
