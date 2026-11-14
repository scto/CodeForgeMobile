#!/usr/bin/env python3
"""Websuche (DuckDuckGo) und Seitenabruf für den CodeForge-Agenten. Nur Standardbibliothek.

Aufruf:  websearch.py search "<suchbegriffe>[ | n]"
         websearch.py fetch  "<https://adresse>"
Ausgabe als Text auf stdout; Fehler auf stderr mit Exit-Code 1.
"""
import html
import ipaddress
import re
import socket
import sys
import urllib.error
import urllib.parse
import urllib.request
from html.parser import HTMLParser

USER_AGENT = "Mozilla/5.0 (Linux; Android 14) CodeForgeMobile-WebSearch/1.0"
SEARCH_URL = "https://html.duckduckgo.com/html/"
TIMEOUT = 15
MAX_RESULTS = 8
MAX_DOWNLOAD = 2 * 1024 * 1024
MAX_TEXT = 12_000
MAX_REDIRECTS = 5


class SearchResultParser(HTMLParser):
    """Liest Treffer aus der HTML-Ergebnisseite von DuckDuckGo; Anzeigen werden markiert."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.results = []
        self.blocked = False
        self._current = None
        self._field = None      # "title" oder "snippet"
        self._field_tag = None  # Tag, das das Feld geöffnet hat
        self._depth = 0         # gleichnamige verschachtelte Tags im Feld

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        cls = (a.get("class") or "").split()
        if "anomaly-modal" in cls or a.get("id") == "challenge-form":
            self.blocked = True
        if self._field and tag == self._field_tag:
            self._depth += 1
            return
        if tag == "div" and "result" in cls and "results_links" in cls:
            self._current = {"title": "", "url": "", "snippet": "", "ad": "result--ad" in cls}
            self.results.append(self._current)
        elif self._current is not None and tag == "a" and "result__a" in cls:
            self._current["url"] = resolve_link(a.get("href", ""))
            self._open("title", tag)
        elif self._current is not None and "result__snippet" in cls:
            self._open("snippet", tag)

    def _open(self, field, tag):
        self._field, self._field_tag, self._depth = field, tag, 0

    def handle_endtag(self, tag):
        if self._field and tag == self._field_tag:
            if self._depth:
                self._depth -= 1
            else:
                self._field = None

    def handle_data(self, data):
        if self._current is not None and self._field:
            self._current[self._field] += data


def resolve_link(href):
    """DuckDuckGo verlinkt über //duckduckgo.com/l/?uddg=<ziel>; liefert das Ziel."""
    href = html.unescape(href or "")
    if href.startswith("//"):
        href = "https:" + href
    parsed = urllib.parse.urlparse(href)
    if parsed.netloc.endswith("duckduckgo.com") and parsed.path.startswith("/l/"):
        target = urllib.parse.parse_qs(parsed.query).get("uddg", [""])[0]
        return target or href
    return href


def clean(text):
    return re.sub(r"\s+", " ", text or "").strip()


def parse_results(page, limit=MAX_RESULTS):
    parser = SearchResultParser()
    parser.feed(page)
    results = []
    for r in parser.results:
        url, title = r.get("url", ""), clean(r.get("title"))
        if r.get("ad") or not url or not title or "duckduckgo.com/y.js" in url:
            continue
        if any(x["url"] == url for x in results):
            continue
        results.append({"title": title, "url": url, "snippet": clean(r.get("snippet"))})
        if len(results) >= limit:
            break
    return results, parser.blocked


def parse_query(raw):
    raw = (raw or "").strip()
    limit = MAX_RESULTS
    m = re.match(r"^(.*?)\s*\|\s*(\d+)\s*$", raw)
    if m:
        raw, limit = m.group(1).strip(), max(1, min(MAX_RESULTS, int(m.group(2))))
    return raw, limit


def search(raw):
    query, limit = parse_query(raw)
    if not query:
        fail("Leere Suchanfrage.")
    data = urllib.parse.urlencode({"q": query, "kl": "wt-wt"}).encode()
    request = urllib.request.Request(SEARCH_URL, data=data, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        page = response.read(MAX_DOWNLOAD).decode("utf-8", errors="replace")
    results, blocked = parse_results(page, limit)
    if not results:
        if blocked:
            fail("DuckDuckGo verlangt gerade eine Bestätigung (zu viele Anfragen). Später erneut versuchen.")
        print(f"Keine Treffer für: {query}")
        return
    print(f"Treffer für: {query}\n")
    for i, r in enumerate(results, 1):
        print(f"{i}. {r['title']}\n   {r['url']}")
        if r["snippet"]:
            print(f"   {r['snippet']}")
        print()


def check_public(url):
    """Erlaubt nur http(s) zu öffentlichen Adressen (kein localhost, keine privaten Netze)."""
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme not in ("http", "https") or not parsed.hostname:
        fail(f"Nur http- und https-Adressen sind erlaubt: {url}")
    try:
        infos = socket.getaddrinfo(parsed.hostname, parsed.port or (443 if parsed.scheme == "https" else 80))
    except socket.gaierror as e:
        fail(f"Adresse nicht auflösbar: {parsed.hostname} ({e})")
    for info in infos:
        ip = ipaddress.ip_address(info[4][0].split("%")[0])
        if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_multicast or ip.is_reserved or ip.is_unspecified:
            fail(f"Lokale oder private Adresse wird nicht abgerufen: {parsed.hostname} ({ip})")


class CheckedRedirects(urllib.request.HTTPRedirectHandler):
    """Prüft jedes Weiterleitungsziel wie die Ausgangsadresse."""
    max_redirections = MAX_REDIRECTS

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        check_public(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


class TextExtractor(HTMLParser):
    """Sichtbarer Text einer Seite: ohne Skripte, Stile und Navigation, Absätze als Zeilen."""
    SKIP = {"script", "style", "noscript", "svg", "nav", "footer", "header", "form", "button", "iframe"}
    BLOCK = {"p", "div", "li", "br", "h1", "h2", "h3", "h4", "h5", "h6", "tr", "pre", "section", "article", "blockquote"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.parts, self.title, self._skip, self._in_title = [], "", 0, False

    def handle_starttag(self, tag, attrs):
        if tag in self.SKIP:
            self._skip += 1
        elif tag == "title":
            self._in_title = True
        elif tag in self.BLOCK:
            self.parts.append("\n")

    def handle_endtag(self, tag):
        if tag in self.SKIP and self._skip:
            self._skip -= 1
        elif tag == "title":
            self._in_title = False
        elif tag in self.BLOCK:
            self.parts.append("\n")

    def handle_data(self, data):
        if self._in_title:
            self.title += data
        elif not self._skip:
            self.parts.append(data)

    def text(self):
        lines = (re.sub(r"[ \t\r\f\v]+", " ", line).strip() for line in "".join(self.parts).split("\n"))
        return "\n".join(line for line in lines if line)


def extract_text(page):
    extractor = TextExtractor()
    extractor.feed(page)
    return clean(extractor.title), extractor.text()


def fetch(url):
    url = (url or "").strip()
    check_public(url)
    opener = urllib.request.build_opener(CheckedRedirects)
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "text/html,text/plain;q=0.9,*/*;q=0.1"})
    with opener.open(request, timeout=TIMEOUT) as response:
        ctype = response.headers.get("Content-Type", "")
        charset = response.headers.get_content_charset() or "utf-8"
        body = response.read(MAX_DOWNLOAD + 1)
        final_url = response.geturl()
    truncated_download = len(body) > MAX_DOWNLOAD
    text_body = body[:MAX_DOWNLOAD].decode(charset, errors="replace")
    if "html" in ctype or "<html" in text_body[:2000].lower():
        title, text = extract_text(text_body)
    elif ctype.startswith("text/") or "json" in ctype or "xml" in ctype:
        title, text = "", text_body
    else:
        fail(f"Kein Text-Inhalt ({ctype or 'unbekannt'}): {final_url}")
    if len(text) > MAX_TEXT:
        text = text[:MAX_TEXT] + "\n… (gekürzt)"
    elif truncated_download:
        text += "\n… (Seite größer als 2 MiB, gekürzt)"
    print(f"Titel: {title or '–'}\nAdresse: {final_url}\n")
    print(text or "(kein lesbarer Text)")


def fail(message):
    print(message, file=sys.stderr)
    sys.exit(1)


def main(argv):
    if len(argv) < 2 or argv[1] not in ("search", "fetch"):
        fail("Aufruf: websearch.py search|fetch <eingabe>")
    arg = argv[2] if len(argv) > 2 else ""
    try:
        search(arg) if argv[1] == "search" else fetch(arg)
    except urllib.error.HTTPError as e:
        fail(f"HTTP-Fehler {e.code}: {e.reason}")
    except (urllib.error.URLError, socket.timeout, TimeoutError) as e:
        fail(f"Keine Verbindung: {getattr(e, 'reason', e)}")


if __name__ == "__main__":
    main(sys.argv)
