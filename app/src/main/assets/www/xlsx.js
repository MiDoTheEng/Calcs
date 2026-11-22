/* Calcs – tiny .xlsx writer (stored zip, inline strings, a few styles). No dependencies. */
(function (root) {
  'use strict';

  /* ------------------------------- zip ------------------------------- */
  var CRC = (function () {
    var t = new Uint32Array(256);
    for (var n = 0; n < 256; n++) { var c = n; for (var k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; }
    return t;
  })();
  function crc32(b) { var c = 0xFFFFFFFF; for (var i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 255] ^ (c >>> 8); return (c ^ 0xFFFFFFFF) >>> 0; }
  function utf8(s) {
    if (typeof TextEncoder !== 'undefined') return new TextEncoder().encode(s);
    var out = [], e = unescape(encodeURIComponent(s));
    for (var i = 0; i < e.length; i++) out.push(e.charCodeAt(i));
    return new Uint8Array(out);
  }
  function zip(files) { // files: [{name, data: Uint8Array}]
    var parts = [], central = [], off = 0;
    function u16(a, v) { a.push(v & 255, (v >>> 8) & 255); }
    function u32(a, v) { a.push(v & 255, (v >>> 8) & 255, (v >>> 16) & 255, (v >>> 24) & 255); }
    files.forEach(function (f) {
      var name = utf8(f.name), crc = crc32(f.data), h = [];
      u32(h, 0x04034b50); u16(h, 20); u16(h, 0x0800); u16(h, 0); u16(h, 0); u16(h, 0x21);
      u32(h, crc); u32(h, f.data.length); u32(h, f.data.length); u16(h, name.length); u16(h, 0);
      var head = new Uint8Array(h.concat(Array.prototype.slice.call(name)));
      parts.push(head, f.data);
      var c = [];
      u32(c, 0x02014b50); u16(c, 20); u16(c, 20); u16(c, 0x0800); u16(c, 0); u16(c, 0); u16(c, 0x21);
      u32(c, crc); u32(c, f.data.length); u32(c, f.data.length); u16(c, name.length); u16(c, 0); u16(c, 0); u16(c, 0); u16(c, 0); u32(c, 0); u32(c, off);
      central.push(new Uint8Array(c.concat(Array.prototype.slice.call(name))));
      off += head.length + f.data.length;
    });
    var cdSize = central.reduce(function (a, c) { return a + c.length; }, 0), e = [];
    u32(e, 0x06054b50); u16(e, 0); u16(e, 0); u16(e, files.length); u16(e, files.length); u32(e, cdSize); u32(e, off); u16(e, 0);
    var all = parts.concat(central, [new Uint8Array(e)]);
    var len = all.reduce(function (a, c) { return a + c.length; }, 0), out = new Uint8Array(len), p = 0;
    all.forEach(function (c) { out.set(c, p); p += c.length; });
    return out;
  }

  /* ------------------------------ styles ------------------------------ */
  // base styles: 0 normal, 1 title, 2 header, 3 input, 4 result, 5 key, 6 note, 7 section, 8 subtitle, 9 total, 10 ok, 11 bad
  var FONTS = '<fonts count="8">' +
    '<font><sz val="10"/><name val="Arial"/></font>' +
    '<font><b/><sz val="15"/><color rgb="FF1B2430"/><name val="Arial"/></font>' +
    '<font><b/><sz val="9"/><color rgb="FFFFFFFF"/><name val="Arial"/></font>' +
    '<font><b/><sz val="10"/><name val="Arial"/></font>' +
    '<font><i/><sz val="9"/><color rgb="FF5D6B7D"/><name val="Arial"/></font>' +
    '<font><b/><sz val="11"/><color rgb="FF1F4E78"/><name val="Arial"/></font>' +
    '<font><b/><sz val="10"/><color rgb="FF1D7A3A"/><name val="Arial"/></font>' +
    '<font><b/><sz val="10"/><color rgb="FFB3261E"/><name val="Arial"/></font></fonts>';
  var FILLS = '<fills count="7"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>' +
    '<fill><patternFill patternType="solid"><fgColor rgb="FF27364A"/></patternFill></fill>' +
    '<fill><patternFill patternType="solid"><fgColor rgb="FFFFF2CC"/></patternFill></fill>' +
    '<fill><patternFill patternType="solid"><fgColor rgb="FFE2EFDA"/></patternFill></fill>' +
    '<fill><patternFill patternType="solid"><fgColor rgb="FFC6E0B4"/></patternFill></fill>' +
    '<fill><patternFill patternType="solid"><fgColor rgb="FFE6EBF1"/></patternFill></fill></fills>';
  var BORDERS = '<borders count="2"><border/><border><left style="thin"><color rgb="FFBFC8D3"/></left><right style="thin"><color rgb="FFBFC8D3"/></right>' +
    '<top style="thin"><color rgb="FFBFC8D3"/></top><bottom style="thin"><color rgb="FFBFC8D3"/></bottom></border></borders>';
  var BASE = [ // [fontId, fillId, borderId, wrap]
    [0, 0, 0, 0], [1, 0, 0, 0], [2, 2, 1, 1], [0, 3, 1, 0], [0, 4, 1, 0], [3, 5, 1, 0], [4, 0, 0, 0], [5, 0, 0, 0], [4, 0, 0, 0], [3, 6, 1, 0], [6, 4, 1, 0], [7, 4, 1, 0]];
  var S = { normal: 0, title: 1, head: 2, input: 3, result: 4, key: 5, note: 6, section: 7, sub: 8, total: 9, ok: 10, bad: 11, label: 12 };

  function Styles() { this.xfs = []; this.map = {}; this.get('normal', null); }
  Styles.prototype.get = function (name, dec) {
    var key = name + '|' + (dec === null || dec === undefined ? '' : dec);
    if (this.map[key] !== undefined) return this.map[key];
    var b = BASE[S[name] === 12 ? 0 : S[name]];
    var numFmt = dec === null || dec === undefined ? 0 : 164 + Math.max(0, Math.min(4, dec));
    var bord = name === 'label' ? 1 : b[2];
    var xf = '<xf numFmtId="' + numFmt + '" fontId="' + b[0] + '" fillId="' + b[1] + '" borderId="' + bord + '" xfId="0"' +
      (numFmt ? ' applyNumberFormat="1"' : '') + ' applyFont="1" applyFill="1" applyBorder="1"' +
      (b[3] || name === 'label' || name === 'note' ? ' applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>' : '><alignment vertical="top"/></xf>');
    this.xfs.push(xf);
    this.map[key] = this.xfs.length - 1;
    return this.map[key];
  };
  Styles.prototype.xml = function () {
    var fmts = '<numFmts count="5"><numFmt numFmtId="164" formatCode="0"/><numFmt numFmtId="165" formatCode="0.0"/><numFmt numFmtId="166" formatCode="0.00"/>' +
      '<numFmt numFmtId="167" formatCode="0.000"/><numFmt numFmtId="168" formatCode="0.0000"/></numFmts>';
    return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">' +
      fmts + FONTS + FILLS + BORDERS + '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>' +
      '<cellXfs count="' + this.xfs.length + '">' + this.xfs.join('') + '</cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>';
  };

  function xmlEsc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/g, ''); }
  function colName(i) { var s = ''; i++; while (i > 0) { var m = (i - 1) % 26; s = String.fromCharCode(65 + m) + s; i = Math.floor((i - 1) / 26); } return s; }

  function Sheet(name, styles) { this.name = name; this.rows = []; this.st = styles; this.widths = []; this.merges = []; this.heights = {}; }
  Sheet.prototype.set = function (r, c, v, style, dec) {
    (this.rows[r] = this.rows[r] || [])[c] = { v: v, s: this.st.get(style || 'normal', typeof v === 'number' ? dec : null) };
    var len = v === null || v === undefined ? 0 : (typeof v === 'number' ? 10 : String(v).length);
    if (style !== 'title' && style !== 'note' && style !== 'sub' && style !== 'section') this.widths[c] = Math.max(this.widths[c] || 8, Math.min(len + 2, 48));
  };
  Sheet.prototype.xml = function () {
    var cols = this.widths.map(function (w, i) { return '<col min="' + (i + 1) + '" max="' + (i + 1) + '" width="' + Math.max(w || 8, 8) + '" customWidth="1"/>'; }).join('');
    var self = this, data = this.rows.map(function (row, r) {
      if (!row) return '';
      var cells = row.map(function (c, ci) {
        if (!c) return '';
        var ref = colName(ci) + (r + 1);
        if (typeof c.v === 'number' && isFinite(c.v)) return '<c r="' + ref + '" s="' + c.s + '"><v>' + c.v + '</v></c>';
        if (c.v === '' || c.v === null || c.v === undefined) return '<c r="' + ref + '" s="' + c.s + '"/>';
        return '<c r="' + ref + '" s="' + c.s + '" t="inlineStr"><is><t xml:space="preserve">' + xmlEsc(c.v) + '</t></is></c>';
      }).join('');
      var ht = self.heights[r] ? ' ht="' + self.heights[r] + '" customHeight="1"' : '';
      return '<row r="' + (r + 1) + '"' + ht + '>' + cells + '</row>';
    }).join('');
    var merges = this.merges.length ? '<mergeCells count="' + this.merges.length + '">' + this.merges.map(function (m) { return '<mergeCell ref="' + m + '"/>'; }).join('') + '</mergeCells>' : '';
    return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">' +
      '<sheetViews><sheetView workbookViewId="0" showGridLines="0"/></sheetViews><sheetFormatPr defaultRowHeight="15"/>' +
      (cols ? '<cols>' + cols + '</cols>' : '') + '<sheetData>' + data + '</sheetData>' + merges +
      '<pageMargins left="0.5" right="0.5" top="0.6" bottom="0.6" header="0.3" footer="0.3"/><pageSetup paperSize="9" orientation="landscape" fitToHeight="0"/></worksheet>';
  };

  function safeName(s, used) {
    var n = String(s).replace(/[\[\]:*?\/\\]/g, ' ').replace(/&/g, 'and').slice(0, 31).trim() || 'Sheet';
    var base = n, k = 2;
    while (used[n.toLowerCase()]) { n = base.slice(0, 28) + ' ' + k++; }
    used[n.toLowerCase()] = 1;
    return n;
  }

  function render(model) {
    var st = new Styles(), sheets = [], used = {};
    var proj = model.project || {}, date = model.date.toISOString().slice(0, 10);

    // Summary sheet
    var sum = new Sheet(safeName('Summary', used), st); sheets.push(sum);
    var r = 0;
    sum.set(r++, 0, 'Calcs – calculation summary', 'title'); sum.heights[0] = 22;
    sum.set(r++, 0, 'Educational tool – verify every value against the governing code edition and authority requirements.', 'note');
    r++;
    [['Project', proj.name], ['Location', proj.location], ['Prepared by', proj.engineer], ['Client / reference', proj.client], ['Date', proj.date || date]].forEach(function (p) {
      if (!p[1]) return; sum.set(r, 0, p[0], 'label'); sum.set(r, 1, p[1], 'normal'); r++;
    });
    r++;
    sum.set(r, 0, 'Branch', 'head'); sum.set(r, 1, 'Calculation', 'head'); sum.set(r, 2, 'Key result', 'head'); sum.set(r, 3, 'Value', 'head'); sum.set(r, 4, 'Unit', 'head'); r++;
    model.branches.forEach(function (b) {
      b.tools.forEach(function (t) {
        t.tiles.forEach(function (k, i) {
          sum.set(r, 0, i ? '' : b.title, 'label'); sum.set(r, 1, i ? '' : t.title, 'label'); sum.set(r, 2, k.label, 'label');
          if (typeof k.value === 'number') sum.set(r, 3, k.value, 'key', k.dec === undefined ? 2 : k.dec); else sum.set(r, 3, k.text, 'key');
          sum.set(r, 4, k.unit || '', 'label'); r++;
        });
      });
    });
    sum.widths[0] = 16; sum.widths[1] = 28; sum.widths[2] = 26; sum.widths[3] = 22; sum.widths[4] = 10;

    model.branches.forEach(function (b) {
      b.tools.forEach(function (t) {
        var sh = new Sheet(safeName((model.branches.length > 1 ? b.short + ' ' : '') + t.title, used), st);
        sheets.push(sh);
        var r = 0;
        sh.set(r, 0, b.title + ' – ' + t.title, 'title'); sh.heights[r] = 22; r++;
        sh.set(r++, 0, t.sub + '  ·  ' + (proj.name || 'Untitled project') + '  ·  ' + date, 'sub');
        sh.set(r++, 0, 'Legend: yellow = inputs · light green = results · green = key results', 'note');
        r++;
        if (t.tiles.length) {
          sh.set(r++, 0, 'KEY RESULTS', 'section');
          t.tiles.forEach(function (k) {
            sh.set(r, 0, k.label, 'label');
            if (typeof k.value === 'number') sh.set(r, 1, k.value, 'key', k.dec === undefined ? 2 : k.dec); else sh.set(r, 1, k.text, 'key');
            sh.set(r, 2, k.unit || '', 'label'); r++;
          });
          r++;
        }
        t.blocks.forEach(function (bl) {
          if (bl.type === 'kv') {
            if (!bl.rows.length) return;
            sh.set(r++, 0, bl.title.toUpperCase(), 'section');
            bl.rows.forEach(function (x) {
              var style = x.kind === 'in' ? 'input' : x.kind === 'key' ? 'key' : 'result';
              if (x.status === 'ok' && x.kind !== 'in') style = 'ok';
              if (x.status === 'bad' && x.kind !== 'in') style = 'bad';
              sh.set(r, 0, x.label, 'label');
              if (typeof x.value === 'number' && isFinite(x.value)) sh.set(r, 1, x.value, style, x.dec === undefined ? decGuess(x.value) : x.dec);
              else sh.set(r, 1, x.text, style);
              sh.set(r, 2, x.unit, 'label'); r++;
            });
            r++;
          } else if (bl.type === 'table') {
            sh.set(r++, 0, bl.title.toUpperCase(), 'section');
            bl.head.forEach(function (h, j) { sh.set(r, j, h, 'head'); });
            sh.heights[r] = 30; r++;
            bl.rows.forEach(function (row) {
              row.forEach(function (c, j) {
                var k = bl.kinds[j], style = k === 'in' ? 'input' : k === 'key' ? 'key' : k === 'out' ? 'result' : 'label';
                if (c.status === 'ok') style = 'ok'; if (c.status === 'bad') style = 'bad';
                if (typeof c.value === 'number' && isFinite(c.value)) sh.set(r, j, c.value, style, c.dec === undefined ? decGuess(c.value) : c.dec);
                else sh.set(r, j, c.text, style);
              });
              r++;
            });
            if (bl.totals) {
              bl.totals.forEach(function (c, j) {
                if (typeof c.value === 'number') sh.set(r, j, c.value, 'total', 1); else sh.set(r, j, c.text || '', 'total');
              });
              r++;
            }
            if (!bl.rows.length) sh.set(r++, 0, 'No rows entered.', 'note');
            r++;
          } else if (bl.type === 'note') {
            sh.set(r, 0, bl.text, 'note'); r += 2;
          }
        });
        sh.widths[0] = Math.max(sh.widths[0] || 0, 30); if (sh.widths[0] > 46) sh.widths[0] = 46;
      });
    });

    var files = [];
    files.push({ name: '[Content_Types].xml', data: utf8('<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">' +
      '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>' +
      '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>' +
      '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>' +
      sheets.map(function (s, i) { return '<Override PartName="/xl/worksheets/sheet' + (i + 1) + '.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'; }).join('') +
      '</Types>') });
    files.push({ name: '_rels/.rels', data: utf8('<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
      '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>') });
    files.push({ name: 'xl/workbook.xml', data: utf8('<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>' +
      sheets.map(function (s, i) { return '<sheet name="' + xmlEsc(s.name) + '" sheetId="' + (i + 1) + '" r:id="rId' + (i + 1) + '"/>'; }).join('') + '</sheets></workbook>') });
    files.push({ name: 'xl/_rels/workbook.xml.rels', data: utf8('<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
      sheets.map(function (s, i) { return '<Relationship Id="rId' + (i + 1) + '" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet' + (i + 1) + '.xml"/>'; }).join('') +
      '<Relationship Id="rId' + (sheets.length + 1) + '" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>') });
    sheets.forEach(function (s, i) { files.push({ name: 'xl/worksheets/sheet' + (i + 1) + '.xml', data: utf8(s.xml()) }); });
    files.push({ name: 'xl/styles.xml', data: utf8(st.xml()) });
    return zip(files);
  }
  function decGuess(v) { var a = Math.abs(v); return a >= 100 ? 1 : a >= 1 ? 2 : 3; }

  root.XlsxReport = { render: render, zip: zip, crc32: crc32 };
})(typeof window !== 'undefined' ? window : this);
