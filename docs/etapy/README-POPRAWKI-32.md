# Commit 32 — poprawki z drugiego przeglądu

`fix: return-w-finally w audycie, 400 dla złych wejść, SID Users, pula schedulera, update wiersza, BIGINT w tokenie`

**Wymaga wcześniej nałożonego commitu 31** (bazuje na jego wersjach: `DatasetRepositories`,
`verify-hardening.ps1`, oba testy endpointowe). Rozpakuj na katalog projektu → `mvn clean verify`.

| # | Problem | Poprawka |
|---|---------|----------|
| 1 | `return` w bloku `finally` (`AuditFilter`, patch 304 z Etapu 6) **połykał wyjątki** lecące z łańcucha | zwykły warunek `!= 304` + wydzielone `writeEntrySafely(...)`; istniejące testy (w tym 304-skip) bez zmian |
| 2 | check ACL pliku z sekretem dopasowywał `'Users|Uzytkownicy'` — polskie „Użytkownicy" (z „ż") cicho przechodziło | tłumaczenie `IdentityReference` na SID `S-1-5-32-545` |
| 3 | nienumeryczne id w ścieżce, zepsuty JSON, brak parametru/pliku → catch-all → **500** ze stack trace | jeden handler `@ExceptionHandler({MethodArgumentTypeMismatch, HttpMessageNotReadable, MissingServletRequestParameter, MissingServletRequestPart})` → 400; **+2 testy** (Tasks: `/api/tasks/abc`, Datasets: zepsuty JSON) |
| 4 | scheduler workera jednowątkowy — 5-minutowy skrypt blokował `OrphanSweeper` | `spring.task.scheduling.pool.size: 2` |
| 5 | `row.update({'data.x': v})` — Tabulator potrafi utworzyć płaską właściwość zamiast zagnieżdżonej | aktualizacja pełnym obiektem `data` |
| 6 | `SUM(version)` w tokenie ETag jako INT — teoretyczny overflow | `SUM(CAST(version AS BIGINT))` |

Pliki — zmienione (8): `AuditFilter` · `GlobalExceptionHandler` · `DatasetRepositories` ·
`frontend/dataset.html` · `portal-worker/application.yml` · `deploy/verify-hardening.ps1` ·
`TasksEndpointTest` · `DatasetsEndpointTest`. Nowe (1): ten plik.

Świadomie nadal poza zakresem (progi w ADR/przeglądach): circuit breaker na LDAP,
sufit czasowy pollingu zadania w UI, literał `"correlationId"` w workerze.

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
