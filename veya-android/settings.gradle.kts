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

rootProject.name = "Veya"
include(":app")

val sharedEngineRoot = file("../youtube-engine-src/youtube-engine")
check(sharedEngineRoot.isDirectory) {
    "Shared YouTube Engine checkout is missing at ${sharedEngineRoot.absolutePath}. " +
        "CI checks out youtube-engine-foundation into youtube-engine-src before Gradle runs."
}
include(":youtube-engine-api", ":youtube-engine-core")
project(":youtube-engine-api").projectDir = sharedEngineRoot.resolve("youtube-engine-api")
project(":youtube-engine-core").projectDir = sharedEngineRoot.resolve("youtube-engine-core")
