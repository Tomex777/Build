package com.homira.aod;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** The only theme renderer: Studio, cards, Preview and Dream all use this View. */
public final class Surface extends View {
  public Domain.Theme theme;
  public boolean editing = false,
      shift = true,
      safeRegion = false,
      ambient = false,
      followSchedules = false,
      allowBackground = false,
      passive = false;
  public String selected = "";

  public interface EditListener {
    void selected(String id);

    void changed(Domain.Theme before);
  }

  public EditListener edits;
  public final LiveState live;
  private final Store store;
  private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Handler handler = new Handler(Looper.getMainLooper());
  private float scale, ox, oy, dx, dy;
  private Domain.Theme before;
  private boolean resizing;
  private int accessibilityFocus = -1;
  private boolean runtimeActive = false;
  private boolean lastCharging = false;
  private float chargingPulse = 0;
  private android.animation.ValueAnimator chargeAnimator;
  private long refreshedMinute = -1;
  private final android.util.LruCache<String, Bitmap> images =
      new android.util.LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
          return bitmap.getAllocationByteCount();
        }
      };
  private final Runnable tick =
      new Runnable() {
        public void run() {
          long minute = System.currentTimeMillis() / 60000;
          if (minute != refreshedMinute) {
            refreshedMinute = minute;
            if (!editing && followSchedules) {
              Store latest = new Store(getContext());
              theme = latest.current(live.charging);
            }
            if (theme.elements.stream().anyMatch(e -> e.visible && e.type.equals("Calendar")))
              live.refreshCalendar();
          }
          invalidate();
          schedule();
        }
      };

  public Surface(Context c, Domain.Theme t) {
    super(c);
    store = new Store(c);
    theme = t;
    live =
        new LiveState(
            c,
            () -> {
              if (followSchedules) {
                theme = new Store(c).current(liveCharging());
              }
              if (liveCharging() != lastCharging) {
                lastCharging = liveCharging();
                if (lastCharging && ambient) {
                  if (chargeAnimator != null) chargeAnimator.cancel();
                  chargeAnimator = android.animation.ValueAnimator.ofFloat(0, 1, 0);
                  chargeAnimator.setDuration(800);
                  chargeAnimator.addUpdateListener(
                      a -> {
                        chargingPulse = (float) a.getAnimatedValue();
                        invalidate();
                      });
                  chargeAnimator.start();
                }
              }
              invalidate();
            });
    setContentDescription("AOD design canvas");
    setFocusable(true);
  }

  private boolean liveCharging() {
    return live != null && live.charging;
  }

  private void schedule() {
    handler.removeCallbacks(tick);
    boolean seconds =
        theme.elements.stream().anyMatch(e -> e.visible && e.seconds && e.type.equals("Clock"));
    long interval = seconds ? 1000 : 60000;
    handler.postDelayed(tick, interval - System.currentTimeMillis() % interval);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    resumeRuntime();
  }

  public boolean isRuntimeActive() {
    return runtimeActive;
  }

  public void refresh() {
    invalidate();
    if (runtimeActive) schedule();
  }

  public void resumeRuntime() {
    if (runtimeActive || !isAttachedToWindow()) return;
    runtimeActive = true;
    if (!passive) live.start();
    else {
      Intent battery =
          getContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
      if (battery != null) {
        int total = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        live.battery =
            total > 0 ? battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / total : -1;
        live.charging = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
      }
    }
    schedule();
  }

  public void pauseRuntime() {
    runtimeActive = false;
    handler.removeCallbacksAndMessages(null);
    if (chargeAnimator != null) chargeAnimator.cancel();
    live.stop();
  }

  @Override
  protected void onDetachedFromWindow() {
    pauseRuntime();
    images.evictAll();
    super.onDetachedFromWindow();
  }

  public Domain.Element selection() {
    for (Domain.Element e : theme.elements) if (e.id.equals(selected)) return e;
    return null;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    scale = Math.min(getWidth() / 360f, getHeight() / 720f);
    ox = (getWidth() - 360 * scale) / 2;
    oy = (getHeight() - 720 * scale) / 2;
    canvas.drawColor(editing ? 0xff111720 : Color.BLACK);
    canvas.save();
    canvas.translate(ox, oy);
    canvas.scale(scale, scale);
    canvas.clipRect(0, 0, 360, 720);
    canvas.drawColor(ambient && !allowBackground ? Color.BLACK : theme.background);
    if ((!ambient || allowBackground) && !theme.backgroundAsset.isEmpty()) {
      Bitmap bg = image(theme.backgroundAsset);
      if (bg != null) {
        p.setAlpha(70);
        canvas.drawBitmap(bg, null, new RectF(0, 0, 360, 720), p);
        p.setAlpha(255);
      }
    }
    if (safeRegion || editing) {
      p.setColor(0xff26323b);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1);
      canvas.drawRect(12, 12, 348, 708, p);
      p.setStyle(Paint.Style.FILL);
    }
    float[] offset =
        shift && !editing
            ? Domain.shift(theme, System.currentTimeMillis() / 60000)
            : new float[] {0, 0};
    canvas.translate(offset[0], offset[1]);
    ZonedDateTime now = ZonedDateTime.now();
    for (Domain.Element e : theme.elements) {
      if (!e.visible) continue;
      int elementLayer = canvas.save();
      canvas.translate(e.x, e.y);
      canvas.rotate(e.rotation, e.w / 2, e.h / 2);
      canvas.clipRect(0, 0, e.w, e.h);
      if (e.opacity < 1) canvas.saveLayerAlpha(0, 0, e.w, e.h, (int) (255 * e.opacity));
      p.reset();
      p.setAntiAlias(true);
      int color = theme.monochrome ? Color.WHITE : e.color;
      p.setColor(color);
      p.setAlpha(255);
      p.setTypeface(Typeface.create(e.font, e.weight >= 600 ? Typeface.BOLD : Typeface.NORMAL));
      p.setTextSize(e.size);
      p.setTextAlign(
          e.align == 0 ? Paint.Align.LEFT : e.align == 2 ? Paint.Align.RIGHT : Paint.Align.CENTER);
      String text = "";
      switch (e.type) {
        case "Clock":
          drawClock(canvas, e, now);
          break;
        case "Date":
          text = now.format(DateTimeFormatter.ofPattern("EEE, d MMM"));
          break;
        case "Battery":
          drawBattery(canvas, e);
          break;
        case "Text":
          text =
              ambient && e.privateContent && !store.settings().getBoolean("personal", false)
                  ? ""
                  : e.text;
          break;
        case "Alarm":
          text = live.alarm;
          break;
        case "Calendar":
          text = ambient && !store.settings().getBoolean("calendar", false) ? "" : live.calendar;
          break;
        case "Notifications":
          drawNotifications(canvas, e);
          break;
        case "Media":
          drawMedia(canvas, e);
          break;
        case "Image":
          Bitmap b = image(e.asset);
          if (b != null) {
            p.setAlpha(ambient ? 100 : 255);
            if (theme.monochrome) {
              ColorMatrix cm = new ColorMatrix();
              cm.setSaturation(0);
              p.setColorFilter(new ColorMatrixColorFilter(cm));
            }
            canvas.drawBitmap(b, null, new RectF(0, 0, e.w, e.h), p);
            p.setColorFilter(null);
          } else text = editing ? "Choose an image" : "";
          break;
        case "Shape":
          contentColor(theme.monochrome ? Color.WHITE : e.accent);
          if (e.gradient && !theme.monochrome)
            p.setShader(
                new LinearGradient(0, 0, e.w, e.h, e.color, e.accent, Shader.TileMode.CLAMP));
          if (e.family.equals("Circle")) canvas.drawOval(0, 0, e.w, e.h, p);
          else if (e.family.equals("Line")) canvas.drawRect(0, e.h / 2 - 1, e.w, e.h / 2 + 1, p);
          else canvas.drawRoundRect(0, 0, e.w, e.h, 8, 8, p);
          break;
      }
      if (!text.isEmpty()) drawText(canvas, e, text, e.h / 2 - (p.ascent() + p.descent()) / 2);
      canvas.restoreToCount(elementLayer);
      if (editing && e.id.equals(selected)) {
        p.setColor(0xffa8e9d1);
        p.setAlpha(255);
        p.setStrokeWidth(1.5f);
        p.setStyle(Paint.Style.STROKE);
        canvas.drawRect(e.x, e.y, e.x + e.w, e.y + e.h, p);
        p.setStyle(Paint.Style.FILL);
        canvas.drawCircle(e.x + e.w, e.y + e.h, 8, p);
        p.setColor(0xff36484a);
        canvas.drawLine(180, 12, 180, 708, p);
        canvas.drawLine(12, 360, 348, 360, p);
      }
    }
    canvas.restore();
  }

  private Bitmap image(String id) {
    if (id.isEmpty()) return null;
    Bitmap cached = images.get(id);
    if (cached != null) return cached;
    try {
      Bitmap b = BitmapFactory.decodeFile(store.asset(id).getPath());
      if (b != null) images.put(id, b);
      return b;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private void drawText(Canvas c, Domain.Element e, String text, float baseline) {
    float x = e.align == 0 ? 0 : e.align == 2 ? e.w : e.w / 2;
    String value = text;
    while (value.length() > 1 && p.measureText(value) > e.w)
      value = value.substring(0, value.length() - 1);
    if (value.length() < text.length() && value.length() > 1)
      value = value.substring(0, value.length() - 1) + "…";
    if (e.spacing == 0) {
      c.drawText(value, x, baseline, p);
      return;
    }
    float width = p.measureText(value) + Math.max(0, value.length() - 1) * e.spacing;
    float left = e.align == 0 ? 0 : e.align == 2 ? e.w - width : (e.w - width) / 2;
    p.setTextAlign(Paint.Align.LEFT);
    for (int i = 0; i < value.length(); i++) {
      String ch = value.substring(i, i + 1);
      c.drawText(ch, left, baseline, p);
      left += p.measureText(ch) + e.spacing;
    }
  }

  private void drawClock(Canvas c, Domain.Element e, ZonedDateTime now) {
    String value = Domain.clock(e, now);
    if (e.family.equals("Analog")) {
      float r = Math.min(e.w, e.h) / 2 - 6, cx = e.w / 2, cy = e.h / 2;
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1.5f);
      c.drawCircle(cx, cy, r, p);
      for (int i = 0; i < 12; i++) {
        double a = i * Math.PI / 6;
        c.drawLine(
            cx + (float) Math.sin(a) * (r - 8),
            cy - (float) Math.cos(a) * (r - 8),
            cx + (float) Math.sin(a) * (r - 3),
            cy - (float) Math.cos(a) * (r - 3),
            p);
      }
      hand(c, cx, cy, r * .5f, (now.getHour() % 12 + now.getMinute() / 60d) * Math.PI / 6, 4);
      hand(c, cx, cy, r * .78f, now.getMinute() * Math.PI / 30, 2);
      if (e.seconds) {
        contentColor(theme.monochrome ? Color.WHITE : e.accent);
        hand(c, cx, cy, r * .82f, now.getSecond() * Math.PI / 30, 1);
      }
      p.setStyle(Paint.Style.FILL);
      c.drawCircle(cx, cy, 3, p);
      return;
    }
    if (e.family.equals("Split")) {
      String[] parts = value.split(":");
      p.setTextSize(Math.min(e.size, e.w / 3));
      p.setTextAlign(Paint.Align.CENTER);
      c.drawText(parts[0], e.w * .25f, e.h / 2 - (p.ascent() + p.descent()) / 2, p);
      contentColor(theme.monochrome ? Color.WHITE : e.accent);
      c.drawText(parts[1].split(" ")[0], e.w * .75f, e.h / 2 - (p.ascent() + p.descent()) / 2, p);
      clockDetail(c, e, now);
      return;
    }
    if (e.family.equals("Vertical")) {
      String[] parts = value.split(":");
      drawText(c, e, parts[0], e.h / 2 - 8);
      contentColor(theme.monochrome ? Color.WHITE : e.accent);
      drawText(c, e, parts[1].split(" ")[0], e.h / 2 + e.size);
      clockDetail(c, e, now);
      return;
    }
    if (e.family.equals("Words")) {
      String[] words = {
        "twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven"
      };
      p.setTextSize(Math.min(e.size, 32));
      drawText(c, e, words[now.getHour() % 12], e.h * (e.seconds ? .32f : .42f));
      drawText(
          c,
          e,
          String.format(Locale.getDefault(), "%02d minutes", now.getMinute()),
          e.h * (e.seconds ? .66f : .8f));
      clockDetail(c, e, now);
      return;
    }
    drawText(c, e, value, e.h / 2 - (p.ascent() + p.descent()) / 2);
    if (e.family.equals("Date integrated")) {
      p.setTextSize(13);
      drawText(
          c, e, now.format(DateTimeFormatter.ofPattern("EEEE · d MMM")), e.dateTop ? 18 : e.h - 3);
    }
  }

  private void contentColor(int color) {
    p.setColor(color);
    p.setAlpha(255);
  }

  private void clockDetail(Canvas c, Domain.Element e, ZonedDateTime now) {
    if (e.seconds || !e.h24) {
      p.setTextSize(12);
      drawText(
          c,
          e,
          (e.seconds ? String.format(Locale.getDefault(), "%02d s", now.getSecond()) : "")
              + (e.h24 ? "" : " · " + now.format(DateTimeFormatter.ofPattern("a"))),
          e.h - 3);
    }
  }

  private void hand(Canvas c, float x, float y, float r, double a, float width) {
    p.setStrokeWidth(width);
    p.setStrokeCap(Paint.Cap.ROUND);
    c.drawLine(x, y, x + (float) Math.sin(a) * r, y - (float) Math.cos(a) * r, p);
  }

  private void drawBattery(Canvas c, Domain.Element e) {
    float cy = e.h / 2;
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(1.5f);
    contentColor(theme.monochrome ? Color.WHITE : e.color);
    c.drawRoundRect(4, cy - 7, 28, cy + 7, 3, 3, p);
    c.drawLine(31, cy - 3, 31, cy + 3, p);
    p.setStyle(Paint.Style.FILL);
    if (live.battery >= 0) c.drawRect(7, cy - 4, 7 + 18 * live.battery / 100f, cy + 4, p);
    if (chargingPulse > 0) {
      p.setAlpha((int) (90 * chargingPulse));
      p.setStyle(Paint.Style.STROKE);
      c.drawCircle(17, cy, 16 + chargingPulse * 3, p);
      p.setStyle(Paint.Style.FILL);
      p.setAlpha(255);
    }
    Domain.Element label = e.copy();
    label.w = e.w - 40;
    label.align = 0;
    c.save();
    c.translate(40, 0);
    p.setTextSize(Math.min(e.size, 22));
    p.setTextAlign(Paint.Align.LEFT);
    drawText(
        c,
        label,
        (live.charging ? "Charging · " : "") + (live.battery < 0 ? "—" : live.battery + "%"),
        cy - (p.ascent() + p.descent()) / 2);
    c.restore();
  }

  private void drawNotifications(Canvas c, Domain.Element e) {
    if (!Notifications.granted(getContext())) {
      if (editing) drawText(c, e, "Notification access is off", e.h * .65f);
      return;
    }
    List<Notifications.Item> list;
    synchronized (Notifications.items) {
      list = new ArrayList<>(Notifications.items.values());
    }
    list.removeIf(item -> store.settings().getBoolean("hide." + item.pkg, false));
    int count = 0;
    float x = 8;
    Set<String> seen = new HashSet<>();
    for (Notifications.Item item : list) {
      if (store.settings().getBoolean("hide." + item.pkg, false)) continue;
      count++;
      if (!seen.add(item.pkg) || x > e.w - 48) continue;
      if (item.icon != null) {
        Drawable d = item.icon.mutate();
        d.setTintList(
            theme.monochrome ? android.content.res.ColorStateList.valueOf(Color.WHITE) : null);
        d.setAlpha(255);
        d.setBounds((int) x, 4, (int) x + 22, 26);
        d.draw(c);
      }
      x += 32;
    }
    if (count > 0) {
      p.setTextSize(13);
      p.setTextAlign(Paint.Align.LEFT);
      c.drawText(Integer.toString(count), Math.min(x, e.w - 22), 23, p);
    }
    if (e.treatment.equals("App names") && !list.isEmpty()) {
      p.setTextSize(12);
      drawText(c, e, list.get(0).label, e.h - 4);
    }
    if (!e.privateContent
        && store.settings().getBoolean("notificationText", false)
        && !list.isEmpty()
        && !list.get(0).sensitive) {
      p.setTextSize(12);
      drawText(c, e, list.get(0).title, e.h - 4);
    }
  }

  private void drawMedia(Canvas c, Domain.Element e) {
    if (live.title.isEmpty()) {
      if (editing) drawText(c, e, "Nothing playing", e.h * .65f);
      return;
    }
    if (ambient && !store.settings().getBoolean("media", false)) {
      drawText(c, e, "Media playing", e.h * .65f);
      return;
    }
    float left = 0;
    if (e.treatment.equals("Icon + text")) {
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(2);
      c.drawCircle(18, 25, 12, p);
      p.setStyle(Paint.Style.FILL);
      if (live.playing) {
        c.drawRect(13, 19, 16, 31, p);
        c.drawRect(20, 19, 23, 31, p);
      } else {
        Path triangle = new Path();
        triangle.moveTo(14, 18);
        triangle.lineTo(24, 25);
        triangle.lineTo(14, 32);
        triangle.close();
        c.drawPath(triangle, p);
      }
      left = 46;
    }
    if (!e.treatment.equals("Text only")
        && !e.treatment.equals("Icon + text")
        && live.art != null) {
      p.setAlpha(e.treatment.equals("Dimmed art") ? 65 : 110);
      if (e.treatment.equals("Monochrome art") || theme.monochrome) {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0);
        p.setColorFilter(new ColorMatrixColorFilter(matrix));
      }
      c.drawBitmap(live.art, null, new RectF(0, 0, Math.min(e.h, 64), Math.min(e.h, 64)), p);
      p.setColorFilter(null);
      p.setAlpha(255);
      left = Math.min(e.h, 64) + 10;
    }
    c.save();
    c.translate(left, 0);
    Domain.Element label = e.copy();
    label.w = e.w - left;
    label.align = 0;
    p.setTextAlign(Paint.Align.LEFT);
    p.setTextSize(Math.min(e.size, 20));
    drawText(c, label, live.title, 24);
    p.setTextSize(12);
    drawText(c, label, live.artist + (live.playing ? "" : " · Paused"), 43);
    if (live.duration > 0) {
      contentColor(theme.monochrome ? Color.WHITE : e.accent);
      float fraction = Math.min(1, live.currentPosition() / (float) live.duration);
      c.drawRect(0, 58, label.w * fraction, 60, p);
    }
    c.restore();
  }

  @Override
  public boolean onTouchEvent(android.view.MotionEvent event) {
    if (!editing) return super.onTouchEvent(event);
    float x = (event.getX() - ox) / scale, y = (event.getY() - oy) / scale;
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        Domain.Element prior = selection();
        resizing =
            prior != null
                && !prior.locked
                && Math.hypot(x - prior.x - prior.w, y - prior.y - prior.h) < 24;
        Domain.Element hit = resizing ? prior : null;
        if (hit == null)
          for (int i = theme.elements.size() - 1; i >= 0; i--) {
            Domain.Element e = theme.elements.get(i);
            if (e.visible
                && x >= e.x - 8
                && x <= e.x + e.w + 8
                && y >= e.y - 8
                && y <= e.y + e.h + 8) {
              hit = e;
              break;
            }
          }
        selected = hit == null ? "" : hit.id;
        before = theme.copy();
        dx = x;
        dy = y;
        if (edits != null) edits.selected(selected);
        invalidate();
        return true;
      case MotionEvent.ACTION_MOVE:
        Domain.Element e = selection();
        if (e != null && !e.locked) {
          if (resizing) {
            e.w += x - dx;
            e.h += y - dy;
          } else {
            e.x += x - dx;
            e.y += y - dy;
          }
          Domain.bounds(e);
          dx = x;
          dy = y;
          invalidate();
        }
        return true;
      case MotionEvent.ACTION_UP:
        Domain.Element el = selection();
        if (el != null && !el.locked) {
          if (!resizing) {
            el.x = Domain.snap(el.x, el.w, 360);
            el.y = Domain.snap(el.y, el.h, 720);
          }
          Domain.bounds(el);
          if (edits != null && !Domain.encode(before).equals(Domain.encode(theme)))
            edits.changed(before);
        }
        performClick();
        invalidate();
        return true;
      case MotionEvent.ACTION_CANCEL:
        if (before != null) theme = before;
        invalidate();
        return true;
    }
    return true;
  }

  @Override
  public void onInitializeAccessibilityNodeInfo(
      android.view.accessibility.AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    if (editing) {
      for (int i = 0; i < theme.elements.size(); i++) info.addChild(this, i + 1);
    }
  }

  @Override
  public android.view.accessibility.AccessibilityNodeProvider getAccessibilityNodeProvider() {
    if (!editing) return super.getAccessibilityNodeProvider();
    return new android.view.accessibility.AccessibilityNodeProvider() {
      @Override
      public android.view.accessibility.AccessibilityNodeInfo createAccessibilityNodeInfo(int id) {
        if (id == HOST_VIEW_ID) {
          android.view.accessibility.AccessibilityNodeInfo node =
              android.view.accessibility.AccessibilityNodeInfo.obtain(Surface.this);
          onInitializeAccessibilityNodeInfo(node);
          return node;
        }
        if (id < 1 || id > theme.elements.size()) return null;
        Domain.Element e = theme.elements.get(id - 1);
        android.view.accessibility.AccessibilityNodeInfo node =
            android.view.accessibility.AccessibilityNodeInfo.obtain();
        node.setSource(Surface.this, id);
        node.setParent(Surface.this);
        node.setPackageName(getContext().getPackageName());
        node.setClassName("android.widget.Button");
        node.setContentDescription(
            e.type
                + ", layer "
                + id
                + (e.locked ? ", locked" : "")
                + (!e.visible ? ", hidden" : "")
                + ", double tap to select. Numeric editing is available below the canvas.");
        node.setEnabled(true);
        node.setFocusable(true);
        node.setClickable(true);
        node.setVisibleToUser(isShown());
        node.setAccessibilityFocused(accessibilityFocus == id);
        node.setBoundsInParent(
            new Rect(
                (int) (ox + e.x * scale),
                (int) (oy + e.y * scale),
                (int) (ox + (e.x + e.w) * scale),
                (int) (oy + (e.y + e.h) * scale)));
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        node.setBoundsInScreen(
            new Rect(
                loc[0] + (int) (ox + e.x * scale),
                loc[1] + (int) (oy + e.y * scale),
                loc[0] + (int) (ox + (e.x + e.w) * scale),
                loc[1] + (int) (oy + (e.y + e.h) * scale)));
        node.addAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
        node.addAction(
            accessibilityFocus == id
                ? android.view.accessibility.AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS
                : android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        return node;
      }

      @Override
      public boolean performAction(int id, int action, Bundle args) {
        if (id < 1 || id > theme.elements.size()) return false;
        if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) {
          selected = theme.elements.get(id - 1).id;
          if (edits != null) edits.selected(selected);
          invalidate();
          return true;
        }
        if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
            || action
                == android.view.accessibility.AccessibilityNodeInfo
                    .ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
          accessibilityFocus =
              action == android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
                  ? id
                  : -1;
          android.view.accessibility.AccessibilityEvent event =
              android.view.accessibility.AccessibilityEvent.obtain(
                  action
                          == android.view.accessibility.AccessibilityNodeInfo
                              .ACTION_ACCESSIBILITY_FOCUS
                      ? android.view.accessibility.AccessibilityEvent
                          .TYPE_VIEW_ACCESSIBILITY_FOCUSED
                      : android.view.accessibility.AccessibilityEvent
                          .TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
          event.setSource(Surface.this, id);
          event.setPackageName(getContext().getPackageName());
          getParent().requestSendAccessibilityEvent(Surface.this, event);
          return true;
        }
        return false;
      }
    };
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }
}
