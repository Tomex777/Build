package dev.nightmods.core.hook.adapters;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

/** Version identity resolved from the target app's own process/context. */
public final class TargetAppInfo {
    public final String packageName;
    public final String versionName;
    public final long versionCode;

    private TargetAppInfo(String packageName, String versionName, long versionCode) {
        this.packageName = packageName;
        this.versionName = versionName == null ? "unknown" : versionName;
        this.versionCode = versionCode;
    }

    public static TargetAppInfo resolve(Context context, String packageName)
            throws PackageManager.NameNotFoundException {
        if (context == null) throw new IllegalArgumentException("Target context is required");
        PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
        long code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? info.getLongVersionCode()
                : info.versionCode;
        return new TargetAppInfo(packageName, info.versionName, code);
    }

    public String describe() {
        return packageName + " versionName=" + versionName + " versionCode=" + versionCode;
    }
}
