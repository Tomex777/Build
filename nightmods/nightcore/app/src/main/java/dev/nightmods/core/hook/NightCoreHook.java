package dev.nightmods.core.hook;

import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import dev.nightmods.core.config.BubbleStyleConfig;
import dev.nightmods.core.hook.adapters.InstagramAdapter;
import dev.nightmods.core.hook.adapters.TargetAdapter;
import dev.nightmods.core.hook.adapters.WhatsAppAdapter;

/** One Night module, many target-app adapters. */
public final class NightCoreHook implements IXposedHookLoadPackage {
    private final List<TargetAdapter> adapters = List.of(new WhatsAppAdapter(), new InstagramAdapter());

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam == null || lpparam.packageName == null) return;
        BubbleStyleConfig config = BubbleStyleConfig.loadForHook();
        if (!config.enabled) return;
        for (TargetAdapter adapter : adapters) {
            if (!adapter.packageName().equals(lpparam.packageName) || !adapter.enabled(config)) continue;
            XposedBridge.log("NightCore attached: " + adapter.displayName()
                    + " package=" + lpparam.packageName + " process=" + lpparam.processName);
            try {
                adapter.attach(lpparam, config);
            } catch (Throwable error) {
                XposedBridge.log("NightCore adapter failed: " + adapter.displayName());
                XposedBridge.log(error);
            }
            return;
        }
    }
}
