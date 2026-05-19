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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Stack;

import a.a.a.wq;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.activities.editor.view.CodeViewerActivity;
import pro.sketchware.activities.editor.view.ViewCodeEditorActivity;
import pro.sketchware.databinding.ActivityProjectFileManagerBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

/**
 * MT Manager-style project file manager.
 * Full file operations: browse, edit, rename, delete, copy, multi-select, search, new file/folder.
 */
public class ProjectFileManagerActivity extends BaseAppCompatActivity {

    public static final String EXTRA_SC_ID = "sc_id";

    // Sort modes
    private static final int SORT_NAME = 0;
    private static final int SORT_DATE = 1;
    private static final int SORT_SIZE = 2;
    private static final int SORT_EXT  = 3;

    private ActivityProjectFileManagerBinding binding;
    private FileAdapter adapter;
    private String scId;
    private File currentDir;
    private int sortMode = SORT_NAME;
    private boolean sortAsc = true;
    private boolean multiSelect = false;
    private final Set<String> selectedPaths = new HashSet<>();
    private String searchQuery = "";
    private final Stack<File> history = new Stack<>();
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());

    // Clipboard for copy/paste
    private File clipboardFile = null;
    private boolean clipboardIsCut = false;

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
        setupActionBar();
        setupRecyclerView();
        setupFab();

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.recyclerView, false, false, false, true);

        // Start at project root
        navigateTo(new File(wq.c(scId)));
    }

    @Override
    public void onBackPressed() {
        if (multiSelect) {
            exitMultiSelect();
        } else if (!history.isEmpty()) {
            currentDir = history.pop();
            refreshList();
        } else {
            super.onBackPressed();
        }
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());

        int MENU_SEARCH = 1, MENU_SORT = 2, MENU_NEW_FILE = 3,
                MENU_NEW_FOLDER = 4, MENU_PASTE = 5, MENU_SELECT = 6,
                MENU_SHOW_DATA = 7;

        binding.toolbar.getMenu().add(0, MENU_SEARCH,    0, "Search");
        binding.toolbar.getMenu().add(0, MENU_SORT,      1, "Sort by");
        binding.toolbar.getMenu().add(0, MENU_SELECT,    2, "Multi-select");
        binding.toolbar.getMenu().add(0, MENU_NEW_FILE,  3, "New file");
        binding.toolbar.getMenu().add(0, MENU_NEW_FOLDER,4, "New folder");
        binding.toolbar.getMenu().add(0, MENU_PASTE,     5, "Paste");
        binding.toolbar.getMenu().add(0, MENU_SHOW_DATA, 6, "Open data folder");

        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == MENU_SEARCH) {
                toggleSearch(); return true;
            }
            if (id == MENU_SORT) {
                showSortDialog(); return true;
            }
            if (id == MENU_SELECT) {
                enterMultiSelect(); return true;
            }
            if (id == MENU_NEW_FILE) {
                promptNewFile(); return true;
            }
            if (id == MENU_NEW_FOLDER) {
                promptNewFolder(); return true;
            }
            if (id == MENU_PASTE) {
                pasteFile(); return true;
            }
            if (id == MENU_SHOW_DATA) {
                navigateTo(new File(wq.b(scId))); return true;
            }
            return false;
        });
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private void setupSearch() {
        binding.tilSearch.setVisibility(View.GONE);
        if (binding.etSearch != null) {
            binding.etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(Editable s) {
                    searchQuery = s.toString().trim().toLowerCase();
                    refreshList();
                }
            });
        }
    }

    private void toggleSearch() {
        boolean visible = binding.tilSearch.getVisibility() == View.VISIBLE;
        binding.tilSearch.setVisibility(visible ? View.GONE : View.VISIBLE);
        if (visible) { searchQuery = ""; refreshList(); }
    }

    // ── Action bar (multi-select) ──────────────────────────────────────────────

    private void setupActionBar() {
        binding.btnSelectAll.setOnClickListener(v -> {
            if (adapter != null) {
                List<FileNode> nodes = adapter.getCurrentNodes();
                for (FileNode n : nodes) {
                    if (!n.isSection && n.file != null)
                        selectedPaths.add(n.file.getAbsolutePath());
                }
                updateSelectionUi();
                adapter.notifyDataSetChanged();
            }
        });

        binding.btnActionDelete.setOnClickListener(v -> {
            if (selectedPaths.isEmpty()) return;
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Delete " + selectedPaths.size() + " item(s)?")
                    .setMessage("This cannot be undone.")
                    .setPositiveButton("Delete", (d, w) -> {
                        for (String path : selectedPaths) {
                            deleteRecursive(new File(path));
                        }
                        exitMultiSelect();
                        refreshList();
                        SketchwareUtil.toast("Deleted");
                    })
                    .setNegativeButton("Cancel", null).show();
        });

        binding.btnCancelSelect.setOnClickListener(v -> exitMultiSelect());
    }

    private void enterMultiSelect() {
        multiSelect = true;
        selectedPaths.clear();
        binding.actionBar.setVisibility(View.VISIBLE);
        adapter.notifyDataSetChanged();
        updateSelectionUi();
    }

    private void exitMultiSelect() {
        multiSelect = false;
        selectedPaths.clear();
        binding.actionBar.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void updateSelectionUi() {
        binding.tvSelectedCount.setText(selectedPaths.size() + " selected");
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private void setupRecyclerView() {
        adapter = new FileAdapter();
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
        binding.recyclerView.addItemDecoration(
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
    }

    // ── FAB ───────────────────────────────────────────────────────────────────

    private void setupFab() {
        binding.fabNew.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Create new")
                    .setItems(new String[]{"File", "Folder"}, (d, which) -> {
                        if (which == 0) promptNewFile();
                        else promptNewFolder();
                    }).show();
        });
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void navigateTo(File dir) {
        if (currentDir != null) history.push(currentDir);
        currentDir = dir;
        refreshList();
    }

    private void refreshList() {
        if (currentDir == null) return;
        binding.progressBar.setVisibility(View.VISIBLE);
        updateBreadcrumb();

        new Thread(() -> {
            List<FileNode> nodes = buildNodes();
            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                adapter.setNodes(nodes);
                binding.tvEmpty.setVisibility(nodes.isEmpty() ? View.VISIBLE : View.GONE);
            });
        }).start();
    }

    // ── Node builder ──────────────────────────────────────────────────────────

    private List<FileNode> buildNodes() {
        List<FileNode> nodes = new ArrayList<>();
        if (currentDir == null || !currentDir.exists()) return nodes;

        File[] files = currentDir.listFiles();
        if (files == null) return nodes;

        // Filter by search
        List<File> filtered = new ArrayList<>();
        for (File f : files) {
            if (searchQuery.isEmpty() ||
                    f.getName().toLowerCase().contains(searchQuery)) {
                filtered.add(f);
            }
        }

        // Sort
        filtered.sort((a, b) -> {
            // Directories first
            if (a.isDirectory() != b.isDirectory())
                return a.isDirectory() ? -1 : 1;
            int cmp = 0;
            switch (sortMode) {
                case SORT_DATE: cmp = Long.compare(a.lastModified(), b.lastModified()); break;
                case SORT_SIZE: cmp = Long.compare(a.length(), b.length()); break;
                case SORT_EXT:  cmp = getExt(a.getName()).compareTo(getExt(b.getName())); break;
                default:        cmp = a.getName().compareToIgnoreCase(b.getName());
            }
            return sortAsc ? cmp : -cmp;
        });

        for (File f : filtered) {
            nodes.add(new FileNode(f, false));
        }
        return nodes;
    }

    // ── Breadcrumb ────────────────────────────────────────────────────────────

    private void updateBreadcrumb() {
        binding.breadcrumbContainer.removeAllViews();
        if (currentDir == null) return;

        String path = currentDir.getAbsolutePath();
        // Replace known roots with short labels
        String dataPath = wq.b(scId);
        String myscPath = wq.c(scId);
        String display = path;
        if (path.startsWith(myscPath)) display = "mysc" + path.substring(myscPath.length());
        else if (path.startsWith(dataPath)) display = "data" + path.substring(dataPath.length());

        String[] parts = display.split("/");
        StringBuilder cumulative = new StringBuilder(path.startsWith(myscPath) ? myscPath
                : path.startsWith(dataPath) ? dataPath : "/");

        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            String part = parts[i];
            String finalCumulative = cumulative.toString();

            TextView crumb = new TextView(this);
            crumb.setText(i == 0 ? part : " / " + part);
            crumb.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
            crumb.setPadding(4, 0, 4, 0);

            File dirForCrumb = new File(finalCumulative + (i == 0 ? "" : "/" + part));
            crumb.setOnClickListener(v -> navigateTo(dirForCrumb));
            binding.breadcrumbContainer.addView(crumb);

            if (i > 0) cumulative.append("/").append(part);
            else cumulative = new StringBuilder(finalCumulative);
        }

        // Scroll to end
        binding.breadcrumbScroll.post(() ->
                binding.breadcrumbScroll.fullScroll(View.FOCUS_RIGHT));
    }

    // ── File actions ──────────────────────────────────────────────────────────

    private void onFileClick(FileNode node) {
        if (node.file == null) return;

        if (multiSelect) {
            String path = node.file.getAbsolutePath();
            if (selectedPaths.contains(path)) selectedPaths.remove(path);
            else selectedPaths.add(path);
            updateSelectionUi();
            adapter.notifyDataSetChanged();
            return;
        }

        if (node.file.isDirectory()) {
            navigateTo(node.file);
        } else {
            openFile(node.file);
        }
    }

    private void onFileLongClick(FileNode node) {
        if (node.file == null) return;
        showFileOptions(node.file);
    }

    private void openFile(File file) {
        String name = file.getName();
        String ext  = getExt(name);

        switch (ext) {
            case "java": case "kt": case "gradle": case "properties":
            case "json": case "txt": case "md": case "pro":
                launchCodeViewer(file, CodeViewerActivity.SCHEME_JAVA);
                break;

            case "xml":
                if (file.getAbsolutePath().contains("/layout/") ||
                        file.getAbsolutePath().contains("/custom_src/xml/")) {
                    // Layout XML → ViewCodeEditor (has Live Preview button)
                    String code = FileUtil.readFile(file.getAbsolutePath());
                    Intent i = new Intent(this, ViewCodeEditorActivity.class);
                    i.putExtra("code", code);
                    i.putExtra("sc_id", scId);
                    i.putExtra("scheme", CodeViewerActivity.SCHEME_XML);
                    i.putExtra("title", name);
                    i.putExtra(CodeViewerActivity.EXTRA_FILENAME, name);
                    startActivity(i);
                } else {
                    launchCodeViewer(file, CodeViewerActivity.SCHEME_XML);
                }
                break;

            case "png": case "jpg": case "jpeg": case "webp": case "svg":
                SketchwareUtil.toast("Image preview coming soon");
                break;

            default:
                // Try as text anyway
                launchCodeViewer(file, CodeViewerActivity.SCHEME_JAVA);
        }
    }

    private void launchCodeViewer(File file, String scheme) {
        String code = FileUtil.readFile(file.getAbsolutePath());
        Intent i = new Intent(this, CodeViewerActivity.class);
        i.putExtra("code", code);
        i.putExtra("sc_id", scId);
        i.putExtra("scheme", scheme);
        i.putExtra(CodeViewerActivity.EXTRA_FILENAME, file.getName());
        startActivity(i);
    }

    // ── File options dialog ───────────────────────────────────────────────────

    private void showFileOptions(File file) {
        String size = file.isDirectory() ? "Folder" : formatSize(file.length());
        String date = dateFmt.format(new Date(file.lastModified()));
        String info = size + "  •  " + date;

        List<String> options = new ArrayList<>();
        if (!file.isDirectory()) options.add("Open / Edit");
        options.add("Rename");
        options.add("Copy");
        options.add("Cut");
        if (!file.isDirectory()) options.add("Copy path");
        if (!file.isDirectory()) options.add("Copy content");
        options.add("Properties");
        options.add("Delete");

        new MaterialAlertDialogBuilder(this)
                .setTitle(file.getName())
                .setMessage(info)
                .setItems(options.toArray(new String[0]), (d, which) -> {
                    String action = options.get(which);
                    switch (action) {
                        case "Open / Edit":   openFile(file); break;
                        case "Rename":        promptRename(file); break;
                        case "Copy":          clipboard(file, false); break;
                        case "Cut":           clipboard(file, true); break;
                        case "Copy path":     copyText(file.getAbsolutePath(), "Path copied"); break;
                        case "Copy content":
                            copyText(FileUtil.readFile(file.getAbsolutePath()), "Content copied"); break;
                        case "Properties":    showProperties(file); break;
                        case "Delete":        confirmDelete(file); break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Rename ────────────────────────────────────────────────────────────────

    private void promptRename(File file) {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText(file.getName());
        et.selectAll();

        new MaterialAlertDialogBuilder(this)
                .setTitle("Rename")
                .setView(et)
                .setPositiveButton("Rename", (d, w) -> {
                    String newName = et.getText().toString().trim();
                    if (newName.isEmpty()) return;
                    File target = new File(file.getParent(), newName);
                    if (file.renameTo(target)) {
                        refreshList();
                        SketchwareUtil.toast("Renamed to " + newName);
                    } else {
                        SketchwareUtil.toastError("Rename failed");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── New file / folder ─────────────────────────────────────────────────────

    private void promptNewFile() {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setHint("e.g. MyClass.java");

        new MaterialAlertDialogBuilder(this)
                .setTitle("New file")
                .setView(et)
                .setPositiveButton("Create", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) return;
                    File newFile = new File(currentDir, name);
                    try {
                        if (newFile.createNewFile()) {
                            refreshList();
                            SketchwareUtil.toast("Created: " + name);
                        } else {
                            SketchwareUtil.toastError("File already exists");
                        }
                    } catch (Exception e) {
                        SketchwareUtil.toastError("Failed: " + e.getMessage());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptNewFolder() {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setHint("Folder name");

        new MaterialAlertDialogBuilder(this)
                .setTitle("New folder")
                .setView(et)
                .setPositiveButton("Create", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) return;
                    File dir = new File(currentDir, name);
                    if (dir.mkdirs()) {
                        refreshList();
                        SketchwareUtil.toast("Created: " + name);
                    } else {
                        SketchwareUtil.toastError("Failed to create folder");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Copy / Cut / Paste ────────────────────────────────────────────────────

    private void clipboard(File file, boolean cut) {
        clipboardFile = file;
        clipboardIsCut = cut;
        SketchwareUtil.toast((cut ? "Cut: " : "Copied: ") + file.getName()
                + "\nNavigate to destination and use Paste from menu");
    }

    private void pasteFile() {
        if (clipboardFile == null) {
            SketchwareUtil.toastError("Nothing to paste");
            return;
        }
        File dest = new File(currentDir, clipboardFile.getName());
        try {
            if (clipboardFile.isDirectory()) {
                copyDir(clipboardFile, dest);
            } else {
                FileUtil.copyFile(clipboardFile.getAbsolutePath(), dest.getAbsolutePath());
            }
            if (clipboardIsCut) {
                deleteRecursive(clipboardFile);
                clipboardFile = null;
            }
            refreshList();
            SketchwareUtil.toast("Pasted: " + dest.getName());
        } catch (Exception e) {
            SketchwareUtil.toastError("Paste failed: " + e.getMessage());
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    private void confirmDelete(File file) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete " + file.getName() + "?")
                .setMessage(file.isDirectory()
                        ? "This will delete the entire folder and its contents."
                        : "This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    deleteRecursive(file);
                    refreshList();
                    SketchwareUtil.toast("Deleted");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }

    // ── Sort ──────────────────────────────────────────────────────────────────

    private void showSortDialog() {
        String[] options = {"Name (A-Z)", "Name (Z-A)", "Date (newest)", "Date (oldest)",
                "Size (largest)", "Size (smallest)", "Extension"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Sort by")
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: sortMode = SORT_NAME; sortAsc = true;  break;
                        case 1: sortMode = SORT_NAME; sortAsc = false; break;
                        case 2: sortMode = SORT_DATE; sortAsc = false; break;
                        case 3: sortMode = SORT_DATE; sortAsc = true;  break;
                        case 4: sortMode = SORT_SIZE; sortAsc = false; break;
                        case 5: sortMode = SORT_SIZE; sortAsc = true;  break;
                        case 6: sortMode = SORT_EXT;  sortAsc = true;  break;
                    }
                    refreshList();
                })
                .show();
    }

    // ── Properties ───────────────────────────────────────────────────────────

    private void showProperties(File file) {
        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(file.getName()).append("\n");
        sb.append("Path: ").append(file.getAbsolutePath()).append("\n");
        sb.append("Size: ").append(file.isDirectory() ? "—" : formatSize(file.length())).append("\n");
        sb.append("Modified: ").append(dateFmt.format(new Date(file.lastModified()))).append("\n");
        sb.append("Readable: ").append(file.canRead()).append("\n");
        sb.append("Writable: ").append(file.canWrite());

        new MaterialAlertDialogBuilder(this)
                .setTitle("Properties")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .setNeutralButton("Copy path", (d, w) ->
                        copyText(file.getAbsolutePath(), "Path copied"))
                .show();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void copyText(String text, String toast) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("file", text));
        SketchwareUtil.toast(toast);
    }

    private void copyDir(File src, File dst) throws Exception {
        dst.mkdirs();
        File[] files = src.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) copyDir(f, new File(dst, f.getName()));
            else FileUtil.copyFile(f.getAbsolutePath(), new File(dst, f.getName()).getAbsolutePath());
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return new DecimalFormat("0.#").format(bytes / 1024.0) + " KB";
        return new DecimalFormat("0.#").format(bytes / (1024.0 * 1024)) + " MB";
    }

    private String getExt(String name) {
        int idx = name.lastIndexOf('.');
        return idx >= 0 ? name.substring(idx + 1).toLowerCase() : "";
    }

    // ── Data model ────────────────────────────────────────────────────────────

    static class FileNode {
        final File file;
        final boolean isSection;
        final String sectionTitle;

        FileNode(File file, boolean isSection) {
            this.file = file;
            this.isSection = isSection;
            this.sectionTitle = null;
        }

        FileNode(String title) {
            this.file = null;
            this.isSection = true;
            this.sectionTitle = title;
        }
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        private final List<FileNode> nodes = new ArrayList<>();
        private final List<FileNode> allNodes = new ArrayList<>();

        void setNodes(List<FileNode> list) {
            allNodes.clear();
            allNodes.addAll(list);
            nodes.clear();
            nodes.addAll(list);
            notifyDataSetChanged();
        }

        List<FileNode> getCurrentNodes() { return nodes; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_file_node, parent, false);
            VH vh = new VH(v);
            v.setOnClickListener(view -> {
                int pos = vh.getAdapterPosition();
                if (pos != RecyclerView.NO_ID && pos < nodes.size())
                    onFileClick(nodes.get(pos));
            });
            v.setOnLongClickListener(view -> {
                int pos = vh.getAdapterPosition();
                if (pos != RecyclerView.NO_ID && pos < nodes.size())
                    onFileLongClick(nodes.get(pos));
                return true;
            });
            return vh;
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            FileNode node = nodes.get(pos);
            if (node.file == null) { h.tvName.setText("?"); return; }
            File f = node.file;

            // Icon + colors
            String ext = getExt(f.getName());
            h.tvIcon.setText(iconFor(ext, f.isDirectory()));
            h.tvIcon.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(colorFor(ext, f.isDirectory())));

            h.tvName.setText(f.getName());

            // Info line
            if (f.isDirectory()) {
                int count = f.listFiles() != null ? f.listFiles().length : 0;
                h.tvInfo.setText(count + " items  •  " + dateFmt.format(new Date(f.lastModified())));
                h.tvArrow.setVisibility(View.VISIBLE);
            } else {
                h.tvInfo.setText(formatSize(f.length()) + "  •  " +
                        dateFmt.format(new Date(f.lastModified())));
                h.tvArrow.setVisibility(View.GONE);
            }

            // Multi-select
            h.checkbox.setVisibility(multiSelect ? View.VISIBLE : View.GONE);
            if (multiSelect) {
                h.checkbox.setChecked(selectedPaths.contains(f.getAbsolutePath()));
            }

            // Selected background
            h.root.setBackgroundColor(
                    selectedPaths.contains(f.getAbsolutePath()) ? 0x206200EE : 0);
        }

        private String iconFor(String ext, boolean dir) {
            if (dir) return "D";
            switch (ext) {
                case "java":  return "J";
                case "kt":    return "K";
                case "xml":   return "X";
                case "gradle":return "G";
                case "png": case "jpg": case "webp": return "I";
                case "svg":   return "V";
                case "json":  return "{}";
                case "pro":   return "P";
                default:      return ext.isEmpty() ? "?" : ext.substring(0,1).toUpperCase();
            }
        }

        private int colorFor(String ext, boolean dir) {
            if (dir) return 0xFF2196F3;
            switch (ext) {
                case "java":  return 0xFFFF9800;
                case "kt":    return 0xFF9C27B0;
                case "xml":   return 0xFF4CAF50;
                case "gradle":return 0xFF009688;
                case "png": case "jpg": case "webp": case "svg": return 0xFFE91E63;
                case "json":  return 0xFFFF5722;
                default:      return 0xFF607D8B;
            }
        }

        @Override public int getItemCount() { return nodes.size(); }

        class VH extends RecyclerView.ViewHolder {
            LinearLayout root;
            CheckBox checkbox;
            TextView tvIcon, tvName, tvInfo, tvArrow;

            VH(View v) {
                super(v);
                root     = (LinearLayout) v;
                checkbox = v.findViewById(R.id.checkbox);
                tvIcon   = v.findViewById(R.id.tv_icon);
                tvName   = v.findViewById(R.id.tv_name);
                tvInfo   = v.findViewById(R.id.tv_info);
                tvArrow  = v.findViewById(R.id.tv_arrow);
            }
        }
    }
}
