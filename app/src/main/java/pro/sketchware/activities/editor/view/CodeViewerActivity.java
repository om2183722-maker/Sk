package pro.sketchware.activities.editor.view;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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

    /** Pass the source file name (e.g. "MainActivity.java") so edits can be saved. */
    public static final String EXTRA_FILENAME = "filename";

    /**
     * Folder inside .sketchware/data/<sc_id>/ where user edits are persisted.
     * ProjectBuilder applies these overrides before compilation.
     */
    public static final String CUSTOM_SRC_DIR = "custom_src";

    private static final String TAG = "CodeViewerActivity";

    private ActivityCodeViewerBinding binding;
    private boolean isEditMode   = false;
    private boolean isWordWrap   = false;
    private boolean hasCustomSrc = false;

    private String scId;
    private String filename;
    private String scheme;
    private String customSrcFilePath;

    // Menu item IDs
    private static final int MENU_COPY      = 1;
    private static final int MENU_WORDWRAP  = 2;
    private static final int MENU_RESET     = 3;

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
        binding.toolbar.setSubtitle(filename != null ? filename : scId);

        // ── Overflow menu ─────────────────────────────────────────────────────
        binding.toolbar.inflateMenu(R.menu.menu_code_viewer);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == MENU_COPY)     { copyToClipboard(); return true; }
            if (id == MENU_WORDWRAP) { toggleWordWrap(item); return true; }
            if (id == MENU_RESET)    { confirmResetToGenerated(); return true; }
            return false;
        });
        // Add menu items programmatically (no XML menu needed)
        Menu menu = binding.toolbar.getMenu();
        menu.add(Menu.NONE, MENU_COPY,     0, "Copy all");
        menu.add(Menu.NONE, MENU_WORDWRAP, 1, "Word wrap: OFF");
        MenuItem resetItem = menu.add(Menu.NONE, MENU_RESET, 2, "Reset to generated");
        resetItem.setVisible(false); // shown only when custom_src exists

        // ── Editor setup ──────────────────────────────────────────────────────
        binding.editor.setTypefaceText(EditorUtils.getTypeface(this));
        binding.editor.setTextSize(14);
        binding.editor.setWordwrap(false);
        loadColorScheme(scheme);

        // Check for custom_src override
        if (scId != null && filename != null) {
            customSrcFilePath = buildCustomSrcPath(scId, filename, scheme);
            hasCustomSrc = FileUtil.isExistFile(customSrcFilePath);
            if (hasCustomSrc) {
                code = FileUtil.readFile(customSrcFilePath);
                resetItem.setVisible(true);
                SketchwareUtil.toast("Showing your saved edits");
            }
        }

        binding.editor.setText(Lx.j(code, false));
        setEditMode(false);

        // ── Edit toggle button ────────────────────────────────────────────────
        binding.btnEditToggle.setOnClickListener(v -> setEditMode(!isEditMode));

        // ── Save button ───────────────────────────────────────────────────────
        binding.btnSave.setOnClickListener(v -> saveEdits());

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.editor, true, false, true, true);
    }

    // ── Edit mode ─────────────────────────────────────────────────────────────

    private void setEditMode(boolean edit) {
        isEditMode = edit;
        binding.editor.setEditable(edit);
        binding.btnSave.setVisibility(edit ? View.VISIBLE : View.GONE);
        binding.tvReadonlyHint.setVisibility(edit ? View.GONE : View.VISIBLE);
        binding.tvEditHint.setVisibility(edit ? View.VISIBLE : View.GONE);
        binding.btnEditToggle.setAlpha(edit ? 1f : 0.6f);
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void saveEdits() {
        if (scId == null || filename == null) {
            SketchwareUtil.toastError("Cannot save: filename missing.\n"
                    + "Open this file from the Design screen to enable saving.");
            return;
        }
        String content = binding.editor.getText().toString();
        try {
            File parent = new File(customSrcFilePath).getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileUtil.writeFile(customSrcFilePath, content);
            hasCustomSrc = true;
            // Show reset option since we now have a custom override
            binding.toolbar.getMenu().findItem(MENU_RESET).setVisible(true);
            SketchwareUtil.toast("Saved ✓ — will be applied on next build");
            LogUtil.d(TAG, "Saved: " + customSrcFilePath);
        } catch (Exception e) {
            SketchwareUtil.toastError("Save failed: " + e.getMessage());
        }
    }

    // ── Reset to generated ────────────────────────────────────────────────────

    private void confirmResetToGenerated() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Reset to generated code?")
                .setMessage("Your saved edits for \"" + filename + "\" will be deleted.\n"
                        + "The original Sketchware-generated code will be used on next build.")
                .setPositiveButton("Reset", (d, w) -> resetToGenerated())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void resetToGenerated() {
        if (customSrcFilePath != null && FileUtil.isExistFile(customSrcFilePath)) {
            new File(customSrcFilePath).delete();
            hasCustomSrc = false;
            binding.toolbar.getMenu().findItem(MENU_RESET).setVisible(false);
            SketchwareUtil.toast("Reset ✓ — generated code will be used on next build");
        }
    }

    // ── Copy all ──────────────────────────────────────────────────────────────

    private void copyToClipboard() {
        String text = binding.editor.getText().toString();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(filename != null ? filename : "code", text));
        SketchwareUtil.toast("Copied to clipboard");
    }

    // ── Word wrap toggle ──────────────────────────────────────────────────────

    private void toggleWordWrap(MenuItem item) {
        isWordWrap = !isWordWrap;
        binding.editor.setWordwrap(isWordWrap);
        item.setTitle("Word wrap: " + (isWordWrap ? "ON" : "OFF"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildCustomSrcPath(String scId, String filename, String scheme) {
        String base = wq.b(scId) + File.separator + CUSTOM_SRC_DIR;
        if (SCHEME_JAVA.equals(scheme)) {
            return base + File.separator + "java" + File.separator + filename;
        } else {
            return base + File.separator + "xml" + File.separator + filename;
        }
    }

    private void loadColorScheme(String scheme) {
        if (SCHEME_XML.equals(scheme)) {
            EditorUtils.loadXmlConfig(binding.editor);
        } else {
            EditorUtils.loadJavaConfig(binding.editor);
        }
    }
}
