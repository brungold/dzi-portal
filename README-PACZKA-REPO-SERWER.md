# Paczka „repo = serwer" (2026-09-08)

Cel: repo `brungold/dzi-portal` znów opisuje to, co faktycznie stoi na DZI-APP01V.
**Zero zmian na serwerze, zero zmian w kodzie Javy** — sama dokumentacja i konfiguracja.
Kod Javy (audyt `data/`, baner, 20 s) jedzie osobno jako paczka kodu #2.

Rozpakować **zawartość** katalogu paczki na katalog repo [DOM] (`Copy-Item "$paczka\*"
-Recurse -Force`, z gwiazdką — bez niej dubel struktury). Pliki oznaczone **(SERWER)**
w tabeli są w paczce, ale ich źródłem prawdy jest serwer — przed commitem porównać.

## Tabela zmian

| Plik w repo | Zmiana | Uwaga |
|---|---|---|
| `deploy/iis/AuthUserHeaderModule.cs` | v2.0 → **v3.0** (departament z OU) | **(SERWER)** porównać z plikiem na serwerze — oczekiwana **jedna** różnica: w komentarzu `<summary>` usunięto nazwiska osób z próbek DN (repo publiczne). Tę samą zmianę nanieść na kopię serwerową (komentarz — DLL bez zmian, bez rekompilacji) |
| `deploy/iis/web.config` | dołożona reguła `portal-apps-proxy`, komentarze pod v3.0 | **(SERWER)** porównać: jedyna dozwolona różnica = komentarze |
| `deploy/iis/INSTRUKCJA-MODUL-WINDOWS-AUTH.md` | sekcja „Departament (v3.0)", `csc.exe` z `/reference:System.DirectoryServices.dll`, test whoami z departamentem, nowe potknięcia | |
| `deploy/iis/apps-rewrite-rule.md` | nagłówek „dokument historyczny" (reguła scalona do web.config) | |
| `deploy/winsw/portal-api.declared.xml` | **nowy** — konfiguracja usługi z serwera (bez sekretów), `<name>` poprawione | **(SERWER)** porównać z `D:\portal\api\portal-api.xml` |
| `deploy/winsw/README-WinSW.md` | wariant `declared` jako główny (instalacja, zegary, testy), gMSA opisane jako wariant A | |
| `deploy/declared/application-declared.yml.example` | **nowy** — szablon `config\application-declared.yml` (hasło = `<UZUPELNIJ>`), wyjaśnienie wcięcia `lifecycle`, opcjonalne progi limitera | |
| `deploy/sql/prod-grants.sql` | przepisany pod realne konta: 3 × DENY + dwie weryfikacje + REVOKE | = to, co wykonano na serwerze 2026-09-08 |
| `deploy/deploy.ps1` | domyślna ścieżka `D:\portal\frontend`, ostrzeżenie „nie uruchamiać" (`robocopy /MIR`) | wariant A |
| `.gitignore` | dane modułów (`apps/**/*.csv`, `*.xlsx`, `apps/*/data/`, `apps/*/dane*/`), `config/application-declared.yml`, wersje robocze `*_v1.js`, `*_old.*` | |
| `apps/README.md` | tabela czterech modułów, konwencje (dane osobno, bez wersji roboczych, dane innych departamentów), procedura | |
| `docs/adr/0008-departament-z-ou-modul-v3.md` | **nowy** — decyzja o źródle departamentu (OU, nie `extensionattribute12`) | |
| `docs/winsw-runbook.md` | **nowy** — operacje usługi, zegary, testy odbiorowe, kompromisy, pułapki | z załącznika Fazy 10, bez loginów |
| `docs/BRIEF-PROJEKTU.md` | przepisany, stan 2026-09-08, bez loginów osób trzecich | |
| `README.md` | architektura produkcyjna, ADR 0006–0008, poprawione „Znane punkty uwagi" (pkt 5 był nieprawdziwy od strażnika), sekcja historii | |
| `docs/etapy/README-PACZKA-PORZADKOWA.md`, `docs/etapy/README-PACZKA-STRAZNIK.md` | przeniesione z korzenia, nazwisko testera zanonimizowane | usunąć oryginały z korzenia (krok 3) |

## Co skopiować z serwera do repo (przez J:, nie z okna czatu)

| Serwer | Repo | Uwaga |
|---|---|---|
| `D:\portal\apps\epo-podpis\{index.html, app.js, module.css}` | `apps/epo-podpis/` | 3 pliki, bez danych |
| `D:\portal\apps\m365_copilot_instrukcja_instalacji\*` (9 plików) | `apps/m365_copilot_instrukcja_instalacji/` | **przejrzeć 6 PNG i index.html** — zrzuty ekranu bez loginów/nazwisk (repo publiczne) |
| `D:\portal\apps\analiza_obecnosci_raporty_AUREA\{index.html, app.js, module.css, *.txt}` | `apps/analiza_obecnosci_raporty_AUREA/` | **BEZ** `dane_Aurea\`, BEZ `*_v1..v4.*`, BEZ `index_v1.html`; `.txt` przeczytać (Piotra notatki — bez danych?) |
| `D:\portal\frontend\web.config` | `deploy/iis/web.config` | porównaj z wersją z paczki; ma być tożsame poza komentarzami |
| źródło `AuthUserHeaderModule.cs` z serwera | `deploy/iis/AuthUserHeaderModule.cs` | porównaj z wersją z paczki; ma być identyczne |
| `D:\portal\api\portal-api.xml` | `deploy/winsw/portal-api.declared.xml` | porównaj z wersją z paczki; ma być identyczne |
| `D:\portal\api\config\application-declared.yml` | **NIE kopiować** (hasło) | szablon `.example` jest w paczce |

Porównanie [DOM lub STACJA, po skopiowaniu na dysk]:
`fc.exe /L "<z serwera>" "<z repo po rozpakowaniu>"` — `FC: no differences encountered`
albo lista różniących się linii (dla `web.config` dopuszczalne tylko linie komentarzy).

## Kroki [DOM]

1. Świeży stan repo (`git status` czysty, `git pull`).
2. Rozpakowanie paczki na repo (`Copy-Item "$paczka\*" -Recurse -Force`).
3. Porządek w korzeniu:
   `git rm README-PACZKA-PORZADKOWA.md README-PACZKA-STRAZNIK.md`
   (kopie z nagłówkiem „przeniesione" są już w `docs/etapy/`).
4. Pliki z serwera (tabela wyżej) w podane ścieżki; trzy porównania `fc.exe`.
5. `git status` — w `apps/` mają się pojawić WYŁĄCZNIE pliki kodu (`.gitignore`
   powinien już blokować CSV/`dane_Aurea`; jeśli `git status` pokazuje CSV — stop,
   nie commitować).
6. `git add -A && git diff --cached --stat` — spodziewane ok. 20 plików zmienionych/nowych,
   2 usunięte z korzenia.
7. Commit i push.

## Plan commitów

Jeden commit wystarczy (paczka jest spójna), sugerowany komunikat:

```
docs+deploy: repo = serwer (modul IIS v3.0 + ADR-0008, web.config, WinSW declared,
szablon config, prod-grants pod realne konta, 3 moduly, .gitignore na dane)
```

Alternatywnie dwa: (1) `deploy/`, `docs/`, `README.md`, `.gitignore`; (2) `apps/`
— jeśli przegląd PNG/txt przeciągnie się na inny dzień.

## Walidacja przed spakowaniem (wykonana)

- XML: `web.config`, `portal-api.declared.xml` — parsowanie OK (`<name>` obecne).
- YAML: `application-declared.yml.example` — parsowanie OK.
- Brak loginów osób trzecich w plikach paczki (grep po znanych nazwiskach: 0).
- Brak haseł (`password:` tylko z `<UZUPELNIJ>`).
- Nagłówki autorskie: nowe pliki `.sql`, `.cs`, `.md` (ADR, runbook) — obecne;
  `.example` i `.gitignore` — nagłówek w komentarzu / brak (jak dotąd w repo).

## Poza zakresem tej paczki (świadomie)

- `frontend/` — czeka na listing `D:\portal\frontend` (powłoka Piotra vs commit 37).
- `apps/red-pisma-sprawy` — czeka na listing (agregaty, `data/`, skrypt generujący).
- Kod Javy — paczka kodu #2.
- Kasacja wersji roboczych AUREA na serwerze — po potwierdzeniu Piotra [SERWER #3].
