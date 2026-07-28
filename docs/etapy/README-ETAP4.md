# Etap 4 — paczka: kolejka + worker. Portal zaczyna DZIAŁAĆ

```
klik kafelka ──POST /api/tiles/{code}/run──> portal-api
  @PreAuthorize canExecute ─┐                    │ INSERT tasks(PENDING, correlation_id z HTTP)
  audyt TILE_EXECUTE        │                    ▼
                            │              [tabela tasks]
frontend polling            │                    │ claim: READPAST+UPDLOCK+OUTPUT (atomowy)
GET /api/tasks/{id} <───────┘                    ▼
GET /api/tasks/{id}/log                    portal-worker ── ProcessRunner ──> powershell.exe
                                                 │   stdout/stderr (Cp852) -> task_log
                                                 └── SUCCEEDED / FAILED(kod) / TIMED_OUT
```

Jeden `correlation_id` spina: wpis audytu HTTP + wiersz zadania + linie logu workera (MDC).

## Twarde gwarancje (i ich testy)

| Gwarancja | Mechanizm | Test |
|---|---|---|
| dwóch workerów nigdy nie weźmie tego samego zadania | `WITH ... UPDLOCK, READPAST` + `OUTPUT` w jednym UPDATE | `TaskQueueIT` (2 wątki równolegle) |
| skrypt tylko z whitelisty | zlecenie wskazuje KAFELEK; join tiles→scripts; ścieżka z żądania nie istnieje jako pojęcie | `TasksEndpointTest`, `TasksFacadeTest` (409) |
| zlecenie tylko z EXECUTE | `@PreAuthorize("@access.canExecute...")` | `TasksEndpointTest` (viewer → 403) |
| status/log tylko właściciela | porównanie `requested_by`; cudze id → 403 (nie 404) | `TasksEndpointTest` |
| zawieszony skrypt nie wiesza portalu | `waitFor(timeout)` + kill **całego drzewa** (`descendants()` przed rodzicem) | demo-timeout + logika w `ProcessRunner` |
| padnięty worker nie zostawia wiecznych IN_PROGRESS | `OrphanSweeper` (timeout+60 s) → TIMED_OUT + linia SYSTEM | `TaskQueueIT` |
| sweeper nie nadpisze wyniku (i odwrotnie) | `WHERE status='IN_PROGRESS'` w finish | `TaskQueueIT` |

## Realia Windows zakodowane jawnie

`ScriptCommands`: argumenty ZAWSZE listą; PS1 = `-NoProfile -NonInteractive -ExecutionPolicy Bypass -File`.
`ProcessRunner`: kodowanie konsoli **Cp852** (PowerShell 5.1/cmd na polskim Windows) —
konfigurowalne `portal.worker.console-charset`; env `PORTAL_TASK_ID`/`PORTAL_CORRELATION_ID`
dla skryptów. Ścieżki w prod ZAWSZE absolutne (seed dev używa względnych — komentarz w V101).

## Pliki (32)

**Zmienione (8):** `common/task/TaskRepository` i `common/script/ScriptRepository` (lean —
selektywne CRUD, double'y w 15 linii) · `worker/pom.xml` (TC + failsafe) · `worker/application.yml`
(charset, sweep) · `worker/polling/TaskPoller` (cienki) · `frontend/portal-bootstrap.js` (v2) ·
`frontend/index.example.html` · test `tiles/InMemoryTilesRepositories` (public — współdzielony z tasks)

**Nowe (24):** common: `TaskLogEntry`, `TaskLogRepository` · worker/execution: `TaskQueue`,
`ProcessRunner`, `ScriptCommands`, `TaskExecutor`, `TaskLogWriter`, `ClaimedTask`,
`WorkerConfiguration` + `OrphanSweeper` · api/tasks: `RunnableScriptRepository`, `TasksFacade`,
`TasksController`, `TasksConfiguration` · `V101__dev_seed_scripts.sql` · `scripts/demo/*.ps1` (3) ·
testy: `ScriptCommandsTest`, `TaskExecutorTest`, `TaskQueueIT`, `TasksEndpointTest`, `TasksFacadeTest`

## Plan commitów (kontynuacja)

| # | Commit |
|---|--------|
| 17 | `feat(common): lean repozytoria + odczyt task_log` |
| 18 | `feat(worker): claim READPAST, ProcessRunner, egzekucja, sweeper + testy` |
| 19 | `feat(api): zlecanie i status zadań przez whitelist + testy` |
| 20 | `feat(db): dev-seed skryptów demo + pliki PS1` |
| 21 | `feat(frontend): uruchamianie i status zadań w bootstrapie` |

## Demo (Windows, 3 minuty)

1. `mvn clean verify` (ITs same się pominą bez Dockera), start api i workera z profilem dev.
2. `http://localhost:8080/index.example.html` jako `tester`:
   - **Restart ETL** → banner „Uruchomiono (#id)" → ~4 s → zielony ✔ (kroki 1–3 w `task_log`),
   - **Demo: błąd** → czerwony ✖ „kod 3", log ze STDERR w konsoli F12,
   - **Demo: timeout** → po ~5 s ✖ „przekroczono limit"; w logu SYSTEM linia o ubiciu drzewa.
3. Zabij workera w trakcie Restart ETL i uruchom ponownie → w ≤60 s sweeper oznaczy TIMED_OUT.
4. `SELECT status, exit_code, worker_host, correlation_id FROM tasks` + `audit_log` po tym samym correlation_id.

## Świadome ograniczenia (z powodami)

1. **Szeregowo, jeden worker** — skrypty operacyjne rzadko chcą działać równolegle na tym
   samym serwerze; kolejka i claim są już wielo-workerowe, więc skalowanie = decyzja, nie refaktor.
2. **Parametry użytkownika wyłączone** — jedyna droga user-input→wiersz poleceń zostaje
   zamknięta do czasu walidacji JSON Schema (kolumny `params`/`params_schema` czekają).
3. **Polling audytowany** — każdy GET statusu to wiersz w audit_log; wolumen ograniczony
   czasem zadania (2,5 s interwał, stop na statusie terminalnym), retencja w Etapie 6.

## Zastrzeżenia

- `TaskQueueIT` ładuje migracje lokalizacją `filesystem:../portal-api/...` — działa z Mavena
  w układzie repo; IDE uruchamiające testy z innego cwd może wymagać poprawki ścieżki.
- Jeśli TC 2.0 zmienił pakiety — jak w Etapie 2, poprawka tylko w importach IT.

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
