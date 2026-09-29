# Cortex release rules.
# Keep this file intentionally small: R8 can optimize the Compose/Kotlin app normally.
# Add targeted keep rules here only when a dependency proves it needs reflection metadata.
-keep class kotlin.jvm.internal.Intrinsics { *; }
\n# Release instrumentation executes AndroidJUnitRunner inside the target process.\n# Keep its tracing runtime present in the minified APK; this does not alter app behavior.\n-keep class androidx.tracing.** { *; }\n