package org.lsposed.manager.nightmods.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.lsposed.manager.R;
import org.lsposed.manager.nightmods.NightCoreBridge;
import org.lsposed.manager.ui.fragment.BaseFragment;

/** Night Mods configuration surface for Night Core's first capability. */
public final class BubbleStylerFragment extends BaseFragment {
    private MaterialToolbar toolbar;
    private SwitchMaterial enabled;
    private SwitchMaterial whatsapp;
    private SwitchMaterial instagram;
    private SeekBar radius;
    private SeekBar spacing;
    private TextView radiusValue;
    private TextView spacingValue;
    private TextView bridgeStatus;
    private boolean suppressWrites;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_night_bubble_styler, container, false);
        toolbar = root.findViewById(R.id.night_bubble_toolbar);
        enabled = root.findViewById(R.id.night_bubble_enabled);
        whatsapp = root.findViewById(R.id.night_bubble_whatsapp);
        instagram = root.findViewById(R.id.night_bubble_instagram);
        radius = root.findViewById(R.id.night_bubble_radius);
        spacing = root.findViewById(R.id.night_bubble_spacing);
        radiusValue = root.findViewById(R.id.night_bubble_radius_value);
        spacingValue = root.findViewById(R.id.night_bubble_spacing_value);
        bridgeStatus = root.findViewById(R.id.night_bubble_bridge_status);
        setupToolbar(toolbar, null, R.string.night_bubble_styler, -1);
        enabled.setOnCheckedChangeListener((button, checked) -> saveCurrent());
        whatsapp.setOnCheckedChangeListener((button, checked) -> saveCurrent());
        instagram.setOnCheckedChangeListener((button, checked) -> saveCurrent());
        radius.setOnSeekBarChangeListener(new SaveOnStopSeekListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                radiusValue.setText(getString(R.string.night_corner_radius_value, progress));
            }
        });
        spacing.setOnSeekBarChangeListener(new SaveOnStopSeekListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                spacingValue.setText(getString(R.string.night_message_spacing_value, progress));
            }
        });
        load();
        return root;
    }

    @Override public void onResume() { super.onResume(); load(); }

    private void load() {
        runAsync(() -> {
            var state = NightCoreBridge.loadBubbleStyle(requireContext().getApplicationContext());
            runOnUiThread(() -> render(state));
        });
    }

    private void render(@Nullable NightCoreBridge.BubbleStyleState state) {
        if (toolbar == null) return;
        suppressWrites = true;
        boolean available = state != null;
        enabled.setEnabled(available);
        whatsapp.setEnabled(available);
        instagram.setEnabled(available);
        radius.setEnabled(available);
        spacing.setEnabled(available);
        bridgeStatus.setText(available ? R.string.night_core_connected : R.string.night_core_unavailable);
        if (available) {
            enabled.setChecked(state.enabled);
            whatsapp.setChecked(state.whatsapp);
            instagram.setChecked(state.instagram);
            radius.setProgress(state.radius);
            spacing.setProgress(state.spacing);
            radiusValue.setText(getString(R.string.night_corner_radius_value, state.radius));
            spacingValue.setText(getString(R.string.night_message_spacing_value, state.spacing));
        }
        suppressWrites = false;
    }

    private void saveCurrent() {
        if (suppressWrites || toolbar == null) return;
        var state = new NightCoreBridge.BubbleStyleState(enabled.isChecked(), whatsapp.isChecked(), instagram.isChecked(), radius.getProgress(), spacing.getProgress());
        runAsync(() -> {
            boolean success = NightCoreBridge.saveBubbleStyle(requireContext().getApplicationContext(), state);
            if (!success) runOnUiThread(() -> showHint(R.string.night_core_write_failed, true));
        });
    }

    @Override
    public void onDestroyView() {
        toolbar = null;
        enabled = null;
        whatsapp = null;
        instagram = null;
        radius = null;
        spacing = null;
        radiusValue = null;
        spacingValue = null;
        bridgeStatus = null;
        super.onDestroyView();
    }

    private abstract class SaveOnStopSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) { saveCurrent(); }
    }
}
