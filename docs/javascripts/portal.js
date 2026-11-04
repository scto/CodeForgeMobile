/* Store- und APT-Ansicht der zentralen Seite: lädt die bestehenden Kataloge live (GitHub Pages sendet CORS-Header). */
(function () {
  "use strict";
  var lang = (document.documentElement.lang || "de").slice(0, 2);

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }
  function loc(v) {
    if (v && typeof v === "object") return v[lang] || v.de || v.en || "";
    return v || "";
  }
  function safeUrl(u) { return /^https?:\/\//i.test(u || "") ? u : null; }
  function fmtSize(n) {
    n = Number(n) || 0;
    var u = ["B", "KB", "MB", "GB"], i = 0;
    while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
    return (i === 0 ? n.toFixed(0) : n.toFixed(1)) + " " + u[i];
  }
  function fail(root, msg) { root.replaceChildren(el("p", "cf-error", msg)); }

  /* ---------- Store ---------- */
  function initStore(root) {
    var src = root.dataset.src;
    root.replaceChildren(el("p", "cf-muted", "Katalog wird geladen …"));
    fetch(src, { cache: "no-cache" }).then(function (r) {
      if (!r.ok) throw new Error(r.status);
      return r.json();
    }).then(function (d) {
      var cats = d.categories || [], items = d.items || [], cur = "all", q = "";
      var search = el("input", "cf-search");
      search.type = "search"; search.placeholder = "Plugins suchen …"; search.setAttribute("aria-label", "Plugins suchen");
      var chips = el("div", "cf-chips"), grid = el("div", "cf-grid"), meta = el("p", "cf-muted");

      function chip(id, label) {
        var b = el("button", "cf-chip", label);
        b.type = "button"; b.dataset.id = id;
        b.addEventListener("click", function () { cur = id; render(); });
        chips.appendChild(b);
      }
      chip("all", "Alle");
      cats.forEach(function (c) { chip(c.id, loc(c.title)); });

      function render() {
        Array.prototype.forEach.call(chips.children, function (b) {
          b.setAttribute("aria-pressed", String(b.dataset.id === cur));
        });
        var list = items.filter(function (i) {
          if (cur !== "all" && i.category !== cur) return false;
          var hay = (loc(i.name) + " " + loc(i.summary) + " " + (i.author || "") + " " + i.id).toLowerCase();
          return !q || hay.indexOf(q) >= 0;
        });
        meta.textContent = list.length + " von " + items.length + " Einträgen" + (d.updated ? " · Stand " + d.updated : "");
        grid.replaceChildren.apply(grid, list.map(function (i) {
          var c = el("div", "cf-card");
          c.appendChild(el("h3", null, loc(i.name)));
          c.appendChild(el("span", "cf-meta", (i.category || "") + " · v" + (i.version || "?") + " · " + (i.author || "")));
          c.appendChild(el("p", null, loc(i.summary)));
          var row = el("p", "cf-actions");
          var dl = safeUrl(i.downloadUrl), hp = safeUrl(i.homepage);
          if (dl) { var a = el("a", "md-button md-button--primary", "Download"); a.href = dl; row.appendChild(a); }
          if (hp) { var h = el("a", "md-button", "Projekt"); h.href = hp; row.appendChild(h); }
          c.appendChild(row);
          if (i.sha256) c.appendChild(el("code", "cf-sha", "SHA-256 " + i.sha256.slice(0, 16) + "…"));
          return c;
        }));
      }
      search.addEventListener("input", function () { q = search.value.trim().toLowerCase(); render(); });
      root.replaceChildren(search, chips, meta, grid);
      render();
    }).catch(function (e) { fail(root, "Katalog konnte nicht geladen werden (" + e.message + ")."); });
  }

  /* ---------- APT ---------- */
  function parsePackages(text) {
    return text.split(/\n\n+/).map(function (blk) {
      var o = {}, last = null;
      blk.split("\n").forEach(function (l) {
        if (/^\s/.test(l) && last) o[last] += "\n" + l.trim();
        else { var k = l.indexOf(":"); if (k > 0) { last = l.slice(0, k); o[last] = l.slice(k + 1).trim(); } }
      });
      return o;
    }).filter(function (o) { return o.Package; });
  }
  function initApt(root) {
    var base = root.dataset.src.replace(/\/$/, ""), archs = (root.dataset.archs || "aarch64").split(",");
    root.replaceChildren(el("p", "cf-muted", "Paketindex wird geladen …"));
    Promise.all(archs.map(function (a) {
      return fetch(base + "/dists/stable/main/binary-" + a + "/Packages", { cache: "no-cache" })
        .then(function (r) { if (!r.ok) throw new Error(a + ": " + r.status); return r.text(); })
        .then(parsePackages);
    })).then(function (all) {
      var rows = {};
      all.forEach(function (pk) {
        pk.forEach(function (p) {
          var r = rows[p.Package] || (rows[p.Package] = { name: p.Package, version: p.Version, desc: p.Description, deps: p.Depends || "", files: {} });
          r.files[p.Architecture] = { url: base + "/" + p.Filename, size: p.Size };
        });
      });
      var list = Object.keys(rows).sort().map(function (k) { return rows[k]; });
      var search = el("input", "cf-search");
      search.type = "search"; search.placeholder = "Pakete suchen (Name oder Beschreibung) …"; search.setAttribute("aria-label", "Pakete suchen");
      var meta = el("p", "cf-muted"), tbody = el("tbody");
      var table = el("table");
      var head = el("thead"), hr = el("tr");
      ["Paket", "Version", "Download", "Größe", "Beschreibung"].forEach(function (h) { hr.appendChild(el("th", null, h)); });
      head.appendChild(hr); table.append(head, tbody);
      var wrap = el("div", "cf-table-wrap"); wrap.appendChild(table);
      var LIMIT = 150;

      function render() {
        var q = search.value.trim().toLowerCase();
        var hit = list.filter(function (r) { return !q || (r.name + " " + (r.desc || "")).toLowerCase().indexOf(q) >= 0; });
        meta.textContent = hit.length + " von " + list.length + " Paketen" + (hit.length > LIMIT ? " – die ersten " + LIMIT + " werden angezeigt, Suche eingrenzen" : "");
        tbody.replaceChildren.apply(tbody, hit.slice(0, LIMIT).map(function (r) {
          var tr = el("tr"), n = el("td"), b = el("b", null, r.name); n.appendChild(b);
          var dl = el("td"), size = 0;
          Object.keys(r.files).forEach(function (a, i) {
            if (i) dl.appendChild(document.createTextNode(" · "));
            var x = el("a", null, a); x.href = r.files[a].url; dl.appendChild(x); size = size || r.files[a].size;
          });
          var d = el("td", null, r.desc || "");
          if (r.deps) d.appendChild(el("div", "cf-muted cf-small", r.deps));
          tr.append(n, el("td", null, r.version || ""), dl, el("td", null, fmtSize(size)), d);
          return tr;
        }));
      }
      search.addEventListener("input", render);
      root.replaceChildren(search, meta, wrap);
      render();
    }).catch(function (e) { fail(root, "Paketindex konnte nicht geladen werden (" + e.message + ")."); });
  }

  function boot() {
    var s = document.getElementById("cf-store"); if (s) initStore(s);
    var a = document.getElementById("cf-apt"); if (a) initApt(a);
  }
  if (window.document$ && window.document$.subscribe) window.document$.subscribe(boot);
  else document.addEventListener("DOMContentLoaded", boot);
})();
