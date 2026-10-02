package com.homira.aod;
import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
@RunWith(AndroidJUnit4.class)
public class StudioDesignTest {
  private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  private final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
  private void dismissTip() throws Exception {
    UiObject2 tip=device.wait(Until.findObject(By.res(java.util.regex.Pattern.compile(".*:id/immersive_cling_title"))),1500);
    if(tip!=null) {
      UiObject2 ok=device.findObject(By.res(java.util.regex.Pattern.compile(".*:id/ok")));
      if(ok!=null) ok.click();
    }
    device.waitForIdle();
  }
  private void capture(String name) throws Exception {
    dismissTip(); device.waitForIdle(); File folder=new File(context.getExternalFilesDir(null),"screenshots"); folder.mkdirs();
    assertTrue(device.takeScreenshot(new File(folder,name+".png")));
  }
  @Test public void wallpaperRendersOnBlackAndRestoresAfterRecreateAndExport() throws Exception {
    context.getSharedPreferences("settings",0).edit().putBoolean("welcomed",true).commit();
    Bitmap wallpaper=Bitmap.createBitmap(360,720,Bitmap.Config.ARGB_8888); Canvas drawing=new Canvas(wallpaper);
    drawing.drawColor(0xff293654); Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(0xfff4d59d);
    drawing.drawCircle(180,280,90,paint); paint.setColor(0xff5379a6);
    Path mountain=new Path(); mountain.moveTo(0,680); mountain.lineTo(110,340); mountain.lineTo(230,580);
    mountain.lineTo(300,420); mountain.lineTo(360,680); mountain.close(); drawing.drawPath(mountain,paint);
    ByteArrayOutputStream png=new ByteArrayOutputStream(); wallpaper.compress(Bitmap.CompressFormat.PNG,100,png); wallpaper.recycle();
    Store store=new Store(context); String asset=store.importImage(new ByteArrayInputStream(png.toByteArray()));
    Domain.Theme theme=new Domain.Theme(); theme.name="Wallpaper studio acceptance";
    Domain.Element clock=new Domain.Element(); clock.y=80; Domain.applyClockFamily(clock,"Thin"); theme.elements.add(clock);
    Domain.installWallpaper(theme,asset); store.put(theme);
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(a -> a.studio(theme));
      AtomicInteger lit=new AtomicInteger(); long deadline=android.os.SystemClock.uptimeMillis()+10000;
      do {
        scenario.onActivity(a -> {
          if(a.canvas.getWidth()==0 || a.canvas.getHeight()==0) return;
          Bitmap frame=Bitmap.createBitmap(a.canvas.getWidth(),a.canvas.getHeight(),Bitmap.Config.ARGB_8888);
          a.canvas.draw(new Canvas(frame)); int count=0;
          for(int y=frame.getHeight()/2;y<frame.getHeight()*9/10;y+=2)
            for(int x=frame.getWidth()/4;x<frame.getWidth()*3/4;x+=2)
              if(Color.red(frame.getPixel(x,y))>100) count++;
          lit.set(count); frame.recycle();
        });
        if(lit.get()>30) break; android.os.SystemClock.sleep(100);
      } while(android.os.SystemClock.uptimeMillis()<deadline);
      assertTrue("Real renderer must contain generated wallpaper edges",lit.get()>30);
      capture("studio-wallpaper-outline"); device.findObject(By.desc("Wallpaper")).click();
      assertNotNull(device.wait(Until.findObject(By.desc("Outline detail")),5000)); capture("studio-wallpaper-controls");
      device.findObject(By.text("Compare · AOD outline")).click();
      UiObject2 choice=device.wait(Until.findObject(By.text("Wallpaper").clazz("android.widget.TextView").clickable(true)),2000);
      if(choice==null) {
        for(UiObject2 node:device.findObjects(By.text("Wallpaper")))
          if(node.getResourceName()!=null && node.getResourceName().endsWith("text1")) { choice=node; break; }
      }
      assertNotNull(choice); choice.click();
      assertTrue(device.wait(Until.hasObject(By.text("Compare · Wallpaper")),5000));
      scenario.onActivity(a -> assertTrue(a.canvas.wallpaperPreview)); capture("studio-wallpaper-comparison");
      device.findObject(By.text("Done")).click();
      assertTrue(device.wait(Until.gone(By.text("Done")),5000));
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity(a -> assertFalse(a.canvas.wallpaperPreview));
      scenario.recreate(); scenario.onActivity(a -> {
        assertEquals(asset,a.canvas.theme.elements.get(0).asset); assertEquals("Outline",a.canvas.theme.elements.get(0).treatment);
      });
      device.waitForIdle(); device.wait(Until.findObject(By.desc("Preview")),5000).click(); dismissTip();
      assertNotNull(device.wait(Until.findObject(By.desc("Close display")),5000));
      android.os.SystemClock.sleep(700); capture("wallpaper-outline-display"); device.findObject(By.desc("Close display")).click();
      device.wait(Until.findObject(By.text("Use design")),5000).click();
      assertNotNull(device.wait(Until.findObject(By.text("Use display")),5000));
      scenario.onActivity(a -> { assertEquals(theme.id,a.store.active); assertTrue(a.store.override); });
      device.pressBack();
    }
    Store fresh=new Store(context);
    Domain.Theme exported=fresh.prepareThemeImport(new ByteArrayInputStream(fresh.exportTheme(fresh.find(theme.id)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    assertEquals(asset,exported.elements.get(0).asset); assertTrue(exported.elements.get(0).locked);
  }
  @Test public void clockGalleryAndLivePartsSupportUndoAndRestore() throws Exception {
    context.getSharedPreferences("settings",0).edit().putBoolean("welcomed",true).commit();
    Domain.Theme theme=new Domain.Theme(); theme.name="Clock studio acceptance"; theme.elements.add(new Domain.Element()); new Store(context).put(theme);
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(a -> a.studio(theme)); device.findObject(By.desc("Clock")).click();
      assertNotNull(device.wait(Until.findObject(By.text("Clock studio")),5000)); capture("studio-clock-gallery");
      UiScrollable scroll=new UiScrollable(new UiSelector().scrollable(true));
      assertTrue(scroll.scrollIntoView(new UiSelector().description("Clock style Thin")));
      device.findObject(By.desc("Clock style Thin")).click();
      assertNotNull(device.wait(Until.findObject(By.text("Browse clock styles")),5000));
      scenario.onActivity(a -> assertEquals("Thin",a.canvas.selection().family)); capture("studio-clock-customization");
      device.findObject(By.text("Done")).click();
      assertTrue(device.wait(Until.gone(By.text("Done")),5000));
      device.wait(Until.findObject(By.desc("Undo")),5000).click();
      scenario.onActivity(a -> assertEquals("Digital",a.canvas.theme.elements.get(0).family));
      device.findObject(By.desc("Redo")).click();
      scenario.recreate(); scenario.onActivity(a -> assertEquals("Thin",a.canvas.theme.elements.get(0).family));
      device.waitForIdle(); device.wait(Until.findObject(By.desc("Clock")),5000).click();
      assertNotNull(device.wait(Until.findObject(By.text("Clock studio")),5000));
      assertTrue(scroll.scrollIntoView(new UiSelector().text("Start custom clock")));
      device.findObject(By.text("Start custom clock")).click();
      assertNotNull(device.wait(Until.findObject(By.text("Browse clock styles")),5000));
      scenario.onActivity(a -> { assertEquals(2,a.canvas.theme.elements.size()); assertEquals("Hours",a.canvas.selection().family); });
      device.findObject(By.text("Done")).click(); assertTrue(device.wait(Until.gone(By.text("Done")),5000));
      scenario.onActivity(a -> a.addClockPart("Seconds"));
      scenario.recreate(); scenario.onActivity(a -> {
        assertEquals("Hours",a.canvas.theme.elements.get(0).family);
        assertEquals("Minutes",a.canvas.theme.elements.get(1).family);
        assertEquals("Seconds",a.canvas.theme.elements.get(2).family);
        assertTrue(a.canvas.theme.elements.get(2).seconds);
      }); capture("studio-custom-clock-parts");
      device.findObject(By.desc("Clock")).click();
      assertNotNull(device.wait(Until.findObject(By.text("Clock studio")),5000));
      assertTrue(scroll.scrollIntoView(new UiSelector().text("Save clock preset")));
      device.findObject(By.text("Save clock preset")).click();
      UiObject2 name=device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000);
      assertNotNull(name); name.setText("Acceptance clock"); device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("(?i)save"))),5000).click();
      assertTrue(device.wait(Until.gone(By.clazz("android.widget.EditText")),5000));
      scenario.onActivity(a -> {
        Domain.Theme preset=null;
        for(Domain.Theme t:a.store.themes) if(t.clockPreset && t.name.equals("Acceptance clock")) preset=t;
        assertNotNull(preset); preset.name="Renamed acceptance clock"; a.store.put(preset);
      });
      assertTrue(scroll.scrollIntoView(new UiSelector().text("Add saved clock")));
      device.findObject(By.text("Add saved clock")).click();
      UiObject2 saved=device.wait(Until.findObject(By.text("Renamed acceptance clock")),5000);
      assertNotNull(saved); saved.click();
      assertTrue(device.wait(Until.gone(By.text("Saved clocks")),5000));
      assertTrue(device.wait(Until.gone(By.text("Done")),5000));
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity(a -> assertEquals(6,a.canvas.theme.elements.size()));
      device.wait(Until.findObject(By.desc("Undo")),5000).click();
      scenario.onActivity(a -> assertEquals(3,a.canvas.theme.elements.size()));
    }
  }
}
