package org.lsposed.manager.nightmods.ui;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.lsposed.manager.App;
import org.lsposed.manager.R;
import org.lsposed.manager.nightmods.NightCoreBridge;
import org.lsposed.manager.nightmods.NightModsBackend;
import org.lsposed.manager.ui.fragment.BaseFragment;
import org.lsposed.manager.util.ModuleUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Night-owned module list backed by LSPosed ET framework plumbing. */
public final class NightModulesFragment extends BaseFragment implements ModuleUtil.ModuleListener {
    private final ModuleUtil moduleUtil = ModuleUtil.getInstance();
    private final ModuleAdapter adapter = new ModuleAdapter();
    private MaterialToolbar toolbar;
    private View firstPartySection;
    private View nightCoreRow;
    private ImageView nightCoreIcon;
    private TextView nightCoreSummary;
    private TextView frameworkTitle;
    private TextView frameworkDetail;
    private View frameworkStatus;
    private RecyclerView recyclerView;
    private View emptyState;
    private TextView emptyTitle;
    private TextView emptyDetail;
    private boolean nightCoreBootstrapStarted;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_night_modules, container, false);
        toolbar = root.findViewById(R.id.night_modules_toolbar);
        firstPartySection = root.findViewById(R.id.night_first_party_section);
        nightCoreRow = root.findViewById(R.id.night_core_row);
        nightCoreIcon = root.findViewById(R.id.night_core_icon);
        nightCoreSummary = root.findViewById(R.id.night_core_summary);
        frameworkStatus = root.findViewById(R.id.night_framework_status);
        frameworkTitle = root.findViewById(R.id.night_framework_title);
        frameworkDetail = root.findViewById(R.id.night_framework_detail);
        recyclerView = root.findViewById(R.id.night_modules_list);
        emptyState = root.findViewById(R.id.night_modules_empty);
        emptyTitle = root.findViewById(R.id.night_modules_empty_title);
        emptyDetail = root.findViewById(R.id.night_modules_empty_detail);
        setupToolbar(toolbar, null, R.string.Modules, R.menu.menu_modules);
        toolbar.setNavigationIcon(null);
        nightCoreRow.setOnClickListener(v -> safeNavigate(R.id.action_modules_fragment_to_night_core));

        var searchItem = toolbar.getMenu().findItem(R.id.menu_search);
        if (searchItem != null && searchItem.getActionView() instanceof SearchView searchView) {
            searchView.setQueryHint(getString(R.string.Modules));
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override public boolean onQueryTextSubmit(String query) { adapter.filter(query); return true; }
                @Override public boolean onQueryTextChange(String newText) { adapter.filter(newText); return true; }
            });
        }
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);
        moduleUtil.addListener(this);
        render();
        return root;
    }

    @Override public void onResume() { super.onResume(); render(); }
    @Override public void onModulesReloaded() { runOnUiThread(this::render); }
    @Override public void onSingleModuleReloaded(ModuleUtil.InstalledModule module) { runOnUiThread(this::render); }

    private void render() {
        if (toolbar == null || recyclerView == null) return;
        boolean frameworkActive = NightModsBackend.isFrameworkActive();
        List<NightModsBackend.ModuleRow> frameworkRows = frameworkActive ? NightModsBackend.modules() : Collections.emptyList();
        renderNightCore(frameworkActive);
        List<NightModsBackend.ModuleRow> regularRows = new ArrayList<>();
        for (NightModsBackend.ModuleRow row : frameworkRows) {
            if (!NightCoreBridge.PACKAGE_NAME.equals(row.packageName)) regularRows.add(row);
        }
        if (frameworkActive) {
            frameworkTitle.setText(R.string.night_framework_active);
            String version = NightModsBackend.frameworkVersion();
            frameworkDetail.setText(TextUtils.isEmpty(version) ? getString(R.string.night_framework_connected_detail) : version);
        } else {
            frameworkTitle.setText(R.string.night_framework_unavailable);
            frameworkDetail.setText(R.string.night_framework_unavailable_detail);
        }
        frameworkStatus.setActivated(frameworkActive);
        adapter.submit(regularRows);
        boolean hasModules = !regularRows.isEmpty();
        recyclerView.setVisibility(hasModules ? View.VISIBLE : View.GONE);
        boolean showEmpty = frameworkActive && !hasModules;
        emptyState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        if (showEmpty) {
            emptyTitle.setText(R.string.night_no_other_modules);
            emptyDetail.setText(R.string.night_no_other_modules_detail);
        }
    }

    private void renderNightCore(boolean frameworkActive) {
        boolean installed = NightCoreBridge.isInstalled(requireContext());
        firstPartySection.setVisibility(installed ? View.VISIBLE : View.GONE);
        if (!installed) return;
        bootstrapNightCore();
        try {
            PackageManager pm = requireContext().getPackageManager();
            nightCoreIcon.setImageDrawable(pm.getApplicationIcon(NightCoreBridge.PACKAGE_NAME));
        } catch (PackageManager.NameNotFoundException ignored) {
            nightCoreIcon.setImageResource(R.drawable.ic_night_eclipse);
        }
        String version = NightCoreBridge.versionName(requireContext());
        var module = NightCoreBridge.frameworkModule();
        if (!frameworkActive) {
            nightCoreSummary.setText(TextUtils.isEmpty(version) ? getString(R.string.night_core_configure) : getString(R.string.night_core_configure_version, version));
        } else if (module == null) {
            nightCoreSummary.setText(R.string.night_core_not_recognized);
        } else {
            String state = getString(module.enabled ? R.string.night_module_enabled : R.string.night_module_disabled);
            String targets = getResources().getQuantityString(R.plurals.night_target_count, module.scopeCount, module.scopeCount);
            nightCoreSummary.setText(TextUtils.isEmpty(version)
                    ? getString(R.string.night_core_framework_summary, state, targets)
                    : getString(R.string.night_core_framework_summary_version, state, targets, version));
        }
    }

    private void bootstrapNightCore() {
        if (nightCoreBootstrapStarted) return;
        Context appContext = requireContext().getApplicationContext();
        if (!NightCoreBridge.isInstalled(appContext)) return;
        nightCoreBootstrapStarted = true;
        runAsync(() -> {
            boolean ready = NightCoreBridge.loadBubbleStyle(appContext) != null;
            if (!ready) runOnUiThread(() -> nightCoreBootstrapStarted = false);
        });
    }

    private void openScopes(@NonNull NightModsBackend.ModuleRow row) {
        Bundle args = new Bundle();
        args.putString("modulePackageName", row.packageName);
        args.putInt("moduleUserId", row.userId);
        try { getNavController().navigate(R.id.action_modules_fragment_to_app_list_fragment, args); }
        catch (IllegalArgumentException ignored) {}
    }

    private void setModuleEnabled(@NonNull NightModsBackend.ModuleRow row, boolean enabled) {
        runAsync(() -> {
            boolean success = NightModsBackend.setEnabled(row.packageName, enabled);
            runOnUiThread(() -> { if (!success) showHint(R.string.night_module_toggle_failed, true); render(); });
        });
    }

    @Override
    public void onDestroyView() {
        moduleUtil.removeListener(this);
        recyclerView.setAdapter(null);
        toolbar = null; firstPartySection = null; nightCoreRow = null; nightCoreIcon = null; nightCoreSummary = null;
        frameworkTitle = null; frameworkDetail = null; frameworkStatus = null; recyclerView = null;
        emptyState = null; emptyTitle = null; emptyDetail = null;
        super.onDestroyView();
    }

    private final class ModuleAdapter extends RecyclerView.Adapter<ModuleViewHolder> {
        private final List<NightModsBackend.ModuleRow> allRows = new ArrayList<>();
        private final List<NightModsBackend.ModuleRow> visibleRows = new ArrayList<>();
        private String query = "";
        void submit(@NonNull List<NightModsBackend.ModuleRow> rows) { allRows.clear(); allRows.addAll(rows); applyFilter(); }
        void filter(@Nullable String value) { query = value == null ? "" : value.trim().toLowerCase(Locale.ROOT); applyFilter(); }
        private void applyFilter() {
            visibleRows.clear();
            if (query.isEmpty()) visibleRows.addAll(allRows);
            else for (NightModsBackend.ModuleRow row : allRows) {
                if (row.name.toLowerCase(Locale.ROOT).contains(query) || row.packageName.toLowerCase(Locale.ROOT).contains(query)) visibleRows.add(row);
            }
            notifyDataSetChanged();
        }
        @NonNull @Override public ModuleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ModuleViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_night_module, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull ModuleViewHolder holder, int position) { holder.bind(visibleRows.get(position)); }
        @Override public int getItemCount() { return visibleRows.size(); }
    }

    private final class ModuleViewHolder extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView title;
        private final TextView subtitle;
        private final SwitchMaterial toggle;
        ModuleViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.night_module_icon);
            title = itemView.findViewById(R.id.night_module_name);
            subtitle = itemView.findViewById(R.id.night_module_subtitle);
            toggle = itemView.findViewById(R.id.night_module_toggle);
        }
        void bind(@NonNull NightModsBackend.ModuleRow row) {
            title.setText(row.name);
            String targetCount = getResources().getQuantityString(R.plurals.night_target_count, row.scopeCount, row.scopeCount);
            subtitle.setText(TextUtils.isEmpty(row.versionName) ? targetCount : targetCount + "  ·  " + row.versionName);
            var installed = NightModsBackend.module(row.packageName, row.userId);
            if (installed != null) icon.setImageDrawable(installed.app.loadIcon(App.getInstance().getPackageManager()));
            else icon.setImageDrawable(null);
            toggle.setOnCheckedChangeListener(null);
            toggle.setChecked(row.enabled);
            toggle.setEnabled(NightModsBackend.isFrameworkActive());
            toggle.setOnCheckedChangeListener((buttonView, isChecked) -> setModuleEnabled(row, isChecked));
            itemView.setOnClickListener(v -> openScopes(row));
        }
    }
}
