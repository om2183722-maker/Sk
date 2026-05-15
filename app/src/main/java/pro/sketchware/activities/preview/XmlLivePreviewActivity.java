package pro.sketchware.activities.preview;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.hey.studios.util.Helper;
import pro.sketchware.databinding.ActivityXmlLivePreviewBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.UI;

public class XmlLivePreviewActivity extends BaseAppCompatActivity {

    public static final String EXTRA_XML   = "xml";
    public static final String EXTRA_TITLE = "title";

    private ActivityXmlLivePreviewBinding binding;
    private String originalXml;
    private boolean isPhoneFrame = true;

    private static final int MENU_REFRESH    = Menu.FIRST;
    private static final int MENU_FRAME      = Menu.FIRST + 1;
    private static final int MENU_SHOW_XML   = Menu.FIRST + 2;

    private MenuItem menuFrame;

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
        binding.toolbar.setSubtitle("LayoutInflater render");

        Menu menu = binding.toolbar.getMenu();
        menu.add(Menu.NONE, MENU_REFRESH,  0, "↺ Refresh");
        menuFrame = menu.add(Menu.NONE, MENU_FRAME, 1, "Phone frame: ON");
        menu.add(Menu.NONE, MENU_SHOW_XML, 2, "Show cleaned XML");

        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == MENU_REFRESH)  { inflatePreview(); return true; }
            if (id == MENU_FRAME)    { toggleFrame(); return true; }
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

        new Thread(() -> {
            String cleaned = sanitizeXml(originalXml);
            runOnUiThread(() -> {
                try {
                    // Write to temp file and inflate via XmlPullParser
                    java.io.File tmp = new java.io.File(getCacheDir(), "lp_tmp.xml");
                    FileUtil.writeFile(tmp.getAbsolutePath(), cleaned);

                    XmlPullParser parser = android.util.Xml.newPullParser();
                    parser.setInput(new StringReader(cleaned));

                    android.view.ContextThemeWrapper ctx =
                            new android.view.ContextThemeWrapper(this,
                                    com.google.android.material.R.style.Theme_Material3_DayNight);

                    View inflated = android.view.LayoutInflater.from(ctx)
                            .inflate(parser, binding.previewContainer, false);

                    binding.previewContainer.removeAllViews();
                    binding.previewContainer.addView(inflated,
                            new android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
                    updatePhoneFrame();
                    showSnack("Preview rendered ✓");
                } catch (Exception e) {
                    showError("Inflation failed:\n" + e.getMessage()
                            + "\n\nTip: tap ⋮ → 'Show cleaned XML' to debug.");
                }
            });
        }).start();
    }

    // ── XML sanitizer ─────────────────────────────────────────────────────────

    /**
     * Replaces project-specific resource refs with safe Android defaults
     * so LayoutInflater can resolve everything.
     */
    public static String sanitizeXml(String xml) {
        if (xml == null) return "";
        String s = xml;

        // Remove tools: namespace and all tools: attributes
        s = s.replaceAll("xmlns:tools=\"[^\"]*\"", "");
        s = s.replaceAll("tools:[a-zA-Z_0-9]+=\"[^\"]*\"", "");

        // @+id/ and @id/ → remove the whole id attribute
        s = s.replaceAll("android:id=\"@\\+?id/[^\"]*\"", "");

        // @color/ → gray
        s = s.replaceAll("@(?:android:)?color/[a-zA-Z0-9_]+", "#9E9E9E");

        // @drawable/ and @mipmap/ → placeholder drawable
        s = s.replaceAll("@(?:drawable|mipmap)/[a-zA-Z0-9_]+",
                "@android:drawable/ic_menu_gallery");
        s = s.replaceAll("@android:drawable/[a-zA-Z0-9_]+",
                "@android:drawable/ic_menu_gallery");

        // @string/ → key name as literal text
        s = replaceStringRefs(s);

        // @dimen/ → 8dp
        s = s.replaceAll("@(?:android:)?dimen/[a-zA-Z0-9_]+", "8dp");

        // @style/ → remove the whole attribute
        s = s.replaceAll("(?:android|app):[a-zA-Z_0-9]+=\"@style/[^\"]*\"", "");
        s = s.replaceAll("style=\"@style/[^\"]*\"", "");

        // @bool/ → true
        s = s.replaceAll("@(?:android:)?bool/[a-zA-Z0-9_]+", "true");

        // @integer/ → 0
        s = s.replaceAll("@(?:android:)?integer/[a-zA-Z0-9_]+", "0");

        // ?attr/ → remove the whole attribute
        s = s.replaceAll("[a-zA-Z]+:[a-zA-Z_0-9]+=\"\\?[^\"]*\"", "");

        // @anim/, @font/, @array/, @xml/, @raw/ → remove attribute
        s = s.replaceAll("[a-zA-Z]+:[a-zA-Z_0-9]+=\"@(anim|font|array|xml|raw)/[^\"]*\"", "");

        // <include> → remove (can't resolve)
        s = s.replaceAll("<include[^>]*/?>", "<!-- include removed -->");

        // <merge> → <FrameLayout>
        s = s.replaceAll("<merge([^>]*)>", "<FrameLayout$1>");
        s = s.replaceAll("</merge>", "</FrameLayout>");

        // ConstraintLayout constraint attributes → remove
        s = s.replaceAll("app:layout_constraint[A-Za-z_0-9]+=\"[^\"]*\"", "");
        s = s.replaceAll("app:layout_editor[A-Za-z_0-9]+=\"[^\"]*\"", "");

        return s;
    }

    private static String replaceStringRefs(String xml) {
        StringBuffer sb = new StringBuffer();
        Matcher m = Pattern.compile("@string/([a-zA-Z0-9_]+)").matcher(xml);
        while (m.find()) {
            m.appendReplacement(sb, m.group(1).replace("_", " "));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ── Phone frame ───────────────────────────────────────────────────────────

    private void toggleFrame() {
        isPhoneFrame = !isPhoneFrame;
        menuFrame.setTitle("Phone frame: " + (isPhoneFrame ? "ON" : "OFF"));
        updatePhoneFrame();
    }

    private void updatePhoneFrame() {
        binding.phoneFrame.setVisibility(isPhoneFrame ? View.VISIBLE : View.GONE);
        binding.previewContainer.setBackgroundColor(Color.WHITE);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
