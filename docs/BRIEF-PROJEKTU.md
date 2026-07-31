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
klient (przeglądarka / skrypt PS) ──X-Auth-User: login──> API (loopback + lista CIDR)
                                                            │ user_departments (SQL) → grupy
                                                  SQL Server ── portal-worker → powershell.exe
```

Zasada, której **nie wolno upraszczać z powrotem**: klient deklaruje wyłącznie login.
Departament, przynależność i uprawnienia wyprowadza serwer. Model „klient przysyła
departament, serwer sprawdza czy istnieje" oraz endpoint „verify" interpretowany przez
skrypt kliencki zostały odrzucone na stałe — decyzja wykonywana na maszynie użytkownika
nie jest kontrolą dostępu (OWASP A01 + A04 + A09). Uzasadnienie: `docs/adr/0003`.

Granice tego trybu: tożsamość jest deklaracją, nie dowodem — chroni przed „wpiszę
departament i wejdę" oraz przed nieznanym loginem (fail-closed), **nie chroni** przed
podszyciem się pod znany login z zaufanej podsieci. Stąd zakaz: żadnych danych osobowych,
finansowych ani kadrowych za kafelkami w tym trybie. Osobno: statyczne powłoki HTML
kafelków APLIKACJA widzi każdy uwierzytelniony — treść wrażliwa mieszka za `/api`.

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
| — | ADR-0004: fizyczne usunięcie profilu `demo` (9 plików); `prod,demo` nie jest już dostępne jako tryb testowy bez bazy |

## 5. Środowisko dev (laptop)

- SDK **corretto-21** / Temurin 21; Maven 3.9.x. Projekt otwierany przez `pom.xml`.
- SQL Express: `localhost,1433` (tryb mieszany, TCP włączone), baza `portal_dev`,
  login `portal_dev` (jawna domyślka dev — NIE sekret prod).
- Profile uruchomieniowe: `dev` (klasycznie) albo `dev,declared` (tryb deklarowany).
  Working directory = katalog modułu (`portal-api` / `portal-worker`) — ścieżki
  do frontendu i skryptów są **względne**.
- W `dev,declared` dev-fallback (`tester`) **nie działa** — deklarację trzeba przysłać
  jawnie nagłówkiem. Bez wpisów w `user_departments` każdy login dostaje 403 (poprawnie).
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

## 7. Znane błędy do naprawienia (aplikacja działa, testy nie)

- `@WebMvcTest` po modularyzacji Boota 4.1 →
  `org.springframework.boot.webmvc.test.autoconfigure` (cztery pliki testowe).
- `TilesConfiguration`, `TileRepository`, `TilePermissionRepository` — brak `public`,
  więc testy z innych pakietów ich nie widzą.
- Skutek: `mvn clean verify` **nie jest zielony**; do wdrożenia używamy
  `mvn clean install -Dmaven.test.skip=true`. To błędy testowe, nie aplikacyjne.

## 8. Zarządzanie dostępami (bez panelu — celowo)

- **Wariant A:** kto należy do roli → AD (grupy `DZI-Portal-*`). Co rola może → SQL
  (`tile_permissions`).
- **Wariant deklarowany:** kto czym jest → tabela `user_departments` (prowizja przez
  `deploy/declared/export-user-departments.ps1` — eksport z AD wykonywany przez
  administratora ze stacji, nie w runtime). Co rola może → SQL (`tile_permissions`).
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
- **Rozstrzygnięte 31.07.2026: tożsamość deklarowana przez klienta (skrypt PowerShell).**
  Witryna IIS **bez Windows Authentication** — wyłącznie terminator TLS i reverse proxy;
  nagłówek `X-Auth-User` przychodzi od klienta i IIS go **nie nadpisuje** (odwrotnie niż
  w wariancie A). ARR: „Include TCP port from client IP" MUSI być odznaczone — inaczej
  limiter anomalii kluczuje po porcie efemerycznym i nigdy nie zadziała.
  Powrót do wariantu A pozostaje otwarty; progi rewizji w ADR-0003.
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
