package mod.hey.studios.build;

import java.io.Serializable;

import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.utility.FileUtil;

public class BuildSettings extends ProjectSettings implements Serializable {

    public static final String SETTING_ANDROID_JAR_PATH = "android_jar";
    public static final String SETTING_CLASSPATH = "classpath";
    public static final String SETTING_DEXER = "dexer";
    public static final String SETTING_JAVA_VERSION = "java_ver";
    public static final String SETTING_NO_HTTP_LEGACY = "no_http_legacy";
    public static final String SETTING_NO_WARNINGS = "no_warn";
    public static final String SETTING_ENABLE_LOGCAT = "enable_logcat";

    public static final String SETTING_DEXER_D8 = "D8";
    public static final String SETTING_DEXER_DX = "Dx";
    public static final String SETTING_JAVA_VERSION_1_7 = "1.7";
    public static final String SETTING_JAVA_VERSION_1_8 = "1.8";
    public static final String SETTING_JAVA_VERSION_1_9 = "1.9";
    public static final String SETTING_JAVA_VERSION_10 = "10";
    public static final String SETTING_JAVA_VERSION_11 = "11";

    // ── Signing settings ──────────────────────────────────────────────────────
    public static final String SETTING_SIGNING_ENABLED       = "signing_enabled";
    public static final String SETTING_SIGNING_KEYSTORE_PATH = "signing_keystore_path";
    public static final String SETTING_SIGNING_KEYSTORE_PASS = "signing_keystore_pass";
    public static final String SETTING_SIGNING_KEY_ALIAS     = "signing_key_alias";
    public static final String SETTING_SIGNING_KEY_PASS      = "signing_key_pass";

    // ── Advanced build settings ───────────────────────────────────────────────
    /** Keep CPU alive during background build via WakeLock. Default: true */
    public static final String SETTING_BACKGROUND_BUILD    = "background_build";
    /** Show the compiling file name in the build notification. Default: true */
    public static final String SETTING_NOTIF_SHOW_FILE     = "notif_show_file";
    /** Skip full compile and always use batch mode. Default: false */
    public static final String SETTING_FORCE_BATCH_COMPILE = "force_batch_compile";

    public BuildSettings(String sc_id) {
        super(sc_id);
    }

    @Override
    public String getPath() {
        return FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/build_config";
    }
}
