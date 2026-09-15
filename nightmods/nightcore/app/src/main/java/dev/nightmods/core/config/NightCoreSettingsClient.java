package dev.nightmods.core.config;

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

/**
 * Target-process reader for Night Core settings.
 *
 * Package visibility prevents real target apps from discovering Night Core's provider on Android 16,
 * so hook-side reads use an explicit ordered broadcast. The receiver verifies Android's shared sender
 * identity before returning a read-only settings snapshot.
 */
public final class NightCoreSettingsClient {
    private static final ComponentName SETTINGS_RECEIVER = new ComponentName(
            "dev.nightmods.core",
            "dev.nightmods.core.config.NightCoreSettingsReceiver"
    );
    private static final long SETTINGS_TIMEOUT_MS = 1500L;

    private NightCoreSettingsClient() {}

    public static BubbleStyleConfig readBubbleStyle(Context context) {
        if (context == null) throw new IllegalArgumentException("Target context is required");

        Intent request = new Intent(NightCoreSettingsReceiver.ACTION_GET_BUBBLE_STYLE)
                .setComponent(SETTINGS_RECEIVER)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        Bundle options = BroadcastOptions.makeBasic()
                .setDeferralPolicy(BroadcastOptions.DEFERRAL_POLICY_NONE)
                .setShareIdentityEnabled(true)
                .toBundle();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Bundle> result = new AtomicReference<>();
        AtomicReference<String> resultData = new AtomicReference<>();
        AtomicInteger resultCode = new AtomicInteger(Activity.RESULT_CANCELED);

        HandlerThread resultThread = new HandlerThread("NightCoreSettingsResult");
        resultThread.start();
        Handler resultHandler = new Handler(resultThread.getLooper());

        BroadcastReceiver finalReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ignored, Intent intent) {
                resultCode.set(getResultCode());
                resultData.set(getResultData());
                result.set(getResultExtras(false));
                latch.countDown();
            }
        };

        try {
            context.sendOrderedBroadcast(
                    request,
                    null,
                    options,
                    finalReceiver,
                    resultHandler,
                    Activity.RESULT_CANCELED,
                    null,
                    null
            );

            if (!latch.await(SETTINGS_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Night Core settings broadcast timed out");
            }
            if (resultCode.get() != Activity.RESULT_OK) {
                throw new SecurityException("Night Core settings rejected target: " + resultData.get());
            }

            Bundle snapshot = result.get();
            if (snapshot == null) {
                throw new IllegalStateException("Night Core settings broadcast returned no data");
            }
            if (snapshot.getInt(NightCoreSettingsReceiver.EXTRA_PROTOCOL_VERSION, -1)
                    != NightCoreSettingsReceiver.PROTOCOL_VERSION) {
                throw new IllegalStateException("Unsupported Night Core settings protocol");
            }
            return BubbleStyleConfig.fromBundle(snapshot);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Night Core settings read interrupted", error);
        } finally {
            resultThread.quitSafely();
        }
    }
}
