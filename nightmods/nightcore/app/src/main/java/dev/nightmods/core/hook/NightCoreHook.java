package dev.nightmods.core.hook;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedContext;
import io.github.libxposed.api.XposedModule;
import dev.nightmods.core.config.BubbleStyleConfig;
import dev.nightmods.core.hook.adapters.InstagramAdapter;
import dev.nightmods.core.hook.adapters.TargetAdapter;
import dev.nightmods.core.hook.adapters.TargetAppInfo;
import dev.nightmods.core.hook.adapters.TargetCompatibility;
import dev.nightmods.core.hook.adapters.WhatsAppAdapter;

/** One modern API-100 Night module, many target-app adapters. */
public final class NightCoreHook extends XposedModule {
    private final Set<String> hookedPackages = ConcurrentHashMap.newKeySet();
    private final List<TargetAdapter> adapters = List.of(new WhatsAppAdapter(), new InstagramAdapter());
    private final String processName;

    public NightCoreHook(XposedContext base, ModuleLoadedParam param) {
        super(base, param);
        processName = param.getProcessName();
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String packageName = param.getPackageName();
        TargetAdapter target = null;
        for (TargetAdapter adapter : adapters) {
            if (adapter.packageName().equals(packageName)) {
                target = adapter;
                break;
            }
        }
        if (target == null || !hookedPackages.add(packageName)) return;

        TargetAdapter selectedAdapter = target;
        ClassLoader packageClassLoader = param.getClassLoader();
        try {
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            hookAfter(attach, callback -> {
                Context context = callback.getArg(0);
                if (context == null) {
                    log("NightCore skipped: target context unavailable for " + selectedAdapter.displayName());
                    return;
                }

                Context application = context.getApplicationContext();
                Context settingsContext = application == null ? context : application;
                Thread loader = new Thread(
                        () -> loadAndAttach(settingsContext, selectedAdapter, packageClassLoader),
                        "NightCoreSettings-" + selectedAdapter.packageName());
                loader.setDaemon(true);
                loader.start();
            });
        } catch (Throwable error) {
            hookedPackages.remove(packageName);
            log("NightCore lifecycle hook failed: " + selectedAdapter.displayName(), error);
        }
    }

    private void loadAndAttach(
            Context context,
            TargetAdapter selectedAdapter,
            ClassLoader classLoader
    ) {
        final BubbleStyleConfig config;
        try {
            SharedPreferences preferences = getSharedPreferences(
                    BubbleStyleConfig.PREFS, Context.MODE_PRIVATE);
            if (preferences == null) {
                log("NightCore skipped: framework remote preferences unavailable for "
                        + selectedAdapter.displayName());
                return;
            }
            config = BubbleStyleConfig.fromPreferences(preferences);
        } catch (Throwable error) {
            log("NightCore skipped: framework settings unavailable for "
                    + selectedAdapter.displayName(), error);
            return;
        }

        if (!config.enabled || !selectedAdapter.enabled(config)) return;

        final TargetAppInfo appInfo;
        try {
            appInfo = TargetAppInfo.resolve(context, selectedAdapter.packageName());
        } catch (Throwable error) {
            log("NightCore skipped: could not resolve target version for "
                    + selectedAdapter.displayName(), error);
            return;
        }

        TargetCompatibility compatibility = selectedAdapter.compatibility(appInfo);
        if (compatibility != TargetCompatibility.SUPPORTED) {
            log("NightCore skipped UI hooks: " + selectedAdapter.displayName()
                    + " compatibility=" + compatibility + " " + appInfo.describe());
            return;
        }

        log("NightCore attaching: " + selectedAdapter.displayName()
                + " " + appInfo.describe() + " process=" + processName);
        try {
            selectedAdapter.attach(context, this, classLoader, config, appInfo);
        } catch (Throwable error) {
            log("NightCore adapter failed: " + selectedAdapter.displayName(), error);
        }
    }
}
