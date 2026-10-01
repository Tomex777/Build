package com.homira.aod;

import android.content.*;
import android.graphics.*;
import android.util.AtomicFile;
import java.io.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.json.*;

/** Every committed Studio edit is durably stored with atomic rename; no delayed autosave window. */
public final class Store {
  private final Context context;
  private final AtomicFile file;
  public List<Domain.Theme> themes = new ArrayList<>();
  public List<Domain.Rule> rules = new ArrayList<>();
  public String active = "";
  public boolean override = false;

  public Store(Context c) {
    context = c.getApplicationContext();
    file = new AtomicFile(new File(context.getFilesDir(), "designs.json"));
    load();
  }

  private void load() {
    if (!file.getBaseFile().exists()) {
      themes = Domain.presets();
      active = themes.get(0).id;
      save();
      return;
    }
    try (InputStream in = file.openRead()) {
      JSONObject j =
          new JSONObject(
              new String(readLimited(in, 4_000_000), java.nio.charset.StandardCharsets.UTF_8));
      JSONArray ts = j.getJSONArray("themes");
      for (int i = 0; i < ts.length(); i++)
        themes.add(Domain.decode(ts.getJSONObject(i).toString()));
      active = j.optString("active", "");
      override = j.optBoolean("override", false);
      JSONArray rs = j.optJSONArray("rules");
      if (rs != null)
        for (int i = 0; i < rs.length(); i++) {
          JSONObject r = rs.getJSONObject(i);
          Domain.Rule rule = new Domain.Rule();
          rule.id = r.getString("id");
          rule.name = r.getString("name");
          rule.themeId = r.getString("theme");
          rule.start = r.getInt("start");
          rule.end = r.getInt("end");
          rule.charging = r.getInt("charging");
          rule.days = r.getInt("days");
          rule.priority = r.getInt("priority");
          rules.add(rule);
        }
      if (themes.isEmpty()) throw new IOException("No designs");
    } catch (Exception e) {
      throw new IllegalStateException(
          "Your designs could not be read. The saved file has been preserved.", e);
    }
  }

  public void save() {
    FileOutputStream out = null;
    try {
      JSONArray ts = new JSONArray(), rs = new JSONArray();
      for (Domain.Theme t : themes) ts.put(new JSONObject(Domain.encode(t)));
      for (Domain.Rule r : rules)
        rs.put(
            new JSONObject()
                .put("id", r.id)
                .put("name", r.name)
                .put("theme", r.themeId)
                .put("start", r.start)
                .put("end", r.end)
                .put("charging", r.charging)
                .put("days", r.days)
                .put("priority", r.priority));
      byte[] data =
          new JSONObject()
              .put("themes", ts)
              .put("rules", rs)
              .put("active", active)
              .put("override", override)
              .toString()
              .getBytes(java.nio.charset.StandardCharsets.UTF_8);
      out = file.startWrite();
      out.write(data);
      file.finishWrite(out);
    } catch (Exception e) {
      if (out != null) file.failWrite(out);
      throw new IllegalStateException("Couldn't save the design.", e);
    }
  }

  public Domain.Theme find(String id) {
    for (Domain.Theme t : themes) if (t.id.equals(id)) return t;
    return themes.get(0);
  }

  public Domain.Theme current(boolean charging) {
    Domain.Rule r = override ? null : Domain.resolve(rules, ZonedDateTime.now(), charging);
    return find(r == null ? active : r.themeId);
  }

  public void put(Domain.Theme t) {
    for (int i = 0; i < themes.size(); i++)
      if (themes.get(i).id.equals(t.id)) {
        themes.set(i, t);
        save();
        return;
      }
    themes.add(t);
    save();
  }

  public SharedPreferences settings() {
    return context.getSharedPreferences("settings", Context.MODE_PRIVATE);
  }

  public File asset(String id) {
    if (!id.matches("[a-f0-9]{64}\\.png")) throw new IllegalArgumentException("Invalid image.");
    return new File(new File(context.getFilesDir(), "assets"), id);
  }

  public String importImage(InputStream in) throws Exception {
    byte[] bytes = readLimited(in, 12_000_000);
    BitmapFactory.Options opt = new BitmapFactory.Options();
    opt.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opt);
    if (opt.outWidth < 1 || opt.outHeight < 1) throw new IOException("Choose an image file.");
    opt.inJustDecodeBounds = false;
    opt.inSampleSize = 1;
    while (Math.max(opt.outWidth, opt.outHeight) / opt.inSampleSize > 1200) opt.inSampleSize *= 2;
    Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opt);
    if (b == null) throw new IOException("Image couldn't be read.");
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    b.compress(Bitmap.CompressFormat.PNG, 100, png);
    b.recycle();
    byte[] data = png.toByteArray();
    StringBuilder key = new StringBuilder();
    for (byte v : MessageDigest.getInstance("SHA-256").digest(data))
      key.append(String.format(Locale.ROOT, "%02x", v));
    String id = key + ".png";
    File f = asset(id);
    f.getParentFile().mkdirs();
    AtomicFile af = new AtomicFile(f);
    FileOutputStream out = af.startWrite();
    try {
      out.write(data);
      af.finishWrite(out);
    } catch (Exception e) {
      af.failWrite(out);
      throw e;
    }
    return id;
  }

  public static byte[] readLimited(InputStream in, int limit) throws IOException {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    int n;
    while ((n = in.read(buf)) != -1) {
      if (b.size() + n > limit) throw new IOException("File is too large.");
      b.write(buf, 0, n);
    }
    return b.toByteArray();
  }

  public String exportTheme(Domain.Theme theme) throws Exception {
    Domain.Theme t = theme.copy();
    JSONObject j = new JSONObject(Domain.encode(t));
    JSONObject assets = new JSONObject();
    Set<String> ids = new HashSet<>();
    for (Domain.Element e : t.elements) if (!e.asset.isEmpty()) ids.add(e.asset);
    if (!t.backgroundAsset.isEmpty()) ids.add(t.backgroundAsset);
    for (String id : ids) {
      try (InputStream in = new FileInputStream(asset(id))) {
        assets.put(
            id,
            android.util.Base64.encodeToString(
                readLimited(in, 12_000_000), android.util.Base64.NO_WRAP));
      }
    }
    j.put("assets", assets);
    return j.toString();
  }

  public Domain.Theme importTheme(InputStream in) throws Exception {
    JSONObject j =
        new JSONObject(
            new String(readLimited(in, 20_000_000), java.nio.charset.StandardCharsets.UTF_8));
    Domain.Theme t = Domain.decode(j.toString());
    JSONObject assets = j.optJSONObject("assets");
    for (Domain.Element e : t.elements) restoreAsset(e.asset, assets);
    restoreAsset(t.backgroundAsset, assets);
    t.id = UUID.randomUUID().toString();
    t.name = t.name + " imported";
    if (t.name.length() > 80) t.name = t.name.substring(0, 80);
    put(t);
    return t;
  }

  private void restoreAsset(String id, JSONObject assets) throws Exception {
    if (id.isEmpty()) return;
    if (assets == null || !assets.has(id)) throw new IOException("Theme image is missing.");
    String encoded = assets.getString(id);
    if (encoded.length() > 16_000_000) throw new IOException("Image too large.");
    String imported =
        importImage(
            new ByteArrayInputStream(
                android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)));
    if (!imported.equals(id)) throw new IOException("Theme image doesn't match its identity.");
  }
}
