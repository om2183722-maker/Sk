package pro.sketchware.activities.editor.view;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.Nullable;

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

    public static final String SCHEME_XML  = "xml";
    public static final String SCHEME_JAVA = "java";
    public static final String EXTRA_FILENAME = "filename";
    public static final String CUSTOM_SRC_DIR = "custom_src";

    private static final String TAG = "CodeViewerActivity";

    // Toolbar menu item IDs
    private static final int MENU_EDIT     = Menu.FIRST;
    private static final int MENU_SAVE     = Menu.FIRST + 1;
    private static final int MENU_COPY     = Menu.FIRST + 2;
    private static final int MENU_WORDWRAP = Menu.FIRST + 3;
    private static final int MENU_RESET    = Menu.FIRST + 4;

    private ActivityCodeViewerBinding binding;
    private boolean isEditMode  = false;
    private boolean isWordWrap  = false;
    private boolean hasCustomSrc = false;

    private String scId;
    private String filename;
    private String scheme;
    private String customSrcFilePath;

    private MenuItem menuEdit;
    private MenuItem menuSave;
    private MenuItem menuReset;
    private MenuItem menuWordWrap;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ActivityCodeViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String code = getIntent().getStringExtra("code");
        scheme      = getIntent().getStringExtra("scheme");
        scId        = getIntent().getStringExtra("sc_id");
        filename    = getIntent().getStringExtra(EXTRA_FILENAME);

        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.toolbar.setTitle(filename != null ? filename : "Code Viewer");
        binding.toolbar.setSubtitle(scId);

        // ── Build toolbar menu programmatically (no menu XML needed) ──────────
        Menu menu = binding.toolbar.getMenu();
        menuEdit     = menu.add(Menu.NONE, MENU_EDIT,     0, "✎ Edit");
        menuSave     = menu.add(Menu.NONE, MENU_SAVE,     1, "💾 Save");
        menuWordWrap = menu.add(Menu.NONE, MENU_WORDWRAP, 2, "Word wrap: OFF");
        MenuItem menuCopy = menu.add(Menu.NONE, MENU_COPY, 3, "Copy all");
        menuReset    = menu.add(Menu.NONE, MENU_RESET,   4, "Reset to generated");

        menuSave.setVisible(false);
        menuReset.setVisible(false);

        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == MENU_EDIT)     { toggleEditMode(); return true; }
            if (id == MENU_SAVE)     { saveEdits(); return true; }
            if (id == MENU_COPY)     { copyToClipboard(); return true; }
            if (id == MENU_WORDWRAP) { toggleWordWrap(); return true; }
            if (id == MENU_RESET)    { confirmReset(); return true; }
            return false;
        });

        // ── Editor setup ──────────────────────────────────────────────────────
        binding.editor.setTypefaceText(EditorUtils.getTypeface(this));
        binding.editor.setTextSize(14);
        binding.editor.setWordwrap(false);
        loadColorScheme(scheme);
        binding.editor.setEditable(false);

        // Load custom_src override if it exists
        if (scId != null && filename != null) {
            customSrcFilePath = buildCustomSrcPath(scId, filename, scheme);
            hasCustomSrc = FileUtil.isExistFile(customSrcFilePath);
            if (hasCustomSrc) {
                code = FileUtil.readFile(customSrcFilePath);
                menuReset.setVisible(true);
                showSnack("Showing your saved edits");
            }
        }

        binding.editor.setText(Lx.j(code, false));

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.editor, true, false, true, true);
    }

    // ── Edit mode ─────────────────────────────────────────────────────────────

    private void toggleEditMode() {
        isEditMode = !isEditMode;
        binding.editor.setEditable(isEditMode);
        menuEdit.setTitle(isEditMode ? "✎ Editing" : "✎ Edit");
        menuSave.setVisible(isEditMode);
        if (isEditMode) {
            showSnack("Edit mode ON — tap 💾 Save when done");
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void saveEdits() {
        if (scId == null || filename == null) {
            SketchwareUtil.toastError("Cannot save: open from Design screen to enable saving");
            return;
        }
        String content = binding.editor.getText().toString();
        try {
            File parent = new File(customSrcFilePath).getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileUtil.writeFile(customSrcFilePath, content);
            hasCustomSrc = true;
            menuReset.setVisible(true);
            showSnack("Saved ✓  —  applied on next build");
            LogUtil.d(TAG, "Saved: " + customSrcFilePath);
        } catch (Exception e) {
            SketchwareUtil.toastError("Save failed: " + e.getMessage());
        }
    }

    // ── Reset to generated ────────────────────────────────────────────────────

    private void confirmReset() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Reset to generated code?")
                .setMessage("Your saved edits for \"" + filename
                        + "\" will be deleted. Sketchware-generated code will be used on next build.")
                .setPositiveButton("Reset", (d, w) -> {
                    if (customSrcFilePath != null) {
                        new File(customSrcFilePath).delete();
                        hasCustomSrc = false;
                        menuReset.setVisible(false);
                        showSnack("Reset ✓  —  generated code will be used");
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ── Copy all ──────────────────────────────────────────────────────────────

    private void copyToClipboard() {
        String text = binding.editor.getText().toString();
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(filename != null ? filename : "code", text));
        showSnack("Copied to clipboard");
    }

    // ── Word wrap ─────────────────────────────────────────────────────────────

    private void toggleWordWrap() {
        isWordWrap = !isWordWrap;
        binding.editor.setWordwrap(isWordWrap);
        menuWordWrap.setTitle("Word wrap: " + (isWordWrap ? "ON" : "OFF"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showSnack(String msg) {
        Snackbar.make(binding.getRoot(), msg, Snackbar.LENGTH_SHORT).show();
    }

    private String buildCustomSrcPath(String scId, String filename, String scheme) {
        String base = wq.b(scId) + File.separator + CUSTOM_SRC_DIR;
        return base + File.separator
                + (SCHEME_JAVA.equals(scheme) ? "java" : "xml")
                + File.separator + filename;
    }

    private void loadColorScheme(String scheme) {
        if (SCHEME_XML.equals(scheme)) {
            EditorUtils.loadXmlConfig(binding.editor);
        } else {
            EditorUtils.loadJavaConfig(binding.editor);
        }
    }
}
