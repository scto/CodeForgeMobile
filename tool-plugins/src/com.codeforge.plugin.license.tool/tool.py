#!/usr/bin/env python3
"""Schreibt LICENSE. Eingabe: '<lizenz> <Name>' mit lizenz = mit | bsd2 | bsd3 | isc | gpl | gplv3 | unlicense, z. B. 'mit Max Mustermann'.
gpl (GPL-2.0) und gplv3 (GPL-3.0): Der unveränderte Volltext wird von gnu.org geladen."""
import datetime, os, sys, urllib.request
from common import args, write

HEAD = "Copyright (c) {year} {name}\n\n"
MIT = """MIT License

Copyright (c) {year} {name}

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
"""
BSD2 = """BSD 2-Clause License

Copyright (c) {year}, {name}

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
"""
BSD3 = BSD2.replace("BSD 2-Clause", "BSD 3-Clause").replace(
    "THIS SOFTWARE IS PROVIDED", """3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED""")
ISC = """ISC License

Copyright (c) {year}, {name}

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
PERFORMANCE OF THIS SOFTWARE.
"""
UNLICENSE = """This is free and unencumbered software released into the public domain.

Anyone is free to copy, modify, publish, use, compile, sell, or
distribute this software, either in source code form or as a compiled
binary, for any purpose, commercial or non-commercial, and by any
means.

In jurisdictions that recognize copyright laws, the author or authors
of this software dedicate any and all copyright interest in the
software to the public domain. We make this dedication for the benefit
of the public at large and to the detriment of our heirs and
successors. We intend this dedication to be an overt act of
relinquishment in perpetuity of all present and future rights to this
software under copyright law.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.

For more information, please refer to <https://unlicense.org>
"""
LICENSES = {"mit": MIT, "bsd2": BSD2, "bsd3": BSD3, "isc": ISC, "unlicense": UNLICENSE}
# GPL-Volltexte sind lang und müssen wortgetreu sein – daher direkt vom Herausgeber (FSF) laden.
GPL_URLS = {
    "gpl": "https://www.gnu.org/licenses/old-licenses/gpl-2.0.txt",
    "gplv3": "https://www.gnu.org/licenses/gpl-3.0.txt",
}


def fetch_gpl(kind):
    try:
        with urllib.request.urlopen(GPL_URLS[kind], timeout=20) as r:
            text = r.read().decode("utf-8")
    except Exception as e:  # Netzwerk, DNS, Zertifikat …
        raise SystemExit(f"GPL-Text konnte nicht von gnu.org geladen werden ({e}). Bitte Internetverbindung prüfen und erneut starten.")
    if "GNU GENERAL PUBLIC LICENSE" not in text or len(text) < 15000:
        raise SystemExit("Unerwartete Antwort von gnu.org – LICENSE wurde nicht geschrieben.")
    return text


def main():
    project, inp = args()
    kind, _, name = inp.partition(" ")
    kind = kind.lower()
    if kind not in LICENSES and kind not in GPL_URLS:
        raise SystemExit(f"Unbekannte Lizenz '{kind}'. Erlaubt: {', '.join(list(LICENSES) + list(GPL_URLS))} (Apache-2.0: Volltext nicht enthalten)")
    if kind not in ("unlicense", "gpl", "gplv3") and not name.strip():
        raise SystemExit("Bitte Namen des Urhebers angeben")
    target = os.path.join(project, "LICENSE")
    existed = os.path.exists(target)
    if kind in GPL_URLS:
        write(target, fetch_gpl(kind))
    else:
        write(target, LICENSES[kind].format(year=datetime.date.today().year, name=name.strip()))
    print(f"LICENSE ({kind}) {'überschrieben' if existed else 'geschrieben'}.")


main()
