#!/usr/bin/env bash
set -euo pipefail

SPOTUI_SHA="f9d05b6450e730469d15f30f9dd4bc790db794db"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

curl -fsSL --retry 3 "https://github.com/Spotui/Spotui/archive/${SPOTUI_SHA}.tar.gz" -o "$TMP/spotui.tar.gz"
tar -xzf "$TMP/spotui.tar.gz" -C "$TMP"
SRC="$TMP/Spotui-${SPOTUI_SHA}/innertube"
DEST="sora-overlay/youtube-innertube"

rm -rf "$DEST"
mkdir -p "$DEST"
cp -R "$SRC/src" "$DEST/src"
cp "$TMP/Spotui-${SPOTUI_SHA}/LICENSE" "$DEST/UPSTREAM_LICENSE_GPL-3.0.txt"
printf '%s\n' "$SPOTUI_SHA" > "$DEST/UPSTREAM_REVISION"

cat > "$DEST/build.gradle.kts" <<'GRADLE'
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"
}

val newPipeExtractorRaw: Configuration by configurations.creating {
    isTransitive = false
    isCanBeConsumed = false
}

dependencies {
    newPipeExtractorRaw("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
}

val newPipeExtractorStripped = tasks.register<org.gradle.api.tasks.bundling.Jar>("stripNewPipeExtractorUtils") {
    archiveFileName.set("NewPipeExtractor-v0.26.5-noutils.jar")
    destinationDirectory.set(layout.buildDirectory.dir("stripped-libs"))
    from(provider { newPipeExtractorRaw.map { zipTree(it) } }) {
        exclude("org/schabi/newpipe/extractor/utils/Utils.class")
        exclude("org/schabi/newpipe/extractor/utils/Utils\$*.class")
    }
}

android {
    namespace = "com.metrolist.innertube"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("io.ktor:ktor-client-core:3.4.2")
    implementation("io.ktor:ktor-client-okhttp:3.4.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.4.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.2")
    implementation("io.ktor:ktor-client-encoding:3.4.2")
    implementation("org.brotli:dec:0.1.2")
    implementation(files(newPipeExtractorStripped))
    implementation("com.github.TeamNewPipe:nanojson:e9d656ddb49a412a5a0a5d5ef20ca7ef09549996")
    implementation("org.jsoup:jsoup:1.22.2")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.google.protobuf:protobuf-javalite:4.35.0")
    implementation("org.mozilla:rhino:1.8.1")
    implementation("org.mozilla:rhino-engine:1.8.1")
    implementation("com.jakewharton.timber:timber:5.0.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
}
GRADLE

echo "Vendored Spotui Innertube at ${SPOTUI_SHA} into ${DEST}."
