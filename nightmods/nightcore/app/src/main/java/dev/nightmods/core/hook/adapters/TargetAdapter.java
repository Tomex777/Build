package dev.nightmods.core.hook.adapters;

import android.content.Context;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import dev.nightmods.core.config.BubbleStyleConfig;

public interface TargetAdapter {
    String packageName();
    String displayName();
    boolean enabled(BubbleStyleConfig config);
    TargetCompatibility compatibility(TargetAppInfo appInfo);
    void attach(
            Context context,
            XC_LoadPackage.LoadPackageParam loadPackage,
            BubbleStyleConfig config,
            TargetAppInfo appInfo
    ) throws Throwable;
}
