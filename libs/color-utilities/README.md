# :libs:color-utilities

Googles [material-color-utilities](https://github.com/material-foundation/material-color-utilities) (Kotlin-Fassung,
Apache 2.0, siehe `LICENSE`), Stand Commit `1e3bae442f032e5a34a57be9239b8341c9c28d6e` (2026-11-23).

Einzige Änderung: Paketnamen mit `com.codeforge.libs.color_utilities.` vorangestellt (das Original nutzt nackte
Pakete wie `scheme`, `hct`). Grundlage für den Theme-Builder (`:feature:themebuilder`) und die App-Farbschemata –
dieselbe Bibliothek, die MaterialKolor und Material Theme Builder nutzen.

Aktualisieren: Kotlin-Quellen aus `kotlin/` des Originals kopieren und `scripts/vendor_color_utilities.sh` ausführen.
