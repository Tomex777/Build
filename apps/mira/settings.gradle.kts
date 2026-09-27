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


rootProject.name = "Mira"
include(":app")
include(":core:domain")
include(":core:source-api")
include(":core:source-runtime")
include(":extensions:internetarchive")
include(":extensions:tvmaze")
