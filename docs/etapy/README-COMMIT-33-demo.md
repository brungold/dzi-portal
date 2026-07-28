# Commit 33 — profil demo: portal bez bazy danych

`feat(demo): profil demo — trwałość in-memory za istniejącymi interfejsami, audyt do logu, symulator zadań`

Rozpakuj na katalog scalonego projektu (nadpisze 1 plik, doda 9) → `mvn clean verify`
→ uruchom z profilami `dev,demo` (laptop) albo `prod,demo` (serwer). Szczegóły
i ograniczenia: `docs/demo-runbook.md`.

## Wynik analizy (dlaczego to NIE jest refactor)

Architektura miała gotowe szwy: repozytoria to interfejsy (lean `Repository`),
fasady dostają je konstruktorem, profile są wyłącznikami. Profil demo dokłada
RÓWNOLEGŁĄ implementację trwałości — logika biznesowa, security i frontend:
**zero zmienionych linii**. Jedyny dotknięty plik produkcyjny to `AuditWriter`
(+`@Profile("!demo")`), bo jako `@Component` rejestrował się bezwarunkowo.

| Element | Jak rozwiązany |
|---|---|
| brak DataSource | `application-demo.yml`: `spring.autoconfigure.exclude` trzech autokonfiguracji JDBC/Flyway (FQN-y Boot 4.1 potwierdzone ze stack trace'a); Data JDBC i health bazy wygaszają się kaskadowo |
| repozytoria | implementacje in-memory w pakietach modułów (rekordy są pakietowe — granice modułów nienaruszone), seed = lustro V100–V102 |
| `@Version` zbiorów | semantyka 1:1 z double'ami testowymi: stara wersja ⇒ `OptimisticLockingFailureException` ⇒ 409; ETag działa identycznie |
| `@Transactional` importu | no-op `PlatformTransactionManager` + uczciwy komentarz (wszystko-albo-nic i tak daje walidacja-przed-zapisem) |
| audyt | `DemoAuditWriter` → log (`AUDYT[demo] ...`); wzorzec dziedziczenia jak w testach; wydzielenie interfejsu = zapisany próg refaktoryzacji |
| kafelki SCRIPT | `DemoTaskSimulator` (@Scheduled, 2 fazy) — pełny cykl UI bez workera; `blad`→FAILED, `timeout`→TIMED_OUT |
| worker | w demo NIE uruchamiany (i nie musi być zbudowany) |

## Pliki

**Zmienione (1):** `portal-common/.../audit/AuditWriter.java` (+`@Profile("!demo")`)

**Nowe (9):** `application-demo.yml` · `infrastructure/demo/`: `DemoConfiguration`,
`DemoAuditWriter`, `DemoTaskStore`, `DemoTaskSimulator` · `tiles/DemoTilesConfiguration` ·
`tasks/DemoTasksConfiguration` · `datasets/DemoDatasetsConfiguration` ·
`docs/demo-runbook.md` · ten plik

## „Kod/commit który dodaje bazę"

Nie istnieje, bo nie musi: kod bazodanowy to CAŁY dotychczasowy projekt (profile
dev/prod). „Dodanie bazy" = usunięcie `,demo` z aktywnych profili + kroki
bazodanowe z runbooków (procedura: demo-runbook, sekcja „Przejście"). Jedna linia
konfiguracji w obie strony — przełącznik, nie rozwidlenie.

## Zastrzeżenia

1. Nazwy wykluczanych autokonfiguracji pochodzą ze stack trace'a Boot 4.1.0-M1;
   gdyby start w demo krzyknął o kolejną autokonfigurację JDBC — log poda jej FQN,
   dopisz go do listy w `application-demo.yml`.
2. Testy istniejące nie dotykają profilu demo (beany @Profile("demo") nie ładują
   się w slice'ach) — `mvn clean verify` przechodzi bez zmian.
3. Dane ulotne + audyt w logu = tryb pokazowo-integracyjny, nie produkcyjny.

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
