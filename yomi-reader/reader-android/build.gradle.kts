plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "reader.shared.android"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":reader-core-contract"))
    api("androidx.recyclerview:recyclerview:1.4.0")
    api("androidx.viewpager:viewpager:1.1.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("io.coil-kt.coil3:coil:3.4.0")
    implementation("io.coil-kt.coil3:coil-gif:3.4.0")
    api("com.github.tachiyomiorg:DirectionalViewPager:1.0.0")
    api("com.github.tachiyomiorg:subsampling-scale-image-view:66e0db195d")
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
