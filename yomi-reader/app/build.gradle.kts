import org.gradle.api.GradleException

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseStoreFile = providers.environmentVariable("YOMI_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("YOMI_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("YOMI_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("YOMI_RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "app.yomi.reader"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.yomi.reader"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.environmentVariable("YOMI_VERSION_CODE").orNull?.toIntOrNull() ?: 1
        versionName = providers.environmentVariable("YOMI_VERSION_NAME").orNull?.takeIf { it.isNotBlank() } ?: "1.0.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("production") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
        }
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("production")
            }
        }
        create("candidate") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks.add("release")
        }
        create("acceptance") {
            initWith(getByName("candidate"))
            applicationIdSuffix = ".dev"
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks.add("candidate")
            matchingFallbacks.add("release")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = true
        }
    }

    sourceSets {
        getByName("acceptance") {
            java.srcDir("src/debug/java")
            manifest.srcFile("src/debug/AndroidManifest.xml")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    doFirst {
        if (!hasReleaseSigning) {
            throw GradleException(
                "Production release signing is not configured. " +
                    "Set YOMI_RELEASE_STORE_FILE, YOMI_RELEASE_STORE_PASSWORD, " +
                    "YOMI_RELEASE_KEY_ALIAS, and YOMI_RELEASE_KEY_PASSWORD.",
            )
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":reader-core-contract"))
    implementation(project(":reader-android"))

    val composeBom = platform("androidx.compose:compose-bom-beta:2026.04.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
