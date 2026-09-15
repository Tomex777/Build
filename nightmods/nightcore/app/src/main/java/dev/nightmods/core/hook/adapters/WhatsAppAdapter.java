package dev.nightmods.core.hook.adapters;

import android.content.Context;

import io.github.libxposed.api.XposedInterface;
import dev.nightmods.core.config.BubbleStyleConfig;

public final class WhatsAppAdapter implements TargetAdapter {
    @Override public String packageName() { return "com.whatsapp"; }
    @Override public String displayName() { return "WhatsApp"; }
    @Override public boolean enabled(BubbleStyleConfig config) { return config.whatsapp; }

    @Override
    public TargetCompatibility compatibility(TargetAppInfo appInfo) {
        // Do not guess compatibility for obfuscated WhatsApp builds.
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
        xposed.log("NightCore: WhatsApp adapter ready for " + appInfo.describe()
                + " radius=" + config.radius + " spacing=" + config.spacing);
    }
}
