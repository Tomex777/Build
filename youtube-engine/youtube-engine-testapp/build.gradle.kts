plugins { id("com.android.application") }
android {
    namespace = "dev.tomex.youtube.testapp"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.tomex.youtube.testapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    splits { abi { isEnable = true; reset(); include("arm64-v8a", "x86_64"); isUniversalApk = false } }
    buildTypes {
        getByName("release") {
            // Proof-app release: signed with the CI debug key so the ARM64 artifact is directly installable.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation(project(":youtube-engine-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.media3:media3-exoplayer:1.11.1")
}
