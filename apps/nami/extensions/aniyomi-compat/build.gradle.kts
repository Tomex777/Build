plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "app.nami.compat.aniyomi"
    compileSdk = 36

    defaultConfig { minSdk = 26 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:source-api"))
    implementation(project(":core:source-runtime"))

    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.github.mihonapp:injekt:91edab2317")
    api("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("org.jsoup:jsoup:1.22.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:1.9.0")
    implementation("io.reactivex:rxjava:1.3.8")
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
