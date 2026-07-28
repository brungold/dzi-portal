# dzi-portal — brief projektu (dla nowych sesji pracy)

Dokument orientacyjny: wklej do wiedzy projektu Claude (Project knowledge) albo załącz
na starcie nowej rozmowy. Stan na: 2026-07-17, po commitach 1–37 (komplet-v3).

## 1. Co to jest

Wewnętrzny **portal kafelkowy DZI/ARiMR** (Java 21, Spring Boot 4.1, SQL Server,
3 moduły Maven: portal-common / portal-api / portal-worker). Kafelki = aplikacje HTML,
raporty-zbiory danych (Tabulator, edycja z wersjonowaniem, import XLSX) i zadania
(skrypty PS1 wykonywane przez osobny proces worker z kolejki w SQL). Dostępy: grupy AD
`DZI-Portal-*` → tabela `tile_permissions` (READ < EXECUTE < EDIT). Wszystko audytowane
(append-only). Autor: Maciej Myśliwiec (nagłówki w plikach; wyjątki: `AUTORSTWO.md`).

## 2. Architektura docelowa (wariant A)

```
przeglądarka ──Kerberos──> IIS (portal.dzi.pl, serwer DZI-APP01V, Win Server 2025)
                             │ proxy /api + nagłówek X-Auth-User (3 warstwy anty-spoof)
                             ▼
                Spring Boot API (127.0.0.1:8080, WinSW, gMSA) ── LDAPS 636 ──> AD (grupy)
                             │                                   (sekret: svc-portal-ldap)
                        SQL Server (tiles/tasks/datasets/audit; claim: UPDLOCK+READPAST+OUTPUT)
                             │
                portal-worker (Windows Service/WinSW, gMSA) ──> powershell.exe
```

W dev: Spring serwuje frontend sam (`file:../frontend/`), tożsamość = fallback `tester`
ze statyczną mapą grup; w prod statykę serwuje IIS.

## 3. Źródło prawdy

- **Kod:** `dzi-portal-komplet-v3.zip` = scalone commity 1–37. Wcześniejsze paczki
  (komplet-v2, autorstwo, demo-33, frontend-34/37, fix-35/36) są NADPISANE przez v3.
- **GitHub:** prywatne repo `github.com/brungold/dzi-portal` (push w toku — patrz §8).
- **Historia decyzji:** `docs/adr/0001..0002`, per-commit: `docs/etapy/README-*.md`.
- Wariant zapasowy PostgreSQL: osobne archiwum `dzi-portal-postgres.zip` (150 plików,
  NIE nakładka!). Blueprint .NET: `szablon-opcja1-aspnetcore.md` (dokument, bez kodu).

## 4. Historia w pigułce (commity)

| # | Zakres |
|---|---|
| 1–30 | Etapy 0–6: fundament+audyt → IIS/Kerberos → kafelki RBAC → zadania+worker → zbiory+XLSX → ETag/hardening/deploy |
| 31–32 | dwa przeglądy seniorskie: izolacja testów, SID-y zamiast nazw grup lokalnych, COUNT dla missingInFile, finally bez return, 400 dla złych wejść, pool schedulera=2 |
| 33 | **profil `demo`**: portal bez bazy — repozytoria in-memory za istniejącymi interfejsami, `DemoTaskSimulator` zamiast workera, audyt do logu; kombinacje `dev,demo` i `prod,demo`; jedyny dotknięty plik prod: `AuditWriter` (+`@Profile("!demo")`) |
| 34+37 | nowa strona główna: render z `/api/tiles` (RBAC serwerowy), identyfikacja ARiMR (logo na białej płycie w zielonym hero, kolor kafelka koduje rodzaj: zieleń=ZADANIE, złoto=DANE, błękit=APLIKACJA), CSP bez inline JS, sanityzacja URL, zero CDN-ów |
| 35 | fix: **BOM testcontainers** w pomie nadrzędnym — Boot 4.1 nie zarządza już tymi wersjami; bez BOM-a Maven nie wczytywał portal-api/worker („version is missing") i IDE nie widziało źródeł |
| 36 | fix: **checksumy Flyway** — nagłówki autorstwa złamały sumy kontrolne migracji; pliki `db/migration/**` wróciły do postaci bajt-w-bajt; ZAKAZ nagłówków w migracjach (sekcja w `AUTORSTWO.md`) |
| 37 | fix: wzorce statyki dev (`/*.css` nie łapie podkatalogów → 403) — dodane `/css/**`, `/js/**`, `/apps/**` |

## 5. Środowisko dev (laptop Maćka) — DZIAŁA

- IntelliJ CE, SDK **corretto-21**; projekt otwierany przez `pom.xml` → Open as Project.
- SQL Express 2022: `localhost,1433` (tryb mieszany, TCP włączone), baza `portal_dev`,
  login `portal_dev` / `PortalDev123!` (jawna domyślka dev — NIE sekret prod).
- Run config API: `PortalApiApplication`, Active profiles `dev`, Working directory =
  katalog `portal-api` (ścieżki względne `../frontend`!). Worker: analogicznie, wd = `portal-worker`.
- Tryb bez bazy: Active profiles `dev,demo` (worker zbędny — symulator w API).
- URL: `http://localhost:8080/index.html` (stary przykład: `index.example.html`).
- Logo: `frontend/assets/logo.png` (poziomy logotyp ARiMR).
- Nieszkodliwe w logu: „Using generated security password" (tożsamość daje filtr nagłówka).

## 6. Klasyczne pułapki (już przerobione — nie powtarzać diagnozy)

1. Rozpakowywanie zipów: wrzucać **zawartość** folderu z archiwum, nie sam folder
   (inaczej dubel struktury i moduły `(1)/(2)` w IDE → skasować `.idea`, otworzyć pom).
2. Pliki `.java` w `resources` zamiast `src/main/java` → ClassNotFound.
3. Brak profilu `dev` → „Failed to configure a DataSource".
4. `Connection refused :1433` → SQL Express ma TCP wyłączone fabrycznie (Configuration
   Manager → TCP/IP Enable → IPAll port 1433, dynamic puste → restart usługi).
5. SSMS: `localhost,1433` z **przecinkiem**; „Certyfikat serwera zaufania" zaznaczyć.
6. `Migration checksum mismatch` → ktoś dotknął plików w `db/migration/**` (zakaz!).
7. OneDrive na Pulpicie potrafi blokować `target/` przy budowie — projekty trzymać poza.

## 7. Zarządzanie dostępami (bez panelu — celowo)

Kto należy do roli → **AD** (grupy `DZI-Portal-*`, zakłada zespół AD albo delegacja OU;
RSAT na stacji admina, NIE na serwerze). Co rola może → **SQL**:
`INSERT INTO tile_permissions (tile_id, ad_group, permission_level) SELECT id,'DZI-Portal-X','READ' FROM tiles WHERE code='...'`.
Serwer aplikacyjny: nic. Opóźnienia: cache grup 10 min + bilet Kerberos po relogowaniu.
Próg panelu admina (Etap 7): >15–20 kafelków albo zmiany częściej niż raz na miesiąc.

## 8. Otwarte tematy (stan na dziś)

- **Push na GitHub**: repo utworzone, tożsamość gita ustawiona; wypchnąć **v3**
  (commit: `feat: portal DZI — kompletny stan (Etapy 0-6, poprawki 31-37)`); repo PRYWATNE.
- Test workera na laptopie (pełny cykl SCRIPT) + test wyścigu 409 w 2 kartach + import XLSX.
- Osobna kopia demo (`dzi-portal-demo` na Pulpicie) — świadoma decyzja usera; ryzyko
  rozjazdu kopii odnotowane.
- Wdrożenie serwerowe: nic jeszcze nie stoi na DZI-APP01V; baza na serwer = zespół
  infrastruktury (instalatory offline; schowek RDP może być zablokowany). `prod,demo`
  pozwala przetestować cały Etap 1 (IIS/Kerberos/LDAP) zanim powstanie baza.
- Oferty złożone, nieodebrane: notatka-zgłoszenie dla infrastruktury (co postawić),
  plan JMeter `.jmx` (3 grupy wątków, obowiązkowy If-None-Match), zestaw SQL
  nadaj/odbierz/kto-co-może + treść wniosku o delegację OU, szablon Etapu 7.
- Świadomie poza zakresem z progami: circuit breaker LDAP, sufit pollingu w UI,
  typ skryptu `PY` (dziś wrapper .ps1), kolumna `category` kafelka (V4), nagłówki
  bezpieczeństwa w IIS (wymaga wyniesienia stylów z dataset.html), interfejs AuditWriter.

## 9. Konwencje współpracy z asystentem (utrzymywać!)

Po polsku, zwięźle. Kod dostarczany jako **paczki zip** w strukturze repo + README
z tabelą zmian i planem commitów; przed spakowaniem walidacja spójności (klamry z
odjęciem literałów, asercje obecności/zakazu, parse XML); **sędzią jest `mvn clean
verify` u usera** — kod powstaje bez kompilacji. Wersje pinowane (POI 5.3.0, Tabulator
6.5.2 zvendorowany, testcontainers 1.20.4). Przy wsparciu uruchomieniowym: jeden krok
naraz, dokładne kliknięcia, prośba o log/zrzut po każdym kroku. Uczciwe przyznawanie
się do błędów paczek (35/36/37 były błędami asystenta) i jawne zastrzeżenia granic.
