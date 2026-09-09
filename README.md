# Portal DZI

Wewnętrzny portal kafelkowy DZI: statyczny frontend za IIS, REST API w Spring Boot
na loopbacku jako usługa Windows, moduły aplikacyjne za strażnikiem, SQL Server.
Stan na 2026-09-08: produkcja od 2026-08-11 na profilu `declared` z tożsamością
z Windows Authentication (ADR-0006/0008) i strażnikiem `/apps` (ADR-0007).

**Produkcja (profil `declared`, serwer DZI-APP01V):**

```
przeglądarka (stacja) ──NTLM──> IIS (arimr-app.zszik.pl, D:\portal\frontend)
   moduł C# PortalAuthUserHeader v3.0: X-Auth-User (login) + X-Auth-Dept (pierwsze OU z AD)
   URL Rewrite + ARR:  /api/*  → 127.0.0.1:8080
                       /apps/* → 127.0.0.1:8080          ← strażnik (ADR-0007)
portal-api (usługa WinSW, LocalSystem, profil declared)
   /api: RBAC {login, departament, wszyscy} vs tile_permissions, audyt każdego żądania
   /apps: plik z D:\portal\apps\<code>\ po sprawdzeniu READ; 403/404 = HTML; audyt selektywny
SQL Server Express 2022 (baza portal, konto SQL portal_app; audit_log append-only przez DENY)
```

Cztery kafelki w produkcji (wszystkie typu LINK → moduł za strażnikiem): `apps/README.md`.
Kod SCRIPT/REPORT (worker, zadania, zbiory danych, Tabulator) jest gotowy i nieużywany —
żaden kafelek go nie uruchamia.

**Dwa warianty tożsamości — ta sama logika, ten sam RBAC, ten sam audyt.**
Różnią się wyłącznie tym, skąd bierze się login. Dalej łańcuch jest identyczny:
`AccessFacade` + `tile_permissions` + `@PreAuthorize` + append-only `audit_log`.

**Wariant A — integracja z AD przez grupy** (profil `prod`; droga powrotna, nieużywany):

```
przeglądarka ──Kerberos/443──> IIS (arimr-app.zszik.pl)
                                ├── /            statyczny frontend (Tabulator lokalnie, zero CDN)
                                └── /api/*  ──X-Auth-User──> portal-api (127.0.0.1:8080)
                                                                │  grupy z AD przez LDAPS
                                                     SQL Server (tiles/tasks/datasets/audit)
                                                                │  claim: READPAST+UPDLOCK+OUTPUT
                                                          portal-worker ──> powershell.exe
```

**Profil `declared`** (produkcyjny; nagłówki wypełnia serwer — moduł IIS — więc
tożsamość jest uwierzytelniona; bez IIS, w dev, te same nagłówki są deklaracją):

```
klient (przeglądarka / skrypt PS) ──X-Auth-User + X-Auth-Dept──> portal-api (loopback + lista CIDR)
                                                            │  uprawnienia żądania:
                                                            │  {login, departament, wszyscy} → tile_permissions
                                                 SQL Server (tiles/tasks/datasets/audit)
                                                            │
                                                     portal-worker ──> powershell.exe
```

W produkcji oba nagłówki wpisuje moduł IIS **po** Windows Authentication i nadpisuje
cokolwiek przysłał klient — login i departament są uwierzytelnione (ADR-0006, ADR-0008).
Deklaracja z przeglądarki (`declared-identity.js`, `docs/deklaracja-runbook.md`) to tryb
dev/awaryjny. Kompensacje z ADR-0003 (limiter, blokada wielu loginów z adresu) działają
nadal; przy tożsamości z NTLM blokada daje głównie fałszywe alarmy — próg konfigurowalny
(`deploy/declared/application-declared.yml.example`).

## Co portal robi

- **Tożsamość ustalana na brzegu, nie w aplikacji**: w wariancie A Kerberos robi IIS,
  a aplikacja ufa nagłówkowi `X-Auth-User` wyłącznie z loopbacku (3 warstwy ochrony;
  test anty-spoof w `deploy/iis/verify-etap1.ps1`). W wariancie `declared` ten sam
  nagłówek (plus drugi: `X-Auth-Dept`) niesie deklarację loginu i departamentu —
  chronioną loopbackiem, jawną listą CIDR i limiterem wykrywającym wiele loginów
  z jednego adresu.
- **Kafelki z dwustronnym RBAC**: lista filtrowana po zbiorze {login, departament,
  `wszyscy`} vs `tile_permissions` (READ<EXECUTE<EDIT), a każda akcja twardo
  egzekwowana `@PreAuthorize` + `AccessFacade` (403 + DENIED w audycie).
- **Strażnik `/apps`** (ADR-0007): pliki modułów leżą poza witryną IIS, wydaje je
  aplikacja po sprawdzeniu READ na kafelku; 403/404 jako strony HTML, ETag/304,
  audyt selektywny (wejście do modułu, pliki danych, każda odmowa).
- **Audyt każdego żądania** + akcje biznesowe (`@Audited`, `object_ref`); rejestr append-only
  na poziomie uprawnień SQL (DENY > rola) — z testem na prawdziwej bazie.
- **Zadania**: klik kafelka SCRIPT → kolejka w SQL (atomowy claim) → `ProcessRunner`
  (timeout + kill drzewa procesów, Cp852) → status i log na żywo w UI; sieroty domyka sweeper.
- **Zbiory danych**: import XLSX (walidacja z raportem, transakcyjny merge, historia importów),
  widok w Tabulatorze, edycja komórek z optimistic lockingiem (`@Version`),
  auto-odświeżanie pollingiem ETag (304 poza audytem).

## Moduły

- **portal-common** — wyłącznie to, czego dotykają oba procesy: kolejka, whitelist skryptów, audyt.
- **portal-api** — REST, security, audyt, Flyway (jedyny właściciel schematu), moduły tiles/tasks/datasets.
- **portal-worker** — polling kolejki i wykonywanie skryptów; bez warstwy web, bez sekretów.

## Szybki start (dev)

1. JDK 21 (Temurin), Maven 3.9+, SQL Server (Express albo Docker: `mcr.microsoft.com/mssql/server:2022-latest`).
2. `deploy/sql/dev-setup.sql` (tworzy bazę `portal_dev` i login).
3. `mvn clean verify` — testy jednostkowe zawsze; `*IT` (Testcontainers) tylko przy dostępnym Dockerze, inaczej same się pomijają.
4. `mvn -pl portal-api spring-boot:run -Dspring-boot.run.profiles=dev`
   i w drugim oknie `mvn -pl portal-worker spring-boot:run -Dspring-boot.run.profiles=dev`.
5. Przeglądarka: `http://localhost:8080/index.html` (dev loguje Cię jako `tester`;
   `index.example.html` + `portal-bootstrap.js` to stara integracja `data-tile-id`,
   zastąpiona renderem z `/api/tiles` w commicie 34/37):
   - **Restart ETL / Demo: błąd / Demo: timeout** — trzy zakończenia zadań na żywo,
   - **Raport licencji** — Tabulator: edytuj komórkę (wyścig w 2 kartach ⇒ 409),
     zaimportuj `docs/przyklady/licencje-import.xlsx`, obserwuj auto-odświeżanie (ETag),
   - twardy RBAC: `curl -i -H "X-Auth-User: DZI\viewer" http://localhost:8080/api/tiles/admin-tylko` ⇒ 403,
   - `SELECT TOP 20 * FROM audit_log ORDER BY id DESC`.

## Profile

- **dev** — SQL auth, grupy ze statycznej mapy, `dev-fallback-user`, seedy z `db/migration-dev`,
  Spring serwuje frontend z `../frontend`.
- **prod** — bind wyłącznie 127.0.0.1, integrated security (gMSA), grupy z LDAPS
  (jedyny sekret: konto read-only, env w WinSW), graceful shutdown; frontend serwuje IIS.
- **declared** — **profil produkcyjny** (ADR-0003 + ADR-0005 + ADR-0006/0008): nagłówki
  `X-Auth-User` i `X-Auth-Dept` (pierwsze OU z AD) wpisuje moduł IIS po NTLM;
  w dev pochodzą z deklaracji; uprawnienia żądania = {login, departament, wszyscy}
  porównywane z `tile_permissions` — czyli dostępy nadaje się departamentowi,
  loginowi imiennie albo miksem, bez rejestru użytkowników po stronie portalu.
  Bez LDAP, bez gMSA, bez sekretów. Domyślnie tylko loopback; otwarcie na sieć wymaga
  **dwóch** przełączników w `application-declared.yml` (`server.address` ORAZ
  `allowed-cidrs`). **Nie łączyć z profilem `prod`** — prod aktywuje LDAP.

## Deployment i eksploatacja

Produkcja (`declared`) — dokumenty w kolejności odtwarzania serwera:

1. `deploy/iis/INSTRUKCJA-MODUL-WINDOWS-AUTH.md` + `deploy/iis/web.config` (IIS, moduł v3.0,
   obie reguły proxy) — stan serwera, zweryfikowany 2026-09-07;
2. `deploy/declared/application-declared.yml.example` → `D:\portal\api\config\` (hasło do bazy);
3. `deploy/winsw/portal-api.declared.xml` + `docs/winsw-runbook.md` (usługa, logi, wdrożenie jara);
4. `deploy/sql/prod-grants.sql` (DENY na `audit_log`), `deploy/sql/apps-nowy-modul.sql`;
5. `apps/README.md` (moduły za strażnikiem — procedura i konwencje).

Wariant A (nieużywany): `docs/etap1-runbook.md`, `docs/etap6-runbook.md`, szablony gMSA
w `deploy/winsw/`. `docs/deklaracja-runbook.md` — deklaracja z przeglądarki (dev/awaryjnie).

Narzędzia: `deploy/deploy-api.ps1` (wdrożenie jara na usługę), retencja w
`deploy/sql/audit-retention.sql`. **`deploy/deploy.ps1` nie uruchamiać** (wariant A;
`robocopy /MIR` na katalog witryny) — patrz ostrzeżenie w skrypcie.

## Dokumentacja decyzji i historia

- `docs/adr/0001-wybory-technologiczne.md` — wybory fundamentu (Etap 0)
- `docs/adr/0002-hardening-i-odswiezanie.md` — ETag, retencja, hardening (Etap 6)
- `docs/adr/0003-deklarowana-tozsamosc.md` — profil `declared`; uzupełnia ADR-0001 dec. 6 i 7
- `docs/adr/0004-wariant-bez-demo.md` — fizyczne usunięcie profilu `demo` z tej linii źródeł
- `docs/adr/0005-deklarowany-departament.md` — departament jako drugi wymiar uprawnień;
  uzasadnienie skalą (4–11,5 tys. użytkowników) i granice modelu
- `docs/adr/0006-tozsamosc-windows-auth-modul-iis.md` — login z Windows Authentication
  przez moduł IIS (produkcja od 2026-08-11)
- `docs/adr/0007-straznik-apps.md` — pliki modułów za aplikacją, RBAC i audyt selektywny
- `docs/adr/0008-departament-z-ou-modul-v3.md` — departament z pierwszego OU w AD (moduł v3.0)
- `docs/winsw-runbook.md` — portal jako usługa Windows (Faza 10)
- `docs/etapy/README-ETAP1..6.md` + `README-POPRAWKI-31/32.md` — pełna historia z planem
  **32 commitów**: etapy budowy (1–30) oraz dwa commity poprawkowe z przeglądu seniorskiego
  (izolacja testów FIRST, SID-y dla polskiej lokalizacji, 400 dla złych wejść, drobne DRY/perf).
  Katalog jest już scalony ze wszystkim, więc alternatywnie: jeden commit początkowy.

## Znane punkty uwagi

1. **Testy: znane relokacje Boota 4.1 są już naniesione w źródłach** — cztery pliki
   testowe importują `@WebMvcTest` z `org.springframework.boot.webmvc.test.autoconfigure`,
   a `TilesConfiguration`, `TileRepository` i `TilePermissionRepository` są `public`
   (zweryfikowane w master 31.07.2026). Sędzią pozostaje `mvn clean verify` u Ciebie;
   przy niespodziewanym FAIL wdrożenia nie blokuj — `mvn clean package -Dmaven.test.skip=true`
   i wróć z logiem testu.
2. Testcontainers 2.0 mógł zmienić pakiety — dotyczy wyłącznie klas `*IT`.
3. Wersje przypięte świadomie: POI **5.3.0** (BOM Boota nie zarządza), Tabulator **6.5.2**
   (zvendorowany w `frontend/lib/`, MIT).
4. Skrypty PowerShell pisane na sucho — po każdym setupie odpal odpowiedni `verify-*.ps1`.
5. **Pliki modułów są za strażnikiem** (ADR-0007, od 2026-08-13): `D:\portal\apps\<code>\`
   wydaje aplikacja po sprawdzeniu READ. Audyt plików danych idzie po rozszerzeniu
   (`csv/xlsx/json`; katalog `data/` i `pdf/xml/txt` — paczka kodu #2) — dane w `.js`
   poza `data/` nie zostawiają śladu. Konwencje: `apps/README.md`.
6. **`DevSecurityConfiguration` ma `@Profile("!prod")`**, więc pod `declared` żyje też
   dev-owy łańcuch statyki (`/`, `/*.html`, `/*.js`, `/*.css`) — nieszkodliwy na loopbacku
   bez zasobów, ale to konfiguracja dev w produkcji. Zmiana na `@Profile("dev")` wymaga
   jednoczesnego ogrodzenia łańcuchów wariantu A w `SecurityConfig` i przebiegu testów —
   zaplanowana, nie zrobiona.
7. **Repo = serwer** od paczki 2026-09-08 z wyjątkiem: `frontend/` (powłoka na serwerze
   vs `index.html` z commitu 37 — do porównania) i agregatów ReD (`red-dashboard.js`,
   `data/`, skrypt generujący) — otwarte.

## Stan po commitach 33–41

- **33** — profil `demo`: portal bez bazy (in-memory za interfejsami repo, symulator zadań,
  audyt do logu). **W tym wariancie źródeł profil usunięty** — decyzja i skutki: `docs/adr/0004-wariant-bez-demo.md`.
- **34/37** — nowa strona główna: kafelki renderowane z `/api/tiles`, identyfikacja ARiMR
  (logo → `frontend/assets/logo.png`), kolor kafelka koduje rodzaj, CSP bez inline JS.
- **35** — fix: BOM testcontainers w pomie nadrzędnym (bez niego Maven nie wczytywał modułów).
- **36** — fix: migracje Flyway wróciły do postaci sprzed nagłówków (checksumy!). Wyjątek
  opisany w `AUTORSTWO.md` — nagłówków do `db/migration/**` NIE dodawać nigdy.
- **37** — fix: wzorce statyki dev obejmują `/css/**`, `/js/**`, `/apps/**` (wcześniej 403).
- **38** — profil **`declared`** (ADR-0003): `DeclaredIdentityProperties`,
  `DeclaredHeaderAuthenticationFilter`, `DeclaredRateLimiter` (wykrywanie anomalii),
  `DeclaredDbGroupResolver`, `DeclaredSecurityConfiguration`, migracja V4
  (`user_departments`), `application-declared.yml`, testy jednostkowe,
  `deploy/declared/export-user-departments.ps1`, `docs/deklaracja-runbook.md`.
  Łańcuch egzekwowania i audytu — bez zmian.
- **39** — dokumentacja obu wariantów tożsamości (bez zmian w kodzie): README, brief,
  banery w runbookach Etapu 1/6 i README WinSW/IIS, odsyłacze ADR-0001 → ADR-0003.
- **40** — deklaracja loginu w **przeglądarce**: `frontend/js/declared-identity.js`
  (nakładka na `fetch`) sonduje `/api/whoami`; przy 401 pokazuje okno deklaracji,
  zapamiętuje login (localStorage) i dokłada `X-Auth-User` do wywołań `/api`;
  w wariancie A / dev (sonda 200) jest przezroczysta. `portal-app.js`,
  `portal-bootstrap.js` i inline-skrypt `dataset.html` — nietknięte.
- **41** — **deklarowany departament** (ADR-0005): drugi nagłówek `X-Auth-Dept`,
  uprawnienia żądania = {login, departament, wszyscy} wprost z deklaracji (bez
  zapytań do bazy o przynależność), okno w przeglądarce z polem departamentu
  i podpowiedzią o formacie loginu, klient `deploy/declared/portal-client.ps1`.
  Usunięte: `DeclaredDbGroupResolver` (+test). Tabela `user_departments` i V4
  zostają nieużywane — droga powrotna do modelu z rejestrem / wariantu A.

- **paczka A/B (2026-08-06..11)** — puste nagłówki → 401, UTC + widoki `v_audit_log_pl`,
  moduł IIS Windows Auth (ADR-0006), frontend bez okna deklaracji.
- **paczka porządkowa (2026-08)** — migracje plik-per-tabela (V1–V13), ADR-0006, moduł v2.0.
- **strażnik (2026-08-13, `2bcdfcc`)** — ADR-0007: `AppsController` i spółka, `apps/`.
- **repo = serwer (2026-09-08)** — moduł v3.0 + ADR-0008, `web.config` = serwer,
  `portal-api.declared.xml`, `application-declared.yml.example`, `prod-grants.sql`
  pod realne konta, `.gitignore` na dane modułów, trzy moduły dogonione, WinSW runbook.

Historia per commit: `docs/etapy/`. Brief dla nowych sesji pracy: `docs/BRIEF-PROJEKTU.md`.

## Autor

**Maciej Myśliwiec** — koncepcja, architektura, decyzje projektowe, implementacja
i dokumentacja (2026). Szczegółowy zakres autorstwa, informacja o prawach oraz
wykaz komponentów zewnętrznych wraz z licencjami: **`AUTORSTWO.md`**.

Autorskie prawa osobiste (prawo do autorstwa) są niezbywalne — art. 16 ustawy
z 4.02.1994 r. o prawie autorskim i prawach pokrewnych. Nagłówków z informacją
o autorstwie w plikach źródłowych nie należy usuwać.

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
