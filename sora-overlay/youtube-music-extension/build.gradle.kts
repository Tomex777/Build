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
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":extension-api"))
    implementation(project(":youtube-innertube"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
