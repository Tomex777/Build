plugins {
    id("com.android.application")
}

android {
    namespace = "org.lsposed.manager"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.lsposed.manager"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
