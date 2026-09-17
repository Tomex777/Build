plugins {
    id("com.android.application")
}

val tumblrApiKey = providers.gradleProperty("SORA_TUMBLR_API_KEY")
    .orElse(providers.environmentVariable("SORA_TUMBLR_API_KEY"))
    .orElse("")

android {
    namespace = "com.night.sora.tumblrmemeextension"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.night.sora.ext.memes.tumblr"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["tumblrApiKey"] = tumblrApiKey.get()
    }
}

dependencies {
    implementation(project(":extension-api"))
}
