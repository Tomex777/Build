package dev.nightmods.core.hook;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import dev.nightmods.core.config.BubbleStyleConfig;
import dev.nightmods.core.hook.adapters.InstagramAdapter;
import dev.nightmods.core.hook.adapters.TargetAdapter;
import dev.nightmods.core.hook.adapters.TargetAppInfo;
import dev.nightmods.core.hook.adapters.TargetCompatibility;
import dev.nightmods.core.hook.adapters.WhatsAppAdapter;

public final class NightCoreHook extends XposedModule {
    private static volatile NightCoreHook activeInstance;

    private final Set<String> hookedPackages = ConcurrentHashMap.newKeySet();
    private final List<TargetAdapter> adapters = List.of(new WhatsAppAdapter(), new InstagramAdapter());
    private final String processName;
    private volatile TargetAdapter pendingAdapter;
    private volatile ClassLoader pendingClassLoader;

    public NightCoreHook(XposedInterface base, ModuleLoadedParam param) {
        super(base, param);
        processName = param.getProcessName();
        activeInstance = this;
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

        pendingAdapter = target;
        pendingClassLoader = param.getClassLoader();
        try {
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            hook(attach, ApplicationAttachHooker.class);
        } catch (Throwable error) {
            hookedPackages.remove(packageName);
            pendingAdapter = null;
            pendingClassLoader = null;
            log("NightCore lifecycle hook failed: " + target.displayName(), error);
        }
    }

    /** API-100 hook callbacks are static Hooker methods, so bridge back to the process-local module. */
    public static final class ApplicationAttachHooker implements XposedInterface.Hooker {
        public static void after(XposedInterface.AfterHookCallback callback) {
            NightCoreHook instance = activeInstance;
            if (instance == null) return;

            Object[] args = callback.getArgs();
            if (args.length == 0 || !(args[0] instanceof Context context)) {
                instance.log("NightCore skipped: target context unavailable after Application.attach");
                return;
            }
            instance.onApplicationAttached(context);
        }
    }

    private void onApplicationAttached(Context context) {
        TargetAdapter selectedAdapter = pendingAdapter;
        ClassLoader classLoader = pendingClassLoader;
        if (selectedAdapter == null || classLoader == null) return;

        Context application = context.getApplicationContext();
        Context settingsContext = application == null ? context : application;
        Thread loader = new Thread(
                () -> loadAndAttach(settingsContext, selectedAdapter, classLoader),
                "NightCoreSettings-" + selectedAdapter.packageName());
        loader.setDaemon(true);
        loader.start();
    }

    private void loadAndAttach(Context context, TargetAdapter selectedAdapter, ClassLoader classLoader) {
        final BubbleStyleConfig config;
        try {
            SharedPreferences preferences = getRemotePreferences(BubbleStyleConfig.PREFS);
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
