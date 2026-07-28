# Etap 2 — paczka: audyt zdarzeń biznesowych + testy na prawdziwej bazie

## Model audytu po tym etapie

| Warstwa | Skąd | Kiedy |
|---|---|---|
| HTTP (method, path, status, czas, user, IP, correlation id) | `AuditFilter` oplatający security | **każde** żądanie `/api/*`, także 401/403 |
| `action` — nazwa akcji biznesowej | `@Audited(action = "TILE_EXECUTE")` na metodzie kontrolera → `AuditedActionInterceptor` | deklaratywnie; działa w preHandle, więc **odmowy też mają action** |
| `object_ref` — obiekt akcji ("tile:42") | `AuditContext.setObjectRef(...)` z kodu kontrolera/fasady | dynamicznie, gdy znany jest konkret |
| Przebieg zadań workera | tabele `tasks` + `task_log` (correlation id spina z audit_log) | granica świadoma: worker NIE pisze do audit_log przez AuditContext |

Append-only egzekwują uprawnienia SQL (DENY > rola) — i od tego etapu **jest na to test**.

## Pliki

**Zmienione (nadpisz, 6):** `portal-api/pom.xml` · `common/audit/AuditEntry.java` ·
`common/audit/AuditWriter.java` · `infrastructure/audit/AuditFilter.java` ·
`infrastructure/web/PortalRequestAttributes.java` · `infrastructure/security/WhoAmIController.java`

**Nowe (8):** `db/migration/V2__audit_object_ref.sql` · `audit/Audited.java` ·
`audit/AuditedActionInterceptor.java` · `audit/AuditContext.java` ·
`web/WebMvcConfiguration.java` · testy: `AuditFilterTest`, `AuditedActionInterceptorTest`,
`AuditPersistenceIT`

Uwaga: pom zawiera też build-info z opcjonalnego commitu 9 Etapu 1 — jeśli go pominąłeś,
właśnie doszedł.

## Plan commitów (kontynuacja)

| # | Commit | Zakres |
|---|--------|--------|
| 10 | `feat(db): V2 — object_ref w audycie` | migracja V2, `AuditEntry`, `AuditWriter` |
| 11 | `feat(audit): deklaratywne akcje biznesowe (@Audited) i AuditContext` | adnotacja, interceptor, `AuditContext`, `WebMvcConfiguration`, `AuditFilter`, `PortalRequestAttributes`, `WhoAmIController`, testy jednostkowe |
| 12 | `test(audit): Testcontainers — migracje, zapis, append-only` | `portal-api/pom.xml`, `AuditPersistenceIT` |

## Jak uruchomić testy integracyjne

```
mvn -pl portal-api -am clean verify        # surefire: testy jednostkowe, failsafe: *IT
mvn -pl portal-api -am clean verify -DskipITs   # bez integracyjnych
```

- Wymagany Docker (obraz `mssql/server:2022-latest`, ~1,6 GB przy pierwszym pobraniu).
- **Bez Dockera nic nie czerwienieje** — `@Testcontainers(disabledWithoutDocker = true)`
  pomija klasę (typowy scenariusz: laptop służbowy bez Dockera → ITs przechodzą w domu/CI).
- IT celowo nie podnosi kontekstu Springa (czysty JUnit + Flyway API + JdbcTemplate),
  więc omija niepewność plasterków Boot 4.

## Weryfikacja ręczna po wdrożeniu

```
curl -i -H "X-Auth-User: DZI\tester" http://localhost:8080/api/whoami
SELECT TOP 5 action, object_ref, username, status FROM audit_log ORDER BY id DESC
```
→ najnowszy wpis ma `action = 'WHOAMI'` (pierwszy konsument mechanizmu).

## Zastrzeżenia

1. **Testcontainers 2.0**: Boot 4 zarządza wersją TC; jeśli po `mvn verify` importy
   `org.testcontainers.*` nie kompilują się (TC 2.0 zmieniał nazwy modułów/pakietów),
   poprawka ogranicza się do importów w `AuditPersistenceIT` i ewentualnie artifactId
   w pomie — wklej błąd, wskażę dokładnie.
2. **Retencja audit_log**: świadomie odroczona (SQL Express nie ma Agenta; kandydat:
   zadanie w workerze albo Task Scheduler). Decyzja przy Etapie 6 — tabela rośnie
   powoli (jeden wiersz ≈ 0,3 KB).
3. Komentarz w V1 („action od Etapu 3+") pozostaje — migracje są niemutowalne;
   stan faktyczny opisuje nagłówek V2.

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
