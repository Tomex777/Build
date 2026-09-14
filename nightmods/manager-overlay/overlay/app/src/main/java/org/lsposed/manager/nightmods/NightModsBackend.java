/*
 * Night Mods integration layer for LSPosed ET.
 * Derived integration is intended to live inside the GPLv3 LSPosed ET manager source tree.
 */
package org.lsposed.manager.nightmods;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import org.lsposed.manager.ConfigManager;
import org.lsposed.manager.adapters.ScopeAdapter;
import org.lsposed.manager.util.ModuleUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stable Night-facing facade over LSPosed ET manager plumbing.
 *
 * Night UI code should call this class rather than talking to Binder/service classes directly.
 * That keeps future LSPosed ET updates isolated to one adapter layer.
 */
public final class NightModsBackend {
    private static final ModuleUtil MODULES = ModuleUtil.getInstance();

    private NightModsBackend() {}

    public static boolean isFrameworkActive() {
        return ConfigManager.isBinderAlive();
    }

    @NonNull
    public static String frameworkVersion() {
        String version = ConfigManager.getXposedVersionName();
        return version == null ? "" : version;
    }

    public static int frameworkApi() {
        return ConfigManager.getXposedApiVersion();
    }

    @NonNull
    public static List<ModuleRow> modules() {
        Map<Pair<String, Integer>, ModuleUtil.InstalledModule> map = MODULES.getModules();
        List<ModuleRow> result = new ArrayList<>();
        if (map == null) return result;
        for (ModuleUtil.InstalledModule module : map.values()) {
            result.add(new ModuleRow(
                    module.packageName,
                    module.userId,
                    module.getAppName().toString(),
                    module.versionName == null ? "" : module.versionName,
                    MODULES.isModuleEnabled(module.packageName),
                    ConfigManager.getModuleScope(module.packageName).size(),
                    module.legacy
            ));
        }
        result.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return result;
    }

    public static boolean setEnabled(@NonNull String packageName, boolean enabled) {
        return MODULES.setModuleEnabled(packageName, enabled);
    }

    @NonNull
    public static Set<TargetApp> scope(@NonNull String packageName) {
        Set<TargetApp> result = new HashSet<>();
        for (ScopeAdapter.ApplicationWithEquals app : ConfigManager.getModuleScope(packageName)) {
            result.add(new TargetApp(app.packageName, app.userId));
        }
        return result;
    }

    public static boolean setScope(
            @NonNull String modulePackage,
            boolean legacy,
            @NonNull Set<TargetApp> targets
    ) {
        Set<ScopeAdapter.ApplicationWithEquals> apps = new HashSet<>();
        for (TargetApp target : targets) {
            apps.add(new ScopeAdapter.ApplicationWithEquals(target.packageName, target.userId));
        }
        return ConfigManager.setModuleScope(modulePackage, legacy, apps);
    }

    @Nullable
    public static ModuleUtil.InstalledModule module(@NonNull String packageName, int userId) {
        return MODULES.getModule(packageName, userId);
    }

    public static final class TargetApp {
        @NonNull public final String packageName;
        public final int userId;

        public TargetApp(@NonNull String packageName, int userId) {
            this.packageName = packageName;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof TargetApp)) return false;
            TargetApp app = (TargetApp) other;
            return userId == app.userId && packageName.equals(app.packageName);
        }

        @Override
        public int hashCode() {
            return 31 * packageName.hashCode() + userId;
        }
    }

    public static final class ModuleRow {
        @NonNull public final String packageName;
        public final int userId;
        @NonNull public final String name;
        @NonNull public final String versionName;
        public final boolean enabled;
        public final int scopeCount;
        public final boolean legacy;

        public ModuleRow(
                @NonNull String packageName,
                int userId,
                @NonNull String name,
                @NonNull String versionName,
                boolean enabled,
                int scopeCount,
                boolean legacy
        ) {
            this.packageName = packageName;
            this.userId = userId;
            this.name = name;
            this.versionName = versionName;
            this.enabled = enabled;
            this.scopeCount = scopeCount;
            this.legacy = legacy;
        }
    }
}
