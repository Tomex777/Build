# Only minified instrumentation variant: preserve symbols called by external tests.
-keep class app.mira.android.** { *; }
-keep class app.mira.runtime.** { *; }
-keep class kotlin.** { *; }
-keep class androidx.tracing.** { *; }

-keep class kotlinx.coroutines.** { *; }
-keep class androidx.compose.** { *; }
-keep class androidx.collection.IntSetKt { *; }
-dontwarn com.google.errorprone.annotations.**

# Compose/Espresso tests link this host interface after host shrinking.
-keep interface com.google.common.util.concurrent.ListenableFuture { *; }
