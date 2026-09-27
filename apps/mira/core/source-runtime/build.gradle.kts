plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:source-api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}
