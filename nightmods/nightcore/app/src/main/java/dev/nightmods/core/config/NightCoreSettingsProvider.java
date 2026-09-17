package dev.nightmods.core.config;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

import dev.nightmods.core.hook.adapters.InstagramAdapter;
import dev.nightmods.core.hook.adapters.SystemUiAdapter;
import dev.nightmods.core.hook.adapters.TargetAdapter;
import dev.nightmods.core.hook.adapters.TargetAppInfo;
import dev.nightmods.core.hook.adapters.WhatsAppAdapter;

/** Narrow manager-to-Night-Core settings/status bridge. Target apps never read this provider. */
public final class NightCoreSettingsProvider extends ContentProvider {
    public static final String AUTHORITY = "dev.nightmods.core.settings";
    public static final String METHOD_GET_BUBBLE_STYLE = "get_bubble_style";
    public static final String METHOD_SET_BUBBLE_STYLE = "set_bubble_style";
    public static final String METHOD_GET_SYSTEM_UI = "get_system_ui";
    public static final String METHOD_SET_SYSTEM_UI = "set_system_ui";
    public static final String METHOD_GET_TARGET_STATUS = "get_target_status";

    public static final String KEY_STATUS_INSTALLED = "installed";
    public static final String KEY_STATUS_VERSION_NAME = "version_name";
    public static final String KEY_STATUS_VERSION_CODE = "version_code";
    public static final String KEY_STATUS_COMPATIBILITY = "compatibility";

    private static final String NIGHT_MODS_PACKAGE = "org.lsposed.manager";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String INSTAGRAM_PACKAGE = "com.instagram.android";

    @Override
    public boolean onCreate() {
        var context = getContext();
        if (context != null) NightCoreServiceStore.initialize(context);
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        var context = getContext();
        if (context == null) throw new IllegalStateException("Night Core unavailable");

        if (METHOD_GET_BUBBLE_STYLE.equals(method)) {
            enforceManagerCaller();
            return NightCoreServiceStore.read(context).toBundle();
        }

        if (METHOD_SET_BUBBLE_STYLE.equals(method)) {
            enforceManagerCaller();
            if (extras == null) throw new IllegalArgumentException("Missing settings bundle");
            if (!NightCoreServiceStore.write(context, BubbleStyleConfig.fromBundle(extras))) {
                throw new IllegalStateException("Night Core could not persist Bubble Styler settings");
            }
            return Bundle.EMPTY;
        }

        if (METHOD_GET_SYSTEM_UI.equals(method)) {
            enforceManagerCaller();
            return NightCoreServiceStore.readSystemUi(context).toBundle();
        }

        if (METHOD_SET_SYSTEM_UI.equals(method)) {
            enforceManagerCaller();
            if (extras == null) throw new IllegalArgumentException("Missing SystemUI settings bundle");
            if (!NightCoreServiceStore.writeSystemUi(context, SystemUiConfig.fromBundle(extras))) {
                throw new IllegalStateException("Night Core could not persist SystemUI settings");
            }
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
        if (SYSTEM_UI_PACKAGE.equals(packageName)) {
            adapter = new SystemUiAdapter();
        } else if (WHATSAPP_PACKAGE.equals(packageName)) {
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

    private void enforceManagerCaller() {
        if (isOwnUid() || callerHasPackage(NIGHT_MODS_PACKAGE)) return;
        throw new SecurityException("Caller is not allowed to access Night Core manager data");
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

    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
