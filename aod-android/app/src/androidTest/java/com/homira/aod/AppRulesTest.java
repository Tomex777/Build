package com.homira.aod;

import static org.junit.Assert.*;

import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Protect the shared design rules from regressions. */
@RunWith(AndroidJUnit4.class)
public final class AppRulesTest {
  private int named(View view, String label) {
    int count = 0;
    if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) count++;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) count += named(group.getChildAt(i), label);
    }
    return count;
  }

  private int described(View view, String description) {
    int count = description.contentEquals(view.getContentDescription()) ? 1 : 0;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++)
        count += described(group.getChildAt(i), description);
    }
    return count;
  }

  @Test
  public void homeHasOneEditActionAndNoFillerCopy() {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(a -> {
        a.home();
        View content = a.findViewById(android.R.id.content);
        assertEquals("Do not duplicate edit actions", 1, named(content, "Edit"));
        assertEquals(1, named(content, "Use display"));
        assertEquals(0, named(content, "Make time yours."));
        assertEquals(0, named(content, "THE ART OF ALWAYS ON"));
        assertEquals(0, named(content, "Designed here. Saved on your device."));
      });
    }
  }

  @Test
  public void emptySchedulesHaveOnlyOneCreateAction() {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(a -> {
        a.store.rules.clear(); // In-memory only; no destructive write.
        a.schedules();
        View content = a.findViewById(android.R.id.content);
        assertEquals(1, named(content, "Create schedule"));
        assertEquals(0, described(content, "Add"));
      });
    }
  }

  @Test
  public void allEditorInputUsesDesignedFieldWithFocusBorder() {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(a -> {
        EditText field = Ui.field(a, "Theme", "Design name");
        assertTrue(field.getBackground() instanceof StateListDrawable);
        assertTrue(field.getMinHeight() >= Ui.dp(a, 52));
        assertEquals("Theme", field.getText().toString());
        field.setText("Updated");
        assertEquals("Updated", field.getText().toString());
      });
    }
  }
}
