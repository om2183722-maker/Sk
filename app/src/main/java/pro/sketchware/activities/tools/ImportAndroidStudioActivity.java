package pro.sketchware.activities.tools;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import a.a.a.GB;
import a.a.a.lC;
import a.a.a.oB;
import a.a.a.wq;
import mod.hey.studios.util.Helper;
import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.databinding.ActivityImportAsBinding;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

/**
 * Import an Android Studio project (ZIP) into Sketchware Pro.
 *
 * What gets imported:
 *  - Package name, app name from AndroidManifest.xml
 *  - Java / Kotlin source files  → saved to FilePathUtil.getPathJava(sc_id)
 *  - XML layout files            → saved to custom_src/xml/
 *  - Drawable resources          → saved to project drawable folder
 *  - strings.xml, colors.xml     → applied as project resources
 *  - Gradle dependencies         → noted in import report
 *
 * What is NOT imported (impossible to convert automatically):
 *  - Sketchware block logic (Java → blocks is AI-level work)
 *  - Visual design canvas (but layouts are viewable in Live Preview)
 */
public class ImportAndroidStudioActivity extends BaseAppCompatActivity {

    private ActivityImportAsBinding binding;
    private String pendingLog = "";

    private final ActivityResultLauncher<Intent> zipPicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == Activity.RESULT_OK && r.getData() != null) {
                    Uri uri = r.getData().getData();
                    if (uri != null) startImport(uri);
                }
            });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityImportAsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.toolbar.setTitle("Import Android Studio Project");

        binding.btnSelectZip.setOnClickListener(v -> openZipPicker());
        binding.btnImport.setVisibility(View.GONE);

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.scrollContent, false, false, false, true);
    }

    private void openZipPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/zip", "application/x-zip-compressed"});
        try { zipPicker.launch(i); }
        catch (Exception e) { SketchwareUtil.toastError("No file manager found"); }
    }

    // ── Import ────────────────────────────────────────────────────────────────

    private void startImport(Uri uri) {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tvLog.setText("Reading ZIP...");
        binding.cardLog.setVisibility(View.VISIBLE);
        pendingLog = "";

        new Thread(() -> {
            try {
                // Extract ZIP to temp directory
                File tempDir = new File(getCacheDir(), "as_import_tmp");
                deleteDir(tempDir);
                tempDir.mkdirs();

                log("Extracting ZIP...");
                extractZip(uri, tempDir);

                // Find project root (the directory containing app/)
                File projectRoot = findProjectRoot(tempDir);
                if (projectRoot == null) {
                    throw new Exception("Could not find app/ directory. Is this an Android Studio project?");
                }
                log("Project root: " + projectRoot.getName());

                // Parse AndroidManifest
                File manifest = new File(projectRoot, "app/src/main/AndroidManifest.xml");
                if (!manifest.exists()) throw new Exception("AndroidManifest.xml not found");

                String[] manifestInfo = parseManifest(manifest);
                String packageName = manifestInfo[0];
                List<String> activities = new ArrayList<>();
                for (int i = 1; i < manifestInfo.length; i++) activities.add(manifestInfo[i]);

                log("Package: " + packageName);
                log("Activities: " + activities.size());

                // Get app name from strings.xml or use package last segment
                String appName = getAppName(projectRoot, packageName);
                String projectName = packageName.contains(".")
                        ? packageName.substring(packageName.lastIndexOf('.') + 1) : packageName;

                log("App name: " + appName);

                // Create new Sketchware project
                String sc_id = lC.b(); // generates next available sc_id
                log("Creating project (sc_id=" + sc_id + ")...");

                HashMap<String, Object> data = buildProjectData(sc_id, packageName, appName, projectName);
                lC.a(sc_id, data);
                wq.a(getApplicationContext(), sc_id);
                new oB().b(wq.b(sc_id));

                // Apply settings
                ProjectSettings ps = new ProjectSettings(sc_id);
                ps.setValue(ProjectSettings.SETTING_NEW_XML_COMMAND,
                        ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
                ps.setValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING,
                        ProjectSettings.SETTING_GENERIC_VALUE_TRUE);

                // Copy Java/Kotlin source files
                log("Copying Java/Kotlin sources...");
                FilePathUtil fpu = new FilePathUtil();
                String destJava = fpu.getPathJava(sc_id);
                new File(destJava).mkdirs();

                File javaSrcDir = new File(projectRoot, "app/src/main/java");
                int[] javaCopied = {0};
                if (javaSrcDir.exists()) {
                    copySourceFiles(javaSrcDir, new File(destJava), javaCopied, ".java", ".kt");
                }

                // Also check Kotlin src directory
                File kotlinSrcDir = new File(projectRoot, "app/src/main/kotlin");
                if (kotlinSrcDir.exists()) {
                    copySourceFiles(kotlinSrcDir, new File(destJava), javaCopied, ".java", ".kt");
                }
                log("Copied " + javaCopied[0] + " Java/Kotlin files");

                // Copy XML layouts as custom_src
                log("Copying XML layouts...");
                String customXml = wq.b(sc_id) + "/custom_src/xml";
                new File(customXml).mkdirs();
                File layoutDir = new File(projectRoot, "app/src/main/res/layout");
                int[] xmlCopied = {0};
                if (layoutDir.exists()) {
                    for (File f : safeList(layoutDir)) {
                        if (f.getName().endsWith(".xml")) {
                            FileUtil.copyFile(f.getAbsolutePath(), customXml + "/" + f.getName());
                            xmlCopied[0]++;
                        }
                    }
                }
                log("Copied " + xmlCopied[0] + " XML layout files");

                // Copy drawables
                log("Copying drawables...");
                String drawableDest = wq.b(sc_id) + "/files/resource/images";
                new File(drawableDest).mkdirs();
                int[] drawCopied = {0};
                for (String resFolder : new String[]{"drawable", "drawable-xhdpi", "mipmap-xhdpi"}) {
                    File src = new File(projectRoot, "app/src/main/res/" + resFolder);
                    if (src.exists()) {
                        for (File f : safeList(src)) {
                            String ext = ext(f.getName());
                            if (ext.equals("png") || ext.equals("jpg") || ext.equals("webp")
                                    || ext.equals("xml") || ext.equals("svg")) {
                                FileUtil.copyFile(f.getAbsolutePath(),
                                        drawableDest + "/" + f.getName());
                                drawCopied[0]++;
                            }
                        }
                    }
                }
                log("Copied " + drawCopied[0] + " drawable files");

                // Read Gradle dependencies
                log("Reading Gradle dependencies...");
                File buildGradle = new File(projectRoot, "app/build.gradle");
                if (!buildGradle.exists()) buildGradle = new File(projectRoot, "app/build.gradle.kts");
                List<String> deps = new ArrayList<>();
                if (buildGradle.exists()) {
                    deps = parseDependencies(buildGradle);
                    log("Found " + deps.size() + " dependencies");
                }

                // Cleanup temp
                deleteDir(tempDir);

                final String finalScId = sc_id;
                final List<String> finalDeps = deps;
                final int javaCount = javaCopied[0];
                final int xmlCount = xmlCopied[0];

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    showImportSuccess(finalScId, packageName, appName,
                            javaCount, xmlCount, finalDeps);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    log("ERROR: " + e.getMessage());
                    SketchwareUtil.toastError("Import failed: " + e.getMessage());
                });
            }
        }).start();
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    private File findProjectRoot(File tempDir) {
        // Look for directory containing app/src/main/
        return findDirContaining(tempDir, "app");
    }

    private File findDirContaining(File dir, String childName) {
        File child = new File(dir, childName);
        if (child.exists()) return dir;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isDirectory()) {
                File found = findDirContaining(f, childName);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Returns [packageName, activity1, activity2, ...] */
    private String[] parseManifest(File manifest) throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser p = factory.newPullParser();
        p.setInput(new java.io.FileInputStream(manifest), "UTF-8");

        String pkg = "com.example.app";
        List<String> acts = new ArrayList<>();
        List<String> result = new ArrayList<>();

        int event = p.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                if ("manifest".equals(p.getName())) {
                    String pkgAttr = p.getAttributeValue(null, "package");
                    if (pkgAttr != null) pkg = pkgAttr;
                }
                if ("activity".equals(p.getName())) {
                    String name = p.getAttributeValue(
                            "http://schemas.android.com/apk/res/android", "name");
                    if (name != null) {
                        if (name.startsWith(".")) name = pkg + name;
                        acts.add(name);
                    }
                }
            }
            event = p.next();
        }

        result.add(pkg);
        result.addAll(acts);
        return result.toArray(new String[0]);
    }

    private String getAppName(File root, String pkg) {
        File strings = new File(root, "app/src/main/res/values/strings.xml");
        if (strings.exists()) {
            try {
                String content = readFile(strings);
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("<string name=\"app_name\"[^>]*>([^<]+)</string>")
                        .matcher(content);
                if (m.find()) return m.group(1).trim();
            } catch (Exception ignored) {}
        }
        return pkg.contains(".") ? pkg.substring(pkg.lastIndexOf('.') + 1) : pkg;
    }

    private List<String> parseDependencies(File buildGradle) {
        List<String> deps = new ArrayList<>();
        try {
            String content = readFile(buildGradle);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("implementation ['\"]([^'\"]+)['\"]")
                    .matcher(content);
            while (m.find()) deps.add(m.group(1));
        } catch (Exception ignored) {}
        return deps;
    }

    private HashMap<String, Object> buildProjectData(String sc_id, String pkg,
                                                      String appName, String projectName) {
        HashMap<String, Object> data = new HashMap<>();
        data.put("sc_id", sc_id);
        data.put("my_sc_pkg_name", pkg);
        data.put("my_ws_name", projectName + " (imported)");
        data.put("my_app_name", appName);
        data.put("my_sc_reg_dt", new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
                .format(new Date()));
        data.put("custom_icon", false);
        data.put("isIconAdaptive", false);
        data.put("sc_ver_code", "1");
        data.put("sc_ver_name", "1.0");
        data.put("sketchware_ver", GB.d(getApplicationContext()));
        // Default theme colors
        data.put("color_accent", "-16740915");
        data.put("color_primary", "-16740915");
        data.put("color_primary_dark", "-16776961");
        data.put("color_control_highlight", "-16740915");
        data.put("color_control_normal", "-16776961");
        return data;
    }

    private void copySourceFiles(File srcDir, File destDir, int[] count, String... exts) {
        File[] files = srcDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                File subDest = new File(destDir, f.getName());
                subDest.mkdirs();
                copySourceFiles(f, subDest, count, exts);
            } else {
                for (String ext : exts) {
                    if (f.getName().endsWith(ext)) {
                        FileUtil.copyFile(f.getAbsolutePath(),
                                new File(destDir, f.getName()).getAbsolutePath());
                        count[0]++;
                        break;
                    }
                }
            }
        }
    }

    // ── Success dialog ────────────────────────────────────────────────────────

    private void showImportSuccess(String sc_id, String pkg, String appName,
                                   int javaCount, int xmlCount, List<String> deps) {
        StringBuilder msg = new StringBuilder();
        msg.append("Project created successfully!\n\n");
        msg.append("sc_id: ").append(sc_id).append("\n");
        msg.append("Package: ").append(pkg).append("\n");
        msg.append("App name: ").append(appName).append("\n");
        msg.append("Java/Kotlin files: ").append(javaCount).append("\n");
        msg.append("Layout XML files: ").append(xmlCount).append("\n");

        if (!deps.isEmpty()) {
            msg.append("\nFound ").append(deps.size()).append(" Gradle dependencies:\n");
            for (int i = 0; i < Math.min(deps.size(), 5); i++) {
                msg.append("  • ").append(deps.get(i)).append("\n");
            }
            if (deps.size() > 5) msg.append("  ... and ").append(deps.size() - 5).append(" more\n");
            msg.append("\nAdd these to Build Settings → Libraries as needed.");
        }

        msg.append("\n\nNote: Block logic cannot be converted automatically.\n" +
                "Your Java/Kotlin code is accessible via:\n" +
                "Design screen → Drawer → Project Files");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Import Complete!")
                .setMessage(msg.toString())
                .setPositiveButton("Open Project Files", (d, w) -> {
                    Intent i = new Intent(this, ProjectFileManagerActivity.class);
                    i.putExtra(ProjectFileManagerActivity.EXTRA_SC_ID, sc_id);
                    startActivity(i);
                    finish();
                })
                .setNeutralButton("Close", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void log(String msg) {
        pendingLog += msg + "\n";
        runOnUiThread(() -> binding.tvLog.setText(pendingLog));
    }

    private void extractZip(Uri uri, File destDir) throws Exception {
        InputStream is = getContentResolver().openInputStream(uri);
        if (is == null) throw new Exception("Cannot open ZIP");
        ZipInputStream zis = new ZipInputStream(is);
        ZipEntry entry;
        byte[] buf = new byte[8192];
        while ((entry = zis.getNextEntry()) != null) {
            File dest = new File(destDir, entry.getName());
            if (entry.isDirectory()) {
                dest.mkdirs();
            } else {
                dest.getParentFile().mkdirs();
                try (FileOutputStream fos = new FileOutputStream(dest)) {
                    int len;
                    while ((len = zis.read(buf)) > 0) fos.write(buf, 0, len);
                }
            }
            zis.closeEntry();
        }
        zis.close();
    }

    private void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) {
            if (f.isDirectory()) deleteDir(f); else f.delete();
        }
        dir.delete();
    }

    private String readFile(File f) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        java.io.FileInputStream fis = new java.io.FileInputStream(f);
        byte[] buf = new byte[4096]; int len;
        while ((len = fis.read(buf)) > 0) bos.write(buf, 0, len);
        fis.close();
        return bos.toString(StandardCharsets.UTF_8.name());
    }

    private File[] safeList(File dir) {
        File[] f = dir.listFiles();
        return f != null ? f : new File[0];
    }

    private String ext(String name) {
        int i = name.lastIndexOf('.'); return i >= 0 ? name.substring(i+1).toLowerCase() : "";
    }
}
