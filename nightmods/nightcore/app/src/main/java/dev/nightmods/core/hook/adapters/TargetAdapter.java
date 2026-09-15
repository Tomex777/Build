package dev.nightmods.core.hook.adapters;

import android.content.Context;

import io.github.libxposed.api.XposedInterface;
import dev.nightmods.core.config.BubbleStyleConfig;

public interface TargetAdapter {
    String packageName();
    String displayName();
    boolean enabled(BubbleStyleConfig config);
    TargetCompatibility compatibility(TargetAppInfo appInfo);
    void attach(
            Context context,
            XposedInterface xposed,
            ClassLoader classLoader,
            BubbleStyleConfig config,
            TargetAppInfo appInfo
    ) throws Throwable;
}
