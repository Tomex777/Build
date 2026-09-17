pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SoraAndroid"
include(":app")
include(":extension-api")
include(":test-extension")
include(":live-extension")
include(":meme-extension")
include(":tumblr-meme-extension")
