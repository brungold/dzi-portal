# frontend/ — witryna IIS (`D:\portal\frontend`)

Stan = serwer (listing 2026-09-09). Co jest czym:

| Plik | Rola |
|---|---|
| `index.html` + `js/portal-app.js` + `css/portal.css` | strona główna: kafelki renderowane z `/api/tiles` (RBAC serwerowy), identyfikacja ARiMR, CSP bez inline JS |
| `js/declared-identity.js` | nakładka na `fetch`: sonda `/api/whoami`; w produkcji przezroczysta (moduł IIS daje 200), okno deklaracji tylko bez IIS (dev/awaryjnie) |
| `assets/css/portal-dzi.css`, `assets/js/portal-dzi.js` | **powłoka modułów kafelków** (`D:\portal\apps\<code>\index.html` ładuje je przez `../../assets/...`); zawiera tabele, stronicowanie, plakietki, karty KPI, panel filtrów — gotowe klocki dla nowych modułów |
| `assets/logo.png` | logotyp (na serwerze wersja 112 KB) |
| `dataset.html` + `lib/tabulator/` | widok zbiorów danych (REPORT) — gotowe, bez kafelka |
| `web.config` | konfiguracja IIS — źródło prawdy w `deploy/iis/web.config` |
| `bin/PortalAuthUserModule.dll` | tylko na serwerze; kompilowany z `deploy/iis/AuthUserHeaderModule.cs` |

Usunięte 2026-09-09: `index.example.html`, `portal-bootstrap.js` — stara integracja
`data-tile-id` z zaszytymi kafelkami, zastąpiona renderem z `/api/tiles` (commit 34/37).
Na serwerze te dwa pliki też można skasować.

Dev: Spring serwuje ten katalog sam (`spring.web.resources.static-locations` w profilu `dev`).
Zasada zero-CDN: wszystko lokalnie.
