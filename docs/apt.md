---
hide:
  - navigation
  - toc
---

# APT-Repo

Signiertes Paket-Repository für die Termux-Shell von CodeForge Mobile (Präfix `com.codeforge.app`). Die App richtet es beim ersten Start ein; für eine manuelle Einbindung:

```bash
echo "deb https://scto.github.io/terminal-packages-codeforge stable main" > $PREFIX/etc/apt/sources.list
curl -fsSL https://scto.github.io/terminal-packages-codeforge/codeforge.asc -o $PREFIX/etc/apt/trusted.gpg.d/codeforge.asc
apt update
```

Schlüssel: [codeforge.asc](https://scto.github.io/terminal-packages-codeforge/codeforge.asc) · [Release](https://scto.github.io/terminal-packages-codeforge/dists/stable/Release) · [große Pakete](https://scto.github.io/terminal-packages-codeforge/large-packages.txt)

<div id="cf-apt" data-src="https://scto.github.io/terminal-packages-codeforge" data-archs="aarch64,arm"></div>

<noscript>Für die Paketliste wird JavaScript benötigt.</noscript>
