package dev.nightmods.core;

import android.app.Activity;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import dev.nightmods.core.config.BubbleStyleConfig;
import dev.nightmods.core.config.NightCorePreferences;

/** Diagnostic fallback. Night Mods is the normal configuration surface. */
@SuppressWarnings("deprecation")
public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        var prefs = NightCorePreferences.open(this);
        Switch enabled = findViewById(R.id.enabled);
        CheckBox whatsapp = findViewById(R.id.target_whatsapp);
        CheckBox instagram = findViewById(R.id.target_instagram);
        SeekBar radius = findViewById(R.id.radius);
        SeekBar spacing = findViewById(R.id.spacing);
        TextView radiusValue = findViewById(R.id.radius_value);
        TextView spacingValue = findViewById(R.id.spacing_value);

        enabled.setChecked(prefs.getBoolean(BubbleStyleConfig.KEY_ENABLED, true));
        whatsapp.setChecked(prefs.getBoolean(BubbleStyleConfig.KEY_WHATSAPP, true));
        instagram.setChecked(prefs.getBoolean(BubbleStyleConfig.KEY_INSTAGRAM, true));
        radius.setProgress(prefs.getInt(BubbleStyleConfig.KEY_RADIUS, 20));
        spacing.setProgress(prefs.getInt(BubbleStyleConfig.KEY_SPACING, 6));
        radiusValue.setText("Corner radius · " + radius.getProgress());
        spacingValue.setText("Message spacing · " + spacing.getProgress());

        enabled.setOnCheckedChangeListener((button, value) ->
                prefs.edit().putBoolean(BubbleStyleConfig.KEY_ENABLED, value).apply());
        whatsapp.setOnCheckedChangeListener((button, value) ->
                prefs.edit().putBoolean(BubbleStyleConfig.KEY_WHATSAPP, value).apply());
        instagram.setOnCheckedChangeListener((button, value) ->
                prefs.edit().putBoolean(BubbleStyleConfig.KEY_INSTAGRAM, value).apply());

        radius.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                radiusValue.setText("Corner radius · " + value);
                if (fromUser) prefs.edit().putInt(BubbleStyleConfig.KEY_RADIUS, value).apply();
            }
        });
        spacing.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                spacingValue.setText("Message spacing · " + value);
                if (fromUser) prefs.edit().putInt(BubbleStyleConfig.KEY_SPACING, value).apply();
            }
        });
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
