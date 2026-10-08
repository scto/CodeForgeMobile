/*
 * :libs:termux-emulator
 *
 * Vendorter, originaler Termux-Terminal-Emulator (com.termux.terminal →
 * com.codeforge.terminal). Quelle: scto/AndroidIDE (dev-Branch) termux/emulator,
 * GPLv3 — siehe docs/sub/LICENSE.termux und docs/sub/TERMUX-PORTING.md.
 *
 * Ersetzt den bisherigen, aus dem Termix-Projekt portierten
 * com.termux.terminal-Code in :libs:terminal-engine (siehe dortige
 * README-VENDORED.md / architecture-decisions.md) — dieser Code dedupliziert
 * das 1:1, nur mit dem echten, vollständigeren Termux/AndroidIDE-Ursprung.
 */

plugins {
    alias(libs.plugins.codeforge.android.library)
}

android {
    namespace = "com.codeforge.emulator"
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        externalNativeBuild {
            ndkBuild {
                cFlags("-std=c11", "-Wall", "-Wextra", "-Werror", "-Os", "-fno-stack-protector", "-Wl,--gc-sections")
            }
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging.jniLibs.useLegacyPackaging = true
}

dependencies {
    implementation(libs.androidx.annotation)
}
