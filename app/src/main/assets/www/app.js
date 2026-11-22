/* Calcs – UI */
(function () {
  'use strict';
  var E = window.Engine, T = window.Tools, R = window.Report;
  var BR = T.BRANCHES;
  var KEY = 'calcs.v1';
  var app = document.getElementById('app');

  /* ------------------------------ state ------------------------------ */
  var S = load();
  function load() {
    var s = null;
    try { s = JSON.parse(localStorage.getItem(KEY) || 'null'); } catch (e) { s = null; }
    s = s && typeof s === 'object' ? s : {};
    s.project = s.project || { name: '', location: '', engineer: '', client: '', date: '' };
    s.tools = s.tools || {};
    s.tab = s.tab || {};
    BR.forEach(function (b) {
      b.tools.forEach(function (t) {
        var d = t.defaults(), cur = s.tools[t.id];
        if (!cur) { s.tools[t.id] = d; return; }
        Object.keys(d).forEach(function (k) { if (cur[k] === undefined) cur[k] = d[k]; });
        if (Array.isArray(d.fix) && (!Array.isArray(cur.fix) || cur.fix.length !== d.fix.length)) cur.fix = d.fix;
      });
    });
    return s;
  }
  var saveTimer = null;
  function save() {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(function () { try { localStorage.setItem(KEY, JSON.stringify(S)); } catch (e) { /* storage unavailable */ } }, 250);
  }

  /* ------------------------------ helpers ------------------------------ */
  function h(tag, attrs, kids) {
    var el = document.createElement(tag);
    if (attrs) Object.keys(attrs).forEach(function (k) {
      var v = attrs[k];
      if (v === undefined || v === null || v === false) return;
      if (k === 'class') el.className = v;
      else if (k === 'text') el.textContent = v;
      else if (k === 'html') el.innerHTML = v;
      else if (k.slice(0, 2) === 'on') el.addEventListener(k.slice(2), v);
      else el.setAttribute(k, v === true ? '' : v);
    });
    (kids || []).forEach(function (c) { if (c !== null && c !== undefined && c !== false) el.appendChild(typeof c === 'string' ? document.createTextNode(c) : c); });
    return el;
  }
  var ICON = {
    back: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 18l-6-6 6-6"/></svg>',
    pdf: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/><path d="M9 14h6M9 17h4"/></svg>',
    xls: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="4" width="16" height="16" rx="2"/><path d="M4 10h16M4 15h16M10 4v16"/></svg>',
    reset: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 1 0 3-6.7"/><path d="M3 4v5h5"/></svg>',
    clear: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M5 7h14M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/></svg>',
    plus: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>',
    swap: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M7 4L3 8l4 4M3 8h14M17 20l4-4-4-4M21 16H7"/></svg>'
  };
  function icon(name) { var s = h('span'); s.innerHTML = ICON[name]; return s.firstChild; }
  function toast(msg) {
    var t = document.getElementById('toast');
    t.textContent = msg; t.classList.add('show');
    clearTimeout(toast.tm); toast.tm = setTimeout(function () { t.classList.remove('show'); }, 2600);
  }
  function statusClass(v) { var s = R.status(v); return s === 'ok' ? 'ok' : s === 'bad' ? 'bad' : ''; }
  function rawValue(v) { return v === undefined || v === null ? '' : String(v); }

  /* ------------------------------ routing ------------------------------ */
  function route() {
    var p = (location.hash || '#/').slice(2).split('/');
    window.scrollTo(0, 0);
    if (p[0] === 'b') {
      var b = BR.filter(function (x) { return x.id === p[1]; })[0];
      if (b) return branchPage(b, p[2]);
    }
    if (p[0] === 'units') return unitsPage();
    homePage();
  }
  window.addEventListener('hashchange', route);
  // Android back button hook
  window.calcsBack = function () {
    if (document.querySelector('.sheet-bg')) { closeSheet(); return true; }
    if ((location.hash || '#/') !== '#/') { history.back(); return true; }
    return false;
  };

  /* ------------------------------ home ------------------------------ */
  function homePage() {
    document.body.setAttribute('data-accent', '');
    app.innerHTML = '';
    var bar = h('div', { class: 'bar' }, [h('div', { class: 'wrap bar-in' }, [
      h('div', { class: 'grow' }, [h('h1', { text: 'Calcs' }), h('div', { class: 'crumb', text: 'MEP design calculations' })]),
      exportButtons(function () { return BR; }, 'all')
    ])]);
    app.appendChild(bar);
    var w = h('div', { class: 'wrap' });
    w.appendChild(h('div', { class: 'hero' }, [h('h2', { text: S.project.name || 'New project' }), h('p', { text: 'Pick a branch, enter your data, export the calculation as PDF or Excel.' })]));

    var grid = h('div', { class: 'grid-home' });
    BR.forEach(function (b) {
      var card = h('button', { class: 'bcard', style: '--c: var(--' + b.accent + ')', onclick: function () { location.hash = '#/b/' + b.id; } }, [
        h('div', { class: 'code', text: b.short }),
        h('h3', { text: b.title }),
        h('p', { text: b.desc }),
        h('ul', null, b.tools.map(function (t) { return h('li', { text: t.title }); }))
      ]);
      grid.appendChild(card);
    });
    grid.appendChild(h('button', { class: 'bcard', style: '--c: var(--units)', onclick: function () { location.hash = '#/units'; } }, [
      h('div', { class: 'code', text: 'U' }), h('h3', { text: 'Unit converter' }), h('p', { text: 'Flow, pressure, length, volume, area, power, sprinkler density & K-factor' })
    ]));
    w.appendChild(grid);

    var pc = h('div', { class: 'card' }, [h('h3', { text: 'Project details (printed on every report)' })]);
    var body = h('div', { class: 'card-body' });
    var proj = h('div', { class: 'proj' });
    [['name', 'Project name'], ['location', 'Location'], ['engineer', 'Prepared by'], ['client', 'Client / reference'], ['date', 'Date (blank = today)']].forEach(function (f) {
      var inp = h('input', { class: 'in text', type: f[0] === 'date' ? 'date' : 'text', value: S.project[f[0]] || '' });
      inp.addEventListener('input', function () {
        S.project[f[0]] = inp.value; save();
        if (f[0] === 'name') w.querySelector('.hero h2').textContent = inp.value || 'New project';
      });
      proj.appendChild(h('div', null, [h('label', { text: f[1] }), inp]));
    });
    body.appendChild(proj);
    body.appendChild(h('div', { class: 'row-actions' }, [
      h('button', { class: 'btn', onclick: function () {
        confirmSheet('Start a new project?', 'Clears the project details and resets every calculation to its worked example.', 'Reset everything', function () {
          try { localStorage.removeItem(KEY); } catch (e) { /* ignore */ }
          S = load(); S.project = { name: '', location: '', engineer: '', client: '', date: '' }; save(); route(); toast('Reset done');
        });
      } }, [icon('reset'), 'New project'])
    ]));
    pc.appendChild(body);
    w.appendChild(pc);
    w.appendChild(h('div', { class: 'foot', text: 'Educational tool – verify every value against the governing code edition and the authority requirements.' }));
    app.appendChild(w);
  }

  /* ------------------------------ branch ------------------------------ */
  function branchPage(b, toolId) {
    document.body.setAttribute('data-accent', b.accent);
    var tool = b.tools.filter(function (t) { return t.id === toolId; })[0] || b.tools.filter(function (t) { return t.id === S.tab[b.id]; })[0] || b.tools[0];
    S.tab[b.id] = tool.id; save();
    app.innerHTML = '';
    var bar = h('div', { class: 'bar' }, [h('div', { class: 'stripe' }), h('div', { class: 'wrap bar-in' }, [
      h('button', { class: 'iconbtn', 'aria-label': 'Back', onclick: function () { location.hash = '#/'; } }, [icon('back')]),
      h('div', { class: 'grow' }, [h('h1', { text: b.title }), h('div', { class: 'crumb', text: S.project.name || 'Untitled project' })]),
      exportButtons(function () { return [b]; }, b.id)
    ])]);
    var tabs = h('div', { class: 'wrap tabs' }, b.tools.map(function (t) {
      return h('button', { class: 'tab' + (t === tool ? ' on' : ''), text: t.title, onclick: function () { location.replace('#/b/' + b.id + '/' + t.id); } });
    }));
    bar.appendChild(tabs);
    app.appendChild(bar);
    var w = h('div', { class: 'wrap' });
    app.appendChild(w);
    renderTool(w, tool);
    setTimeout(function () { var on = tabs.querySelector('.on'); if (on && on.scrollIntoView) on.scrollIntoView({ inline: 'center', block: 'nearest' }); }, 0);
  }

  function renderTool(w, tool) {
    var s = S.tools[tool.id];
    var updaters = [];
    var res = tool.compute(s);
    function refresh() {
      try { res = tool.compute(s); } catch (e) { return; }
      updaters.forEach(function (u) { u(res); });
    }
    function changed() { save(); refresh(); }
    function rebuild() { save(); var y = window.scrollY; w.innerHTML = ''; renderTool(w, tool); window.scrollTo(0, y); }

    w.appendChild(h('div', { class: 'toolhead' }, [
      h('div', { class: 't' }, [h('h2', { text: tool.title }), h('p', { text: tool.sub })]),
      h('button', { class: 'btn ghost', onclick: function () {
        confirmSheet('Load the worked example?', 'Replaces the inputs of “' + tool.title + '” with the example values.', 'Load example', function () { S.tools[tool.id] = tool.defaults(); s = S.tools[tool.id]; rebuild(); });
      } }, [icon('reset'), 'Example']),
      tool.blank ? h('button', { class: 'btn ghost', onclick: function () {
        confirmSheet('Clear this calculation?', 'Empties the quantities / rows of “' + tool.title + '” so you can enter your project.', 'Clear', function () { S.tools[tool.id] = tool.blank(); s = S.tools[tool.id]; rebuild(); });
      } }, [icon('clear'), 'Clear']) : null
    ]));

    // tiles
    if (tool.tiles) {
      var tiles = h('div', { class: 'tiles' });
      w.appendChild(tiles);
      updaters.push(function (r) {
        tiles.innerHTML = '';
        tool.tiles(s, r).forEach(function (t) {
          var v = R.fmt(t[1], t[3]);
          tiles.appendChild(h('div', { class: 'tile' }, [h('div', { class: 'tile-l', text: t[0] }),
            h('div', { class: 'tile-v ' + statusClass(t[1]) }, [v, t[2] ? h('small', { text: t[2] }) : null])]));
        });
      });
    }

    tool.blocks.forEach(function (b) {
      if (b.t === 'section') w.appendChild(sectionCard(tool, b, s, updaters, changed, rebuild));
      else if (b.t === 'grid') w.appendChild(gridCard(tool, b, s, updaters, changed, rebuild));
      else if (b.t === 'note') w.appendChild(h('div', { class: 'note', text: b.text }));
      else if (b.t === 'sketch') w.appendChild(sketchCard(tool, s, updaters));
    });
    refresh();
  }

  function fieldInput(f, obj, key, onChange, onRebuild, wide) {
    var el;
    if (f.kind === 'select') {
      el = h('select', { class: 'in' }, f.options.map(function (o) {
        var v = Array.isArray(o) ? o[0] : o, l = Array.isArray(o) ? o[1] : o;
        return h('option', { value: v, text: l });
      }));
      el.value = rawValue(obj[key]);
      if (el.value !== rawValue(obj[key]) && el.options.length) {
        // numeric options (e.g. 26.6 vs "26.6")
        for (var i = 0; i < el.options.length; i++) if (parseFloat(el.options[i].value) === parseFloat(obj[key])) el.selectedIndex = i;
      }
      el.addEventListener('change', function () {
        var v = el.value, n = parseFloat(v);
        obj[key] = v !== '' && String(n) === v ? n : v;
        if (f.onChange) { f.onChange(obj); onRebuild(); return; }
        onChange();
      });
    } else {
      el = h('input', { class: 'in' + (f.kind === 'text' ? ' text' : ''), type: 'text', inputmode: f.kind === 'text' ? 'text' : 'decimal', value: rawValue(obj[key]), autocomplete: 'off' });
      el.addEventListener('input', function () {
        var v = el.value.trim();
        if (f.kind === 'text') obj[key] = el.value;
        else { var n = parseFloat(v.replace(',', '.')); obj[key] = v === '' ? '' : (isFinite(n) && /^-?[\d.,]+$/.test(v) ? n : v); }
        if (!f.rebuild) onChange();
      });
      if (f.rebuild) el.addEventListener('change', function () { onRebuild(); });
      el.addEventListener('focus', function () { setTimeout(function () { try { el.select(); } catch (e) { /* ignore */ } }, 0); });
    }
    return el;
  }

  function resultRows(rows) {
    return rows.map(function (x) {
      return h('div', { class: 'r' + (x[4] ? ' key' : '') }, [h('div', { class: 'l', text: x[0] }), h('div', { class: 'v ' + statusClass(x[1]), text: R.fmt(x[1], x[3]) }), h('div', { class: 'u', text: x[2] || '' })]);
    });
  }

  function sectionCard(tool, b, s, updaters, changed, rebuild) {
    var card = h('div', { class: 'card' }, [h('h3', { text: b.title })]);
    var body = h('div', { class: 'card-body' });
    var split = h('div', { class: 'split' + (b.fields.length && b.results ? '' : ' single') });
    if (b.fields.length) {
      var fields = h('div', { class: 'fields' });
      b.fields.forEach(function (f) {
        var row = h('div', { class: 'field' + (f.kind === 'select' && !f.unit ? ' wide' : '') }, [h('label', { text: f.label }), fieldInput(f, s, f.k, changed, rebuild)]);
        if (!(f.kind === 'select' && !f.unit)) row.appendChild(h('div', { class: 'unit', text: f.unit || '' }));
        if (f.hint) row.appendChild(h('div', { class: 'hint', text: f.hint }));
        fields.appendChild(row);
      });
      if (b.action) fields.appendChild(h('div', null, [h('button', { class: 'btn', onclick: function () { b.action.run(s); rebuild(); toast('Applied'); } }, [b.action.label])]));
      split.appendChild(fields);
    }
    if (b.results) {
      var list = h('div', { class: 'res' });
      split.appendChild(list);
      updaters.push(function (r) { list.innerHTML = ''; resultRows(b.results(s, r)).forEach(function (n) { list.appendChild(n); }); });
    }
    body.appendChild(split);
    card.appendChild(body);
    return card;
  }

  function gridCard(tool, b, s, updaters, changed, rebuild) {
    var card = h('div', { class: 'card' }, [h('h3', { text: b.title })]);
    if (b.help) card.appendChild(h('p', { class: 'help', text: b.help }));
    var body = h('div', { class: 'card-body' });
    var list = s[b.k] = s[b.k] || [];
    var n = R.rowCount(tool, b, s);
    if (b.count) { while (list.length < n) list.push(JSON.parse(JSON.stringify(list[list.length - 1] || { d: 26.6, len: 3.5, fit: 0, dz: 0 }))); }
    var head = [];
    if (b.rowLabel) head.push('#');
    (b.fixedCols || []).forEach(function (c) { head.push(c.label); });
    b.cols.forEach(function (c) { head.push(c.label); });
    b.outs.forEach(function (c) { head.push(c.label); });
    if (b.add) head.push('');
    var table = h('table', { class: 'g' }, [h('thead', null, [h('tr', null, head.map(function (x) { return h('th', { text: x }); }))])]);
    var tbody = h('tbody');
    table.appendChild(tbody);
    var outCells = [];
    for (var i = 0; i < n; i++) (function (i) {
      var row = list[i] = list[i] || {};
      var tr = h('tr');
      if (b.rowLabel) tr.appendChild(h('td', { class: 'fx', text: b.rowLabel(i) }));
      (b.fixed ? b.fixed[i] : []).forEach(function (v, j) {
        var c = b.fixedCols[j];
        tr.appendChild(h('td', { class: 'fx' + (j ? ' dim' : ''), style: typeof v === 'number' ? 'text-align:right' : null, text: R.fmt(v, c.dec) }));
      });
      b.cols.forEach(function (c) {
        tr.appendChild(h('td', { class: 'inp' + (c.kind === 'text' ? ' wide' : c.kind === 'select' ? ' sel' : '') }, [fieldInput(c, row, c.k, changed, rebuild)]));
      });
      var oc = b.outs.map(function (o) { var td = h('td', { class: 'o' + (o.key ? ' key' : '') }); tr.appendChild(td); return td; });
      outCells.push({ tr: tr, cells: oc, row: row });
      if (b.add) tr.appendChild(h('td', { class: 'del' }, [h('button', { class: 'delbtn', 'aria-label': 'Delete row', text: '×', onclick: function () {
        if (list.length <= 1) { toast('Keep at least one row'); return; }
        list.splice(i, 1); rebuild();
      } })]));
      tbody.appendChild(tr);
    })(i);
    var totRow = null;
    if (b.totals) { totRow = h('tr', { class: 'tot' }); tbody.appendChild(totRow); }
    updaters.push(function (r) {
      outCells.forEach(function (x, i) {
        x.cells.forEach(function (td, j) {
          var o = b.outs[j], v = o.get(r, i);
          td.textContent = R.fmt(v, o.dec);
          td.className = 'o' + (o.key ? ' key' : '') + (typeof v === 'string' ? ' txt ' + statusClass(v) : '');
        });
        if (b.fixed) {
          var used = b.cols.some(function (c) { return E.num(x.row[c.k]) > 0; });
          x.tr.className = used ? 'used' : '';
        }
      });
      if (totRow) {
        totRow.innerHTML = '';
        var t = b.totals(r);
        for (var k = 0; k < head.length; k++) {
          var v = t[k];
          totRow.appendChild(h('td', { style: typeof v === 'number' ? 'text-align:right' : null, text: typeof v === 'number' ? R.fmt(v, 1) : (v || '') }));
        }
      }
    });
    var scroller = h('div', { class: 'tscroll' }, [table]);
    body.appendChild(scroller);
    // Narrow screens: the result columns may be scrolled out of view, so repeat the key ones under the table.
    var keyOuts = b.outs.filter(function (o) { return o.key || /^(Check|Velocity check|Duration|Min vent)/.test(o.label); });
    if (b.add && keyOuts.length) {
      var summary = h('div', { class: 'res', style: 'margin-top:10px' });
      body.appendChild(summary);
      updaters.push(function (r) {
        summary.hidden = !(scroller.scrollWidth > scroller.clientWidth + 4);
        if (summary.hidden) return;
        summary.innerHTML = '';
        outCells.forEach(function (x, i) {
          var name = x.row.name || x.row.zone || ('Row ' + (i + 1));
          var parts = [], bad = false;
          keyOuts.forEach(function (o) {
            var v = o.get(r, i);
            if (v === undefined || v === '') return;
            if (R.status(v) === 'bad') bad = true;
            var unit = /\((.*)\)/.test(o.label) ? o.label.match(/\((.*)\)/)[1] : '';
            var pre = o.key || /vent/i.test(o.label) ? o.label.replace(/\s*\(.*\)/, '') + ' ' : '';
            parts.push(pre + R.fmt(v, o.dec) + (typeof v === 'number' && unit ? ' ' + unit : ''));
          });
          summary.appendChild(h('div', { class: 'r' }, [h('div', { class: 'l', text: name }), h('div', { class: 'v ' + (bad ? 'bad' : ''), text: parts.join(' · ') }), h('div', { class: 'u' })]));
        });
      });
    }
    if (b.add) {
      body.appendChild(h('div', { class: 'gridfoot' }, [h('button', { class: 'btn', onclick: function () {
        var last = list[list.length - 1];
        var nr = JSON.parse(JSON.stringify(b.add));
        if (last) b.cols.forEach(function (c) { if (c.k !== 'name' && c.k !== 'zone' && c.kind !== 'text' && (nr[c.k] === '' || nr[c.k] === undefined) && last[c.k] !== undefined && /vt|fpct|c|n|slope|K|coverage|duration/.test(c.k)) nr[c.k] = last[c.k]; });
        nr.name !== undefined && (nr.name = 'Segment ' + (list.length + 1));
        list.push(nr); rebuild();
      } }, [icon('plus'), 'Add row'])]));
    }
    if (b.results) {
      var resList = h('div', { class: 'res', style: 'margin-top:12px' });
      body.appendChild(resList);
      updaters.push(function (r) { resList.innerHTML = ''; resultRows(b.results(s, r)).forEach(function (n) { resList.appendChild(n); }); });
    }
    card.appendChild(body);
    return card;
  }

  /* --------------------------- fire branch sketch --------------------------- */
  function sketchCard(tool, s, updaters) {
    var card = h('div', { class: 'card' }, [h('h3', { text: 'Branch sketch' }), h('p', { class: 'help', text: 'Tap a sprinkler to see its data next to the one before it. Pipe colour = velocity.' })]);
    var body = h('div', { class: 'card-body sketch' });
    var holder = h('div', { class: 'tscroll', style: 'border:none' });
    var info = h('div', { class: 'sketch-info' });
    var legend = h('div', { class: 'legend' });
    body.appendChild(holder); body.appendChild(legend); body.appendChild(info);
    card.appendChild(body);
    var sel = -1;
    function vcol(v) { return v > (E.num(s.vPractice) || 6) ? 'var(--v-bad)' : v > (E.num(s.vGood) || 3) ? 'var(--v-ok)' : 'var(--v-good)'; }
    function draw(r) {
      var rows = r.rows, n = rows.length;
      if (sel >= n) sel = n - 1;
      if (sel < 0) sel = n - 1;
      var gap = 92, W = Math.max(640, 70 + gap * n + 40), H = 210, y0 = 112;
      var x = function (i) { return W - 50 - i * gap; }; // S1 (i=0) at the right
      var riserX = x(n - 1) - gap;
      var svg = '<svg viewBox="0 0 ' + W + ' ' + H + '" width="' + Math.round(W * 1.3) + '" height="' + Math.round(H * 1.3) + '" font-family="system-ui, sans-serif">';
      svg += '<rect x="' + (riserX - 6) + '" y="30" width="12" height="150" rx="3" fill="var(--head)"/>';
      svg += '<text x="' + riserX + '" y="22" text-anchor="middle" font-size="12" font-weight="700" fill="var(--ink)">Riser</text>';
      svg += '<text x="' + (riserX + 12) + '" y="198" font-size="12" font-weight="700" fill="var(--ink)">Inlet ' + r.Pin.toFixed(2) + ' bar · ' + r.Q.toFixed(0) + ' L/min</text>';
      rows.forEach(function (o, i) {
        var xa = x(i), xb = i === n - 1 ? riserX : x(i + 1);
        var wdt = Math.max(3, Math.min(11, (o.d - 15) / 5));
        svg += '<line x1="' + xa + '" y1="' + y0 + '" x2="' + xb + '" y2="' + y0 + '" stroke="' + vcol(o.v) + '" stroke-width="' + wdt + '" stroke-linecap="round"/>';
        svg += '<text x="' + ((xa + xb) / 2) + '" y="' + (y0 + 26) + '" text-anchor="middle" font-size="11" font-weight="700" fill="' + vcol(o.v) + '">' + o.v.toFixed(1) + ' m/s</text>';
        svg += '<text x="' + ((xa + xb) / 2) + '" y="' + (y0 + 40) + '" text-anchor="middle" font-size="10" fill="var(--muted)">Ø' + o.d + '</text>';
      });
      rows.forEach(function (o, i) {
        var xa = x(i), on = i === sel;
        svg += '<g class="node" data-i="' + i + '">';
        svg += '<rect x="' + (xa - 40) + '" y="34" width="80" height="96" fill="transparent"/>';
        svg += '<line x1="' + xa + '" y1="' + (y0 - 18) + '" x2="' + xa + '" y2="' + y0 + '" stroke="var(--muted)" stroke-width="2"/>';
        svg += '<circle cx="' + xa + '" cy="' + (y0 - 22) + '" r="' + (on ? 9 : 7) + '" fill="' + (on ? 'var(--accent)' : 'var(--surface)') + '" stroke="var(--accent)" stroke-width="3"/>';
        svg += '<text x="' + xa + '" y="42" text-anchor="middle" font-size="12" font-weight="800" fill="var(--ink)">S' + (i + 1) + '</text>';
        svg += '<text x="' + xa + '" y="57" text-anchor="middle" font-size="11" fill="var(--ink)">' + o.P.toFixed(2) + ' bar</text>';
        svg += '<text x="' + xa + '" y="71" text-anchor="middle" font-size="11" fill="var(--muted)">' + o.q.toFixed(1) + ' L/min</text>';
        svg += '</g>';
      });
      svg += '</svg>';
      holder.innerHTML = svg;
      Array.prototype.forEach.call(holder.querySelectorAll('.node'), function (g) {
        g.addEventListener('click', function () { sel = +g.getAttribute('data-i'); draw(r); });
      });
      legend.innerHTML = '<span><i style="background:var(--v-good)"></i>&lt; ' + (E.num(s.vGood) || 3) + ' m/s</span><span><i style="background:var(--v-ok)"></i>' +
        (E.num(s.vGood) || 3) + '–' + (E.num(s.vPractice) || 6) + ' m/s</span><span><i style="background:var(--v-bad)"></i>&gt; ' + (E.num(s.vPractice) || 6) + ' m/s → upsize</span>';
      var a = rows[sel], p = rows[sel - 1];
      function box(title, o) {
        return '<div class="box"><b>' + title + '</b>P = ' + o.P.toFixed(3) + ' bar (' + (o.P * E.BAR_M).toFixed(1) + ' m)<br>q = ' + o.q.toFixed(1) + ' L/min<br>Pipe after it: ' +
          o.Q.toFixed(1) + ' L/min · Ø' + o.d + ' · ' + o.v.toFixed(2) + ' m/s<br>Loss to next point: ' + o.loss.toFixed(3) + ' bar</div>';
      }
      info.innerHTML = box('S' + (sel + 1) + ' (selected)', a) + (p ? box('S' + sel + ' (before it)', p) +
        '<div class="box"><b>Difference S' + (sel + 1) + ' − S' + sel + '</b>ΔP = +' + (a.P - p.P).toFixed(3) + ' bar<br>Δq = +' + (a.q - p.q).toFixed(1) + ' L/min<br>Pressure rises toward the riser, so each sprinkler gives more water.</div>'
        : '<div class="box"><b>S1 is the most remote</b>Its pressure is your input (' + E.num(s.p1) + ' bar). Every other sprinkler is worked out from it.</div>');
    }
    updaters.push(draw);
    return card;
  }

  /* ------------------------------ units ------------------------------ */
  function unitsPage() {
    document.body.setAttribute('data-accent', 'units');
    app.innerHTML = '';
    app.appendChild(h('div', { class: 'bar' }, [h('div', { class: 'stripe' }), h('div', { class: 'wrap bar-in' }, [
      h('button', { class: 'iconbtn', 'aria-label': 'Back', onclick: function () { location.hash = '#/'; } }, [icon('back')]),
      h('div', { class: 'grow' }, [h('h1', { text: 'Unit converter' }), h('div', { class: 'crumb', text: 'Type a value, pick its unit' })])
    ])]));
    var w = h('div', { class: 'wrap', style: 'padding-top:16px' });
    var grid = h('div', { class: 'units-grid' });
    var names = { flow: 'Flow', pressure: 'Pressure / head', length: 'Length / diameter', volume: 'Volume', area: 'Area', power: 'Power', density: 'Sprinkler density', kfactor: 'K-factor' };
    S.units = S.units || {};
    Object.keys(E.UNITS).forEach(function (g) {
      var st = S.units[g] = S.units[g] || { v: 1, u: E.UNITS[g].u[0][0] };
      var card = h('div', { class: 'card' }, [h('h3', { text: names[g] })]);
      var body = h('div', { class: 'card-body' });
      var inp = h('input', { class: 'in', inputmode: 'decimal', value: st.v });
      var selU = h('select', { class: 'in' }, E.UNITS[g].u.map(function (u) { return h('option', { value: u[0], text: u[0] }); }));
      selU.value = st.u;
      var out = h('div', { class: 'res' });
      function upd() {
        st.v = inp.value; st.u = selU.value; save();
        out.innerHTML = '';
        E.convert(g, inp.value, selU.value).forEach(function (c) {
          var a = Math.abs(c[1]), d = a === 0 ? 0 : a >= 1000 ? 1 : a >= 1 ? 3 : 5;
          out.appendChild(h('div', { class: 'r' + (c[0] === selU.value ? ' key' : '') }, [h('div', { class: 'l', text: c[0] }), h('div', { class: 'v', text: isNaN(c[1]) ? '—' : R.fmt(c[1], d) }), h('div', { class: 'u' })]));
        });
      }
      inp.addEventListener('input', upd); selU.addEventListener('change', upd);
      body.appendChild(h('div', { class: 'conv' }, [inp, selU]));
      body.appendChild(out);
      card.appendChild(body);
      grid.appendChild(card);
      upd();
    });
    w.appendChild(grid);
    app.appendChild(w);
  }

  /* ------------------------------ sheets ------------------------------ */
  function closeSheet() { var s = document.querySelector('.sheet-bg'); if (s) s.remove(); }
  function sheet(content) {
    closeSheet();
    var bg = h('div', { class: 'sheet-bg', onclick: function (e) { if (e.target === bg) closeSheet(); } }, [h('div', { class: 'sheet' }, content)]);
    document.body.appendChild(bg);
  }
  function confirmSheet(title, msg, okLabel, fn) {
    sheet([h('h4', { text: title }), h('p', { text: msg }), h('div', { class: 'opts' }, [
      h('button', { class: 'opt', onclick: function () { closeSheet(); } }, [h('b', { text: 'Cancel' })]),
      h('button', { class: 'opt', style: 'border-color:var(--accent)', onclick: function () { closeSheet(); fn(); } }, [h('b', { text: okLabel })])
    ])]);
  }

  /* ------------------------------ export ------------------------------ */
  function exportButtons(getBranches, tag) {
    return h('div', { style: 'display:flex;gap:8px' }, [
      h('button', { class: 'btn primary', onclick: function () { doExport(getBranches(), tag, 'pdf'); } }, [icon('pdf'), 'PDF']),
      h('button', { class: 'btn', onclick: function () { doExport(getBranches(), tag, 'xlsx'); } }, [icon('xls'), 'Excel'])
    ]);
  }
  function b64(bytes) {
    var s = '', chunk = 0x8000;
    for (var i = 0; i < bytes.length; i += chunk) s += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
    return btoa(s);
  }
  function slug(s) { return String(s || '').replace(/[^A-Za-z0-9؀-ۿ]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 40); }
  function doExport(branches, tag, kind) {
    var model;
    try { model = R.build(branches, S); } catch (e) { toast('Could not build the report: ' + e.message); return; }
    var title = branches.length > 1 ? 'MEP Calculations' : branches[0].title;
    var bytes;
    try {
      bytes = kind === 'pdf' ? PdfReport.render(model, { accent: branches.length > 1 ? 'all' : branches[0].id === 'drainage' ? 'drainage' : branches[0].id, heading: title, title: title })
        : XlsxReport.render(model);
    } catch (e) { toast('Export failed: ' + e.message); return; }
    var date = new Date().toISOString().slice(0, 10);
    var name = ['Calcs', slug(S.project.name) || 'Project', slug(title), date].join('_') + (kind === 'pdf' ? '.pdf' : '.xlsx');
    var mime = kind === 'pdf' ? 'application/pdf' : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
    if (window.CalcsAndroid && window.CalcsAndroid.saveFile) {
      var resp = '';
      try { resp = window.CalcsAndroid.saveFile(name, mime, b64(bytes)); } catch (e) { resp = 'ERR ' + e.message; }
      if (/^OK/.test(resp)) {
        sheet([h('h4', { text: 'Saved to Downloads/Calcs' }), h('p', { text: name }), h('div', { class: 'opts' }, [
          h('button', { class: 'opt', onclick: function () { closeSheet(); window.CalcsAndroid.openLast(); } }, [h('b', { text: 'Open' }), h('span', { text: 'View the file now' })]),
          h('button', { class: 'opt', onclick: function () { closeSheet(); window.CalcsAndroid.shareLast(); } }, [h('b', { text: 'Share' }), h('span', { text: 'WhatsApp, email, Drive…' })])
        ]), h('button', { class: 'btn cancel', onclick: closeSheet, text: 'Done' })]);
      } else toast('Save failed: ' + resp);
      return;
    }
    // Browser fallback (testing on a computer)
    var blob = new Blob([bytes], { type: mime });
    var a = h('a', { href: URL.createObjectURL(blob), download: name });
    document.body.appendChild(a); a.click(); a.remove();
    window.__lastExport = { name: name, b64: b64(bytes) };
    toast('Downloaded ' + name);
  }

  route();
})();
