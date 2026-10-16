package com.homira.aod;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared visual system for the editor. Ambient/charging rendering stays pure black. */
public final class Ui {
  private Ui() {}
  public static final int BG = 0xff0b1015;
  public static final int PANEL = 0xff182128;
  public static final int CARD = 0xff1d2931;
  public static final int BORDER = 0xff304047;
  public static final int TEXT = 0xfff0f7f3;
  public static final int MUTED = 0xffa2b5b5;
  public static final int ACCENT = 0xffa4ebd3;

  public static int dp(Context c, float n) {
    return Math.round(n * c.getResources().getDisplayMetrics().density);
  }

  public static TextView text(Context c, String value, int size, int color) {
    TextView t = new TextView(c);
    t.setText(value);
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
    t.setTextColor(color);
    t.setGravity(Gravity.CENTER_VERTICAL);
    t.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
    return t;
  }

  public static TextView headline(Context c, String value) {
    TextView t = text(c, value, 29, TEXT);
    t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    t.setLetterSpacing(-.035f);
    t.setPadding(dp(c, 16), 0, dp(c, 16), dp(c, 4));
    return t;
  }

  public static TextView eyebrow(Context c, String value) {
    TextView t = text(c, value, 11, ACCENT);
    t.setLetterSpacing(.16f);
    t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    t.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 6));
    return t;
  }

  public static LinearLayout column(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  public static LinearLayout row(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.HORIZONTAL);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  public static GradientDrawable rounded(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(radius);
    return d;
  }

  public static GradientDrawable bordered(Context c, int color, int radius) {
    GradientDrawable d = rounded(color, dp(c, radius));
    d.setStroke(dp(c, 1), BORDER);
    return d;
  }

  public static View separator(Context c) {
    View line = new View(c);
    line.setBackgroundColor(BORDER);
    line.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(c, 1)));
    return line;
  }

  /** Custom-styled text input: no platform underline, with a clear focus treatment. */
  public static EditText field(Context c, String value, String label) {
    EditText edit = new EditText(c);
    edit.setText(value);
    edit.setSingleLine(true);
    edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    edit.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    edit.setTextColor(TEXT);
    edit.setHintTextColor(MUTED);
    edit.setSelectAllOnFocus(true);
    edit.setSelection(edit.length());
    edit.setContentDescription(label);
    edit.setPadding(dp(c, 14), 0, dp(c, 14), 0);
    edit.setMinHeight(dp(c, 52));
    GradientDrawable normal = rounded(CARD, dp(c, 13));
    normal.setStroke(dp(c, 1), BORDER);
    GradientDrawable focused = rounded(CARD, dp(c, 13));
    focused.setStroke(dp(c, 2), ACCENT);
    StateListDrawable states = new StateListDrawable();
    states.addState(new int[] {android.R.attr.state_focused}, focused);
    states.addState(new int[] {}, normal);
    edit.setBackground(states);
    return edit;
  }

  public static Button button(Context c, String label, Runnable action) {
    Button b = new Button(c);
    b.setAllCaps(false);
    b.setText(label);
    b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
    b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    b.setTextColor(TEXT);
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    b.setMinHeight(dp(c, 48));
    b.setPadding(dp(c, 12), 0, dp(c, 12), 0);
    b.setElevation(0);
    b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x338ddfbd),
        bordered(c, PANEL, 13), rounded(0xffffffff, dp(c, 13))));
    b.setOnClickListener(v -> action.run());
    return b;
  }

  public static Button primary(Context c, String label, Runnable action) {
    Button b = button(c, label, action);
    b.setTextColor(BG);
    b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3388a88e),
        rounded(ACCENT, dp(c, 13)), rounded(0xffffffff, dp(c, 13))));
    return b;
  }

  private static int drawable(String name) {
    switch (name) {
      case "Back": return R.drawable.ic_back;
      case "Add": return R.drawable.ic_add;
      case "Undo": return R.drawable.ic_undo;
      case "Redo": return R.drawable.ic_redo;
      case "Preview": return R.drawable.ic_preview;
      case "Layers": return R.drawable.ic_layers;
      case "Settings": return R.drawable.ic_settings;
      case "Wallpaper": return R.drawable.ic_wallpaper;
      case "Clock":
      case "Schedules": return R.drawable.ic_clock;
      default: return R.drawable.ic_more;
    }
  }

  private static ImageView glyph(Context c, String name) {
    ImageView v = new ImageView(c);
    v.setImageResource(drawable(name));
    v.setImageTintList(new ColorStateList(
        new int[][] {new int[] {android.R.attr.state_enabled}, new int[] {}},
        new int[] {ACCENT, MUTED}));
    v.setScaleType(ImageView.ScaleType.FIT_CENTER);
    v.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
    return v;
  }

  public static View icon(Context c, String name, Runnable action) {
    ImageView v = glyph(c, name);
    v.setContentDescription(name);
    v.setFocusable(true);
    v.setClickable(true);
    TypedValue selectable = new TypedValue();
    c.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, selectable, true);
    if (selectable.resourceId != 0) v.setBackgroundResource(selectable.resourceId);
    v.setOnClickListener(w -> action.run());
    v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 48), dp(c, 48)));
    return v;
  }

  public static View tool(Context c, String name, String label, Runnable action) {
    LinearLayout tool = column(c);
    tool.setGravity(Gravity.CENTER);
    ImageView icon = glyph(c, name);
    icon.setPadding(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));
    tool.addView(icon, new LinearLayout.LayoutParams(dp(c, 29), dp(c, 29)));
    TextView caption = text(c, label, 11, MUTED);
    caption.setPadding(0, dp(c, 7), 0, 0);
    caption.setGravity(Gravity.CENTER);
    tool.addView(caption);
    tool.setContentDescription(name);
    tool.setFocusable(true);
    tool.setClickable(true);
    tool.setBackground(rounded(PANEL, dp(c, 12)));
    tool.setOnClickListener(v -> action.run());
    return tool;
  }
}
