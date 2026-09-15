package org.lsposed.manager.nightmods.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.lsposed.manager.R;
import org.lsposed.manager.nightmods.NightCoreBridge;
import org.lsposed.manager.nightmods.NightModsBackend;
import org.lsposed.manager.ui.fragment.BaseFragment;
import org.lsposed.manager.util.ModuleUtil;

/** First-party Night Core module detail inside Night Mods. */
public final class NightCoreFragment extends BaseFragment implements ModuleUtil.ModuleListener {
    private final ModuleUtil moduleUtil = ModuleUtil.getInstance();
    private MaterialToolbar toolbar;
    private TextView engineDetail;
    private TextView moduleDetail;
    private TextView scopeDetail;
    private SwitchMaterial moduleEnabled;
    private View scopeRow;
    private View bubbleStylerRow;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_night_core, container, false);
        toolbar = root.findViewById(R.id.night_core_toolbar);
        engineDetail = root.findViewById(R.id.night_core_engine_detail);
        moduleDetail = root.findViewById(R.id.night_core_module_detail);
        scopeDetail = root.findViewById(R.id.night_core_scope_detail);
        moduleEnabled = root.findViewById(R.id.night_core_module_enabled);
        scopeRow = root.findViewById(R.id.night_core_scope_row);
        bubbleStylerRow = root.findViewById(R.id.night_bubble_styler_row);
        setupToolbar(toolbar, null, R.string.night_core, -1);
        scopeRow.setOnClickListener(v -> openScopes());
        bubbleStylerRow.setOnClickListener(v -> safeNavigate(R.id.action_night_core_to_bubble_styler));
        moduleUtil.addListener(this);
        render();
        return root;
    }

    @Override public void onResume() { super.onResume(); render(); }
    @Override public void onModulesReloaded() { runOnUiThread(this::render); }
    @Override public void onSingleModuleReloaded(ModuleUtil.InstalledModule module) { runOnUiThread(this::render); }

    private void render() {
        if (toolbar == null) return;
        boolean installed = NightCoreBridge.isInstalled(requireContext());
        String version = NightCoreBridge.versionName(requireContext());
        engineDetail.setText(installed
                ? (TextUtils.isEmpty(version) ? getString(R.string.night_installed) : getString(R.string.night_installed_version, version))
                : getString(R.string.night_core_not_installed));
        boolean frameworkActive = NightModsBackend.isFrameworkActive();
        var module = NightCoreBridge.frameworkModule();
        moduleEnabled.setOnCheckedChangeListener(null);
        if (!frameworkActive) {
            moduleEnabled.setChecked(false);
            moduleEnabled.setEnabled(false);
            moduleDetail.setText(R.string.night_framework_unavailable);
            scopeDetail.setText(R.string.night_scope_unavailable);
            scopeRow.setEnabled(false);
        } else if (module == null) {
            moduleEnabled.setChecked(false);
            moduleEnabled.setEnabled(false);
            moduleDetail.setText(R.string.night_core_not_recognized);
            scopeDetail.setText(R.string.night_scope_unavailable);
            scopeRow.setEnabled(false);
        } else {
            moduleEnabled.setChecked(module.enabled);
            moduleEnabled.setEnabled(true);
            moduleDetail.setText(module.enabled ? R.string.night_module_enabled : R.string.night_module_disabled);
            scopeDetail.setText(getResources().getQuantityString(R.plurals.night_target_count, module.scopeCount, module.scopeCount));
            scopeRow.setEnabled(true);
            moduleEnabled.setOnCheckedChangeListener((button, checked) -> setEnabled(checked));
        }
        bubbleStylerRow.setEnabled(installed);
        bubbleStylerRow.setAlpha(installed ? 1f : 0.45f);
    }

    private void setEnabled(boolean enabled) {
        runAsync(() -> {
            boolean success = NightModsBackend.setEnabled(NightCoreBridge.PACKAGE_NAME, enabled);
            runOnUiThread(() -> {
                if (!success) showHint(R.string.night_module_toggle_failed, true);
                render();
            });
        });
    }

    private void openScopes() {
        var module = NightCoreBridge.frameworkModule();
        if (module == null) return;
        Bundle args = new Bundle();
        args.putString("modulePackageName", module.packageName);
        args.putInt("moduleUserId", module.userId);
        try {
            getNavController().navigate(R.id.action_night_core_to_app_list, args);
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Override
    public void onDestroyView() {
        moduleUtil.removeListener(this);
        toolbar = null;
        engineDetail = null;
        moduleDetail = null;
        scopeDetail = null;
        moduleEnabled = null;
        scopeRow = null;
        bubbleStylerRow = null;
        super.onDestroyView();
    }
}
