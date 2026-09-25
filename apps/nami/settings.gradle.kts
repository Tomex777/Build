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
        maven(url = "https://www.jitpack.io")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "Nami"
include(":app")
include(":core:domain")
include(":core:source-api")
include(":core:source-runtime")
include(":data:local")
include(":extensions:aniyomi-compat")
include(":extensions:kayoanime")
include(":extensions:nami-jikan")


include(":test-fixtures:v17-extension")

include(":test-fixtures:v14-extension")
