/* Calcs – tool definitions: inputs, defaults, result layout.
   Each tool = { id, title, sub, defaults(), blank(), compute(s), blocks: [...] }
   Block types:
     section : { t, title, fields:[{k,label,unit,hint,kind,options,onChange}], results:(s,r)=>rows, action }
     grid    : { t, title, k, cols:[...], outs:[...], fixed, count, add, totals, skipEmpty }
     note    : { t, text }
     sketch  : { t } (fire branch only)
   Result row = [label, value, unit, decimals, key?]                           */
(function (root) {
  'use strict';
  var E = root.Engine;
  function range(n, f) { var a = []; for (var i = 0; i < n; i++) a.push(f(i)); return a; }
  function clone(o) { return JSON.parse(JSON.stringify(o)); }

  var YESNO = ['Yes', 'No'];
  var HAZ = E.HAZARDS.map(function (h) { return h[0]; });
  var DRAIN_SEL = [['', '—'], [1.25, '1¼"'], [1.5, '1½"'], [2, '2"'], [2.5, '2½"'], [3, '3"'], [4, '4"'], [5, '5"'], [6, '6"'], [8, '8"']];
  var LEADER_SEL = [['', '—'], [2, '2"'], [2.5, '2½"'], [3, '3"'], [4, '4"'], [5, '5"'], [6, '6"'], [8, '8"']];
  var SCH = E.SCH40.map(function (p) { return [p[1], p[0] + ' (' + p[1] + ')']; });
  function inch(v) {
    if (typeof v !== 'number') return v;
    var m = { 1.25: '1¼"', 1.5: '1½"', 2.5: '2½"' };
    return m[v] || (v + '"');
  }

  /* =============================== WATER SUPPLY =============================== */
  var wsBuilding = {
    id: 'ws-building', title: 'Building demand & pump', sub: 'Tank, WSFU → peak flow, main pipe, friction, pump',
    defaults: function () {
      var fix = range(E.WSFU.length, function () { return { unit: 0, extra: 0 }; });
      fix[8].unit = 1; fix[11].unit = 2; fix[15].unit = 2; fix[19].unit = 1; fix[23].unit = 2;
      return { units: 20, persons: 5, lpd: 200, days: 1.5, fix: fix, vTarget: 1.5, id: 66, vMax: 2, C: 150,
        len: 60, fpct: 30, feq: '', stat: 15, resBar: 1.5, safety: 10, eff: 60 };
    },
    blank: function () { var d = this.defaults(); d.fix = range(E.WSFU.length, function () { return { unit: 0, extra: 0 }; }); return d; },
    compute: E.wsBuilding,
    tiles: function (s, r) {
      return [['Storage tank', r.tank, 'm³', 1], ['Total WSFU', r.totWsfu, 'WSFU', 1], ['Peak flow', r.ls, 'L/s', 2],
        ['Pump duty', r.m3h.toFixed(1) + ' m³/h @ ' + r.head.toFixed(1) + ' m', ''], ['Shaft power', r.shaft, 'kW', 2]];
    },
    blocks: [
      { t: 'section', title: '1 · Building data & storage tank', fields: [
        { k: 'units', label: 'Number of identical units (apartments)', unit: 'no.' },
        { k: 'persons', label: 'Persons per unit', unit: 'no.' },
        { k: 'lpd', label: 'Daily consumption per person', unit: 'L/p/day', hint: 'Authority / project requirement' },
        { k: 'days', label: 'Storage duration', unit: 'days' }],
        results: function (s, r) { return [['Total persons', r.persons, 'no.', 0], ['Daily demand', r.daily, 'm³/day', 2], ['Required storage volume', r.tank, 'm³', 2, 1]]; } },
      { t: 'grid', title: '2 · Fixtures & WSFU (IPC E103.3)', k: 'fix', skipEmpty: true,
        help: 'Quantity per unit is multiplied by the number of units. Extra = fixtures outside the units (lobby, gym…). Bathroom group = WC + lavatory + bath/shower: use it OR the single fixtures, never both.',
        fixed: E.WSFU.map(function (f) { return [f[0], f[1], f[2], f[5]]; }),
        fixedCols: [{ label: 'Fixture' }, { label: 'Occupancy' }, { label: 'Control' }, { label: 'WSFU each', dec: 2 }],
        cols: [{ k: 'unit', label: 'Qty / unit', kind: 'num' }, { k: 'extra', label: 'Extra qty', kind: 'num' }],
        outs: [{ label: 'Total qty', get: function (r, i) { return r.rows[i].qty; }, dec: 0 },
          { label: 'Total WSFU', get: function (r, i) { return r.rows[i].wsfu; }, dec: 1 },
          { label: 'Cold', get: function (r, i) { return r.rows[i].cold; }, dec: 1 },
          { label: 'Hot', get: function (r, i) { return r.rows[i].hot; }, dec: 1 }],
        totals: function (r) { return ['TOTAL', '', '', '', '', '', r.totQty, r.totWsfu, r.totCold, r.totHot]; },
        results: function (s, r) {
          return [['Total WSFU (building main)', r.totWsfu, 'WSFU', 1], ['Hunter curve used (auto)', r.curve, ''],
            ['Peak flow (Hunter, interpolated)', isNaN(r.gpm) ? 'Above table' : r.gpm, 'gpm', 2], ['Peak flow Q', r.ls, 'L/s', 3, 1]];
        } },
      { t: 'section', title: '3 · Main pipe sizing', fields: [
        { k: 'vTarget', label: 'Target design velocity', unit: 'm/s' },
        { k: 'id', label: 'Selected pipe INTERNAL diameter', unit: 'mm', hint: 'Real ID from the catalogue (≥ required)' },
        { k: 'vMax', label: 'Maximum allowable velocity', unit: 'm/s' },
        { k: 'C', label: 'Hazen-Williams C', unit: '-', hint: '150 PPR / PVC · 130–140 copper' }],
        results: function (s, r) { return [['Required internal diameter', r.reqID, 'mm', 1], ['Actual velocity', r.v, 'm/s', 2], ['Velocity check', r.vOK ? 'OK' : 'TOO HIGH – increase pipe size', '', 0, 1]]; } },
      { t: 'section', title: '4 · Friction loss on the critical path', fields: [
        { k: 'len', label: 'Straight length, pump → most remote fixture', unit: 'm' },
        { k: 'fpct', label: 'Fittings allowance', unit: '% of L', hint: 'Rule of thumb 20–50 %' },
        { k: 'feq', label: 'OR fittings equivalent length (catalogue)', unit: 'm', hint: 'If filled, overrides the %' }],
        results: function (s, r) { return [['Equivalent length Le', r.Le, 'm', 1], ['Friction per m (at peak flow)', r.j, 'm/m', 4], ['Total friction loss', r.hf, 'm', 2, 1]]; } },
      { t: 'section', title: '5 · Pump selection', fields: [
        { k: 'stat', label: 'Static height, pump → highest fixture', unit: 'm' },
        { k: 'resBar', label: 'Residual pressure at remote fixture', unit: 'bar' },
        { k: 'safety', label: 'Safety margin', unit: '%' },
        { k: 'eff', label: 'Pump efficiency (assumed)', unit: '%' }],
        results: function (s, r) {
          return [['Residual pressure as head', r.resM, 'm', 2], ['Friction loss (section 4)', r.hf, 'm', 2], ['Subtotal head', r.sub, 'm', 2],
            ['TOTAL PUMP HEAD', r.head, 'm', 2, 1], ['Pump flow', r.m3h, 'm³/h', 2, 1], ['Hydraulic power', r.hyd, 'kW', 2], ['Pump shaft power', r.shaft, 'kW', 2, 1]];
        } },
      { t: 'note', text: 'If any flush-valve WC or urinal is present, the flush-valve Hunter curve is used for the whole building. Low floors: check static pressure; PRVs or zoning may be needed. WSFU and Hunter values per IPC Appendix E – verify against your code edition and authority.' }
    ]
  };

  var wsSegments = {
    id: 'ws-segments', title: 'Pipe sizing & friction', sub: 'Segment by segment along the critical path',
    defaults: function () {
      return { vMax: 2, C: 150, seg: [['Main (pump → tee)', 61, 61.4, 10], ['Riser A', 57, 61.4, 3.5], ['Riser B', 48, 51.4, 3.5], ['Riser C', 38, 51.4, 3.5],
        ['Riser D', 26.3, 40.8, 3.5], ['Floor 4 branch', 26.3, 40.8, 12], ['Apartment branch', 16.5, 32.6, 9]].map(function (a) {
        return { name: a[0], gpm: a[1], vt: 1.5, id: a[2], len: a[3], fpct: 30 }; }) };
    },
    blank: function () { return { vMax: 2, C: 150, seg: [{ name: 'Segment 1', gpm: '', vt: 1.5, id: '', len: '', fpct: 30 }] }; },
    compute: E.wsSegments,
    tiles: function (s, r) {
      var bad = r.rows.filter(function (o) { return o.check && o.check !== 'OK'; }).length;
      return [['Segments', (s.seg || []).length, '', 0], ['Total friction', r.total, 'm', 2], ['Checks', bad ? bad + ' to fix' : 'All OK', '']];
    },
    blocks: [
      { t: 'section', title: 'Design limits', fields: [{ k: 'vMax', label: 'Maximum velocity', unit: 'm/s' }, { k: 'C', label: 'Hazen-Williams C', unit: '-' }] },
      { t: 'grid', title: 'Segments (pump → most remote fixture)', k: 'seg', add: { name: 'New segment', gpm: '', vt: 1.5, id: '', len: '', fpct: 30 },
        help: 'Enter only the segments of ONE path to get its total loss. Peak flow per segment from the WSFU of what it serves. ID = real internal diameter from the catalogue.',
        cols: [{ k: 'name', label: 'Segment', kind: 'text', w: 9 }, { k: 'gpm', label: 'Peak flow (gpm)', kind: 'num' }, { k: 'vt', label: 'Target v (m/s)', kind: 'num' },
          { k: 'id', label: 'Selected ID (mm)', kind: 'num' }, { k: 'len', label: 'Length (m)', kind: 'num' }, { k: 'fpct', label: 'Fittings %', kind: 'num' }],
        outs: [{ label: 'Flow (L/s)', get: function (r, i) { return r.rows[i].ls; }, dec: 2 },
          { label: 'Required ID (mm)', get: function (r, i) { return r.rows[i].req; }, dec: 1 },
          { label: 'Velocity (m/s)', get: function (r, i) { return r.rows[i].v; }, dec: 2 },
          { label: 'Check', get: function (r, i) { return r.rows[i].check; } },
          { label: 'Le (m)', get: function (r, i) { return r.rows[i].Le; }, dec: 1 },
          { label: 'Loss (m)', get: function (r, i) { return r.rows[i].loss; }, dec: 3, key: 1 }],
        results: function (s, r) { return [['TOTAL friction along the path', r.total, 'm', 2, 1]]; } },
      { t: 'note', text: '1 gpm = 0.0630902 L/s. Required ID = √(4Q/πV). hf = 10.67 × Le × Q^1.852 / (C^1.852 × D^4.87), SI units. C = 150 plastic, 130–140 copper, ~100 old steel.' }
    ]
  };

  var wsHot = {
    id: 'ws-hot', title: 'Hot water heater', sub: 'Storage volume & heater power (fixture method)',
    defaults: function () { return { qty: [2, 0, 0, 2, 1, 1, 1, 0, 0].map(function (q) { return { q: q }; }), df: 0.3, sf: 1.25, tc: 25, ts: 60, vol: '', rec: 2, persons: 4, lppd: 50 }; },
    blank: function () { var d = this.defaults(); d.qty = range(9, function () { return { q: 0 }; }); return d; },
    compute: function (s) { return E.wsHot({ qty: (s.qty || []).map(function (x) { return x.q; }), df: s.df, sf: s.sf, tc: s.tc, ts: s.ts, vol: s.vol, rec: s.rec, persons: s.persons, lppd: s.lppd }); },
    tiles: function (s, r) { return [['Required storage', r.storage, 'L', 0], ['Recovery (PMD)', r.pmd, 'L/h', 0], ['Heater power', r.pTank, 'kW', 2]]; },
    blocks: [
      { t: 'grid', title: '1 · Fixtures served by this heater', k: 'qty',
        help: 'One heater → enter only the fixtures it serves. Values: ASHRAE / ASPE hot water demand per fixture (apartment house, 60 °C).',
        fixed: E.HOT_FIXTURES.map(function (f) { return [f[0], f[1]]; }),
        fixedCols: [{ label: 'Fixture' }, { label: 'Demand (US gal/h)', dec: 0 }],
        cols: [{ k: 'q', label: 'Quantity', kind: 'num' }],
        outs: [{ label: 'L/h each', get: function (r, i) { return r.rows[i].lh; }, dec: 1 }, { label: 'Total (L/h)', get: function (r, i) { return r.rows[i].total; }, dec: 1 }],
        totals: function (r) { return ['Possible maximum demand', '', '', '', r.possible]; } },
      { t: 'section', title: '2 · Demand & storage', fields: [
        { k: 'df', label: 'Demand factor', unit: '-', hint: 'Apartment house 0.30' },
        { k: 'sf', label: 'Storage capacity factor', unit: '-', hint: 'Apartment house 1.25' }],
        results: function (s, r) { return [['Possible maximum demand', r.possible, 'L/h', 1], ['Probable maximum demand (PMD) = recovery', r.pmd, 'L/h', 1], ['Required storage volume', r.storage, 'L', 1, 1]]; } },
      { t: 'section', title: '3 · Heater power', fields: [
        { k: 'tc', label: 'Cold water inlet temperature', unit: '°C' },
        { k: 'ts', label: 'Storage temperature', unit: '°C', hint: '≥ 60 °C against Legionella' },
        { k: 'vol', label: 'Selected heater volume (blank = required)', unit: 'L' },
        { k: 'rec', label: 'Recovery time (heat full tank)', unit: 'h' }],
        results: function (s, r) { return [['ΔT', r.dT, '°C', 1], ['Volume used', r.vol, 'L', 1], ['Power to heat the tank in the recovery time', r.pTank, 'kW', 2, 1], ['Power to keep up with PMD continuously', r.pCont, 'kW', 2]]; } },
      { t: 'section', title: '4 · Quick check: daily use', fields: [
        { k: 'persons', label: 'Persons served', unit: 'no.' }, { k: 'lppd', label: 'Hot water use per person (60 °C)', unit: 'L/p/day' }],
        results: function (s, r) { return [['Daily hot water use', r.daily, 'L/day', 0], ['Storage as % of daily use', r.pct * 100, '%', 0]]; } },
      { t: 'note', text: 'P = V × 4.186 × ΔT / (3600 × t). Store at ≥ 60 °C and deliver through a thermostatic mixing valve where required. Verify factors against your reference edition and the project specification.' }
    ]
  };

  /* ================================ DRAINAGE ================================= */
  var drFix = {
    id: 'dr-fixtures', title: 'Fixtures & DFU', sub: 'Soil / waste DFU for the building and per unit',
    defaults: function () {
      var f = range(E.DFU.length, function () { return { unit: 0, extra: 0 }; });
      f[0].unit = 2; f[2].extra = 2; f[8] = { unit: 2, extra: 2 }; f[9] = { unit: 2, extra: 2 }; f[12].unit = 1; f[13].unit = 1; f[14].unit = 1;
      return { units: 12, fix: f };
    },
    blank: function () { return { units: 1, fix: range(E.DFU.length, function () { return { unit: 0, extra: 0 }; }) }; },
    compute: E.drFixtures,
    tiles: function (s, r) { return [['Total DFU', r.total, 'DFU', 1], ['Soil DFU', r.soil, 'DFU', 1], ['Waste DFU', r.waste, 'DFU', 1], ['Per unit soil / waste', r.unitSoil + ' / ' + r.unitWaste, 'DFU']]; },
    blocks: [
      { t: 'section', title: 'Building', fields: [{ k: 'units', label: 'Number of identical units (apartments)', unit: 'no.' }] },
      { t: 'grid', title: 'Fixtures & DFU (IPC 709.1)', k: 'fix', skipEmpty: true,
        help: 'Two-pipe system: WC & urinals → soil, the rest → waste. Bathroom groups are valid only for a combined (one-pipe) system.',
        fixed: E.DFU.map(function (f) { return [f[0], f[1], f[2], f[3]]; }),
        fixedCols: [{ label: 'Fixture' }, { label: 'Occupancy' }, { label: 'DFU each', dec: 1 }, { label: 'System' }],
        cols: [{ k: 'unit', label: 'Qty / unit', kind: 'num' }, { k: 'extra', label: 'Extra qty', kind: 'num' }],
        outs: [{ label: 'Total qty', get: function (r, i) { return r.rows[i].qty; }, dec: 0 }, { label: 'Total DFU', get: function (r, i) { return r.rows[i].dfu; }, dec: 1 }],
        totals: function (r) { return ['TOTAL', '', '', '', '', '', '', r.total]; },
        results: function (s, r) {
          var a = [['Total DFU (combined / building drain)', r.total, 'DFU', 1, 1], ['Soil DFU (building)', r.soil, 'DFU', 1], ['Waste DFU (building)', r.waste, 'DFU', 1],
            ['Soil DFU per unit', r.unitSoil, 'DFU', 1], ['Waste DFU per unit', r.unitWaste, 'DFU', 1]];
          if (r.combinedWarning) a.push(['Warning', 'Bathroom groups entered: combined (one-pipe) system only', '']);
          return a;
        } }
    ]
  };

  var drPipes = {
    id: 'dr-pipes', title: 'Pipe sizing & vents', sub: 'Branches, stacks, building drain (IPC 710.1)',
    defaults: function () {
      return { seg: [
        { name: 'Apartment soil branch (WC)', type: 'Horizontal branch', dfu: 6, intervals: '', atInterval: '', slope: '', wc: 'Yes', selected: '' },
        { name: 'Apartment waste branch', type: 'Horizontal branch', dfu: 12, intervals: '', atInterval: '', slope: '', wc: 'No', selected: '' },
        { name: 'Soil stack (4 floors)', type: 'Stack', dfu: 24, intervals: 4, atInterval: 6, slope: '', wc: 'Yes', selected: '' },
        { name: 'Waste stack (4 floors)', type: 'Stack', dfu: 48, intervals: 4, atInterval: 12, slope: '', wc: 'No', selected: '' },
        { name: 'Building drain @ 2%', type: 'Building drain', dfu: 230, intervals: '', atInterval: '', slope: 2, wc: 'Yes', selected: '' },
        { name: 'Building drain @ 4%', type: 'Building drain', dfu: 230, intervals: '', atInterval: '', slope: 4, wc: 'Yes', selected: '' }] };
    },
    blank: function () { return { seg: [{ name: 'Segment 1', type: 'Horizontal branch', dfu: '', intervals: '', atInterval: '', slope: '', wc: 'No', selected: '' }] }; },
    compute: E.drPipes,
    tiles: function (s, r) {
      var bad = r.rows.filter(function (o) { return o.check === 'TOO SMALL' || o.required === 'Above table'; }).length;
      return [['Segments', (s.seg || []).length, '', 0], ['Checks', bad ? bad + ' to fix' : 'All OK', '']];
    },
    blocks: [
      { t: 'grid', title: 'Segments', k: 'seg', add: { name: 'New segment', type: 'Horizontal branch', dfu: '', intervals: '', atInterval: '', slope: '', wc: 'No', selected: '' },
        help: 'Stacks: branch intervals ≈ floors, and the max DFU entering at one interval (needed above 3 intervals). Building drains: slope 1, 2 or 4 %. Take DFU from the Fixtures & DFU tab.',
        cols: [{ k: 'name', label: 'Segment', kind: 'text', w: 9 },
          { k: 'type', label: 'Type', kind: 'select', options: ['Horizontal branch', 'Stack', 'Building drain'] },
          { k: 'dfu', label: 'Total DFU', kind: 'num' }, { k: 'intervals', label: 'Branch intervals', kind: 'num' },
          { k: 'atInterval', label: 'Max DFU at one interval', kind: 'num' },
          { k: 'slope', label: 'Slope %', kind: 'select', options: [['', '—'], [1, '1'], [2, '2'], [4, '4']] },
          { k: 'wc', label: 'Serves a WC?', kind: 'select', options: YESNO },
          { k: 'selected', label: 'Your size', kind: 'select', options: DRAIN_SEL }],
        outs: [{ label: 'By table (in)', get: function (r, i) { return inch(r.rows[i].table); } },
          { label: 'Required (in)', get: function (r, i) { return inch(r.rows[i].required); }, key: 1 },
          { label: '≈ DN (mm)', get: function (r, i) { return r.rows[i].dn; }, dec: 0 },
          { label: 'Check', get: function (r, i) { return r.rows[i].check; } },
          { label: 'Min vent (in)', get: function (r, i) { return inch(r.rows[i].vent); } }] },
      { t: 'note', text: 'WC drains never below 3". Pipes never reduce in the direction of flow. Stacks over 3 branch intervals: both the total AND the DFU at one interval must fit. Vent ≥ ½ of the drain it serves, min 1¼" (IPC 906.2). Many regional specs set a 4" minimum soil stack – record the reason if you go larger.' }
    ]
  };

  /* ================================ RAINWATER ================================ */
  var rnLeaders = {
    id: 'rn-leaders', title: 'Roof areas & leaders', sub: 'Q = C·i·A/3600 → vertical leaders (IPC 1106.2)',
    defaults: function () { return { intensity: 75, minLeader: 3, areas: [{ name: 'Roof', area: 300, c: 1, n: 2, selected: '' }] }; },
    blank: function () { return { intensity: 75, minLeader: 3, areas: [{ name: 'Area 1', area: '', c: 1, n: 1, selected: '' }] }; },
    compute: E.rnLeaders,
    tiles: function (s, r) { return [['Total area', r.area, 'm²', 0], ['Total flow', r.ls, 'L/s', 2], ['Total flow', r.gpm, 'gpm', 1]]; },
    blocks: [
      { t: 'section', title: 'Design data', fields: [
        { k: 'intensity', label: 'Rainfall intensity i', unit: 'mm/h', hint: 'From the authority for the project location' },
        { k: 'minLeader', label: 'Minimum leader size used in practice', unit: 'in', hint: '0 = follow the table only' }] },
      { t: 'grid', title: 'Catchment areas & leaders', k: 'areas', add: { name: 'New area', area: '', c: 1, n: 1, selected: '' },
        help: 'C: roofs / concrete 0.9–1.0, paving 0.7–0.9, gravel 0.4–0.6, landscape 0.1–0.3.',
        cols: [{ k: 'name', label: 'Area', kind: 'text', w: 8 }, { k: 'area', label: 'Area (m²)', kind: 'num' }, { k: 'c', label: 'C', kind: 'num' },
          { k: 'n', label: 'No. of leaders', kind: 'num' }, { k: 'selected', label: 'Your size', kind: 'select', options: LEADER_SEL }],
        outs: [{ label: 'Q (L/s)', get: function (r, i) { return r.rows[i].ls; }, dec: 2 }, { label: 'Q (gpm)', get: function (r, i) { return r.rows[i].gpm; }, dec: 1 },
          { label: 'Q per leader (gpm)', get: function (r, i) { return r.rows[i].per; }, dec: 1 },
          { label: 'By table (in)', get: function (r, i) { return inch(r.rows[i].table); } },
          { label: 'Required (in)', get: function (r, i) { return inch(r.rows[i].required); }, key: 1 },
          { label: '≈ DN (mm)', get: function (r, i) { return r.rows[i].dn; }, dec: 0 },
          { label: 'Check', get: function (r, i) { return r.rows[i].check; } }],
        totals: function (r) { return ['TOTAL', r.area, '', '', '', r.ls, r.gpm]; } },
      { t: 'note', text: 'Q = C × i × A / 3600 → L/s (1 mm of rain on 1 m² = 1 litre). Use more than one drain per roof and provide overflow drains / scuppers where parapets can trap water. Keep rainwater separate from sanitary drainage unless the authority allows otherwise.' }
    ]
  };

  var rnDrains = {
    id: 'rn-drains', title: 'Horizontal storm drains', sub: 'Manning, pipe flowing full',
    defaults: function () { return { seg: [{ name: 'Roof leaders → storm manhole', q: 6.25, slope: 1, n: 0.009 }] }; },
    blank: function () { return { seg: [{ name: 'Segment 1', q: '', slope: 1, n: 0.009 }] }; },
    compute: E.rnDrains,
    tiles: function (s, r) {
      var bad = r.rows.filter(function (o) { return o.check && o.check !== 'OK'; }).length;
      return [['Segments', (s.seg || []).length, '', 0], ['Checks', bad ? bad + ' to fix' : 'All OK', '']];
    },
    blocks: [
      { t: 'grid', title: 'Segments', k: 'seg', add: { name: 'New segment', q: '', slope: 1, n: 0.009 },
        help: 'Flow from the Roof areas tab (L/s). n ≈ 0.009–0.011 smooth plastic, 0.013 concrete.',
        cols: [{ k: 'name', label: 'Segment', kind: 'text', w: 10 }, { k: 'q', label: 'Flow Q (L/s)', kind: 'num' }, { k: 'slope', label: 'Slope (%)', kind: 'num' }, { k: 'n', label: 'Manning n', kind: 'num' }],
        outs: [{ label: 'Required Ø (mm)', get: function (r, i) { return r.rows[i].req; }, dec: 1 },
          { label: 'Selected (mm)', get: function (r, i) { return r.rows[i].nominal; }, dec: 0, key: 1 },
          { label: 'Capacity full (L/s)', get: function (r, i) { return r.rows[i].cap; }, dec: 2 },
          { label: 'Velocity full (m/s)', get: function (r, i) { return r.rows[i].v; }, dec: 2 },
          { label: 'Check', get: function (r, i) { return r.rows[i].check; } }] },
      { t: 'note', text: 'D = [ Q × n × 4^(5/3) / (π × √S) ]^(3/8), Q in m³/s, S as decimal. Selected size = first of 75, 100, 150, 200, 250, 300, 375, 450 mm (used as internal Ø – replace with catalogue IDs). Check against the code table for horizontal storm drains (e.g. IPC 1106.3).' }
    ]
  };

  /* ============================== FIRE FIGHTING ============================== */
  var fireDemand = {
    id: 'fire-demand', title: 'Fire water demand', sub: 'Density/area, hose, one sprinkler, UAE risers → pump & tank',
    defaults: function () { return { hazard: 'Light', duration: 30, coverage: 12, K: 80.6, pMin: 0.5, risers: 1, sprinklered: 'Yes', lvBar: 6.9, lvElev: 15, lvFric: 5, spElev: 17, spFric: 8 }; },
    compute: E.fireDemand,
    tiles: function (s, r) {
      return [['Sprinkler flow', r.sprGpm, 'gpm', 0], ['Total (spr + hose)', r.totLs, 'L/s', 2], ['Fire pump', r.pumpGpm.toFixed(0) + ' gpm @ ' + r.pumpHead.toFixed(1) + ' m', ''],
        ['Fire tank (UAE)', r.tank, 'm³', 1]];
    },
    blocks: [
      { t: 'section', title: '1 · Hazard classification', fields: [
        { k: 'hazard', label: 'Hazard classification', kind: 'select', options: HAZ,
          onChange: function (s) { var h = E.hazard(s.hazard); if (h) s.duration = h[5]; } }],
        results: function (s, r) {
          return [['Examples', r.hz[1], ''], ['Design density', r.densUS, 'gpm/ft²', 2], ['Design density', r.dens, 'mm/min', 2],
            ['Design area', r.areaUS, 'ft²', 0], ['Design area', r.area, 'm²', 1]];
        } },
      { t: 'section', title: '2 · Sprinkler demand, hose & total', fields: [],
        results: function (s, r) {
          return [['Sprinkler flow = density × area', r.sprLmin, 'L/min', 1], ['Sprinkler flow', r.sprGpm, 'gpm', 1], ['Sprinkler flow', r.sprLs, 'L/s', 2],
            ['Hose stream allowance (added)', r.hoseGpm, 'gpm', 0], ['Hose stream allowance', r.hoseLs, 'L/s', 2],
            ['TOTAL fire water flow', r.totGpm, 'gpm', 1, 1], ['TOTAL fire water flow', r.totLs, 'L/s', 2, 1]];
        } },
      { t: 'section', title: '3 · Fire water storage (NFPA only)', fields: [
        { k: 'duration', label: 'Selected duration (NFPA table)', unit: 'min', hint: 'UAE tank uses ≥ 60 min of PUMP flow (section 5)' }],
        results: function (s, r) { return [['Duration range (table)', r.durRange, 'min'], ['Duration check', r.durCheck, ''], ['Fire water, sprinklers + hose (NFPA)', r.nfpaTank, 'm³', 1]]; } },
      { t: 'section', title: '4 · One sprinkler: Q = K √P', fields: [
        { k: 'coverage', label: 'Coverage per sprinkler (as designed)', unit: 'm²' },
        { k: 'K', label: 'K-factor', unit: 'L/min/bar^0.5', hint: 'K80 = K5.6' },
        { k: 'pMin', label: 'Minimum operating pressure', unit: 'bar', hint: '≈ 7 psi typical' }],
        results: function (s, r) {
          return [['Max coverage for this hazard', r.maxCov, 'm²', 1], ['Coverage check', r.covCheck, ''], ['Minimum flow per sprinkler', r.qMin, 'L/min', 1],
            ['Required pressure P = (Q/K)²', r.pReq, 'bar', 3], ['Design pressure at sprinkler', r.pDes, 'bar', 2, 1], ['Flow at design pressure', r.qDes, 'L/min', 1],
            ['Sprinklers operating in design area', r.nSpr, 'no.', 0]];
        } },
      { t: 'section', title: '5 · UAE wet riser & governing values', fields: [
        { k: 'risers', label: 'Number of wet risers (Class III)', unit: 'no.' },
        { k: 'sprinklered', label: 'Building fully sprinklered?', kind: 'select', options: YESNO },
        { k: 'lvBar', label: 'Residual pressure at remotest 65 mm landing valve', unit: 'bar', hint: '6.9 or 7 bar depending on clause' },
        { k: 'lvElev', label: 'Elevation pump → remotest landing valve', unit: 'm' },
        { k: 'lvFric', label: 'Friction to remotest landing valve', unit: 'm' },
        { k: 'spElev', label: 'Elevation pump → remotest sprinkler', unit: 'm' },
        { k: 'spFric', label: 'Friction to remotest sprinkler', unit: 'm' }],
        results: function (s, r) {
          return [['Riser demand (500 + 250 / extra riser)', r.riserGpm, 'gpm', 0], ['Riser head needed', r.riserHead, 'm', 1], ['Sprinkler head needed', r.sprHead, 'm', 1],
            ['FIRE PUMP flow (largest)', r.pumpGpm, 'gpm', 0, 1], ['FIRE PUMP flow', r.pumpLs, 'L/s', 2], ['FIRE PUMP head (largest)', r.pumpHead, 'm', 1, 1],
            ['FIRE PUMP pressure', r.pumpBar, 'bar', 2], ['Tank duration (UAE ≥ 60 min of pump flow)', r.tankMin, 'min', 0], ['FIRE WATER TANK', r.tank, 'm³', 1, 1]];
        } },
      { t: 'note', text: 'Hose reel (info): 6.5 gpm at 4.5 bar at the remotest 25 mm reel, max 12 bar, PRV above 7 bar. UAE values from a published copy of the UAE Fire & Life Safety Code Ch. 9 (may be an older edition) – verify with Civil Defence. The final design needs a full hydraulic calculation.' }
    ]
  };

  var fireZones = {
    id: 'fire-zones', title: 'Building zones → pump & tank', sub: 'Several hazards in one building',
    defaults: function () {
      return { risers: 1, sprinklered: 'Yes', lvBar: 6.9, lvElev: 26, lvFric: 10, zones: [
        { zone: 'Car park (basement)', hazard: 'Ordinary Group 1', duration: 60, coverage: 12, K: 80.6, elev: '', fric: '' },
        { zone: 'Retail (ground)', hazard: 'Ordinary Group 2', duration: 60, coverage: 12, K: 80.6, elev: 8, fric: 10 },
        { zone: 'Offices (top floor)', hazard: 'Light', duration: 30, coverage: 12, K: 80.6, elev: 29, fric: 12 }] };
    },
    blank: function () { return { risers: 1, sprinklered: 'Yes', lvBar: 6.9, lvElev: '', lvFric: '', zones: [{ zone: 'Zone 1', hazard: 'Light', duration: 30, coverage: 12, K: 80.6, elev: '', fric: '' }] }; },
    compute: E.fireZones,
    tiles: function (s, r) { return [['Fire pump', r.pumpGpm.toFixed(0) + ' gpm @ ' + r.pumpHead.toFixed(1) + ' m', ''], ['Pump pressure', r.pumpBar, 'bar', 2], ['Fire tank', r.tank, 'm³', 1]]; },
    blocks: [
      { t: 'grid', title: 'Zones', k: 'zones', add: { zone: 'New zone', hazard: 'Light', duration: 30, coverage: 12, K: 80.6, elev: '', fric: '' },
        help: 'One row per zone. Leave elevation / friction blank if unknown – that zone then doesn\'t count for the head.',
        cols: [{ k: 'zone', label: 'Zone', kind: 'text', w: 8 }, { k: 'hazard', label: 'Hazard', kind: 'select', options: HAZ }, { k: 'duration', label: 'Duration (min)', kind: 'num' },
          { k: 'coverage', label: 'Coverage (m²)', kind: 'num' }, { k: 'K', label: 'K', kind: 'num' }, { k: 'elev', label: 'Elevation (m)', kind: 'num' }, { k: 'fric', label: 'Friction (m)', kind: 'num' }],
        outs: [{ label: 'Spr (gpm)', get: function (r, i) { return r.rows[i].spr; }, dec: 0 }, { label: 'Hose (gpm)', get: function (r, i) { return r.rows[i].hose; }, dec: 0 },
          { label: 'Total (gpm)', get: function (r, i) { return r.rows[i].tot; }, dec: 0, key: 1 }, { label: 'Total (L/s)', get: function (r, i) { return r.rows[i].ls; }, dec: 2 },
          { label: 'P last spr (bar)', get: function (r, i) { return r.rows[i].p; }, dec: 2 }, { label: 'Head (m)', get: function (r, i) { return r.rows[i].head; }, dec: 1 },
          { label: 'Water (m³)', get: function (r, i) { return r.rows[i].vol; }, dec: 1 }, { label: 'Duration', get: function (r, i) { return r.rows[i].check; } }] },
      { t: 'section', title: 'Wet riser (UAE)', fields: [
        { k: 'risers', label: 'Number of wet risers (Class III)', unit: 'no.' },
        { k: 'sprinklered', label: 'Building fully sprinklered?', kind: 'select', options: YESNO },
        { k: 'lvBar', label: 'Residual pressure at remotest landing valve', unit: 'bar' },
        { k: 'lvElev', label: 'Elevation pump → remotest landing valve', unit: 'm' },
        { k: 'lvFric', label: 'Friction to remotest landing valve', unit: 'm' }],
        results: function (s, r) {
          return [['Riser demand', r.riserGpm, 'gpm', 0], ['Riser head needed', r.riserHead, 'm', 1], ['FIRE PUMP rated flow', r.pumpGpm, 'gpm', 0, 1],
            ['Fire pump rated flow', r.pumpLs, 'L/s', 2], ['Fire pump rated flow', r.pumpM3h, 'm³/h', 1], ['FIRE PUMP rated head', r.pumpHead, 'm', 1, 1],
            ['Fire pump rated pressure', r.pumpBar, 'bar', 2], ['NFPA 20: at 150 % flow, at least', r.at150, 'm', 1], ['NFPA 20: shut-off head not above', r.shutoff, 'm', 1],
            ['FIRE TANK = max(zones, pump × 60 min)', r.tank, 'm³', 1, 1]];
        } },
      { t: 'note', text: 'Duty point = largest flow AND largest head, even from different zones. Lower zones will see excess pressure → PRVs / zoning. The authority may require extra storage (hose reels, hydrants).' }
    ]
  };

  function branchSeg(d, len) { return { d: d, len: len, fit: 0, dz: 0 }; }
  var fireBranch = {
    id: 'fire-branch', title: 'Branch hydraulic calc', sub: 'Sprinkler by sprinkler, with velocity check & sketch',
    defaults: function () { return { K: 80.6, p1: 0.5, C: 120, n: 4, dDef: 26.6, lenDef: 3.5, density: 4.0746, coverage: 12, vGood: 3, vPractice: 6, vMax: 10,
      seg: range(15, function () { return branchSeg(26.6, 3.5); }) }; },
    compute: E.fireBranch,
    count: function (s) { return Math.max(1, Math.min(15, Math.round(E.num(s.n)) || 1)); },
    tiles: function (s, r) { return [['Total flow', r.Q, 'L/min', 1], ['Total flow', r.Qgpm, 'gpm', 1], ['Pressure at branch inlet', r.Pin, 'bar', 2], ['Highest velocity', r.vMax, 'm/s', 2], ['Hydraulic / estimate', r.ratio * 100, '%', 0]]; },
    blocks: [
      { t: 'section', title: 'Branch data', fields: [
        { k: 'K', label: 'K-factor', unit: 'L/min/bar^0.5' },
        { k: 'p1', label: 'Pressure at the most remote sprinkler', unit: 'bar' },
        { k: 'C', label: 'Hazen-Williams C', unit: '-', hint: '120 black steel (wet)' },
        { k: 'n', label: 'Number of sprinklers on the branch (1–15)', unit: 'no.', rebuild: true },
        { k: 'dDef', label: 'Pipe size for all segments', kind: 'select', options: SCH },
        { k: 'lenDef', label: 'Spacing for all segments', unit: 'm' }],
        action: { label: 'Apply size & spacing to all pipes', run: function (s) { s.seg.forEach(function (g) { g.d = E.num(s.dDef); g.len = E.num(s.lenDef); }); } } },
      { t: 'sketch' },
      { t: 'grid', title: 'Sprinklers (1 = most remote → riser)', k: 'seg', count: true,
        help: 'Each row = a sprinkler and the pipe after it toward the riser. Elevation drop = how much LOWER the next point is (negative if higher).',
        rowLabel: function (i) { return 'S' + (i + 1); },
        cols: [{ k: 'd', label: 'Pipe (Sch 40 ID)', kind: 'select', options: SCH }, { k: 'len', label: 'Length (m)', kind: 'num' },
          { k: 'fit', label: 'Fittings eq. L (m)', kind: 'num' }, { k: 'dz', label: 'Elev. drop (m)', kind: 'num' }],
        outs: [{ label: 'P (bar)', get: function (r, i) { return r.rows[i] && r.rows[i].P; }, dec: 3 }, { label: 'q (L/min)', get: function (r, i) { return r.rows[i] && r.rows[i].q; }, dec: 1 },
          { label: 'Q pipe (L/min)', get: function (r, i) { return r.rows[i] && r.rows[i].Q; }, dec: 1 }, { label: 'p (bar/m)', get: function (r, i) { return r.rows[i] && r.rows[i].p; }, dec: 4 },
          { label: 'Loss (bar)', get: function (r, i) { return r.rows[i] && r.rows[i].loss; }, dec: 3 }, { label: 'P next (bar)', get: function (r, i) { return r.rows[i] && r.rows[i].next; }, dec: 3 },
          { label: 'v (m/s)', get: function (r, i) { return r.rows[i] && r.rows[i].v; }, dec: 2 }, { label: 'Velocity check', get: function (r, i) { return r.rows[i] && r.rows[i].check; } }],
        results: function (s, r) {
          return [['Total flow on the branch', r.Q, 'L/min', 1, 1], ['Total flow', r.Qls, 'L/s', 2], ['Total flow', r.Qgpm, 'gpm', 1],
            ['Pressure needed at branch inlet', r.Pin, 'bar', 3, 1], ['Pressure needed at branch inlet', r.PinM, 'm', 1],
            ['Density × area estimate', r.est, 'L/min', 1], ['Hydraulic flow / estimate', r.ratio * 100, '%', 0], ['Highest velocity', r.vMax, 'm/s', 2, 1]];
        } },
      { t: 'section', title: 'Comparison & velocity limits', fields: [
        { k: 'density', label: 'Design density (for comparison)', unit: 'mm/min', hint: 'Light 4.07 · OH1 6.11 · OH2 8.15' },
        { k: 'coverage', label: 'Coverage per sprinkler', unit: 'm²' },
        { k: 'vGood', label: 'Comfortable velocity (green below)', unit: 'm/s' },
        { k: 'vPractice', label: 'Practice limit for sprinkler pipes', unit: 'm/s', hint: 'NFPA 13 sets none; common practice' },
        { k: 'vMax', label: 'Absolute maximum (EN 12845)', unit: 'm/s' }] },
      { t: 'note', text: 'q = K√P · Q = cumulative flow · p (bar/m) = 6.05×10⁵ × Q^1.85 / (C^1.85 × d^4.87) with Q in L/min and d = INTERNAL Ø in mm · loss = p × (L + Le) · v = Q / A. Pump suction (NFPA 20): ≤ 4.6 m/s at 150 % flow. Project specs may set lower limits – they govern. Fix high velocity by upsizing the pipe nearest the riser first.' }
    ]
  };

  /* ================================ BRANCHES ================================= */
  var BRANCHES = [
    { id: 'water', title: 'Water Supply', short: 'WS', accent: 'water', desc: 'Demand, WSFU & Hunter, pipe sizing, friction, pumps, hot water', tools: [wsBuilding, wsSegments, wsHot] },
    { id: 'drainage', title: 'Drainage', short: 'DR', accent: 'drain', desc: 'DFU, soil & waste, branches, stacks, building drain, vents', tools: [drFix, drPipes] },
    { id: 'rain', title: 'Rainwater', short: 'RW', accent: 'rain', desc: 'Roof flow, leaders, horizontal storm drains (Manning)', tools: [rnLeaders, rnDrains] },
    { id: 'fire', title: 'Fire Fighting', short: 'FF', accent: 'fire', desc: 'Density/area, UAE risers, pump & tank, branch hydraulics', tools: [fireDemand, fireZones, fireBranch] }
  ];

  root.Tools = { BRANCHES: BRANCHES, clone: clone, inch: inch, range: range };
})(typeof window !== 'undefined' ? window : this);
