package com.codeforge.shared.termux.shell.command.environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.codeforge.shared.termux.TermuxConstants;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Entwicklungsumgebung (Android-SDK, JDK, Gradle) der Termux-Shell.
 *
 * Ersetzt {@code dev.mutwakil.androidide.utils.Environment} (AndroidIDE, GPLv3). Dessen
 * {@code putEnvironment(env, forFailsafe)} setzte HOME, ANDROID_HOME, ANDROID_SDK_ROOT,
 * ANDROID_USER_HOME, JAVA_HOME, GRADLE_USER_HOME, SYSROOT, PROJECTS sowie (nicht-failsafe)
 * TERMUX_PKG_NO_MIRROR_SELECT.
 *
 * Abweichungen vom Original (bewusst, an das CodeForge-Layout angepasst):
 * <ul>
 *   <li>ANDROID_HOME = {@code $PREFIX/opt/android-sdk} (AndroidIDE: {@code $HOME/android-sdk}) – das ist
 *       der Root, den das Skript {@code codeforge-env} befüllt ({@code TermuxEnvironment.sdkRoot}).</li>
 *   <li>JAVA_HOME = höchste installierte {@code $PREFIX/lib/jvm/java-N-openjdk} (AndroidIDE: fest
 *       java-21); wird nur gesetzt, wenn ein JDK existiert.</li>
 *   <li>PROJECTS = {@code <filesDir>/projects} (= {@code TemplateEngineRepository.defaultProjectsDirectory()}).</li>
 *   <li>HOME wird nicht erneut gesetzt (macht {@link TermuxShellEnvironment}).</li>
 * </ul>
 * <p>Benutzerkonfiguration: Optionale Datei {@code $PREFIX/etc/codeforge-environment.properties}
 * (manuell oder durch Tools gepflegt) (JAVA_HOME, ANDROID_SDK_ROOT,
 * GRADLE_USER_HOME, AAPT2_HOME, beliebige weitere Keys). Diese Datei hat Vorrang vor den Standardwerten
 * oben (außer im Failsafe-Modus); {@code $HOME}, {@code $PREFIX}, {@code $SYSROOT} werden aufgelöst.
 *
 * Reihenfolge-Quelle der Variablen: AndroidIDE {@code Environment.kt}, Stand der Datei im Projekt-Upload.
 */
public final class TermuxDevEnvironment {

    /** SDK-Root relativ zum Prefix; muss mit {@code SDK_ROOT} in assets/codeforge-env übereinstimmen. */
    public static final String SDK_ROOT_RELATIVE_PATH = "opt/android-sdk";

    /** Optionale Override-Datei, relativ zum Prefix. */
    public static final String ENV_PROPERTIES_RELATIVE_PATH = "etc/codeforge-environment.properties";

    private static final Pattern VAR_REF = Pattern.compile("\\$\\{(\\w+)\\}|\\$(\\w+)");
    private static final Pattern JDK_DIR = Pattern.compile("java-(\\d+)-openjdk");

    private TermuxDevEnvironment() {}

    @NonNull
    public static File getEnvPropertiesFile() {
        return new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, ENV_PROPERTIES_RELATIVE_PATH);
    }

    /**
     * Liest {@code codeforge-environment.properties} (Format {@code KEY=VALUE}, {@code #}-Kommentare).
     * Fehlende/unlesbare Datei → leere Map. {@code $HOME}/{@code $PREFIX}/{@code $SYSROOT} und
     * zuvor definierte Keys werden in Werten aufgelöst.
     */
    @NonNull
    public static Map<String, String> readEnvProperties() {
        Map<String, String> props = new LinkedHashMap<>();
        File file = getEnvPropertiesFile();
        if (!file.isFile()) return props;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                int eq = line.indexOf('=');
                if (line.isEmpty() || line.startsWith("#") || eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                        || value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                if (!value.isEmpty()) props.put(key, expand(value, props));
            }
        } catch (java.io.IOException e) {
            // Unlesbar → wie nicht vorhanden behandeln; die Shell startet mit den Standardwerten.
            props.clear();
        }
        return props;
    }

    @NonNull
    private static String expand(@NonNull String value, @NonNull Map<String, String> known) {
        Matcher m = VAR_REF.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(1) != null ? m.group(1) : m.group(2);
            String replacement;
            switch (name) {
                case "HOME": replacement = TermuxConstants.TERMUX_HOME_DIR_PATH; break;
                case "PREFIX":
                case "SYSROOT": replacement = TermuxConstants.TERMUX_PREFIX_DIR_PATH; break;
                default: replacement = known.get(name);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement != null ? replacement : m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * SDK-Root: {@code ANDROID_SDK_ROOT} (bzw. {@code ANDROID_HOME}) aus der Properties-Datei, sonst
     * {@code $PREFIX/opt/android-sdk}.
     */
    @NonNull
    public static String getSdkRoot() {
        Map<String, String> props = readEnvProperties();
        String fromProps = props.get("ANDROID_SDK_ROOT");
        if (fromProps == null) fromProps = props.get("ANDROID_HOME");
        return fromProps != null ? fromProps : TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/" + SDK_ROOT_RELATIVE_PATH;
    }

    @NonNull
    public static String getGradleUserHome() {
        return TermuxConstants.TERMUX_HOME_DIR_PATH + "/.gradle";
    }

    @NonNull
    public static String getAndroidUserHome() {
        return TermuxConstants.TERMUX_HOME_DIR_PATH + "/.android";
    }

    @NonNull
    public static String getProjectsDir() {
        return TermuxConstants.TERMUX_FILES_DIR_PATH + "/projects";
    }

    /** JDK-Verzeichnis eines Termux-{@code openjdk-N}-Pakets. */
    @NonNull
    public static String getJdkHome(@NonNull String version) {
        return TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/lib/jvm/java-" + version + "-openjdk";
    }

    /** Höchste installierte JDK ({@code bin/java} vorhanden) oder {@code null}. */
    @Nullable
    public static String findJavaHome() {
        File[] dirs = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/lib/jvm").listFiles();
        if (dirs == null) return null;
        int best = -1;
        File bestDir = null;
        for (File dir : dirs) {
            Matcher m = JDK_DIR.matcher(dir.getName());
            if (!m.matches() || !new File(dir, "bin/java").isFile()) continue;
            int version = Integer.parseInt(m.group(1));
            if (version > best) {
                best = version;
                bestDir = dir;
            }
        }
        return bestDir == null ? null : bestDir.getAbsolutePath();
    }

    /**
     * PATH-Ergänzungen für das SDK. Nur existierende Verzeichnisse – ein (noch) nicht installiertes
     * SDK darf den Shell-Start nicht beeinflussen.
     */
    @NonNull
    public static List<String> getSdkPathEntries() {
        List<String> entries = new ArrayList<>();
        String cmdlineTools = getSdkRoot() + "/cmdline-tools/latest/bin";
        if (new File(cmdlineTools).isDirectory()) entries.add(cmdlineTools);
        return entries;
    }

    /**
     * Entspricht {@code Environment.putEnvironment(env, forFailsafe)} aus AndroidIDE.
     *
     * @param forFailsafe {@code true} im Failsafe-Modus (nur die Basis-Variablen).
     */
    public static void putEnvironment(@NonNull Map<String, String> env, boolean forFailsafe) {
        String sdkRoot = getSdkRoot();
        env.put("ANDROID_HOME", sdkRoot);
        env.put("ANDROID_SDK_ROOT", sdkRoot);
        env.put("ANDROID_USER_HOME", getAndroidUserHome());
        env.put("GRADLE_USER_HOME", getGradleUserHome());
        env.put("SYSROOT", TermuxConstants.TERMUX_PREFIX_DIR_PATH);
        env.put("PROJECTS", getProjectsDir());

        String javaHome = findJavaHome();
        if (javaHome != null) env.put("JAVA_HOME", javaHome);

        if (!forFailsafe) {
            env.put("TERMUX_PKG_NO_MIRROR_SELECT", "true");
            // Benutzerwerte aus der Properties-Datei haben Vorrang (JAVA_HOME, AAPT2_HOME, …).
            Map<String, String> props = readEnvProperties();
            env.putAll(props);
            if (props.containsKey("ANDROID_SDK_ROOT") && !props.containsKey("ANDROID_HOME")) {
                env.put("ANDROID_HOME", props.get("ANDROID_SDK_ROOT"));
            }
        }
    }

    /** PATH mit angehängten SDK-Verzeichnissen (z. B. für {@code sdkmanager}). */
    @NonNull
    public static String appendSdkToPath(@NonNull String path) {
        List<String> entries = getSdkPathEntries();
        if (entries.isEmpty()) return path;
        StringBuilder sb = new StringBuilder(path);
        for (String e : entries) {
            if (sb.length() > 0) sb.append(':');
            sb.append(e);
        }
        return sb.toString();
    }

}
