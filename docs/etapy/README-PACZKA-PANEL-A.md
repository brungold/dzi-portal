# Paczka: panel administracyjny — Faza A (odczyt) + paczka kodu #2 (2026-09-09)

Jeden jar, dwa tematy:

1. **Panel administracyjny** (ADR-0009): kafelek `administracja`, sześć endpointów `/api/admin/**`
   (same GET-y), moduł `apps/administracja/` na powłoce portal-dzi, generator SQL do nadawania
   uprawnień. Zero nowych tabel, zero migracji, zero endpointów zapisu.
2. **Paczka kodu #2** (aneks ADR-0007): audyt całego katalogu `data\` modułów niezależnie od
   rozszerzenia (+ `pdf/xml/txt/xls`), baner `declared` bez „wróci z modułem v2",
   `spring.lifecycle.timeout-per-shutdown-phase: 20s` w jarze, komentarze OU.

Kod powstał bez kompilacji u asystenta — **sędzią jest `mvn clean verify`** (z Dockerem:
dwa nowe testy integracyjne na prawdziwym SQL Serverze sprawdzają T-SQL panelu i audytu).

## Zawartość — Java (portal-api)

| Plik | Rola |
|---|---|
| `admin/AdminController.java` | 6 × GET pod `@PreAuthorize("@access.canRead('administracja', authentication)")`, każdy `@Audited("ADMIN_VIEW")` + `object_ref` |
| `admin/AdminFacade.java` | składanie widoków; rozpoznawanie rodzaju wpisu (login / departament / wszyscy); flaga „login nieznany w audycie"; walidacja loginu i kodu (400), 404 dla nieznanego kafelka; zakres dat domyślnie 30 dni |
| `admin/AdminQueries.java` | interfejs modelu odczytu (rekordy wierszy) |
| `admin/JdbcAdminQueries.java` | jawny T-SQL na `JdbcTemplate`, po istniejących indeksach `audit_log` |
| `admin/AdminViews.java` | rekordy odpowiedzi JSON |
| `admin/AdminConfiguration.java` | bean fasady (jak `TilesConfiguration`) |
| `apps/AppsAuditPolicy.java`, `apps/AppsController.java` | paczka #2: reguła `data\` |
| `security/DeclaredSecurityConfiguration.java` | paczka #2: baner |
| `resources/application.yml`, `application-declared.yml` | paczka #2: 20 s, komentarze |

## Zawartość — testy

| Plik | Co sprawdza |
|---|---|
| `admin/AdminFacadeTest.java` (7) | logika widoków bez Springa: okna 30/90 dni, klasyfikacja wpisów, ⚠ nieznany login, „co widzi osoba", zakres dat, limit audytu |
| `admin/AdminEndpointTest.java` (6) | bramka: 401 bez tożsamości, 403 dla zalogowanego bez kafelka `administracja`, 200 dla admina, 404, 400, kształt JSON |
| `admin/JdbcAdminQueriesIT.java` (6, Docker) | T-SQL na SQL Server 2022 po migracjach; wpisy audytu z produkcyjnego `AuditWriter` |
| `admin/InMemoryAdminQueries.java`, `tiles/AdminGateTestConfiguration.java` | atrapy |
| `apps/AppsAuditPolicyTest.java` (6), `apps/AppsControllerTest.java` (+2) | paczka #2 |

Spodziewane po `mvn clean verify`: jednostkowe **87 → 108**, integracyjne w `portal-api` **4 → 10**.

## Zawartość — front, SQL, docs

| Plik | Rola |
|---|---|
| `apps/administracja/index.html`, `app.js`, `module.css` | moduł na powłoce `portal-dzi` (jak ReD/AUREA); zero bibliotek, zero `innerHTML`; tabele sortowane po nagłówku, stronicowane po 50; eksport CSV; generator `INSERT` |
| `deploy/sql/apps-administracja.sql` | rejestracja kafelka (`display_order` 900) + READ dla dwóch loginów administratorów; idempotentny |
| `docs/adr/0009-panel-administracyjny.md` | decyzja (rewiduje „bez panelu — celowo") |
| `docs/adr/0007-straznik-apps.md` | aneks: definicja pliku danych |
| `apps/README.md`, `.gitignore` | wiersz panelu; wyjątek `!apps/administracja/` — panel jest w repo, bo jest sprzężony z jarem |

## Sekcje panelu

| Sekcja | Endpoint | Co pokazuje |
|---|---|---|
| Kafelki | `GET /api/admin/tiles` | wszystkie (także nieaktywne): typ, stan, kolejność, liczba wpisów uprawnień, wejścia 30/90 dni, ostatnie wejście; klik → uprawnienia |
| Uprawnienia · kafelek | `GET /api/admin/tiles/{code}` | reguły (login / departament / wszyscy, poziom, ⚠ login nigdy nie wszedł) + kto faktycznie wchodził |
| Uprawnienia · osoba | `GET /api/admin/users/{login}` | kafelki imienne i „wszyscy" (pewne), departamentowe (warunkowe: „jeśli w: dzi"), wejścia osoby, pierwsze/ostatnie wejście |
| Ostatnie wejścia | `GET /api/admin/audit?limit=&login=&action=&tile=` | 10–500 wpisów od najnowszego; filtry; „tylko odmowy" |
| Statystyka | `GET /api/admin/stats?from=&to=` | zakres (presety 7/30/90): wejścia na portal, per moduł, per osoba; eksport CSV |
| Generator SQL | `GET /api/admin/logins?q=` (podpowiedzi) | gotowy `INSERT ... WHERE NOT EXISTS` do wklejenia w SSMS; ostrzeżenie przy loginie nieznanym w audycie |

Wejście = `APP_OPEN` z `object_ref = 'tile:<kod>'`. Każde wywołanie panelu ląduje w audycie
jako `ADMIN_VIEW` (kto oglądał panel — też widać w sekcji „Ostatnie wejścia").

## Wdrożenie — kolejność

### [DOM]
1. `git status` czysty. Rozpakować **zawartość** `paczka-panel-A` na repo (`Copy-Item "$paczka\*" -Recurse -Force`).
2. `README-PACZKA-PANEL-A.md` → `docs\etapy\`.
3. `mvn clean verify` (Docker włączony). Oczekiwane: `BUILD SUCCESS`, `Tests run: 108` w portal-api,
   IT: 10 (w tym `JdbcAdminQueriesIT` 6). FAIL → log tu, nie commitować.
4. `git add -A`, `git status` → ~13 nowych, ~9 zmienionych, w tym `apps/administracja/` (3 pliki —
   to jest celowy wyjątek od „moduły poza repo").
5. Commit: `feat(admin): panel administracyjny Faza A (ADR-0009) + audyt data/ (aneks ADR-0007)` → push.

### [LAPTOP] — build (instrukcja wdrożenia jara, sekcja A)
6. Świeży ZIP z GitHuba → nowy folder → `mvn clean package "-DskipTests"` → `BUILD SUCCESS`.
7. `Get-FileHash` jara, kopia na J: jako `portal-api-<data>.jar`. **Do J: także trzy pliki
   `apps\administracja\`** (z tego samego ZIP-a).

### [SERWER #2] — przeniesienie
8. Jar z J: → `D:\portal\archiwum\portal-api-<data>.jar`; `Get-FileHash` = laptop.
9. `apps\administracja\` z J: → tymczasowo `D:\portal\archiwum\administracja\`.

### [SERWER #3] — wdrożenie (sekcja C instrukcji jara)
10. `Stop-Service portal-api` → kopia `portal-api.jar` do archiwum jako `-przed` → podmiana → `Start-Service portal-api` → health `UP` → log: `Started PortalApiApplication`, baner bez „wróci z modułem v2".
11. `New-Item -ItemType Directory D:\portal\apps\administracja` → skopiować `index.html`, `app.js`, `module.css` z `D:\portal\archiwum\administracja\`.
    Kontrola: `Get-ChildItem D:\portal\apps\administracja` → dokładnie 3 pliki.

### [SERWER #2] — rejestracja kafelka
12. `apps-administracja.sql` z J: na `D:\portal\archiwum\`, potem:
    `sqlcmd -S localhost -d portal -E -f 65001 -i D:\portal\archiwum\apps-administracja.sql`
    Oczekiwane: `(1 rows affected)` ×3 i tabela kontrolna z dwoma wierszami uprawnień.

### [STACJA] — test
13. Strona główna, Ctrl+F5 → kafelek „Administracja portalu" **jako ostatni**. Otwórz: pięć sekcji,
    „Kafelki" pokazuje 5 wierszy (cztery moduły + panel) z liczbami wejść.
14. Sekcja „Ostatnie wejścia" → na górze Twoje `ADMIN_VIEW`.
15. Ze stacji Piotra: panel otwiera się; ze stacji osoby spoza dwójki: kafelka nie ma,
    a `/apps/administracja/` = „Brak dostępu" (+ `APP_DENIED` w audycie).
16. [SERWER #2] kontrola paczki #2: po otwarciu ReD i jednego pliku z `data\`:
    `sqlcmd -S localhost -d portal -E -W -w 250 -Q "SELECT TOP 5 ts_pl, username, action, path FROM v_audit_log_pl WHERE action = 'APP_DATA' ORDER BY id DESC"`
    → wpis z `path` kończącym się na `data/<plik>.js`.

### Rollback
- Jar: `Stop-Service` → `-przed.jar` jako `portal-api.jar` → `Start-Service`.
- Kafelek: `UPDATE tiles SET active = 0 WHERE code = N'administracja'` (znika z listy i wyłącza API);
  usunięcie całkowite: `DELETE FROM tile_permissions WHERE tile_id = (SELECT id FROM tiles WHERE code = N'administracja'); DELETE FROM tiles WHERE code = N'administracja';`

## Walidacja przed spakowaniem (wykonana)

- Bilans klamer w 15 plikach Java: równy; nazwy parametrów `@RequestParam`/`@PathVariable` jawne.
- `node --check app.js`: składnia OK; wszystkie `id` używane w JS istnieją w HTML; zero `innerHTML`.
- Nagłówki autorskie: komplet.
- Escapowanie `LIKE` w T-SQL sprawdzone bajt po bajcie (`ESCAPE '\'`).
- Loginy w paczce: wyłącznie dwóch administratorów (już w repo od `prod-grants.sql`) i dane testowe fikcyjne.

## Poza zakresem (świadomie)

- Nadawanie/odbieranie uprawnień z panelu — Faza B, po 2–3 tygodniach użycia.
- `DevSecurityConfiguration` → `@Profile("dev")` — osobna paczka po zielonych testach tej.
- Próg blokady loginów z adresu — decyzja administratora, jedna linia w `config\application-declared.yml`.
