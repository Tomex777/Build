# Only minified instrumentation variant: preserve symbols called by external tests.
-keep class app.mira.android.** { *; }
-keep class app.mira.runtime.** { *; }
-keep class kotlin.** { *; }
-keep class androidx.tracing.** { *; }
