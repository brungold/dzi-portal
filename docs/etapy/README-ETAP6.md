# Etap 6 — paczka: odświeżanie ETag + hardening + deploy z rollbackiem

Ostatni etap planu. Portal po nim: SSO Kerberos → kafelki RBAC → zadania → zbiory danych
z auto-odświeżaniem, wszystko audytowane, wdrażane jednym skryptem z przećwiczonym rollbackiem.

## Co i dlaczego (ADR-0002 ma pełne uzasadnienia)

| Temat | Decyzja |
|---|---|
| odświeżanie | polling ETag co 10 s; token = `COUNT:SUM(version):MAX(updated_at)` (1 agregacja); 304 bez treści; STOMP dopiero za progiem z ADR |
| audyt vs polling | **304 poza audytem** — audytujemy zdarzenia, nie tykanie zegara; realne 200 i odmowy zostają |
| retencja | Task Scheduler + konto utrzymaniowe z GRANT DELETE; DENY dla gMSA nietknięte; partie po 5000 |
| gMSA | ddladmin zostaje przy api (próg rewizji w ADR); worker: scripts **read-only** — twardy FAIL w verify |
| deploy | jeden skrypt: stop→backup→podmiana→migracje→smoke→start; porażka = automatyczny rollback; `-RollbackOnly` do ćwiczeń |
| shutdown | graceful 20 s (api) < stoptimeout 30 s (WinSW) |

## Pliki

**Zmienione (9):** `DatasetRepositories` (+stateToken) · `DatasetsFacade` (+stateToken) ·
`DatasetsController` (ETag/If-None-Match/304) · `AuditFilter` (+pominięcie 304) ·
`application-prod.yml` (graceful) · `frontend/dataset.html` (v2: polling, pauza przy edycji) ·
testy: `AuditFilterTest`, `DatasetsEndpointTest`, `InMemoryDatasetRepositories`

**Nowe (7):** `deploy/deploy.ps1` · `deploy/verify-hardening.ps1` ·
`deploy/winsw/portal-worker.xml.template` · `deploy/sql/audit-retention.sql` ·
`deploy/register-audit-retention.ps1` · `docs/adr/0002-hardening-i-odswiezanie.md` ·
`docs/etap6-runbook.md`

## Plan commitów (kontynuacja i finał)

| # | Commit |
|---|--------|
| 27 | `feat(datasets): ETag/304 na widoku zbioru + 304 poza audytem + graceful shutdown` |
| 28 | `feat(frontend): auto-odświeżanie dataset.html (polling ETag, pauza przy edycji)` |
| 29 | `feat(deploy): deploy.ps1 z rollbackiem, WinSW workera, retencja rejestrów` |
| 30 | `docs: ADR-0002 (odświeżanie/hardening) + runbook Etapu 6` |

## Zastrzeżenia

1. Token ETag opiera się na `SUM(version)` i `MAX(updated_at)` — teoretyczny martwy punkt
   (dwie edycje znoszące sumę w tej samej milisekundzie) jest praktycznie nieosiągalny,
   a najbliższy tick i tak przyniesie świeży token przy kolejnej zmianie.
2. `verify-hardening.ps1` sprawdza ACL po nazwach kont — przy innym sAMAccountName gMSA
   podaj parametry `-ApiAccount/-WorkerAccount`.
3. Skrypty PS jak zawsze pisane na sucho — kolejność na serwerze: runbook B, potem C
   (test rollbacku!), na końcu D; co czerwone — wklej.

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
