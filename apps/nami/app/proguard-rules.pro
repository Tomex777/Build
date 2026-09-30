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

# AndroidX Test's release-candidate instrumentation shares the production app
# classloader. The target already resolves androidx.tracing transitively, but R8 can
# remove it because production code does not call it directly. Keep the tracing
# runtime so the signer-matched runner can start and exercise the real minified APK.
-keep class androidx.tracing.** { *; }

# The API 36 release-acceptance APK is separately compiled against NamiApplication's
# installed-source registry accessor, then executed against the real minified target.
# Preserve that concrete getter and registry ABI so the proof exercises installed
# extensions instead of failing because R8 optimized away test-visible host symbols.
-keep class app.nami.android.NamiApplication {
    public app.nami.runtime.CachingNamiSourceRegistry getInstalledSourceRegistry();
}
-keep class app.nami.runtime.CachingNamiSourceRegistry { *; }
