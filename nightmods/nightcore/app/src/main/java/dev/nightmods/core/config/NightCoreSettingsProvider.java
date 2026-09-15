package dev.nightmods.core.config;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

import dev.nightmods.core.hook.adapters.InstagramAdapter;
import dev.nightmods.core.hook.adapters.TargetAdapter;
import dev.nightmods.core.hook.adapters.TargetAppInfo;
import dev.nightmods.core.hook.adapters.WhatsAppAdapter;

/** Narrow IPC bridge between Night Mods and Night Core settings/status. */
public final class NightCoreSettingsProvider extends ContentProvider {
    public static final String AUTHORITY = "dev.nightmods.core.settings";
    public static final String METHOD_GET_BUBBLE_STYLE = "get_bubble_style";
    public static final String METHOD_SET_BUBBLE_STYLE = "set_bubble_style";
    public static final String METHOD_GET_TARGET_STATUS = "get_target_status";

    public static final String KEY_STATUS_INSTALLED = "installed";
    public static final String KEY_STATUS_VERSION_NAME = "version_name";
    public static final String KEY_STATUS_VERSION_CODE = "version_code";
    public static final String KEY_STATUS_COMPATIBILITY = "compatibility";

    private static final String NIGHT_MODS_PACKAGE = "org.lsposed.manager";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String INSTAGRAM_PACKAGE = "com.instagram.android";

    @Override public boolean onCreate() { return true; }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        var context = getContext();
        if (context == null) throw new IllegalStateException("Night Core unavailable");

        if (METHOD_GET_BUBBLE_STYLE.equals(method)) {
            enforceReadCaller();
            var prefs = NightCorePreferences.open(context);
            Bundle result = new Bundle();
            result.putBoolean(BubbleStyleConfig.KEY_ENABLED, prefs.getBoolean(BubbleStyleConfig.KEY_ENABLED, true));
            result.putBoolean(BubbleStyleConfig.KEY_WHATSAPP, prefs.getBoolean(BubbleStyleConfig.KEY_WHATSAPP, true));
            result.putBoolean(BubbleStyleConfig.KEY_INSTAGRAM, prefs.getBoolean(BubbleStyleConfig.KEY_INSTAGRAM, true));
            result.putInt(BubbleStyleConfig.KEY_RADIUS, prefs.getInt(BubbleStyleConfig.KEY_RADIUS, 20));
            result.putInt(BubbleStyleConfig.KEY_SPACING, prefs.getInt(BubbleStyleConfig.KEY_SPACING, 6));
            return result;
        }

        if (METHOD_SET_BUBBLE_STYLE.equals(method)) {
            enforceWriteCaller();
            if (extras == null) throw new IllegalArgumentException("Missing settings bundle");
            var prefs = NightCorePreferences.open(context);
            prefs.edit()
                    .putBoolean(BubbleStyleConfig.KEY_ENABLED, extras.getBoolean(BubbleStyleConfig.KEY_ENABLED, true))
                    .putBoolean(BubbleStyleConfig.KEY_WHATSAPP, extras.getBoolean(BubbleStyleConfig.KEY_WHATSAPP, true))
                    .putBoolean(BubbleStyleConfig.KEY_INSTAGRAM, extras.getBoolean(BubbleStyleConfig.KEY_INSTAGRAM, true))
                    .putInt(BubbleStyleConfig.KEY_RADIUS, clamp(extras.getInt(BubbleStyleConfig.KEY_RADIUS, 20), 0, 48))
                    .putInt(BubbleStyleConfig.KEY_SPACING, clamp(extras.getInt(BubbleStyleConfig.KEY_SPACING, 6), 0, 24))
                    .apply();
            return Bundle.EMPTY;
        }

        if (METHOD_GET_TARGET_STATUS.equals(method)) {
            enforceManagerCaller();
            if (arg == null) throw new IllegalArgumentException("Missing target package");
            return targetStatus(arg);
        }

        throw new IllegalArgumentException("Unknown Night Core settings method: " + method);
    }

    private Bundle targetStatus(String packageName) {
        var context = getContext();
        if (context == null) throw new IllegalStateException("Night Core unavailable");

        TargetAdapter adapter;
        if (WHATSAPP_PACKAGE.equals(packageName)) {
            adapter = new WhatsAppAdapter();
        } else if (INSTAGRAM_PACKAGE.equals(packageName)) {
            adapter = new InstagramAdapter();
        } else {
            throw new IllegalArgumentException("Unsupported Night Core target: " + packageName);
        }

        Bundle result = new Bundle();
        try {
            TargetAppInfo info = TargetAppInfo.resolve(context, packageName);
            result.putBoolean(KEY_STATUS_INSTALLED, true);
            result.putString(KEY_STATUS_VERSION_NAME, info.versionName);
            result.putLong(KEY_STATUS_VERSION_CODE, info.versionCode);
            result.putString(KEY_STATUS_COMPATIBILITY, adapter.compatibility(info).name());
        } catch (PackageManager.NameNotFoundException ignored) {
            result.putBoolean(KEY_STATUS_INSTALLED, false);
            result.putString(KEY_STATUS_VERSION_NAME, "");
            result.putLong(KEY_STATUS_VERSION_CODE, -1L);
            result.putString(KEY_STATUS_COMPATIBILITY, "NOT_INSTALLED");
        }
        return result;
    }

    private void enforceReadCaller() {
        if (isOwnUid()) return;
        if (callerHasPackage(NIGHT_MODS_PACKAGE)
                || callerHasPackage(WHATSAPP_PACKAGE)
                || callerHasPackage(INSTAGRAM_PACKAGE)) return;
        throw new SecurityException("Caller is not allowed to read Night Core settings");
    }

    private void enforceManagerCaller() {
        if (isOwnUid() || callerHasPackage(NIGHT_MODS_PACKAGE)) return;
        throw new SecurityException("Caller is not allowed to read Night Core manager metadata");
    }

    private void enforceWriteCaller() {
        if (isOwnUid() || callerHasPackage(NIGHT_MODS_PACKAGE)) return;
        throw new SecurityException("Caller is not allowed to write Night Core settings");
    }

    private boolean isOwnUid() {
        return Binder.getCallingUid() == android.os.Process.myUid();
    }

    private boolean callerHasPackage(String expectedPackage) {
        var context = getContext();
        if (context == null) return false;
        PackageManager pm = context.getPackageManager();
        String[] packages = pm.getPackagesForUid(Binder.getCallingUid());
        if (packages == null) return false;
        for (String packageName : packages) {
            if (expectedPackage.equals(packageName)) return true;
        }
        return false;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException();
    }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
