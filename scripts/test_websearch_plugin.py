#!/usr/bin/env python3
"""Tests für examples/web-search-plugin/websearch.py ohne Netz (Parser, Text, Adressprüfung)."""
import importlib.util
import pathlib
import sys

sys.dont_write_bytecode = True  # kein __pycache__ im Plugin-Ordner (würde sonst mit gepackt)
ROOT = pathlib.Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("websearch", ROOT / "examples/web-search-plugin/websearch.py")
ws = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ws)

PAGE = """
<html><body>
<div class="result results_links results_links_deep result--ad">
  <h2 class="result__title"><a class="result__a" href="https://duckduckgo.com/y.js?ad=1">Anzeige</a></h2>
  <a class="result__snippet" href="#">Werbung</a>
</div>
<div class="result results_links results_links_deep web-result ">
  <div class="links_main links_deep result__body">
    <h2 class="result__title">
      <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fdeveloper.android.com%2Fcompose%3Fa%3D1%26b%3D2&amp;rut=abc">Jetpack <b>Compose</b> &amp; Lists</a>
    </h2>
    <a class="result__snippet" href="#">Use <b>LazyColumn</b> for long
       lists.</a>
  </div>
</div>
<div class="result results_links results_links_deep web-result ">
  <h2 class="result__title"><a class="result__a" href="https://kotlinlang.org/docs/">Kotlin Docs</a></h2>
  <div class="result__snippet">Kotlin <span>language</span> docs</div>
</div>
<div class="result results_links results_links_deep web-result ">
  <h2 class="result__title"><a class="result__a" href="https://kotlinlang.org/docs/">Kotlin Docs (doppelt)</a></h2>
</div>
</body></html>
"""

failures = 0


def check(name, cond, detail=""):
    global failures
    if not cond:
        failures += 1
        print(f"FAIL {name} {detail}")


results, blocked = ws.parse_results(PAGE)
check("anzahl", len(results) == 2, results)
check("kein block", not blocked)
check("titel", results[0]["title"] == "Jetpack Compose & Lists", results[0])
check("uddg", results[0]["url"] == "https://developer.android.com/compose?a=1&b=2", results[0]["url"])
check("snippet", results[0]["snippet"] == "Use LazyColumn for long lists.", results[0]["snippet"])
check("div-snippet", results[1]["snippet"] == "Kotlin language docs", results[1])
check("limit", len(ws.parse_results(PAGE, limit=1)[0]) == 1)
check("captcha", ws.parse_results('<div class="anomaly-modal">x</div>')[1])

check("query n", ws.parse_query("compose lazy | 3") == ("compose lazy", 3))
check("query cap", ws.parse_query("x | 99") == ("x", 8))
check("query plain", ws.parse_query("  a | b  ") == ("a | b", 8))

title, text = ws.extract_text(
    "<html><head><title> Seite </title><style>.x{}</style></head><body><nav>Menü</nav>"
    "<h1>Kopf</h1><p>Erster   Absatz</p><script>alert(1)</script><ul><li>eins</li><li>zwei</li></ul></body></html>"
)
check("titel text", title == "Seite", title)
check("text", text == "Kopf\nErster Absatz\neins\nzwei", repr(text))


def rejected(url):
    try:
        ws.check_public(url)
    except SystemExit:
        return True
    return False


for bad in ("file:///etc/passwd", "ftp://example.org/", "http://localhost:11434/", "http://127.0.0.1/", "http://10.0.0.1/",
            "http://192.168.1.1/", "http://[::1]/", "http://169.254.169.254/latest/"):
    check("blockiert " + bad, rejected(bad))

print("PASS: websearch" if failures == 0 else f"{failures} Fehler")
sys.exit(1 if failures else 0)
