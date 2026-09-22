
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    id ("com.google.dagger.hilt.android")
    id ("kotlin-kapt")
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10"

}

android {
    namespace = "com.example.whatsapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.whatsapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    // Keep native media libraries out of one oversized universal APK.
    // Users install the APK matching their CPU; universal remains available for CI/debugging.
    splits {
        abi {
            isEnable = true
            isUniversalApk = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }
}

dependencies {
    implementation(project(":night-extension-sdk"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.firebase.database)


    // Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))

    // Firebase Auth
    implementation("com.google.firebase:firebase-auth-ktx")

    // Firebase Realtime Database
    implementation("com.google.firebase:firebase-database-ktx")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation(libs.androidx.material3)
    implementation(libs.material)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material.icons.extended.android)


    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Jetpack Compose Integration
    implementation("androidx.navigation:navigation-compose:2.7.6")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    //hilt
    implementation ("com.google.dagger:hilt-android:2.57.2")
    kapt("com.google.dagger:hilt-compiler:2.57.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    //coil dependency
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")
    implementation("io.coil-kt:coil:2.7.0")

    // Add this line for Material Icons
    implementation("androidx.compose.material:material-icons-extended-android:1.6.8")

    // Activity Compose (Must for LocalActivity)
    implementation("androidx.activity:activity-compose:1.9.0")
    
    // OkHttp for API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Night local document understanding (PDF text extraction)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // MIT, minSdk 21: embedded PDF rendering/zoom/scroll for Night documents.
    implementation("io.github.afreakyelf:Pdf-Viewer:2.4.0")

    // Night sandboxed JavaScript command runtime. Rhino runs in interpreter mode on Android.
    implementation("org.mozilla:rhino:1.9.1")

    // Night local persistence
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    kapt("androidx.room:room-compiler:2.7.2")

    // Night scheduled AI tasks
    implementation("androidx.work:work-runtime-ktx:2.10.3")

    // Night video engine (VLC/libVLC)
    implementation("org.videolan.android:libvlc-all:3.7.6")

    // Night full-screen media editor
    implementation("com.burhanrashid52:photoeditor:3.1.1")
    implementation("com.vanniktech:android-image-cropper:4.7.0")
    implementation("androidx.media3:media3-transformer:1.11.1")

    // Mihon reader transplant — upstream reader mechanics
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.viewpager:viewpager:1.1.0")
    implementation("com.github.tachiyomiorg:DirectionalViewPager:1.0.0")
    // Last Mihon fork revision before its compileSdk 37 toolchain bump.
    // Reader behavior/API is the same path Night uses; this keeps Night on compileSdk 36.
    implementation("com.github.mihonapp:subsampling-scale-image-view:64b392f85fff37bf011dfcc0db10574cfed8937c")
}
