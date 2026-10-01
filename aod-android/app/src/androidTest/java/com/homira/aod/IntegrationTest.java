package com.homira.aod;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import java.io.File;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class IntegrationTest {
  @Test
  public void realNotificationAndMediaCallbacks() throws Exception {
    Context target = InstrumentationRegistry.getInstrumentation().getTargetContext(), test = target;
    target.getSharedPreferences("settings", 0).edit().putBoolean("welcomed", true).commit();
    UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    if (Build.VERSION.SDK_INT >= 33)
      device.executeShellCommand(
          "pm grant " + test.getPackageName() + " android.permission.POST_NOTIFICATIONS");
    device.executeShellCommand(
        "cmd notification allow_listener com.homira.aod/com.homira.aod.Notifications");
    // API 26 exposes listener access through Secure settings instead of the newer shell command.
    if (!Notifications.granted(target))
      device.executeShellCommand(
          "settings put secure enabled_notification_listeners"
              + " com.homira.aod/com.homira.aod.Notifications");
    android.service.notification.NotificationListenerService.requestRebind(
        new ComponentName(target, Notifications.class));
    long deadline = SystemClock.elapsedRealtime() + 15000;
    while (!Notifications.granted(target) && SystemClock.elapsedRealtime() < deadline)
      SystemClock.sleep(100);
    assertTrue(
        "Notification permission must be granted for real integration proof",
        Notifications.granted(target));
    NotificationManager nm = test.getSystemService(NotificationManager.class);
    nm.createNotificationChannel(
        new NotificationChannel(
            "aod-proof", "AOD integration proof", NotificationManager.IMPORTANCE_LOW));
    MediaSession session = new MediaSession(target, "AOD acceptance session");
    Store store = new Store(target);
    Domain.Theme theme = new Domain.Theme();
    theme.name = "Runtime integration";
    Domain.Element clock = new Domain.Element();
    clock.y = 160;
    theme.elements.add(clock);
    Domain.Element notifications = new Domain.Element();
    notifications.type = "Notifications";
    notifications.size = 15;
    notifications.y = 360;
    notifications.h = 70;
    theme.elements.add(notifications);
    Domain.Element media = new Domain.Element();
    media.type = "Media";
    media.size = 18;
    media.y = 450;
    media.h = 90;
    theme.elements.add(media);
    store.put(theme);
    try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
      nm.notify(
          700,
          new Notification.Builder(test, "aod-proof")
              .setSmallIcon(android.R.drawable.ic_dialog_info)
              .setContentTitle("AOD public fixture")
              .setContentText("Public test notification")
              .setVisibility(Notification.VISIBILITY_PUBLIC)
              .build());
      nm.notify(
          701,
          new Notification.Builder(test, "aod-proof")
              .setSmallIcon(android.R.drawable.ic_dialog_info)
              .setContentTitle("Private fixture")
              .setContentText("PRIVATE CONTENT MUST NOT BE STORED")
              .setVisibility(Notification.VISIBILITY_PRIVATE)
              .build());
      deadline = SystemClock.elapsedRealtime() + 15000;
      boolean received = false;
      while (!received && SystemClock.elapsedRealtime() < deadline) {
        synchronized (Notifications.items) {
          received =
              Notifications.items.values().stream()
                  .anyMatch(i -> i.pkg.equals(test.getPackageName()) && !i.sensitive);
        }
        if (!received) SystemClock.sleep(100);
      }
      assertTrue("Real posted notification must reach listener", received);
      synchronized (Notifications.items) {
        assertFalse(
            Notifications.items.values().stream()
                .anyMatch(i -> i.title.contains("PRIVATE CONTENT")));
      }
      session.setMetadata(
          new MediaMetadata.Builder()
              .putString(MediaMetadata.METADATA_KEY_TITLE, "Test track from a real session")
              .putString(MediaMetadata.METADATA_KEY_ARTIST, "AOD acceptance")
              .putLong(MediaMetadata.METADATA_KEY_DURATION, 240000)
              .build());
      session.setPlaybackState(
          new PlaybackState.Builder()
              .setState(PlaybackState.STATE_PLAYING, 60000, 1)
              .setActions(PlaybackState.ACTION_PLAY_PAUSE)
              .build());
      session.setActive(true);
      activity.onActivity(a -> a.studio(theme));
      java.util.concurrent.atomic.AtomicBoolean mediaReady =
          new java.util.concurrent.atomic.AtomicBoolean(false);
      deadline = SystemClock.elapsedRealtime() + 15000;
      while (!mediaReady.get() && SystemClock.elapsedRealtime() < deadline) {
        activity.onActivity(
            a -> mediaReady.set(a.canvas.live.title.equals("Test track from a real session")));
        if (!mediaReady.get()) SystemClock.sleep(100);
      }
      assertTrue("Active media session metadata must render", mediaReady.get());
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      SystemClock.sleep(180);
      device.waitForIdle();
      File folder = new File(target.getExternalFilesDir(null), "screenshots");
      folder.mkdirs();
      assertTrue(device.takeScreenshot(new File(folder, "notification-media-live.png")));
      nm.cancel(700);
      nm.cancel(701);
      deadline = SystemClock.elapsedRealtime() + 15000;
      boolean removed = false;
      while (!removed && SystemClock.elapsedRealtime() < deadline) {
        synchronized (Notifications.items) {
          removed =
              Notifications.items.values().stream()
                  .noneMatch(i -> i.pkg.equals(test.getPackageName()));
        }
        if (!removed) SystemClock.sleep(100);
      }
      assertTrue("Removed notifications must clear", removed);
    } finally {
      session.release();
      nm.cancel(700);
      nm.cancel(701);
      device.executeShellCommand(
          "cmd notification disallow_listener com.homira.aod/com.homira.aod.Notifications");
      device.executeShellCommand("settings put secure enabled_notification_listeners null");
    }
  }
}
