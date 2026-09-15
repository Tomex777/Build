package dev.nightmods.core.hook;

import android.app.Application;
import android.content.Context;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import dev.nightmods.core.config.BubbleStyleConfig;
import dev.nightmods.core.config.NightCoreSettingsClient;
import dev.nightmods.core.hook.adapters.InstagramAdapter;
import dev.nightmods.core.hook.adapters.TargetAdapter;
import dev.nightmods.core.hook.adapters.TargetAppInfo;
import dev.nightmods.core.hook.adapters.TargetCompatibility;
import dev.nightmods.core.hook.adapters.WhatsAppAdapter;

/** One Night module, many target-app adapters. */
public final class NightCoreHook implements IXposedHookLoadPackage {
    private static final Set<String> HOOKED_PROCESSES = ConcurrentHashMap.newKeySet();

    private final List<TargetAdapter> adapters = List.of(new WhatsAppAdapter(), new InstagramAdapter());

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam == null || lpparam.packageName == null) return;

        TargetAdapter target = null;
        for (TargetAdapter adapter : adapters) {
            if (adapter.packageName().equals(lpparam.packageName)) {
                target = adapter;
                break;
            }
        }
        if (target == null) return;

        String processName = lpparam.processName == null ? lpparam.packageName : lpparam.processName;
        String processKey = lpparam.packageName + "|" + processName;
        if (!HOOKED_PROCESSES.add(processKey)) return;

        TargetAdapter selectedAdapter = target;
        try {
            XposedHelpers.findAndHookMethod(
                    Application.class,
                    "attach",
                    Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam<?> param) {
                            Context context = param.args != null && param.args.length > 0
                                    ? (Context) param.args[0]
                                    : null;
                            if (context == null) {
                                XposedBridge.log("NightCore skipped: target context unavailable for "
                                        + selectedAdapter.displayName());
                                return;
                            }

                            Context appContext = context.getApplicationContext();
                            if (appContext == null) appContext = context;
                            Context settingsContext = appContext;

                            Thread loader = new Thread(() -> loadAndAttach(
                                    settingsContext,
                                    selectedAdapter,
                                    lpparam,
                                    processName
                            ), "NightCoreSettings-" + selectedAdapter.packageName());
                            loader.setDaemon(true);
                            loader.start();
                        }
                    }
            );
        } catch (Throwable error) {
            HOOKED_PROCESSES.remove(processKey);
            XposedBridge.log("NightCore lifecycle hook failed: " + target.displayName());
            XposedBridge.log(error);
        }
    }

    private static void loadAndAttach(
            Context context,
            TargetAdapter selectedAdapter,
            XC_LoadPackage.LoadPackageParam lpparam,
            String processName
    ) {
        final BubbleStyleConfig config;
        try {
            config = NightCoreSettingsClient.readBubbleStyle(context);
        } catch (Throwable error) {
            XposedBridge.log("NightCore skipped: settings unavailable for "
                    + selectedAdapter.displayName());
            XposedBridge.log(error);
            return;
        }

        if (!config.enabled || !selectedAdapter.enabled(config)) return;

        final TargetAppInfo appInfo;
        try {
            appInfo = TargetAppInfo.resolve(context, selectedAdapter.packageName());
        } catch (Throwable error) {
            XposedBridge.log("NightCore skipped: could not resolve target version for "
                    + selectedAdapter.displayName());
            XposedBridge.log(error);
            return;
        }

        TargetCompatibility compatibility = selectedAdapter.compatibility(appInfo);
        if (compatibility != TargetCompatibility.SUPPORTED) {
            XposedBridge.log("NightCore skipped UI hooks: " + selectedAdapter.displayName()
                    + " compatibility=" + compatibility + " " + appInfo.describe());
            return;
        }

        XposedBridge.log("NightCore attaching: " + selectedAdapter.displayName()
                + " " + appInfo.describe() + " process=" + processName);
        try {
            selectedAdapter.attach(context, lpparam, config, appInfo);
        } catch (Throwable error) {
            XposedBridge.log("NightCore adapter failed: " + selectedAdapter.displayName());
            XposedBridge.log(error);
        }
    }
}
