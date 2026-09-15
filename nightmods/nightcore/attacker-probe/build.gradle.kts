plugins {
    id("com.android.application")
}

android {
    namespace = "dev.nightmods.attacker"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.nightmods.attacker"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    sourceSets {
        getByName("main") {
            java.srcDir("../target-probe/src/main/java")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
