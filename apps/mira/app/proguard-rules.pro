# Public extension ABI is supplied by the minified host to installed extension APKs.
-keep class app.mira.domain.** { *; }
-keep interface app.mira.domain.** { *; }
-keep class app.mira.source.** { *; }
-keep interface app.mira.source.** { *; }
-keep enum app.mira.source.** { *; }
-keep class org.videolan.libvlc.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
