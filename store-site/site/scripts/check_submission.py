#!/usr/bin/env python3
"""Prüft eine Plugin-Einreichung (Issue-Formular „Plugin einreichen“) und schreibt einen Bericht (Markdown).

Automatisch geprüft werden Archiv, Manifest und die Store-Regeln (wie in der App: DeclarativeOnly/SandboxPolicy).
Die Übernahme in den Store bleibt eine Entscheidung der Maintainer nach Durchsicht des Inhalts.

Aufruf (Workflow):  python3 scripts/check_submission.py <issue-body.md> <bericht.md>
Exit 0 = Prüfung bestanden, 1 = Fehler (Bericht nennt sie).
"""
import hashlib
import io
import json
import re
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAX_BYTES = 20 * 1024 * 1024
ID_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]*$")
VERSION_RE = re.compile(r"^\d+(\.\d+){0,3}([-+][0-9A-Za-z.-]+)?$")
CODE_SUFFIXES = (".dex", ".jar", ".so", ".apk", ".class", ".aar", ".sh")
ATTACHMENT_RE = re.compile(r"https://github\.com/(?:user-attachments/files|[^/\s]+/[^/\s]+/files)/[^\s)\"']+\.zip")
TEXT_PATH_KINDS = ("colorThemes", "iconThemes", "terminalThemes", "languagePacks", "grammars", "snippets")


def field(body, label):
    """Wert eines Formularfelds (Überschrift `### <label>` bis zur nächsten Überschrift)."""
    m = re.search(r"^###\s+" + re.escape(label) + r"[^\n]*\n(.*?)(?=^###\s|\Z)", body, re.S | re.M)
    value = m.group(1).strip() if m else ""
    return "" if value == "_No response_" else value


def version_key(v):
    return [int(x) for x in re.findall(r"\d+", v.split("-")[0].split("+")[0])]


def check_archive(data, form_id="", form_version="", catalog=None):
    """Prüft das ZIP; liefert (fehler, hinweise, manifest)."""
    errors, notes = [], []
    if len(data) > MAX_BYTES:
        return [f"Archiv größer als {MAX_BYTES // 1024 // 1024} MiB"], notes, None
    try:
        z = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile:
        return ["Kein gültiges ZIP-Archiv"], notes, None
    names = [n for n in z.namelist() if not n.endswith("/")]
    for n in names:
        if n.startswith("/") or ".." in Path(n).parts or "\\" in n:
            errors.append(f"Unzulässiger Pfad im Archiv: `{n}`")
    if "plugin.json" not in names:
        return errors + ["`plugin.json` fehlt auf oberster Ebene"], notes, None
    try:
        m = json.loads(z.read("plugin.json").decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as e:
        return errors + [f"`plugin.json` ist kein gültiges JSON: {e}"], notes, None
    for n in names:
        if n.endswith(".json") and n != "plugin.json":
            try:
                json.loads(z.read(n).decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError) as e:
                errors.append(f"`{n}` ist kein gültiges JSON: {e}")

    pid, version = m.get("id", ""), str(m.get("version", ""))
    if not isinstance(pid, str) or not ID_RE.match(pid):
        errors.append("`id` fehlt oder ist ungültig")
    elif pid.startswith("com.codeforge."):
        errors.append("IDs mit `com.codeforge.` sind dem Projekt vorbehalten – eigenes Präfix wählen")
    if not VERSION_RE.match(version):
        errors.append("`version` fehlt oder ist ungültig (z. B. 1.0.0)")
    for key in ("name", "publisher"):
        if not str(m.get(key, "")).strip():
            errors.append(f"`{key}` fehlt")
    api = m.get("apiVersion")
    if api not in (2, 3, 4):
        errors.append("`apiVersion` muss 2, 3 oder 4 sein (API 1 ist abgekündigt)")
    if form_id and pid and form_id != pid:
        errors.append(f"Formular-ID `{form_id}` ≠ `id` in plugin.json `{pid}`")
    if form_version and version and form_version != version:
        errors.append(f"Formular-Version `{form_version}` ≠ `version` in plugin.json `{version}`")

    contributes = m.get("contributes") or {}
    sandbox = m.get("sandbox") is True
    if m.get("entryPointClass"):
        if not sandbox:
            errors.append("Code-Plugin ohne `\"sandbox\": true` – der Store nimmt nur Sandbox-Code an (sonst Sideload)")
        elif "classes.dex" not in names:
            errors.append("Sandbox-Plugin ohne `classes.dex`")
    for n in names:
        allowed = sandbox and n == "classes.dex"
        if n.lower().endswith(CODE_SUFFIXES) and not allowed:
            errors.append(f"Ausführbare Datei nicht erlaubt: `{n}` (Skripte für `tools` als Python)")
    if contributes.get("agentTools") or contributes.get("mcpServers"):
        errors.append("Agent-Werkzeuge und MCP-Server sind nur per Sideload erlaubt")
    if not contributes and not m.get("entryPointClass"):
        errors.append("Keine Beiträge (`contributes`) und kein Code")
    if contributes.get("features"):
        errors.append("`features` schaltet eingebaute App-Funktionen frei und ist offiziellen CodeForge-Plugins vorbehalten")
    if contributes.get("tools"):
        notes.append("Enthält Werkzeuge (`tools`, Berechtigung `shell`) – Skripte bei der Durchsicht lesen")
    for kind in TEXT_PATH_KINDS:
        for item in contributes.get(kind) or []:
            path = item.get("path", "") if isinstance(item, dict) else ""
            if path not in names:
                errors.append(f"`{kind}` verweist auf fehlende Datei `{path}`")

    if catalog and pid:
        known = {i["id"]: i for i in catalog.get("items", [])}
        if pid in known:
            published = str(known[pid].get("version", "0"))
            if version_key(version) <= version_key(published):
                errors.append(f"Version {version} ist nicht höher als die veröffentlichte {published}")
            notes.append(f"Update eines vorhandenen Pakets (bisher {published}, Herausgeber {known[pid].get('author', '?')})")
    perms = m.get("permissions") or []
    if perms:
        notes.append("Berechtigungen: " + ", ".join(f"`{p}`" for p in perms))
    return errors, notes, m


def report(errors, notes, manifest, data, url):
    lines = ["## Automatische Prüfung", ""]
    if manifest:
        lines += [f"- **ID:** `{manifest.get('id')}`", f"- **Version:** `{manifest.get('version')}`",
                  f"- **API:** {manifest.get('apiVersion')}", f"- **Art:** " +
                  ("Sandbox-Code" if manifest.get("entryPointClass") else "deklarativ")]
    if data:
        lines += [f"- **Größe:** {len(data) // 1024} KiB", f"- **SHA-256:** `{hashlib.sha256(data).hexdigest()}`"]
    if url:
        lines.append(f"- **Archiv:** {url}")
    lines.append("")
    if errors:
        lines += ["### ❌ Fehler", ""] + [f"- {e}" for e in errors] + ["",
                  "Bitte korrigieren, neu bauen (`./gradlew packagePlugin`) und das Issue mit dem neuen ZIP bearbeiten."]
    else:
        lines += ["### ✅ Prüfung bestanden", "", "Ein Maintainer sieht sich den Inhalt an und übernimmt das Paket."]
    if notes:
        lines += ["", "### Hinweise", ""] + [f"- {n}" for n in notes]
    return "\n".join(lines) + "\n"


def main(body_file, out_file):
    body = Path(body_file).read_text(encoding="utf-8")
    urls = ATTACHMENT_RE.findall(body)
    catalog_file = ROOT / "store.json"
    catalog = json.loads(catalog_file.read_text(encoding="utf-8")) if catalog_file.is_file() else None
    if not urls:
        Path(out_file).write_text(report(["Kein ZIP-Anhang gefunden (ZIP in das Feld „Plugin-ZIP“ ziehen)"], [], None, None, None),
                                  encoding="utf-8")
        return 1
    url = urls[0]
    with urllib.request.urlopen(url, timeout=60) as r:  # noqa: S310 – nur github.com-Anhänge (Regex)
        data = r.read(MAX_BYTES + 1)
    errors, notes, manifest = check_archive(data, field(body, "Plugin-ID"), field(body, "Version"), catalog)
    Path(out_file).write_text(report(errors, notes, manifest, data, url), encoding="utf-8")
    return 1 if errors else 0


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    sys.exit(main(sys.argv[1], sys.argv[2]))
