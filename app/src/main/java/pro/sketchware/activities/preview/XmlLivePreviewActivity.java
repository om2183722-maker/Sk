package pro.sketchware.activities.preview;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.hey.studios.util.Helper;
import pro.sketchware.databinding.ActivityXmlLivePreviewBinding;
import pro.sketchware.utility.ManualLayoutInflater;
import pro.sketchware.utility.UI;

/**
 * Live XML Preview using ManualLayoutInflater — builds a real View hierarchy
 * directly from the XML string without needing a compiled resource parser.
 *
 * Works on all API levels.
 */
public class XmlLivePreviewActivity extends BaseAppCompatActivity {

    public static final String EXTRA_XML   = "xml";
    public static final String EXTRA_TITLE = "title";

    private ActivityXmlLivePreviewBinding binding;
    private String originalXml;
    private boolean isPhoneFrame = true;

    private static final int MENU_REFRESH  = Menu.FIRST;
    private static final int MENU_FRAME    = Menu.FIRST + 1;
    private static final int MENU_SHOW_XML = Menu.FIRST + 2;
    private static final int MENU_THEME    = Menu.FIRST + 3;

    private MenuItem menuFrame;
    private MenuItem menuTheme;
    private boolean isDarkBg = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityXmlLivePreviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        originalXml = getIntent().getStringExtra(EXTRA_XML);
        String title = getIntent().getStringExtra(EXTRA_TITLE);

        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.toolbar.setTitle(title != null ? title : "Live Preview");
        binding.toolbar.setSubtitle("Live render");

        Menu menu = binding.toolbar.getMenu();
        menu.add(Menu.NONE, MENU_REFRESH,  0, "↺ Refresh");
        menuFrame = menu.add(Menu.NONE, MENU_FRAME, 1, "Phone frame: ON");
        menuTheme = menu.add(Menu.NONE, MENU_THEME, 2, "BG: Light");
        menu.add(Menu.NONE, MENU_SHOW_XML, 3, "Show cleaned XML");

        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == MENU_REFRESH)  { inflatePreview(); return true; }
            if (id == MENU_FRAME)    { toggleFrame(); return true; }
            if (id == MENU_THEME)    { toggleBackground(); return true; }
            if (id == MENU_SHOW_XML) { showCleanedXml(); return true; }
            return false;
        });

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.previewContainer, false, false, false, true);

        inflatePreview();
    }

    // ── Inflation ─────────────────────────────────────────────────────────────

    private void inflatePreview() {
        binding.previewContainer.removeAllViews();
        binding.errorCard.setVisibility(View.GONE);

        if (originalXml == null || originalXml.trim().isEmpty()) {
            showError("No XML content provided.");
            return;
        }

        String cleaned = sanitizeXml(originalXml);

        new Thread(() -> {
            try {
                ManualLayoutInflater inflater = new ManualLayoutInflater(this);
                View inflated = inflater.inflate(cleaned);

                List<String> warns = inflater.warnings;

                runOnUiThread(() -> {
                    binding.previewContainer.removeAllViews();
                    binding.previewContainer.addView(inflated,
                            new android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
                    updatePhoneFrame();

                    if (!warns.isEmpty()) {
                        showSnack(warns.size() + " element(s) approximated — tap ⋮ for details");
                    } else {
                        showSnack("Rendered ✓");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(
                        "Render failed:\n" + e.getMessage()
                        + "\n\nTap ⋮ → 'Show cleaned XML' to debug."));
            }
        }).start();
    }

    // ── XML sanitizer ─────────────────────────────────────────────────────────

    public static String sanitizeXml(String xml) {
        if (xml == null) return "";
        String s = xml;

        s = s.replaceAll("xmlns:tools=\"[^\"]*\"", "");
        s = s.replaceAll("tools:[a-zA-Z_0-9]+=\"[^\"]*\"", "");
        s = s.replaceAll("android:id=\"@\\+?id/[^\"]*\"", "");
        s = s.replaceAll("@(?:android:)?color/[a-zA-Z0-9_]+", "#9E9E9E");
        s = s.replaceAll("@(?:drawable|mipmap)/[a-zA-Z0-9_]+", "");
        s = s.replaceAll("@android:drawable/[a-zA-Z0-9_]+", "");
        s = replaceStringRefs(s);
        s = s.replaceAll("@(?:android:)?dimen/[a-zA-Z0-9_]+", "8dp");
        s = s.replaceAll("(?:android|app):[a-zA-Z_0-9]+=\"@style/[^\"]*\"", "");
        s = s.replaceAll("style=\"@style/[^\"]*\"", "");
        s = s.replaceAll("@(?:android:)?bool/[a-zA-Z0-9_]+", "true");
        s = s.replaceAll("@(?:android:)?integer/[a-zA-Z0-9_]+", "0");
        s = s.replaceAll("[a-zA-Z]+:[a-zA-Z_0-9]+=\"\\?[^\"]*\"", "");
        s = s.replaceAll("[a-zA-Z]+:[a-zA-Z_0-9]+=\"@(anim|font|array|xml|raw)/[^\"]*\"", "");
        s = s.replaceAll("<include[^>]*/?>", "<!-- include removed -->");
        s = s.replaceAll("<merge([^>]*)>", "<FrameLayout$1>");
        s = s.replaceAll("</merge>", "</FrameLayout>");
        s = s.replaceAll("app:layout_constraint[A-Za-z_0-9]+=\"[^\"]*\"", "");
        s = s.replaceAll("app:layout_editor[A-Za-z_0-9]+=\"[^\"]*\"", "");
        s = s.replaceAll("app:[a-zA-Z_0-9]+=\"[^\"]*\"", "");
        return s;
    }

    private static String replaceStringRefs(String xml) {
        StringBuffer sb = new StringBuffer();
        Matcher m = Pattern.compile("@string/([a-zA-Z0-9_]+)").matcher(xml);
        while (m.find()) m.appendReplacement(sb, m.group(1).replace("_", " "));
        m.appendTail(sb);
        return sb.toString();
    }

    // ── UI controls ───────────────────────────────────────────────────────────

    private void toggleFrame() {
        isPhoneFrame = !isPhoneFrame;
        menuFrame.setTitle("Phone frame: " + (isPhoneFrame ? "ON" : "OFF"));
        updatePhoneFrame();
    }

    private void toggleBackground() {
        isDarkBg = !isDarkBg;
        menuTheme.setTitle("BG: " + (isDarkBg ? "Dark" : "Light"));
        binding.previewContainer.setBackgroundColor(isDarkBg ? Color.BLACK : Color.WHITE);
    }

    private void updatePhoneFrame() {
        binding.phoneFrame.setVisibility(isPhoneFrame ? View.VISIBLE : View.GONE);
        binding.previewContainer.setBackgroundColor(isDarkBg ? Color.BLACK : Color.WHITE);
    }

    private void showError(String msg) {
        binding.errorCard.setVisibility(View.VISIBLE);
        binding.tvError.setText(msg);
    }

    private void showCleanedXml() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Cleaned XML")
                .setMessage(sanitizeXml(originalXml))
                .setPositiveButton("Close", null)
                .show();
    }

    private void showSnack(String msg) {
        Snackbar.make(binding.getRoot(), msg, Snackbar.LENGTH_SHORT).show();
    }
}
