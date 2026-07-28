# Commit 31 — poprawki z przeglądu seniorskiego

`fix: izolacja testów (FIRST), SID-y zamiast nazw grup, COUNT dla missingInFile, DRY`

Rozpakuj na katalog scalonego projektu (struktura się pokrywa, nadpisze 11 plików, doda 2)
i uruchom `mvn clean verify`.

| # | Problem | Poprawka |
|---|---------|----------|
| 1 | **FIRST-Isolated**: beany-double'y w `TasksEndpointTest`/`DatasetsEndpointTest` to singletony kontekstu — testy edycji, ETag i zlecania dzieliły mutowalny stan; wynik zależał od kolejności metod | `reset()` w double'ach + `@BeforeEach` (seed wiersza przeniesiony z beana do `@BeforeEach`) |
| 2 | **Lokalizacja Windows**: `Get-LocalGroupMember -Group 'Administrators'` i `icacls "Administrators:..."` na polskim serwerze („Administratorzy") cicho nie trafiają | SID-y: grupa `S-1-5-32-544`, SYSTEM `S-1-5-18` — `verify-hardening.ps1`, `README-WinSW.md`, `etap6-runbook.md` |
| 3 | `missingInFile` ładował wszystkie agregaty (N+1 po komórkach), żeby policzyć wiersze | `countByDatasetId` / `countMissingKeys` — COUNT po stronie bazy; guard pustej kolekcji w fasadzie; semantyka odwzorowana w double'u |
| 4 | **DRY**: zbiór adresów loopback w 2 klasach; identyczny `asUser` w 3 testach | `ClientIpResolver.isLoopback(...)` jako jedyne źródło prawdy; `testsupport/TestRequests.asUser(...)` |

Pliki — zmienione (11): `ClientIpResolver`, `LoopbackHeaderAuthenticationFilter`,
`DatasetRepositories`, `DatasetsFacade`, `InMemoryDatasetRepositories`,
`DatasetsEndpointTest`, `TasksEndpointTest`, `TilesEndpointTest`,
`deploy/verify-hardening.ps1`, `deploy/winsw/README-WinSW.md`, `docs/etap6-runbook.md`.
Nowe (2): `testsupport/TestRequests.java`, ten plik.

Poza zakresem (świadomie, progi w ADR/przeglądzie): N+1 przy ładowaniu komórek widoku,
interfejsy dla klas workera, publiczne metody kontrolerów.

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
