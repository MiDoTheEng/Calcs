/* Calcs – tiny PDF writer (Helvetica, WinAnsi) + report layout. No dependencies. */
(function (root) {
  'use strict';
  var W_REG=[278,278,355,556,556,889,667,191,333,333,389,584,278,333,278,278,556,556,556,556,556,556,556,556,556,556,278,278,584,584,584,556,1015,667,667,722,722,667,611,778,722,278,500,667,556,833,722,778,667,778,722,667,611,722,667,944,667,667,611,278,278,278,469,556,333,556,556,500,556,556,278,556,556,222,222,500,222,833,556,556,556,556,333,500,278,556,500,722,500,500,500,334,260,334,584,761,556,0,222,556,333,1000,556,556,333,1000,667,333,1000,0,611,0,0,222,222,333,333,350,556,1000,333,1000,500,333,944,0,500,667,278,333,556,556,556,556,260,556,333,737,370,556,584,333,737,333,400,584,333,333,333,556,537,278,333,333,365,556,834,834,834,611,667,667,667,667,667,667,1000,722,667,667,667,667,278,278,278,278,722,722,778,778,778,778,778,584,778,722,722,722,722,667,667,611,556,556,556,556,556,556,889,500,556,556,556,556,278,278,278,278,556,556,556,556,556,556,556,584,611,556,556,556,556,500,556,500];
  var W_BOLD=[278,333,474,556,556,889,722,238,333,333,389,584,278,333,278,278,556,556,556,556,556,556,556,556,556,556,333,333,584,584,584,611,975,722,722,722,722,667,611,778,722,278,556,722,611,833,722,778,667,778,722,667,611,722,667,944,667,667,611,333,278,333,584,556,333,556,611,556,611,556,333,611,611,278,278,556,278,889,611,611,611,611,389,556,333,611,556,778,556,556,500,389,280,389,584,761,556,0,278,556,500,1000,556,556,333,1000,667,333,1000,0,611,0,0,278,278,500,500,350,556,1000,333,1000,556,333,944,0,500,667,278,333,556,556,556,556,280,556,333,737,370,556,584,333,737,333,400,584,333,333,333,611,556,278,333,333,365,556,834,834,834,611,722,722,722,722,722,722,1000,722,667,667,667,667,278,278,278,278,722,722,778,778,778,778,778,584,778,722,722,722,722,667,667,611,556,556,556,556,556,556,889,556,556,556,556,556,278,278,278,278,611,611,611,611,611,611,611,584,611,611,611,611,611,556,611,556];
  var CP = { 8364: 128, 8218: 130, 402: 131, 8222: 132, 8230: 133, 8224: 134, 8225: 135, 710: 136, 8240: 137, 352: 138, 8249: 139, 338: 140, 381: 142,
    8216: 145, 8217: 146, 8220: 147, 8221: 148, 8226: 149, 8211: 150, 8212: 151, 732: 152, 8482: 153, 353: 154, 8250: 155, 339: 156, 382: 158, 376: 159 };
  var SUBS = { '≥': '>=', '≤': '<=', '√': 'sqrt', 'π': 'pi', '→': '->', '←': '<-', '↓': 'v', '₂': '2', '⁵': '5', '✓': 'OK', '⚠': '!', 'Δ': 'd', '−': '-', '·': '·', ' ': ' ' };
  function win(s) {
    s = String(s === undefined || s === null ? '' : s)
      .replace(/\s?√\(/g, ' sqrt(').replace(/\s?√([A-Za-z0-9.]+)/g, ' sqrt($1)');
    var out = '';
    for (var i = 0; i < s.length; i++) {
      var ch = s.charAt(i), c = s.charCodeAt(i);
      if (SUBS[ch] !== undefined && c > 255) { out += SUBS[ch]; continue; }
      if (c <= 255) out += ch; // already WinAnsi or Latin-1
      else if (CP[c]) out += String.fromCharCode(CP[c]);
      else out += '?';
    }
    return out;
  }
  function textWidth(s, size, bold) {
    var w = bold ? W_BOLD : W_REG, t = 0;
    for (var i = 0; i < s.length; i++) { var c = s.charCodeAt(i); t += (c >= 32 && c <= 255) ? (w[c - 32] || 500) : 500; }
    return t * size / 1000;
  }
  function esc(s) { return s.replace(/\\/g, '\\\\').replace(/\(/g, '\\(').replace(/\)/g, '\\)'); }
  function rgb(hex) { var n = parseInt(hex.slice(1), 16); return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255]; }
  function f2(n) { return (Math.round(n * 100) / 100).toString(); }

  function Doc() { this.W = 595.28; this.H = 841.89; this.pages = []; this.c = null; }
  Doc.prototype.page = function () { this.c = []; this.pages.push(this.c); };
  Doc.prototype.fill = function (hex) { var c = rgb(hex); this.c.push(f2(c[0]) + ' ' + f2(c[1]) + ' ' + f2(c[2]) + ' rg'); };
  Doc.prototype.stroke = function (hex) { var c = rgb(hex); this.c.push(f2(c[0]) + ' ' + f2(c[1]) + ' ' + f2(c[2]) + ' RG'); };
  Doc.prototype.rect = function (x, y, w, h, fill, stroke, lw) {
    if (!fill && !stroke) return;
    if (fill) this.fill(fill);
    if (stroke) { this.stroke(stroke); this.c.push((lw || 0.5) + ' w'); }
    this.c.push(f2(x) + ' ' + f2(this.H - y - h) + ' ' + f2(w) + ' ' + f2(h) + ' re ' + (fill && stroke ? 'B' : fill ? 'f' : 'S'));
  };
  Doc.prototype.line = function (x1, y1, x2, y2, hex, lw) {
    this.stroke(hex); this.c.push((lw || 0.5) + ' w ' + f2(x1) + ' ' + f2(this.H - y1) + ' m ' + f2(x2) + ' ' + f2(this.H - y2) + ' l S');
  };
  // y = top of the text line box
  Doc.prototype.text = function (x, y, s, size, bold, hex, align, maxW) {
    s = win(s);
    var w = textWidth(s, size, bold);
    if (maxW && w > maxW) { while (s.length > 1 && textWidth(s + '…', size, bold) > maxW) s = s.slice(0, -1); s += '…'; w = textWidth(s, size, bold); }
    var xx = align === 'right' ? x - w : align === 'center' ? x - w / 2 : x;
    this.fill(hex || '#1b2430');
    this.c.push('BT /' + (bold ? 'F2' : 'F1') + ' ' + size + ' Tf ' + f2(xx) + ' ' + f2(this.H - y - size * 0.8) + ' Td (' + esc(s) + ') Tj ET');
    return w;
  };
  Doc.prototype.wrap = function (s, size, bold, maxW) {
    s = win(s);
    var words = s.split(/\s+/), lines = [], cur = '';
    words.forEach(function (wd) {
      if (!wd) return;
      var t = cur ? cur + ' ' + wd : wd;
      if (textWidth(t, size, bold) <= maxW) { cur = t; return; }
      if (cur) lines.push(cur);
      while (textWidth(wd, size, bold) > maxW && wd.length > 1) {
        var k = wd.length; while (k > 1 && textWidth(wd.slice(0, k), size, bold) > maxW) k--;
        lines.push(wd.slice(0, k)); wd = wd.slice(k);
      }
      cur = wd;
    });
    if (cur || !lines.length) lines.push(cur);
    return lines;
  };
  Doc.prototype.bytes = function () {
    var objs = [], self = this;
    function add(s) { objs.push(s); return objs.length; }
    var catalog = add(''), pagesId = add('');
    var f1 = add('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>');
    var f2id = add('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>');
    var kids = [];
    this.pages.forEach(function (ops) {
      var stream = ops.join('\n');
      var cid = add('<< /Length ' + stream.length + ' >>\nstream\n' + stream + '\nendstream');
      kids.push(add('<< /Type /Page /Parent ' + pagesId + ' 0 R /MediaBox [0 0 ' + self.W + ' ' + self.H + '] /Resources << /Font << /F1 ' + f1 + ' 0 R /F2 ' + f2id + ' 0 R >> >> /Contents ' + cid + ' 0 R >>'));
    });
    objs[catalog - 1] = '<< /Type /Catalog /Pages ' + pagesId + ' 0 R >>';
    objs[pagesId - 1] = '<< /Type /Pages /Count ' + kids.length + ' /Kids [' + kids.map(function (k) { return k + ' 0 R'; }).join(' ') + '] >>';
    var out = '%PDF-1.4\n%âãÏÓ\n', offs = [];
    objs.forEach(function (o, i) { offs.push(out.length); out += (i + 1) + ' 0 obj\n' + o + '\nendobj\n'; });
    var xref = out.length;
    out += 'xref\n0 ' + (objs.length + 1) + '\n0000000000 65535 f \n';
    offs.forEach(function (o) { out += ('0000000000' + o).slice(-10) + ' 00000 n \n'; });
    out += 'trailer\n<< /Size ' + (objs.length + 1) + ' /Root ' + catalog + ' 0 R >>\nstartxref\n' + xref + '\n%%EOF';
    var b = new Uint8Array(out.length);
    for (var i = 0; i < out.length; i++) b[i] = out.charCodeAt(i) & 255;
    return b;
  };

  /* ---------------------------- report layout ---------------------------- */
  var ACCENT = { water: '#1f6fd1', drainage: '#8a5a1e', rain: '#0e8a83', fire: '#c9342b', all: '#27364a' };
  var INK = '#1b2430', MUTED = '#5d6b7d', RULE = '#cfd6df', INFILL = '#fff6d6', KEYFILL = '#dff1e2', OKC = '#1d7a3a', BADC = '#b3261e', ZEBRA = '#f4f6f9';

  function render(model, opt) {
    var d = new Doc(), M = 40, top = 64, bottom = d.H - 46, avail = d.W - 2 * M, y = 0;
    var accent = ACCENT[opt.accent] || ACCENT.all;
    var proj = model.project || {};
    var dateStr = model.date.toISOString().slice(0, 10);

    function header() {
      d.rect(0, 0, d.W, 6, accent);
      d.text(M, 20, 'CALCS  ·  ' + opt.heading.toUpperCase(), 8, true, accent);
      d.text(d.W - M, 20, proj.name || 'Untitled project', 8, true, INK, 'right', 260);
      d.text(d.W - M, 31, dateStr, 7, false, MUTED, 'right');
      d.line(M, 44, d.W - M, 44, RULE, 0.6);
    }
    function newPage() { d.page(); header(); y = top; }
    function ensure(h) { if (y + h > bottom) newPage(); }

    newPage();
    // Title block
    d.text(M, y, opt.title, 20, true, INK); y += 28;
    d.text(M, y, 'Calculation report', 10, false, MUTED); y += 22;
    var info = [['Project', proj.name], ['Location', proj.location], ['Prepared by', proj.engineer], ['Client / reference', proj.client], ['Date', proj.date || dateStr]]
      .filter(function (r) { return r[1]; });
    info.forEach(function (r, i) {
      d.rect(M, y, avail, 16, i % 2 ? null : ZEBRA);
      d.text(M + 6, y + 4, r[0], 8.5, true, MUTED); d.text(M + 130, y + 4, r[1], 8.5, false, INK, 'left', avail - 140); y += 16;
    });
    y += 6;
    // contents
    var tools = [];
    model.branches.forEach(function (b) { b.tools.forEach(function (t) { tools.push({ b: b, t: t }); }); });
    d.text(M, y, 'Contents', 9, true, INK); y += 14;
    tools.forEach(function (x, i) { d.text(M + 6, y, (i + 1) + '.  ' + (model.branches.length > 1 ? x.b.title + ' – ' : '') + x.t.title, 8.5, false, INK); y += 12; });
    y += 10;

    tools.forEach(function (x, ti) {
      var t = x.t;
      ensure(90);
      if (ti > 0) y += 8;
      d.rect(M, y, 4, 30, accent);
      d.text(M + 12, y + 1, (ti + 1) + '.  ' + (model.branches.length > 1 ? x.b.title + ' – ' : '') + t.title, 13, true, INK);
      d.text(M + 12, y + 18, t.sub || '', 8.5, false, MUTED);
      y += 40;
      if (t.tiles.length) {
        var n = t.tiles.length, gap = 6, tw = (avail - gap * (n - 1)) / n;
        ensure(42);
        t.tiles.forEach(function (k, i) {
          var x0 = M + i * (tw + gap);
          d.rect(x0, y, tw, 38, '#f2f5f9', RULE);
          d.text(x0 + 7, y + 6, k.label, 7, false, MUTED, 'left', tw - 12);
          var val = k.text + (k.unit ? ' ' + k.unit : '');
          var sz = 12; while (sz > 7 && textWidth(win(val), sz, true) > tw - 12) sz -= 0.5;
          d.text(x0 + 7, y + 19, val, sz, true, INK);
        });
        y += 48;
      }
      t.blocks.forEach(function (b) {
        if (b.type === 'kv') kv(b); else if (b.type === 'table') table(b); else if (b.type === 'note') note(b.text);
      });
    });

    function blockTitle(s) { ensure(34); d.text(M, y, s, 9.5, true, accent); y += 15; }
    function kv(b) {
      if (!b.rows.length) return;
      blockTitle(b.title);
      var cw = [avail * 0.58, avail * 0.26, avail * 0.16], sz = 8.5;
      b.rows.forEach(function (r, i) {
        var lines = d.wrap(r.label, sz, r.kind === 'key', cw[0] - 12);
        var vlines = d.wrap(r.text, sz, r.kind === 'key', cw[1] - 10);
        var h = Math.max(lines.length, vlines.length) * 11 + 6;
        ensure(h);
        if (i % 2 === 0) d.rect(M, y, avail, h, ZEBRA);
        if (r.kind === 'in') d.rect(M + cw[0], y, cw[1], h, INFILL);
        if (r.kind === 'key') d.rect(M + cw[0], y, cw[1], h, KEYFILL);
        lines.forEach(function (l, k) { d.text(M + 6, y + 4 + k * 11, l, sz, r.kind === 'key', INK); });
        var col = r.status === 'ok' ? OKC : r.status === 'bad' ? BADC : INK;
        vlines.forEach(function (l, k) { d.text(M + cw[0] + cw[1] - 6, y + 4 + k * 11, l, sz, r.kind !== 'in', col, 'right'); });
        d.text(M + cw[0] + cw[1] + 6, y + 4, r.unit, 8, false, MUTED, 'left', cw[2] - 8);
        y += h;
      });
      d.line(M, y, M + avail, y, RULE, 0.6);
      y += 12;
    }
    function table(b) {
      blockTitle(b.title);
      if (!b.rows.length) { note(b.emptyNote || 'No rows.'); return; }
      var n = b.head.length, sz = n > 10 ? 6.6 : n > 7 ? 7.2 : 7.8, hs = sz - 0.3, pad = 4;
      var nat = [], minw = [];
      for (var j = 0; j < n; j++) {
        var hw = 0, words = win(b.head[j]).split(/\s+/);
        words.forEach(function (w) { hw = Math.max(hw, textWidth(w, hs, true)); });
        var cw = textWidth(win(b.head[j]), hs, true);
        b.rows.concat(b.totals ? [b.totals] : []).forEach(function (row) { cw = Math.max(cw, textWidth(win(row[j].text), sz, b.kinds[j] === 'key')); });
        nat.push(cw + 2 * pad); minw.push(Math.min(Math.max(hw + 2 * pad, 26), 90));
      }
      var tot = nat.reduce(function (a, c) { return a + c; }, 0), w = nat.slice();
      if (tot > avail) {
        // shrink the widest columns first, down to their minimum
        var guard = 0;
        while (tot > avail + 0.1 && guard++ < 400) {
          var big = -1; for (var k = 0; k < n; k++) if (w[k] > minw[k] + 0.5 && (big < 0 || w[k] > w[big])) big = k;
          if (big < 0) break;
          var cut = Math.min(w[big] - minw[big], tot - avail, 4);
          w[big] -= cut; tot -= cut;
        }
        if (tot > avail) w = w.map(function (c) { return c * avail / tot; });
      } else {
        w = w.map(function (c) { return c * avail / tot; });
      }
      function headRow() {
        var hl = b.head.map(function (h, j) { return d.wrap(h, hs, true, w[j] - 2 * pad); });
        var hh = Math.max.apply(null, hl.map(function (l) { return l.length; })) * (hs + 2) + 7;
        ensure(hh + 14);
        var x = M;
        d.rect(M, y, avail, hh, '#27364a');
        hl.forEach(function (lines, j) {
          lines.forEach(function (l, k) { d.text(x + pad, y + 4 + k * (hs + 2), l, hs, true, '#ffffff'); });
          x += w[j];
        });
        y += hh;
      }
      headRow();
      function drawRow(row, i, isTotal) {
        var lines = row.map(function (c, j) { return d.wrap(c.text, sz, b.kinds[j] === 'key' || isTotal, w[j] - 2 * pad); });
        var h = Math.max.apply(null, lines.map(function (l) { return l.length; })) * (sz + 2.5) + 5;
        if (y + h > bottom) { newPage(); headRow(); }
        if (isTotal) d.rect(M, y, avail, h, '#e6ebf1');
        else if (i % 2) d.rect(M, y, avail, h, ZEBRA);
        var x = M;
        row.forEach(function (c, j) {
          if (!isTotal && b.kinds[j] === 'in') d.rect(x, y, w[j], h, INFILL);
          if (!isTotal && b.kinds[j] === 'key') d.rect(x, y, w[j], h, KEYFILL);
          var numeric = typeof c.value === 'number' && b.kinds[j] !== 'fixed' || (typeof c.value === 'number' && j > 0);
          var col = c.status === 'ok' ? OKC : c.status === 'bad' ? BADC : INK;
          lines[j].forEach(function (l, k) {
            if (numeric) d.text(x + w[j] - pad, y + 3 + k * (sz + 2.5), l, sz, b.kinds[j] === 'key' || isTotal, col, 'right');
            else d.text(x + pad, y + 3 + k * (sz + 2.5), l, sz, b.kinds[j] === 'key' || isTotal, col);
          });
          x += w[j];
        });
        y += h;
      }
      b.rows.forEach(function (row, i) { drawRow(row, i, false); });
      if (b.totals) drawRow(b.totals, 0, true);
      d.line(M, y, M + avail, y, RULE, 0.6);
      y += 12;
    }
    function note(s) {
      var lines = d.wrap(s, 7.8, false, avail - 16);
      var h = lines.length * 10 + 8;
      ensure(h);
      d.rect(M, y, 2, h, RULE);
      lines.forEach(function (l, k) { d.text(M + 9, y + 4 + k * 10, l, 7.8, false, MUTED); });
      y += h + 10;
    }

    // footers
    var total = d.pages.length;
    d.pages.forEach(function (ops, i) {
      d.c = ops;
      d.line(M, d.H - 34, d.W - M, d.H - 34, RULE, 0.6);
      d.text(M, d.H - 28, 'Educational tool – verify every value against the governing code edition and authority requirements.', 6.8, false, MUTED);
      d.text(d.W - M, d.H - 28, 'Page ' + (i + 1) + ' / ' + total, 7, true, MUTED, 'right');
    });
    return d.bytes();
  }

  root.PdfReport = { render: render, win: win, textWidth: textWidth };
})(typeof window !== 'undefined' ? window : this);
