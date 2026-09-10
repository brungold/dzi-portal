/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 * Autorskie prawa osobiste (prawo do autorstwa) niezbywalne — art. 16 pr. aut.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 *
 * Panel administracyjny, Faza A (ADR-0009): wyłącznie odczyt z /api/admin/**.
 * Zasady modułu: zero bibliotek, zero innerHTML (tylko createElement/textContent),
 * wszystkie tabele stronicowane po 50 wierszy i sortowane po kliknięciu nagłówka.
 */
(() => {
  'use strict';

  const PAGE_SIZE = 50;
  const state = { tiles: [], logins: [], loginSet: new Set() };

  const $ = id => document.getElementById(id);

  /* ---------------------------------------------------------------- helpers */

  function el(tag, attrs, children) {
    const node = document.createElement(tag);
    if (attrs) {
      for (const [key, value] of Object.entries(attrs)) {
        if (key === 'text') node.textContent = value;
        else if (key === 'class') node.className = value;
        else if (value !== null && value !== undefined) node.setAttribute(key, String(value));
      }
    }
    if (children) for (const child of children) if (child) node.appendChild(child);
    return node;
  }

  function clear(node) { while (node.firstChild) node.removeChild(node.firstChild); }

  const dateTime = new Intl.DateTimeFormat('pl-PL', { dateStyle: 'short', timeStyle: 'short' });
  function fmtDate(iso) { return iso ? dateTime.format(new Date(iso)) : '—'; }
  function fmtNum(n) { return (n ?? 0).toLocaleString('pl-PL'); }
  function isoDay(date) { return date.toISOString().slice(0, 10); }

  function showError(message) {
    const box = $('admin-error');
    box.textContent = message;
    box.hidden = !message;
  }

  function setStatus(text, ok) {
    $('admin-status-text').textContent = text;
    const dot = $('admin-status').querySelector('.status-dot');
    dot.className = 'status-dot ' + (ok ? 'status-ok' : 'status-warn');
  }

  async function api(path) {
    let response;
    try {
      response = await fetch(path, { cache: 'no-store', headers: { Accept: 'application/json' } });
    } catch (error) {
      throw new Error('Brak połączenia z API portalu (' + error.message + ').');
    }
    if (response.ok) return response.json();
    const messages = {
      401: 'Portal nie rozpoznał Twojej tożsamości (401). Odśwież stronę; jeśli się powtarza — sprawdź moduł IIS.',
      403: 'Brak uprawnień do panelu (403). Dostęp wymaga READ na kafelku „administracja".',
      404: 'Nie znaleziono (404).',
      400: 'Niepoprawne dane wejściowe (400).',
      429: 'Za dużo żądań z tej stacji — limit 120/min. Odczekaj minutę (429).'
    };
    let detail = '';
    try { const problem = await response.json(); if (problem && problem.detail) detail = ' ' + problem.detail; } catch (ignored) { /* brak treści */ }
    throw new Error((messages[response.status] || ('Błąd serwera (' + response.status + ').')) + detail);
  }

  /* ------------------------------------------------------- generic table */

  /**
   * columns: [{ key, label, render?(value,row) -> Node|string, sortValue?(row), align? }]
   * opts: { pagination: Node|null, onRowClick?(row), csvName? }
   */
  function renderTable(table, columns, rows, opts) {
    const options = opts || {};
    const thead = table.querySelector('thead');
    const tbody = table.querySelector('tbody');
    let sortKey = null, sortDir = 1, page = 1;

    function sorted() {
      if (!sortKey) return rows;
      const column = columns.find(c => c.key === sortKey);
      const valueOf = column.sortValue || (row => row[sortKey]);
      return [...rows].sort((a, b) => {
        const va = valueOf(a), vb = valueOf(b);
        if (va == null && vb == null) return 0;
        if (va == null) return 1;
        if (vb == null) return -1;
        if (typeof va === 'number' && typeof vb === 'number') return (va - vb) * sortDir;
        return String(va).localeCompare(String(vb), 'pl') * sortDir;
      });
    }

    function drawHead() {
      clear(thead);
      const tr = el('tr');
      for (const column of columns) {
        const th = el('th', { text: column.label, scope: 'col', class: column.align === 'right' ? 'num' : '' });
        th.classList.add('sortable');
        if (sortKey === column.key) th.classList.add(sortDir === 1 ? 'asc' : 'desc');
        th.addEventListener('click', () => {
          if (sortKey === column.key) sortDir = -sortDir; else { sortKey = column.key; sortDir = 1; }
          page = 1; draw();
        });
        tr.appendChild(th);
      }
      thead.appendChild(tr);
    }

    function drawBody() {
      clear(tbody);
      const data = sorted();
      const pages = Math.max(1, Math.ceil(data.length / PAGE_SIZE));
      if (page > pages) page = pages;
      const slice = options.pagination ? data.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE) : data;
      if (!slice.length) {
        tbody.appendChild(el('tr', null, [el('td', { text: 'Brak danych', colspan: columns.length, class: 'empty' })]));
      }
      for (const row of slice) {
        const tr = el('tr');
        for (const column of columns) {
          const raw = row[column.key];
          const rendered = column.render ? column.render(raw, row) : (raw == null ? '—' : String(raw));
          const td = el('td', { class: column.align === 'right' ? 'num' : '' });
          if (rendered instanceof Node) td.appendChild(rendered); else td.textContent = rendered;
          tr.appendChild(td);
        }
        if (options.onRowClick) {
          tr.classList.add('clickable');
          tr.tabIndex = 0;
          tr.addEventListener('click', () => options.onRowClick(row));
          tr.addEventListener('keydown', event => { if (event.key === 'Enter') options.onRowClick(row); });
        }
        tbody.appendChild(tr);
      }
      if (options.pagination) drawPagination(data.length, pages);
    }

    function drawPagination(total, pages) {
      const box = options.pagination;
      clear(box);
      box.appendChild(el('span', { text: fmtNum(total) + ' wierszy · strona ' + page + ' z ' + pages }));
      const buttons = el('div');
      const prev = el('button', { text: 'Poprzednia', type: 'button' });
      const next = el('button', { text: 'Następna', type: 'button' });
      prev.disabled = page <= 1; next.disabled = page >= pages;
      prev.addEventListener('click', () => { page -= 1; drawBody(); });
      next.addEventListener('click', () => { page += 1; drawBody(); });
      buttons.appendChild(prev); buttons.appendChild(next);
      box.appendChild(buttons);
    }

    function draw() { drawHead(); drawBody(); }
    draw();
    return { rows: () => sorted() };
  }

  function downloadCsv(name, columns, rows) {
    const escape = value => {
      const text = value == null ? '' : String(value);
      return /[";\n]/.test(text) ? '"' + text.replace(/"/g, '""') + '"' : text;
    };
    const lines = [columns.map(c => escape(c.label)).join(';')];
    for (const row of rows) lines.push(columns.map(c => escape(c.csv ? c.csv(row) : row[c.key])).join(';'));
    const blob = new Blob(['\ufeff' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' });
    const link = el('a', { href: URL.createObjectURL(blob), download: name });
    document.body.appendChild(link); link.click(); link.remove();
  }

  function pill(text, kind) { return el('span', { text, class: 'pill ' + kind }); }
  function kindPill(kind) {
    if (kind === 'ALL') return pill('wszyscy', 'progress');
    if (kind === 'DEPARTMENT') return pill('departament', 'progress');
    return pill('login', 'success');
  }
  function activePill(active) { return active ? pill('aktywny', 'success') : pill('nieaktywny', 'error'); }

  /* ------------------------------------------------------------ sections */

  function activate(section) {
    document.querySelectorAll('.admin-tabs .filter').forEach(button => {
      const active = button.dataset.section === section;
      button.classList.toggle('active', active);
      button.setAttribute('aria-pressed', String(active));
    });
    document.querySelectorAll('.admin-section').forEach(panel => { panel.hidden = panel.dataset.panel !== section; });
    showError('');
  }

  function fillTileSelects() {
    for (const id of ['perm-tile', 'audit-tile', 'gen-tile']) {
      const select = $(id);
      const keepAll = id === 'audit-tile';
      clear(select);
      if (keepAll) select.appendChild(el('option', { text: 'wszystkie', value: '' }));
      for (const tile of state.tiles) {
        select.appendChild(el('option', { text: tile.name + ' (' + tile.code + ')' + (tile.active ? '' : ' — nieaktywny'), value: tile.code }));
      }
    }
  }

  function fillLoginDatalist() {
    const list = $('known-logins');
    clear(list);
    for (const login of state.logins) list.appendChild(el('option', { value: login.login }));
    state.loginSet = new Set(state.logins.map(l => l.login));
  }

  /* 1. Kafelki */
  const tileColumns = [
    { key: 'code', label: 'Kod' },
    { key: 'name', label: 'Nazwa' },
    { key: 'tileType', label: 'Typ' },
    { key: 'active', label: 'Stan', render: v => activePill(v), csv: r => r.active ? 'aktywny' : 'nieaktywny' },
    { key: 'displayOrder', label: 'Kolejność', align: 'right' },
    { key: 'permissionCount', label: 'Wpisów uprawnień', align: 'right' },
    { key: 'visits30', label: 'Wejścia 30 dni', align: 'right' },
    { key: 'visits90', label: 'Wejścia 90 dni', align: 'right' },
    { key: 'lastVisit', label: 'Ostatnie wejście', render: v => fmtDate(v), sortValue: r => r.lastVisit || '' }
  ];

  async function loadTiles() {
    state.tiles = await api('/api/admin/tiles');
    renderTable($('tiles-table'), tileColumns, state.tiles, {
      pagination: $('tiles-pagination'),
      onRowClick: row => { activate('uprawnienia'); $('perm-tile').value = row.code; showTile(row.code); }
    });
    fillTileSelects();
  }

  /* 2. Uprawnienia */
  const permColumns = [
    { key: 'principal', label: 'Komu', render: (v, r) => {
      const wrap = el('span');
      wrap.appendChild(el('span', { text: v, class: 'mono' }));
      if (r.kind === 'LOGIN' && !r.knownInAudit) wrap.appendChild(el('span', { text: ' ⚠ nigdy nie wszedł na portal', class: 'warn-inline', title: 'Login nie występuje w audycie — możliwa literówka' }));
      return wrap;
    } },
    { key: 'kind', label: 'Rodzaj', render: v => kindPill(v) },
    { key: 'level', label: 'Poziom' },
    { key: 'knownInAudit', label: 'Znany portalowi', render: (v, r) => r.kind === 'LOGIN' ? (v ? 'tak' : 'NIE') : 'n/d' }
  ];
  const visitorColumns = [
    { key: 'login', label: 'Login', render: v => el('span', { text: v, class: 'mono' }) },
    { key: 'visits', label: 'Wejścia', align: 'right' },
    { key: 'lastVisit', label: 'Ostatnie wejście', render: v => fmtDate(v), sortValue: r => r.lastVisit || '' }
  ];
  let currentPermissions = [];

  async function showTile(code) {
    if (!code) return;
    $('perm-user-view').hidden = true;
    const detail = await api('/api/admin/tiles/' + encodeURIComponent(code));
    $('perm-tile-title').textContent = 'Reguły dostępu — ' + detail.name + ' (' + detail.code + ')' + (detail.active ? '' : ' — kafelek nieaktywny');
    currentPermissions = detail.permissions;
    renderTable($('perm-table'), permColumns, detail.permissions, {});
    renderTable($('visitors-table'), visitorColumns, detail.visitors, { pagination: $('visitors-pagination') });
    $('perm-tile-view').hidden = false;
  }

  const grantColumns = [
    { key: 'code', label: 'Kod' },
    { key: 'name', label: 'Nazwa' },
    { key: 'level', label: 'Poziom' },
    { key: 'via', label: 'Przez co', render: (v, r) => v === 'login' ? pill('imiennie', 'success') : v === 'wszyscy' ? pill('wszyscy', 'progress') : pill('jeśli w: ' + v, 'progress') }
  ];
  const userVisitColumns = [
    { key: 'name', label: 'Moduł' },
    { key: 'code', label: 'Kod' },
    { key: 'visits', label: 'Wejścia', align: 'right' },
    { key: 'lastVisit', label: 'Ostatnie wejście', render: v => fmtDate(v), sortValue: r => r.lastVisit || '' }
  ];

  async function showUser(login) {
    const value = (login || '').trim().toLowerCase();
    if (!value) { showError('Wpisz login.'); return; }
    $('perm-tile-view').hidden = true;
    const user = await api('/api/admin/users/' + encodeURIComponent(value));
    $('user-login').textContent = user.login;
    $('user-known').textContent = user.knownInAudit ? 'znany portalowi' : 'NIGDY nie wszedł na portal — sprawdź pisownię loginu';
    $('user-known').className = 'trend ' + (user.knownInAudit ? 'positive' : 'negative');
    $('user-first').textContent = fmtDate(user.firstSeen);
    $('user-last').textContent = fmtDate(user.lastSeen);
    const grants = [
      ...user.byLogin.map(g => ({ ...g, via: 'login' })),
      ...user.forAll.map(g => ({ ...g, via: 'wszyscy' })),
      ...user.byDepartment
    ];
    renderTable($('user-grants-table'), grantColumns, grants, {});
    renderTable($('user-visits-table'), userVisitColumns, user.visits, {});
    $('perm-user-view').hidden = false;
  }

  /* 3. Ostatnie wejścia */
  const auditColumns = [
    { key: 'ts', label: 'Kiedy', render: v => fmtDate(v), sortValue: r => r.ts },
    { key: 'username', label: 'Kto', render: v => el('span', { text: v, class: 'mono' }) },
    { key: 'action', label: 'Akcja', render: v => v || '—' },
    { key: 'httpStatus', label: 'HTTP', align: 'right', render: (v, r) => r.httpStatus >= 400 ? pill(String(v), 'error') : String(v) },
    { key: 'objectRef', label: 'Obiekt', render: v => v || '—' },
    { key: 'path', label: 'Ścieżka', render: v => el('span', { text: v, class: 'mono small' }) },
    { key: 'clientIp', label: 'Stacja', render: v => el('span', { text: v, class: 'mono' }) }
  ];
  let auditRows = [];

  async function loadAudit() {
    const params = new URLSearchParams();
    params.set('limit', $('audit-limit').value);
    const login = $('audit-login').value.trim();
    if (login) params.set('login', login);
    const tile = $('audit-tile').value;
    if (tile) params.set('tile', tile);
    const action = $('audit-denied').checked ? 'APP_DENIED' : $('audit-action').value;
    if (action) params.set('action', action);
    auditRows = await api('/api/admin/audit?' + params.toString());
    $('audit-info').textContent = auditRows.length + ' wpisów, od najnowszego' + (login ? ' · osoba: ' + login : '') + (tile ? ' · kafelek: ' + tile : '') + (action ? ' · akcja: ' + action : '');
    renderTable($('audit-table'), auditColumns, auditRows, { pagination: $('audit-pagination') });
  }

  /* 4. Statystyka */
  const statTileColumns = [
    { key: 'name', label: 'Moduł' },
    { key: 'code', label: 'Kod' },
    { key: 'visits', label: 'Wejścia', align: 'right' },
    { key: 'distinctUsers', label: 'Różnych osób', align: 'right' }
  ];
  const statUserColumns = [
    { key: 'login', label: 'Login', render: v => el('span', { text: v, class: 'mono' }) },
    { key: 'visits', label: 'Wejścia', align: 'right' },
    { key: 'distinctTiles', label: 'Różnych modułów', align: 'right' }
  ];
  let statsData = null;

  function setPreset(days) {
    const to = new Date();
    const from = new Date(); from.setDate(to.getDate() - days);
    $('stats-from').value = isoDay(from);
    $('stats-to').value = isoDay(to);
  }

  async function loadStats() {
    const params = new URLSearchParams();
    if ($('stats-from').value) params.set('from', $('stats-from').value);
    if ($('stats-to').value) params.set('to', $('stats-to').value);
    statsData = await api('/api/admin/stats?' + params.toString());
    $('stats-from').value = statsData.from;
    $('stats-to').value = statsData.to;
    const moduleVisits = statsData.perTile.reduce((sum, t) => sum + t.visits, 0);
    $('kpi-portal-visits').textContent = fmtNum(statsData.portalVisits);
    $('kpi-portal-users').textContent = fmtNum(statsData.portalUsers) + ' różnych osób';
    $('kpi-module-visits').textContent = fmtNum(moduleVisits);
    $('kpi-module-users').textContent = fmtNum(statsData.perUser.length) + ' różnych osób';
    $('kpi-range').textContent = statsData.from + ' – ' + statsData.to;
    renderTable($('stats-tiles-table'), statTileColumns, statsData.perTile, {});
    renderTable($('stats-users-table'), statUserColumns, statsData.perUser, { pagination: $('stats-users-pagination') });
  }

  /* 5. Generator SQL */
  function sqlLiteral(value) { return "N'" + String(value).replace(/'/g, "''") + "'"; }

  function buildSql() {
    const code = $('gen-tile').value;
    const login = $('gen-login').value.trim().toLowerCase();
    const warning = $('gen-warning');
    warning.hidden = true;
    if (!code || !login) { showError('Wybierz kafelek i wpisz login.'); return; }
    if (!/^[a-z0-9._-]{1,64}$/.test(login)) { showError('Login może zawierać tylko małe litery, cyfry, kropkę, myślnik i podkreślenie.'); return; }
    if (login !== 'wszyscy' && login.indexOf('.') < 0) {
      warning.textContent = 'Uwaga: „' + login + '" nie wygląda jak login (brak kropki). Jeśli to skrót departamentu — w porządku; jeśli miał być login — sprawdź pisownię.';
      warning.hidden = false;
    } else if (login !== 'wszyscy' && !state.loginSet.has(login)) {
      warning.textContent = 'Uwaga: „' + login + '" nigdy nie wszedł na portal. Nadanie zadziała, ale jeśli to literówka, kafelek będzie niewidoczny bez żadnego błędu. Loginy w organizacji mają mieszany format (nazwisko.imię / imię.nazwisko / sufiksy cyfrowe).';
      warning.hidden = false;
    }
    if (code === 'administracja') {
      warning.textContent = 'Nadajesz dostęp do panelu administracyjnego. To świadoma decyzja: osoba zobaczy wszystkie kafelki, uprawnienia i wejścia.';
      warning.hidden = false;
    }
    const tile = state.tiles.find(t => t.code === code);
    const sql = [
      '-- Portal DZI: uprawnienie READ do kafelka "' + (tile ? tile.name : code) + '" dla ' + login,
      '-- Wygenerowane w panelu administracyjnym ' + new Date().toLocaleString('pl-PL') + '. Wykonaj w SSMS na bazie portal.',
      'USE portal;',
      'GO',
      'INSERT INTO tile_permissions (tile_id, ad_group, permission_level)',
      'SELECT id, ' + sqlLiteral(login) + ", 'READ' FROM tiles WHERE code = " + sqlLiteral(code),
      "  AND NOT EXISTS (SELECT 1 FROM tile_permissions p WHERE p.tile_id = tiles.id AND p.ad_group = " + sqlLiteral(login) + " AND p.permission_level = 'READ');",
      'GO',
      '-- Kontrola: powinien pojawić się wiersz z tym loginem',
      'SELECT t.code, p.ad_group, p.permission_level FROM tile_permissions p JOIN tiles t ON t.id = p.tile_id WHERE t.code = ' + sqlLiteral(code) + ' ORDER BY p.ad_group;'
    ].join('\n');
    $('gen-sql').value = sql;
    $('gen-copied').hidden = true;
  }

  async function copySql() {
    const text = $('gen-sql').value;
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
    } catch (error) {
      $('gen-sql').select();
      document.execCommand('copy');
    }
    $('gen-copied').hidden = false;
  }

  /* --------------------------------------------------------------- wiring */

  function guard(fn) {
    return async (...args) => {
      showError('');
      try { await fn(...args); } catch (error) { showError(error.message); }
    };
  }

  async function loadWhoAmI() {
    try {
      const me = await api('/api/whoami');
      const box = document.querySelector('.user-name');
      if (box && me.login) box.textContent = me.login;
    } catch (ignored) { /* nagłówek zostaje domyślny */ }
  }

  async function init() {
    setStatus('Łączenie…', true);
    await loadWhoAmI();
    try {
      await loadTiles();
      state.logins = await api('/api/admin/logins');
      fillLoginDatalist();
      setStatus('Dane z rejestru audytu · ' + state.tiles.length + ' kafelków', true);
    } catch (error) {
      setStatus('Błąd', false);
      showError(error.message);
      return;
    }
    setPreset(30);

    document.querySelectorAll('.admin-tabs .filter').forEach(button => {
      button.addEventListener('click', guard(async () => {
        activate(button.dataset.section);
        if (button.dataset.section === 'wejscia' && !auditRows.length) await loadAudit();
        if (button.dataset.section === 'statystyka' && !statsData) await loadStats();
      }));
    });

    $('tiles-export').addEventListener('click', () => downloadCsv('kafelki.csv', tileColumns, state.tiles));
    $('perm-show-tile').addEventListener('click', guard(() => showTile($('perm-tile').value)));
    $('perm-show-user').addEventListener('click', guard(() => showUser($('perm-login').value)));
    $('perm-login').addEventListener('keydown', event => { if (event.key === 'Enter') guard(() => showUser($('perm-login').value))(); });
    $('perm-export').addEventListener('click', () => downloadCsv('uprawnienia-' + $('perm-tile').value + '.csv', permColumns.map(c => ({ key: c.key, label: c.label })), currentPermissions));
    $('audit-refresh').addEventListener('click', guard(loadAudit));
    $('audit-denied').addEventListener('change', guard(loadAudit));
    $('audit-export').addEventListener('click', () => downloadCsv('audyt.csv', auditColumns.map(c => ({ key: c.key, label: c.label })), auditRows));
    $('stats-refresh').addEventListener('click', guard(loadStats));
    document.querySelectorAll('.admin-presets [data-days]').forEach(button => {
      button.addEventListener('click', guard(async () => { setPreset(Number(button.dataset.days)); await loadStats(); }));
    });
    $('stats-export-tiles').addEventListener('click', () => { if (statsData) downloadCsv('statystyka-moduly.csv', statTileColumns, statsData.perTile); });
    $('stats-export-users').addEventListener('click', () => { if (statsData) downloadCsv('statystyka-osoby.csv', statUserColumns, statsData.perUser); });
    $('gen-build').addEventListener('click', guard(async () => buildSql()));
    $('gen-copy').addEventListener('click', guard(copySql));
  }

  const run = () => { guard(init)(); };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', run); else run();
})();
