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

/** Night Mods configuration surface for Night Core phone-level Status Bar Tweaks. */
public final class StatusBarTweaksFragment extends BaseFragment {
    private MaterialToolbar toolbar;
    private SwitchMaterial enabled;
    private SwitchMaterial paddingEnabled;
    private SeekBar padding;
    private TextView paddingValue;
    private TextView bridgeStatus;
    private boolean suppressWrites;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_night_status_bar_tweaks, container, false);
        toolbar = root.findViewById(R.id.night_status_bar_toolbar);
        enabled = root.findViewById(R.id.night_system_ui_enabled);
        paddingEnabled = root.findViewById(R.id.night_status_bar_padding_enabled);
        padding = root.findViewById(R.id.night_status_bar_padding);
        paddingValue = root.findViewById(R.id.night_status_bar_padding_value);
        bridgeStatus = root.findViewById(R.id.night_status_bar_bridge_status);
        setupToolbar(toolbar, null, R.string.night_status_bar_tweaks, -1);

        enabled.setOnCheckedChangeListener((button, checked) -> {
            refreshControlState();
            saveCurrent();
        });
        paddingEnabled.setOnCheckedChangeListener((button, checked) -> {
            refreshControlState();
            saveCurrent();
        });
        padding.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                paddingValue.setText(getString(R.string.night_status_bar_padding_value, progress));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) { saveCurrent(); }
        });
        load();
        return root;
    }

    @Override public void onResume() { super.onResume(); load(); }

    private void load() {
        runAsync(() -> {
            var state = NightCoreBridge.loadSystemUi(requireContext().getApplicationContext());
            runOnUiThread(() -> render(state));
        });
    }

    private void render(@Nullable NightCoreBridge.SystemUiState state) {
        if (toolbar == null) return;
        suppressWrites = true;
        boolean available = state != null;
        enabled.setEnabled(available);
        bridgeStatus.setText(available ? R.string.night_core_connected : R.string.night_core_unavailable);
        if (available) {
            enabled.setChecked(state.enabled);
            paddingEnabled.setChecked(state.statusBarPaddingEnabled);
            padding.setProgress(state.statusBarPaddingDp);
            paddingValue.setText(getString(R.string.night_status_bar_padding_value, state.statusBarPaddingDp));
        }
        suppressWrites = false;
        refreshControlState();
    }

    private void refreshControlState() {
        if (enabled == null || paddingEnabled == null || padding == null) return;
        boolean master = enabled.isEnabled() && enabled.isChecked();
        paddingEnabled.setEnabled(master);
        padding.setEnabled(master && paddingEnabled.isChecked());
        if (paddingValue != null) paddingValue.setEnabled(master && paddingEnabled.isChecked());
    }

    private void saveCurrent() {
        if (suppressWrites || toolbar == null) return;
        var state = new NightCoreBridge.SystemUiState(
                enabled.isChecked(),
                paddingEnabled.isChecked(),
                padding.getProgress());
        runAsync(() -> {
            boolean success = NightCoreBridge.saveSystemUi(requireContext().getApplicationContext(), state);
            if (!success) runOnUiThread(() -> showHint(R.string.night_core_write_failed, true));
        });
    }

    @Override
    public void onDestroyView() {
        toolbar = null;
        enabled = null;
        paddingEnabled = null;
        padding = null;
        paddingValue = null;
        bridgeStatus = null;
        super.onDestroyView();
    }
}
