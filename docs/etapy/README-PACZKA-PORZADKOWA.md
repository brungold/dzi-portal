> Przeniesione z korzenia repo do `docs/etapy/` 2026-09-08 (historia paczki z 2026-08).

# Paczka porządkowa — konsolidacja migracji + dokumentacja pod stan faktyczny

Domyka tydzień 2026-08-06 → 11: schemat bazy w czytelnym układzie plik-per-tabela,
dokumentacja i ADR-y opisują stan faktyczny (Windows Auth przez moduł IIS),
martwe artefakty deklaracji usunięte.

## UWAGA: ta paczka USUWA pliki — zip tego nie zrobi za Ciebie

Po rozpakowaniu zipa na repo wykonaj w katalogu repo (PowerShell):

    git rm portal-api/src/main/resources/db/migration/V1__init.sql
    git rm portal-api/src/main/resources/db/migration/V2__audit_object_ref.sql
    git rm portal-api/src/main/resources/db/migration/V3__datasets.sql
    git rm portal-api/src/main/resources/db/migration/V4__declared_user_departments.sql
    git rm portal-api/src/main/resources/db/migration/V5__audit_log_local_time_view.sql
    git rm portal-api/src/main/resources/db/migration/V6__task_log_local_time_view.sql
    git rm portal-api/src/main/resources/db/migration-dev/V100__dev_seed_tiles.sql
    git rm portal-api/src/main/resources/db/migration-dev/V101__dev_seed_scripts.sql
    git rm portal-api/src/main/resources/db/migration-dev/V102__dev_seed_dataset_licencje.sql
    git rm deploy/declared/export-user-departments.ps1

Kontrola: `git status` NIE może pokazywać żadnego pliku V100+ ani starych V1–V6;
w `db/migration` ma być 13 nowych plików (V1–V13), w `db/migration-dev` jeden
(`R__dev_seed.sql`).

## Co się zmienia

| Obszar | Zmiana |
|---|---|
| Migracje | Konsolidacja do układu plik-per-tabela w postaci DOCELOWEJ: V1 tiles, V2 tile_permissions, V3 scripts, V4 tasks, V5 task_log, V6 audit_log (z object_ref z dawnej V2), V7–V11 rodzina datasets, V12/V13 widoki czasu polskiego. Tabela `user_departments` NIE wraca (martwa — zero użyć w kodzie, model zastąpiony przez ADR-0006) |
| Seedy dev | Dawne V100–102 → jedna migracja `R__dev_seed.sql` (repeatable, strażniki idempotencji). Koniec konfliktu numeracji: migracje schematu można dokładać bez oglądania się na seedy |
| Aplikacja | Baner startowy profilu declared mówi prawdę: w prod nagłówek wypełnia moduł IIS po Windows Authentication (login uwierzytelniony), deklaracja = tryb dev/awaryjny. Javadoc AuditPersistenceIT pod nowy układ migracji |
| Moduł IIS | `AuthUserHeaderModule.cs` v2.0 (finalny, bez diagnostyki) + `web.config` zsynchronizowany z serwerem + instrukcja z trzema lekcjami wdrożenia (kernel-mode ON, weryfikacja appcmd po edycji web.config, Notatnik jako admin) |
| ADR | Nowy **ADR-0006** (tożsamość z Windows Auth przez moduł, ustalenia twarde); adnotacje o zastąpieniu w ADR-0003 i ADR-0005 |
| Dokumentacja | `portal.dzi.pl` → `arimr-app.zszik.pl` w README, BRIEF, runbookach i skryptach; README-IIS przepisany pod stan faktyczny (NTLM bez SPN, moduł, kernel-mode ON, loopback check) |

## Wdrożenie

0. **Warunek startu na serwerze**: moduł v2.0 wgrany i potwierdzony
   (curl Piotra → 200 z loginem). Sam commit/push można zrobić wcześniej.
1. **Dom**: rozpakuj zip na repo → wykonaj `git rm` z listy powyżej →
   `git status` i przegląd diffu → `mvn -q verify` — tym razem **z włączonym
   Dockerem**, bo test integracyjny zbuduje całą nową sekwencję migracji na
   prawdziwym SQL Serverze; to główna weryfikacja tej paczki → commit + push.
2. **Dom, baza dev** (jeśli istnieje uruchomieniowa): DROP — nowa numeracja
   wymaga budowy od zera.
3. **Laptop**: pull, `mvn clean package -DskipTests`, jar na serwer.
4. **Serwer**: zrzut audytu do archiwum (pierwsze historyczne loginy z Windows
   Auth warto zachować!) → stop aplikacji (Ctrl+C, konsola #1) → reset bazy
   (konsola #2):

       sqlcmd -S localhost -E -Q "ALTER DATABASE portal SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE portal; CREATE DATABASE portal;"

   **PYTANIE OTWARTE przed tym krokiem**: czy na serwerowej bazie był kiedyś
   stosowany `deploy/sql/prod-grants.sql` (albo inne ręczne GRANT-y)? Jeśli
   tak — po CREATE trzeba je wykonać ponownie; jeśli aplikacja łączy się
   kontem z prawami dbo/sysadmin, nic nie trzeba. Rozstrzygnij przed DROP.
5. **Serwer**: podmiana jara (stary do archiwum z datą) → start → w logu
   Flyway ma zbudować **V1…V13** (13 migracji) → weryfikacja: curl ze
   średnikiem → 401 JSON; Piotr/przeglądarka → 200 z loginem; portal działa
   (kafelków nadal 0 — Faza 9 to następny krok).

## Świadome decyzje wpisane w paczkę (do Twojego veta)

- Baner declared: przeredagowanie zamiast osobnego profilu (mniejsze ryzyko;
  osobny profil możliwy później, jeśli zechcesz).
- Historyczne runbooki (etap1, deklaracja) dostały tylko korektę nazwy hosta —
  treściowo pozostają zapisem historii; stan faktyczny opisuje README-IIS
  i ADR-0006.

## Poza paczką — kolejne kroki wg planu

Faza 9 (pierwsze kafelki na prawdziwych loginach), moduł v2 (departament z OU —
najpierw `whoami /fqdn` od Pawła i Piotra), Faza 10 (WinSW), Faza 12 (restart +
BackConnectionHostNames), TLS, strefa Intranet przez GPO, **repo → private**.
