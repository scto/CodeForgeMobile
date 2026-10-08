/*
 * :libs:termux-view
 *
 * Vendorte, originale Termux-TerminalView-UI (com.termux.view →
 * com.codeforge.view). Quelle: scto/AndroidIDE (dev-Branch) termux/view,
 * GPLv3 — siehe docs/sub/LICENSE.termux und docs/sub/TERMUX-PORTING.md.
 */

plugins {
    alias(libs.plugins.codeforge.android.library)
}

android {
    namespace = "com.codeforge.view"
    ndkVersion = libs.versions.ndk.get()

}

dependencies {
    api(project(":libs:termux-emulator"))
    implementation(libs.androidx.annotation)
}
