plugins { kotlin("jvm") }

kotlin { jvmToolchain(17) }

dependencies {
    compileOnly(project(":core:domain"))
    compileOnly(project(":core:source-api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("org.jsoup:jsoup:1.22.2")
}
