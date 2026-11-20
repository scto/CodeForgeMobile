/* CodeForge Store – Marktplatz der Website (wie der VS-Code-Marketplace, als Kategorienbaum).
 *
 * Lädt store.json (Katalog) und stats.json (Downloads und Bewertungen, täglich aktualisiert) vom Store-Repo auf
 * GitHub Pages. Suche, Kategorienbaum (beliebig tief), Tags (mehrfach), Art (deklarativ/Code), Sortierung und eine
 * Detailansicht je Paket (#/p/<id>) mit Bewertungen. Ohne Bibliotheken.
 */
(function () {
  "use strict";
  var lang = (document.documentElement.lang || "de").slice(0, 2);
  var T = {
    de: {
      search: "Plugins, Themes, Sprachen suchen …", all: "Alle", sort: "Sortierung", relevance: "Relevanz",
      downloads: "Downloads", rating: "Bewertung", name: "Name", updated: "Aktualisiert", newest: "Neu",
      categories: "Kategorien", tags: "Tags", kind: "Art", declarative: "Deklarativ (In-App)", code: "Code-Plugin (Sideload)", sandbox: "Sandbox-Plugin (In-App)",
      results: "{n} von {t} Paketen", download: "Herunterladen", back: "← Zurück", rate: "Bewerten",
      ratings: "{n} Bewertungen", noRatings: "Noch keine Bewertungen", reviews: "Rezensionen", version: "Version",
      size: "Größe", published: "Veröffentlicht", author: "Herausgeber", permissions: "Berechtigungen", none: "keine",
      install: "Installation", installStore: "In der App: Einstellungen → Store → Paket suchen → Installieren.",
      installSideload: "Code-Plugin: ZIP herunterladen und in der App unter Einstellungen → Plugins → „+“ installieren.",
      stats: "Stand der Statistik: {d}", loading: "Katalog wird geladen …", error: "Katalog konnte nicht geladen werden ({e}).",
      clear: "Filter zurücksetzen", sideload: "Sideload", mirror: "Spiegel", copied: "kopiert", copy: "SHA-256 kopieren",
      notFound: "Paket nicht gefunden.", totalDl: "{n} Downloads insgesamt",
    },
    en: {
      search: "Search plugins, themes, languages …", all: "All", sort: "Sort", relevance: "Relevance",
      downloads: "Downloads", rating: "Rating", name: "Name", updated: "Updated", newest: "Newest",
      categories: "Categories", tags: "Tags", kind: "Kind", declarative: "Declarative (in-app)", code: "Code plugin (sideload)", sandbox: "Sandbox plugin (in-app)",
      results: "{n} of {t} packages", download: "Download", back: "← Back", rate: "Rate",
      ratings: "{n} ratings", noRatings: "No ratings yet", reviews: "Reviews", version: "Version",
      size: "Size", published: "Published", author: "Publisher", permissions: "Permissions", none: "none",
      install: "Installation", installStore: "In the app: Settings → Store → search the package → Install.",
      installSideload: "Code plugin: download the ZIP and install it in the app via Settings → Plugins → “+”.",
      stats: "Statistics as of {d}", loading: "Loading catalog …", error: "Could not load the catalog ({e}).",
      clear: "Reset filters", sideload: "Sideload", mirror: "Mirror", copied: "copied", copy: "Copy SHA-256",
      notFound: "Package not found.", totalDl: "{n} downloads in total",
    },
  }[lang === "de" ? "de" : "en"];

  function t(key, vars) {
    var s = T[key] || key;
    Object.keys(vars || {}).forEach(function (k) { s = s.replace("{" + k + "}", vars[k]); });
    return s;
  }
  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }
  function loc(v) { return v && typeof v === "object" ? (v[lang] || v.de || v.en || "") : (v || ""); }
  function safeUrl(u) { return /^https:\/\//i.test(u || "") ? u : null; }
  function fmtSize(n) {
    n = Number(n) || 0;
    var u = ["B", "KB", "MB"], i = 0;
    while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
    return (i === 0 ? n.toFixed(0) : n.toFixed(1)) + " " + u[i];
  }
  function fmtNum(n) { return (Number(n) || 0).toLocaleString(lang); }
  function stars(r) {
    var full = Math.round(r || 0);
    return "★★★★★".slice(0, full) + "☆☆☆☆☆".slice(0, 5 - full);
  }
  function initials(name) {
    return name.replace(/\(.*?\)/g, "").trim().split(/\s+/).slice(0, 2).map(function (w) { return w[0] || ""; }).join("").toUpperCase();
  }
  function hue(id) { var h = 0; for (var i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) % 360; return h; }

  function initStore(root) {
    var src = root.dataset.src;
    var statsSrc = root.dataset.stats || src.replace(/store\.json$/, "stats.json");
    root.replaceChildren(el("p", "cf-muted", t("loading")));
    Promise.all([
      fetch(src, { cache: "no-cache" }).then(function (r) { if (!r.ok) throw new Error(r.status); return r.json(); }),
      fetch(statsSrc, { cache: "no-cache" }).then(function (r) { return r.ok ? r.json() : {}; }).catch(function () { return {}; }),
    ]).then(function (res) { mount(root, res[0], res[1] || {}); })
      .catch(function (e) { root.replaceChildren(el("p", "cf-error", t("error", { e: e.message }))); });
  }

  function mount(root, catalog, stats) {
    var items = catalog.items || [];
    var st = stats.items || {};
    var tree = catalog.tree || [];
    var titles = {};
    tree.forEach(function (n) { titles[n.id] = loc(n.title); });
    var byId = {};
    items.forEach(function (i) {
      i._s = st[i.id] || {};
      i._name = loc(i.name);
      i._hay = (i._name + " " + loc(i.summary) + " " + i.id + " " + (i.tags || []).join(" ") + " " + (i.categories || []).map(function (c) { return titles[c] || c; }).join(" ")).toLowerCase();
      byId[i.id] = i;
    });
    var state = { q: "", cat: "", tags: [], kind: "", sort: "relevance", open: {} };

    // ---- Bausteine ----
    var search = el("input", "cf-search");
    search.type = "search"; search.placeholder = t("search"); search.setAttribute("aria-label", t("search"));
    var sort = el("select", "cf-select");
    sort.setAttribute("aria-label", t("sort"));
    ["relevance", "downloads", "rating", "name", "updated", "newest"].forEach(function (k) {
      var o = el("option", null, t(k)); o.value = k; sort.appendChild(o);
    });
    var side = el("aside", "cf-side"), main = el("section", "cf-main"), meta = el("p", "cf-muted"), grid = el("div", "cf-grid");
    var toolbar = el("div", "cf-toolbar");
    toolbar.append(search, sort);
    var layout = el("div", "cf-layout");
    layout.append(side, main);
    main.append(meta, grid);
    var detail = el("div", "cf-detail");

    function count(path) {
      return items.filter(function (i) { return (i.categories || []).some(function (c) { return c === path || c.indexOf(path + "/") === 0; }); }).length;
    }
    function children(parent) {
      return tree.filter(function (n) {
        var p = n.id.lastIndexOf("/") < 0 ? "" : n.id.slice(0, n.id.lastIndexOf("/"));
        return p === parent;
      });
    }

    function renderSide() {
      side.replaceChildren();
      side.appendChild(el("h4", null, t("categories")));
      var ul = el("ul", "cf-tree");
      var allLi = el("li");
      var allB = el("button", "cf-node" + (state.cat === "" ? " is-active" : ""), t("all") + " (" + items.length + ")");
      allB.type = "button"; allB.onclick = function () { state.cat = ""; update(); };
      allLi.appendChild(allB); ul.appendChild(allLi);
      (function walk(parent, into) {
        children(parent).forEach(function (n) {
          var n2 = count(n.id);
          if (!n2) return;
          var li = el("li");
          var kids = children(n.id).filter(function (k) { return count(k.id) > 0; });
          var row = el("div", "cf-row");
          if (kids.length) {
            var tog = el("button", "cf-tog", state.open[n.id] || state.cat.indexOf(n.id + "/") === 0 ? "▾" : "▸");
            tog.type = "button"; tog.setAttribute("aria-label", "auf-/zuklappen");
            tog.onclick = function () { state.open[n.id] = !(state.open[n.id] || state.cat.indexOf(n.id + "/") === 0); renderSide(); };
            row.appendChild(tog);
          } else row.appendChild(el("span", "cf-tog-space"));
          var b = el("button", "cf-node" + (state.cat === n.id ? " is-active" : ""), loc(n.title) + " (" + n2 + ")");
          b.type = "button"; b.onclick = function () { state.cat = n.id; state.open[n.id] = true; update(); };
          row.appendChild(b);
          li.appendChild(row);
          if (kids.length && (state.open[n.id] || state.cat === n.id || state.cat.indexOf(n.id + "/") === 0)) {
            var sub = el("ul", "cf-tree"); walk(n.id, sub); li.appendChild(sub);
          }
          into.appendChild(li);
        });
      })("", ul);
      side.appendChild(ul);

      side.appendChild(el("h4", null, t("kind")));
      var kinds = el("div", "cf-chips");
      [["", t("all")], ["declarative", t("declarative")], ["sandbox", t("sandbox")], ["code", t("code")]].forEach(function (k) {
        var b = el("button", "cf-chip", k[1]);
        b.type = "button"; b.setAttribute("aria-pressed", String(state.kind === k[0]));
        b.onclick = function () { state.kind = k[0]; update(); };
        kinds.appendChild(b);
      });
      side.appendChild(kinds);

      side.appendChild(el("h4", null, t("tags")));
      var tagCount = {};
      filtered(true).forEach(function (i) { (i.tags || []).forEach(function (g) { tagCount[g] = (tagCount[g] || 0) + 1; }); });
      var tags = el("div", "cf-chips");
      Object.keys(tagCount).sort(function (a, b) { return tagCount[b] - tagCount[a] || a.localeCompare(b); }).slice(0, 40).forEach(function (g) {
        var on = state.tags.indexOf(g) >= 0;
        var b = el("button", "cf-chip cf-tag", "#" + g + " " + tagCount[g]);
        b.type = "button"; b.setAttribute("aria-pressed", String(on));
        b.onclick = function () { state.tags = on ? state.tags.filter(function (x) { return x !== g; }) : state.tags.concat([g]); update(); };
        tags.appendChild(b);
      });
      side.appendChild(tags);
      if (state.cat || state.tags.length || state.kind || state.q) {
        var clr = el("button", "md-button", t("clear"));
        clr.type = "button";
        clr.onclick = function () { state.cat = ""; state.tags = []; state.kind = ""; state.q = ""; search.value = ""; update(); };
        side.appendChild(clr);
      }
    }

    function score(i) {
      if (!state.q) return 0;
      var s = 0, q = state.q;
      if (i._name.toLowerCase().indexOf(q) === 0) s += 50;
      if (i._name.toLowerCase().indexOf(q) >= 0) s += 20;
      if ((i.tags || []).indexOf(q) >= 0) s += 15;
      if (i.id.toLowerCase().indexOf(q) >= 0) s += 5;
      return s + Math.log10(1 + (i._s.downloads || 0)) * 3 + (i._s.rating || 0);
    }

    function filtered(ignoreTags) {
      return items.filter(function (i) {
        if (state.cat && !(i.categories || []).some(function (c) { return c === state.cat || c.indexOf(state.cat + "/") === 0; })) return false;
        if (state.kind && (i.kind || (i.category === "plugin" ? "code" : "declarative")) !== state.kind) return false;
        if (!ignoreTags && state.tags.some(function (g) { return (i.tags || []).indexOf(g) < 0; })) return false;
        if (state.q && state.q.split(/\s+/).some(function (w) { return i._hay.indexOf(w) < 0; })) return false;
        return true;
      });
    }

    function sorted(list) {
      var cmp = {
        relevance: function (a, b) { return score(b) - score(a) || (b._s.downloads || 0) - (a._s.downloads || 0) || a._name.localeCompare(b._name); },
        downloads: function (a, b) { return (b._s.downloads || 0) - (a._s.downloads || 0) || a._name.localeCompare(b._name); },
        rating: function (a, b) { return (b._s.rating || 0) - (a._s.rating || 0) || (b._s.ratings || 0) - (a._s.ratings || 0); },
        name: function (a, b) { return a._name.localeCompare(b._name, lang); },
        updated: function (a, b) { return (b.updated || "").localeCompare(a.updated || "") || a._name.localeCompare(b._name); },
        newest: function (a, b) { return (b.published || "").localeCompare(a.published || "") || a._name.localeCompare(b._name); },
      }[state.sort];
      return list.slice().sort(cmp);
    }

    function card(i) {
      var c = el("a", "cf-card cf-card--link");
      c.href = "#/p/" + encodeURIComponent(i.id);
      var head = el("div", "cf-card-head");
      var av = el("span", "cf-avatar", initials(i._name));
      av.style.background = "hsl(" + hue(i.id) + " 55% 45%)";
      var titleBox = el("div");
      titleBox.append(el("h3", null, i._name), el("span", "cf-meta", (i.author || "") + " · v" + (i.version || "?")));
      head.append(av, titleBox);
      c.appendChild(head);
      c.appendChild(el("p", "cf-summary", loc(i.summary)));
      var foot = el("div", "cf-card-foot");
      foot.appendChild(el("span", "cf-stars", i._s.ratings ? stars(i._s.rating) + " " + i._s.rating.toFixed(1) + " (" + i._s.ratings + ")" : "☆☆☆☆☆"));
      foot.appendChild(el("span", "cf-dl", "⤓ " + fmtNum(i._s.downloads || 0)));
      if (i.kind === "sandbox") foot.appendChild(el("span", "cf-badge", "Sandbox"));
      else if ((i.kind || "") === "code" || i.category === "plugin") foot.appendChild(el("span", "cf-badge", t("sideload")));
      c.appendChild(foot);
      return c;
    }

    function renderList() {
      var list = sorted(filtered(false));
      var info = t("results", { n: list.length, t: items.length });
      if (stats.updated) info += " · " + t("stats", { d: String(stats.updated).slice(0, 10) });
      if (stats.totals && stats.totals.downloads) info += " · " + t("totalDl", { n: fmtNum(stats.totals.downloads) });
      meta.textContent = info;
      grid.replaceChildren.apply(grid, list.map(card));
    }

    function renderDetail(id) {
      var i = byId[id];
      detail.replaceChildren();
      var back = el("a", "cf-back", t("back"));
      back.href = "#";
      detail.appendChild(back);
      if (!i) { detail.appendChild(el("p", "cf-error", t("notFound"))); return; }
      var head = el("div", "cf-card-head cf-detail-head");
      var av = el("span", "cf-avatar cf-avatar--big", initials(i._name));
      av.style.background = "hsl(" + hue(i.id) + " 55% 45%)";
      var box = el("div");
      box.append(el("h2", null, i._name), el("p", "cf-meta", i.id));
      var line = el("p", "cf-meta", (i._s.ratings ? stars(i._s.rating) + " " + i._s.rating.toFixed(1) + " · " + t("ratings", { n: i._s.ratings }) : t("noRatings")) + " · ⤓ " + fmtNum(i._s.downloads || 0));
      box.appendChild(line);
      head.append(av, box);
      detail.appendChild(head);
      detail.appendChild(el("p", null, loc(i.summary)));

      var actions = el("p", "cf-actions");
      var dl = safeUrl(i.downloadUrl), mirror = safeUrl(i.mirrorUrl);
      if (dl) { var a = el("a", "md-button md-button--primary", t("download") + " (" + fmtSize(i.size) + ")"); a.href = dl; actions.appendChild(a); }
      if (mirror) { var m = el("a", "md-button", t("mirror")); m.href = mirror; actions.appendChild(m); }
      var rateUrl = safeUrl(catalog.ratingUrl);
      if (rateUrl) {
        var r = el("a", "md-button", "★ " + t("rate"));
        r.href = rateUrl + "&title=" + encodeURIComponent("Bewertung: " + i.id) + "&plugin=" + encodeURIComponent(i.id);
        r.target = "_blank"; r.rel = "noopener";
        actions.appendChild(r);
      }
      if (i.sha256) {
        var cp = el("button", "md-button", t("copy"));
        cp.type = "button";
        cp.onclick = function () { navigator.clipboard && navigator.clipboard.writeText(i.sha256).then(function () { cp.textContent = t("copied"); }); };
        actions.appendChild(cp);
      }
      detail.appendChild(actions);

      var facts = el("dl", "cf-facts");
      function fact(k, v) { if (!v) return; facts.append(el("dt", null, k), el("dd", null, v)); }
      fact(t("version"), i.version);
      fact(t("author"), i.author);
      fact(t("size"), i.size ? fmtSize(i.size) : "");
      fact(t("updated"), i.updated);
      fact(t("published"), i.published);
      fact(t("kind"), i.kind === "sandbox" ? t("sandbox") : (i.kind === "code" || i.category === "plugin" ? t("code") : t("declarative")));
      fact(t("permissions"), (i.permissions || []).join(", ") || t("none"));
      fact("SHA-256", i.sha256);
      detail.appendChild(facts);

      var chips = el("div", "cf-chips");
      (i.categories || []).forEach(function (c) {
        var b = el("a", "cf-chip", titlePath(c));
        b.href = "#"; b.onclick = function (e) { e.preventDefault(); state.cat = c; location.hash = ""; update(); };
        chips.appendChild(b);
      });
      (i.tags || []).forEach(function (g) {
        var b = el("a", "cf-chip cf-tag", "#" + g);
        b.href = "#"; b.onclick = function (e) { e.preventDefault(); state.tags = [g]; location.hash = ""; update(); };
        chips.appendChild(b);
      });
      detail.appendChild(chips);

      detail.appendChild(el("h3", null, t("install")));
      detail.appendChild(el("p", null, i.kind === "code" ? t("installSideload") : t("installStore")));

      if (i._s.ratings) {
        detail.appendChild(el("h3", null, t("rating")));
        var hist = el("div", "cf-hist");
        for (var s = 5; s >= 1; s--) {
          var n = (i._s.stars || [])[s - 1] || 0;
          var row = el("div", "cf-hist-row");
          var bar = el("span", "cf-hist-bar");
          var fill = el("span", "cf-hist-fill");
          fill.style.width = Math.round(100 * n / i._s.ratings) + "%";
          bar.appendChild(fill);
          row.append(el("span", null, s + "★"), bar, el("span", "cf-muted", String(n)));
          hist.appendChild(row);
        }
        detail.appendChild(hist);
      }
      if ((i._s.reviews || []).length) {
        detail.appendChild(el("h3", null, t("reviews")));
        i._s.reviews.forEach(function (rv) {
          var q = el("blockquote", "cf-review");
          q.append(el("strong", null, stars(rv.stars) + " " + rv.user), el("span", "cf-muted", " · " + rv.date), el("p", null, rv.text));
          if (safeUrl(rv.url)) { var l = el("a", "cf-muted", "GitHub"); l.href = rv.url; q.appendChild(l); }
          detail.appendChild(q);
        });
      }
    }

    function titlePath(path) {
      var parts = path.split("/"), out = [];
      for (var k = 1; k <= parts.length; k++) out.push(titles[parts.slice(0, k).join("/")] || parts[k - 1]);
      return out.join(" › ");
    }

    function update() {
      var m = /^#\/p\/(.+)$/.exec(location.hash || "");
      if (m) {
        renderDetail(decodeURIComponent(m[1]));
        root.replaceChildren(detail);
        window.scrollTo(0, 0);
        return;
      }
      renderSide();
      renderList();
      if (!root.contains(layout)) root.replaceChildren(toolbar, layout);
    }

    var timer = null;
    search.addEventListener("input", function () {
      clearTimeout(timer);
      timer = setTimeout(function () { state.q = search.value.trim().toLowerCase(); update(); }, 120);
    });
    sort.addEventListener("change", function () { state.sort = sort.value; update(); });
    window.addEventListener("hashchange", update);
    update();
  }

  window.cfInitStore = initStore;
})();
