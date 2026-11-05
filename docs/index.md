---
hide:
  - navigation
  - toc
---

<div class="cf-hero" markdown>

<div class="cf-hero-row" markdown>
<img class="cf-basti-hero" src="assets/basti.gif" alt="Basti, ein Golden Retriever im Pixel-Stil, wedelt mit der Rute" width="128" height="128">
<div markdown>

# CodeForge Mobile

*Gewidmet [Basti](basti.md), meinem Golden Retriever.*

</div>
</div>

Native Android-IDE mit Sora Editor, Termux-Shell, Git, Language Server, Compose-Live-Preview und visuellem Layout-Designer – gebaut mit Kotlin, Jetpack Compose und Material 3 Expressive.

[APK herunterladen](https://github.com/scto/CodeForgeMobile/releases){ .md-button .md-button--primary }
[Anwenderhandbuch](user/Anwenderhandbuch.md){ .md-button }
[Quellcode](https://github.com/scto/CodeForgeMobile){ .md-button }

</div>

<div class="cf-grid" markdown>

<div class="cf-card" markdown>
### Store
<span class="cf-meta">Plugins · Themes · Werkzeuge</span>

Farbschemata, Icon-Pakete, Sprachpakete, LSP-Server, Formatierer und Werkzeug-Plugins. Der Katalog wird live geladen; die App nutzt denselben Katalog unter Einstellungen → Store.

[Zum Store](store.md){ .md-button .md-button--primary }
</div>

<div class="cf-card" markdown>
### APT-Repo
<span class="cf-meta">Pakete für die Termux-Shell</span>

Eigenes, signiertes APT-Repository für das Präfix `com.codeforge.app`: JDK, Build-Werkzeuge, Git und mehr. Durchsuchbar, mit Quellzeile zum Einbinden.

[Zum APT-Repo](apt.md){ .md-button .md-button--primary }
</div>

<div class="cf-card" markdown>
### Dokumentation
<span class="cf-meta">Handbücher · Entwickler · API</span>

Anwenderhandbuch (DE), User Guide (EN), Entwicklerhandbuch mit Modulgraph, APK-Dokumentation und die KDoc-API-Referenz.

[Zur Dokumentation](user/Anwenderhandbuch.md){ .md-button .md-button--primary }
[API-Referenz](api/index.html){ .md-button }
</div>

</div>

## Screenshots

| Startseite | Über CodeForge Mobile |
|:---:|:---:|
| ![Startseite (Hell)](screenshots/welcome-light.png#only-light){ width=240 }![Startseite (Dunkel)](screenshots/welcome-dark.png#only-dark){ width=240 } | ![Über (Hell)](screenshots/about-light.png#only-light){ width=240 }![Über (Dunkel)](screenshots/about-dark.png#only-dark){ width=240 } |

Die Bilder entstehen aus Compose-Screenshot-Tests mit Beispieldaten; Schriften und Systemleisten können auf einem Gerät abweichen.

!!! warning "Entwicklungsstand"
    Die Release-Pipeline läuft, die Release-APK ist aber noch nicht auf Geräten geprüft. Stand und offene Punkte: [Release Notes 3.0.4](release-notes/3.0.4.md).
