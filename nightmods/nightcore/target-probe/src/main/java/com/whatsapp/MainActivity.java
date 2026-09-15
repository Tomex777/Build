package com.whatsapp;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;

/** CI-only external UID probe for Night Core's Android 16 settings provider contract. */
public final class MainActivity extends Activity {
    private static final Uri SETTINGS_URI = Uri.parse("content://dev.nightmods.core.settings");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        var resultPrefs = getSharedPreferences("night_core_probe", MODE_PRIVATE);
        try {
            Bundle result = getContentResolver().call(
                    SETTINGS_URI,
                    "get_bubble_style",
                    null,
                    null
            );
            if (result == null) throw new IllegalStateException("Provider returned no settings");
            resultPrefs.edit()
                    .clear()
                    .putBoolean("success", true)
                    .putBoolean("bubble_enabled", result.getBoolean("bubble_enabled", true))
                    .putBoolean("target_whatsapp", result.getBoolean("target_whatsapp", true))
                    .putBoolean("target_instagram", result.getBoolean("target_instagram", true))
                    .putInt("bubble_radius", result.getInt("bubble_radius", -1))
                    .putInt("bubble_spacing", result.getInt("bubble_spacing", -1))
                    .commit();
        } catch (Throwable error) {
            resultPrefs.edit()
                    .clear()
                    .putBoolean("success", false)
                    .putString("error", error.getClass().getName() + ": " + error.getMessage())
                    .commit();
        }
        finish();
    }
}
