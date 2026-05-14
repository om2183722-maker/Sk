package pro.sketchware.activities.preview;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.hey.studios.util.Helper;
import pro.sketchware.databinding.ActivityXmlLivePreviewBinding;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

/**
 * Live XML Preview — inflates the actual layout XML using Android's LayoutInflater
 * giving pixel-accurate rendering instead of Sketchware's custom ViewPane approximation.
 *
 * Handles unresolvable project-specific resource references by replacing them
 * with safe defaults before inflation.
 */
public class XmlLivePreviewActivity extends BaseAppCompatActivity {

    public static final String EXTRA_XML   = "xml";
    public static final String EXTRA_TITLE = "title";

    private ActivityXmlLivePreviewBinding binding;
    private String originalXml;
    private boolean isPhoneFrame = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityXmlLivePreviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        originalXml = getIntent().getStringExtra(EXTRA_XML);
        String title = getIntent().getStringExtra(EXTRA_TITLE);

        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.toolbar.setTitle(title != null ? title : "Live Preview");
        binding.toolbar.setSubtitle("LayoutInflater render");

        // Toolbar menu: refresh, toggle phone frame, show cleaned XML
        binding.toolbar.getMenu().add(0, 1, 0, "↺ Refresh");
        binding.toolbar.getMenu().add(0, 2, 1, "□ Phone frame: ON");
        binding.toolbar.getMenu().add(0, 3, 2, "Show cleaned XML");

        binding.toolbar.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: inflatePreview(); return true;
                case 2:
                    isPhoneFrame = !isPhoneFrame;
                    item.setTitle("□ Phone frame: " + (isPhoneFrame ? "ON" : "OFF"));
                    updatePhoneFrame();
                    return true;
                case 3:
                    showCleanedXml();
                    return true;
            }
            return false;
        });

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.previewContainer, false, false, false, true);

        inflatePreview();
    }

    // ── Preview inflation ─────────────────────────────────────────────────────

    private void inflatePreview() {
        binding.previewContainer.removeAllViews();
        binding.errorCard.setVisibility(View.GONE);

        if (originalXml == null || originalXml.trim().isEmpty()) {
            showError("No XML content provided.");
            return;
        }

        // Run on background thread — inflation can be slow for complex layouts
        new Thread(() -> {
            String cleaned = sanitizeXml(originalXml);
            runOnUiThread(() -> {
                try {
                    View inflated = LayoutInflater.from(this)
                            .inflate(
                                    new android.util.Xml.newPullParser().getClass()
                                            .equals(Object.class) ? null
                                            : getXmlParser(cleaned),
                                    binding.previewContainer,
                                    false);
                    binding.previewContainer.addView(inflated,
                            new FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT));
                    updatePhoneFrame();
                } catch (Exception e) {
                    // Fallback: try inflating via stream directly
                    try {
                        byte[] bytes = cleaned.getBytes(StandardCharsets.UTF_8);
                        android.content.res.XmlResourceParser parser =
                                new pro.sketchware.utility.XmlStringParser(cleaned);
                        showError("Parser not available — see cleaned XML via menu.\n\n"
                                + e.getMessage());
                    } catch (Exception e2) {
                        showError(e.getMessage());
                    }
                }
            });
        }).start();
    }

    /**
     * Real inflation using Android's LayoutInflater with an XML string.
     * We write to a temp file and inflate from there.
     */
    private void inflateFromString(String xml) {
        try {
            // Write cleaned XML to temp file in cache dir
            java.io.File tmpFile = new java.io.File(getCacheDir(), "live_preview_tmp.xml");
            pro.sketchware.utility.FileUtil.writeFile(tmpFile.getAbsolutePath(), xml);

            // Use XmlPullParser to inflate
            android.util.XmlPullParser parser = android.util.Xml.newPullParser();
            parser.setInput(new java.io.FileInputStream(tmpFile), "UTF-8");

            View inflated = LayoutInflater.from(
                    new android.view.ContextThemeWrapper(this,
                            com.google.android.material.R.style.Theme_Material3_DayNight))
                    .inflate(parser, null, false);

            binding.previewContainer.removeAllViews();
            binding.previewContainer.addView(inflated,
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT));
            updatePhoneFrame();

        } catch (Exception e) {
            showError("Inflation failed: " + e.getMessage()
                    + "\n\nTip: Use 'Show cleaned XML' to see what was attempted.");
        }
    }

    private android.util.XmlPullParser getXmlParser(String xml) throws Exception {
        android.util.XmlPullParser parser = android.util.Xml.newPullParser();
        parser.setInput(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "UTF-8");
        return parser;
    }

    /** Main entry — sanitize then inflate */
    private void inflatePreview2() {
        binding.previewContainer.removeAllViews();
        binding.errorCard.setVisibility(View.GONE);
        if (originalXml == null || originalXml.isEmpty()) { showError("No XML."); return; }
        String cleaned = sanitizeXml(originalXml);
        new Thread(() -> runOnUiThread(() -> inflateFromString(cleaned))).start();
    }

    // ── XML sanitizer ─────────────────────────────────────────────────────────

    /**
     * Replaces project-specific resource references with safe Android defaults
     * so that LayoutInflater can resolve everything.
     *
     * Rules:
     *  @+id/xxx          → removed (let system auto-assign)
     *  @id/xxx           → removed
     *  @color/xxx        → #808080
     *  @drawable/xxx     → @android:color/darker_gray  (gray placeholder)
     *  @string/xxx       → "xxx"  (key name as text)
     *  @dimen/xxx        → 8dp
     *  @style/xxx        → removed attribute
     *  @mipmap/xxx       → @android:drawable/sym_def_app_icon
     *  @layout/xxx       → removed (nested layouts unsupported)
     *  @anim/xxx         → removed
     *  @font/xxx         → removed (use default font)
     *  @array/xxx        → removed
     *  @bool/xxx         → true
     *  @integer/xxx      → 0
     *  app:xxx="..."     → kept (Material attrs work with Material theme)
     *  tools:xxx="..."   → removed
     */
    public static String sanitizeXml(String xml) {
        if (xml == null) return "";

        String result = xml;

        // Remove tools: namespace and attributes
        result = result.replaceAll("xmlns:tools=\"[^\"]*\"", "");
        result = result.replaceAll("tools:[a-zA-Z_]+=\"[^\"]*\"", "");

        // @+id/ and @id/ → remove the whole android:id attribute
        result = result.replaceAll("android:id=\"@\\+id/[^\"]*\"", "");
        result = result.replaceAll("android:id=\"@id/[^\"]*\"", "");

        // @color/ → gray
        result = result.replaceAll("@color/[a-zA-Z0-9_]+", "#808080");
        result = result.replaceAll("@android:color/[a-zA-Z0-9_]+", "#808080");

        // @drawable/ and @mipmap/ → placeholder
        result = result.replaceAll("@(drawable|mipmap)/[a-zA-Z0-9_]+",
                "@android:drawable/ic_menu_gallery");

        // @string/ → show key name as literal
        result = replaceStrings(result);

        // @dimen/ → 8dp
        result = result.replaceAll("@dimen/[a-zA-Z0-9_]+", "8dp");
        result = result.replaceAll("@android:dimen/[a-zA-Z0-9_]+", "8dp");

        // @style/ → remove whole attribute
        result = result.replaceAll("[a-zA-Z]+:style=\"@style/[^\"]*\"", "");
        result = result.replaceAll("style=\"@style/[^\"]*\"", "");

        // @layout/ → remove whole attribute (include/merge not supported live)
        result = result.replaceAll("android:layout=\"@layout/[^\"]*\"", "");
        result = result.replaceAll("<include[^/]*/?>", "<!-- include removed -->");
        result = result.replaceAll("<merge[^>]*>", "<FrameLayout>");
        result = result.replaceAll("</merge>", "</FrameLayout>");

        // @anim/, @font/, @array/, @xml/ → remove
        result = result.replaceAll("[a-zA-Z]+:[a-zA-Z_]+=\"@(anim|font|array|xml|bool|integer|raw)/[^\"]*\"", "");

        // @bool/xxx → true/false
        result = result.replaceAll("@bool/[a-zA-Z0-9_]+", "true");

        // @integer/xxx → 0
        result = result.replaceAll("@integer/[a-zA-Z0-9_]+", "0");

        // ?attr/xxx → remove containing attribute (attr refs need theme)
        result = result.replaceAll("[a-zA-Z]+:[a-zA-Z_]+=\"\\?[^\"]*\"", "");
        result = result.replaceAll("[a-zA-Z]+:[a-zA-Z_]+='\\?[^']*'", "");

        // Remove layout_constraintXxx attributes (ConstraintLayout attrs — keep basic ones)
        result = result.replaceAll("app:layout_constraint[A-Za-z_]+=\"[^\"]*\"", "");
        result = result.replaceAll("app:layout_editor[A-Za-z_]+=\"[^\"]*\"", "");

        // Replace unknown custom view classes with FrameLayout as fallback tag comment
        // (We can't do this with regex alone — LayoutInflater handles unknown views gracefully)

        return result;
    }

    private static String replaceStrings(String xml) {
        // Replace @string/key_name with the key_name as a literal string
        StringBuffer sb = new StringBuffer();
        Matcher m = Pattern.compile("@string/([a-zA-Z0-9_]+)").matcher(xml);
        while (m.find()) {
            m.appendReplacement(sb, m.group(1).replace("_", " "));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ── Phone frame ───────────────────────────────────────────────────────────

    private void updatePhoneFrame() {
        if (isPhoneFrame) {
            binding.phoneFrame.setVisibility(View.VISIBLE);
            binding.previewContainer.setBackgroundColor(Color.WHITE);
        } else {
            binding.phoneFrame.setVisibility(View.GONE);
            binding.previewContainer.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    // ── Error display ─────────────────────────────────────────────────────────

    private void showError(String msg) {
        binding.errorCard.setVisibility(View.VISIBLE);
        binding.tvError.setText(msg);
    }

    // ── Cleaned XML viewer ────────────────────────────────────────────────────

    private void showCleanedXml() {
        String cleaned = sanitizeXml(originalXml);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Cleaned XML (what LayoutInflater sees)")
                .setMessage(cleaned)
                .setPositiveButton("Close", null)
                .show();
    }
}
