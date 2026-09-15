package com.whatsapp;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;

/** CI-only probe proving target/foreign UIDs cannot read Night Core's manager provider. */
public final class MainActivity extends Activity {
    private static final Uri SETTINGS_URI = Uri.parse("content://dev.nightmods.core.settings");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        var resultPrefs = getSharedPreferences("night_core_probe", MODE_PRIVATE);
        try {
            Bundle result = getContentResolver().call(SETTINGS_URI, "get_bubble_style", null, null);
            resultPrefs.edit()
                    .clear()
                    .putBoolean("access_denied", false)
                    .putBoolean("settings_disclosed", result != null)
                    .putBoolean("bubble_enabled", result != null && result.getBoolean("bubble_enabled", true))
                    .commit();
        } catch (Throwable error) {
            resultPrefs.edit()
                    .clear()
                    .putBoolean("access_denied", true)
                    .putBoolean("settings_disclosed", false)
                    .putString("error", error.getClass().getName() + ": " + error.getMessage())
                    .commit();
        } finally {
            finish();
        }
    }
}
