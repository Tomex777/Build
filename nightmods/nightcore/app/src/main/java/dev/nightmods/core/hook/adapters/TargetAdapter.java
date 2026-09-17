package dev.nightmods.core.hook.adapters;

import android.content.Context;

import io.github.libxposed.api.XposedInterface;
import dev.nightmods.core.config.NightCoreConfig;

public interface TargetAdapter {
    String packageName();
    String displayName();
    boolean enabled(NightCoreConfig config);
    TargetCompatibility compatibility(TargetAppInfo appInfo);

    /**
     * Some first-party/system targets can safely perform structural runtime probing while
     * compatibility is still ANALYSIS_REQUIRED. Third-party app adapters stay fail-closed.
     */
    default boolean attachWhenAnalysisRequired() { return false; }

    void attach(
            Context context,
            XposedInterface xposed,
            ClassLoader classLoader,
            NightCoreConfig config,
            TargetAppInfo appInfo
    ) throws Throwable;
}
