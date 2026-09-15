package dev.nightmods.core.hook.adapters;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import dev.nightmods.core.config.BubbleStyleConfig;

public interface TargetAdapter {
    String packageName();
    String displayName();
    boolean enabled(BubbleStyleConfig config);
    void attach(XC_LoadPackage.LoadPackageParam loadPackage, BubbleStyleConfig config) throws Throwable;
}
