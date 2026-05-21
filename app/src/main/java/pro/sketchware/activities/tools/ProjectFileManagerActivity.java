package pro.sketchware.activities.tools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.SrcCodeBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import a.a.a.ProjectBuilder;
import a.a.a.jC;
import a.a.a.yq;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.activities.editor.view.CodeViewerActivity;
import pro.sketchware.activities.editor.view.ViewCodeEditorActivity;
import pro.sketchware.databinding.ActivityProjectFileManagerBinding;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

/**
 * Project File Manager — uses the SAME source generation method as SrcViewerActivity.
 * Shows all Java activity files + XML layouts directly from Sketchware's internal data,
 * no build required.
 */
public class ProjectFileManagerActivity extends BaseAppCompatActivity {

    public static final String EXTRA_SC_ID = "sc_id";

    private ActivityProjectFileManagerBinding binding;
    private SourceAdapter adapter;
    private String scId;
    private List<SrcCodeBean> allBeans = new ArrayList<>();
    private String searchQuery = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityProjectFileManagerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        scId = getIntent().getStringExtra(EXTRA_SC_ID);
        if (scId == null) { finish(); return; }

        setupToolbar();
        setupSearch();
        setupRecyclerView();

        // Hide unused views from the MT-style layout
        binding.actionBar.setVisibility(View.GONE);
        binding.fabNew.setVisibility(View.GONE);
        binding.breadcrumbScroll.setVisibility(View.GONE);

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.recyclerView, false, false, false, true);

        loadSourceFiles();
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
        binding.toolbar.setTitle("Source Files");
        binding.toolbar.setSubtitle("Loading...");

        binding.toolbar.getMenu().add(0, 1, 0, "Search");
        binding.toolbar.getMenu().add(0, 2, 1, "Refresh");
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) { toggleSearch(); return true; }
            if (item.getItemId() == 2) { loadSourceFiles(); return true; }
            return false;
        });
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private void setupSearch() {
        binding.tilSearch.setVisibility(View.GONE);
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c) {}
            @Override public void onTextChanged(CharSequence s,int a,int b,int c) {}
            @Override public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim().toLowerCase();
                applyFilter();
            }
        });
    }

    private void toggleSearch() {
        boolean vis = binding.tilSearch.getVisibility() == View.VISIBLE;
        binding.tilSearch.setVisibility(vis ? View.GONE : View.VISIBLE);
        if (vis) { searchQuery = ""; applyFilter(); }
        else binding.etSearch.requestFocus();
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private void setupRecyclerView() {
        adapter = new SourceAdapter();
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
        binding.recyclerView.addItemDecoration(
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
    }

    // ── Source loading — SAME method as SrcViewerActivity ────────────────────

    private void loadSourceFiles() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.recyclerView.setVisibility(View.GONE);
        binding.tvEmpty.setVisibility(View.GONE);
        binding.toolbar.setSubtitle("Generating sources...");
        allBeans.clear();
        adapter.setItems(new ArrayList<>());

        new Thread(() -> {
            try {
                // Exact same code as SrcViewerActivity
                var yqVar = new yq(getBaseContext(), scId);
                var fileManager = jC.b(scId);
                var dataManager = jC.a(scId);
                var libraryManager = jC.c(scId);
                yqVar.a(libraryManager, fileManager, dataManager,
                        yq.ExportType.SOURCE_CODE_VIEWING);
                ProjectBuilder builder = new ProjectBuilder(this, yqVar);
                builder.buildBuiltInLibraryInformation();
                ArrayList<SrcCodeBean> beans =
                        yqVar.a(fileManager, dataManager, builder.getBuiltInLibraryManager());

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.recyclerView.setVisibility(View.VISIBLE);

                    if (beans == null || beans.isEmpty()) {
                        binding.tvEmpty.setVisibility(View.VISIBLE);
                        binding.tvEmpty.setText(
                                "No source files found.\nAdd at least one Activity to your project.");
                        binding.toolbar.setSubtitle("0 files");
                        return;
                    }

                    allBeans = beans;
                    binding.toolbar.setSubtitle(beans.size() + " files");
                    applyFilter();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.tvEmpty.setVisibility(View.VISIBLE);
                    binding.tvEmpty.setText("Error loading files:\n" + e.getMessage());
                    binding.toolbar.setSubtitle("Error");
                });
            }
        }).start();
    }

    private void applyFilter() {
        if (searchQuery.isEmpty()) {
            adapter.setItems(allBeans);
        } else {
            List<SrcCodeBean> filtered = new ArrayList<>();
            for (SrcCodeBean b : allBeans) {
                if (b.srcFileName.toLowerCase().contains(searchQuery) ||
                        (b.pkgName != null && b.pkgName.toLowerCase().contains(searchQuery))) {
                    filtered.add(b);
                }
            }
            adapter.setItems(filtered);
        }
        binding.tvEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    // ── Open file ─────────────────────────────────────────────────────────────

    private void openBean(SrcCodeBean bean) {
        String name = bean.srcFileName;
        String scheme = name.endsWith(".xml")
                ? CodeViewerActivity.SCHEME_XML
                : CodeViewerActivity.SCHEME_JAVA;

        if (name.endsWith(".xml")) {
            // Layout XML → ViewCodeEditor (has Live Preview button)
            Intent i = new Intent(this, ViewCodeEditorActivity.class);
            i.putExtra("code", bean.source);
            i.putExtra("sc_id", scId);
            i.putExtra("scheme", scheme);
            i.putExtra("title", name);
            i.putExtra(CodeViewerActivity.EXTRA_FILENAME, name);
            startActivity(i);
        } else {
            // Java/Kotlin → CodeViewer with edit support
            Intent i = new Intent(this, CodeViewerActivity.class);
            i.putExtra("code", bean.source);
            i.putExtra("sc_id", scId);
            i.putExtra("scheme", scheme);
            i.putExtra(CodeViewerActivity.EXTRA_FILENAME, name);
            startActivity(i);
        }
    }

    private void showOptions(SrcCodeBean bean) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(bean.srcFileName)
                .setMessage(bean.pkgName != null ? bean.pkgName : "")
                .setItems(new String[]{"Open / Edit", "Copy source code"}, (d, w) -> {
                    if (w == 0) openBean(bean);
                    else {
                        ClipboardManager cm =
                                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText(bean.srcFileName, bean.source));
                        SketchwareUtil.toast("Copied: " + bean.srcFileName);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    class SourceAdapter extends RecyclerView.Adapter<SourceAdapter.VH> {
        private final List<SrcCodeBean> items = new ArrayList<>();

        void setItems(List<SrcCodeBean> list) {
            items.clear(); items.addAll(list); notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_file_node, parent, false);
            VH vh = new VH(v);
            v.setOnClickListener(view -> {
                int p = vh.getAdapterPosition();
                if (p >= 0 && p < items.size()) openBean(items.get(p));
            });
            v.setOnLongClickListener(view -> {
                int p = vh.getAdapterPosition();
                if (p >= 0 && p < items.size()) showOptions(items.get(p));
                return true;
            });
            return vh;
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            SrcCodeBean bean = items.get(pos);
            String name = bean.srcFileName;
            boolean isXml = name.endsWith(".xml");
            boolean isKt = name.endsWith(".kt");

            // Icon
            h.tvIcon.setText(isXml ? "X" : isKt ? "K" : "J");
            int color = isXml ? 0xFF4CAF50 : isKt ? 0xFF9C27B0 : 0xFFFF9800;
            h.tvIcon.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(color));

            h.tvName.setText(name);

            // Info: package name + line count
            String info = bean.pkgName != null ? bean.pkgName : "";
            if (bean.source != null) {
                int lines = bean.source.split("\n").length;
                info = (info.isEmpty() ? "" : info + "  •  ") + lines + " lines";
            }
            h.tvInfo.setText(info);
            h.tvArrow.setVisibility(View.GONE);
            h.checkbox.setVisibility(View.GONE);
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            LinearLayout root;
            android.widget.CheckBox checkbox;
            TextView tvIcon, tvName, tvInfo, tvArrow;
            VH(View v) {
                super(v); root = (LinearLayout) v;
                checkbox = v.findViewById(R.id.checkbox);
                tvIcon   = v.findViewById(R.id.tv_icon);
                tvName   = v.findViewById(R.id.tv_name);
                tvInfo   = v.findViewById(R.id.tv_info);
                tvArrow  = v.findViewById(R.id.tv_arrow);
            }
        }
    }
}
