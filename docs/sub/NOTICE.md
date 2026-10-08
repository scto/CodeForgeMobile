# NOTICE

Die Module `:libs:termux-app`, `:libs:termux-shared`, `:libs:termux-view`
und `:libs:termux-emulator` in diesem Verzeichnis sind vendorter und auf den
Namespace `com.codeforge` umgeschriebener Code, ursprünglich aus:

- Termux (https://github.com/termux/termux-app, https://github.com/termux/termux-app/tree/master/termux-shared, etc.)
- in der Variante von scto/AndroidIDE, dev-Branch (https://github.com/scto/AndroidIDE/tree/dev/termux)

**Lizenz: GNU General Public License v3.0 (GPLv3)**, siehe `LICENSE` in
diesem Verzeichnis.

Da dieser Code vendort und direkt in CodeForgeMobile gebaut/gelinkt wird,
ist **CodeForgeMobile als Gesamtprojekt ab Einbindung dieser Module
GPLv3-pflichtig** (Copyleft wirkt auf das gesamte verlinkte Werk). Dies war
eine explizite, bewusste Entscheidung (siehe architecture-decisions.md,
Abschnitt "Termux-Ersatz").

Ausnahme: `TermuxConstants.java` und ursprünglich auch
`TermuxPropertyConstants.java` sind im Original MIT-lizenziert — das ändert
aber nichts an der Gesamt-GPLv3-Pflicht des restlichen Codes.

## Namespace-Änderungen

Alle Vorkommen von `com.termux` wurden mechanisch durch `com.codeforge`
ersetzt (Package-Deklarationen, Imports, Pfad-Konstanten, native
JNI-Bindings `RegisterNatives`, AndroidManifest-Referenzen). Fork-spezifische
AndroidIDE-Branding-Werte (`TERMUX_APP_NAME`, `TERMUX_PACKAGE_NAME`,
`TERMUX_GITHUB_ORGANIZATION_NAME`, `TERMUX_GITHUB_REPO_NAME`) wurden manuell
auf CodeForge-Werte umgestellt (siehe TERMUX-PORTING.md).

**Korrektur gegenüber einer früheren Vorab-Version dieser Datei:**
`TERMUX_PACKAGE_NAME` ist auf `com.codeforge.app` gesetzt — das ist der
tatsächliche `applicationId` von `:app` in diesem Projekt (nicht
`com.codeforge`). Alle `TERMUX_*_DIR_PATH`-Konstanten in
`TermuxConstants.java` sowie der Bootstrap-Build-Präfix für
`terminal-packages-codeforge` richten sich danach.
