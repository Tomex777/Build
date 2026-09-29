-keep class kotlin.** { *; }
-dontwarn com.google.errorprone.annotations.**
-keep class app.nami.data.local.NamiDatabase { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class app.nami.android.NamiNativeConfigurationHandle { *; }
-keep class app.nami.android.NamiVlcPlayer { *; }
-keep class androidx.compose.** { *; }
# Compose UI Test invokes Collection APIs from the target app classloader.
# Preserve test-only APIs that the release app itself does not reference.
-keep class androidx.collection.** { *; }
