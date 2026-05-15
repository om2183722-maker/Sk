package pro.sketchware.activities.tools;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import mod.hey.studios.util.Helper;
import pro.sketchware.databinding.ActivitySvgToVectorBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.SvgToVectorConverter;
import pro.sketchware.utility.UI;

public class SvgToVectorActivity extends BaseAppCompatActivity {

    private ActivitySvgToVectorBinding binding;
    private String convertedXml = "";
    private String currentFileName = "vector.xml";

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

        binding.btnPickSvg.setOnClickListener(v -> openSvgPicker());

        binding.btnPasteSvg.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm.hasPrimaryClip() && cm.getPrimaryClip() != null
                    && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                if (text != null && !text.toString().trim().isEmpty()) {
                    convertSvgString(text.toString(), "pasted.xml");
                } else {
                    SketchwareUtil.toastError("Clipboard is empty");
                }
            } else {
                SketchwareUtil.toastError("Clipboard is empty");
            }
        });

        binding.btnCopy.setOnClickListener(v -> {
            if (convertedXml.isEmpty()) return;
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("vector_xml", convertedXml));
            SketchwareUtil.toast("Copied ✓");
        });

        binding.btnSave.setOnClickListener(v -> {
            if (convertedXml.isEmpty()) return;
            saveToDownloads();
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
                InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) throw new Exception("Cannot open file");
                // Compatible readAllBytes for API < 33
                String svgContent = readStream(is);
                is.close();

                String rawName = uri.getLastPathSegment();
                if (rawName != null && rawName.contains("/"))
                    rawName = rawName.substring(rawName.lastIndexOf("/") + 1);
                String fileName = (rawName != null ? rawName : "vector")
                        .replace(".svg", "").replace(".SVG", "") + ".xml";

                String finalFileName = fileName;
                runOnUiThread(() -> convertSvgString(svgContent, finalFileName));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    showError("Failed to read file: " + e.getMessage());
                });
            }
        }).start();
    }

    /** Reads all bytes from stream — compatible with all API levels */
    private String readStream(InputStream is) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int bytesRead;
        while ((bytesRead = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
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
                List<String> warnings = result.warnings;

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    convertedXml = xml;

                    binding.tvFileName.setText(fileName);
                    binding.tvVectorXml.setText(xml);
                    binding.cardResult.setVisibility(View.VISIBLE);
                    binding.btnCopy.setVisibility(View.VISIBLE);
                    binding.btnSave.setVisibility(View.VISIBLE);

                    if (!warnings.isEmpty()) {
                        binding.cardWarnings.setVisibility(View.VISIBLE);
                        StringBuilder sb = new StringBuilder();
                        for (String w : warnings) sb.append("• ").append(w).append("\n");
                        binding.tvWarnings.setText(sb.toString().trim());
                    }
                    SketchwareUtil.toast("Converted ✓");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    showError("Conversion failed: " + e.getMessage());
                });
            }
        }).start();
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void saveToDownloads() {
        String outPath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()
                + File.separator + currentFileName;
        try {
            FileUtil.writeFile(outPath, convertedXml);
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Saved ✓")
                    .setMessage("File saved to:\n" + outPath
                            + "\n\nCopy this to your project's res/drawable/ folder.")
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
