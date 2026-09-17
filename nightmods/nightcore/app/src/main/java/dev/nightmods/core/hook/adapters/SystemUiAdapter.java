package dev.nightmods.core.hook.adapters;

import android.content.Context;
import android.os.Build;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import dev.nightmods.core.config.NightCoreConfig;

/**
 * Phone-level SystemUI capability adapter.
 *
 * The first proof tweak is additive status-bar side padding for Samsung Android 16 / One UI.
 * It is deliberately opt-in and structurally gated: if the exact IndicatorGarden subclasses
 * and zero-argument int padding methods are not present, no visual hook is installed.
 */
public final class SystemUiAdapter implements TargetAdapter {
    private static final String[] SAMSUNG_INDICATOR_GARDENS = {
            "com.android.systemui.statusbar.phone.IndicatorGardenAlgorithmCenterCutout",
            "com.android.systemui.statusbar.phone.IndicatorGardenAlgorithmNoCutout",
            "com.android.systemui.statusbar.phone.IndicatorGardenAlgorithmSidelingCenterCutout"
    };

    private static volatile int extraPaddingPx;

    @Override public String packageName() { return "com.android.systemui"; }
    @Override public String displayName() { return "System UI"; }
    @Override public boolean enabled(NightCoreConfig config) { return config.systemUi.enabled; }

    @Override
    public TargetCompatibility compatibility(TargetAppInfo appInfo) {
        if (Build.VERSION.SDK_INT != 36) return TargetCompatibility.UNSUPPORTED;
        // SystemUI is OEM-specific. Runtime structural probing decides whether the first tweak
        // is safe to attach; manager status therefore remains Analysis required for now.
        return TargetCompatibility.ANALYSIS_REQUIRED;
    }

    @Override public boolean attachWhenAnalysisRequired() { return true; }

    @Override
    public void attach(
            Context context,
            XposedInterface xposed,
            ClassLoader classLoader,
            NightCoreConfig config,
            TargetAppInfo appInfo
    ) {
        xposed.log("NightCore: SystemUI probe " + appInfo.describe()
                + " sdk=" + Build.VERSION.SDK_INT
                + " manufacturer=" + Build.MANUFACTURER
                + " model=" + Build.MODEL
                + " fingerprint=" + Build.FINGERPRINT);

        if (!config.systemUi.statusBarPaddingEnabled) {
            xposed.log("NightCore: SystemUI structural probe only; status-bar padding is disabled");
            probeSamsungPaddingTargets(xposed, classLoader, false);
            return;
        }

        if (Build.VERSION.SDK_INT != 36 || !"samsung".equalsIgnoreCase(Build.MANUFACTURER)) {
            xposed.log("NightCore: status-bar padding not attached; first implementation is gated to Samsung Android 16");
            probeSamsungPaddingTargets(xposed, classLoader, false);
            return;
        }

        int dp = config.systemUi.statusBarPaddingDp;
        int px = Math.round(dp * context.getResources().getDisplayMetrics().density);
        if (px <= 0) {
            xposed.log("NightCore: status-bar padding enabled with 0dp; no hook needed");
            return;
        }

        extraPaddingPx = px;
        int hooked = probeSamsungPaddingTargets(xposed, classLoader, true);
        if (hooked == 0) {
            extraPaddingPx = 0;
            xposed.log("NightCore: SystemUI analysis required; no compatible IndicatorGarden padding methods found");
        } else {
            xposed.log("NightCore: status-bar padding attached safely to " + hooked
                    + " IndicatorGarden implementation(s), extra=" + dp + "dp/" + px + "px");
        }
    }

    private static int probeSamsungPaddingTargets(
            XposedInterface xposed,
            ClassLoader classLoader,
            boolean installHooks
    ) {
        int compatible = 0;
        for (String className : SAMSUNG_INDICATOR_GARDENS) {
            try {
                Class<?> target = Class.forName(className, false, classLoader);
                Method left = exactPaddingMethod(target, "calculateLeftPadding");
                Method right = exactPaddingMethod(target, "calculateRightPadding");
                if (left == null || right == null) {
                    xposed.log("NightCore: SystemUI probe mismatch " + className
                            + " (expected calculateLeftPadding/calculateRightPadding -> int)");
                    continue;
                }
                compatible++;
                xposed.log("NightCore: SystemUI probe compatible " + className);
                if (installHooks) {
                    xposed.hook(left, PaddingHooker.class);
                    xposed.hook(right, PaddingHooker.class);
                }
            } catch (ClassNotFoundException ignored) {
                xposed.log("NightCore: SystemUI probe class absent " + className);
            } catch (Throwable error) {
                xposed.log("NightCore: SystemUI probe failed " + className, error);
            }
        }
        return compatible;
    }

    private static Method exactPaddingMethod(Class<?> target, String name) {
        try {
            Method method = target.getDeclaredMethod(name);
            if (method.getParameterCount() != 0 || method.getReturnType() != int.class) return null;
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    /** API-100 after callback: preserve the OEM result and add only the configured delta. */
    public static final class PaddingHooker implements XposedInterface.Hooker {
        public static void after(XposedInterface.AfterHookCallback callback) {
            if (callback.getThrowable() != null) return;
            Object result = callback.getResult();
            int extra = extraPaddingPx;
            if (!(result instanceof Integer value) || extra <= 0) return;
            callback.setResult(Math.max(0, value + extra));
        }
    }
}
