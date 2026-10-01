package com.homira.aod;

import android.app.*;
import android.content.*;
import android.database.*;
import android.graphics.Bitmap;
import android.media.*;
import android.media.session.*;
import android.os.*;
import android.provider.CalendarContract;
import java.util.*;

/**
 * Subscriptions exist only while a presentation surface is attached. No background polling service.
 */
public final class LiveState {
  private final Context context;
  private final Runnable changed;
  private boolean started;
  public int battery = -1;
  public boolean charging = false;
  public String title = "", artist = "", source = "", calendar = "", alarm = "";
  public Bitmap art;
  public long position = 0, duration = 0;
  public boolean playing = false;
  private final List<MediaController> controllers = new ArrayList<>();
  private final MediaController.Callback callback =
      new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata m) {
          refreshMedia();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState s) {
          refreshMedia();
        }

        @Override
        public void onSessionDestroyed() {
          refreshMedia();
        }
      };
  private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::bindSessions;
  private final BroadcastReceiver receiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
          if (Intent.ACTION_BATTERY_CHANGED.equals(i.getAction())) {
            int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            battery = scale > 0 ? i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / scale : -1;
            charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
          }
          if ("com.homira.aod.STATE".equals(i.getAction())) connectMedia();
          refreshAlarm();
          changed.run();
        }
      };
  private final ContentObserver calendarObserver =
      new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean self) {
          refreshCalendar();
          changed.run();
        }
      };

  public LiveState(Context c, Runnable update) {
    context = c;
    changed = update;
  }

  public void start() {
    if (started) return;
    started = true;
    IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    f.addAction(Intent.ACTION_TIME_CHANGED);
    f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
    f.addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
    f.addAction("com.homira.aod.STATE");
    androidx.core.content.ContextCompat.registerReceiver(
        context, receiver, f, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
    connectMedia();
    refreshAlarm();
    refreshCalendar();
    if (context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR)
        == android.content.pm.PackageManager.PERMISSION_GRANTED)
      context
          .getContentResolver()
          .registerContentObserver(CalendarContract.Events.CONTENT_URI, true, calendarObserver);
    changed.run();
  }

  private void connectMedia() {
    MediaSessionManager manager = context.getSystemService(MediaSessionManager.class);
    try {
      manager.removeOnActiveSessionsChangedListener(sessions);
      if (Notifications.granted(context)) {
        ComponentName n = new ComponentName(context, Notifications.class);
        manager.addOnActiveSessionsChangedListener(sessions, n);
        bindSessions(manager.getActiveSessions(n));
      } else bindSessions(Collections.emptyList());
    } catch (SecurityException e) {
      bindSessions(Collections.emptyList());
    }
  }

  private void bindSessions(List<MediaController> list) {
    for (MediaController c : controllers) c.unregisterCallback(callback);
    controllers.clear();
    if (list != null) controllers.addAll(list);
    for (MediaController c : controllers) c.registerCallback(callback);
    refreshMedia();
  }

  private void refreshMedia() {
    title = "";
    artist = "";
    source = "";
    art = null;
    playing = false;
    position = duration = 0;
    MediaController selected = null;
    for (MediaController c : controllers) {
      if (selected == null) selected = c;
      PlaybackState s = c.getPlaybackState();
      if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
        selected = c;
        break;
      }
    }
    if (selected != null) {
      MediaMetadata m = selected.getMetadata();
      PlaybackState s = selected.getPlaybackState();
      source = selected.getPackageName();
      if (m != null) {
        title = Objects.toString(m.getString(MediaMetadata.METADATA_KEY_TITLE), "");
        artist = Objects.toString(m.getString(MediaMetadata.METADATA_KEY_ARTIST), "");
        art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        duration = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
      }
      if (s != null) {
        position = s.getPosition();
        playing = s.getState() == PlaybackState.STATE_PLAYING;
      }
    }
    changed.run();
  }

  public void refreshAlarm() {
    AlarmManager.AlarmClockInfo info =
        context.getSystemService(AlarmManager.class).getNextAlarmClock();
    alarm =
        info == null
            ? "No next alarm"
            : java.time.Instant.ofEpochMilli(info.getTriggerTime())
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("EEE HH:mm"));
  }

  public void refreshCalendar() {
    calendar = "Calendar access is off";
    if (context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR)
        != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
    calendar = "No upcoming events";
    long now = System.currentTimeMillis();
    android.net.Uri.Builder uri = CalendarContract.Instances.CONTENT_URI.buildUpon();
    ContentUris.appendId(uri, now);
    ContentUris.appendId(uri, now + 24 * 60 * 60 * 1000);
    try (Cursor c =
        context
            .getContentResolver()
            .query(
                uri.build(),
                new String[] {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN},
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC")) {
      if (c != null && c.moveToFirst()) calendar = c.getString(0);
    } catch (SecurityException ignored) {
      calendar = "Calendar access is off";
    }
  }

  public void stop() {
    if (!started) return;
    started = false;
    context.unregisterReceiver(receiver);
    context.getContentResolver().unregisterContentObserver(calendarObserver);
    context
        .getSystemService(MediaSessionManager.class)
        .removeOnActiveSessionsChangedListener(sessions);
    for (MediaController c : controllers) c.unregisterCallback(callback);
    controllers.clear();
    art = null;
  }
}
