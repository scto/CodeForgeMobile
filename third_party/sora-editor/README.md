# sora-editor (Fork mit Code-Folding)

CodeForgeMobile nutzt sora-editor aus dem Fork
[scto/codeforgemobile-sora-editor](https://github.com/scto/codeforgemobile-sora-editor) (0.24.7 mit Code-Folding,
Gutter-Faltmarken, Faltellipse). Der Fork ist nicht auf Maven Central; die gebauten Artefakte liegen hier unter
`maven/` und werden über ein lokales Maven-Repository eingebunden (`settings.gradle.kts`, nur Gruppe
`io.github.rosemoe`). Version: `0.24.7-cfm.1`.

`cfm.patch` passt den Fork an die App an:

| Änderung | Grund |
|---|---|
| Kotlin-Sprach-/API-Version 2.1 statt 2.4 | Die App kompiliert mit Kotlin 2.1.21 und kann Metadaten von 2.4 nicht lesen |
| `kotlin-stdlib` 2.1.21, `kotlin.stdlib.default.dependency=false` | gleiche Kotlin-Laufzeit wie die App |
| `androidx.annotation` 1.9.1, `androidx.collection` 1.5.0 | die Versionen, die die App ohnehin auflöst |
| `?android:attr/colorControlNormal` in zwei Icons | `?attr/…` gibt es ohne AppCompat nicht (Ressourcenfehler) |
| Signieren nur mit `CFM_SIGN=true`, feste Version `0.24.7-cfm.1` | lokales Bauen ohne Schlüssel und CI-SNAPSHOT |
| compileSdk/targetSdk 36, Build-Tools 36 | die App kompiliert gegen SDK 36 (AGP 8.13); sonst verlangt das AAR compileSdk 37 |

Neu bauen (z. B. nach Änderungen am Fork):

```bash
ANDROID_HOME=/pfad/zum/sdk third_party/sora-editor/build.sh            # Commit aus FORK_COMMIT
ANDROID_HOME=/pfad/zum/sdk third_party/sora-editor/build.sh <commit>   # anderer Stand des Forks
```

Danach bei inhaltlichen Änderungen die Version in `cfm.patch` (`artifactVersion`) und in
`feature/editor/build.gradle.kts` erhöhen. Lizenz: LGPL 2.1 (wie sora-editor).
