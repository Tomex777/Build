plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.night.keyboard.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:model"))
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("androidx.room:room-compiler:2.8.5")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    testImplementation("junit:junit:4.13.2")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}
