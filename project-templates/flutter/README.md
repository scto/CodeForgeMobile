# Flutter-Vorlagen (Vorbereitung)

`upstream/fluttertemplates/` ist eine unveränderte Kopie von `flutter_apps/core` aus
[bimsina/fluttertemplates.dev](https://github.com/bimsina/fluttertemplates.dev) (MIT, Commit in `UPSTREAM_COMMIT`):

* **5 komplette Apps** (`lib/templates/`): `auth_flow`, `banking_wallet`, `food_delivery`, `retail_pos`, `team_chat`
  → werden Projektvorlagen im Assistenten.
* **~250 Screens/Widgets** in 38 Kategorien (`lib/<kategorie>/*.dart`, z. B. `auth/login_hero.dart`)
  → Widget-Galerie: Screen ins offene Flutter-Projekt einfügen (wie `fluttertemplates add auth/login_hero`).
* Abhängigkeiten: nur Flutter, `salomon_bottom_bar`, `concentric_transition`, `cupertino_icons`.

Noch nicht verdrahtet. Voraussetzungen: Flutter-SDK im SDK-Manager (nullij/flutter-sdk-android-arm64 über
codeforge-tools) und Projektvorlagen mit Sprache `dart` (ohne Gradle-DSL; `android/` erzeugt
`flutter create --platforms=android .` nach dem Anlegen). Lizenztext bei jeder erzeugten Vorlage mitliefern.
