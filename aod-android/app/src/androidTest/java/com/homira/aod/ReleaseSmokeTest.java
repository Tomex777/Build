package com.homira.aod;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import java.io.File;
import org.junit.*;
import org.junit.runner.RunWith;

/** Uses only external UI automation so minified classes are not held open for tests. */
@RunWith(AndroidJUnit4.class)
public class ReleaseSmokeTest {
  @Test
  public void releaseOpensSavedDesignAndPreview() throws Exception {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    device.executeShellCommand("am start -W -n com.homira.aod/.MainActivity");
    UiObject2 explore = device.wait(Until.findObject(By.text("Explore")), 1500);
    if (explore != null) explore.click();
    assertTrue(device.wait(Until.hasObject(By.text("AOD")), 5000));
    UiObject2 edit = device.wait(Until.findObject(By.text("Edit")), 5000);
    assertNotNull(edit);
    edit.click();
    assertNotNull(device.wait(Until.findObject(By.desc("AOD design canvas")), 5000));
    device.findObject(By.desc("Preview")).click();
    assertNotNull(device.wait(Until.findObject(By.text("Close")), 5000));
    device.waitForIdle();
    File folder = new File(context.getExternalFilesDir(null), "screenshots");
    folder.mkdirs();
    File file = new File(folder, "release-preview.png");
    assertTrue(device.takeScreenshot(file));
    Bitmap bitmap = BitmapFactory.decodeFile(file.getPath());
    int content = 0;
    for (int y = bitmap.getHeight() / 5; y < bitmap.getHeight() * 4 / 5; y += 4)
      for (int x = bitmap.getWidth() / 5; x < bitmap.getWidth() * 4 / 5; x += 4) {
        int px = bitmap.getPixel(x, y);
        if (android.graphics.Color.red(px) > 100
            && android.graphics.Color.green(px) > 100
            && android.graphics.Color.blue(px) > 100) content++;
      }
    bitmap.recycle();
    assertTrue(
        "Release Preview must contain visible AOD content, not only system bars", content > 20);
    device.findObject(By.text("Close")).click();
    device.pressBack();
  }
}
