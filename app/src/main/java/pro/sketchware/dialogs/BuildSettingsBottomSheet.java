package pro.sketchware.dialogs;

import static mod.hey.studios.build.BuildSettings.*;

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

import mod.hey.studios.build.BuildSettings;
import pro.sketchware.databinding.ProjectConfigLayoutBinding;
import pro.sketchware.utility.SketchwareUtil;

public class BuildSettingsBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = BuildSettingsBottomSheet.class.getSimpleName();

    private ProjectConfigLayoutBinding binding;
    private BuildSettings projectSettings;

    private final ActivityResultLauncher<Intent> keystorePicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null && binding != null)
                        binding.etKeystorePath.setText(resolveRealPath(uri));
                }
            });

    public static BuildSettingsBottomSheet newInstance(String sc_id) {
        BuildSettingsBottomSheet sheet = new BuildSettingsBottomSheet();
        Bundle args = new Bundle();
        args.putString("sc_id", sc_id);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectSettings = new BuildSettings(requireArguments().getString("sc_id"));
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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
        binding.noWarnings.setOnClickListener(v -> binding.cbNoWarnings.performClick());
        binding.noHttpLegacy.setOnClickListener(v -> binding.cbNoHttpLegacy.performClick());
        binding.enableLogcat.setOnClickListener(v -> binding.cbEnableLogcat.performClick());

        binding.tilAndroidJar.getEditText().setText(projectSettings.getValue(SETTING_ANDROID_JAR_PATH, ""));
        binding.tilClasspath.getEditText().setText(projectSettings.getValue(SETTING_CLASSPATH, ""));

        setupRadioGroup(binding.rgDexer, new String[]{"Dx", "D8"}, SETTING_DEXER, "Dx");
        setupRadioGroup(binding.rgJavaVersion,
                new String[]{SETTING_JAVA_VERSION_1_7, SETTING_JAVA_VERSION_1_8,
                        SETTING_JAVA_VERSION_1_9, SETTING_JAVA_VERSION_10, SETTING_JAVA_VERSION_11},
                SETTING_JAVA_VERSION, SETTING_JAVA_VERSION_1_7);

        setCheckbox(binding.cbNoWarnings,   SETTING_NO_WARNINGS,   true);
        setCheckbox(binding.cbNoHttpLegacy, SETTING_NO_HTTP_LEGACY, false);
        setCheckbox(binding.cbEnableLogcat, SETTING_ENABLE_LOGCAT,  true);
    }

    // ── Signing tab ───────────────────────────────────────────────────────────

    private void setupSigningTab() {
        boolean enabled = "true".equals(projectSettings.getValue(SETTING_SIGNING_ENABLED, "false"));
        binding.cbSigningEnabled.setChecked(enabled);
        setSigningFieldsEnabled(enabled);

        binding.etKeystorePath.setText(projectSettings.getValue(SETTING_SIGNING_KEYSTORE_PATH, ""));
        binding.etKeystorePass.setText(projectSettings.getValue(SETTING_SIGNING_KEYSTORE_PASS, ""));
        binding.etKeyAlias.setText(projectSettings.getValue(SETTING_SIGNING_KEY_ALIAS, ""));
        binding.etKeyPass.setText(projectSettings.getValue(SETTING_SIGNING_KEY_PASS, ""));

        binding.llSigningEnabled.setOnClickListener(v -> binding.cbSigningEnabled.performClick());
        binding.cbSigningEnabled.setOnCheckedChangeListener((b, c) -> setSigningFieldsEnabled(c));
        binding.btnBrowseKeystore.setOnClickListener(v -> openKeystorePicker());
    }

    private void setSigningFieldsEnabled(boolean enabled) {
        binding.llKeystoreFields.setAlpha(enabled ? 1f : 0.4f);
        setGroupEnabled(binding.llKeystoreFields, enabled);
    }

    private void openKeystorePicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/octet-stream", "application/x-java-keystore"});
        try { keystorePicker.launch(i); }
        catch (Exception e) { SketchwareUtil.toastError("No file manager found"); }
    }

    private String resolveRealPath(Uri uri) {
        try {
            String docId = DocumentsContract.getDocumentId(uri);
            if (docId != null && docId.contains(":")) {
                String[] split = docId.split(":");
                if ("primary".equalsIgnoreCase(split[0])) {
                    return android.os.Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            }
        } catch (Exception ignored) {}
        return uri.toString();
    }

    // ── Advanced tab ──────────────────────────────────────────────────────────

    private void setupAdvancedTab() {
        // Background build
        boolean bgBuild    = "true".equals(projectSettings.getValue(SETTING_BACKGROUND_BUILD,    "true"));
        boolean notifFile  = "true".equals(projectSettings.getValue(SETTING_NOTIF_SHOW_FILE,     "true"));
        boolean forceBatch = "true".equals(projectSettings.getValue(SETTING_FORCE_BATCH_COMPILE, "false"));
        boolean compose    = "true".equals(projectSettings.getValue(SETTING_JETPACK_COMPOSE,     "false"));

        binding.cbBackgroundBuild.setChecked(bgBuild);
        binding.cbNotifShowFile.setChecked(notifFile);
        binding.cbForceBatch.setChecked(forceBatch);
        binding.cbJetpackCompose.setChecked(compose);

        binding.llBackgroundBuild.setOnClickListener(v -> binding.cbBackgroundBuild.performClick());
        binding.llNotifShowFile.setOnClickListener(v -> binding.cbNotifShowFile.performClick());
        binding.llForceBatch.setOnClickListener(v -> binding.cbForceBatch.performClick());
        binding.llJetpackCompose.setOnClickListener(v -> binding.cbJetpackCompose.performClick());

        // Compose version fields
        binding.etComposeCompiler.setText(projectSettings.getValue(
                SETTING_COMPOSE_COMPILER_VER, DEFAULT_COMPOSE_COMPILER_VER));
        binding.etComposeBom.setText(projectSettings.getValue(
                SETTING_COMPOSE_BOM_VER, DEFAULT_COMPOSE_BOM_VER));

        setComposeFieldsEnabled(compose);
        binding.cbJetpackCompose.setOnCheckedChangeListener((b, checked) -> {
            setComposeFieldsEnabled(checked);
            if (checked) {
                SketchwareUtil.toast("Compose needs Kotlin + kotlinc bundled in your Sketchware build");
            }
        });
    }

    private void setComposeFieldsEnabled(boolean enabled) {
        binding.llComposeVersions.setAlpha(enabled ? 1f : 0.4f);
        setGroupEnabled(binding.llComposeVersions, enabled);
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────

    private void setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Build"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Signing"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Advanced"));

        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                int pos = tab.getPosition();
                binding.scrollBuild.setVisibility(pos == 0 ? View.VISIBLE : View.GONE);
                binding.scrollSigning.setVisibility(pos == 1 ? View.VISIBLE : View.GONE);
                binding.scrollAdvanced.setVisibility(pos == 2 ? View.VISIBLE : View.GONE);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void saveAll() {
        // Build
        projectSettings.setValue(SETTING_ANDROID_JAR_PATH,
                text(binding.tilAndroidJar.getEditText()));
        projectSettings.setValue(SETTING_CLASSPATH,
                text(binding.tilClasspath.getEditText()));
        projectSettings.setValue(SETTING_DEXER,       checkedRadioText(binding.rgDexer, "Dx"));
        projectSettings.setValue(SETTING_JAVA_VERSION, checkedRadioText(binding.rgJavaVersion, "1.7"));
        projectSettings.setValue(SETTING_NO_WARNINGS,   bool(binding.cbNoWarnings));
        projectSettings.setValue(SETTING_NO_HTTP_LEGACY, bool(binding.cbNoHttpLegacy));
        projectSettings.setValue(SETTING_ENABLE_LOGCAT,  bool(binding.cbEnableLogcat));

        // Signing
        projectSettings.setValue(SETTING_SIGNING_ENABLED,       bool(binding.cbSigningEnabled));
        projectSettings.setValue(SETTING_SIGNING_KEYSTORE_PATH,  text(binding.etKeystorePath));
        projectSettings.setValue(SETTING_SIGNING_KEYSTORE_PASS,  text(binding.etKeystorePass));
        projectSettings.setValue(SETTING_SIGNING_KEY_ALIAS,      text(binding.etKeyAlias));
        projectSettings.setValue(SETTING_SIGNING_KEY_PASS,       text(binding.etKeyPass));

        // Advanced
        projectSettings.setValue(SETTING_BACKGROUND_BUILD,    bool(binding.cbBackgroundBuild));
        projectSettings.setValue(SETTING_NOTIF_SHOW_FILE,     bool(binding.cbNotifShowFile));
        projectSettings.setValue(SETTING_FORCE_BATCH_COMPILE, bool(binding.cbForceBatch));
        projectSettings.setValue(SETTING_JETPACK_COMPOSE,     bool(binding.cbJetpackCompose));
        projectSettings.setValue(SETTING_COMPOSE_COMPILER_VER, text(binding.etComposeCompiler));
        projectSettings.setValue(SETTING_COMPOSE_BOM_VER,      text(binding.etComposeBom));

        dismiss();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String text(android.widget.EditText et) {
        return et != null && et.getText() != null ? et.getText().toString().trim() : "";
    }

    private String bool(CheckBox cb) { return Boolean.toString(cb.isChecked()); }

    private String checkedRadioText(RadioGroup rg, String def) {
        int id = rg.getCheckedRadioButtonId();
        if (id < 0) return def;
        RadioButton rb = rg.findViewById(id);
        return rb != null ? rb.getText().toString() : def;
    }

    private void setupRadioGroup(RadioGroup rg, String[] options, String key, String def) {
        rg.removeAllViews();
        String val = projectSettings.getValue(key, def);
        for (String opt : options) {
            RadioButton rb = new RadioButton(rg.getContext());
            rb.setText(opt);
            rb.setId(View.generateViewId());
            rb.setLayoutParams(new RadioGroup.LayoutParams(0, -2, 1f));
            if (opt.equals(val)) rb.setChecked(true);
            rg.addView(rb);
        }
    }

    private void setCheckbox(CheckBox cb, String key, boolean def) {
        cb.setChecked("true".equals(projectSettings.getValue(key, Boolean.toString(def))));
    }

    private void setGroupEnabled(ViewGroup group, boolean enabled) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setEnabled(enabled);
            if (child instanceof ViewGroup) setGroupEnabled((ViewGroup) child, enabled);
        }
    }
}
