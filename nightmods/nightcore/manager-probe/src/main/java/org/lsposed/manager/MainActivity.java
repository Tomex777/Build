package org.lsposed.manager;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;

/** CI-only probe that reproduces Night Mods bootstrapping and writing Night Core settings. */
public final class MainActivity extends Activity {
    private static final Uri SETTINGS_URI = Uri.parse("content://dev.nightmods.core.settings");
    private static final String METHOD_GET = "get_bubble_style";
    private static final String METHOD_SET = "set_bubble_style";
    private static final String METHOD_GET_SYSTEM_UI = "get_system_ui";
    private static final String METHOD_SET_SYSTEM_UI = "set_system_ui";
    private static final String METHOD_TARGET_STATUS = "get_target_status";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        var prefs = getSharedPreferences("night_core_manager_probe", MODE_PRIVATE);
        boolean readOnly = getIntent().getBooleanExtra("read_only", false);
        try {
            if (!readOnly) {
                Bundle desired = new Bundle();
                desired.putBoolean("bubble_enabled", false);
                desired.putBoolean("target_whatsapp", true);
                desired.putBoolean("target_instagram", false);
                desired.putInt("bubble_radius", 31);
                desired.putInt("bubble_spacing", 9);
                getContentResolver().call(SETTINGS_URI, METHOD_SET, null, desired);

                Bundle systemUiDesired = new Bundle();
                systemUiDesired.putBoolean("systemui_enabled", true);
                systemUiDesired.putBoolean("statusbar_padding_enabled", true);
                systemUiDesired.putInt("statusbar_padding_dp", 11);
                getContentResolver().call(SETTINGS_URI, METHOD_SET_SYSTEM_UI, null, systemUiDesired);
            }

            Bundle result = getContentResolver().call(SETTINGS_URI, METHOD_GET, null, null);
            Bundle systemUiResult = getContentResolver().call(
                    SETTINGS_URI, METHOD_GET_SYSTEM_UI, null, null);
            if (result == null || systemUiResult == null) {
                throw new IllegalStateException("Night Core returned no settings");
            }

            Bundle systemUi = getContentResolver().call(
                    SETTINGS_URI, METHOD_TARGET_STATUS, "com.android.systemui", null);
            Bundle whatsapp = getContentResolver().call(
                    SETTINGS_URI, METHOD_TARGET_STATUS, "com.whatsapp", null);
            Bundle instagram = getContentResolver().call(
                    SETTINGS_URI, METHOD_TARGET_STATUS, "com.instagram.android", null);
            if (systemUi == null || whatsapp == null || instagram == null) {
                throw new IllegalStateException("Night Core returned no target metadata");
            }

            prefs.edit()
                    .clear()
                    .putBoolean("success", true)
                    .putString("mode", readOnly ? "read_only" : "write")
                    .putBoolean("bubble_enabled", result.getBoolean("bubble_enabled", true))
                    .putBoolean("target_whatsapp", result.getBoolean("target_whatsapp", false))
                    .putBoolean("target_instagram", result.getBoolean("target_instagram", true))
                    .putInt("bubble_radius", result.getInt("bubble_radius", -1))
                    .putInt("bubble_spacing", result.getInt("bubble_spacing", -1))
                    .putBoolean("systemui_enabled", systemUiResult.getBoolean("systemui_enabled", false))
                    .putBoolean("statusbar_padding_enabled", systemUiResult.getBoolean("statusbar_padding_enabled", false))
                    .putInt("statusbar_padding_dp", systemUiResult.getInt("statusbar_padding_dp", -1))
                    .putBoolean("systemui_installed", systemUi.getBoolean("installed", false))
                    .putString("systemui_compatibility", systemUi.getString("compatibility", "UNKNOWN"))
                    .putString("systemui_version_name", systemUi.getString("version_name", ""))
                    .putBoolean("whatsapp_installed", whatsapp.getBoolean("installed", false))
                    .putString("whatsapp_compatibility", whatsapp.getString("compatibility", "UNKNOWN"))
                    .putString("whatsapp_version_name", whatsapp.getString("version_name", ""))
                    .putBoolean("instagram_installed", instagram.getBoolean("installed", false))
                    .putString("instagram_compatibility", instagram.getString("compatibility", "UNKNOWN"))
                    .commit();
        } catch (Throwable error) {
            prefs.edit()
                    .clear()
                    .putBoolean("success", false)
                    .putString("mode", readOnly ? "read_only" : "write")
                    .putString("error", error.getClass().getName() + ": " + error.getMessage())
                    .commit();
        } finally {
            finish();
        }
    }
}
