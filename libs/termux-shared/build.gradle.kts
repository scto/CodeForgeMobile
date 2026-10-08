/*
 * :libs:termux-shared
 *
 * Vendorter Termux-"shared"-Util-Layer (com.termux.shared →
 * com.codeforge.shared), inkl. des app-spezifischen Subpakets
 * `com.codeforge.shared.termux.*` (TermuxConstants, TermuxBootstrap,
 * TermuxShellManager, TermuxSession, Extra-Keys, Notifications, ...).
 * Quelle: scto/AndroidIDE (dev-Branch) termux/shared, GPLv3 — siehe
 * docs/sub/LICENSE.termux und docs/sub/TERMUX-PORTING.md.
 *
 * Enthält natives local-socket.cpp (GPLv3) für den Termux:API-Socket-Server.
 */

plugins {
    alias(libs.plugins.codeforge.android.library)
}

android {
    namespace = "com.codeforge.shared"
    ndkVersion = libs.versions.ndk.get()

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/Android.mk")
        }
    }
    lint.disable += "ProtectedPermissions"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging.jniLibs.useLegacyPackaging = true
}

dependencies {
    implementation(project(":libs:termux-view"))

    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.window) // BEGRÜNDUNG: ViewUtils.getDisplaySize() nutzt WindowMetricsCalculator statt AndroidIDEs eigenem WindowManager-Wrapper
    api(libs.androidx.appcompat) // BEGRÜNDUNG: ReportActivity/ActivityUtils/ThemeUtils u.a. exponieren AppCompatActivity-Typen über Modulgrenzen hinweg (auch von :libs:termux-app genutzt)
    implementation(libs.androidx.preference)
    implementation(libs.google.material)
    implementation(libs.google.guava)
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.strikethrough)
    implementation(libs.markwon.linkify)
    implementation(libs.markwon.recycler)
    implementation(libs.hidden.api.bypass) // BEGRÜNDUNG: von TermuxShellEnvironment für Reflection-Workarounds auf neueren API-Levels benötigt
}
