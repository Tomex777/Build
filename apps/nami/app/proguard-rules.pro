# Nami extensions are separately installed APKs compiled against this stable public ABI.
# Keep package names, constructors, accessors, and suspend bridge methods unchanged.
-keep public class app.nami.domain.** { *; }
-keep public interface app.nami.domain.** { *; }
-keep public class app.nami.source.** { *; }
-keep public interface app.nami.source.** { *; }
-keep public enum app.nami.source.** { *; }

# libVLC uses JNI and native-side class lookups at runtime.
-keep class org.videolan.libvlc.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
# Release acceptance instrumentation runs against the real non-debuggable APK and
# therefore shares the target app classloader. AGP removes shared test dependencies
# from the androidTest APK; keep Kotlin runtime classes in the minified target so
# AndroidX Test and Kotlin-compiled test code can start against the production APK.
-keep class kotlin.** { *; }
