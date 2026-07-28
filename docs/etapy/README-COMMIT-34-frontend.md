# Commit 34 — strona główna portalu (frontend)

`feat(frontend): dynamiczna strona główna — kafelki z API, identyfikacja ARiMR, wzmocnienia bezpieczeństwa`

Rozpakuj na katalog projektu (dodaje 4 pliki do `frontend/`, niczego nie nadpisuje —
`index.example.html` i `portal-bootstrap.js` zostają jako referencja). Potem wrzuć
logo: **`frontend/assets/logo.png`**.

## Najważniejsza zmiana wzorca

Dotychczasowy przykład dekorował ręcznie napisane kafelki. Nowa strona renderuje je
**z odpowiedzi `/api/tiles`** — jedynym źródłem prawdy jest baza:

- dodanie kafelka = `INSERT INTO tiles` + `INSERT INTO tile_permissions` — **zero edycji HTML**,
- użytkownik widzi wyłącznie to, co zwrócił serwer (RBAC w `AccessFacade`); frontend
  niczego nie rozstrzyga, więc nie da się „odkryć" kafelka przez podejrzenie źródła strony,
- Twoja obecna strona (jeden statyczny HTML z linkami) ma odwrotny model — każdy widzi
  wszystko, a linki zna cały świat.

## GDZIE UMIESZCZAĆ PLIKI (odpowiedź na pytanie z wiadomości)

```
frontend/                              (prod: D:\portal\frontend\)
├── index.html                ← strona główna (ta paczka)
├── css/portal.css            ← style (ta paczka)
├── js/portal-app.js          ← logika (ta paczka)
├── assets/logo.png           ← TU WRZUĆ LOGO (wysokość wyświetlania 34 px)
├── apps/                     ← TU TRAFIAJĄ APLIKACJE HTML (dashboardy itp.)
│   ├── wizualizator-xml/
│   │   └── index.html        (+ jego css/js w tym samym katalogu)
│   └── mapa-jo/
│       └── index.html
└── dataset.html, lib/...     (istniejące, bez zmian)

scripts/                               (prod: D:\portal\scripts\ — NTFS read-only dla workera)
└── prod/
    └── generuj-raport.ps1    ← TU TRAFIAJĄ SKRYPTY uruchamiane kafelkami
```

### Kafelek = aplikacja HTML (Twoje dashboardy)

1. Skopiuj katalog aplikacji do `frontend/apps/<nazwa>/` (całość: html+css+js).
2. Zarejestruj kafelek:
   ```sql
   INSERT INTO tiles (code, name, description, icon, tile_type, action_ref, active, display_order)
   VALUES ('mapa-jo', 'Mapa JO ARiMR', 'Mapa i dane adresowe jednostek ARiMR',
           'map', 'LINK', '/apps/mapa-jo/index.html', 1, 50);

   INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
   SELECT id, 'DZI-Portal-Wszyscy', 'READ' FROM tiles WHERE code = 'mapa-jo';
   ```
3. Koniec — kafelek pojawi się u osób z grupy, bez restartu.

**WAŻNE — dlaczego NIE linki `file:///J:\...` jak na obecnej stronie:** przeglądarka
**blokuje** otwieranie `file://` ze strony serwowanej po http(s) — po wdrożeniu portalu
te linki po prostu przestaną działać. Poza tym wymagają zamapowanego `J:` u każdego
użytkownika i omijają RBAC. Pliki mają być serwowane przez portal — stąd `apps/`.

### Kafelek = program (Python itd.)

Dwa różne przypadki:

**(a) Program coś WYKONUJE (przetwarza, generuje, wysyła)** → mechanizm zadań:
plik na serwer do `D:\portal\scripts\prod\`, wpis do whitelisty, kafelek SCRIPT:
```sql
INSERT INTO scripts (code, path, script_type, timeout_seconds, active)
VALUES ('generuj-raport', 'D:\portal\scripts\prod\generuj-raport.ps1', 'PS1', 600, 1);

INSERT INTO tiles (code, name, description, icon, tile_type, action_ref, active, display_order)
VALUES ('generuj-raport', 'Generuj raport CEN', 'Przetwarza dane wydruku centralnego',
        'play', 'SCRIPT', 'generuj-raport', 1, 60);
-- + tile_permissions z poziomem EXECUTE
```
Python dzisiaj: **wrapper `.ps1`** wołający `python.exe skrypt.py` (typ `PY` w
`ScriptType` to zaplanowana furtka — jednolinijkowy commit, gdy będzie potrzebny).
Wykonuje to **worker**, z logiem na żywo w portalu i pełnym audytem.

**(b) Program to serwer WWW (Dash/Streamlit/Flask)** → osobna usługa Windows na
swoim porcie, a w portalu zwykły kafelek LINK do jej adresu.

## Co strona robi (UX)

Nagłówek z logo i sygnaturą „miedzy" (podwójny pas z logotypu — powtarza się jako
akcent kafelka), wyszukiwarka, filtry rodzaju (ZADANIE / DANE / APLIKACJA — mapowane
z `tileType`; osobna kolumna „kategoria" to kandydat na mały commit V4), pasek
użytkownika (login + liczby z `/api/whoami`), stany puste z instrukcją, pełny cykl
zadania na kafelku (kółko → ✔/✖/⏱) + log w konsoli. Czcionki systemowe i ikony
inline SVG — **zero CDN-ów** (sieć zamknięta; obecna strona używa lucide z CDN,
co w intranecie przestanie działać).

## Bezpieczeństwo frontendu

| Mechanizm | Gdzie | Przed czym chroni |
|---|---|---|
| CSP (meta): `script-src 'self'`, zero inline JS | index.html | wstrzyknięty `<script>`/`onclick` się nie wykona |
| `textContent`/`createElement`, zero `innerHTML` z danymi | portal-app.js | XSS przez nazwę/opis kafelka |
| sanityzacja `action_ref` (tylko http/https/ścieżki) | portal-app.js | `javascript:` w adresie kafelka |
| `rel="noopener noreferrer"` + `window.open('noopener')` | portal-app.js | tabnabbing — nowa karta bez uchwytu do portalu |
| `referrer: same-origin` | index.html | wyciek adresów portalu do zewnętrznych stron |
| RBAC wyłącznie serwerowy | architektura | ukrycie kafelka w CSS ≠ brak dostępu; tu ukrywa serwer |

Nagłówki serwerowe (X-Frame-Options, X-Content-Type-Options, HSTS) — do dodania
w IIS przy wdrożeniu; celowo nie ruszam `web.config` w tej paczce, bo globalny CSP
wymagałby najpierw wyniesienia stylów z `dataset.html` do plików (osobny drobny commit).

## Sprawdzenie po rozpakowaniu (dev)

1. `frontend/assets/logo.png` — wrzucone.
2. Start api (profil `dev` albo `dev,demo`) → `http://localhost:8080/index.html`.
3. Widok: logo, pas, 6 kafelków z badge'ami; wyszukiwarka zawęża; „Restart ETL"
   pokazuje kółko i ✔ (z workerem lub w demo z symulatorem).

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
