plugins { kotlin("jvm") }

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:source-api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}
