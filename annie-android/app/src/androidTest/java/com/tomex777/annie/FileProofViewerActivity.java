package com.tomex777.annie;

import android.app.Activity;
import android.os.Bundle;
import android.net.Uri;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/** Independent test-APK recipient: no dependency on the target APK's Kotlin runtime. */
public class FileProofViewerActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String result;
        try {
            Uri uri = getIntent().getData();
            if (uri == null || !"content".equals(uri.getScheme())) throw new IllegalArgumentException("Expected content URI");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            }
            byte[] bytes = output.toByteArray();
            StringBuilder hash = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) hash.append(String.format("%02x", value & 255));
            result = "File opened safely\n" + bytes.length + " bytes\n" + hash;
        } catch (Exception failure) {
            result = "File permission failed: " + failure.getClass().getSimpleName();
        }
        TextView label = new TextView(this);
        label.setText(result);
        label.setTextSize(18f);
        label.setPadding(24, 60, 24, 24);
        setContentView(label);
    }
}
