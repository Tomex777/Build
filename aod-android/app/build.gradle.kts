plugins { id("com.android.application") }
android {
    namespace = "com.homira.aod"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.homira.aod"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    signingConfigs {
        getByName("debug") {System.getenv("AOD_QA_KEYSTORE")?.let {storeFile=file(it)}}
        val path = System.getenv("AOD_RELEASE_KEYSTORE")
        if (!path.isNullOrBlank()) create("production") {
            storeFile = file(path)
            storePassword = System.getenv("AOD_RELEASE_STORE_PASSWORD")
            keyAlias = System.getenv("AOD_RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("AOD_RELEASE_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("production")?.let { signingConfig = it }
        }
    }
}
dependencies {
    implementation("androidx.activity:activity:1.13.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
