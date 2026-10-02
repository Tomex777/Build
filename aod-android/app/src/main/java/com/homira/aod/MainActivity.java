package com.homira.aod;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends androidx.activity.ComponentActivity {
  public Store store;
  public Surface canvas;
  public Domain.History history = new Domain.History();

  public static class EditorState extends androidx.lifecycle.ViewModel {
    public Domain.History history = new Domain.History();
    public String themeId = "";
  }

  private EditorState editorState;
  public String screen = "Home";
  private LinearLayout root, bar, contextBar;
  private TextView title;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private String pendingImage = "", pendingExport = "";
  private Dialog sheet;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    editorState = new androidx.lifecycle.ViewModelProvider(this).get(EditorState.class);
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new androidx.activity.OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (screen.equals("Home")) finish();
                else home();
              }
            });
    try {
      store = new Store(this);
    } catch (Exception e) {
      LinearLayout failed = Ui.column(this);
      failed.addView(
          Ui.text(
              this, "Your designs couldn't be opened. Your saved file is preserved.", 18, Ui.TEXT));
      setContentView(failed);
      return;
    }
    if (state != null) {
      pendingImage = state.getString("image", "");
      pendingExport = state.getString("export", "");
      String page = state.getString("screen", "Home");
      if (page.equals("Studio")) {
        studio(store.find(state.getString("theme", store.active)));
        return;
      }
      if (page.equals("Schedules")) {
        schedules();
        return;
      }
      if (page.equals("Settings")) {
        settings();
        return;
      }
    }
    home();
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    state.putString("screen", screen);
    state.putString("theme", canvas == null ? store.active : canvas.theme.id);
    state.putString("image", pendingImage);
    state.putString("export", pendingExport);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (store != null && screen.equals("Settings")) settings();
  }

  private void shell(String label, boolean back) {
    root = Ui.column(this);
    root.setBackgroundColor(Ui.BG);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(
              insets.getSystemWindowInsetLeft(),
              insets.getSystemWindowInsetTop(),
              insets.getSystemWindowInsetRight(),
              insets.getSystemWindowInsetBottom());
          return insets;
        });
    setContentView(root);
    root.requestApplyInsets();
    bar = Ui.row(this);
    if (back) bar.addView(Ui.icon(this, "Back", this::home));
    title = Ui.text(this, label, 20, Ui.TEXT);
    title.setSingleLine();
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    title.setTypeface(null, android.graphics.Typeface.BOLD);
    bar.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 56), 1));
    root.addView(bar);
    canvas = null;
  }

  private void toast(String value) {
    Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
  }

  private void error(Exception e) {
    toast(e.getMessage() == null ? "Couldn't complete that action." : e.getMessage());
  }

  private void persist() {
    try {
      store.put(canvas.theme);
    } catch (Exception e) {
      error(e);
    }
    canvas.refresh();
    contextControls();
  }

  private void change(Runnable edit) {
    Domain.Theme before = canvas.theme.copy();
    try {
      edit.run();
      Domain.validate(canvas.theme);
      history.record(before);
      persist();
    } catch (Exception e) {
      canvas.theme = before;
      canvas.invalidate();
      if (sheet != null) sheet.dismiss();
      error(e);
    }
  }

  public void home() {
    screen = "Home";
    shell("AOD", false);
    bar.addView(Ui.icon(this, "Schedules", this::schedules));
    bar.addView(Ui.icon(this, "Settings", this::settings));
    bar.addView(Ui.icon(this, "Add", this::newDesign));
    ScrollView scroll = new ScrollView(this);
    LinearLayout list = Ui.column(this);
    scroll.addView(list);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    Domain.Theme current = store.current(isCharging());
    Domain.Rule rule =
        store.override ? null : Domain.resolve(store.rules, ZonedDateTime.now(), isCharging());
    list.addView(
        Ui.text(
            this,
            rule == null
                ? (store.override ? "Manual · " : "Current · ") + current.name
                : rule.name + " · " + current.name,
            14,
            Ui.ACCENT));
    LinearLayout hero=Ui.column(this);
    hero.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),Ui.dp(this,12));
    Surface featured=new Surface(this,current);
    featured.passive=true; featured.shift=false; featured.designThumbnail=true;
    featured.setContentDescription("Edit current design"); featured.setOnClickListener(v -> studio(current));
    hero.addView(featured,new LinearLayout.LayoutParams(-1,Ui.dp(this,250)));
    LinearLayout start=Ui.row(this);
    start.addView(Ui.button(this,"Edit",() -> studio(current)),new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
    start.addView(Ui.button(this,"From wallpaper",() -> {
      Domain.Theme t=new Domain.Theme(); t.name="Wallpaper "+(store.themes.size()+1);
      Domain.Element clock=new Domain.Element(); clock.y=96; Domain.applyClockFamily(clock,"Thin");
      t.elements.add(clock); store.put(t); studio(t); chooseImage("outline");
    }),new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
    hero.addView(start); list.addView(hero);
    list.addView(Ui.text(this,"Your designs",18,Ui.TEXT));
    LinearLayout galleryRow = null;
    int index = 0;
    for (Domain.Theme t : store.themes) {
      if (index++ % 2 == 0) {
        galleryRow = Ui.row(this);
        galleryRow.setGravity(Gravity.TOP);
        list.addView(galleryRow);
      }
      LinearLayout card = Ui.column(this);
      card.setBackground(Ui.rounded(Ui.PANEL, Ui.dp(this, 12)));
      LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
      cp.setMargins(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
      galleryRow.addView(card, cp);
      Surface preview = new Surface(this, t);
      preview.shift = false;
      preview.passive = true;
      preview.setContentDescription("Edit " + t.name + " design");
      card.addView(preview, new LinearLayout.LayoutParams(-1, Ui.dp(this, 195)));
      preview.setOnClickListener(v -> studio(t));
      TextView name = Ui.text(this, t.name, 15, Ui.TEXT);
      name.setSingleLine();
      name.setEllipsize(android.text.TextUtils.TruncateAt.END);
      name.setOnClickListener(v -> studio(t));
      card.addView(name);
      LinearLayout actions = Ui.row(this);
      actions.addView(
          Ui.button(this, "Edit", () -> studio(t)),
          new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
      actions.addView(Ui.icon(this, "Preview", () -> preview(t, "Preview")));
      actions.addView(Ui.icon(this, "More", () -> designMenu(t)));
      card.addView(actions);
    }
    if (index % 2 == 1) galleryRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
    LinearLayout bottom = Ui.row(this);
    bottom.addView(
        Ui.primary(this, "Use display", this::modes), new LinearLayout.LayoutParams(0, -2, 1));
    bottom.addView(
        Ui.button(this, "Import theme", this::importTheme),
        new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(bottom);
    if (!store.settings().getBoolean("welcomed", false)) {
      store.settings().edit().putBoolean("welcomed", true).commit();
      new AlertDialog.Builder(this)
          .setTitle("Make space for your time")
          .setMessage(
              "Edit a design, then choose Preview, bedside Charging or your device's Ambient"
                  + " screensaver. Notification access is optional.")
          .setPositiveButton("Start designing", (d, w) -> studio(store.find(store.active)))
          .setNegativeButton("Explore", null)
          .show();
    }
  }

  private boolean isCharging() {
    Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    return i != null && i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
  }

  private void designMenu(Domain.Theme t) {
    new AlertDialog.Builder(this)
        .setTitle(t.name)
        .setItems(
            new String[] {
              "Use this design", "Rename", "Duplicate", "Export theme", "Share theme", "Delete"
            },
            (d, w) -> {
              switch (w) {
                case 0:
                  store.active = t.id;
                  store.override = true;
                  store.save();
                  home();
                  break;
                case 1:
                  input(
                      "Design name",
                      t.name,
                      value -> {
                        Domain.Theme renamed = t.copy();
                        renamed.name = value.trim();
                        try {
                          store.put(renamed);
                          home();
                        } catch (Exception e) {
                          error(e);
                        }
                      });
                  break;
                case 2:
                  Domain.Theme c = t.copy();
                  c.id = UUID.randomUUID().toString();
                  c.name = t.name.substring(0, Math.min(75, t.name.length())) + " copy";
                  store.put(c);
                  home();
                  break;
                case 3:
                  exportTheme(t);
                  break;
                case 4:
                  shareTheme(t);
                  break;
                case 5:
                  if (store.themes.size() == 1) {
                    toast("Keep at least one design.");
                    return;
                  }
                  new AlertDialog.Builder(this)
                      .setTitle("Delete " + t.name + "?")
                      .setMessage("This also removes schedules using this design.")
                      .setPositiveButton(
                          "Delete",
                          (q, k) -> {
                            store.themes.remove(t);
                            store.rules.removeIf(r -> r.themeId.equals(t.id));
                            if (store.active.equals(t.id)) store.active = store.themes.get(0).id;
                            store.save();
                            home();
                          })
                      .setNegativeButton("Cancel", null)
                      .show();
                  break;
              }
            })
        .show();
  }

  private void newDesign() {
    input(
        "Design name",
        "My AOD",
        name -> {
          Domain.Theme t = new Domain.Theme();
          t.name = name.trim();
          Domain.Element e = new Domain.Element();
          t.elements.add(e);
          try {
            store.put(t);
            studio(t);
          } catch (Exception ex) {
            error(ex);
          }
        });
  }

  private void input(String label, String value, java.util.function.Consumer<String> done) {
    EditText edit = new EditText(this);
    edit.setText(value);
    edit.setSingleLine();
    edit.setSelectAllOnFocus(true);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(label)
            .setView(edit)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create();
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    b -> {
                      if (edit.getText().toString().trim().isEmpty()) {
                        edit.setError("Enter a value");
                        return;
                      }
                      done.accept(edit.getText().toString());
                      dialog.dismiss();
                    }));
    dialog.show();
  }

  public void studio(Domain.Theme t) {
    screen = "Studio";
    if (!editorState.themeId.equals(t.id)) {
      editorState.themeId = t.id;
      editorState.history = new Domain.History();
    }
    history = editorState.history;
    shell(t.name, true);
    bar.addView(
        Ui.icon(
            this,
            "Undo",
            () -> {
              canvas.theme = history.undo(canvas.theme);
              persist();
            }));
    bar.addView(
        Ui.icon(
            this,
            "Redo",
            () -> {
              canvas.theme = history.redo(canvas.theme);
              persist();
            }));
    bar.addView(Ui.icon(this, "Preview", () -> preview(canvas.theme, "Preview")));
    canvas = new Surface(this, t.copy());
    canvas.editing = true;
    canvas.shift = false;
    canvas.edits =
        new Surface.EditListener() {
          public void selected(String id) {
            contextControls();
          }

          public void changed(Domain.Theme before) {
            history.record(before);
            persist();
          }
        };
    root.addView(canvas, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout tools=Ui.row(this);
    tools.setPadding(Ui.dp(this,8),Ui.dp(this,6),Ui.dp(this,8),Ui.dp(this,6));
    tools.addView(Ui.tool(this,"Wallpaper","Wallpaper",this::wallpaperControls),new LinearLayout.LayoutParams(0,Ui.dp(this,64),1));
    tools.addView(Ui.tool(this,"Clock","Clocks",this::clockDesigner),new LinearLayout.LayoutParams(0,Ui.dp(this,64),1));
    tools.addView(Ui.tool(this,"Add","Add",this::addMenu),new LinearLayout.LayoutParams(0,Ui.dp(this,64),1));
    tools.addView(Ui.tool(this,"Layers","Layers",this::layers),new LinearLayout.LayoutParams(0,Ui.dp(this,64),1));
    root.addView(tools);
    contextBar = Ui.row(this);
    contextBar.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),Ui.dp(this,8));
    root.addView(contextBar);
    contextControls();
  }

  private void contextControls() {
    if (contextBar == null || canvas == null || !screen.equals("Studio")) return;
    contextBar.removeAllViews();

    Domain.Element e = canvas.selection();
    if (e != null) {
      contextBar.addView(
          Ui.button(this, e.type, this::inspector),
          new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
      contextBar.addView(Ui.icon(this, "More", () -> elementMenu(e)));
    } else {
      contextBar.addView(
          Ui.button(this, "Canvas style", this::canvasStyle),
          new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
    }
    contextBar.addView(Ui.primary(this,"Use design",() -> {
      try {
        store.put(canvas.theme); store.active=canvas.theme.id; store.override=true; store.save();
        modes();
      } catch(Exception ex) { error(ex); }
    }),new LinearLayout.LayoutParams(Ui.dp(this,112),Ui.dp(this,48)));
    bar.getChildAt(2).setEnabled(history.canUndo());
    bar.getChildAt(3).setEnabled(history.canRedo());
  }

  public void add(String type) {
    change(
        () -> {
          Domain.Element e = new Domain.Element();
          e.type = type;
          e.y = 320;
          e.size = type.equals("Clock") ? 52 : 18;
          e.h = type.equals("Clock") ? 90 : 64;
          if (type.equals("Shape")) {
            e.family = "Rectangle";
            e.h = 40;
          }
          canvas.theme.elements.add(e);
          canvas.selected = e.id;
        });
    if (type.equals("Calendar")
        && checkSelfPermission(android.Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED)
      new AlertDialog.Builder(this)
          .setTitle("Calendar on your display")
          .setMessage(
              "Allow access to show your next event. Event titles stay hidden on ambient displays"
                  + " unless enabled in Privacy.")
          .setPositiveButton(
              "Allow",
              (d, w) ->
                  requestPermissions(new String[] {android.Manifest.permission.READ_CALENDAR}, 40))
          .setNegativeButton("Later", null)
          .show();
    if (type.equals("Notifications") || type.equals("Media"))
      if (!Notifications.granted(this)) notificationAccess();
    if (type.equals("Image")) chooseImage(canvas.selected);
  }

  private void addMenu() {
    new AlertDialog.Builder(this)
        .setTitle("Add element")
        .setItems(Domain.TYPES, (d, w) -> add(Domain.TYPES[w]))
        .show();
  }

  private void layers() {
    String[] values = new String[canvas.theme.elements.size()];
    for (int i = 0; i < values.length; i++) {
      Domain.Element e = canvas.theme.elements.get(i);
      values[i] =
          (i + 1)
              + " · "
              + e.type
              + (e.locked ? " · locked" : "")
              + (!e.visible ? " · hidden" : "");
    }
    new AlertDialog.Builder(this)
        .setTitle("Layers · back to front")
        .setItems(
            values,
            (d, w) -> {
              canvas.selected = canvas.theme.elements.get(w).id;
              canvas.invalidate();
              contextControls();
              inspector();
            })
        .show();
  }

  private void elementMenu(Domain.Element e) {
    new AlertDialog.Builder(this)
        .setTitle(e.type)
        .setItems(
            new String[] {
              "Duplicate",
              "Delete",
              e.locked ? "Unlock" : "Lock",
              e.visible ? "Hide" : "Show",
              "Bring forward",
              "Send backward",
              "Center horizontally",
              "Center vertically"
            },
            (d, w) ->
                change(
                    () -> {
                      int index = canvas.theme.elements.indexOf(e);
                      switch (w) {
                        case 0:
                          canvas.selected = Domain.duplicate(canvas.theme, e).id;
                          break;
                        case 1:
                          Domain.delete(canvas.theme, e.id);
                          canvas.selected = "";
                          break;
                        case 2:
                          e.locked = !e.locked;
                          break;
                        case 3:
                          e.visible = !e.visible;
                          break;
                        case 4:
                          if (index < canvas.theme.elements.size() - 1)
                            Collections.swap(canvas.theme.elements, index, index + 1);
                          break;
                        case 5:
                          if (index > 0) Collections.swap(canvas.theme.elements, index, index - 1);
                          break;
                        case 6:
                          e.x = (360 - e.w) / 2;
                          break;
                        case 7:
                          e.y = (720 - e.h) / 2;
                          break;
                      }
                    }))
        .show();
  }

  private LinearLayout sheet(String label) {
    if (sheet != null) sheet.dismiss();
    sheet = new Dialog(this);
    LinearLayout layout = Ui.column(this);
    layout.setBackground(Ui.rounded(Ui.PANEL, Ui.dp(this, 20)));
    layout.setFocusableInTouchMode(true);
    layout.requestFocus();
    LinearLayout head = Ui.row(this);
    head.addView(Ui.text(this, label, 18, Ui.TEXT), new LinearLayout.LayoutParams(0, -2, 1));
    head.addView(Ui.button(this, "Done", () -> sheet.dismiss()));
    layout.addView(head);
    ScrollView scroll = new ScrollView(this);
    LinearLayout content = Ui.column(this);
    scroll.addView(content);
    layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    sheet.setContentView(layout);
    Window window = sheet.getWindow();
    window.setBackgroundDrawableResource(android.R.color.transparent);
    window.setGravity(Gravity.BOTTOM);
    WindowManager.LayoutParams attrs=window.getAttributes(); attrs.dimAmount=.15f; window.setAttributes(attrs);
    sheet.setOnDismissListener(d -> {
      if(canvas!=null) { canvas.wallpaperPreview=false; canvas.invalidate(); }
    });
    window.setLayout(-1, (int) (getResources().getDisplayMetrics().heightPixels * .46));
    window.setSoftInputMode(
        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    sheet.show();
    window.setLayout(-1, (int) (getResources().getDisplayMetrics().heightPixels * .42));
    return content;
  }

  private void number(
      LinearLayout parent, String name, float value, java.util.function.Consumer<Float> set) {
    LinearLayout r = Ui.row(this);
    r.addView(
        Ui.text(this, name, 14, Ui.TEXT), new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
    EditText input = new EditText(this);
    input.setSingleLine();
    input.setText(
        new java.math.BigDecimal(Float.toString(value))
            .setScale(3, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString());
    input.setTextColor(Ui.TEXT);
    input.setTextSize(15);
    input.setInputType(
        android.text.InputType.TYPE_CLASS_NUMBER
            | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
    input.setContentDescription(name);
    r.addView(input, new LinearLayout.LayoutParams(Ui.dp(this, 92), Ui.dp(this, 48)));
    r.addView(
        Ui.button(
            this,
            "Apply",
            () -> {
              try {
                float v = Float.parseFloat(input.getText().toString());
                if (!Float.isFinite(v)) throw new IllegalArgumentException();
                set.accept(v);
              } catch (Exception e) {
                input.setError("Enter a valid number");
              }
            }));
    parent.addView(r);
  }

  private void toggle(
      LinearLayout parent, String name, boolean value, java.util.function.Consumer<Boolean> set) {
    Switch s = new Switch(this);
    s.setText(name);
    s.setTextColor(Ui.TEXT);
    s.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
    s.setMinHeight(Ui.dp(this, 48));
    s.setChecked(value);
    s.setOnCheckedChangeListener((b, v) -> set.accept(v));
    parent.addView(s);
  }

  private void choose(
      LinearLayout parent,
      String name,
      String value,
      String[] choices,
      java.util.function.Consumer<String> set) {
    Button control = Ui.button(this, name + " · " + value, () -> {});
    control.setOnClickListener(
        v ->
            new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(
                    choices,
                    (d, w) -> {
                      set.accept(choices[w]);
                      control.setText(name + " · " + choices[w]);
                    })
                .show());
    parent.addView(control);
  }

  private void color(
      LinearLayout parent, String name, int current, java.util.function.IntConsumer set) {
    String[] labels = {"Pearl", "Mint", "Amber", "Ice", "Rose", "White", "Custom hex"};
    int[] values = {0xffedf3f0, 0xffa8e9d1, 0xffc9a886, 0xff9bcff5, 0xffdf9bac, Color.WHITE};
    parent.addView(
        Ui.button(
            this,
            name,
            () ->
                new AlertDialog.Builder(this)
                    .setTitle(name)
                    .setItems(
                        labels,
                        (d, w) -> {
                          if (w < values.length) set.accept(values[w]);
                          else
                            input(
                                "Color · #RRGGBB",
                                String.format("#%06X", current & 0xffffff),
                                v -> {
                                  try {
                                    set.accept(Color.parseColor(v));
                                  } catch (Exception e) {
                                    toast("Use #RRGGBB.");
                                  }
                                });
                        })
                    .show()));
  }

  public void inspector() {
    Domain.Element e = canvas.selection();
    if (e == null) return;
    LinearLayout content = sheet(e.type);
    if (e.locked) {
      content.addView(
          Ui.button(
              this,
              "Unlock to edit",
              () -> {
                change(() -> e.locked = false);
                inspector();
              }));
      return;
    }
    if(e.type.equals("Clock")) content.addView(Ui.button(this,"Browse clock styles",this::clockDesigner));
    if(!e.type.equals("Image") && !e.type.equals("Shape"))
      range(content,"Text size",e.size,8,160,v -> change(() -> e.size=v));
    range(content,"Opacity",e.opacity,0,1,v -> change(() -> e.opacity=v));
    color(content, "Foreground color", e.color, v -> change(() -> e.color = v));
    color(content, "Accent color", e.accent, v -> change(() -> e.accent = v));
    choose(
        content,
        "Font",
        e.font,
        new String[] {
          "sans-serif", "sans-serif-thin", "sans-serif-condensed", "serif", "monospace"
        },
        v -> change(() -> e.font = v));
    toggle(content, "Bold", e.weight >= 600, v -> change(() -> e.weight = v ? 700 : 400));
    choose(
        content,
        "Alignment",
        new String[] {"Left", "Center", "Right"}[e.align],
        new String[] {"Left", "Center", "Right"},
        v -> change(() -> e.align = Arrays.asList("Left", "Center", "Right").indexOf(v)));
    if (e.type.equals("Clock")) {
      choose(
          content,
          "Clock family",
          e.family,
          Domain.CLOCKS,
          v ->
              change(
                  () -> {
                    Domain.applyClockFamily(e, v);
                  }));
      if(e.family.equals("Analog")) {
        toggle(content,"Dial ring",e.dialRing,v -> change(() -> e.dialRing=v));
        toggle(content,"Hour markers",e.dialMarkers,v -> change(() -> e.dialMarkers=v));
        range(content,"Hand thickness",e.lineWidth,.5f,6,v -> change(() -> e.lineWidth=v));
      }
      if (e.family.equals("Date integrated"))
        toggle(content, "Date above clock", e.dateTop, v -> change(() -> e.dateTop = v));
      boolean part=e.family.equals("Hours") || e.family.equals("Minutes") || e.family.equals("Seconds");
      if(!part || e.family.equals("Hours")) {
        toggle(content,"24-hour time",e.h24,v -> change(() -> e.h24=v));
        toggle(content,"Leading zero",e.zero,v -> change(() -> e.zero=v));
      }
      if(!part) toggle(content,"Show seconds",e.seconds,v -> change(() -> e.seconds=v));
    }
    if (e.type.equals("Text")) {
      content.addView(
          Ui.button(this, "Edit text", () -> input("Text", e.text, v -> change(() -> e.text = v))));
      toggle(content, "Personal text", e.privateContent, v -> change(() -> e.privateContent = v));
    }
    if (e.type.equals("Image")) {
      content.addView(Ui.button(this,"Choose image",() -> chooseImage(e.id)));
      if(e.treatment.equals("Outline")) outlineControls(content,e);
    }
    if (e.type.equals("Shape")) {
      toggle(content, "Gradient", e.gradient, v -> change(() -> e.gradient = v));
    }
    if (e.type.equals("Shape"))
      choose(
          content,
          "Shape",
          e.family,
          new String[] {"Rectangle", "Circle", "Line"},
          v -> change(() -> e.family = v));
    if (e.type.equals("Media"))
      choose(
          content,
          "Artwork",
          e.treatment,
          new String[] {"Text only", "Icon + text", "Compact art", "Dimmed art", "Monochrome art"},
          v -> change(() -> e.treatment = v));
    if (e.type.equals("Notifications")) {
      choose(
          content,
          "Labels",
          e.treatment,
          new String[] {"Icons", "App names"},
          v -> change(() -> e.treatment = v));
      toggle(content, "Icon-only", e.privateContent, v -> change(() -> e.privateContent = v));
      content.addView(Ui.button(this, "Notification access", this::notificationAccess));
    }
    content.addView(Ui.text(this,"Position & size",16,Ui.MUTED));
    number(content,"Letter spacing",e.spacing,v -> change(() -> e.spacing=Domain.clamp(v,-2,12)));
    number(
        content,
        "X",
        e.x,
        v ->
            change(
                () -> {
                  e.x = v;
                  Domain.bounds(e);
                }));
    number(
        content,
        "Y",
        e.y,
        v ->
            change(
                () -> {
                  e.y = v;
                  Domain.bounds(e);
                }));
    number(
        content,
        "Width",
        e.w,
        v ->
            change(
                () -> {
                  e.w = v;
                  Domain.bounds(e);
                }));
    number(
        content,
        "Height",
        e.h,
        v ->
            change(
                () -> {
                  e.h = v;
                  Domain.bounds(e);
                }));
    number(
        content,
        "Rotation",
        e.rotation,
        v ->
            change(
                () -> {
                  e.rotation = v;
                  Domain.bounds(e);
                }));
  }

  private void range(LinearLayout parent,String label,float value,float low,float high,java.util.function.Consumer<Float> set) {
    TextView heading=Ui.text(this,label+" · "+Math.round(value*100)/100f,14,Ui.TEXT);
    parent.addView(heading);
    SeekBar seek=new SeekBar(this); seek.setMax(100); seek.setProgress(Math.round((value-low)/(high-low)*100));
    seek.setContentDescription(label);
    seek.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
    seek.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
    seek.setPadding(Ui.dp(this,20),0,Ui.dp(this,20),0);
    seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar bar,int progress,boolean user) {
        heading.setText(label+" · "+Math.round((low+(high-low)*progress/100)*100)/100f);
      }
      public void onStartTrackingTouch(SeekBar bar) {}
      public void onStopTrackingTouch(SeekBar bar) { set.accept(low+(high-low)*bar.getProgress()/100); }
    });
    parent.addView(seek,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));
  }

  private void outlineControls(LinearLayout content,Domain.Element e) {
    range(content,"Outline detail",e.outlineDetail,0,1,v -> change(() -> e.outlineDetail=v));
    range(content,"Line thickness",e.lineWidth,.5f,6,v -> change(() -> e.lineWidth=v));
    range(content,"Brightness",e.opacity,.05f,1,v -> change(() -> e.opacity=v));
    color(content,"Outline color",e.color,v -> change(() -> e.color=v));
  }

  public void wallpaperControls() {
    LinearLayout content=sheet("Wallpaper");
    content.addView(Ui.button(this,"Choose wallpaper",() -> chooseImage("outline")));
    Domain.Element found=null;
    for(Domain.Element e:canvas.theme.elements) if(e.type.equals("Image") && e.treatment.equals("Outline")) { found=e; break; }
    if(found==null) return;
    Domain.Element e=found;
    choose(content,"Compare",canvas.wallpaperPreview?"Wallpaper":"AOD outline",new String[]{"AOD outline","Wallpaper"},v -> {
      canvas.wallpaperPreview=v.equals("Wallpaper"); canvas.invalidate();
    });
    outlineControls(content,e);
    toggle(content,"Show outline",e.visible,v -> change(() -> e.visible=v));
    toggle(content,"Lock position",e.locked,v -> change(() -> e.locked=v));
    content.addView(Ui.button(this,"Arrange outline",() -> {
      change(() -> e.locked=false); canvas.selected=e.id; canvas.wallpaperPreview=false;
      canvas.invalidate(); contextControls(); sheet.dismiss();
    }));
  }

  public void clockDesigner() {
    Domain.Element selected=canvas.selection();
    Domain.Element found=selected!=null && selected.type.equals("Clock")?selected:null;
    if(found==null) for(Domain.Element e:canvas.theme.elements) if(e.type.equals("Clock")) { found=e; break; }
    if(found!=null && found.locked) {
      canvas.selected=found.id; canvas.invalidate(); contextControls(); inspector(); return;
    }
    Domain.Element target=found;
    LinearLayout content=sheet("Clock studio");
    content.addView(Ui.text(this,"Choose a style",16,Ui.TEXT));
    LinearLayout row=null;
    for(int i=0;i<8;i++) {
      if(i%2==0) { row=Ui.row(this); content.addView(row); }
      String family=Domain.CLOCKS[i];
      Domain.Theme sample=new Domain.Theme(); Domain.Element example=new Domain.Element();
      Domain.applyClockFamily(example,family); example.x=(360-example.w)/2; example.y=(720-example.h)/2;
      sample.elements.add(example);
      LinearLayout tile=Ui.column(this); tile.setBackground(Ui.rounded(Ui.BG,Ui.dp(this,12)));
      Surface thumbnail=new Surface(this,sample); thumbnail.passive=true; thumbnail.shift=false; thumbnail.clockThumbnail=true;
      thumbnail.setContentDescription("Clock style "+family);
      tile.addView(thumbnail,new LinearLayout.LayoutParams(-1,Ui.dp(this,104)));
      TextView label=Ui.text(this,family,14,Ui.TEXT); label.setGravity(Gravity.CENTER); tile.addView(label);
      Runnable select=() -> {
        change(() -> {
          Domain.Element e=target;
          if(e==null) { e=new Domain.Element(); canvas.theme.elements.add(e); }
          Domain.applyClockFamily(e,family); canvas.selected=e.id;
        });
        canvas.invalidate(); inspector();
      };
      thumbnail.setOnClickListener(v -> select.run()); label.setOnClickListener(v -> select.run());
      LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);
      params.setMargins(Ui.dp(this,6),Ui.dp(this,6),Ui.dp(this,6),Ui.dp(this,6)); row.addView(tile,params);
    }
    content.addView(Ui.text(this,"Build your own",16,Ui.TEXT));
    content.addView(Ui.button(this,"Start custom clock",() -> {
      change(() -> {
        Domain.Element base=new Domain.Element();
        for(Domain.Element e:canvas.theme.elements) if(e.type.equals("Clock")) { base=e.copy(); break; }
        canvas.theme.elements.removeIf(e -> e.type.equals("Clock"));
        Domain.Element hours=base.copy(); hours.id=UUID.randomUUID().toString(); hours.family="Hours";
        hours.x=48; hours.w=112; hours.h=110; hours.size=72; hours.locked=false;
        Domain.bounds(hours);
        Domain.Element minutes=hours.copy(); minutes.id=UUID.randomUUID().toString(); minutes.family="Minutes";
        minutes.x=200; minutes.color=base.accent; Domain.bounds(minutes);
        canvas.theme.elements.add(hours); canvas.theme.elements.add(minutes); canvas.selected=hours.id;
      });
      canvas.invalidate(); inspector();
    }));
    content.addView(Ui.button(this,"Add clock part",() -> new AlertDialog.Builder(this).setTitle("Clock part")
        .setItems(new String[]{"Hours","Minutes","Seconds","Date","Text","Shape"},(d,w) -> {
          addClockPart(new String[]{"Hours","Minutes","Seconds","Date","Text","Shape"}[w]);
          inspector();
        }).show()));
    content.addView(Ui.button(this,"Save clock preset",() -> input("Clock preset name","My clock",name -> {
      Domain.Theme preset=new Domain.Theme(); preset.name=name.trim(); preset.clockPreset=true;
      for(Domain.Element e:canvas.theme.elements)
        if(e.type.equals("Clock") || e.type.equals("Date") || e.type.equals("Shape") || e.type.equals("Text")) preset.elements.add(e.copy());
      if(preset.elements.isEmpty()) { toast("Add a clock first."); return; }
      try { store.put(preset); toast("Clock preset saved"); } catch(Exception ex) { error(ex); }
    })));
    content.addView(Ui.button(this,"Add saved clock",() -> {
      List<Domain.Theme> presets=new ArrayList<>();
      for(Domain.Theme t:store.themes) if(t.clockPreset && !t.id.equals(canvas.theme.id)) presets.add(t);
      if(presets.isEmpty()) { toast("Save a clock preset first."); return; }
      String[] names=new String[presets.size()]; for(int i=0;i<names.length;i++) names[i]=presets.get(i).name;
      new AlertDialog.Builder(this).setTitle("Saved clocks").setItems(names,(d,w) -> {
        change(() -> {
          if(canvas.theme.elements.size()+presets.get(w).elements.size()>100) throw new IllegalArgumentException("A design can contain up to 100 elements.");
          for(Domain.Element original:presets.get(w).elements) {
            Domain.Element e=original.copy(); e.id=UUID.randomUUID().toString(); canvas.theme.elements.add(e); canvas.selected=e.id;
          }
        });
        canvas.invalidate(); sheet.dismiss();
      }).show();
    }));
  }

  public void addClockPart(String part) {
    change(() -> {
      boolean clock=part.equals("Hours") || part.equals("Minutes") || part.equals("Seconds");
      Domain.Element e=new Domain.Element(); e.type=clock?"Clock":part;
      e.family=clock?part:part.equals("Shape")?"Rectangle":"Digital"; e.seconds=part.equals("Seconds");
      e.w=clock?112:240; e.h=clock?110:48; e.x=part.equals("Minutes")?188:60; e.y=220; e.size=clock?72:18;
      canvas.theme.elements.add(e); canvas.selected=e.id;
    });
    canvas.invalidate();
  }

  private void canvasStyle() {
    LinearLayout content = sheet("Canvas");
    toggle(
        content,
        "Monochrome",
        canvas.theme.monochrome,
        v -> change(() -> canvas.theme.monochrome = v));
    content.addView(
        Ui.button(
            this,
            "Pure black",
            () ->
                change(
                    () -> {
                      canvas.theme.background = Color.BLACK;
                      canvas.theme.backgroundAsset = "";
                    })));
    color(
        content,
        "Preview background",
        canvas.theme.background,
        v -> change(() -> canvas.theme.background = v));
    content.addView(Ui.button(this, "Preview background image", () -> chooseImage("background")));
    content.addView(
        Ui.button(
            this,
            "Movement safe area",
            () -> {
              canvas.safeRegion = !canvas.safeRegion;
              canvas.invalidate();
            }));
  }

  private void preview(Domain.Theme theme, String mode) {
    Intent intent = new Intent(this, PreviewActivity.class);
    intent.putExtra("theme", theme.id);
    intent.putExtra("mode", mode);
    intent.putExtra("followSchedules", !mode.equals("Preview"));
    startActivity(intent);
  }

  private void modes() {
    new AlertDialog.Builder(this)
        .setTitle("Use display")
        .setItems(
            new String[] {
              "Preview",
              "Charging · bedside display",
              "Ambient · system screensaver",
              "Lock screen · open manually"
            },
            (d, w) -> {
              if (w == 2) {
                Intent i = new Intent(Settings.ACTION_DREAM_SETTINGS);
                if (i.resolveActivity(getPackageManager()) == null) {
                  toast("Ambient screensaver setup isn't available on this device.");
                  return;
                }
                new AlertDialog.Builder(this)
                    .setTitle("Enable Ambient")
                    .setMessage(
                        "Choose AOD in your device's screensaver settings and select when it runs."
                            + " Availability depends on your device.")
                    .setPositiveButton("Open settings", (q, k) -> startActivity(i))
                    .setNegativeButton("Cancel", null)
                    .show();
              } else if (w == 3)
                new AlertDialog.Builder(this)
                    .setTitle("Manual lock-screen display")
                    .setMessage(
                        "AOD can remain visible over the lock screen when opened here. It doesn't"
                            + " replace your phone's built-in always-on display or open itself in"
                            + " the background.")
                    .setPositiveButton(
                        "Open", (q, k) -> preview(store.current(isCharging()), "Lock screen"))
                    .setNegativeButton("Cancel", null)
                    .show();
              else preview(store.current(w == 1), w == 0 ? "Preview" : "Charging");
            })
        .show();
  }

  private void notificationAccess() {
    if (Notifications.granted(this)) {
      toast("Notification access is enabled.");
      return;
    }
    new AlertDialog.Builder(this)
        .setTitle("Notification and media access")
        .setMessage(
            "Allow AOD to display app icons and read active media sessions. Message text stays"
                + " hidden by default.")
        .setPositiveButton(
            "Open settings",
            (d, w) -> {
              Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
              if (i.resolveActivity(getPackageManager()) != null) startActivity(i);
              else toast("Notification access settings aren't available.");
            })
        .setNegativeButton("Later", null)
        .show();
  }

  public void schedules() {
    screen = "Schedules";
    shell("Schedules", true);
    bar.addView(Ui.icon(this, "Add", () -> ruleEditor(null)));
    toggle(
        root,
        "Manual design override",
        store.override,
        v -> {
          store.override = v;
          store.save();
        });
    Domain.Rule active =
        store.override ? null : Domain.resolve(store.rules, ZonedDateTime.now(), isCharging());
    root.addView(
        Ui.text(
            this,
            active == null
                ? "Current · " + store.find(store.active).name
                : "Active · " + active.name + " / " + store.find(active.themeId).name,
            14,
            Ui.ACCENT));
    ScrollView scroll = new ScrollView(this);
    LinearLayout list = Ui.column(this);
    scroll.addView(list);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (store.rules.isEmpty())
      list.addView(Ui.text(this, "Create a day, night or charging schedule.", 15, Ui.MUTED));
    for (Domain.Rule r : store.rules) {
      list.addView(
          Ui.button(
              this,
              r.name
                  + " · "
                  + time(r.start)
                  + "–"
                  + time(r.end)
                  + " · "
                  + store.find(r.themeId).name,
              () -> ruleEditor(r)));
    }
    root.addView(
        Ui.text(
            this, "Higher priority wins. Equal priorities use a stable rule order.", 12, Ui.MUTED));
  }

  private String time(int value) {
    return String.format(Locale.ROOT, "%02d:%02d", value / 60, value % 60);
  }

  private void ruleEditor(Domain.Rule original) {
    Domain.Rule r = new Domain.Rule();
    if (original != null) {
      r.id = original.id;
      r.name = original.name;
      r.themeId = original.themeId;
      r.start = original.start;
      r.end = original.end;
      r.charging = original.charging;
      r.priority = original.priority;
      r.days = original.days;
    } else r.themeId = store.active;
    LinearLayout c = sheet("Schedule");
    c.addView(
        Ui.button(this, "Name · " + r.name, () -> input("Rule name", r.name, v -> r.name = v)));
    String[] designs = store.themes.stream().map(t -> t.name).toArray(String[]::new);
    choose(
        c,
        "Design",
        store.find(r.themeId).name,
        designs,
        v -> {
          for (Domain.Theme t : store.themes)
            if (t.name.equals(v)) {
              r.themeId = t.id;
              break;
            }
        });
    c.addView(
        Ui.button(
            this,
            "Start · " + time(r.start),
            () ->
                new TimePickerDialog(
                        this, (p, h, m) -> r.start = h * 60 + m, r.start / 60, r.start % 60, true)
                    .show()));
    c.addView(
        Ui.button(
            this,
            "End · " + time(r.end),
            () ->
                new TimePickerDialog(
                        this, (p, h, m) -> r.end = h * 60 + m, r.end / 60, r.end % 60, true)
                    .show()));
    choose(
        c,
        "Power",
        new String[] {"Any", "Not charging", "Charging"}[r.charging + 1],
        new String[] {"Any", "Not charging", "Charging"},
        v -> r.charging = Arrays.asList("Any", "Not charging", "Charging").indexOf(v) - 1);
    number(c, "Priority", r.priority, v -> r.priority = (int) Domain.clamp(v, -100, 100));
    String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    for (int i = 0; i < 7; i++) {
      final int bit = 1 << i;
      toggle(
          c,
          days[i],
          (r.days & bit) != 0,
          v -> {
            if (v) r.days |= bit;
            else r.days &= ~bit;
          });
    }
    c.addView(
        Ui.button(
            this,
            "Save schedule",
            () -> {
              if (r.days == 0) {
                toast("Choose at least one day.");
                return;
              }
              if (original != null) store.rules.remove(original);
              store.rules.add(r);
              store.save();
              sheet.dismiss();
              schedules();
            }));
    if (original != null)
      c.addView(
          Ui.button(
              this,
              "Delete schedule",
              () -> {
                store.rules.remove(original);
                store.save();
                sheet.dismiss();
                schedules();
              }));
  }

  public void settings() {
    screen = "Settings";
    shell("Settings", true);
    ScrollView scroll = new ScrollView(this);
    LinearLayout c = Ui.column(this);
    scroll.addView(c);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    android.content.SharedPreferences p = store.settings();
    c.addView(Ui.text(this, "Display", 16, Ui.ACCENT));
    toggle(
        c,
        "Burn-in pixel shifting",
        p.getBoolean("shift", true),
        v -> p.edit().putBoolean("shift", v).commit());
    toggle(
        c,
        "Use design background when charging",
        p.getBoolean("chargingBackground", false),
        v -> p.edit().putBoolean("chargingBackground", v).commit());
    toggle(
        c,
        "Use device brightness",
        p.getBoolean("deviceBrightness", false),
        v -> p.edit().putBoolean("deviceBrightness", v).commit());
    number(
        c,
        "Brightness (0–1)",
        p.getFloat("brightness", .12f),
        v -> p.edit().putFloat("brightness", Domain.clamp(v, .01f, 1)).commit());
    toggle(
        c,
        "Dim at night",
        p.getBoolean("night", true),
        v -> p.edit().putBoolean("night", v).commit());
    number(
        c,
        "Night brightness",
        p.getFloat("nightBrightness", .02f),
        v -> p.edit().putFloat("nightBrightness", Domain.clamp(v, .01f, .2f)).commit());
    number(
        c,
        "Night start hour",
        p.getInt("nightStart", 22),
        v -> p.edit().putInt("nightStart", (int) Domain.clamp(v, 0, 23)).commit());
    number(
        c,
        "Night end hour",
        p.getInt("nightEnd", 7),
        v -> p.edit().putInt("nightEnd", (int) Domain.clamp(v, 0, 23)).commit());
    c.addView(Ui.text(this, "Privacy on ambient displays", 16, Ui.ACCENT));
    toggle(
        c,
        "Show media titles",
        p.getBoolean("media", false),
        v -> p.edit().putBoolean("media", v).commit());
    toggle(
        c,
        "Show calendar titles",
        p.getBoolean("calendar", false),
        v -> p.edit().putBoolean("calendar", v).commit());
    toggle(
        c,
        "Show personal text",
        p.getBoolean("personal", false),
        v -> p.edit().putBoolean("personal", v).commit());
    toggle(
        c,
        "Allow public notification previews",
        p.getBoolean("notificationText", false),
        v -> p.edit().putBoolean("notificationText", v).commit());
    c.addView(
        Ui.button(
            this,
            Notifications.granted(this) ? "Notification access · on" : "Notification access · off",
            this::notificationAccess));
    Set<String> packages = new TreeSet<>();
    synchronized (Notifications.items) {
      for (Notifications.Item i : Notifications.items.values()) packages.add(i.pkg);
    }
    for (String pkg : packages)
      toggle(
          c,
          "Hide " + pkg,
          p.getBoolean("hide." + pkg, false),
          v -> p.edit().putBoolean("hide." + pkg, v).commit());
    c.addView(
        Ui.button(
            this,
            checkSelfPermission(android.Manifest.permission.READ_CALENDAR)
                    == PackageManager.PERMISSION_GRANTED
                ? "Calendar access · on"
                : "Calendar access · off",
            () ->
                requestPermissions(new String[] {android.Manifest.permission.READ_CALENDAR}, 40)));
    c.addView(Ui.button(this, "Configure Ambient", this::modes));
    c.addView(
        Ui.text(
            this,
            "AOD · local-first\n"
                + "Themes stay on this device. Shared themes include your custom text and images;"
                + " review them before export.",
            12,
            Ui.MUTED));
  }

  private void chooseImage(String id) {
    pendingImage = id;
    if (sheet != null) sheet.dismiss();
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.setType("image/*");
    i.addCategory(Intent.CATEGORY_OPENABLE);
    startActivityForResult(i, 10);
  }

  private void importTheme() {
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.setType("*/*");
    i.addCategory(Intent.CATEGORY_OPENABLE);
    startActivityForResult(i, 11);
  }

  private void exportTheme(Domain.Theme t) {
    new AlertDialog.Builder(this)
        .setTitle("Export " + t.name + "?")
        .setMessage(
            "Custom text and images are included. Notifications, calendar events and media data are"
                + " never exported.")
        .setPositiveButton(
            "Export",
            (d, w) -> {
              pendingExport = t.id;
              Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
              i.setType("application/json");
              i.addCategory(Intent.CATEGORY_OPENABLE);
              i.putExtra(Intent.EXTRA_TITLE, "AOD-" + t.name + ".aod.json");
              startActivityForResult(i, 12);
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void shareTheme(Domain.Theme theme) {
    Domain.Theme snapshot = theme.copy();
    new AlertDialog.Builder(this)
        .setTitle("Share " + theme.name + "?")
        .setMessage(
            "Custom text and images are included. Live notifications, calendar events and media"
                + " data are excluded.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Continue",
            (dialog, which) ->
                io.execute(
                    () -> {
                      try {
                        File directory = new File(getCacheDir(), "themes");
                        if (!directory.isDirectory() && !directory.mkdirs())
                          throw new IOException("Couldn't prepare the theme.");
                        File[] old = directory.listFiles();
                        if (old != null)
                          for (File file : old)
                            if (System.currentTimeMillis() - file.lastModified() > 86400000)
                              file.delete();
                        File file = new File(directory, "AOD-" + UUID.randomUUID() + ".aod.json");
                        try (FileOutputStream out = new FileOutputStream(file)) {
                          out.write(
                              store
                                  .exportTheme(snapshot)
                                  .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        }
                        android.net.Uri uri =
                            androidx.core.content.FileProvider.getUriForFile(
                                this, getPackageName() + ".themes", file);
                        runOnUiThread(
                            () -> {
                              Intent send =
                                  new Intent(Intent.ACTION_SEND)
                                      .setType("application/json")
                                      .putExtra(Intent.EXTRA_STREAM, uri)
                                      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                              send.setClipData(ClipData.newRawUri("AOD theme", uri));
                              startActivity(Intent.createChooser(send, "Share theme"));
                            });
                      } catch (Exception exception) {
                        runOnUiThread(() -> error(exception));
                      }
                    }))
        .show();
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    android.net.Uri uri = data.getData();
    Domain.Theme editing = canvas == null ? null : canvas.theme.copy();
    String selected = pendingImage;
    Domain.Theme exportTheme = request == 12 ? store.find(pendingExport).copy() : null;
    io.execute(
        () -> {
          try {
            if (request == 10) {
              String id;
              try (InputStream in = getContentResolver().openInputStream(uri)) {
                id = store.importImage(in);
              }
              runOnUiThread(
                  () -> {
                    try {
                      if (editing == null) return;
                      Domain.Theme current = null;
                      for (Domain.Theme t : store.themes)
                        if (t.id.equals(editing.id)) current = t.copy();
                      if (current == null) return;
                      boolean open = canvas != null && screen.equals("Studio")
                          && canvas.theme.id.equals(editing.id);
                      if (open) current = canvas.theme.copy();
                      Domain.Theme before = current.copy();
                      if (selected.equals("outline")) Domain.installWallpaper(current,id);
                      else if (selected.equals("background")) current.backgroundAsset = id;
                      else {
                        boolean found = false;
                        for (Domain.Element e : current.elements)
                          if (e.id.equals(selected)) { e.asset = id; found = true; }
                        if (!found) return;
                      }
                      store.put(current);
                      if (open) {
                        history.record(before);
                        canvas.theme = current;
                        canvas.wallpaperPreview=false;
                        if(selected.equals("outline")) {
                          for(Domain.Element e:current.elements) if(e.treatment.equals("Outline")) canvas.selected=e.id;
                          if(sheet!=null) sheet.dismiss();
                        }
                        canvas.refresh(); contextControls();
                        if(selected.equals("outline")) wallpaperControls();
                      }
                    } catch (Exception e) {
                      error(e);
                    }
                  });
            } else if (request == 11) {
              Domain.Theme imported;
              try (InputStream in = getContentResolver().openInputStream(uri)) {
                imported = store.prepareThemeImport(in);
              }
              runOnUiThread(() -> {
                try { store.put(imported); studio(imported); }
                catch (Exception e) { error(e); }
              });
            } else if (request == 12) {
              String json = store.exportTheme(exportTheme);
              try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
              }
              runOnUiThread(() -> toast("Theme exported"));
            }
          } catch (Exception e) {
            runOnUiThread(() -> error(e));
          }
        });
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
    super.onRequestPermissionsResult(request, permissions, grants);
    if (canvas != null) {
      canvas.pauseRuntime();
      canvas.resumeRuntime();
      canvas.live.refreshCalendar();
      canvas.invalidate();
    }
    if (screen.equals("Settings")) settings();
  }

  private void runtimeViews(View v, boolean active) {
    if (v == null) return;
    if (v instanceof Surface) {
      if (active) ((Surface) v).resumeRuntime();
      else ((Surface) v).pauseRuntime();
    }
    if (v instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) v;
      for (int i = 0; i < group.getChildCount(); i++) runtimeViews(group.getChildAt(i), active);
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    runtimeViews(root, true);
  }

  @Override
  protected void onStop() {
    runtimeViews(root, false);
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    if (sheet != null) sheet.dismiss();
    io.shutdown();
    super.onDestroy();
  }
}
