
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    id ("com.google.dagger.hilt.android")
    id ("kotlin-kapt")
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"

}

android {
    namespace = "com.example.whatsapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.whatsapp"
        minSdk = 28
        targetSdk = 34
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
}

dependencies {
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
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Jetpack Compose Integration
    implementation("androidx.navigation:navigation-compose:2.7.6")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    //hilt
    implementation ("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    //coil dependency
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil:2.7.0")

    // Add this line for Material Icons
    implementation("androidx.compose.material:material-icons-extended-android:1.6.8")

    // Activity Compose (Must for LocalActivity)
    implementation("androidx.activity:activity-compose:1.9.0")
    
    // OkHttp for API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
