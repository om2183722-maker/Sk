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

public class ProjectFileManagerActivity extends BaseAppCompatActivity {

    public static final String EXTRA_SC_ID = "sc_id";

    private static final int SORT_NAME = 0, SORT_DATE = 1, SORT_SIZE = 2, SORT_EXT = 3;

    // Binary extensions — show info but don't open as text
    private static final Set<String> BINARY_EXTS = new HashSet<>(Arrays.asList(
            "class","dex","jar","aar","apk","aab","zip","png","jpg","jpeg",
            "webp","gif","ttf","otf","mp3","mp4","ogg","wav","so","bin","dat"));

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
    private final SimpleDateFormat dateFmt =
            new SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault());
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

        // Start at DATA directory (readable source files), NOT mysc (binary build output)
        String dataPath = wq.b(scId);   // .sketchware/data/sc_id
        File filesDir = new File(dataPath + "/files");
        if (filesDir.exists()) {
            navigateTo(filesDir);
        } else {
            navigateTo(new File(dataPath));
        }
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

        int SEARCH=1, SORT=2, SELECT=3, NEW_FILE=4, NEW_FOLDER=5,
                PASTE=6, GO_DATA=7, GO_JAVA=8, GO_LAYOUT=9, GO_CUSTOM=10;

        binding.toolbar.getMenu().add(0,SEARCH,0,"Search");
        binding.toolbar.getMenu().add(0,SORT,1,"Sort by");
        binding.toolbar.getMenu().add(0,SELECT,2,"Multi-select");
        binding.toolbar.getMenu().add(0,NEW_FILE,3,"New file");
        binding.toolbar.getMenu().add(0,NEW_FOLDER,4,"New folder");
        binding.toolbar.getMenu().add(0,PASTE,5,"Paste");
        binding.toolbar.getMenu().add(0,GO_DATA,6,"Go to data folder");
        binding.toolbar.getMenu().add(0,GO_JAVA,7,"Go to Java sources");
        binding.toolbar.getMenu().add(0,GO_LAYOUT,8,"Go to Layouts");
        binding.toolbar.getMenu().add(0,GO_CUSTOM,9,"Go to custom_src (your edits)");

        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id==SEARCH)    { toggleSearch(); return true; }
            if (id==SORT)      { showSortDialog(); return true; }
            if (id==SELECT)    { enterMultiSelect(); return true; }
            if (id==NEW_FILE)  { promptNew(false); return true; }
            if (id==NEW_FOLDER){ promptNew(true); return true; }
            if (id==PASTE)     { pasteFile(); return true; }
            if (id==GO_DATA)   { navigateTo(new File(wq.b(scId))); return true; }
            if (id==GO_JAVA)   { goToJava(); return true; }
            if (id==GO_LAYOUT) { goToLayout(); return true; }
            if (id==GO_CUSTOM) { goToCustomSrc(); return true; }
            return false;
        });
    }

    private void goToJava() {
        // Java sources: data/sc_id/files/java/
        File javaDir = new File(wq.b(scId) + "/files/java");
        if (!javaDir.exists()) javaDir = new File(wq.b(scId) + "/files");
        navigateTo(javaDir.exists() ? javaDir : new File(wq.b(scId)));
    }

    private void goToLayout() {
        File layoutDir = new File(wq.b(scId) + "/files/resource/layout");
        if (!layoutDir.exists()) layoutDir = new File(wq.b(scId) + "/files/resource");
        navigateTo(layoutDir.exists() ? layoutDir : new File(wq.b(scId)));
    }

    private void goToCustomSrc() {
        File customDir = new File(wq.b(scId) + "/custom_src");
        if (!customDir.exists()) {
            customDir.mkdirs();
            SketchwareUtil.toast("custom_src folder created");
        }
        navigateTo(customDir);
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private void setupSearch() {
        binding.tilSearch.setVisibility(View.GONE);
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void onTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim().toLowerCase();
                refreshList();
            }
        });
    }

    private void toggleSearch() {
        boolean vis = binding.tilSearch.getVisibility() == View.VISIBLE;
        binding.tilSearch.setVisibility(vis ? View.GONE : View.VISIBLE);
        if (vis) { searchQuery = ""; refreshList(); }
        else binding.etSearch.requestFocus();
    }

    // ── Action bar ────────────────────────────────────────────────────────────

    private void setupActionBar() {
        binding.btnSelectAll.setOnClickListener(v -> {
            for (FileNode n : adapter.getNodes())
                if (n.file != null) selectedPaths.add(n.file.getAbsolutePath());
            updateSelectionUi();
            adapter.notifyDataSetChanged();
        });
        binding.btnActionDelete.setOnClickListener(v -> confirmMultiDelete());
        binding.btnCancelSelect.setOnClickListener(v -> exitMultiSelect());
    }

    private void enterMultiSelect() {
        multiSelect = true;
        selectedPaths.clear();
        binding.actionBar.setVisibility(View.VISIBLE);
        updateSelectionUi();
        adapter.notifyDataSetChanged();
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

    private void confirmMultiDelete() {
        if (selectedPaths.isEmpty()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete " + selectedPaths.size() + " item(s)?")
                .setMessage("This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    for (String p : selectedPaths) deleteRecursive(new File(p));
                    exitMultiSelect();
                    refreshList();
                    SketchwareUtil.toast("Deleted");
                })
                .setNegativeButton("Cancel", null).show();
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
        binding.fabNew.setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Create new")
                        .setItems(new String[]{"File", "Folder"},
                                (d, w) -> promptNew(w == 1))
                        .show());
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void navigateTo(File dir) {
        if (dir == null || !dir.exists()) {
            SketchwareUtil.toastError("Folder not found");
            return;
        }
        if (currentDir != null) history.push(currentDir);
        currentDir = dir;
        refreshList();
    }

    private void refreshList() {
        if (currentDir == null) return;
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.recyclerView.setVisibility(View.GONE);
        updateBreadcrumb();

        new Thread(() -> {
            List<FileNode> nodes = buildNodes();
            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                binding.recyclerView.setVisibility(View.VISIBLE);
                adapter.setNodes(nodes);
                binding.tvEmpty.setVisibility(nodes.isEmpty() ? View.VISIBLE : View.GONE);
                // Scroll to top
                binding.recyclerView.scrollToPosition(0);
            });
        }).start();
    }

    // ── Node builder ──────────────────────────────────────────────────────────

    private List<FileNode> buildNodes() {
        List<FileNode> nodes = new ArrayList<>();
        if (!currentDir.exists()) return nodes;

        File[] files = currentDir.listFiles();
        if (files == null) return nodes;

        List<File> list = new ArrayList<>();
        for (File f : files) {
            if (searchQuery.isEmpty() ||
                    f.getName().toLowerCase().contains(searchQuery))
                list.add(f);
        }

        list.sort((a, b) -> {
            if (a.isDirectory() != b.isDirectory())
                return a.isDirectory() ? -1 : 1;
            int c = 0;
            switch (sortMode) {
                case SORT_DATE: c = Long.compare(a.lastModified(), b.lastModified()); break;
                case SORT_SIZE: c = Long.compare(a.length(), b.length()); break;
                case SORT_EXT:  c = ext(a.getName()).compareTo(ext(b.getName())); break;
                default:        c = a.getName().compareToIgnoreCase(b.getName());
            }
            return sortAsc ? c : -c;
        });

        for (File f : list) nodes.add(new FileNode(f));
        return nodes;
    }

    // ── Breadcrumb ────────────────────────────────────────────────────────────

    private void updateBreadcrumb() {
        binding.breadcrumbContainer.removeAllViews();
        if (currentDir == null) return;

        String dataPath = wq.b(scId);
        String path = currentDir.getAbsolutePath();
        String display = path.startsWith(dataPath) ? "data" + path.substring(dataPath.length()) : path;

        String[] parts = display.split("/");
        StringBuilder cum = new StringBuilder(dataPath);

        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            final String part = parts[i];
            final String dirPath = i == 0 ? dataPath : cum + "/" + part;

            TextView crumb = new TextView(this);
            crumb.setText((i > 0 ? " › " : "") + part);
            crumb.setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
            crumb.setPadding(4, 0, 4, 0);
            crumb.setOnClickListener(v -> navigateTo(new File(dirPath)));
            binding.breadcrumbContainer.addView(crumb);
            cum.append("/").append(part);
        }
        binding.breadcrumbScroll.post(() ->
                binding.breadcrumbScroll.fullScroll(View.FOCUS_RIGHT));
    }

    // ── File actions ──────────────────────────────────────────────────────────

    private void onFileClick(FileNode node) {
        if (node.file == null) return;

        if (multiSelect) {
            String p = node.file.getAbsolutePath();
            if (selectedPaths.contains(p)) selectedPaths.remove(p);
            else selectedPaths.add(p);
            updateSelectionUi();
            adapter.notifyDataSetChanged();
            return;
        }

        if (node.file.isDirectory()) {
            navigateTo(node.file);
        } else {
            String e = ext(node.file.getName());
            if (BINARY_EXTS.contains(e)) {
                showProperties(node.file);
            } else {
                openFile(node.file);
            }
        }
    }

    private void onFileLongClick(FileNode node) {
        if (node.file != null) showFileOptions(node.file);
    }

    private void openFile(File file) {
        String name = file.getName();
        String e = ext(name);

        if (e.equals("xml") && (file.getAbsolutePath().contains("/layout/")
                || file.getAbsolutePath().contains("/custom_src/xml/"))) {
            String code = FileUtil.readFile(file.getAbsolutePath());
            Intent i = new Intent(this, ViewCodeEditorActivity.class);
            i.putExtra("code", code);
            i.putExtra("sc_id", scId);
            i.putExtra("scheme", CodeViewerActivity.SCHEME_XML);
            i.putExtra("title", name);
            i.putExtra(CodeViewerActivity.EXTRA_FILENAME, name);
            startActivity(i);
        } else {
            String scheme = e.equals("xml") ? CodeViewerActivity.SCHEME_XML
                    : CodeViewerActivity.SCHEME_JAVA;
            String code = FileUtil.readFile(file.getAbsolutePath());
            Intent i = new Intent(this, CodeViewerActivity.class);
            i.putExtra("code", code);
            i.putExtra("sc_id", scId);
            i.putExtra("scheme", scheme);
            i.putExtra(CodeViewerActivity.EXTRA_FILENAME, name);
            startActivity(i);
        }
    }

    // ── File options ──────────────────────────────────────────────────────────

    private void showFileOptions(File file) {
        String e = ext(file.getName());
        boolean isBinary = BINARY_EXTS.contains(e);
        boolean isDir = file.isDirectory();

        List<String> opts = new ArrayList<>();
        if (!isDir && !isBinary) opts.add("Open / Edit");
        opts.add("Rename");
        opts.add("Copy");
        opts.add("Cut");
        if (!isDir && !isBinary) opts.add("Copy content");
        opts.add("Copy path");
        opts.add("Properties");
        opts.add("Delete");

        new MaterialAlertDialogBuilder(this)
                .setTitle(file.getName())
                .setMessage(isDir ? "Folder" : formatSize(file.length()) + "  •  " +
                        dateFmt.format(new Date(file.lastModified())))
                .setItems(opts.toArray(new String[0]), (d, w) -> {
                    switch (opts.get(w)) {
                        case "Open / Edit":  openFile(file); break;
                        case "Rename":       promptRename(file); break;
                        case "Copy":         clipboard(file, false); break;
                        case "Cut":          clipboard(file, true); break;
                        case "Copy content": copyText(FileUtil.readFile(file.getAbsolutePath()), "Content copied"); break;
                        case "Copy path":    copyText(file.getAbsolutePath(), "Path copied"); break;
                        case "Properties":   showProperties(file); break;
                        case "Delete":       confirmDelete(file); break;
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void promptRename(File file) {
        EditText et = new EditText(this);
        et.setText(file.getName()); et.selectAll();
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        new MaterialAlertDialogBuilder(this).setTitle("Rename").setView(et)
                .setPositiveButton("Rename", (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (n.isEmpty()) return;
                    File t = new File(file.getParent(), n);
                    if (file.renameTo(t)) { refreshList(); SketchwareUtil.toast("Renamed"); }
                    else SketchwareUtil.toastError("Rename failed");
                }).setNegativeButton("Cancel", null).show();
    }

    private void promptNew(boolean isFolder) {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setHint(isFolder ? "Folder name" : "File name (e.g. MyClass.java)");
        new MaterialAlertDialogBuilder(this).setTitle("New " + (isFolder ? "folder" : "file"))
                .setView(et)
                .setPositiveButton("Create", (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (n.isEmpty()) return;
                    File f = new File(currentDir, n);
                    try {
                        boolean ok = isFolder ? f.mkdirs() : f.createNewFile();
                        if (ok) { refreshList(); SketchwareUtil.toast("Created: " + n); }
                        else SketchwareUtil.toastError("Already exists");
                    } catch (Exception ex) {
                        SketchwareUtil.toastError("Failed: " + ex.getMessage());
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void clipboard(File f, boolean cut) {
        clipboardFile = f; clipboardIsCut = cut;
        SketchwareUtil.toast((cut?"Cut: ":"Copied: ") + f.getName()
                + "\nGo to destination → ⋮ → Paste");
    }

    private void pasteFile() {
        if (clipboardFile == null) { SketchwareUtil.toastError("Nothing to paste"); return; }
        File dest = new File(currentDir, clipboardFile.getName());
        try {
            if (clipboardFile.isDirectory()) copyDir(clipboardFile, dest);
            else FileUtil.copyFile(clipboardFile.getAbsolutePath(), dest.getAbsolutePath());
            if (clipboardIsCut) { deleteRecursive(clipboardFile); clipboardFile = null; }
            refreshList();
            SketchwareUtil.toast("Pasted: " + dest.getName());
        } catch (Exception e) {
            SketchwareUtil.toastError("Paste failed: " + e.getMessage());
        }
    }

    private void confirmDelete(File file) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete " + file.getName() + "?")
                .setMessage(file.isDirectory() ? "Entire folder will be deleted." : "Cannot be undone.")
                .setPositiveButton("Delete", (d,w) -> {
                    deleteRecursive(file); refreshList(); SketchwareUtil.toast("Deleted");
                }).setNegativeButton("Cancel", null).show();
    }

    private void showSortDialog() {
        String[] opts = {"Name A-Z","Name Z-A","Date newest","Date oldest",
                "Size largest","Size smallest","Extension"};
        new MaterialAlertDialogBuilder(this).setTitle("Sort by")
                .setItems(opts, (d,w) -> {
                    switch(w){
                        case 0:sortMode=SORT_NAME;sortAsc=true;break;
                        case 1:sortMode=SORT_NAME;sortAsc=false;break;
                        case 2:sortMode=SORT_DATE;sortAsc=false;break;
                        case 3:sortMode=SORT_DATE;sortAsc=true;break;
                        case 4:sortMode=SORT_SIZE;sortAsc=false;break;
                        case 5:sortMode=SORT_SIZE;sortAsc=true;break;
                        case 6:sortMode=SORT_EXT;sortAsc=true;break;
                    }
                    refreshList();
                }).show();
    }

    private void showProperties(File f) {
        StringBuilder sb = new StringBuilder();
        sb.append("Name:     ").append(f.getName()).append("\n");
        sb.append("Path:     ").append(f.getAbsolutePath()).append("\n");
        sb.append("Size:     ").append(f.isDirectory()?"(folder)":formatSize(f.length())).append("\n");
        sb.append("Modified: ").append(dateFmt.format(new Date(f.lastModified()))).append("\n");
        sb.append("Readable: ").append(f.canRead()).append("\n");
        sb.append("Writable: ").append(f.canWrite());
        if (BINARY_EXTS.contains(ext(f.getName())))
            sb.append("\n\nBinary file — not editable as text.");

        new MaterialAlertDialogBuilder(this).setTitle("Properties").setMessage(sb)
                .setPositiveButton("OK",null)
                .setNeutralButton("Copy path",(d,w)->copyText(f.getAbsolutePath(),"Path copied"))
                .show();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void deleteRecursive(File f) {
        if (f.isDirectory()) { File[] ch=f.listFiles(); if(ch!=null) for(File c:ch) deleteRecursive(c); }
        f.delete();
    }

    private void copyDir(File src, File dst) throws Exception {
        dst.mkdirs();
        File[] ch = src.listFiles();
        if (ch==null) return;
        for (File f:ch) {
            if(f.isDirectory()) copyDir(f,new File(dst,f.getName()));
            else FileUtil.copyFile(f.getAbsolutePath(),new File(dst,f.getName()).getAbsolutePath());
        }
    }

    private void copyText(String text, String toast) {
        ((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE))
                .setPrimaryClip(ClipData.newPlainText("file",text));
        SketchwareUtil.toast(toast);
    }

    private String ext(String name) {
        int i=name.lastIndexOf('.'); return i>=0?name.substring(i+1).toLowerCase():"";
    }

    private String formatSize(long b) {
        if(b<1024) return b+" B";
        if(b<1024*1024) return new DecimalFormat("0.#").format(b/1024.0)+" KB";
        return new DecimalFormat("0.#").format(b/(1024.0*1024))+" MB";
    }

    // ── Model ─────────────────────────────────────────────────────────────────

    static class FileNode {
        final File file;
        FileNode(File f) { file=f; }
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        private final List<FileNode> nodes = new ArrayList<>();

        void setNodes(List<FileNode> list) {
            nodes.clear(); nodes.addAll(list); notifyDataSetChanged();
        }

        List<FileNode> getNodes() { return nodes; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_file_node, parent, false);
            VH vh = new VH(v);
            v.setOnClickListener(view -> {
                int p = vh.getAdapterPosition();
                if (p!=RecyclerView.NO_ID && p<nodes.size()) onFileClick(nodes.get(p));
            });
            v.setOnLongClickListener(view -> {
                int p = vh.getAdapterPosition();
                if (p!=RecyclerView.NO_ID && p<nodes.size()) onFileLongClick(nodes.get(p));
                return true;
            });
            return vh;
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            File f = nodes.get(pos).file;
            if (f==null) return;

            String e = ext(f.getName());
            boolean isBin = BINARY_EXTS.contains(e);
            boolean isDir = f.isDirectory();

            // Icon text + color
            h.tvIcon.setText(iconFor(e, isDir));
            int color = colorFor(e, isDir, isBin);
            h.tvIcon.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(color));

            h.tvName.setText(f.getName());
            h.tvName.setAlpha(isBin ? 0.5f : 1f);

            if (isDir) {
                File[] ch = f.listFiles();
                int cnt = ch!=null?ch.length:0;
                h.tvInfo.setText(cnt+" items  •  "+dateFmt.format(new Date(f.lastModified())));
                h.tvArrow.setVisibility(View.VISIBLE);
            } else {
                String sizeStr = isBin ? formatSize(f.length())+" (binary)" : formatSize(f.length());
                h.tvInfo.setText(sizeStr+"  •  "+dateFmt.format(new Date(f.lastModified())));
                h.tvArrow.setVisibility(View.GONE);
            }

            h.checkbox.setVisibility(multiSelect?View.VISIBLE:View.GONE);
            if (multiSelect) h.checkbox.setChecked(selectedPaths.contains(f.getAbsolutePath()));

            h.root.setBackgroundColor(selectedPaths.contains(f.getAbsolutePath())?0x206200EE:0);
        }

        private String iconFor(String e, boolean dir) {
            if(dir) return "D";
            switch(e){
                case "java": return "J"; case "kt": return "K";
                case "xml":  return "X"; case "gradle": return "G";
                case "json": return "{}"; case "png": case "jpg":
                case "webp": return "IMG"; case "svg": return "SVG";
                case "class": return "C"; case "dex": return "DEX";
                default: return e.isEmpty()?"?":e.substring(0,1).toUpperCase();
            }
        }

        private int colorFor(String e, boolean dir, boolean bin) {
            if(bin) return 0xFF9E9E9E;
            if(dir) return 0xFF2196F3;
            switch(e){
                case "java":   return 0xFFFF9800;
                case "kt":     return 0xFF9C27B0;
                case "xml":    return 0xFF4CAF50;
                case "gradle": return 0xFF009688;
                case "json":   return 0xFFFF5722;
                case "svg": case "png": case "jpg": return 0xFFE91E63;
                default:       return 0xFF607D8B;
            }
        }

        @Override public int getItemCount() { return nodes.size(); }

        class VH extends RecyclerView.ViewHolder {
            LinearLayout root; CheckBox checkbox;
            TextView tvIcon,tvName,tvInfo,tvArrow;
            VH(View v){
                super(v); root=(LinearLayout)v;
                checkbox=v.findViewById(R.id.checkbox);
                tvIcon=v.findViewById(R.id.tv_icon);
                tvName=v.findViewById(R.id.tv_name);
                tvInfo=v.findViewById(R.id.tv_info);
                tvArrow=v.findViewById(R.id.tv_arrow);
            }
        }
    }
}
