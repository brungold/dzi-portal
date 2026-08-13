(() => {
  'use strict';
  const FILE = 'red-pisma-sprawy.csv', REQUIRED = ['kodjo', 'ko', 'kodrwe', 'tresc', 'znak_sprawy', 'sprawa'];
  const state = { all: [], rows: [], joToKo: new Map(), page: 1, pageSize: 50 };
  const $ = id => document.getElementById(id), fmt = n => new Intl.NumberFormat('pl-PL').format(n), trim = v => String(v ?? '').trim();
  const reg = v => { const p = trim(v).slice(0, 3).toUpperCase(); return ['RWE', 'RWY', 'RDW'].includes(p) ? p : 'INNE' };
  const hasCase = v => !['', '0', 'null', 'nie', 'false'].includes(trim(v).toLowerCase());
  const esc = v => String(v).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  function parseCsv(text) {
    const delimiter = (text.split(/\r?\n/)[0] || '').includes(';') ? ';' : ',';
    const records = []; let row = [], field = '', quoted = false;
    for (let i = 0; i < text.length; i++) {
      const c = text[i], next = text[i + 1];
      if (c === '"' && quoted && next === '"') { field += '"'; i++; }
      else if (c === '"') quoted = !quoted;
      else if (c === delimiter && !quoted) { row.push(field); field = ''; }
      else if ((c === '\r' || c === '\n') && !quoted) { if (c === '\r' && next === '\n') i++; row.push(field); field = ''; if (row.some(v => v !== '')) records.push(row); row = []; }
      else field += c;
    }
    row.push(field); if (row.some(v => v !== '')) records.push(row);
    if (records.length < 2) throw new Error('Plik CSV nie zawiera rekordów danych.');
    const headers = records.shift().map(v => trim(v).replace(/^\ufeff/, '').toLowerCase());
    const missing = REQUIRED.filter(v => !headers.includes(v)); if (missing.length) throw new Error('Brak wymaganych kolumn: ' + missing.join(', '));
    const ix = Object.fromEntries(REQUIRED.map(v => [v, headers.indexOf(v)]));
    return records.map(r => { const v = k => trim(r[ix[k]]), jo = v('kodjo'); return jo ? { jo, ko: v('ko'), kod: v('kodrwe'), text: v('tresc'), ps: v('znak_sprawy'), raw: v('sprawa'), case: hasCase(v('sprawa')) ? 1 : 0, reg: reg(v('kodrwe')) } : null; }).filter(Boolean);
  }

  function setStatus(kind, title, meta) { const box = $('load-status'); box.className = 'load-status ' + kind; $('status-title').textContent = title; $('status-meta').textContent = meta; }
  function buildFilters() {
    state.joToKo = new Map(); state.all.forEach(r => { if (!state.joToKo.has(r.jo)) state.joToKo.set(r.jo, new Set()); if (r.ko) state.joToKo.get(r.jo).add(r.ko); });
    const jo = $('filter-jo'); jo.innerHTML = '<option value="">Wszystkie</option>'; [...state.joToKo.keys()].sort((a, b) => a.localeCompare(b, 'pl')).forEach(v => jo.add(new Option(v, v)));
    if (state.joToKo.size === 1) { jo.value = [...state.joToKo.keys()][0]; buildKo(jo.value); } else buildKo('');
  }

  function buildKo(jo) { const select = $('filter-ko'); select.innerHTML = '<option value="">Wszystkie</option>'; select.disabled = !jo; if (jo) [...(state.joToKo.get(jo) || [])].sort((a, b) => a.localeCompare(b, 'pl')).forEach(v => select.add(new Option(v, v))); }
  function applyFilters() { const jo = $('filter-jo').value, ko = $('filter-ko').value, r = $('filter-reg').value, c = $('filter-case').value, q = trim($('filter-search').value).toLowerCase(); state.rows = state.all.filter(x => (!jo || x.jo === jo) && (!ko || x.ko === ko) && (!r || x.reg === r) && (c === '' || x.case === Number(c)) && (!q || x.text.toLowerCase().includes(q) || x.ps.toLowerCase().includes(q))); state.page = 1; render(); }
  function stats(register) { const rows = state.rows.filter(x => x.reg === register), yes = rows.filter(x => x.case).length; return { total: rows.length, yes, no: rows.length - yes }; }
  function renderDonut(register) { const x = stats(register), pct = x.total ? x.yes / x.total * 100 : 0, id = register.toLowerCase(); $(id + '-total').textContent = fmt(x.total); $(id + '-yes').textContent = fmt(x.yes); $(id + '-no').textContent = fmt(x.no); $(id + '-donut').style.setProperty('--pct', pct.toFixed(2)); $(id + '-pct').textContent = pct.toFixed(1) + ' %'; }
  function renderKpis() { const total = state.rows.length, yes = state.rows.filter(x => x.case).length, no = total - yes; $('kpi-total').textContent = fmt(total); $('kpi-with').textContent = fmt(yes); $('kpi-without').textContent = fmt(no); $('kpi-with-pct').textContent = total ? (yes / total * 100).toFixed(1) + ' % wszystkich pism' : '0%'; $('kpi-without-pct').textContent = total ? (no / total * 100).toFixed(1) + ' % wszystkich pism' : '0%'; $('kpi-org').textContent = new Set(state.rows.map(x => x.jo)).size + ' / ' + new Set(state.rows.map(x => x.ko).filter(Boolean)).size; $('kpi-source').textContent = fmt(state.all.length) + ' rekordów w źródle'; }
  function renderBars() { const by = $('filter-jo').value ? 'ko' : 'jo', counts = new Map(); state.rows.forEach(x => counts.set(x[by] || '(brak)', (counts.get(x[by] || '(brak)') || 0) + 1)); const rows = [...counts].sort((a, b) => b[1] - a[1]).slice(0, 20), max = Math.max(...rows.map(x => x[1]), 1); $('bar-title').textContent = $('filter-jo').value ? 'Liczba pism według KO w JO: ' + $('filter-jo').value : 'Liczba pism według JO'; $('bars').innerHTML = rows.map(([label, value]) => '<div class="bar"><span title="' + esc(label) + '">' + esc(label) + '</span><span class="track"><span class="fill" style="--w: ' + (value / max * 100) + '%"></span></span><span class="value">' + fmt(value) + '</span></div>').join('') || '<p>Brak danych.</p>'; }

  function pageCount() { return Math.max(1, Math.ceil(state.rows.length / state.pageSize)); }
  function renderTable() { const pages = pageCount(); if (state.page > pages) state.page = pages; const start = (state.page - 1) * state.pageSize, end = Math.min(start + state.pageSize, state.rows.length), pageRows = state.rows.slice(start, end); $('tbody').innerHTML = pageRows.map(x => '<tr><td>' + esc(x.jo) + '</td><td>' + esc(x.ko) + '</td><td>' + esc(x.kod) + '</td><td>' + esc(x.text) + '</td><td><span class="pill ' + (x.case ? 'yes' : 'no') + '">' + (x.case ? 'Ze sprawą' : 'Bez sprawy') + '</span></td><td>' + esc(x.ps) + '</td></tr>').join(''); $('table-info').textContent = fmt(state.rows.length) + ' rekordów po filtracji'; $('page-info').textContent = (state.rows.length ? (start + 1) : '0') + '-' + fmt(end) + ' z ' + fmt(state.rows.length); $('first').disabled = $('prev').disabled = state.page <= 1; $('next').disabled = $('last').disabled = state.page >= pages; }
  function render() { renderKpis(); ['RWE', 'RWY', 'RDW'].forEach(renderDonut); renderBars(); renderTable(); }
  function exportCsv() { const quote = v => '"' + String(v ?? '').replace(/"/g, '""') + '"', lines = ['kodjo;ko;kodrwe;tresc;znak_sprawy;sprawa', ...state.rows.map(x => [x.jo, x.ko, x.kod, x.text, x.ps, x.raw].map(quote).join(';'))], blob = new Blob(['\ufeff' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8;' }), a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = 'Red_pisma_sprawy_' + new Date().toISOString().slice(0, 10) + '.csv'; a.click(); URL.revokeObjectURL(a.href); }
  async function loadData() { setStatus('loading', 'Wczytywanie danych...', FILE); try { const response = await fetch(FILE, { cache: 'no-store' }); if (!response.ok) throw new Error('HTTP ' + response.status); state.all = parseCsv(await response.text()); state.rows = [...state.all]; buildFilters(); if (state.joToKo.size === 1) applyFilters(); else render(); setStatus('ok', 'Dane aktualne', fmt(state.all.length) + ' rekordów'); } catch (error) { setStatus('error', 'Błąd źródła', FILE); $('message').className = 'message error'; $('message').textContent = 'Nie udało się wczytać ' + FILE + ': ' + error.message + '. Sprawdź, czy plik ' + FILE + ' znajduje się w katalogu modułu na serwerze.'; } }

  async function loadIdentity() {
    try {
      const response = await fetch('/api/whoami', { cache: 'no-store' });
      if (!response.ok) return;
      const data = await response.json();
      const login = trim(data.user ?? data.login ?? data.username ?? '');
      const dept = trim(data.department ?? data.dept ?? '');
      if (login) { const el = document.querySelector('.user-name'); if (el) el.textContent = login; }
      if (dept) { const el = document.querySelector('.user-meta'); if (el) el.textContent = dept.toUpperCase(); }
    } catch (error) { /* brak whoami = zostaje tekst domyślny; moduł działa dalej */ }
  }

  $('filter-jo').onchange = e => { buildKo(e.target.value); applyFilters(); }; $('filter-ko').onchange = () => applyFilters(); $('filter-reg').onchange = () => applyFilters(); $('filter-case').onchange = () => applyFilters(); $('filter-search').oninput = () => applyFilters(); $('filter-search').onkeydown = e => { if (e.key === 'Enter') applyFilters(); }; $('page-size').onchange = e => { state.pageSize = Number(e.target.value); state.page = 1; renderTable(); }; $('first').onclick = () => { state.page = 1; renderTable(); }; $('prev').onclick = () => { if (state.page > 1) { state.page--; renderTable(); } }; $('next').onclick = () => { if (state.page < pageCount()) { state.page++; renderTable(); } }; $('last').onclick = () => { state.page = pageCount(); renderTable(); }; $('export').onclick = exportCsv; loadData(); loadIdentity();
})();
