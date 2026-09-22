plugins {
    id("com.android.application")
}

android {
    namespace = "com.tomex.securerenderlab.system"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tomex.securerenderlab.system"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0-sanity"
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("../aosp-system-lab/AndroidManifest.xml")
            java.srcDirs("../aosp-system-lab/src")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
