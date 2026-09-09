# dzi-portal — brief projektu (dla nowych sesji pracy)

Dokument orientacyjny: wklej do wiedzy projektu asystenta albo załącz na starcie
nowej rozmowy. **Stan na: 2026-09-08** (po WinSW i paczce „repo = serwer").
Repo jest publiczne — brief nie zawiera loginów użytkowników, adresów ani haseł;
te rzeczy żyją w bazie i na serwerze.

## 1. Co to jest

Wewnętrzny **portal kafelkowy DZI/ARiMR**: Java 21, Spring Boot 4.1, SQL Server
Express 2022, 3 moduły Maven (portal-common / portal-api / portal-worker). Kafelek =
moduł aplikacyjny (HTML/JS za strażnikiem `/apps`, ADR-0007). Dostępy: zbiór
{login, departament, `wszyscy`} vs `tile_permissions` (READ < EXECUTE < EDIT).
Wszystko audytowane (`audit_log`, append-only przez DENY od 2026-09-08).
Autor: Maciej Myśliwiec (nagłówki w plikach; wyjątki: `AUTORSTWO.md`).

## 2. Architektura produkcyjna (profil `declared`)

```
przeglądarka (stacja, Edge) ──NTLM──> IIS (arimr-app.zszik.pl, D:\portal\frontend, serwer DZI-APP01V)
   moduł C# PortalAuthUserHeader v3.0: X-Auth-User (login) + X-Auth-Dept (pierwsze OU z AD → np. dzi)
   URL Rewrite + ARR:  /api/*  → 127.0.0.1:8080
                       /apps/* → 127.0.0.1:8080     ← strażnik (ADR-0007)
portal-api — usługa Windows (WinSW 2.x, LocalSystem), profil declared, loopback 8080
   /api: RBAC + audyt pełny;  /apps: plik z D:\portal\apps\<code>\ po sprawdzeniu READ,
   403/404 = strony HTML, ETag/304, audyt selektywny (APP_OPEN / APP_DATA / APP_DENIED)
SQL Server Express (baza portal): tiles, tile_permissions, audit_log (+ gotowe, nieużywane:
   scripts/tasks/task_log, datasets*); widoki v_audit_log_pl, v_task_log_pl; Flyway v13
```

- Serwer bez internetu, praca przez RDP; testy przeglądarkowe **tylko ze stacji**
  (loopback check NTLM). Konsole: #1 zwykły PS (dawniej ręczna Java — od Fazy 10
  zbędna), #2 zwykły PS (sqlcmd, odczyty, kopiowanie z J:), #3 PS admin (IIS,
  usługa, zapis w `D:\portal`). **J: nie istnieje w sesji admina.**
- Katalogi: `D:\portal\api` (jar, `portal-api.exe` = WinSW, `portal-api.xml`,
  `config\application-declared.yml` z hasłem `portal_app`, logi), `D:\portal\apps`
  (moduły za strażnikiem), `D:\portal\frontend` (witryna IIS: powłoka + assets + `bin\`
  z DLL modułu), `D:\portal\archiwum`.
- Profil `prod` (wariant A: Kerberos + grupy AD + LDAP + gMSA) — droga powrotna, nieużywany.

## 3. Źródło prawdy

- **Repo:** `github.com/brungold/dzi-portal` (PUBLICZNE na czas prac — po zakończeniu
  przełączyć na private). Obieg: DOM (IntelliJ + git) → LAPTOP (świeży ZIP, `mvn clean
  package`, bez gita) → SERWER (artefakty przez J:). Wariant „pliki najpierw na
  serwer" zdarzył się trzy razy — wtedy repo dogania serwer tego samego tygodnia.
- **Od 2026-09-09 repo = serwer** (listing) dla: modułu IIS (v3.0), `web.config`,
  `portal-api.xml` (`deploy/winsw/portal-api.declared.xml`), struktury
  `config\application-declared.yml` (`.example`), całego `frontend/`.
  **Katalogi modułów kafelków (`D:\portal\apps\*`) są celowo TYLKO na serwerze** —
  decyzja 2026-09-08; repo ma procedurę i układ katalogów (`apps/README.md`).
  Konsekwencja: kopia zapasowa `D:\portal\apps` to jedyne zabezpieczenie modułów.
- Decyzje: `docs/adr/0001..0008`; historia paczek: `docs/etapy/`; eksploatacja:
  `docs/winsw-runbook.md`, `deploy/iis/INSTRUKCJA-MODUL-WINDOWS-AUTH.md`, `apps/README.md`.

## 4. Baza (stan 2026-09-08)

- Konta SQL: `portal_app` (aplikacja: `db_ddladmin` + reader + writer — Flyway),
  dwa konta robocze administratorów (reader + writer). Właściciel bazy = konto
  Windows administratora instancji (sysadmin). Żadne konto runtime nie jest `db_owner`.
- `audit_log`: DENY UPDATE/DELETE dla wszystkich trzech kont (`deploy/sql/prod-grants.sql`).
- Kafelki (4, wszystkie LINK, `display_order` co 10): `red-pisma-sprawy` (10),
  `m365_copilot_instrukcja_instalacji` (20), `epo-podpis` (30),
  `analiza_obecnosci_raporty_AUREA` (40). Uprawnienia: loginy imienne, poziom READ.
- Schemat wyłącznie przez migracje (V14+); dane (`tiles`, `tile_permissions`) —
  swobodnie SSMS/sqlcmd (`USE portal; GO` — SSMS startuje w `master`).
- Loginy AD w organizacji mają **mieszany format** (nazwisko.imię / imię.nazwisko /
  sufiksy cyfrowe). Nie zgadywać: weryfikacja w `v_audit_log_pl` po pierwszym wejściu
  osoby; zła pisownia = kafelek niewidoczny bez błędu.

## 5. Środowisko dev (laptop)

IntelliJ CE, SDK 21; SQL Express `localhost,1433`, baza `portal_dev`, login
`portal_dev` (jawna domyślka dev z `deploy/sql/dev-setup.sql`). Run config API:
`PortalApiApplication`, profil `dev`, working dir = `portal-api`. Seed dev używa nazw
grup AD (wariant A) — pod `dev,declared` widać 0 kafelków, dopóki seed nie dostanie
wiersza `wszyscy`. `mvn clean verify` — testy jednostkowe; `*IT` tylko z Dockerem.

## 6. Pułapki (przerobione — nie odkrywać ponownie)

1. PowerShell w `"..."` podstawia `$slowo` — hasła z `$` zostają okrojone; znaki
   bezpieczne `# ! @ *`; hasło w komendzie = w historii (`Clear-History`) i na ekranie.
2. `curl -H "X:"` USUWA nagłówek, `-H "X;"` wysyła pusty; NTLM: `--ntlm -u :`.
3. Po edycji `web.config` (Notatnik jako admin) zawsze `appcmd list config "portal"`
   dla `modules` i `rewrite/rules`. Kernel-mode auth ON.
4. Windows ukrywa rozszerzenia — `index` bez `.html` = plik do pobrania.
5. `<name>` w XML wyświetla się w oknach czatu jako `<n>` — pliki z serwera są
   poprawne; kopie z czatu porównać `fc.exe` przed użyciem.
6. `Get-Content` bez `-Encoding UTF8` krzaczy polskie znaki; sqlcmd: `-f 65001`
   + literały `N'...'`; do wklejania wyników `-W -w 250`.
7. WinSW 2.x: komendy bez myślników (`version`). Zegary: graceful 20 s < `stoptimeout` 30 s.
8. Katalog modułu na serwerze jest serwowany w całości: `app_v1.js`, `index_old.html`
   są dostępne pod swoim URL-em każdemu uprawnionemu — wersje robocze poza `/apps`.
9. Migracje Flyway: żadnych nagłówków autorskich w `db/migration/**` (checksumy).
10. `deploy/deploy.ps1` (wariant A) robi `robocopy /MIR` na witrynę — nie uruchamiać.

## 7. Otwarte tematy (kolejność ważności)

1. **Biuro:** Faza 12 (restart serwera w cichej porze, `BackConnectionHostNames`).
   Test A i test 403 — wykonane 2026-09-09 (strażnik ma komplet potwierdzeń).
2. **Porządki w katalogach modułów (Piotr):** ReD — 279 MB JSON, skrypt SQL, generator
   i stara migawka `data\detailss_20260817` poza witrynę (`D:\portal\praca\`);
   AUREA — kasacja `_v1..v4`, `_old`; układ docelowy w `apps/README.md`.
3. **Paczka kodu #2 (jar):** audyt `data/` + `pdf/xml/txt` w `AppsAuditPolicy`,
   baner `declared` (tekst o module v2), 20 s w `application.yml`, komentarz
   `extensionattribute12` → OU. Osobno, po przebiegu testów: `DevSecurityConfiguration`
   → `@Profile("dev")` z ogrodzeniem wariantu A w `SecurityConfig`.
4. **Decyzje administratora:** próg blokady >3 loginów z adresu (fałszywe alarmy
   przy NTLM) — `application-declared.yml.example`; podstawa danych innych
   departamentów w module AUREA; konto usługi (LocalSystem → NetworkService).
5. **Panel administracyjny** (Faza A tylko do odczytu: wszystkie kafelki, kto widzi,
   co widzi dana osoba, wyświetlenia z audytu, loginy nieznane w audycie; Faza B:
   nadawanie/odbieranie uprawnień z `@Audited`, CSRF przez nagłówek + JSON,
   nietykalny kafelek `administracja`; potem zdjęcie `db_datawriter` z kont roboczych).
   Rewiduje „bez panelu, celowo" — ADR-0009.
6. Hasło `portal_app` poza plikiem jawnym; TLS (dziś HTTP); retencja audytu przez
   Task Scheduler; strefa Intranet przez GPO; repo → private po zakończeniu prac.

## 8. Konwencje współpracy z asystentem (utrzymywać!)

Po polsku, zwięźle, **jeden krok naraz**, po każdym kroku log/zrzut (przy
niepewności: tekst, nie zdjęcie). Wyjaśnienie „po ludzku" PRZED komendą, etykieta
miejsca `[STACJA] [SERWER #2/#3] [DOM] [LAPTOP]`. Kod jako paczki ZIP w strukturze
repo + README z tabelą zmian; walidacja spójności przed spakowaniem; **sędzią jest
`mvn clean verify` u Maćka** — kod powstaje bez kompilacji u asystenta. Pliki
istniejące na serwerze idą do repo przez J:, nie przez okno czatu. Nagłówki
autorskie w plikach źródłowych — zachowywać; `AUTORSTWO.md` — nie wracać do tematu.
Uczciwe przyznawanie się do błędów paczek i jawne zastrzeganie granic wiedzy.
