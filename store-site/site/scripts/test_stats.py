"""python3 -m unittest scripts/test_stats.py"""
import os, sys, unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import stats  # noqa: E402

CATALOG = {"items": [{"id": "com.codeforge.plugin.go.lsp"}, {"id": "com.codeforge.plugin.go-pack.bundle"}]}


def issue(user, pid, stars, text="", date="2026-11-20T10:00:00Z", labels=("rating",), n=1):
    body = f"### Paket-ID / Package ID\n\n{pid}\n\n### Sterne / Stars\n\n{stars}\n\n### Bewertung / Review (optional)\n\n{text or '_No response_'}\n"
    return {"user": {"login": user}, "body": body, "updated_at": date, "labels": [{"name": l} for l in labels],
            "html_url": f"https://x/{n}", "title": f"Bewertung: {pid}"}


class StatsTest(unittest.TestCase):
    def test_downloads_über_alle_versionen(self):
        assets = [{"name": "com.codeforge.plugin.go.lsp-1.0.0.zip", "download_count": 3},
                  {"name": "com.codeforge.plugin.go.lsp-1.1.0.zip", "download_count": 2},
                  {"name": "com.codeforge.plugin.go-pack.bundle-1.0.0.zip", "download_count": 7},
                  {"name": "fremd.zip", "download_count": 99}]
        d = stats.downloads(assets, [i["id"] for i in CATALOG["items"]])
        self.assertEqual({"com.codeforge.plugin.go.lsp": 5, "com.codeforge.plugin.go-pack.bundle": 7}, d)

    def test_bewertungen_neueste_je_nutzer_und_ausschlüsse(self):
        issues = [
            issue("ada", "com.codeforge.plugin.go.lsp", "★★☆☆☆ (2)", "geht so", "2026-11-01T00:00:00Z", n=1),
            issue("ada", "com.codeforge.plugin.go.lsp", "★★★★★ (5)", "jetzt super", "2026-11-10T00:00:00Z", n=2),
            issue("bob", "com.codeforge.plugin.go.lsp", "★★★★☆ (4)", n=3),
            issue("eve", "com.codeforge.plugin.go.lsp", "★☆☆☆☆ (1)", "spam", labels=("rating", "spam"), n=4),
            issue("max", "unbekannt", "★★★★★ (5)", n=5),
        ]
        s = stats.build(CATALOG, [], issues, "now")
        go = s["items"]["com.codeforge.plugin.go.lsp"]
        self.assertEqual(2, go["ratings"])
        self.assertEqual(4.5, go["rating"])
        self.assertEqual([0, 0, 0, 1, 1], go["stars"])
        self.assertEqual(["jetzt super"], [r["text"] for r in go["reviews"]])
        self.assertEqual(2, s["totals"]["ratings"])
        self.assertNotIn("rating", s["items"]["com.codeforge.plugin.go-pack.bundle"])

    def test_sternformate(self):
        self.assertEqual(3, stats.parse_rating(issue("a", "p", "★★★☆☆ (3)"))[1])
        self.assertEqual(4, stats.parse_rating(issue("a", "p", "4"))[1])
        self.assertEqual(2, stats.parse_rating(issue("a", "p", "★★"))[1])
        self.assertIsNone(stats.parse_rating(issue("a", "p", "super")))


if __name__ == "__main__":
    unittest.main()
