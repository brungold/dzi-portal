# dzi-portal — brief projektu (dla nowych sesji pracy)

Dokument orientacyjny: wklej do wiedzy projektu Claude (Project knowledge) albo załącz
na starcie nowej rozmowy. **Stan na: 2026-07-31**, po commitach 1–38 (komplet-v4 bez demo).

## 1. Co to jest

Wewnętrzny **portal kafelkowy DZI** (Java 21, Spring Boot 4.1, SQL Server, 3 moduły Maven:
portal-common / portal-api / portal-worker). Kafelki = aplikacje HTML (LINK), zbiory danych
(DATASET: Tabulator, edycja z wersjonowaniem, import XLSX) i zadania (SCRIPT: skrypty PS1
wykonywane przez osobny proces worker z kolejki w SQL). Uprawnienia zawsze w tabeli
`tile_permissions` (READ < EXECUTE < EDIT), egzekwowane `@PreAuthorize` + `AccessFacade`.
Wszystko audytowane, rejestr append-only. Autor: Maciej Myśliwiec (nagłówki w plikach;
wyjątek: `db/migration/**` — patrz `AUTORSTWO.md`).

## 2. Dwa warianty tożsamości

Logika, RBAC i audyt są **wspólne**. Różnica jest wyłącznie w tym, skąd bierze się login
i przynależność.

**Wariant A — z AD (docelowy, profil `prod`):**

```
przeglądarka ──Kerberos──> IIS (portal.dzi.pl) ──X-Auth-User──> API (127.0.0.1:8080, gMSA)
                                                                  │ LDAPS 636 → grupy AD
                                                        SQL Server ── portal-worker → powershell.exe
```

**Wariant deklarowany (profil `declared`, ADR-0003)** — gdy integracja z katalogiem
w runtime jest organizacyjnie niedostępna:

```
klient (przeglądarka / skrypt PS) ──X-Auth-User + X-Auth-Dept──> API (loopback + CIDR)
                                                            │ uprawnienia = {login, dept, wszyscy}
                                                            │ vs tile_permissions
                                                  SQL Server ── portal-worker → powershell.exe
```

**ADR-0005 (03.08.2026):** departament jest DEKLAROWANY drugim nagłówkiem (skrót
z AD `extensionattribute12`: dzi/dag/dpb…), serwer niczego nie sprawdza w katalogu
ani w bazie. Powód: skala (4 tys. centrala → 11,5 tys. organizacja) czyni rejestr
`user_departments` i jego synchronizację z kadrami kosztem nie do przyjęcia.
Decyzję o dostępie do każdego zasobu API nadal podejmuje SERWER (porównanie
z `tile_permissions`) — po stronie klienta nie ma żadnej logiki dostępu.

Granice, nazwane wprost: kafelek jest widoczny dla każdego, kto zna adres portalu
i wpisze właściwy skrót departamentu; `wszyscy` = dosłownie każdy, kto dotrze do
portalu. `tile_permissions` PORZĄDKUJE widoczność, nie chroni danych. Warunki
przyjęcia: sieć wewnętrzna (monitorowana), ZAKAZ danych osobowych, finansowych
i kadrowych za kafelkami. Kompensacje z ADR-0003 zostają: loopback/CIDR, limiter
anomalii, audyt każdej deklaracji (login, departament, adres). Osobno: statyczne
powłoki HTML kafelków APLIKACJA widzi każdy — treść wrażliwa mieszka za `/api`.

## 3. Źródło prawdy

- **Kod:** to repozytorium (`master`). Paczka odniesienia: `dzi-portal-komplet-v4-bez-demo`
  = commity 1–38, z fizycznie usuniętym profilem `demo`. Wszystkie wcześniejsze archiwa
  (komplet-v2/v3, demo-33, frontend-34/37, fix-35/36) są NADPISANE.
- **Decyzje:** `docs/adr/0001..0004`; per-commit: `docs/etapy/README-*.md`.
- **Runbooki:** `docs/deklaracja-runbook.md` (wariant deklarowany — ten realizujemy),
  `docs/etap1-runbook.md` + `docs/etap6-runbook.md` (wariant A; kroki AD nie stosują się).
- Wariant zapasowy PostgreSQL: osobne archiwum (NIE nakładka). Blueprint .NET:
  `szablon-opcja1-aspnetcore.md` (dokument, bez kodu).

## 4. Historia w pigułce (commity)

| # | Zakres |
|---|---|
| 1–30 | Etapy 0–6: fundament+audyt → IIS/Kerberos → kafelki RBAC → zadania+worker → zbiory+XLSX → ETag/hardening/deploy |
| 31–32 | przeglądy seniorskie: izolacja testów, SID-y zamiast nazw grup lokalnych, 400 dla złych wejść, pool schedulera=2 |
| 33 | profil `demo` (portal bez bazy) — **następnie usunięty**, patrz ADR-0004 |
| 34+37 | strona główna renderowana z `/api/tiles`, identyfikacja ARiMR, kolor kafelka koduje rodzaj, CSP bez inline JS, zero CDN |
| 35 | fix: BOM testcontainers w pomie nadrzędnym (bez niego Maven nie wczytywał modułów) |
| 36 | fix: checksumy Flyway — ZAKAZ nagłówków autorstwa w `db/migration/**` |
| 37 | fix: wzorce statyki dev (`/css/**`, `/js/**`, `/apps/**`) |
| 38 | **profil `declared`** (ADR-0003): filtr nagłówka, limiter anomalii, resolver grup z SQL, konfiguracja security, migracja V4 (`user_departments`), `application-declared.yml`, testy, skrypt eksportu z AD, runbook |
| 41 | **deklarowany departament** (ADR-0005): drugi nagłówek `X-Auth-Dept`, uprawnienia {login, dept, wszyscy} bez rejestru użytkowników; okno z polem departamentu; klient `portal-client.ps1`; usunięty `DeclaredDbGroupResolver`; `user_departments`+V4 zostają nieużywane |
| — | ADR-0004: fizyczne usunięcie profilu `demo` (9 plików); `prod,demo` nie jest już dostępne jako tryb testowy bez bazy |

## 5. Środowisko dev (laptop)

- SDK **corretto-21** / Temurin 21; Maven 3.9.x. Projekt otwierany przez `pom.xml`.
- SQL Express: `localhost,1433` (tryb mieszany, TCP włączone), baza `portal_dev`,
  login `portal_dev` (jawna domyślka dev — NIE sekret prod).
- Profile uruchomieniowe: `dev` (klasycznie) albo `dev,declared` (tryb deklarowany).
  Working directory = katalog modułu (`portal-api` / `portal-worker`) — ścieżki
  do frontendu i skryptów są **względne**.
- W `dev,declared` dev-fallback (`tester`) **nie działa** — deklarację (login
  + departament) trzeba przysłać jawnie nagłówkami.
- **Tryb bez bazy nie istnieje.** Profil `demo` usunięty (ADR-0004) — start bez bazy
  kończy się fail-fast. To świadomy koszt tej linii źródeł.
- URL: `http://localhost:8080/index.html`.

## 6. Klasyczne pułapki (już przerobione — nie powtarzać diagnozy)

1. Rozpakowywanie zipów: wrzucać **zawartość** folderu z archiwum, nie sam folder.
2. Pliki `.java` w `resources` zamiast `src/main/java` → ClassNotFound.
3. Brak aktywnego profilu → „Failed to configure a DataSource".
4. `Connection refused :1433` → SQL Express ma TCP wyłączone fabrycznie.
5. SSMS: `localhost,1433` z **przecinkiem**; zaznaczyć zaufanie certyfikatowi.
6. `Migration checksum mismatch` → ktoś dotknął plików w `db/migration/**` (zakaz!).
7. OneDrive na Pulpicie potrafi blokować `target/` przy budowie.
8. W tabeli `tiles` kolumna nazywa się **`name`**, nie `title`.
9. „Apache" w tym projekcie = **Apache POI** (biblioteka w JAR-ze), nie Apache HTTP Server.

## 7. Testy — stan faktyczny (zweryfikowany w master 31.07.2026)

- Relokacja `@WebMvcTest` (Boot 4.1) jest już naniesiona — cztery pliki testowe
  importują `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`.
- `TilesConfiguration`, `TileRepository`, `TilePermissionRepository` są `public`.
- Oczekiwanie: `mvn clean verify` zielony (klasy `*IT` bez Dockera pomijają się same).
  Przy niespodziewanym FAIL wdrożenia nie blokować:
  `mvn clean package -Dmaven.test.skip=true` i wrócić z logiem.

## 8. Zarządzanie dostępami (bez panelu — celowo)

- **Wariant A:** kto należy do roli → AD (grupy `DZI-Portal-*`). Co rola może → SQL
  (`tile_permissions`).
- **Wariant deklarowany (ADR-0005):** kto czym jest → DEKLARACJA klienta (login
  + skrót departamentu z AD `extensionattribute12`). Co kto może → SQL
  (`tile_permissions.ad_group` = skrót departamentu ALBO login — można mieszać,
  np. cały `dag` + dwie osoby imiennie; `wszyscy` = kafelek publiczny).
  Portal nie prowadzi rejestru użytkowników; `export-user-departments.ps1`
  pozostaje w repo jako narzędzie nieaktywnego wariantu z rejestrem.
- Serwer aplikacyjny: nic. Próg panelu admina (Etap 7): >15–20 kafelków albo zmiany
  częściej niż raz na miesiąc.

## 9. Otwarte tematy (stan na dziś)

- **Wdrożenie serwerowe w toku**, wariant deklarowany, samodzielnie (bez zespołu
  infrastruktury). Przygotowane: dysk danych i układ katalogów, JDK 21, SQL Express
  (instancja domyślna, tryb mieszany, TCP 1433), baza i login aplikacyjny, IIS
  z URL Rewrite + ARR (proxy włączone). Pozostało: budowa paczki i przeniesienie,
  rozłożenie plików, `application-declared.yml`, start z konsoli, usługa WinSW,
  witryna IIS + reguła rewrite `/api`, weryfikacja, dane (`user_departments`, `tiles`,
  `tile_permissions`), worker, test restartu. **Dziennik wdrożenia prowadzony poza
  repozytorium** (dyscyplina bus-factor-2: konta imienne, wpis po każdej fazie).
- **Rozstrzygnięte 03.08.2026 (ADR-0005): deklarowany departament.** Drugi nagłówek
  `X-Auth-Dept`; uprawnienia żądania {login, dept, wszyscy}; bez rejestru
  użytkowników. Warunki: sieć wewnętrzna, zakaz danych wrażliwych. Paczka 41.
- **Rozstrzygnięte 31.07.2026: tożsamość deklarowana przez klienta (skrypt PowerShell).**
  Witryna IIS **bez Windows Authentication** — wyłącznie terminator TLS i reverse proxy;
  nagłówek `X-Auth-User` przychodzi od klienta i IIS go **nie nadpisuje** (odwrotnie niż
  w wariancie A). ARR: „Include TCP port from client IP" MUSI być odznaczone — inaczej
  limiter anomalii kluczuje po porcie efemerycznym i nigdy nie zadziała.
  Powrót do wariantu A pozostaje otwarty; progi rewizji w ADR-0003.
  Przeglądarka również deklaruje (paczka 40): `frontend/js/declared-identity.js`
  sonduje `/api/whoami`, przy 401 pokazuje okno deklaracji i dokłada `X-Auth-User`
  do każdego wywołania `/api`; w wariancie A / dev (sonda 200) jest przezroczysty.
- Publikacja zasobów: zbiory XLSX (kafelki DATASET), statyczne aplikacje HTML (LINK),
  skrypty PS1/Python (SCRIPT, Python przez wrapper .ps1). Kandydat: arkusz ~293 rekordów
  systemów/modułów jako kafelek DATASET.
- Demo dla kierownictwa, potem formalne zatwierdzenie.
- Świadomie poza zakresem z progami: circuit breaker LDAP, sufit pollingu w UI, typ
  skryptu `PY`, kolumna `category` kafelka, nagłówki bezpieczeństwa w IIS, interfejs
  `AuditWriter`. Odłożone w ADR-0003: hasła lokalne (argon2id) i mTLS — wracają przy
  pierwszym kafelku z danymi wrażliwymi albo pierwszym incydencie podszycia.

## 10. Konwencje współpracy z asystentem (utrzymywać!)

Po polsku, zwięźle. Kod dostarczany jako **paczki zip** w strukturze repo + README
z tabelą zmian i planem commitów; przed spakowaniem walidacja spójności (klamry
z odjęciem literałów, asercje obecności/zakazu, parse XML/YAML, nagłówki autorstwa
w plikach nie-migracyjnych); **sędzią jest build u usera** — kod powstaje bez kompilacji.
Wersje pinowane (POI 5.3.0, Tabulator 6.5.2 zvendorowany, testcontainers 1.20.4).
Przy wsparciu uruchomieniowym: jeden krok naraz, dokładne kliknięcia, prośba o log/zrzut
po każdym kroku. Uczciwe przyznawanie się do błędów paczek i jawne zastrzeżenia granic.

---

*Autor: Maciej Myśliwiec, 2026. Szczegóły: `AUTORSTWO.md`.*
