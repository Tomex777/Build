package com.homira.aod;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

public final class Ui {
  public static final int BG = 0xff0b0d10,
      PANEL = 0xff191d22,
      TEXT = 0xffe9efed,
      MUTED = 0xff89979e,
      ACCENT = 0xffa8e9d1;

  public static int dp(Context c, float n) {
    return Math.round(n * c.getResources().getDisplayMetrics().density);
  }

  public static TextView text(Context c, String value, int size, int color) {
    TextView t = new TextView(c);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setGravity(Gravity.CENTER_VERTICAL);
    t.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
    return t;
  }

  public static LinearLayout column(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  public static LinearLayout row(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  public static GradientDrawable rounded(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(radius);
    return d;
  }

  public static Button button(Context c, String label, Runnable action) {
    Button b = new Button(c);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextSize(14);
    b.setTextColor(ACCENT);
    b.setMinHeight(dp(c, 48));
    b.setBackground(rounded(PANEL, dp(c, 12)));
    b.setOnClickListener(v -> action.run());
    return b;
  }

  public static Button primary(Context c, String label, Runnable action) {
    Button button=button(c,label,action);
    button.setTextColor(BG);
    button.setBackground(rounded(ACCENT,dp(c,12)));
    return button;
  }

  public static View icon(Context c, String name, Runnable action) {
    Icon i = new Icon(c, name);
    i.setContentDescription(name);
    i.setFocusable(true);
    i.setOnClickListener(v -> action.run());
    i.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 48), dp(c, 48)));
    return i;
  }

  public static View tool(Context c, String name, String label, Runnable action) {
    LinearLayout tool=column(c);
    tool.setGravity(Gravity.CENTER);
    tool.addView(new Icon(c,name),new LinearLayout.LayoutParams(dp(c,32),dp(c,32)));
    TextView caption=text(c,label,11,TEXT);
    caption.setPadding(0,dp(c,3),0,0); caption.setGravity(Gravity.CENTER);
    tool.addView(caption);
    tool.setContentDescription(name); tool.setFocusable(true);
    tool.setOnClickListener(v -> action.run());
    return tool;
  }

  static final class Icon extends View {
    final String name;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    Icon(Context c, String n) {
      super(c);
      name = n;
    }

    @Override
    public void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      canvas.save();
      canvas.translate(
          getWidth() / 2f - 12 * getResources().getDisplayMetrics().density,
          getHeight() / 2f - 12 * getResources().getDisplayMetrics().density);
      canvas.scale(
          getResources().getDisplayMetrics().density, getResources().getDisplayMetrics().density);
      p.setColor(ACCENT);
      p.setStrokeWidth(1.8f);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeCap(Paint.Cap.ROUND);
      Path path = new Path();
      switch (name) {
        case "Back":
          path.moveTo(15, 4);
          path.lineTo(7, 12);
          path.lineTo(15, 20);
          break;
        case "Add":
          canvas.drawLine(12, 4, 12, 20, p);
          canvas.drawLine(4, 12, 20, 12, p);
          break;
        case "Undo":
        case "Redo":
          canvas.drawArc(5, 7, 21, 21, name.equals("Undo") ? 190 : 330, 220, false, p);
          path.moveTo(5, 4);
          path.lineTo(5, 12);
          path.lineTo(12, 12);
          break;
        case "Preview":
          path.moveTo(8, 4);
          path.lineTo(20, 12);
          path.lineTo(8, 20);
          path.close();
          break;
        case "Layers":
          path.moveTo(3, 8);
          path.lineTo(12, 3);
          path.lineTo(21, 8);
          path.lineTo(12, 13);
          path.close();
          canvas.drawLine(3, 13, 12, 18, p);
          canvas.drawLine(12, 18, 21, 13, p);
          canvas.drawLine(3, 18, 12, 23, p);
          canvas.drawLine(12, 23, 21, 18, p);
          break;
        case "Settings":
          canvas.drawCircle(12, 12, 7, p);
          canvas.drawCircle(12, 12, 2, p);
          for (int n = 0; n < 8; n++) {
            double a = n * Math.PI / 4;
            canvas.drawLine(
                12 + (float) Math.sin(a) * 8,
                12 + (float) Math.cos(a) * 8,
                12 + (float) Math.sin(a) * 10,
                12 + (float) Math.cos(a) * 10,
                p);
          }
          break;
        case "Wallpaper":
          canvas.drawRoundRect(3,3,21,21,3,3,p); canvas.drawCircle(8,8,2,p);
          path.moveTo(4,18); path.lineTo(10,12); path.lineTo(14,16); path.lineTo(18,11); path.lineTo(21,15);
          break;
        case "Clock":
        case "Schedules":
          canvas.drawCircle(12, 12, 9, p);
          path.moveTo(12, 5);
          path.lineTo(12, 12);
          path.lineTo(17, 15);
          break;
        default:
          canvas.drawCircle(5, 12, 1, p);
          canvas.drawCircle(12, 12, 1, p);
          canvas.drawCircle(19, 12, 1, p);
      }
      canvas.drawPath(path, p);
      canvas.restore();
    }
  }
}
