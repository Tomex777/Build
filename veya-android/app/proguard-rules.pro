# Veya keeps its persisted model property names explicit in JSON, so no model keep rules are needed.
-keepattributes *Annotation*

# WorkManager/Room instantiate these classes reflectively in release builds.
# AGP 9 / R8 full mode can otherwise remove the required constructors.
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.InputMerger {
    <init>();
}
