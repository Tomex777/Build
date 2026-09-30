# Veya keeps its persisted model property names explicit in JSON, so no model keep rules are needed.
-keepattributes *Annotation*

# libVLC's native JNI layer resolves Java classes/members by their original names.
# R8 full mode must not rename or remove the libVLC Java bridge.
-keep class org.videolan.libvlc.** { *; }
-keep interface org.videolan.libvlc.** { *; }

# WorkManager/Room instantiate these classes reflectively in release builds.
# AGP 9 / R8 full mode can otherwise remove the required constructors.
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.InputMerger {
    <init>();
}
