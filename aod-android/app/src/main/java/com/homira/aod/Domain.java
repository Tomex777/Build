package com.homira.aod;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.*;

/** Portable coordinates use a 360 × 720 design space, independent of display density. */
public final class Domain {
  public static final int VERSION = 2;
  public static final String[] TYPES = {
    "Clock",
    "Date",
    "Battery",
    "Notifications",
    "Media",
    "Alarm",
    "Calendar",
    "Text",
    "Image",
    "Shape"
  };
  public static final String[] CLOCKS = {
    "Digital", "Large", "Thin", "Split", "Vertical", "Analog", "Words", "Date integrated"
  };

  public static class Element {
    public String id = UUID.randomUUID().toString(),
        type = "Clock",
        text = "Stay curious",
        family = "Digital",
        font = "sans-serif",
        asset = "",
        treatment = "Text only";
    public float x = 48,
        y = 220,
        w = 264,
        h = 90,
        rotation = 0,
        size = 52,
        opacity = 1,
        spacing = 0;
    public int color = 0xffedf3f0, accent = 0xffa8e9d1, weight = 400, align = 1;
    public boolean locked = false,
        visible = true,
        seconds = false,
        h24 = true,
        zero = true,
        privateContent = true,
        gradient = false,
        dateTop = false;

    public Element copy() {
      try {
        return readElement(writeElement(this));
      } catch (JSONException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  public static class Theme {
    public String id = UUID.randomUUID().toString(), name = "Untitled", backgroundAsset = "";
    public int background = 0xff000000;
    public boolean monochrome = false;
    public List<Element> elements = new ArrayList<>();

    public Theme copy() {
      return decode(encode(this));
    }
  }

  public static class Rule {
    public String id = UUID.randomUUID().toString(), name = "Night", themeId = "";
    public int start = 19 * 60, end = 7 * 60, charging = -1, priority = 0, days = 127;

    public boolean matches(ZonedDateTime t, boolean plugged) {
      int minute = t.getHour() * 60 + t.getMinute();
      // After midnight belongs to the day on which this overnight window began.
      int day = t.getDayOfWeek().getValue() - 1;
      if (start > end && minute < end) day = (day + 6) % 7;
      return (days & (1 << day)) != 0
          && (charging == -1 || (charging == 1) == plugged)
          && (start == end
              || (start < end ? minute >= start && minute < end : minute >= start || minute < end));
    }
  }

  public static Rule resolve(List<Rule> rules, ZonedDateTime time, boolean charging) {
    return rules.stream()
        .filter(r -> r.matches(time, charging))
        .sorted(Comparator.comparingInt((Rule r) -> r.priority).reversed().thenComparing(r -> r.id))
        .findFirst()
        .orElse(null);
  }

  public static String clock(Element e, ZonedDateTime t) {
    String pattern = e.h24 ? (e.zero ? "HH" : "H") : (e.zero ? "hh" : "h");
    return t.format(
        DateTimeFormatter.ofPattern(
            pattern + ":mm" + (e.seconds ? ":ss" : "") + (e.h24 ? "" : " a"), Locale.getDefault()));
  }

  public static void applyClockFamily(Element e, String family) {
    e.family = family;
    e.font = "sans-serif";
    e.weight = 400;
    e.size = 52;
    e.h = 100;
    switch (family) {
      case "Large":
        e.size = 88;
        e.h = 150;
        e.font = "sans-serif-black";
        e.weight = 700;
        break;
      case "Thin":
        e.font = "sans-serif-thin";
        e.weight = 100;
        break;
      case "Split":
        e.size = 78;
        e.h = 140;
        break;
      case "Vertical":
        e.size = 86;
        e.h = 250;
        break;
      case "Analog":
        e.w = 220;
        e.h = 220;
        break;
      case "Words":
        e.font = "serif";
        e.size = 32;
        e.h = 130;
        break;
      case "Date integrated":
        e.h = 120;
        break;
      default:
        break;
    }
    bounds(e);
  }

  public static void bounds(Element e) {
    e.w = clamp(e.w, 24, 336);
    e.h = clamp(e.h, 24, 696);
    e.size = clamp(e.size, 8, 160);
    e.opacity = clamp(e.opacity, 0, 1);
    e.rotation = clamp(e.rotation, -180, 180);
    double angle = Math.toRadians(e.rotation);
    float cs = (float) Math.abs(Math.cos(angle)), sn = (float) Math.abs(Math.sin(angle));
    float width = e.w * cs + e.h * sn, height = e.w * sn + e.h * cs;
    float fit = Math.min(1, Math.min(336 / width, 696 / height));
    e.w *= fit;
    e.h *= fit;
    float halfW = (e.w * cs + e.h * sn) / 2, halfH = (e.w * sn + e.h * cs) / 2;
    float cx = clamp(e.x + e.w / 2, 12 + halfW, 348 - halfW),
        cy = clamp(e.y + e.h / 2, 12 + halfH, 708 - halfH);
    e.x = cx - e.w / 2;
    e.y = cy - e.h / 2;
  }

  public static float clamp(float v, float low, float high) {
    if (!Float.isFinite(v)) return low;
    return Math.max(low, Math.min(high, v));
  }

  public static float snap(float p, float extent, float total) {
    float center = (total - extent) / 2;
    if (Math.abs(p - center) < 7) return center;
    if (Math.abs(p - 12) < 7) return 12;
    if (Math.abs(p - (total - 12 - extent)) < 7) return total - 12 - extent;
    return Math.round(p / 4) * 4;
  }

  public static float[] shift(Theme t, long epochMinute) {
    float[] xs = {-3, -2, 0, 2, 3, 2, 0, -2}, ys = {0, 2, 3, 2, 0, -2, -3, -2};
    int index = (int) Math.floorMod(epochMinute / 2, 8);
    float x = xs[index], y = ys[index];
    for (Element e : t.elements)
      if (e.visible) {
        double angle = Math.toRadians(e.rotation);
        float rw = (float) (Math.abs(Math.cos(angle)) * e.w + Math.abs(Math.sin(angle)) * e.h);
        float rh = (float) (Math.abs(Math.sin(angle)) * e.w + Math.abs(Math.cos(angle)) * e.h);
        float cx = e.x + e.w / 2, cy = e.y + e.h / 2;
        x = clamp(x, -(cx - rw / 2), 360 - (cx + rw / 2));
        y = clamp(y, -(cy - rh / 2), 720 - (cy + rh / 2));
      }
    return new float[] {x, y};
  }

  public static void validate(Theme t) {
    if (t.name == null || t.name.trim().isEmpty() || t.name.length() > 80)
      throw new IllegalArgumentException("Use a design name of 1–80 characters.");
    if (t.elements.size() > 100)
      throw new IllegalArgumentException("A design can contain up to 100 elements.");
    Set<String> ids = new HashSet<>();
    for (Element e : t.elements) {
      if (!Arrays.asList(TYPES).contains(e.type) || !ids.add(e.id))
        throw new IllegalArgumentException("Invalid element.");
      if (e.align < 0
          || e.align > 2
          || e.weight < 100
          || e.weight > 900
          || !Float.isFinite(e.spacing)) throw new IllegalArgumentException("Invalid typography.");
      if (e.text.length() > 2000) throw new IllegalArgumentException("Text is too long.");
      if (!e.asset.isEmpty() && !e.asset.matches("[a-f0-9]{64}\\.png"))
        throw new IllegalArgumentException("Invalid image reference.");
      bounds(e);
    }
    if (!t.backgroundAsset.isEmpty() && !t.backgroundAsset.matches("[a-f0-9]{64}\\.png"))
      throw new IllegalArgumentException("Invalid background reference.");
  }

  static JSONObject writeElement(Element e) throws JSONException {
    return new JSONObject()
        .put("id", e.id)
        .put("type", e.type)
        .put("text", e.text)
        .put("family", e.family)
        .put("font", e.font)
        .put("asset", e.asset)
        .put("treatment", e.treatment)
        .put("x", e.x)
        .put("y", e.y)
        .put("w", e.w)
        .put("h", e.h)
        .put("rotation", e.rotation)
        .put("size", e.size)
        .put("opacity", e.opacity)
        .put("spacing", e.spacing)
        .put("color", e.color)
        .put("accent", e.accent)
        .put("weight", e.weight)
        .put("align", e.align)
        .put("locked", e.locked)
        .put("visible", e.visible)
        .put("seconds", e.seconds)
        .put("h24", e.h24)
        .put("zero", e.zero)
        .put("private", e.privateContent)
        .put("gradient", e.gradient)
        .put("dateTop", e.dateTop);
  }

  static Element readElement(JSONObject j) throws JSONException {
    Element e = new Element();
    e.id = j.optString("id", e.id);
    e.type = j.optString("type", "Clock");
    e.text = j.optString("text", "Stay curious");
    e.family = j.optString("family", "Digital");
    e.font = j.optString("font", "sans-serif");
    e.asset = j.optString("asset", "");
    e.treatment = j.optString("treatment", "Text only");
    e.x = (float) j.optDouble("x", 48);
    e.y = (float) j.optDouble("y", 220);
    e.w = (float) j.optDouble("w", 264);
    e.h = (float) j.optDouble("h", 90);
    e.rotation = (float) j.optDouble("rotation", 0);
    e.size = (float) j.optDouble("size", 52);
    e.opacity = (float) j.optDouble("opacity", 1);
    e.spacing = (float) j.optDouble("spacing", 0);
    e.color = j.optInt("color", 0xffedf3f0);
    e.accent = j.optInt("accent", 0xffa8e9d1);
    e.weight = j.optInt("weight", 400);
    e.align = j.optInt("align", 1);
    e.locked = j.optBoolean("locked", false);
    e.visible = j.optBoolean("visible", true);
    e.seconds = j.optBoolean("seconds", false);
    e.h24 = j.optBoolean("h24", true);
    e.zero = j.optBoolean("zero", true);
    e.privateContent = j.optBoolean("private", true);
    e.gradient = j.optBoolean("gradient", false);
    e.dateTop = j.optBoolean("dateTop", false);
    return e;
  }

  public static String encode(Theme t) {
    try {
      validate(t);
      JSONArray a = new JSONArray();
      for (Element e : t.elements) a.put(writeElement(e));
      return new JSONObject()
          .put("schemaVersion", VERSION)
          .put("id", t.id)
          .put("name", t.name)
          .put("background", t.background)
          .put("backgroundAsset", t.backgroundAsset)
          .put("monochrome", t.monochrome)
          .put("elements", a)
          .toString();
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static Theme decode(String json) {
    try {
      JSONObject j = new JSONObject(json);
      int v = j.optInt("schemaVersion", 1);
      if (v < 1 || v > VERSION)
        throw new IllegalArgumentException("This theme needs a newer AOD version.");
      Theme t = new Theme();
      t.id = j.optString("id", t.id);
      t.name = j.optString("name", "Imported");
      t.background = j.optInt("background", 0xff000000);
      t.backgroundAsset = j.optString("backgroundAsset", "");
      t.monochrome = j.optBoolean("monochrome", false);
      JSONArray a = j.getJSONArray("elements");
      for (int i = 0; i < a.length(); i++) t.elements.add(readElement(a.getJSONObject(i)));
      validate(t);
      return t;
    } catch (JSONException e) {
      throw new IllegalArgumentException("Invalid theme file.", e);
    }
  }

  public static class History {
    private final Deque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();

    public void record(Theme before) {
      undo.addLast(encode(before));
      while (undo.size() > 60) undo.removeFirst();
      redo.clear();
    }

    public Theme undo(Theme now) {
      if (undo.isEmpty()) return now;
      redo.addLast(encode(now));
      return decode(undo.removeLast());
    }

    public Theme redo(Theme now) {
      if (redo.isEmpty()) return now;
      undo.addLast(encode(now));
      return decode(redo.removeLast());
    }

    public boolean canUndo() {
      return !undo.isEmpty();
    }

    public boolean canRedo() {
      return !redo.isEmpty();
    }
  }

  public static Element duplicate(Theme t, Element e) {
    Element c = e.copy();
    c.id = UUID.randomUUID().toString();
    c.x += 8;
    c.y += 8;
    c.locked = false;
    bounds(c);
    t.elements.add(c);
    return c;
  }

  public static void delete(Theme t, String id) {
    t.elements.removeIf(e -> e.id.equals(id));
  }

  public static List<Theme> presets() {
    List<Theme> list = new ArrayList<>();
    String[] names = {"Minimal", "Big Clock", "Analog", "Music", "Night", "Charging"};
    String[] clocks = {"Thin", "Split", "Analog", "Digital", "Vertical", "Date integrated"};
    for (int i = 0; i < names.length; i++) {
      Theme t = new Theme();
      t.name = names[i];
      Element c = new Element();
      c.family = clocks[i];
      if (i == 1 || i == 4) {
        c.y = 165;
        c.h = 210;
        c.size = 86;
      }
      if (i == 2) {
        c.w = 220;
        c.h = 220;
        c.x = 70;
        c.y = 190;
      }
      if (i == 4) c.color = 0xffc9a886;
      t.elements.add(c);
      Element d = new Element();
      d.type = "Date";
      d.y = 420;
      d.h = 36;
      d.size = 17;
      t.elements.add(d);
      Element b = new Element();
      b.type = i == 3 ? "Media" : "Battery";
      b.y = 478;
      b.h = i == 3 ? 80 : 32;
      b.size = 16;
      b.color = 0xffa8e9d1;
      t.elements.add(b);
      list.add(t);
    }
    return list;
  }
}
