package org.lsposed.manager.nightmods.ui;

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
import org.lsposed.manager.nightmods.NightModsBackend;
import org.lsposed.manager.ui.fragment.BaseFragment;
import org.lsposed.manager.util.ModuleUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Night-owned module list presentation backed by the existing LSPosed ET plumbing.
 * Framework/service code remains upstream; this fragment owns only Night Mods UI.
 */
public final class NightModulesFragment extends BaseFragment implements ModuleUtil.ModuleListener {
    private final ModuleUtil moduleUtil = ModuleUtil.getInstance();
    private final ModuleAdapter adapter = new ModuleAdapter();

    private MaterialToolbar toolbar;
    private TextView frameworkTitle;
    private TextView frameworkDetail;
    private View frameworkStatus;
    private RecyclerView recyclerView;
    private View emptyState;
    private TextView emptyTitle;
    private TextView emptyDetail;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View root = inflater.inflate(R.layout.fragment_night_modules, container, false);

        toolbar = root.findViewById(R.id.night_modules_toolbar);
        frameworkStatus = root.findViewById(R.id.night_framework_status);
        frameworkTitle = root.findViewById(R.id.night_framework_title);
        frameworkDetail = root.findViewById(R.id.night_framework_detail);
        recyclerView = root.findViewById(R.id.night_modules_list);
        emptyState = root.findViewById(R.id.night_modules_empty);
        emptyTitle = root.findViewById(R.id.night_modules_empty_title);
        emptyDetail = root.findViewById(R.id.night_modules_empty_detail);

        setupToolbar(toolbar, null, R.string.Modules, R.menu.menu_modules);
        toolbar.setNavigationIcon(null);

        var searchItem = toolbar.getMenu().findItem(R.id.menu_search);
        if (searchItem != null && searchItem.getActionView() instanceof SearchView searchView) {
            searchView.setQueryHint(getString(R.string.Modules));
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    adapter.filter(query);
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    adapter.filter(newText);
                    return true;
                }
            });
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);

        moduleUtil.addListener(this);
        render();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        render();
    }

    @Override
    public void onModulesReloaded() {
        runOnUiThread(this::render);
    }

    @Override
    public void onSingleModuleReloaded(ModuleUtil.InstalledModule module) {
        runOnUiThread(this::render);
    }

    private void render() {
        if (toolbar == null || recyclerView == null) return;

        boolean frameworkActive = NightModsBackend.isFrameworkActive();
        List<NightModsBackend.ModuleRow> rows = frameworkActive
                ? NightModsBackend.modules()
                : Collections.emptyList();

        if (frameworkActive) {
            frameworkTitle.setText(R.string.night_framework_active);
            String version = NightModsBackend.frameworkVersion();
            frameworkDetail.setText(TextUtils.isEmpty(version)
                    ? getString(R.string.night_framework_connected_detail)
                    : version);
        } else {
            frameworkTitle.setText(R.string.night_framework_unavailable);
            frameworkDetail.setText(R.string.night_framework_unavailable_detail);
        }

        frameworkStatus.setActivated(frameworkActive);
        adapter.submit(rows);

        boolean empty = rows.isEmpty();
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);

        if (empty) {
            if (frameworkActive) {
                emptyTitle.setText(R.string.night_modules_empty_title);
                emptyDetail.setText(R.string.night_modules_empty_detail);
            } else {
                emptyTitle.setText(R.string.night_modules_offline_title);
                emptyDetail.setText(R.string.night_modules_offline_detail);
            }
        }
    }

    private void openScopes(@NonNull NightModsBackend.ModuleRow row) {
        Bundle args = new Bundle();
        args.putString("modulePackageName", row.packageName);
        args.putInt("moduleUserId", row.userId);
        try {
            getNavController().navigate(R.id.action_modules_fragment_to_app_list_fragment, args);
        } catch (IllegalArgumentException ignored) {
            // Navigation may already be in progress after a rapid double tap.
        }
    }

    private void setModuleEnabled(@NonNull NightModsBackend.ModuleRow row, boolean enabled) {
        runAsync(() -> {
            boolean success = NightModsBackend.setEnabled(row.packageName, enabled);
            runOnUiThread(() -> {
                if (!success) {
                    showHint(R.string.night_module_toggle_failed, true);
                }
                render();
            });
        });
    }

    @Override
    public void onDestroyView() {
        moduleUtil.removeListener(this);
        recyclerView.setAdapter(null);
        toolbar = null;
        frameworkTitle = null;
        frameworkDetail = null;
        frameworkStatus = null;
        recyclerView = null;
        emptyState = null;
        emptyTitle = null;
        emptyDetail = null;
        super.onDestroyView();
    }

    private final class ModuleAdapter extends RecyclerView.Adapter<ModuleViewHolder> {
        private final List<NightModsBackend.ModuleRow> allRows = new ArrayList<>();
        private final List<NightModsBackend.ModuleRow> visibleRows = new ArrayList<>();
        private String query = "";

        void submit(@NonNull List<NightModsBackend.ModuleRow> rows) {
            allRows.clear();
            allRows.addAll(rows);
            applyFilter();
        }

        void filter(@Nullable String value) {
            query = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            applyFilter();
        }

        private void applyFilter() {
            visibleRows.clear();
            if (query.isEmpty()) {
                visibleRows.addAll(allRows);
            } else {
                for (NightModsBackend.ModuleRow row : allRows) {
                    if (row.name.toLowerCase(Locale.ROOT).contains(query)
                            || row.packageName.toLowerCase(Locale.ROOT).contains(query)) {
                        visibleRows.add(row);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ModuleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_night_module, parent, false);
            return new ModuleViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ModuleViewHolder holder, int position) {
            NightModsBackend.ModuleRow row = visibleRows.get(position);
            holder.bind(row);
        }

        @Override
        public int getItemCount() {
            return visibleRows.size();
        }
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

            String targetCount = row.scopeCount == 1
                    ? "1 target"
                    : row.scopeCount + " targets";
            if (TextUtils.isEmpty(row.versionName)) {
                subtitle.setText(targetCount);
            } else {
                subtitle.setText(targetCount + "  ·  " + row.versionName);
            }

            var installed = NightModsBackend.module(row.packageName, row.userId);
            if (installed != null) {
                PackageManager pm = App.getInstance().getPackageManager();
                icon.setImageDrawable(installed.app.loadIcon(pm));
            } else {
                icon.setImageDrawable(null);
            }

            toggle.setOnCheckedChangeListener(null);
            toggle.setChecked(row.enabled);
            toggle.setEnabled(NightModsBackend.isFrameworkActive());
            toggle.setOnCheckedChangeListener((buttonView, isChecked) ->
                    setModuleEnabled(row, isChecked));

            itemView.setOnClickListener(v -> openScopes(row));
        }
    }
}
