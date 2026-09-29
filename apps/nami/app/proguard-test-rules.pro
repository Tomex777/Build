-keep class kotlin.** { *; }
-dontwarn com.google.errorprone.annotations.**
-keep class app.nami.data.local.NamiDatabase { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class app.nami.android.NamiNativeConfigurationHandle { *; }
-keep class app.nami.android.NamiVlcPlayer { *; }
-keep class androidx.compose.** { *; }
# Compose UI Test invokes intSetOf from the target app classloader.
# Keep the one generated facade whose API the release app does not call directly.
-keep class androidx.collection.IntSetKt { *; }
