package dev.nightmods.core;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;

/** Diagnostic/status surface. Night Mods is the only normal configuration UI. */
public final class MainActivity extends Activity {
    private static final String NIGHT_MODS_PACKAGE = "org.lsposed.manager";
    private static final Uri SETTINGS_URI = Uri.parse("content://dev.nightmods.core.settings");
    private static final String METHOD_TARGET_STATUS = "get_target_status";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView version = findViewById(R.id.engine_version);
        TextView managerStatus = findViewById(R.id.manager_status);
        TextView whatsappStatus = findViewById(R.id.whatsapp_status);
        TextView instagramStatus = findViewById(R.id.instagram_status);
        Button openNightMods = findViewById(R.id.open_night_mods);

        version.setText(getString(R.string.engine_version_value, BuildConfig.VERSION_NAME));

        boolean managerInstalled = isPackageInstalled(NIGHT_MODS_PACKAGE);
        managerStatus.setText(getString(managerInstalled
                ? R.string.night_mods_detected
                : R.string.night_mods_not_detected));
        openNightMods.setEnabled(managerInstalled);
        openNightMods.setOnClickListener(v -> openNightMods());

        renderTargetStatus(whatsappStatus, "com.whatsapp");
        renderTargetStatus(instagramStatus, "com.instagram.android");
    }

    private void openNightMods() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(NIGHT_MODS_PACKAGE);
        if (launch == null) return;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private void renderTargetStatus(TextView view, String packageName) {
        try {
            Bundle status = getContentResolver().call(
                    SETTINGS_URI, METHOD_TARGET_STATUS, packageName, null);
            if (status == null) {
                view.setText(R.string.target_status_unavailable);
                return;
            }

            boolean installed = status.getBoolean("installed", false);
            String version = status.getString("version_name", "");
            String compatibility = status.getString("compatibility", "UNKNOWN");
            if (!installed) {
                view.setText(R.string.target_not_installed);
                return;
            }

            String label;
            switch (compatibility) {
                case "SUPPORTED" -> label = getString(R.string.target_supported);
                case "ANALYSIS_REQUIRED" -> label = getString(R.string.target_analysis_required);
                case "UNSUPPORTED" -> label = getString(R.string.target_unsupported);
                default -> label = getString(R.string.target_status_unavailable);
            }
            view.setText(TextUtils.isEmpty(version)
                    ? label
                    : getString(R.string.target_status_with_version, version, label));
        } catch (RuntimeException error) {
            view.setText(R.string.target_status_unavailable);
        }
    }
}
