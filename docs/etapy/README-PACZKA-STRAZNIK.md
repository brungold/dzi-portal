> Przeniesione z korzenia repo do `docs/etapy/` 2026-09-08 (historia paczki z 2026-08-13).

# Paczka: strażnik /apps (ADR-0007) — RBAC i audyt dla plików modułów

Zamyka ryzyko nazwane przy wdrożeniu ReD: pliki modułów (w tym CSV z danymi
osobowymi) wydawał IIS każdemu kontu domeny. Po tej paczce wydaje je aplikacja —
po sprawdzeniu `tile_permissions`, z 403 + DENIED w audycie dla nieuprawnionych.

## Tabela zmian

| Plik | Typ | Co i po co |
|---|---|---|
| `portal-api/.../apps/AppsController.java` | NOWY | serce strażnika: code z 1. segmentu ścieżki → `AccessFacade.canRead` → plik strumieniowo (ETag/304) albo 403/404 jako strona HTML |
| `.../apps/PathSafety.java` | NOWY | brama ścieżek: normalizacja + twarde „wewnątrz bazy"; traversal ⇒ empty ⇒ 404 |
| `.../apps/AppsAuditPolicy.java` | NOWY | D3: audyt HTML + dane (csv/xlsx/json) + każde ≥400; css/js/img poza rejestrem |
| `.../apps/AppsMediaTypes.java` | NOWY | Content-Type po rozszerzeniu; text/* zawsze z charset=utf-8 (ogonki w CSV) |
| `.../apps/AppsErrorPages.java` | NOWY | strony 403/404 w barwach portalu (moduły otwiera człowiek, nie API) |
| `.../apps/AppsAuthenticationEntryPoint.java` | NOWY | 401 jako HTML dla /apps/** |
| `.../apps/AppsProperties.java` + `AppsConfiguration.java` | NOWE | `portal.apps.base-dir` |
| `.../infrastructure/audit/AuditPolicy.java` | NOWY | interfejs decyzji o wpisie; `ALWAYS` = zachowanie dotychczasowe |
| `.../infrastructure/audit/AuditFilter.java` | ZMIANA | konstruktor z polityką; stary 1-arg deleguje do ALWAYS (test bez zmian); 304 i ścieżka wyjątku jak dotąd |
| `.../infrastructure/web/WebFiltersConfiguration.java` | ZMIANA | correlation+sameorigin także `/apps/*`; DRUGA rejestracja AuditFilter dla `/apps/*` z AppsAuditPolicy |
| `.../infrastructure/security/SecurityConfig.java` | ZMIANA | nowy łańcuch `/apps/**` @Order(2) (Loopback filtr, HTML 401); fallback deny-all → @Order(3) |
| `.../infrastructure/security/DeclaredSecurityConfiguration.java` | ZMIANA | bliźniaczy łańcuch `/apps/**` @Order(-9) dla profilu declared |
| `.../infrastructure/security/DevSecurityConfiguration.java` | ZMIANA | `/apps/**` USUNIĘTE z permitAll — strażnik działa też w dev |
| `application-declared.yml` / `application-dev.yml` | ZMIANA | `portal.apps.base-dir`: `D:/portal/apps` / `../apps` |
| `.../test/.../apps/PathSafetyTest.java` + `AppsControllerTest.java` | NOWE | traversal, index dla katalogu, redirect bez ukośnika, 403-HTML, 404, ETag/304 |
| `apps/red-pisma-sprawy/` (index.html, app.js, module.css) | NOWE w repo | moduł ReD w docelowej lokalizacji; **module.css z łatką `max-width:100%`** (koniec poziomego paska) |
| `apps/README.md` | NOWY | STAŁA PROCEDURA dokładania modułów (repo→J:→serwer→SQL) |
| `frontend/assets/css/portal-dzi.css`, `frontend/assets/js/portal-dzi.js` | NOWE w repo | powłoka Piotra — repo dogania serwer |
| `docs/adr/0007-straznik-apps.md` | NOWY | decyzja + warianty odrzucone + progi rewizji |
| `deploy/iis/apps-rewrite-rule.md` | NOWY | reguła rewrite + obowiązkowa weryfikacja appcmd |
| `deploy/sql/apps-nowy-modul.sql` | NOWY | szablon rejestracji modułu |

`git rm`: **brak** (frontend/apps nigdy nie było w repo). CSV **nie ma i nie może być** w repo.
AUTORSTWO.md: dopisać wiersz — „moduł ReD + powłoka portal-dzi (assets) — autorstwo Piotr Zwonik".

## Wdrożenie — kolejność (jeden krok naraz, po każdym log/zrzut)

1. **[DOM]** zip na repo, diff, commit
   `feat: straznik /apps — RBAC i audyt plikow modulow (ADR-0007)`, push.
2. **[LAPTOP]** świeży ZIP z GitHuba, `mvn clean package "-DskipTests"`
   (`clean` obowiązkowo — stare zasoby żyją w target). Wynikowy
   `portal-api/target/portal-api-*.jar` przez J: na serwer.
3. **[SERWER #3]** dysk:
   ```powershell
   New-Item -ItemType Directory -Force -Path D:\portal\apps
   Move-Item D:\portal\frontend\apps\red-pisma-sprawy D:\portal\apps\
   Remove-Item -Recurse D:\portal\frontend\apps          # gant i form_upr odchodzą razem z katalogiem
   Test-Path D:\portal\frontend\apps                     # MUSI być False (test 5!)
   ```
   Nadpisz `D:\portal\apps\red-pisma-sprawy\module.css` wersją z paczki (łatka max-width).
   NTFS (odczyt dla wszystkich lokalnych, zapis tylko admini; dziedziczenie od D:\ zwykle
   to daje — kontrola: `icacls D:\portal\apps`).
4. **[SERWER #3]** `web.config` wg `deploy/iis/apps-rewrite-rule.md` (Notatnik jako
   administrator!) + weryfikacja appcmd — obie reguły widoczne.
5. **[SERWER #3]** wyrównanie repo=serwer dla powłoki: nadpisz
   `D:\portal\frontend\assets\css\portal-dzi.css` wersją z paczki (różnica: jedna
   zdublowana linia mniej; `portal-dzi.js` identyczny — bez zmian).
6. **[SERWER #1]** stary jar → `D:\portal\archiwum\jar_arch\portal-api-2026-08-13.jar`,
   nowy jako `portal-api.jar`, start:
   `& "$env:JAVA_HOME\bin\java.exe" "-Dfile.encoding=UTF-8" -jar portal-api.jar --spring.profiles.active=declared`
   W banerze declared bez zmian; brak błędów startu.
7. **[SERWER #2]** baza: NIC do zrobienia dla ReD (kafelek id=1 i 2 uprawnienia już są).
8. **[STACJA]** testy akceptacyjne — sekcja niżej.

## Testy akceptacyjne (kryteria zamknięcia)

| # | Test | Oczekiwane |
|---|---|---|
| 1 | uprawniony: `http://arimr-app.zszik.pl/apps/red-pisma-sprawy/` | dashboard, dane, login w rogu |
| 2 | uprawniony, bez ukośnika `/apps/red-pisma-sprawy` | przekierowanie na wersję z `/` |
| 3 | **nieuprawniony** (konto spoza 2 wpisów): ten sam URL | strona „Brak dostępu", 403 |
| 4 | **nieuprawniony**: bezpośredni link do `.../red-pisma-sprawy.csv` | 403, plik niepobrany |
| 5 | `Test-Path D:\portal\frontend\apps` | **False** |
| 6 | `v_audit_log_pl` po 3–4 | DENIED z loginem próbującego, path modułu |
| 7 | audyt po teście 1 | wpisy APP_OPEN i APP_DATA; **brak** wpisów o css/js/logo |
| 8 | strona główna, `/api/tiles`, `/api/whoami`, moduł Raport licencji | bez zmian |

Test 3–4 najlepiej kontem testera spoza listy uprawnionych — to dosłownie scenariusz
„X przesłał link Y-owi". Kontrola audytu: [SERWER #2]
`sqlcmd -S localhost -d portal -E -Q "SELECT TOP 15 ts_pl, username, path, status, http_status FROM v_audit_log_pl ORDER BY id DESC"`

## Rollback (gdyby coś stanęło)

Kolejność odwrotna: stary jar z archiwum → usunięcie reguły apps z web.config
(+ appcmd) → `Move-Item D:\portal\apps\red-pisma-sprawy D:\portal\frontend\apps\`
(katalog frontend\apps utworzyć). Baza nietknięta — kafelek działa w obu światach.

## Po wdrożeniu

- Dodanie pozostałych loginów (gdy Piotr poda + potwierdzenie formatu per osoba):
  `INSERT INTO tile_permissions (tile_id, ad_group, permission_level) SELECT 1, v.l, 'READ' FROM (VALUES (N'...'),(N'...')) v(l)`
- U Piotra: `cache:'no-store'` → `'no-cache'` w app.js (ETag zacznie oddawać 304 na CSV).
- Repo → private po zakończeniu prac. Faza 10 (WinSW) — następna w kolejce.
