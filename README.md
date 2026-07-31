# Portal DZI

Wewnętrzny portal kafelkowy DZI: statyczny frontend, REST API w Spring Boot na
loopbacku, osobny worker wykonujący skrypty z kolejki w SQL Server.
Stan po Etapach 0–6 oraz po wprowadzeniu trybu deklarowanej tożsamości (ADR-0003)
i usunięciu profilu `demo` (ADR-0004).

**Dwa warianty tożsamości — ta sama logika, ten sam RBAC, ten sam audyt.**
Różnią się wyłącznie tym, skąd bierze się login. Dalej łańcuch jest identyczny:
`AccessFacade` + `tile_permissions` + `@PreAuthorize` + append-only `audit_log`.

**Wariant A — integracja z AD** (docelowy; profil `prod`):

```
przeglądarka ──Kerberos/443──> IIS (portal.dzi.pl)
                                ├── /            statyczny frontend (Tabulator lokalnie, zero CDN)
                                └── /api/*  ──X-Auth-User──> portal-api (127.0.0.1:8080)
                                                                │  grupy z AD przez LDAPS
                                                     SQL Server (tiles/tasks/datasets/audit)
                                                                │  claim: READPAST+UPDLOCK+OUTPUT
                                                          portal-worker ──> powershell.exe
```

**Wariant deklarowany** (profil `declared`; gdy integracja z katalogiem w runtime
jest organizacyjnie niedostępna):

```
klient (przeglądarka / skrypt PS) ──X-Auth-User: login──> portal-api (loopback + lista CIDR)
                                                            │  departament z tabeli user_departments
                                                            │  (login = JEDYNA rzecz deklarowana)
                                                 SQL Server (tiles/tasks/datasets/audit)
                                                            │
                                                     portal-worker ──> powershell.exe
```

Tożsamość jest tu **deklaracją, nie dowodem**. Granice modelu, kompensacje
(fail-closed, limiter anomalii, dwa przełączniki otwarcia na sieć) oraz twarde
zakazy — `docs/adr/0003-deklarowana-tozsamosc.md`. Uruchomienie i użycie —
`docs/deklaracja-runbook.md`.

## Co portal robi

- **Tożsamość ustalana na brzegu, nie w aplikacji**: w wariancie A Kerberos robi IIS,
  a aplikacja ufa nagłówkowi `X-Auth-User` wyłącznie z loopbacku (3 warstwy ochrony;
  test anty-spoof w `deploy/iis/verify-etap1.ps1`). W wariancie `declared` ten sam
  nagłówek niesie samą deklarację loginu — chronioną loopbackiem, jawną listą CIDR
  i limiterem wykrywającym wiele loginów z jednego adresu.
- **Kafelki z dwustronnym RBAC**: lista filtrowana po przynależności użytkownika
  (grupy AD albo `user_departments`; READ<EXECUTE<EDIT),
  a każda akcja twardo egzekwowana `@PreAuthorize` + `AccessFacade` (403 + DENIED w audycie).
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
5. Przeglądarka: `http://localhost:8080/index.example.html` (dev loguje Cię jako `tester`):
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
- **declared** — tożsamość deklarowana (ADR-0003): źródłem loginu jest nagłówek
  `X-Auth-User`, przynależność wyprowadza serwer z tabeli `user_departments`.
  Bez LDAP, bez gMSA, bez sekretów. Domyślnie tylko loopback; otwarcie na sieć wymaga
  **dwóch** przełączników w `application-declared.yml` (`server.address` ORAZ
  `allowed-cidrs`). **Nie łączyć z profilem `prod`** — prod aktywuje LDAP.
  Nieznany login = pusty zbiór grup = 403 (fail-closed).

## Deployment i eksploatacja

Ścieżka zależy od wariantu tożsamości:

- **wariant A (z AD):** `docs/etap1-runbook.md` (Kerberos/IIS/gMSA/LDAP/usługa),
  potem `docs/etap6-runbook.md`;
- **wariant deklarowany:** `docs/deklaracja-runbook.md` (uruchomienie, prowizja
  `user_departments`, klient PowerShell, zakres ochrony). Z runbooków Etapu 1/6
  obowiązują wtedy wyłącznie części niezwiązane z AD — kroki Kerberos/SPN/gMSA/LDAP
  **nie mają zastosowania**. Prowizja przynależności: `deploy/declared/export-user-departments.ps1`.

Wspólne dla obu: `docs/etap6-runbook.md` w części worker/NTFS/retencja/
**obowiązkowy test rollbacku**. Narzędzia: `deploy/deploy.ps1` (z automatycznym rollbackiem),
`deploy/iis/setup-iis.ps1` + `verify-etap1.ps1`, `deploy/verify-hardening.ps1`,
szablony WinSW w `deploy/winsw/`, retencja w `deploy/sql/audit-retention.sql`.

## Dokumentacja decyzji i historia

- `docs/adr/0001-wybory-technologiczne.md` — wybory fundamentu (Etap 0)
- `docs/adr/0002-hardening-i-odswiezanie.md` — ETag, retencja, hardening (Etap 6)
- `docs/adr/0003-deklarowana-tozsamosc.md` — profil `declared`; uzupełnia ADR-0001 dec. 6 i 7
- `docs/adr/0004-wariant-bez-demo.md` — fizyczne usunięcie profilu `demo` z tej linii źródeł
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
5. **Tryb `declared`: statyczne powłoki HTML kafelków APLIKACJA są dostępne dla każdego,
   kto przejdzie filtr tożsamości.** RBAC chroni `/api`, nie pliki statyczne. Treść
   wrażliwa ma mieszkać wyłącznie za endpointami `/api` — nigdy w samym HTML-u kafelka.

## Stan po commitach 33–40

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
