package pro.sketchware.activities.tools;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import mod.hey.studios.util.Helper;
import pro.sketchware.databinding.ActivitySvgToVectorBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.SvgToVectorConverter;
import pro.sketchware.utility.UI;

/**
 * Standalone SVG → Android VectorDrawable XML converter.
 * User can pick an SVG file, see the converted XML, copy it, or save it.
 */
public class SvgToVectorActivity extends BaseAppCompatActivity {

    private ActivitySvgToVectorBinding binding;
    private String convertedXml = "";
    private String currentFileName = "";

    private final ActivityResultLauncher<Intent> svgPicker =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK
                                && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) loadAndConvert(uri);
                        }
                    });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivitySvgToVectorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.toolbar.setTitle("SVG → Vector XML");

        // ── Buttons ───────────────────────────────────────────────────────────
        binding.btnPickSvg.setOnClickListener(v -> openSvgPicker());

        binding.btnCopy.setOnClickListener(v -> {
            if (convertedXml.isEmpty()) return;
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("vector_xml", convertedXml));
            SketchwareUtil.toast("Copied to clipboard ✓");
        });

        binding.btnSave.setOnClickListener(v -> {
            if (convertedXml.isEmpty()) return;
            saveVectorXml();
        });

        binding.btnPasteSvg.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm.hasPrimaryClip() && cm.getPrimaryClip() != null) {
                CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                if (text != null) {
                    convertSvgString(text.toString(), "pasted.svg");
                } else {
                    SketchwareUtil.toastError("Clipboard is empty");
                }
            }
        });

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.scrollContent, false, false, false, true);
    }

    // ── File picker ───────────────────────────────────────────────────────────

    private void openSvgPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "image/svg+xml", "text/xml", "application/xml", "text/plain"});
        try { svgPicker.launch(intent); }
        catch (Exception e) { SketchwareUtil.toastError("No file manager found"); }
    }

    private void loadAndConvert(Uri uri) {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.cardResult.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                // Read SVG content from URI
                InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) throw new Exception("Cannot open file");
                byte[] bytes = is.readAllBytes();
                is.close();
                String svgContent = new String(bytes, StandardCharsets.UTF_8);

                // Extract filename
                String fileName = uri.getLastPathSegment();
                if (fileName != null && fileName.contains("/")) {
                    fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
                }
                if (fileName == null) fileName = "vector.xml";
                fileName = fileName.replace(".svg", ".xml");
                String finalFileName = fileName;

                runOnUiThread(() -> convertSvgString(svgContent, finalFileName));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    showError("Failed to read file:\n" + e.getMessage());
                });
            }
        }).start();
    }

    // ── Conversion ────────────────────────────────────────────────────────────

    private void convertSvgString(String svgContent, String fileName) {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.cardResult.setVisibility(View.GONE);
        binding.cardWarnings.setVisibility(View.GONE);
        currentFileName = fileName;

        new Thread(() -> {
            try {
                SvgToVectorConverter converter = new SvgToVectorConverter();
                SvgToVectorConverter.ConversionResult result = converter.convert(svgContent);
                String xml = result.vectorXml;
                java.util.List<String> warnings = result.warnings;

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    convertedXml = xml;

                    // Show result
                    binding.tvFileName.setText(fileName);
                    binding.tvVectorXml.setText(xml);
                    binding.cardResult.setVisibility(View.VISIBLE);
                    binding.btnCopy.setVisibility(View.VISIBLE);
                    binding.btnSave.setVisibility(View.VISIBLE);

                    // Show warnings if any
                    if (!warnings.isEmpty()) {
                        binding.cardWarnings.setVisibility(View.VISIBLE);
                        StringBuilder sb = new StringBuilder();
                        for (String w : warnings) sb.append("• ").append(w).append("\n");
                        binding.tvWarnings.setText(sb.toString().trim());
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    showError("Conversion failed:\n" + e.getMessage());
                });
            }
        }).start();
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void saveVectorXml() {
        // Save to Downloads folder
        String downloadsPath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        String outPath = downloadsPath + File.separator + currentFileName;

        try {
            FileUtil.writeFile(outPath, convertedXml);
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Saved ✓")
                    .setMessage("Saved to:\n" + outPath
                            + "\n\nCopy this file to your Sketchware project's drawable folder.")
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception e) {
            SketchwareUtil.toastError("Save failed: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Error")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }
}
