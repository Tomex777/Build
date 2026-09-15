package dev.nightmods.core.hook.adapters;

import android.content.Context;

import io.github.libxposed.api.XposedInterface;
import dev.nightmods.core.config.BubbleStyleConfig;

public final class InstagramAdapter implements TargetAdapter {
    @Override public String packageName() { return "com.instagram.android"; }
    @Override public String displayName() { return "Instagram"; }
    @Override public boolean enabled(BubbleStyleConfig config) { return config.instagram; }

    @Override
    public TargetCompatibility compatibility(TargetAppInfo appInfo) {
        // Do not guess compatibility for rapidly changing Instagram builds.
        // Exact supported versions are added only after inspecting that APK/build.
        return TargetCompatibility.ANALYSIS_REQUIRED;
    }

    @Override
    public void attach(
            Context context,
            XposedInterface xposed,
            ClassLoader classLoader,
            BubbleStyleConfig config,
            TargetAppInfo appInfo
    ) {
        // Reachable only after an exact target version is explicitly marked SUPPORTED.
        xposed.log("NightCore: Instagram adapter ready for " + appInfo.describe()
                + " radius=" + config.radius + " spacing=" + config.spacing);
    }
}
