#!/usr/bin/env python3
"""Signiert ein CodeForge-Plugin (Ordner oder ZIP) mit einem EC-P-256-Schlüssel und schreibt signature.json.

    python3 scripts/plugin-sign.py <plugin-ordner|plugin.zip> <schlüssel.pem>
    python3 scripts/plugin-sign.py --new-key <schlüssel.pem>      # neuen Schlüssel erzeugen

Signiert wird dieselbe Liste wie in der App (core/domain …/PluginSignatures.kt): je Datei außer signature.json eine
Zeile "<sha256>  <pfad>\\n", nach Pfad sortiert. Braucht nur `openssl` (Termux: pkg install openssl-tool).
Den Schlüssel geheim halten und sichern: Updates müssen mit demselben Schlüssel signiert werden.
"""
import base64, hashlib, json, os, shutil, subprocess, sys, tempfile, zipfile

SIG = "signature.json"


def manifest_of(root):
    rows = []
    for d, _, files in os.walk(root):
        for f in files:
            full = os.path.join(d, f)
            rel = os.path.relpath(full, root).replace(os.sep, "/")
            if rel == SIG:
                continue
            with open(full, "rb") as fh:
                rows.append((rel, hashlib.sha256(fh.read()).hexdigest()))
    return "".join(f"{h}  {p}\n" for p, h in sorted(rows))


def openssl(*args, data=None):
    return subprocess.run(["openssl", *args], input=data, capture_output=True, check=True).stdout


def sign_dir(root, key):
    data = manifest_of(root).encode()
    with tempfile.NamedTemporaryFile(delete=False) as tmp:
        tmp.write(data)
    try:
        signature = openssl("dgst", "-sha256", "-sign", key, tmp.name)
    finally:
        os.unlink(tmp.name)
    public = openssl("pkey", "-in", key, "-pubout", "-outform", "DER")
    with open(os.path.join(root, SIG), "w", encoding="utf-8") as f:
        json.dump({
            "algorithm": "SHA256withECDSA",
            "publicKey": base64.b64encode(public).decode(),
            "signature": base64.b64encode(signature).decode(),
        }, f, indent=2)
    return hashlib.sha256(public).hexdigest()


def sign_zip(path, key):
    work = tempfile.mkdtemp()
    try:
        with zipfile.ZipFile(path) as z:
            z.extractall(work)
        fp = sign_dir(work, key)
        tmp = path + ".tmp"
        with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as z:
            for d, _, files in os.walk(work):
                for f in sorted(files):
                    full = os.path.join(d, f)
                    z.write(full, os.path.relpath(full, work).replace(os.sep, "/"))
        os.replace(tmp, path)
        return fp
    finally:
        shutil.rmtree(work, ignore_errors=True)


def main(argv):
    if len(argv) == 3 and argv[1] == "--new-key":
        openssl("ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", argv[2])
        os.chmod(argv[2], 0o600)
        print(f"Schlüssel: {argv[2]} (geheim halten und sichern)")
        return 0
    if len(argv) != 3:
        print(__doc__)
        return 2
    target, key = argv[1], argv[2]
    fp = sign_zip(target, key) if target.endswith(".zip") else sign_dir(target, key)
    print(f"Signiert: {target}\nFingerabdruck: {':'.join(fp[i:i+2] for i in range(0, 16, 2)).upper()} …")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
