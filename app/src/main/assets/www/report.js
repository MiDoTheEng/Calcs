/* Calcs – report model shared by the screen, the PDF and the Excel writers. */
(function (root) {
  'use strict';
  var E = root.Engine;

  function fmt(v, dec) {
    if (v === undefined || v === null || v === '') return '';
    if (typeof v === 'number') {
      if (!isFinite(v)) return '—';
      var d = dec === undefined ? 2 : dec;
      return v.toLocaleString('en-US', { minimumFractionDigits: d, maximumFractionDigits: d });
    }
    return String(v);
  }
  function status(v) {
    if (typeof v !== 'string') return '';
    if (/too|upsize|outside|above|small|warning|fix|combined/i.test(v)) return 'bad';
    if (/^ok|good|all ok/i.test(v)) return 'ok';
    return '';
  }
  function optLabel(opts, v) {
    for (var i = 0; i < opts.length; i++) {
      var o = opts[i];
      if (Array.isArray(o)) { if (String(o[0]) === String(v)) return o[1]; } else if (String(o) === String(v)) return o;
    }
    return v === undefined ? '' : String(v);
  }
  function rowCount(tool, b, s) {
    if (b.fixed) return b.fixed.length;
    if (b.count) return tool.count(s);
    return (s[b.k] || []).length;
  }
  function gridHead(b) {
    var h = [];
    if (b.rowLabel) h.push('#');
    (b.fixedCols || []).forEach(function (c) { h.push(c.label); });
    b.cols.forEach(function (c) { h.push(c.label); });
    b.outs.forEach(function (c) { h.push(c.label); });
    return h;
  }
  function isEmptyRow(b, row) {
    return b.cols.every(function (c) { var v = E.num(row[c.k]); return isNaN(v) || v === 0; });
  }

  // Build the model for one tool
  function toolModel(tool, s) {
    var r = tool.compute(s);
    var m = { title: tool.title, sub: tool.sub, tiles: [], blocks: [] };
    (tool.tiles ? tool.tiles(s, r) : []).forEach(function (t) {
      m.tiles.push({ label: t[0], text: fmt(t[1], t[3]), unit: t[2], value: t[1], dec: t[3] });
    });
    tool.blocks.forEach(function (b) {
      if (b.t === 'section') {
        var rows = b.fields.map(function (f) {
          var v = s[f.k];
          var text = f.kind === 'select' ? optLabel(f.options, v) : fmt(E.num(v), decOf(v));
          if (f.kind !== 'select' && (v === '' || v === undefined)) text = '';
          return { label: f.label, text: text, unit: f.unit || '', value: f.kind === 'select' ? text : E.num(v), dec: decOf(v), kind: 'in' };
        });
        var res = b.results ? b.results(s, r) : [];
        m.blocks.push({ type: 'kv', title: b.title, rows: rows.concat(res.map(function (x) {
          return { label: x[0], text: fmt(x[1], x[3]), unit: x[2] || '', value: x[1], dec: x[3], kind: x[4] ? 'key' : 'out', status: status(x[1]) };
        })) });
      } else if (b.t === 'grid') {
        var head = gridHead(b), kinds = [], out = [], n = rowCount(tool, b, s), list = s[b.k] || [];
        if (b.rowLabel) kinds.push('fixed');
        (b.fixedCols || []).forEach(function () { kinds.push('fixed'); });
        b.cols.forEach(function () { kinds.push('in'); });
        b.outs.forEach(function (o) { kinds.push(o.key ? 'key' : 'out'); });
        for (var i = 0; i < n; i++) {
          var row = list[i] || {};
          if (b.skipEmpty && isEmptyRow(b, row)) continue;
          var cells = [];
          if (b.rowLabel) cells.push({ text: b.rowLabel(i), value: b.rowLabel(i) });
          (b.fixed ? b.fixed[i] : []).forEach(function (v, j) { cells.push({ text: fmt(v, b.fixedCols[j].dec), value: v, dec: b.fixedCols[j].dec }); });
          b.cols.forEach(function (c) {
            var v = row[c.k];
            if (c.kind === 'select') { var t = optLabel(c.options, v); cells.push({ text: t, value: t }); }
            else if (c.kind === 'text') cells.push({ text: v || '', value: v || '' });
            else cells.push({ text: (v === '' || v === undefined) ? '' : fmt(E.num(v), decOf(v)), value: E.num(v), dec: decOf(v) });
          });
          b.outs.forEach(function (o) { var v = o.get(r, i); cells.push({ text: fmt(v, o.dec), value: v, dec: o.dec, status: status(v) }); });
          out.push(cells);
        }
        var totals = null;
        if (b.totals) {
          totals = b.totals(r).map(function (v, j) { return { text: typeof v === 'number' ? fmt(v, 1) : v, value: v, dec: 1 }; });
          while (totals.length < head.length) totals.push({ text: '', value: '' });
        }
        m.blocks.push({ type: 'table', title: b.title, head: head, kinds: kinds, rows: out, totals: totals, emptyNote: out.length ? '' : 'No rows entered.' });
        if (b.results) {
          m.blocks.push({ type: 'kv', title: 'Results', rows: b.results(s, r).map(function (x) {
            return { label: x[0], text: fmt(x[1], x[3]), unit: x[2] || '', value: x[1], dec: x[3], kind: x[4] ? 'key' : 'out', status: status(x[1]) };
          }) });
        }
      } else if (b.t === 'note') {
        m.blocks.push({ type: 'note', text: b.text });
      }
    });
    return m;
  }
  function decOf(v) {
    var n = E.num(v);
    if (isNaN(n)) return 0;
    var s = String(v), i = s.indexOf('.');
    return i < 0 ? 0 : Math.min(4, s.length - i - 1);
  }

  function build(branches, state) {
    return {
      app: 'Calcs',
      project: state.project || {},
      date: new Date(),
      branches: branches.map(function (br) {
        return { id: br.id, short: br.short, title: br.title, tools: br.tools.map(function (t) { return toolModel(t, state.tools[t.id]); }) };
      })
    };
  }

  root.Report = { fmt: fmt, status: status, optLabel: optLabel, build: build, toolModel: toolModel, rowCount: rowCount };
})(typeof window !== 'undefined' ? window : this);
