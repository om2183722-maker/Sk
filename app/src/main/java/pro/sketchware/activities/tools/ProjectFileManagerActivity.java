package pro.sketchware.activities.tools;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import a.a.a.wq;
import mod.hey.studios.util.Helper;
import pro.sketchware.activities.editor.view.CodeViewerActivity;
import pro.sketchware.activities.editor.view.ViewCodeEditorActivity;
import pro.sketchware.databinding.ActivityProjectFileManagerBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

public class ProjectFileManagerActivity extends BaseAppCompatActivity {

    public static final String EXTRA_SC_ID = "sc_id";

    private ActivityProjectFileManagerBinding binding;
    private FileTreeAdapter adapter;
    private String scId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityProjectFileManagerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        scId = getIntent().getStringExtra(EXTRA_SC_ID);
        if (scId == null) { finish(); return; }

        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.toolbar.setTitle("Project Files");
        binding.toolbar.setSubtitle("sc_id: " + scId);

        binding.toolbar.getMenu()
                .add(0, 1, 0, "Refresh")
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) { loadTree(); return true; }
            return false;
        });

        adapter = new FileTreeAdapter();
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.recyclerView, false, false, false, true);

        loadTree();
    }

    // ── Tree loading ──────────────────────────────────────────────────────────

    private void loadTree() {
        binding.progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            List<FileNode> nodes = buildTree();
            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                adapter.setNodes(nodes);
                binding.tvEmpty.setVisibility(nodes.isEmpty() ? View.VISIBLE : View.GONE);
            });
        }).start();
    }

    private List<FileNode> buildTree() {
        List<FileNode> list = new ArrayList<>();
        String data  = wq.b(scId);
        String mysc  = wq.c(scId);

        // Manifests
        list.add(new FileNode(0, "manifests", null, true, true, "AndroidManifest"));
        addFile(list, 1, mysc + "/app/src/main/AndroidManifest.xml");

        // Java
        list.add(new FileNode(0, "java", null, true, true, "Java & Kotlin"));
        addDir(list, new File(mysc + "/app/src/main/java"), 1);

        // Custom edits
        File customJava = new File(data + "/custom_src/java");
        if (customJava.exists()) {
            list.add(new FileNode(0, "java (your edits)", null, true, true, "Saved edits"));
            addDir(list, customJava, 1);
        }

        // Layouts
        list.add(new FileNode(0, "res / layout", null, true, true, "XML Layouts"));
        addDir(list, new File(mysc + "/app/src/main/res/layout"), 1);

        File customXml = new File(data + "/custom_src/xml");
        if (customXml.exists()) {
            list.add(new FileNode(0, "res / layout (your edits)", null, true, true, "Saved edits"));
            addDir(list, customXml, 1);
        }

        // Drawables
        list.add(new FileNode(0, "res / drawable", null, true, true, "Images & Vectors"));
        addDir(list, new File(mysc + "/app/src/main/res/drawable"), 1);
        addDir(list, new File(mysc + "/app/src/main/res/drawable-xhdpi"), 1);

        // Values
        list.add(new FileNode(0, "res / values", null, true, true, "Strings, Colors, Styles"));
        addDir(list, new File(mysc + "/app/src/main/res/values"), 1);

        // Other res folders
        for (String folder : new String[]{"raw", "font", "anim", "menu"}) {
            File d = new File(mysc + "/app/src/main/res/" + folder);
            if (d.exists()) {
                list.add(new FileNode(0, "res / " + folder, null, true, true, ""));
                addDir(list, d, 1);
            }
        }

        // Assets
        File assets = new File(mysc + "/app/src/main/assets");
        if (assets.exists()) {
            list.add(new FileNode(0, "assets", null, true, true, "Asset Files"));
            addDir(list, assets, 1);
        }

        // Gradle
        list.add(new FileNode(0, "Gradle Scripts", null, true, true, "Build config"));
        addFile(list, 1, mysc + "/app/build.gradle");
        addFile(list, 1, mysc + "/build.gradle");
        addFile(list, 1, mysc + "/gradle.properties");

        return list;
    }

    private void addDir(List<FileNode> out, File dir, int depth) {
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() != b.isDirectory())
                return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        for (File f : files) {
            out.add(new FileNode(depth, f.getName(), f.getAbsolutePath(),
                    f.isDirectory(), false, ""));
            if (f.isDirectory()) addDir(out, f, depth + 1);
        }
    }

    private void addFile(List<FileNode> out, int depth, String path) {
        File f = new File(path);
        if (f.exists()) out.add(new FileNode(depth, f.getName(), path, false, false, ""));
    }

    // ── File actions ──────────────────────────────────────────────────────────

    private void openFile(FileNode node) {
        if (node.isHeader || node.isDirectory || node.path == null) return;
        String name = node.name;
        String ext  = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";

        if (ext.equals("java") || ext.equals("kt")
                || ext.equals("gradle") || ext.equals("properties")
                || ext.equals("json") || ext.equals("txt")) {
            openInCodeViewer(node.path, name, CodeViewerActivity.SCHEME_JAVA);
        } else if (ext.equals("xml")) {
            if (node.path.contains("/layout/") || node.path.contains("/custom_src/xml/")) {
                // Layout → ViewCodeEditor (has Live Preview)
                String code = FileUtil.readFile(node.path);
                Intent i = new Intent(this, ViewCodeEditorActivity.class);
                i.putExtra("code", code);
                i.putExtra("sc_id", scId);
                i.putExtra("scheme", CodeViewerActivity.SCHEME_XML);
                i.putExtra("title", name);
                i.putExtra(CodeViewerActivity.EXTRA_FILENAME, name);
                startActivity(i);
            } else {
                openInCodeViewer(node.path, name, CodeViewerActivity.SCHEME_XML);
            }
        } else {
            SketchwareUtil.toast("Cannot preview ." + ext + " files");
        }
    }

    private void openInCodeViewer(String path, String name, String scheme) {
        String code = FileUtil.readFile(path);
        Intent i = new Intent(this, CodeViewerActivity.class);
        i.putExtra("code", code);
        i.putExtra("sc_id", scId);
        i.putExtra("scheme", scheme);
        i.putExtra(CodeViewerActivity.EXTRA_FILENAME, name);
        startActivity(i);
    }

    private void showFileOptions(FileNode node) {
        if (node.isHeader || node.path == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(node.name)
                .setItems(node.isDirectory
                        ? new String[]{"Cancel"}
                        : new String[]{"Open / Edit", "Delete"},
                        (d, which) -> {
                            if (which == 0 && !node.isDirectory) openFile(node);
                            else if (which == 1) confirmDelete(node);
                        })
                .show();
    }

    private void confirmDelete(FileNode node) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete " + node.name + "?")
                .setMessage("This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    if (new File(node.path).delete()) {
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
        final String name, path, subtitle;
        final boolean isDirectory, isHeader;

        FileNode(int d, String n, String p, boolean dir, boolean hdr, String sub) {
            depth = d; name = n; path = p;
            isDirectory = dir; isHeader = hdr; subtitle = sub;
        }
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    class FileTreeAdapter extends RecyclerView.Adapter<FileTreeAdapter.VH> {
        private final List<FileNode> nodes = new ArrayList<>();

        void setNodes(List<FileNode> list) {
            nodes.clear();
            nodes.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Inflate from XML instead of building programmatically
            View row = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.two_line_list_item, parent, false);
            return new VH(row);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            FileNode node = nodes.get(pos);
            float density = getResources().getDisplayMetrics().density;
            int indent = (int)(node.depth * 16 * density);
            int pad = (int)(8 * density);
            h.root.setPadding(indent + pad, pad, pad, pad);

            if (node.isHeader) {
                h.title.setText(node.name.toUpperCase());
                h.title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                h.title.setAlpha(0.5f);
                h.title.setTextSize(11);
                h.subtitle.setText(node.subtitle);
                h.subtitle.setVisibility(node.subtitle.isEmpty() ? View.GONE : View.VISIBLE);
                h.root.setBackgroundColor(0x08000000);
                h.root.setOnClickListener(null);
                h.root.setOnLongClickListener(null);
            } else {
                String icon = node.isDirectory ? "▶  " : iconFor(node.name) + "  ";
                h.title.setText(icon + node.name);
                h.title.setTypeface(node.isDirectory
                        ? android.graphics.Typeface.DEFAULT_BOLD
                        : android.graphics.Typeface.MONOSPACE);
                h.title.setAlpha(1f);
                h.title.setTextSize(13);
                h.subtitle.setVisibility(View.GONE);
                h.root.setBackground(null);
                h.root.setClickable(true);
                h.root.setFocusable(true);
                h.root.setOnClickListener(v -> openFile(node));
                h.root.setOnLongClickListener(v -> { showFileOptions(node); return true; });
            }
        }

        private String iconFor(String name) {
            if (name.endsWith(".java"))   return "J";
            if (name.endsWith(".kt"))     return "K";
            if (name.endsWith(".xml"))    return "X";
            if (name.endsWith(".gradle")) return "G";
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".webp")) return "I";
            if (name.endsWith(".svg"))    return "V";
            if (name.endsWith(".json"))   return "{}";
            return "-";
        }

        @Override
        public int getItemCount() { return nodes.size(); }

        class VH extends RecyclerView.ViewHolder {
            View root;
            TextView title, subtitle;

            VH(View v) {
                super(v);
                root = v;
                title    = v.findViewById(android.R.id.text1);
                subtitle = v.findViewById(android.R.id.text2);
            }
        }
    }
}
