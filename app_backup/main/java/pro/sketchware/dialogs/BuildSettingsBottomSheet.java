package pro.sketchware.dialogs;

import static mod.hey.studios.build.BuildSettings.SETTING_ANDROID_JAR_PATH;
import static mod.hey.studios.build.BuildSettings.SETTING_CLASSPATH;
import static mod.hey.studios.build.BuildSettings.SETTING_DEXER;
import static mod.hey.studios.build.BuildSettings.SETTING_ENABLE_LOGCAT;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_10;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_11;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_1_7;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_1_8;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_1_9;
import static mod.hey.studios.build.BuildSettings.SETTING_NO_HTTP_LEGACY;
import static mod.hey.studios.build.BuildSettings.SETTING_NO_WARNINGS;
import static mod.hey.studios.build.BuildSettings.SETTING_BACKGROUND_BUILD;
import static mod.hey.studios.build.BuildSettings.SETTING_NOTIF_SHOW_FILE;
import static mod.hey.studios.build.BuildSettings.SETTING_FORCE_BATCH_COMPILE;
import static mod.hey.studios.build.BuildSettings.SETTING_SIGNING_ENABLED;
import static mod.hey.studios.build.BuildSettings.SETTING_SIGNING_KEY_ALIAS;
import static mod.hey.studios.build.BuildSettings.SETTING_SIGNING_KEY_PASS;
import static mod.hey.studios.build.BuildSettings.SETTING_SIGNING_KEYSTORE_PASS;
import static mod.hey.studios.build.BuildSettings.SETTING_SIGNING_KEYSTORE_PATH;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayout;

import java.io.File;

import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.util.Helper;
import pro.sketchware.databinding.ProjectConfigLayoutBinding;
import pro.sketchware.utility.SketchwareUtil;

public class BuildSettingsBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = BuildSettingsBottomSheet.class.getSimpleName();
    private static int totalViews = 0;

    private static final int VIEW_ANDROIR_JAR_PATH = totalViews++;
    private static final int VIEW_CLASS_PATH       = totalViews++;
    private static final int VIEW_DEXER            = totalViews++;
    private static final int VIEW_JAVA_VERSION     = totalViews++;
    private static final int VIEW_NO_WARNINGS      = totalViews++;
    private static final int VIEW_NO_HTTP_LEGACY   = totalViews++;
    private static final int VIEW_ENABLE_LOGCAT    = totalViews++;
    private View[] views;

    private ProjectConfigLayoutBinding binding;
    private BuildSettings projectSettings;

    /** Launcher used to pick keystore file via Storage Access Framework */
    private final ActivityResultLauncher<Intent> keystorePicker =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                // Convert content:// URI to real path where possible,
                                // otherwise keep the string form of the URI.
                                String path = resolveRealPath(uri);
                                if (binding != null) {
                                    binding.etKeystorePath.setText(path);
                                }
                            }
                        }
                    });

    public static BuildSettingsBottomSheet newInstance(String sc_id) {
        BuildSettingsBottomSheet sheet = new BuildSettingsBottomSheet();
        Bundle arguments = new Bundle();
        arguments.putString("sc_id", sc_id);
        sheet.setArguments(arguments);
        return sheet;
    }

    public static String[] getAvailableJavaVersions() {
        return new String[]{
                SETTING_JAVA_VERSION_1_7,
                SETTING_JAVA_VERSION_1_8,
                SETTING_JAVA_VERSION_1_9,
                SETTING_JAVA_VERSION_10,
                SETTING_JAVA_VERSION_11,
        };
    }

    public static void handleJavaVersionChange(String choice) {
        if (!choice.equals(SETTING_JAVA_VERSION_1_7)) {
            SketchwareUtil.toast("Don't forget to enable D8 to be able to compile Java 8+ code");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = getArguments();
        projectSettings = new BuildSettings(arguments.getString("sc_id"));
        views = new View[totalViews];
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        binding = ProjectConfigLayoutBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupBuildTab();
        setupSigningTab();
        setupAdvancedTab();
        setupTabs();

        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.btnSave.setOnClickListener(v -> saveAll());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ── Build tab ─────────────────────────────────────────────────────────────

    private void setupBuildTab() {
        // Existing settings – unchanged behaviour
        binding.noWarnings.setOnClickListener(v -> binding.cbNoWarnings.performClick());
        binding.noHttpLegacy.setOnClickListener(v -> binding.cbNoHttpLegacy.performClick());
        binding.enableLogcat.setOnClickListener(v -> binding.cbEnableLogcat.performClick());

        binding.tilAndroidJar.getEditText().setText(projectSettings.getValue(SETTING_ANDROID_JAR_PATH, ""));
        binding.tilClasspath.getEditText().setText(projectSettings.getValue(SETTING_CLASSPATH, ""));

        setRadioGroupOptions(binding.rgDexer, new String[]{"Dx", "D8"}, SETTING_DEXER, "Dx");
        setRadioGroupOptions(binding.rgJavaVersion, getAvailableJavaVersions(), SETTING_JAVA_VERSION, "1.7");

        setCheckboxValue(binding.cbNoWarnings,   SETTING_NO_WARNINGS,   true);
        setCheckboxValue(binding.cbNoHttpLegacy, SETTING_NO_HTTP_LEGACY, false);
        setCheckboxValue(binding.cbEnableLogcat, SETTING_ENABLE_LOGCAT,  true);

        // Register views for bulk-save via ProjectSettings.setValues()
        binding.tilAndroidJar.getEditText().setTag(SETTING_ANDROID_JAR_PATH);
        binding.tilClasspath.getEditText().setTag(SETTING_CLASSPATH);
        binding.rgDexer.setTag(SETTING_DEXER);
        binding.rgJavaVersion.setTag(SETTING_JAVA_VERSION);
        binding.cbNoWarnings.setTag(SETTING_NO_WARNINGS);
        binding.cbNoHttpLegacy.setTag(SETTING_NO_HTTP_LEGACY);
        binding.cbEnableLogcat.setTag(SETTING_ENABLE_LOGCAT);

        views[VIEW_ANDROIR_JAR_PATH] = binding.tilAndroidJar.getEditText();
        views[VIEW_CLASS_PATH]       = binding.tilClasspath.getEditText();
        views[VIEW_DEXER]            = binding.rgDexer;
        views[VIEW_ENABLE_LOGCAT]    = binding.cbEnableLogcat;
        views[VIEW_JAVA_VERSION]     = binding.rgJavaVersion;
        views[VIEW_NO_HTTP_LEGACY]   = binding.cbNoHttpLegacy;
        views[VIEW_NO_WARNINGS]      = binding.cbNoWarnings;
    }

    // ── Signing tab ───────────────────────────────────────────────────────────

    private void setupSigningTab() {
        // Load persisted values
        boolean signingEnabled = projectSettings.getValue(SETTING_SIGNING_ENABLED, "false").equals("true");
        binding.cbSigningEnabled.setChecked(signingEnabled);
        updateSigningFieldsEnabled(signingEnabled);

        binding.etKeystorePath.setText(projectSettings.getValue(SETTING_SIGNING_KEYSTORE_PATH, ""));
        binding.etKeystorePass.setText(projectSettings.getValue(SETTING_SIGNING_KEYSTORE_PASS, ""));
        binding.etKeyAlias.setText(projectSettings.getValue(SETTING_SIGNING_KEY_ALIAS, ""));
        binding.etKeyPass.setText(projectSettings.getValue(SETTING_SIGNING_KEY_PASS, ""));

        // Toggle fields enabled/disabled when checkbox clicked
        binding.llSigningEnabled.setOnClickListener(v -> binding.cbSigningEnabled.performClick());
        binding.cbSigningEnabled.setOnCheckedChangeListener((btn, checked) ->
                updateSigningFieldsEnabled(checked));

        // Browse button → open file picker
        binding.btnBrowseKeystore.setOnClickListener(v -> openKeystorePicker());
    }

    private void updateSigningFieldsEnabled(boolean enabled) {
        binding.llKeystoreFields.setAlpha(enabled ? 1f : 0.4f);
        setGroupEnabled(binding.llKeystoreFields, enabled);
    }

    private void setGroupEnabled(ViewGroup group, boolean enabled) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setEnabled(enabled);
            if (child instanceof ViewGroup) {
                setGroupEnabled((ViewGroup) child, enabled);
            }
        }
    }

    private void openKeystorePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        // Suggest .jks / .bks extensions (not all launchers honour MIME filter)
        String[] mimes = {"application/octet-stream", "application/x-java-keystore"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimes);
        try {
            keystorePicker.launch(intent);
        } catch (Exception e) {
            SketchwareUtil.toastError("No file manager found");
        }
    }

    /**
     * Tries to resolve a content:// URI to an absolute file path.
     * Falls back to URI string if resolution fails (e.g. cloud-only files).
     */
    private String resolveRealPath(Uri uri) {
        // Simple heuristic: if the path segment looks like a real file path, use it.
        try {
            String docId = DocumentsContract.getDocumentId(uri);
            if (docId != null && docId.contains(":")) {
                String[] split = docId.split(":");
                // primary:Download/mykeystore.jks → /sdcard/Download/mykeystore.jks
                if ("primary".equalsIgnoreCase(split[0])) {
                    return android.os.Environment.getExternalStorageDirectory()
                            + "/" + split[1];
                }
            }
        } catch (Exception ignored) {
        }
        // Fallback: return URI as string (user can manually edit it)
        return uri.toString();
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────

    private void setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Build"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Signing"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Advanced"));

        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int pos = tab.getPosition();
                binding.scrollBuild.setVisibility(pos == 0 ? View.VISIBLE : View.GONE);
                binding.scrollSigning.setVisibility(pos == 1 ? View.VISIBLE : View.GONE);
                binding.scrollAdvanced.setVisibility(pos == 2 ? View.VISIBLE : View.GONE);
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    // ── Advanced tab ──────────────────────────────────────────────────────────

    private void setupAdvancedTab() {
        boolean bgBuild   = projectSettings.getValue(SETTING_BACKGROUND_BUILD,   "true").equals("true");
        boolean notifFile = projectSettings.getValue(SETTING_NOTIF_SHOW_FILE,    "true").equals("true");
        boolean forceBatch = projectSettings.getValue(SETTING_FORCE_BATCH_COMPILE,"false").equals("true");

        binding.cbBackgroundBuild.setChecked(bgBuild);
        binding.cbNotifShowFile.setChecked(notifFile);
        binding.cbForceBatch.setChecked(forceBatch);

        binding.llBackgroundBuild.setOnClickListener(v -> binding.cbBackgroundBuild.performClick());
        binding.llNotifShowFile.setOnClickListener(v -> binding.cbNotifShowFile.performClick());
        binding.llForceBatch.setOnClickListener(v -> binding.cbForceBatch.performClick());
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void saveAll() {
        // Build tab – uses the bulk-save helper from ProjectSettings
        projectSettings.setValues(views);

        // Signing tab – save manually
        projectSettings.setValue(SETTING_SIGNING_ENABLED,
                Boolean.toString(binding.cbSigningEnabled.isChecked()));
        projectSettings.setValue(SETTING_SIGNING_KEYSTORE_PATH,
                binding.etKeystorePath.getText() != null
                        ? binding.etKeystorePath.getText().toString().trim() : "");
        projectSettings.setValue(SETTING_SIGNING_KEYSTORE_PASS,
                binding.etKeystorePass.getText() != null
                        ? binding.etKeystorePass.getText().toString() : "");
        projectSettings.setValue(SETTING_SIGNING_KEY_ALIAS,
                binding.etKeyAlias.getText() != null
                        ? binding.etKeyAlias.getText().toString().trim() : "");
        projectSettings.setValue(SETTING_SIGNING_KEY_PASS,
                binding.etKeyPass.getText() != null
                        ? binding.etKeyPass.getText().toString() : "");

        // Advanced tab
        projectSettings.setValue(SETTING_BACKGROUND_BUILD,    Boolean.toString(binding.cbBackgroundBuild.isChecked()));
        projectSettings.setValue(SETTING_NOTIF_SHOW_FILE,     Boolean.toString(binding.cbNotifShowFile.isChecked()));
        projectSettings.setValue(SETTING_FORCE_BATCH_COMPILE, Boolean.toString(binding.cbForceBatch.isChecked()));

        dismiss();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setRadioGroupOptions(RadioGroup radioGroup, String[] options,
                                      String key, String defaultValue) {
        radioGroup.removeAllViews();
        String value = projectSettings.getValue(key, defaultValue);
        for (String option : options) {
            RadioButton rb = new RadioButton(radioGroup.getContext());
            rb.setText(option);
            rb.setId(View.generateViewId());
            rb.setLayoutParams(new RadioGroup.LayoutParams(0, -2, 1f));
            if (value.equals(option)) rb.setChecked(true);
            rb.setOnCheckedChangeListener((btn, isChecked) -> {
                if (!isChecked) return;
                if (key.equals(SETTING_JAVA_VERSION)) handleJavaVersionChange(option);
            });
            radioGroup.addView(rb);
        }
    }

    private void setCheckboxValue(CheckBox checkBox, String key, boolean defaultValue) {
        String value = projectSettings.getValue(key, defaultValue ? "true" : "false");
        checkBox.setChecked(value.equals("true"));
        checkBox.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked && key.equals(SETTING_NO_HTTP_LEGACY)) {
                SketchwareUtil.toast("Note that this option may cause issues if RequestNetwork component is used");
            }
        });
    }
}
