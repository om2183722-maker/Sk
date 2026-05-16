package pro.sketchware.activities.tools;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import a.a.a.wq;
import a.a.a.yq;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.activities.editor.view.CodeViewerActivity;
import pro.sketchware.activities.editor.view.ViewCodeEditorActivity;
import pro.sketchware.databinding.ActivityProjectFileManagerBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

/**
 * Android Studio-style project file manager.
 * Shows the full project file tree — Java/Kotlin sources, layouts,
 * drawables, assets, manifests, Gradle scripts — and lets the user
 * open and edit any file directly.
 */
public class ProjectFileManagerActivity extends BaseAppCompatActivity {

    public static final String EXTRA_SC_ID = "sc_id";

    private ActivityProjectFileManagerBinding binding;
    private FileTreeAdapter adapter;
    private String scId;
    private yq projectPaths;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityProjectFileManagerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        scId = getIntent().getStringExtra(EXTRA_SC_ID);
        if (scId == null) { finish(); return; }

        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.toolbar.setTitle("Project");
        binding.toolbar.setSubtitle("sc_id: " + scId);

        // Refresh button
        binding.toolbar.getMenu().add(0, 1, 0, "↺ Refresh")
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) { loadTree(); return true; }
            return false;
        });

        adapter = new FileTreeAdapter();
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
        binding.recyclerView.addItemDecoration(
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.recyclerView, false, false, false, true);

        loadTree();
    }

    // ── Tree loading ──────────────────────────────────────────────────────────

    private void loadTree() {
        binding.progressBar.setVisibility(View.VISIBLE);
        adapter.clear();

        new Thread(() -> {
            List<FileNode> nodes = buildFileTree();
            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                adapter.setNodes(nodes);
                if (nodes.isEmpty()) {
                    binding.tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    binding.tvEmpty.setVisibility(View.GONE);
                }
            });
        }).start();
    }

    private List<FileNode> buildFileTree() {
        List<FileNode> nodes = new ArrayList<>();

        // Base paths from wq (Sketchware path utilities)
        String dataPath  = wq.b(scId);    // .sketchware/data/sc_id
        String myscPath  = wq.c(scId);    // .sketchware/mysc/sc_id

        // ── Manifests ─────────────────────────────────────────────────────────
        FileNode manifests = header("manifests", "📄");
        nodes.add(manifests);
        addFileIfExists(nodes, 1, myscPath + "/app/src/main/AndroidManifest.xml", "AndroidManifest.xml");

        // ── Java / Kotlin sources ─────────────────────────────────────────────
        FileNode javaHeader = header("java", "☕");
        nodes.add(javaHeader);
        String javaRoot = myscPath + "/app/src/main/java";
        addRecursive(nodes, new File(javaRoot), 1, javaRoot);

        // Custom source overrides (user's edits)
        String customSrcJava = dataPath + "/custom_src/java";
        if (new File(customSrcJava).exists()) {
            nodes.add(header("java (custom_src — your edits)", "✎"));
            addRecursive(nodes, new File(customSrcJava), 1, customSrcJava);
        }

        // ── Res ───────────────────────────────────────────────────────────────
        String resRoot = myscPath + "/app/src/main/res";

        // Layout
        FileNode layoutHeader = header("res/layout", "🖼");
        nodes.add(layoutHeader);
        addRecursive(nodes, new File(resRoot + "/layout"), 1, resRoot + "/layout");

        // Custom XML overrides
        String customSrcXml = dataPath + "/custom_src/xml";
        if (new File(customSrcXml).exists()) {
            nodes.add(header("res/layout (custom_src)", "✎"));
            addRecursive(nodes, new File(customSrcXml), 1, customSrcXml);
        }

        // Drawable
        nodes.add(header("res/drawable", "🎨"));
        addRecursive(nodes, new File(resRoot + "/drawable"), 1, resRoot + "/drawable");
        addRecursive(nodes, new File(resRoot + "/drawable-xhdpi"), 1, resRoot + "/drawable-xhdpi");

        // Values
        nodes.add(header("res/values", "📝"));
        addRecursive(nodes, new File(resRoot + "/values"), 1, resRoot + "/values");

        // Raw / Font / Other res
        addResIfExists(nodes, resRoot, "raw");
        addResIfExists(nodes, resRoot, "font");
        addResIfExists(nodes, resRoot, "anim");
        addResIfExists(nodes, resRoot, "menu");

        // ── Assets ────────────────────────────────────────────────────────────
        String assetsRoot = myscPath + "/app/src/main/assets";
        if (new File(assetsRoot).exists()) {
            nodes.add(header("assets", "📦"));
            addRecursive(nodes, new File(assetsRoot), 1, assetsRoot);
        }

        // ── Gradle Scripts ────────────────────────────────────────────────────
        nodes.add(header("Gradle Scripts", "🐘"));
        addFileIfExists(nodes, 1, myscPath + "/app/build.gradle", "build.gradle (Module: app)");
        addFileIfExists(nodes, 1, myscPath + "/build.gradle",     "build.gradle (Project)");
        addFileIfExists(nodes, 1, myscPath + "/gradle.properties","gradle.properties");

        return nodes;
    }

    private void addRecursive(List<FileNode> out, File dir, int depth, String baseRoot) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        for (File f : files) {
            if (f.isDirectory()) {
                out.add(new FileNode(depth, f.getName(), f.getAbsolutePath(), true, false));
                addRecursive(out, f, depth + 1, baseRoot);
            } else {
                out.add(new FileNode(depth, f.getName(), f.getAbsolutePath(), false, false));
            }
        }
    }

    private void addFileIfExists(List<FileNode> out, int depth, String path, String displayName) {
        if (new File(path).exists()) {
            out.add(new FileNode(depth, displayName, path, false, false));
        }
    }

    private void addResIfExists(List<FileNode> out, String resRoot, String folder) {
        File dir = new File(resRoot + "/" + folder);
        if (dir.exists()) {
            out.add(header("res/" + folder, "📁"));
            addRecursive(out, dir, 1, dir.getAbsolutePath());
        }
    }

    private FileNode header(String name, String icon) {
        return new FileNode(0, icon + "  " + name, null, true, true);
    }

    // ── File open ─────────────────────────────────────────────────────────────

    private void openFile(FileNode node) {
        if (node.isHeader || node.isDirectory) return;
        String path = node.absolutePath;
        String name = node.name;

        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')+1).toLowerCase() : "";

        switch (ext) {
            case "java":
            case "kt": {
                String code = FileUtil.readFile(path);
                Intent i = new Intent(this, CodeViewerActivity.class);
                i.putExtra("code", code);
                i.putExtra("sc_id", scId);
                i.putExtra("scheme", CodeViewerActivity.SCHEME_JAVA);
                i.putExtra(CodeViewerActivity.EXTRA_FILENAME, name);
                startActivity(i);
                break;
            }
            case "xml": {
                if (name.equals("AndroidManifest.xml") || path.contains("/values/")) {
                    // Open in code viewer for direct editing
                    String code = FileUtil.readFile(path);
                    Intent i = new Intent(this, CodeViewerActivity.class);
                    i.putExtra("code", code);
                    i.putExtra("sc_id", scId);
                    i.putExtra("scheme", CodeViewerActivity.SCHEME_XML);
                    i.putExtra(CodeViewerActivity.EXTRA_FILENAME, name);
                    startActivity(i);
                } else if (path.contains("/layout/") || path.contains("/custom_src/xml/")) {
                    // Layout XML → open in ViewCodeEditor (has Live Preview)
                    String code = FileUtil.readFile(path);
                    Intent i = new Intent(this, ViewCodeEditorActivity.class);
                    i.putExtra("code", code);
                    i.putExtra("sc_id", scId);
                    i.putExtra("scheme", CodeViewerActivity.SCHEME_XML);
                    i.putExtra("title", name);
                    i.putExtra(CodeViewerActivity.EXTRA_FILENAME, name);
                    startActivity(i);
                } else {
                    String code = FileUtil.readFile(path);
                    Intent i = new Intent(this, CodeViewerActivity.class);
                    i.putExtra("code", code);
                    i.putExtra("sc_id", scId);
                    i.putExtra("scheme", CodeViewerActivity.SCHEME_XML);
                    i.putExtra(CodeViewerActivity.EXTRA_FILENAME, name);
                    startActivity(i);
                }
                break;
            }
            case "gradle":
            case "properties":
            case "txt":
            case "json":
            case "md": {
                String code = FileUtil.readFile(path);
                Intent i = new Intent(this, CodeViewerActivity.class);
                i.putExtra("code", code);
                i.putExtra("sc_id", scId);
                i.putExtra("scheme", CodeViewerActivity.SCHEME_JAVA);
                i.putExtra(CodeViewerActivity.EXTRA_FILENAME, name);
                startActivity(i);
                break;
            }
            default:
                SketchwareUtil.toast("Cannot preview: " + ext + " file");
        }
    }

    private void longPressFile(FileNode node) {
        if (node.isHeader || node.isDirectory || node.absolutePath == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(node.name)
                .setItems(new String[]{"Open", "Delete"}, (d, which) -> {
                    if (which == 0) openFile(node);
                    else confirmDelete(node);
                })
                .show();
    }

    private void confirmDelete(FileNode node) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete file?")
                .setMessage("Delete \"" + node.name + "\"?\nThis cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    if (new File(node.absolutePath).delete()) {
                        SketchwareUtil.toast("Deleted: " + node.name);
                        loadTree();
                    } else {
                        SketchwareUtil.toastError("Delete failed");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Data model ────────────────────────────────────────────────────────────

    static class FileNode {
        final int depth;
        final String name;
        final String absolutePath;
        final boolean isDirectory;
        final boolean isHeader;
        boolean expanded = true;

        FileNode(int depth, String name, String path, boolean dir, boolean header) {
            this.depth = depth; this.name = name; this.absolutePath = path;
            this.isDirectory = dir; this.isHeader = header;
        }
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    class FileTreeAdapter extends RecyclerView.Adapter<FileTreeAdapter.VH> {
        private final List<FileNode> nodes = new ArrayList<>();

        void setNodes(List<FileNode> list) {
            nodes.clear(); nodes.addAll(list);
            notifyDataSetChanged();
        }

        void clear() { nodes.clear(); notifyDataSetChanged(); }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT));

            int dp8 = (int)(8 * getResources().getDisplayMetrics().density);
            row.setPadding(dp8, dp8/2, dp8, dp8/2);

            ImageView icon = new ImageView(parent.getContext());
            icon.setId(R.id.image);
            int sz = (int)(20 * getResources().getDisplayMetrics().density);
            icon.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
            row.addView(icon);

            TextView tv = new TextView(parent.getContext());
            tv.setId(R.id.title);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = dp8;
            tv.setLayoutParams(lp);
            row.addView(tv);

            return new VH(row, icon, tv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            FileNode node = nodes.get(pos);
            int dp = (int)(getResources().getDisplayMetrics().density);
            int indent = node.depth * 16 * dp;

            h.root.setPadding(indent + 8*dp, 6*dp, 8*dp, 6*dp);

            if (node.isHeader) {
                h.tv.setText(node.name);
                h.tv.setTextSize(12);
                h.tv.setTypeface(null, android.graphics.Typeface.BOLD);
                h.tv.setAlpha(0.6f);
                h.icon.setVisibility(View.GONE);
                h.root.setBackgroundColor(0x08000000);
            } else if (node.isDirectory) {
                h.tv.setText("📁  " + node.name);
                h.tv.setTextSize(13);
                h.tv.setTypeface(null, android.graphics.Typeface.BOLD);
                h.tv.setAlpha(1f);
                h.icon.setVisibility(View.GONE);
                h.root.setBackground(null);
            } else {
                String icon = iconForFile(node.name);
                h.tv.setText(icon + "  " + node.name);
                h.tv.setTextSize(13);
                h.tv.setTypeface(android.graphics.Typeface.MONOSPACE);
                h.tv.setAlpha(1f);
                h.icon.setVisibility(View.GONE);
                h.root.setBackground(null);
            }

            h.root.setOnClickListener(v -> openFile(node));
            h.root.setOnLongClickListener(v -> { longPressFile(node); return true; });
        }

        private String iconForFile(String name) {
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')+1).toLowerCase() : "";
            switch (ext) {
                case "java":  return "☕";
                case "kt":    return "🅺";
                case "xml":   return "📄";
                case "gradle": return "🐘";
                case "json":  return "{}";
                case "png": case "jpg": case "svg": case "webp": return "🖼";
                case "ttf": case "otf": return "🔤";
                case "mp3": case "ogg": return "🎵";
                case "mp4": return "🎬";
                default:      return "📄";
            }
        }

        @Override public int getItemCount() { return nodes.size(); }

        class VH extends RecyclerView.ViewHolder {
            LinearLayout root; ImageView icon; TextView tv;
            VH(LinearLayout root, ImageView icon, TextView tv) {
                super(root); this.root = root; this.icon = icon; this.tv = tv;
            }
        }
    }
}
