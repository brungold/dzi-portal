# Commit 36 — POPRAWKA: nagłówki autorstwa złamały checksumy Flyway

`fix(db): usunięcie nagłówków autorstwa z migracji Flyway — checksum mismatch blokował start`

## Objaw

```
Validate failed: Migrations have failed validation
Migration checksum mismatch for migration version 1
-> Applied to database : -549227711
-> Resolved locally    : -415152359
```
...i tak dla wszystkich sześciu wersji (1, 2, 3, 100, 101, 102). Aplikacja nie startuje
na bazie, na której schemat już istnieje.

## Przyczyna (błąd w paczce `komplet-v2-autorstwo`)

Nagłówek z informacją o autorstwie został dopisany **także do plików migracji Flyway**.
Flyway liczy checksum z treści pliku i zapisuje go w `flyway_schema_history` przy
pierwszym zastosowaniu. **Migracja raz zastosowana jest niezmienna** — dopisanie choćby
komentarza zmienia sumę kontrolną i walidacja przerywa start.

To nie jest problem kosmetyczny: w tej postaci paczka wysadziłaby **każde** środowisko
z istniejącym schematem, łącznie z produkcją.

## Poprawka

Sześć plików migracji wraca do postaci **bajt-w-bajt sprzed nagłówków** — checksumy znów
zgadzają się z tym, co zapisano w bazie. Zero zmian w SQL-u, zero zmian w bazie.

`AUTORSTWO.md` dostaje sekcję „Wyjątek: migracje Flyway BEZ nagłówków" z uzasadnieniem —
żeby nikt (łącznie z autorem za pół roku) nie dodał ich tam ponownie.

Skrypty `deploy/sql/**` nie są zarządzane przez Flyway — nagłówki w nich zostają.

## Pliki (7)

- `portal-api/src/main/resources/db/migration/V1__init.sql`, `V2__audit_object_ref.sql`, `V3__datasets.sql`
- `portal-api/src/main/resources/db/migration-dev/V100__dev_seed_tiles.sql`, `V101__dev_seed_scripts.sql`, `V102__dev_seed_dataset_licencje.sql`
- `AUTORSTWO.md` — sekcja z wyjaśnieniem wyjątku

## Zastosowanie

Rozpakuj na katalog projektu (nadpisz) → uruchom aplikację. Bazy **nie** trzeba ruszać.

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
