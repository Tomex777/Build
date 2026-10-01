package com.homira.aod;

import static org.junit.Assert.*;

import android.content.*;
import android.graphics.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import java.io.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ProductTest {
  private UiDevice device;
  private Context context;

  @Before
  public void setup() {
    device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.getSharedPreferences("settings", 0).edit().putBoolean("welcomed", true).commit();
  }

  private void capture(String name) throws Exception {
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    android.os.SystemClock.sleep(180);
    device.waitForIdle();
    File folder = new File(context.getExternalFilesDir(null), "screenshots");
    folder.mkdirs();
    assertTrue(device.takeScreenshot(new File(folder, name + ".png")));
    Bitmap b = BitmapFactory.decodeFile(new File(folder, name + ".png").getPath());
    int lit = 0;
    for (int y = 0; y < b.getHeight(); y += 8)
      for (int x = 0; x < b.getWidth(); x += 8) if ((b.getPixel(x, y) & 0xffffff) > 0x303030) lit++;
    b.recycle();
    assertTrue("Screenshot must contain visible content: " + name, lit > 10);
  }

  @Test
  public void studioEditPersistenceAndSharedPreview() throws Exception {
    AtomicReference<String> id = new AtomicReference<>();
    AtomicReference<String> saved = new AtomicReference<>();
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      capture("home");
      scenario.onActivity(
          a -> {
            Domain.Theme t = new Domain.Theme();
            t.name = "Acceptance design";
            t.elements.add(new Domain.Element());
            a.store.put(t);
            id.set(t.id);
            a.studio(t);
          });
      capture("studio-fresh");
      scenario.onActivity(
          a -> {
            a.canvas.selected = a.canvas.theme.elements.get(0).id;
            a.canvas.edits.selected(a.canvas.selected);
            a.canvas.invalidate();
          });
      capture("studio-selected-clock");
      final int[] rect = new int[4];
      scenario.onActivity(
          a -> {
            int[] loc = new int[2];
            a.canvas.getLocationOnScreen(loc);
            float s = Math.min(a.canvas.getWidth() / 360f, a.canvas.getHeight() / 720f);
            rect[0] = loc[0] + a.canvas.getWidth() / 2;
            rect[1] =
                loc[1] + Math.round(260 * s) + (a.canvas.getHeight() - Math.round(720 * s)) / 2;
            rect[2] = Math.round(25 * s);
            rect[3] = Math.round(30 * s);
          });
      device.swipe(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3], 12);
      scenario.onActivity(
          a -> {
            assertTrue(a.canvas.theme.elements.get(0).x > 48);
            assertTrue(a.history.canUndo());
          });
      final int[] handle = new int[2];
      scenario.onActivity(
          a -> {
            Domain.Element e = a.canvas.theme.elements.get(0);
            int[] loc = new int[2];
            a.canvas.getLocationOnScreen(loc);
            float s = Math.min(a.canvas.getWidth() / 360f, a.canvas.getHeight() / 720f);
            handle[0] =
                loc[0]
                    + (a.canvas.getWidth() - Math.round(360 * s)) / 2
                    + Math.round((e.x + e.w) * s);
            handle[1] =
                loc[1]
                    + (a.canvas.getHeight() - Math.round(720 * s)) / 2
                    + Math.round((e.y + e.h) * s);
          });
      device.swipe(handle[0], handle[1], handle[0] - 30, handle[1] + 35, 12);
      scenario.onActivity(
          a -> {
            assertTrue(a.canvas.theme.elements.get(0).h > 90);
            a.inspector();
          });
      capture("element-inspector");
      device.pressBack();
      scenario.onActivity(
          a -> {
            Domain.Theme before = a.canvas.theme.copy();
            a.history.record(before);
            a.canvas.theme.elements.get(0).color = 0xffdf9bac;
            a.store.put(a.canvas.theme);
            a.canvas.invalidate();
            Domain.Theme restored = a.history.undo(a.canvas.theme);
            assertNotEquals(0xffdf9bac, restored.elements.get(0).color);
            a.canvas.theme = a.history.redo(restored);
            assertEquals(0xffdf9bac, a.canvas.theme.elements.get(0).color);
            a.store.put(a.canvas.theme);
            saved.set(Domain.encode(a.canvas.theme));
          });
      scenario.recreate();
      scenario.onActivity(
          a -> {
            assertEquals("Studio", a.screen);
            assertEquals(saved.get(), Domain.encode(a.canvas.theme));
          });
      capture("studio-restored");
      for (String family : Domain.CLOCKS) {
        scenario.onActivity(
            a -> {
              Domain.Element e = a.canvas.theme.elements.get(0);
              Domain.applyClockFamily(e, family);
              e.y = 150;
              Domain.bounds(e);
              a.canvas.invalidate();
              a.store.put(a.canvas.theme);
            });
        capture("clock-" + family.replace(' ', '-'));
      }
      scenario.onActivity(
          a -> {
            a.add("Clock");
            assertEquals(2, a.canvas.theme.elements.size());
          });
      scenario.onActivity(a -> a.settings());
      capture("settings-permission-off");
      scenario.onActivity(a -> a.schedules());
      capture("schedules");
    }
    Store reopened = new Store(context);
    assertEquals(2, reopened.find(id.get()).elements.size());
    Intent preview =
        new Intent(context, PreviewActivity.class)
            .putExtra("theme", id.get())
            .putExtra("mode", "Preview");
    try (ActivityScenario<PreviewActivity> s = ActivityScenario.launch(preview)) {
      s.onActivity(
          a -> {
            assertEquals(id.get(), a.surface.theme.id);
            assertEquals(Domain.encode(reopened.find(id.get())), Domain.encode(a.surface.theme));
          });
      capture("preview");
    }
  }

  @Test
  public void dreamDeclarationAndPrivacyDefaults() throws Exception {
    android.content.pm.ServiceInfo info =
        context
            .getPackageManager()
            .getServiceInfo(new ComponentName(context, AmbientService.class), 0);
    assertEquals("android.permission.BIND_DREAM_SERVICE", info.permission);
    assertTrue(info.exported);
    Store s = new Store(context);
    assertFalse(s.settings().getBoolean("notificationText", false));
    assertFalse(s.settings().getBoolean("calendar", false));
    assertFalse(s.settings().getBoolean("media", false));
    assertFalse(s.settings().getBoolean("personal", false));
  }

  @Test
  public void assetsAndPortableImport() throws Exception {
    Store s = new Store(context);
    Bitmap b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
    b.eraseColor(0xffa8e9d1);
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    b.compress(Bitmap.CompressFormat.PNG, 100, png);
    String asset = s.importImage(new ByteArrayInputStream(png.toByteArray()));
    Domain.Theme t = new Domain.Theme();
    t.name = "Portable";
    Domain.Element e = new Domain.Element();
    e.type = "Image";
    e.asset = asset;
    t.elements.add(e);
    String json = s.exportTheme(t);
    assertFalse(json.contains("content://"));
    Domain.Theme imported =
        s.importTheme(
            new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    assertTrue(s.asset(imported.elements.get(0).asset).exists());
  }

  @Test
  public void chargingAndElementSurfacesRender() throws Exception {
    Store store = new Store(context);
    Domain.Theme t = Domain.presets().get(5);
    store.put(t);
    try (ActivityScenario<PreviewActivity> scenario =
        ActivityScenario.launch(
            new Intent(context, PreviewActivity.class)
                .putExtra("theme", t.id)
                .putExtra("mode", "Charging"))) {
      capture("charging");
      scenario.onActivity(
          a -> {
            assertTrue(a.surface.ambient);
            assertTrue(
                (a.getWindow().getAttributes().flags
                        & android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    != 0);
          });
    }
    try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
      s.onActivity(
          a -> {
            a.studio(t);
            a.add("Notifications");
          });
      UiObject2 later = device.findObject(By.text("Later"));
      if (later != null) later.click();
      capture("notifications-permission-off");
      s.onActivity(a -> a.add("Media"));
      later = device.findObject(By.text("Later"));
      if (later != null) later.click();
      capture("media-permission-off");
    }
  }

  @Test
  public void realStyleControlsUndoAndBackgroundRestore() throws Exception {
    Store store = new Store(context);
    Domain.Theme theme = new Domain.Theme();
    theme.name = "UI controls";
    theme.elements.add(new Domain.Element());
    store.put(theme);
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(
          a -> {
            a.studio(theme);
            a.canvas.selected = a.canvas.theme.elements.get(0).id;
            a.canvas.edits.selected(a.canvas.selected);
            a.canvas.invalidate();
          });
      UiObject2 clock = device.wait(Until.findObject(By.text("Clock")), 5000);
      assertNotNull(clock);
      clock.click();
      UiScrollable scroll = new UiScrollable(new UiSelector().scrollable(true));
      assertTrue(scroll.scrollIntoView(new UiSelector().text("Foreground color")));
      device.findObject(By.text("Foreground color")).click();
      UiObject2 mint = device.wait(Until.findObject(By.text("Mint")), 5000);
      assertNotNull(mint);
      android.os.SystemClock.sleep(250);
      mint.click();
      assertTrue(device.wait(Until.gone(By.text("Mint")), 5000));
      capture("style-picked");
      scenario.onActivity(a -> assertEquals(0xffa8e9d1, a.canvas.selection().color));
      device.findObject(By.text("Done")).click();
      assertTrue(device.wait(Until.gone(By.text("Done")), 5000));
      UiObject2 undo = device.wait(Until.findObject(By.desc("Undo")), 5000);
      assertNotNull(undo);
      undo.click();
      device.waitForIdle();
      android.os.SystemClock.sleep(150);
      scenario.onActivity(a -> assertEquals(0xffedf3f0, a.canvas.theme.elements.get(0).color));
      device.findObject(By.desc("Redo")).click();
      device.waitForIdle();
      android.os.SystemClock.sleep(150);
      scenario.onActivity(a -> assertEquals(0xffa8e9d1, a.canvas.theme.elements.get(0).color));
      scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
      scenario.onActivity(a -> assertFalse(a.canvas.isRuntimeActive()));
      scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
      scenario.onActivity(a -> assertTrue(a.canvas.isRuntimeActive()));
      scenario.recreate();
      scenario.onActivity(
          a -> {
            assertEquals(0xffa8e9d1, a.canvas.theme.elements.get(0).color);
            assertEquals(theme.id, a.canvas.theme.id);
            assertTrue(a.history.canUndo());
          });
      capture("style-controls-restored");
      scenario.onActivity(
          a -> {
            android.view.accessibility.AccessibilityNodeProvider provider =
                a.canvas.getAccessibilityNodeProvider();
            assertNotNull(provider);
            assertTrue(
                provider.performAction(
                    1, android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, null));
            assertEquals(a.canvas.theme.elements.get(0).id, a.canvas.selected);
          });
    }
  }
}
