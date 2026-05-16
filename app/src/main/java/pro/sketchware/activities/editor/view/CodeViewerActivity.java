package pro.sketchware.activities.editor.view;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;

import a.a.a.Lx;
import a.a.a.wq;
import mod.hey.studios.util.Helper;
import mod.jbk.util.LogUtil;
import pro.sketchware.databinding.ActivityCodeViewerBinding;
import pro.sketchware.utility.EditorUtils;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

public class CodeViewerActivity extends BaseAppCompatActivity {

    public static final String SCHEME_XML      = "xml";
    public static final String SCHEME_JAVA     = "java";
    public static final String EXTRA_FILENAME  = "filename";
    public static final String CUSTOM_SRC_DIR  = "custom_src";

    private static final String TAG = "CodeViewerActivity";

    private static final int MENU_EDIT      = Menu.FIRST;
    private static final int MENU_SAVE      = Menu.FIRST + 1;
    private static final int MENU_COPY      = Menu.FIRST + 2;
    private static final int MENU_WORDWRAP  = Menu.FIRST + 3;
    private static final int MENU_RESET     = Menu.FIRST + 4;
    private static final int MENU_SET_FILE  = Menu.FIRST + 5;

    private ActivityCodeViewerBinding binding;
    private boolean isEditMode   = false;
    private boolean isWordWrap   = false;
    private boolean hasCustomSrc = false;

    private String scId;
    private String filename;
    private String scheme;
    private String customSrcFilePath;

    private MenuItem menuEdit, menuSave, menuReset, menuWordWrap;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ActivityCodeViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String code = getIntent().getStringExtra("code");
        scheme      = getIntent().getStringExtra("scheme");
        scId        = getIntent().getStringExtra("sc_id");
        filename    = getIntent().getStringExtra(EXTRA_FILENAME);

        // Auto-detect filename from code if not provided
        if (filename == null && code != null) {
            filename = tryDetectFilename(code, scheme);
        }

        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        updateToolbarTitle();

        // Build toolbar menu programmatically
        Menu menu = binding.toolbar.getMenu();
        menuEdit     = menu.add(Menu.NONE, MENU_EDIT,     0, "✎ Edit");
        menuSave     = menu.add(Menu.NONE, MENU_SAVE,     1, "Save");
        menuWordWrap = menu.add(Menu.NONE, MENU_WORDWRAP, 2, "Word wrap: OFF");
        menu.add(Menu.NONE, MENU_COPY,    3, "Copy all");
        menuReset    = menu.add(Menu.NONE, MENU_RESET,    4, "Reset to generated");
        menu.add(Menu.NONE, MENU_SET_FILE,5, "Set filename for saving");

        menuSave.setVisible(false);
        menuReset.setVisible(false);

        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == MENU_EDIT)     { toggleEdit(); return true; }
            if (id == MENU_SAVE)     { saveEdits(); return true; }
            if (id == MENU_COPY)     { copyAll(); return true; }
            if (id == MENU_WORDWRAP) { toggleWordWrap(); return true; }
            if (id == MENU_RESET)    { confirmReset(); return true; }
            if (id == MENU_SET_FILE) { promptFilename(); return true; }
            return false;
        });

        // Editor setup
        binding.editor.setTypefaceText(EditorUtils.getTypeface(this));
        binding.editor.setTextSize(14);
        binding.editor.setWordwrap(false);
        binding.editor.setEditable(false);
        loadColorScheme(scheme);

        // Load custom_src override if exists
        rebuildCustomSrcPath();
        if (hasCustomSrc) {
            code = FileUtil.readFile(customSrcFilePath);
            menuReset.setVisible(true);
            showSnack("Showing your saved edits");
        }

        binding.editor.setText(code != null ? Lx.j(code, false) : "");

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.editor, true, false, true, true);
    }

    // ── Edit mode ─────────────────────────────────────────────────────────────

    private void toggleEdit() {
        isEditMode = !isEditMode;
        binding.editor.setEditable(isEditMode);
        menuEdit.setTitle(isEditMode ? "✎ Editing" : "✎ Edit");
        menuSave.setVisible(isEditMode);
        if (isEditMode) showSnack("Edit mode ON — tap Save when done");
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void saveEdits() {
        if (scId == null || filename == null) {
            // Ask user to set filename
            promptFilename();
            return;
        }
        String content = binding.editor.getText().toString();
        try {
            File parent = new File(customSrcFilePath).getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileUtil.writeFile(customSrcFilePath, content);
            hasCustomSrc = true;
            menuReset.setVisible(true);
            showSnack("Saved ✓ — applied on next build");
            LogUtil.d(TAG, "Saved: " + customSrcFilePath);
        } catch (Exception e) {
            SketchwareUtil.toastError("Save failed: " + e.getMessage());
        }
    }

    /** Ask user to type the filename if it wasn't passed by caller */
    private void promptFilename() {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setHint("e.g. MainActivity.java or activity_main.xml");
        if (filename != null) et.setText(filename);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Set filename for saving")
                .setMessage("Enter the filename so edits can be saved to custom_src:")
                .setView(et)
                .setPositiveButton("Set", (d, w) -> {
                    String entered = et.getText().toString().trim();
                    if (!entered.isEmpty()) {
                        filename = entered;
                        // Auto-detect scheme from extension
                        if (scheme == null) {
                            scheme = filename.endsWith(".xml") ? SCHEME_XML : SCHEME_JAVA;
                        }
                        rebuildCustomSrcPath();
                        updateToolbarTitle();
                        showSnack("Filename set — now tap Save");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void rebuildCustomSrcPath() {
        if (scId == null || filename == null) { hasCustomSrc = false; return; }
        customSrcFilePath = wq.b(scId) + File.separator + CUSTOM_SRC_DIR
                + File.separator
                + (SCHEME_JAVA.equals(scheme) ? "java" : "xml")
                + File.separator + filename;
        hasCustomSrc = FileUtil.isExistFile(customSrcFilePath);
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    private void confirmReset() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Reset to generated code?")
                .setMessage("Your saved edits for \"" + filename + "\" will be deleted.")
                .setPositiveButton("Reset", (d, w) -> {
                    if (customSrcFilePath != null) {
                        new File(customSrcFilePath).delete();
                        hasCustomSrc = false;
                        menuReset.setVisible(false);
                        showSnack("Reset ✓");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Copy / Wordwrap ───────────────────────────────────────────────────────

    private void copyAll() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(filename != null ? filename : "code",
                binding.editor.getText().toString()));
        showSnack("Copied to clipboard");
    }

    private void toggleWordWrap() {
        isWordWrap = !isWordWrap;
        binding.editor.setWordwrap(isWordWrap);
        menuWordWrap.setTitle("Word wrap: " + (isWordWrap ? "ON" : "OFF"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void updateToolbarTitle() {
        binding.toolbar.setTitle(filename != null ? filename : "Code Viewer");
        binding.toolbar.setSubtitle(scId);
    }

    /**
     * Try to detect filename from the code content.
     * Looks for "class ClassName" or "activity_xxx" patterns.
     */
    private String tryDetectFilename(String code, String scheme) {
        if (code == null) return null;
        try {
            if (SCHEME_XML.equals(scheme)) {
                // Look for root element or activity_ prefix clues
                return null; // can't reliably detect XML filename
            }
            // Java: look for "class ClassName" or "public class ClassName"
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?:public\\s+)?class\\s+(\\w+)")
                    .matcher(code);
            if (m.find()) {
                return m.group(1) + ".java";
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void loadColorScheme(String sc) {
        if (SCHEME_XML.equals(sc)) {
            EditorUtils.loadXmlConfig(binding.editor);
        } else {
            EditorUtils.loadJavaConfig(binding.editor);
        }
    }

    private void showSnack(String msg) {
        Snackbar.make(binding.getRoot(), msg, Snackbar.LENGTH_SHORT).show();
    }
}
