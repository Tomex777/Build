package com.whatsapp;

import android.app.Activity;
import android.app.BroadcastOptions;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** CI-only external UID probe for Night Core's Android 16 target-read contract. */
public final class MainActivity extends Activity {
    private static final String ACTION_GET_BUBBLE_STYLE =
            "dev.nightmods.core.action.GET_BUBBLE_STYLE";
    private static final ComponentName SETTINGS_RECEIVER = new ComponentName(
            "dev.nightmods.core",
            "dev.nightmods.core.config.NightCoreSettingsReceiver"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        var resultPrefs = getSharedPreferences("night_core_probe", MODE_PRIVATE);
        HandlerThread resultThread = new HandlerThread("NightCoreProbeResult");
        resultThread.start();

        try {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger code = new AtomicInteger(Activity.RESULT_CANCELED);
            AtomicReference<String> data = new AtomicReference<>();
            AtomicReference<Bundle> extras = new AtomicReference<>();

            BroadcastReceiver finalReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    code.set(getResultCode());
                    data.set(getResultData());
                    extras.set(getResultExtras(false));
                    latch.countDown();
                }
            };

            Intent request = new Intent(ACTION_GET_BUBBLE_STYLE)
                    .setComponent(SETTINGS_RECEIVER)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            Bundle options = BroadcastOptions.makeBasic()
                    .setDeferralPolicy(BroadcastOptions.DEFERRAL_POLICY_NONE)
                    .setShareIdentityEnabled(true)
                    .toBundle();

            sendOrderedBroadcast(
                    request,
                    null,
                    options,
                    finalReceiver,
                    new Handler(resultThread.getLooper()),
                    Activity.RESULT_CANCELED,
                    null,
                    null
            );

            if (!latch.await(1500L, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Night Core settings broadcast timed out");
            }
            if (code.get() != Activity.RESULT_OK) {
                throw new SecurityException("Night Core rejected probe: " + data.get());
            }

            Bundle result = extras.get();
            if (result == null) throw new IllegalStateException("Broadcast returned no settings");
            resultPrefs.edit()
                    .clear()
                    .putBoolean("success", true)
                    .putInt("protocol_version", result.getInt("night_core_protocol_version", -1))
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
        } finally {
            resultThread.quitSafely();
            finish();
        }
    }
}
